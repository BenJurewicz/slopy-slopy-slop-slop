<script lang="ts">
    import type { Workspace } from '../workspace.svelte';
    import type { Competition } from '../api';
    import { money, status } from '../api';

    let {
        workspace,
        competition,
    }: { workspace: Workspace; competition: Competition } = $props();
</script>

<dl class="summary">
    <dt>Etap</dt>
    <dd>{status(competition.stage)}</dd>
    <dt>Pozostały budżet</dt>
    <dd>{money(workspace.remainingDisplay)}</dd>
    <dt>Przyznane środki</dt>
    <dd>{money(workspace.awardedDisplay)}</dd>
    <dt>Wnioski</dt>
    <dd>{workspace.applications.length}</dd>
    <dt>Szkice wniosków</dt>
    <dd>
        {workspace.applications.filter(
            (application) => application.state === 'DRAFT',
        ).length}
    </dd>
    <dt>Oceny końcowe</dt>
    <dd>
        {workspace.linkedEvaluations.filter(
            (evaluation) => evaluation.state === 'FINAL',
        ).length}
    </dd>
    <dt>Wydane decyzje</dt>
    <dd>
        {workspace.linkedDecisions.filter(
            (decision) => decision.state === 'ISSUED',
        ).length}
    </dd>
</dl>
