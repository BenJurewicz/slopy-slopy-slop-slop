<script lang="ts">
    import type { Workspace } from '../workspace.svelte';
    import { status } from '../api';

    let { workspace }: { workspace: Workspace } = $props();
</script>

{#if !workspace.linkedEvaluations.length}
    <div class="empty">
        <h2>Brak ocen</h2>
        <button
            class="primary"
            onclick={() => workspace.navigate('applications')}
        >
            Wybierz wniosek do oceny
        </button>
    </div>
{:else}
    <section class="panel table-scroll">
        <table>
            <thead>
                <tr>
                    <th>Projekt</th>
                    <th>Oceniający</th>
                    <th>Punkty</th>
                    <th>Status</th>
                    <th>Działania</th>
                </tr>
            </thead>
            <tbody>
                {#each workspace.linkedEvaluations as evaluation (evaluation.id)}
                    <tr>
                        <td>
                            <strong>
                                {workspace.applicationTitle(
                                    evaluation.applicationId,
                                )}
                            </strong>
                            <small>
                                {evaluation.eligible
                                    ? 'Kwalifikowalny'
                                    : 'Niekwalifikowalny'}
                            </small>
                        </td>
                        <td>{evaluation.reviewer}</td>
                        <td>{evaluation.total}</td>
                        <td>
                            <span
                                class="badge"
                                class:good={evaluation.state === 'FINAL'}
                            >
                                {status(evaluation.state)}
                            </span>
                        </td>
                        <td>
                            <div class="actions">
                                <button
                                    class="secondary"
                                    onclick={() =>
                                        workspace.openEditor(
                                            'evaluation',
                                            evaluation,
                                        )}
                                >
                                    Otwórz
                                </button>
                                {#if evaluation.state === 'DRAFT'}
                                    <button
                                        class="primary"
                                        disabled={workspace.busy}
                                        onclick={() =>
                                            workspace.finalizeEvaluation(
                                                evaluation,
                                            )}
                                    >
                                        Zatwierdź ocenę
                                    </button>
                                    <button
                                        class="quiet danger"
                                        onclick={() =>
                                            workspace.remove(
                                                'evaluation',
                                                evaluation,
                                            )}
                                    >
                                        Usuń
                                    </button>
                                {/if}
                                <a
                                    rel="external"
                                    href={`/api/documents/evaluation/${evaluation.id}/docx`}
                                >
                                    DOCX
                                </a>
                                <a
                                    rel="external"
                                    href={`/api/documents/evaluation/${evaluation.id}/xml`}
                                >
                                    XML
                                </a>
                            </div>
                        </td>
                    </tr>
                {/each}
            </tbody>
        </table>
    </section>
{/if}
