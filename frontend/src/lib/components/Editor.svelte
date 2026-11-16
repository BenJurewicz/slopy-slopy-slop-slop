<script lang="ts">
    import Fields from './Fields.svelte';
    import { fields, type Data } from '../forms';
    import { api, type Kind } from '../api';
    import type { BudgetLine } from '../generated/api/types.gen';

    const documentTitles: Record<Kind, string> = {
        competition: 'Ustawienia CloudMigration',
        application: 'Wniosek',
        evaluation: 'Ocena',
        decision: 'Decyzja',
    };

    let {
        kind,
        value,
        save,
        close,
        busy = false,
        settingsLocked = false,
    }: {
        kind: Kind;
        value: Data;
        save: (value: Data) => Promise<void>;
        close: () => void;
        busy?: boolean;
        settingsLocked?: boolean;
    } = $props();

    // Live score totals need a deep draft that tracks nested mutations.
    // eslint-disable-next-line svelte/prefer-writable-derived
    let draft = $state<Data>({});
    let error = $state('');
    let calculating = $state(false);
    const readonly = $derived(value.state && value.state !== 'DRAFT');

    $effect(() => {
        draft = structuredClone($state.snapshot(value));
    });

    $effect(() => {
        if (kind !== 'evaluation' || !draft.scores) return;
        draft.total = totalScore(draft.scores);
    });

    function totalScore(scores: unknown): number {
        return (scores as { points: number }[]).reduce(
            (sum, score) => sum + score.points,
            0,
        );
    }

    async function calculate() {
        calculating = true;
        error = '';
        try {
            const result = await api.calculateBudget(
                draft.budget as BudgetLine[],
                String(draft.requestedAmount ?? '0.00'),
            );
            draft = {
                ...draft,
                budget: result.lines,
                projectCost: result.total,
                ownContribution: result.ownContribution,
            };
        } catch (cause) {
            error = (cause as Error).message;
        } finally {
            calculating = false;
        }
    }

    async function submit(event: SubmitEvent) {
        event.preventDefault();
        error = '';
        if (kind === 'evaluation')
            draft = {
                ...draft,
                total: totalScore(draft.scores),
            };
        try {
            await save(draft);
        } catch (cause) {
            error = (cause as Error).message;
        }
    }
</script>

<section class="panel editor">
    <header class="section-heading">
        <div>
            <h2>{documentTitles[kind]}</h2>
        </div>
        <button class="quiet" onclick={close}>Zamknij</button>
    </header>
    {#if readonly}
        <p class="notice">Zatwierdzony: tylko do odczytu.</p>
    {/if}
    {#if error}
        <p class="error" role="alert">{error}</p>
    {/if}
    <form onsubmit={submit}>
        {#if kind === 'competition' && settingsLocked}
            <Fields
                bind:value={draft}
                spec={fields.competition.filter(
                    (field) => field.key !== 'stage',
                )}
                disabled
            />
            <Fields
                bind:value={draft}
                spec={fields.competition.filter(
                    (field) => field.key === 'stage',
                )}
                disabled={busy}
            />
        {:else}
            <Fields
                bind:value={draft}
                spec={fields[kind]}
                disabled={Boolean(readonly) || busy}
            />
        {/if}
        {#if kind === 'application' && !readonly}
            <div class="notice">
                Kwoty netto w PLN. Po zmianie kosztów lub dotacji przelicz
                budżet. <button
                    type="button"
                    class="secondary"
                    disabled={calculating || busy}
                    onclick={calculate}
                >
                    {calculating ? 'Przeliczanie...' : 'Przelicz budżet'}
                </button>
            </div>
        {/if}
        {#if !readonly}
            <div class="form-actions">
                <button
                    class="primary"
                    disabled={busy || calculating}
                    type="submit"
                >
                    {busy
                        ? 'Zapisywanie...'
                        : kind === 'competition'
                          ? 'Zapisz ustawienia'
                          : 'Zapisz szkic'}
                </button>
                <button class="secondary" type="button" onclick={close}>
                    Anuluj
                </button>
            </div>
        {/if}
    </form>
</section>
