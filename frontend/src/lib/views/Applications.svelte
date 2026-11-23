<script lang="ts">
    import type { Workspace } from '../workspace.svelte';
    import { money } from '../api';
    import { newApplication } from '../forms';

    let { workspace }: { workspace: Workspace } = $props();
</script>

<div class="toolbar">
    <div class="actions">
        <a rel="external" class="secondary" href="/api/documents/template">
            Pobierz pusty formularz DOCX
        </a>
        <a rel="external" class="secondary" href="/api/documents/example">
            Zobacz wypełniony przykład
        </a>
        <label class="file-picker">
            Importuj DOCX
            <input
                type="file"
                accept=".docx"
                disabled={workspace.busy || !workspace.competition}
                onchange={workspace.importApplication}
            />
        </label>
    </div>
    <button
        class="primary"
        disabled={!workspace.competition}
        onclick={() => workspace.openEditor('application', newApplication())}
    >
        Nowy wniosek
    </button>
</div>

<div class="toolbar">
    <input
        class="search"
        aria-label="Szukaj wniosków"
        placeholder="Szukaj projektu lub firmy..."
        bind:value={workspace.searchText}
        oninput={() => (workspace.pageIndex = 0)}
    />
    <select
        style="max-width:200px"
        aria-label="Status wniosku"
        bind:value={workspace.statusFilter}
        onchange={() => (workspace.pageIndex = 0)}
    >
        <option value="">Wszystkie statusy</option>
        <option value="DRAFT">Szkice</option>
        <option value="SUBMITTED">Złożone</option>
    </select>
</div>
{#if !workspace.filteredApplications.length}
    <div class="empty">
        <p>Brak wniosków dla wybranych filtrów.</p>
    </div>
{:else}
    <section class="panel table-scroll">
        <table>
            <thead>
                <tr>
                    <th>Projekt / firma</th>
                    <th>Dotacja</th>
                    <th>Status</th>
                    <th>Działania</th>
                </tr>
            </thead>
            <tbody>
                {#each workspace.filteredApplications.slice(workspace.pageIndex * 10, (workspace.pageIndex + 1) * 10) as application (application.id)}
                    <tr>
                        <td>
                            <strong>{application.title}</strong>
                            <small>{application.company.name}</small>
                        </td>
                        <td>{money(application.requestedAmount)}</td>
                        <td>
                            <span
                                class="badge"
                                class:good={application.state === 'SUBMITTED'}
                            >
                                {workspace.applicationStatus(application)}
                            </span>
                        </td>
                        <td>
                            <div class="actions">
                                <button
                                    class="secondary"
                                    onclick={() =>
                                        workspace.openEditor(
                                            'application',
                                            application,
                                        )}
                                >
                                    Otwórz
                                </button>
                                {#if application.state === 'DRAFT'}
                                    <button
                                        class="quiet danger"
                                        onclick={() =>
                                            workspace.remove(
                                                'application',
                                                application,
                                            )}
                                    >
                                        Usuń
                                    </button>
                                {:else if !workspace.linkedEvaluations.some((evaluation) => evaluation.applicationId === application.id)}
                                    <button
                                        class="primary"
                                        onclick={() =>
                                            workspace.startEvaluation(
                                                application,
                                            )}
                                    >
                                        Oceń
                                    </button>
                                {/if}
                            </div>
                        </td>
                    </tr>
                {/each}
            </tbody>
        </table>
        <div class="pagination">
            <button
                class="quiet"
                disabled={workspace.pageIndex === 0}
                onclick={() => workspace.pageIndex--}
            >
                Poprzednia
            </button>
            <span>
                Strona {workspace.pageIndex + 1} z {Math.ceil(
                    workspace.filteredApplications.length / 10,
                )}
            </span>
            <button
                class="quiet"
                disabled={(workspace.pageIndex + 1) * 10 >=
                    workspace.filteredApplications.length}
                onclick={() => workspace.pageIndex++}
            >
                Następna
            </button>
        </div>
    </section>
{/if}
