<script lang="ts">
    import { onMount } from 'svelte';
    import { navigation } from '../lib/navigation';
    import { Workspace } from '../lib/workspace.svelte';
    import DocumentView from '../lib/views/DocumentView.svelte';
    import Overview from '../lib/views/Overview.svelte';
    import Settings from '../lib/views/Settings.svelte';
    import Applications from '../lib/views/Applications.svelte';
    import Evaluations from '../lib/views/Evaluations.svelte';
    import Funding from '../lib/views/Funding.svelte';
    import Decisions from '../lib/views/Decisions.svelte';
    import Reports from '../lib/views/Reports.svelte';

    const workspace = new Workspace();
    onMount(workspace.initialize);
</script>

{#snippet feedback()}
    {#if workspace.error}
        <div class="error" role="alert">
            {workspace.error}
            <button class="quiet" onclick={workspace.refresh}>
                Odśwież dane
            </button>
        </div>
    {/if}
    {#if workspace.message}
        <p class="success" role="status">
            {workspace.message}
        </p>
    {/if}
    {#if workspace.loading}
        <p class="notice" role="status">Wczytywanie dokumentów...</p>
    {/if}
{/snippet}

<svelte:head>
    <title>CloudMigration</title>
    <meta
        name="description"
        content="Obsługa wniosków, ocen i finansowania projektów migracji do chmury."
    />
</svelte:head>
<div class="workspace">
    <header class="sidebar">
        <strong>CloudMigration</strong>
        <nav aria-label="Główna nawigacja">
            {#each navigation as item (item.id)}
                <button
                    class:active={workspace.view === item.id}
                    aria-current={workspace.view === item.id
                        ? 'page'
                        : undefined}
                    onclick={() => workspace.navigate(item.id)}
                >
                    {item.label}
                </button>
            {/each}
        </nav>
        <a href="/swagger-ui.html" target="_blank" rel="external noreferrer">
            API
        </a>
    </header>
    <main class="main">
        <header class="topbar">
            <h1>
                {navigation.find((section) => section.id === workspace.view)
                    ?.label}
            </h1>
        </header>
        {#if workspace.editor || workspace.view !== 'decisions'}
            {@render feedback()}
        {/if}
        {#if workspace.editor}
            <DocumentView {workspace} editor={workspace.editor} />
        {:else if workspace.view === 'overview' && workspace.competition}
            <Overview {workspace} competition={workspace.competition} />
        {:else if workspace.view === 'settings' && workspace.competition}
            <Settings {workspace} competition={workspace.competition} />
        {:else if workspace.view === 'applications'}
            <Applications {workspace} />
        {:else if workspace.view === 'evaluations'}
            <Evaluations {workspace} />
        {:else if workspace.view === 'funding'}
            <Funding {workspace} />
        {:else if workspace.view === 'decisions'}
            <Decisions {workspace} {feedback} />
        {:else if workspace.view === 'reports'}
            <Reports {workspace} />
        {/if}
    </main>
</div>
