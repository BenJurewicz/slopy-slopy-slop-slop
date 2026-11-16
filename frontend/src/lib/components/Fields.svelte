<script lang="ts">
    import Fields from './Fields.svelte';
    import { captions, empty, type Data, type Field } from '../forms';
    let {
        value = $bindable(),
        spec,
        disabled = false,
    }: { value: Data; spec: Field[]; disabled?: boolean } = $props();
    function set(key: string, next: unknown) {
        value = { ...value, [key]: next };
    }
    function object(key: string) {
        return (value[key] ?? {}) as Data;
    }
    function list(key: string) {
        return (value[key] ?? []) as Data[];
    }
    function nested(key: string, index: number, item: Data) {
        set(
            key,
            list(key).map((row, i) => (i === index ? item : row)),
        );
    }
</script>

{#each spec as f (f.key)}
    {#if f.type === 'object'}
        <fieldset>
            <legend>{f.label}</legend><Fields
                spec={f.children!}
                {disabled}
                bind:value={() => object(f.key), (v) => set(f.key, v)}
            />
        </fieldset>
    {:else if f.type === 'list'}
        <fieldset>
            <legend>{f.label}</legend>
            <!-- Values change immutably; index keys keep active row inputs mounted. -->
            {#each list(f.key) as _item, index (index)}
                <div class="repeat">
                    <div class="repeat-title">
                        <strong>Pozycja {index + 1}</strong
                        >{#if !disabled && f.key !== 'scores'}<button
                                type="button"
                                class="quiet"
                                aria-label={`Usuń pozycję ${index + 1} - ${f.label}`}
                                onclick={() =>
                                    set(
                                        f.key,
                                        list(f.key).filter(
                                            (_, i) => i !== index,
                                        ),
                                    )}>Usuń</button
                            >{/if}
                    </div>
                    <Fields
                        spec={f.children!}
                        {disabled}
                        bind:value={
                            () => list(f.key)[index],
                            (v) => nested(f.key, index, v)
                        }
                    />
                </div>
            {/each}
            {#if !disabled && f.key !== 'scores'}<button
                    type="button"
                    class="secondary"
                    onclick={() =>
                        set(f.key, [...list(f.key), empty(f.children!)])}
                    >Dodaj pozycję - {f.label}</button
                >{/if}
        </fieldset>
    {:else}
        <label class:check={f.type === 'boolean'}
            ><span>{f.label}</span>
            {#if f.type === 'textarea'}<textarea
                    rows="3"
                    required
                    {disabled}
                    value={String(value[f.key] ?? '')}
                    oninput={(e) => set(f.key, e.currentTarget.value)}
                ></textarea>
            {:else if f.type === 'select'}<select
                    {disabled}
                    value={String(value[f.key] ?? f.options![0])}
                    onchange={(e) => set(f.key, e.currentTarget.value)}
                    >{#each f.options! as option (option)}<option value={option}
                            >{captions[option] ?? option}</option
                        >{/each}</select
                >
            {:else if f.type === 'boolean'}<input
                    type="checkbox"
                    disabled={disabled || f.readonly}
                    checked={Boolean(value[f.key])}
                    onchange={(e) => set(f.key, e.currentTarget.checked)}
                />
            {:else if f.type === 'number'}<input
                    type="number"
                    min="0"
                    step="1"
                    required
                    disabled={disabled || f.readonly}
                    value={Number(value[f.key] ?? 0)}
                    oninput={(e) => set(f.key, Number(e.currentTarget.value))}
                />
            {:else}<input
                    type={f.type === 'date'
                        ? 'date'
                        : f.type === 'email'
                          ? 'email'
                          : 'text'}
                    required={!f.optional && !f.readonly}
                    readonly={f.readonly}
                    {disabled}
                    value={String(value[f.key] ?? '')}
                    oninput={(e) => set(f.key, e.currentTarget.value)}
                />{/if}
        </label>
    {/if}
{/each}
