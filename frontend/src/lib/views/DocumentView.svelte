<script lang="ts">
    import type { Workspace } from '../workspace.svelte';
    import Editor from '../components/Editor.svelte';

    let {
        workspace,
        editor,
    }: { workspace: Workspace; editor: NonNullable<Workspace['editor']> } =
        $props();

    let dirty = $state(false);
</script>

{#if workspace.selectedApplication}
    <div class="toolbar">
        <span class="badge">
            {workspace.applicationStatus(workspace.selectedApplication)}
        </span>
        <div class="actions">
            <a
                rel="external"
                href={`/api/documents/application/${workspace.selectedApplication.id}/xml`}
            >
                Pobierz XML
            </a>
            <a
                rel="external"
                href={`/api/documents/application/${workspace.selectedApplication.id}/docx`}
            >
                Pobierz DOCX
            </a>
            {#if workspace.selectedApplication.state === 'DRAFT'}
                <button
                    class="primary"
                    disabled={workspace.busy || dirty}
                    onclick={() =>
                        workspace.submitApplication(
                            workspace.selectedApplication!,
                        )}
                >
                    Złóż wniosek
                </button>
            {:else if !workspace.linkedEvaluations.some((evaluation) => evaluation.applicationId === workspace.selectedApplication?.id)}
                <button
                    class="primary"
                    onclick={() =>
                        workspace.startEvaluation(
                            workspace.selectedApplication!,
                        )}
                >
                    Oceń wniosek
                </button>
            {/if}
        </div>
    </div>
    {#if workspace.selectedApplication.state === 'DRAFT' && dirty}
        <p class="notice">Zapisz szkic przed złożeniem wniosku.</p>
    {/if}
{/if}
{#if editor.kind === 'evaluation' && workspace.competition}
    <p class="notice">
        {workspace.competition.criteria
            .map(
                (criterion) =>
                    `${criterion.name}: maks. ${criterion.maximum} pkt`,
            )
            .join(' | ')}
    </p>
{/if}
{#if workspace.selectedApplication}
    <div class="actions">
        {#each workspace.linkedEvaluations.filter((evaluation) => evaluation.applicationId === workspace.selectedApplication?.id) as evaluation (evaluation.id)}
            <button
                class="secondary"
                onclick={() => workspace.openEditor('evaluation', evaluation)}
            >
                Powiązana ocena
            </button>
        {/each}{#each workspace.linkedDecisions.filter((decision) => decision.applicationId === workspace.selectedApplication?.id) as decision (decision.id)}
            <button
                class="secondary"
                onclick={() => workspace.openEditor('decision', decision)}
            >
                Powiązana decyzja
            </button>
        {/each}
    </div>
{/if}
{#key `${editor.kind}-${editor.value.id ?? 'new'}`}
    <Editor
        bind:dirty
        kind={editor.kind}
        value={editor.value}
        save={workspace.saveDocument}
        close={() => (workspace.editor = null)}
        busy={workspace.busy}
    />
{/key}
