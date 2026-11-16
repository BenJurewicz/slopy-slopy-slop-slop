<script lang="ts">
    import type { Workspace } from '../workspace.svelte';
    import { money, status } from '../api';

    let { workspace }: { workspace: Workspace } = $props();
</script>

<div class="toolbar">
    <p class="muted">
        Szkice nie obciążają budżetu. Wydanie decyzji jest nieodwracalne.
    </p>
    <button
        class="primary"
        disabled={workspace.busy ||
            !workspace.linkedDecisions.some(
                (decision) => decision.state === 'DRAFT',
            )}
        onclick={workspace.issueDecisions}>Wydaj decyzje</button
    >
</div>
{#if !workspace.linkedDecisions.length}<div class="empty">
        <h2>Brak decyzji</h2>
        <button class="primary" onclick={() => workspace.navigate('funding')}
            >Przygotuj finansowanie</button
        >
    </div>{:else}<section class="panel table-scroll">
        <table>
            <thead
                ><tr
                    ><th>Projekt</th><th>Wynik</th><th>Kwota</th><th>Status</th
                    ><th>Działania</th></tr
                ></thead
            ><tbody
                >{#each workspace.linkedDecisions as decision (decision.id)}<tr
                        ><td
                            ><strong
                                >{workspace.applicationTitle(
                                    decision.applicationId,
                                )}</strong
                            ><small>{decision.reason}</small></td
                        ><td
                            ><span
                                class="badge"
                                class:good={decision.outcome === 'AWARD'}
                                >{status(decision.outcome)}</span
                            ></td
                        ><td>{money(decision.amount)}</td><td
                            >{status(decision.state)}</td
                        ><td
                            ><div class="actions">
                                <button
                                    class="secondary"
                                    onclick={() =>
                                        workspace.openEditor(
                                            'decision',
                                            decision,
                                        )}>Otwórz</button
                                >{#if decision.state === 'DRAFT'}<button
                                        class="quiet danger"
                                        onclick={() =>
                                            workspace.remove(
                                                'decision',
                                                decision,
                                            )}>Usuń</button
                                    >{/if}<a
                                    rel="external"
                                    href={`/api/documents/decision/${decision.id}/docx`}
                                    >DOCX</a
                                ><a
                                    rel="external"
                                    href={`/api/documents/decision/${decision.id}/xml`}
                                    >XML</a
                                >
                            </div></td
                        ></tr
                    >{/each}</tbody
            >
        </table>
    </section>{/if}
