<script lang="ts">
    import type { Workspace } from '../workspace.svelte';
    import { reportLabels, formatReportCell } from '../report-display';

    let { workspace }: { workspace: Workspace } = $props();
</script>

<section class="panel">
    <label>
        <span>Raport</span>
        <select
            aria-label="Raport"
            aria-describedby="report-description"
            bind:value={workspace.selectedQueryId}
            onchange={() => (workspace.report = null)}
        >
            {#each workspace.queries as query (query.id)}
                <option value={query.id}>
                    {query.name}
                </option>
            {/each}
        </select>
    </label>
    <p id="report-description">
        {workspace.selectedQuery?.description}
    </p>
    <div class="toolbar">
        {#if workspace.selectedQueryId === 'over-budget'}
            <label>
                <span>Dotacja większa niż (PLN)</span>
                <input
                    inputmode="decimal"
                    placeholder="np. 50000"
                    bind:value={workspace.reportThreshold}
                    oninput={() => (workspace.report = null)}
                />
            </label>
        {/if}
        <button
            class="primary"
            disabled={workspace.busy || !workspace.competition}
            onclick={() => workspace.runReport()}
        >
            Pokaż wyniki
        </button>
    </div>
    <details>
        <summary>Pokaż kod XQuery</summary>
        <pre class="query-code">{workspace.selectedQuery?.source}</pre>
    </details>
</section>
{#if workspace.report}
    <section class="panel">
        <h2>Wyniki: {workspace.report.total}</h2>
        {#if !workspace.report.rows.length}
            <p class="muted">Brak danych dla wybranych parametrów.</p>
        {:else}
            <div class="table-scroll">
                <table>
                    <thead>
                        <tr>
                            {#each Object.keys(workspace.report.rows[0]) as key (key)}
                                <th>
                                    {reportLabels[key] ?? key}
                                </th>
                            {/each}
                        </tr>
                    </thead>
                    <tbody>
                        {#each workspace.report.rows as row, index (index)}
                            <tr>
                                {#each Object.entries(row) as [key, value] (key)}
                                    <td>
                                        {formatReportCell(key, value)}
                                    </td>
                                {/each}
                            </tr>
                        {/each}
                    </tbody>
                </table>
            </div>
        {/if}
        {#if workspace.report.total > workspace.reportPageSize}
            <div class="pagination">
                <button
                    class="quiet"
                    disabled={workspace.busy || workspace.reportOffset === 0}
                    onclick={() =>
                        workspace.runReport(
                            workspace.reportOffset - workspace.reportPageSize,
                        )}
                >
                    Poprzednia
                </button>
                <span>
                    Strona {Math.floor(
                        workspace.reportOffset / workspace.reportPageSize,
                    ) + 1} z {Math.ceil(
                        workspace.report.total / workspace.reportPageSize,
                    )}
                </span>
                <button
                    class="quiet"
                    disabled={workspace.busy ||
                        workspace.reportOffset + workspace.reportPageSize >=
                            workspace.report.total}
                    onclick={() =>
                        workspace.runReport(
                            workspace.reportOffset + workspace.reportPageSize,
                        )}
                >
                    Następna
                </button>
            </div>
        {/if}
    </section>
{/if}
