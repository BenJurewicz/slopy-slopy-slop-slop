<script lang="ts">
    import type { Workspace } from '../workspace.svelte';
    import { api, money, status } from '../api';

    let { workspace }: { workspace: Workspace } = $props();
</script>

<div class="toolbar">
    <p class="muted">Tylko pełne dotacje; projekty ponad budżet są pomijane.</p>
    <button
        class="primary"
        disabled={workspace.busy ||
            workspace.loading ||
            workspace.fundingLoading ||
            !workspace.fundingPreview?.rows.length}
        onclick={() =>
            workspace.runAction(
                () => api.prepareDecisions(),
                'Przygotowano szkice decyzji.',
            )}
    >
        Przygotuj decyzje
    </button>
</div>
{#if workspace.fundingPreview}
    <section class="panel">
        <h2>Dostępne: {money(workspace.fundingPreview.available)}</h2>
        <div class="table-scroll">
            <table>
                <thead>
                    <tr>
                        <th>Miejsce / projekt</th>
                        <th>Punkty</th>
                        <th>Kwota</th>
                        <th>Propozycja</th>
                        <th>Powód</th>
                    </tr>
                </thead>
                <tbody>
                    {#each workspace.fundingPreview.rows as row, index (row.applicationId)}
                        <tr>
                            <td><strong>{index + 1}. {row.title}</strong></td>
                            <td>{row.score}</td>
                            <td>{money(row.requestedAmount)}</td>
                            <td>
                                <span
                                    class="badge"
                                    class:good={row.outcome === 'AWARD'}
                                >
                                    {status(row.outcome)}
                                </span>
                            </td>
                            <td>{row.reason}</td>
                        </tr>
                    {/each}
                </tbody>
            </table>
        </div>
    </section>
{:else if workspace.fundingLoading && !workspace.loading}
    <p class="notice" role="status">Wczytywanie rankingu...</p>
{/if}
