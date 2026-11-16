<script lang="ts">
    import Fields from './Fields.svelte';
    import { captions, initialValues, type Data, type Field } from '../forms';
    let {
        value = $bindable(),
        spec,
        disabled = false,
    }: { value: Data; spec: Field[]; disabled?: boolean } = $props();

    function setField(key: string, next: unknown) {
        value = { ...value, [key]: next };
    }

    function objectValue(key: string) {
        return (value[key] ?? {}) as Data;
    }

    function listValue(key: string) {
        return (value[key] ?? []) as Data[];
    }

    function appendListItem(field: Field) {
        setField(field.key, [
            ...listValue(field.key),
            initialValues(field.children!),
        ]);
    }

    function removeListItem(key: string, index: number) {
        setField(
            key,
            listValue(key).filter((_, rowIndex) => rowIndex !== index),
        );
    }

    function updateListItem(key: string, index: number, item: Data) {
        setField(
            key,
            listValue(key).map((row, rowIndex) =>
                rowIndex === index ? item : row,
            ),
        );
    }
</script>

{#each spec as field (field.key)}
    {#if field.type === 'object'}
        <fieldset>
            <legend>{field.label}</legend>
            <Fields
                spec={field.children!}
                {disabled}
                bind:value={
                    () => objectValue(field.key),
                    (updatedValue) => setField(field.key, updatedValue)
                }
            />
        </fieldset>
    {:else if field.type === 'list'}
        <fieldset>
            <legend>{field.label}</legend>
            <!-- Values change immutably; index keys keep active row inputs mounted. -->
            {#each listValue(field.key) as _item, index (index)}
                <div class="repeat">
                    <div class="repeat-title">
                        <strong>Pozycja {index + 1}</strong>
                        {#if !disabled && field.key !== 'scores'}
                            <button
                                type="button"
                                class="quiet"
                                aria-label={`Usuń pozycję ${index + 1} - ${field.label}`}
                                onclick={() => removeListItem(field.key, index)}
                            >
                                Usuń
                            </button>
                        {/if}
                    </div>
                    <Fields
                        spec={field.children!}
                        {disabled}
                        bind:value={
                            () => listValue(field.key)[index],
                            (updatedValue) =>
                                updateListItem(field.key, index, updatedValue)
                        }
                    />
                </div>
            {/each}
            {#if !disabled && field.key !== 'scores'}
                <button
                    type="button"
                    class="secondary"
                    onclick={() => appendListItem(field)}
                >
                    Dodaj pozycję - {field.label}
                </button>
            {/if}
        </fieldset>
    {:else}
        <label class:check={field.type === 'boolean'}>
            <span>{field.label}</span>
            {#if field.type === 'textarea'}
                <textarea
                    rows="3"
                    required
                    {disabled}
                    value={String(value[field.key] ?? '')}
                    oninput={(event) =>
                        setField(
                            field.key,
                            event.currentTarget.value,
                        )}></textarea>
            {:else if field.type === 'select'}
                <select
                    {disabled}
                    value={String(value[field.key] ?? field.options![0])}
                    onchange={(event) =>
                        setField(field.key, event.currentTarget.value)}
                >
                    {#each field.options! as option (option)}
                        <option value={option}>
                            {captions[option] ?? option}
                        </option>
                    {/each}
                </select>
            {:else if field.type === 'boolean'}
                <input
                    type="checkbox"
                    disabled={disabled || field.readonly}
                    checked={Boolean(value[field.key])}
                    onchange={(event) =>
                        setField(field.key, event.currentTarget.checked)}
                />
            {:else if field.type === 'number'}
                <input
                    type="number"
                    min="0"
                    step="1"
                    required
                    disabled={disabled || field.readonly}
                    value={Number(value[field.key] ?? 0)}
                    oninput={(event) =>
                        setField(field.key, Number(event.currentTarget.value))}
                />
            {:else}
                <input
                    type={field.type === 'date'
                        ? 'date'
                        : field.type === 'email'
                          ? 'email'
                          : 'text'}
                    required={!field.optional && !field.readonly}
                    readonly={field.readonly}
                    {disabled}
                    value={String(value[field.key] ?? '')}
                    oninput={(event) =>
                        setField(field.key, event.currentTarget.value)}
                />
            {/if}
        </label>
    {/if}
{/each}
