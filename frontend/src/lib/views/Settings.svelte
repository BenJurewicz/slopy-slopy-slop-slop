<script lang="ts">
    import type { Workspace } from '../workspace.svelte';
    import type { Competition } from '../api';
    import Editor from '../components/Editor.svelte';

    let {
        workspace,
        competition,
    }: { workspace: Workspace; competition: Competition } = $props();
</script>

{#if !workspace.applications.length}<p class="notice">
        Ustawienia można zmieniać do utworzenia pierwszego wniosku.
    </p>{/if}
{#key competition.revision}<Editor
        kind="competition"
        settingsLocked={workspace.applications.length > 0}
        value={{ ...competition }}
        save={workspace.saveSettings}
        close={() => workspace.navigate('overview')}
        busy={workspace.busy}
    />{/key}
<a rel="external" href={`/api/documents/competition/${competition.id}/xml`}
    >Pobierz ustawienia XML</a
>
<section>
    <h2>Dane testowe</h2>
    <div class="actions">
        <button
            disabled={workspace.busy || workspace.loading}
            onclick={workspace.loadDemo}>Wczytaj dane demonstracyjne</button
        >
        <button
            class="danger"
            disabled={workspace.busy || workspace.loading}
            onclick={workspace.resetData}
            >Usuń wszystkie wnioski, oceny i decyzje</button
        >
    </div>
</section>
