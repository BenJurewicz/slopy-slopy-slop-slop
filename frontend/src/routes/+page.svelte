<script lang="ts">
    import { onMount } from 'svelte';
    import Editor from '../lib/components/Editor.svelte';
    import { api, money, status } from '../lib/api';
    import { newApplication } from '../lib/forms';
    import { navigation } from '../lib/navigation';
    import { reportLabels, formatReportCell } from '../lib/report-display';
    import { Workspace } from '../lib/workspace.svelte';

    const workspace = new Workspace();
    onMount(workspace.initialize);
</script>

<svelte:head
    ><title>CloudMigration</title><meta
        name="description"
        content="Obsługa wniosków, ocen i finansowania projektów migracji do chmury."
    /></svelte:head
>
<div class="workspace">
    <header class="sidebar">
        <strong>CloudMigration</strong>
        <nav aria-label="Główna nawigacja">
            {#each navigation as item (item.id)}<button
                    class:active={workspace.view === item.id}
                    aria-current={workspace.view === item.id
                        ? 'page'
                        : undefined}
                    onclick={() => workspace.navigate(item.id)}
                    >{item.label}</button
                >{/each}
        </nav>
        <a href="/swagger-ui.html" target="_blank" rel="external noreferrer"
            >API</a
        >
    </header>
    <main class="main">
        <header class="topbar">
            <h1>{navigation.find((n) => n.id === workspace.view)?.label}</h1>
        </header>
        {#if workspace.error}<div class="error" role="alert">
                {workspace.error}
                <button class="quiet" onclick={workspace.refresh}
                    >Odśwież dane</button
                >
            </div>{/if}
        {#if workspace.message}<p class="success" role="status">
                {workspace.message}
            </p>{/if}
        {#if workspace.loading}<p class="notice" role="status">
                Wczytywanie dokumentów...
            </p>{/if}
        {#if workspace.editor}
            {#if workspace.selectedApplication}<div class="toolbar">
                    <span class="badge"
                        >{workspace.applicationStatus(
                            workspace.selectedApplication,
                        )}</span
                    >
                    <div class="actions">
                        <a
                            rel="external"
                            href={`/api/documents/application/${workspace.selectedApplication.id}/xml`}
                            >Pobierz XML</a
                        ><a
                            rel="external"
                            href={`/api/documents/application/${workspace.selectedApplication.id}/docx`}
                            >Pobierz DOCX</a
                        >{#if workspace.selectedApplication.state === 'DRAFT'}<button
                                class="primary"
                                disabled={workspace.busy}
                                onclick={() =>
                                    workspace.submitApplication(
                                        workspace.selectedApplication!,
                                    )}>Złóż wniosek</button
                            >{:else if !workspace.linkedEvaluations.some((e) => e.applicationId === workspace.selectedApplication?.id)}<button
                                class="primary"
                                onclick={() =>
                                    workspace.startEvaluation(
                                        workspace.selectedApplication!,
                                    )}>Oceń wniosek</button
                            >{/if}
                    </div>
                </div>{/if}
            {#if workspace.editor.kind === 'evaluation' && workspace.competition}<p
                    class="notice"
                >
                    {workspace.competition.criteria
                        .map((c) => `${c.name}: maks. ${c.maximum} pkt`)
                        .join(' | ')}
                </p>{/if}
            {#if workspace.selectedApplication}<div class="actions">
                    {#each workspace.linkedEvaluations.filter((e) => e.applicationId === workspace.selectedApplication?.id) as e (e.id)}<button
                            class="secondary"
                            onclick={() =>
                                workspace.openEditor('evaluation', e)}
                            >Powiązana ocena</button
                        >{/each}{#each workspace.linkedDecisions.filter((d) => d.applicationId === workspace.selectedApplication?.id) as d (d.id)}<button
                            class="secondary"
                            onclick={() => workspace.openEditor('decision', d)}
                            >Powiązana decyzja</button
                        >{/each}
                </div>{/if}
            {#key `${workspace.editor.kind}-${workspace.editor.value.id ?? 'new'}`}<Editor
                    kind={workspace.editor.kind}
                    value={workspace.editor.value}
                    save={workspace.saveDocument}
                    close={() => (workspace.editor = null)}
                    busy={workspace.busy}
                />{/key}
        {:else if workspace.view === 'overview' && workspace.competition}
            <dl class="summary">
                <dt>Etap</dt>
                <dd>{status(workspace.competition.stage)}</dd>
                <dt>Pozostały budżet</dt>
                <dd>{money(workspace.remainingDisplay)}</dd>
                <dt>Przyznane środki</dt>
                <dd>{money(workspace.awardedDisplay)}</dd>
                <dt>Wnioski</dt>
                <dd>{workspace.applications.length}</dd>
                <dt>Szkice wniosków</dt>
                <dd>
                    {workspace.applications.filter((a) => a.state === 'DRAFT')
                        .length}
                </dd>
                <dt>Oceny końcowe</dt>
                <dd>
                    {workspace.linkedEvaluations.filter(
                        (e) => e.state === 'FINAL',
                    ).length}
                </dd>
                <dt>Wydane decyzje</dt>
                <dd>
                    {workspace.linkedDecisions.filter(
                        (d) => d.state === 'ISSUED',
                    ).length}
                </dd>
            </dl>
        {:else if workspace.view === 'settings' && workspace.competition}
            {#if !workspace.applications.length}<p class="notice">
                    Ustawienia można zmieniać do utworzenia pierwszego wniosku.
                </p>{/if}
            {#key workspace.competition.revision}<Editor
                    kind="competition"
                    settingsLocked={workspace.applications.length > 0}
                    value={{ ...workspace.competition }}
                    save={workspace.saveSettings}
                    close={() => workspace.navigate('overview')}
                    busy={workspace.busy}
                />{/key}
            <a
                rel="external"
                href={`/api/documents/competition/${workspace.competition.id}/xml`}
                >Pobierz ustawienia XML</a
            >
            <section>
                <h2>Dane testowe</h2>
                <div class="actions">
                    <button
                        disabled={workspace.busy || workspace.loading}
                        onclick={workspace.loadDemo}
                        >Wczytaj dane demonstracyjne</button
                    >
                    <button
                        class="danger"
                        disabled={workspace.busy || workspace.loading}
                        onclick={workspace.resetData}
                        >Usuń wszystkie wnioski, oceny i decyzje</button
                    >
                </div>
            </section>
        {:else if workspace.view === 'applications'}
            <div class="toolbar">
                <div class="actions">
                    <a
                        rel="external"
                        class="secondary"
                        href="/api/documents/template"
                        >Pobierz pusty formularz DOCX</a
                    ><a
                        rel="external"
                        class="secondary"
                        href="/api/documents/example"
                        >Zobacz wypełniony przykład</a
                    ><label class="secondary file-picker"
                        ><span>Importuj DOCX</span><input
                            aria-label="Importuj DOCX"
                            type="file"
                            accept=".docx"
                            disabled={workspace.busy || !workspace.competition}
                            onchange={workspace.importApplication}
                        /></label
                    >
                </div>
                <button
                    class="primary"
                    disabled={!workspace.competition}
                    onclick={() =>
                        workspace.openEditor('application', newApplication())}
                    >Nowy wniosek</button
                >
            </div>

            <div class="toolbar">
                <input
                    class="search"
                    aria-label="Szukaj wniosków"
                    placeholder="Szukaj projektu lub firmy..."
                    bind:value={workspace.searchText}
                    oninput={() => (workspace.pageIndex = 0)}
                /><select
                    style="max-width:200px"
                    aria-label="Status wniosku"
                    bind:value={workspace.statusFilter}
                    onchange={() => (workspace.pageIndex = 0)}
                    ><option value="">Wszystkie statusy</option><option
                        value="DRAFT">Szkice</option
                    ><option value="SUBMITTED">Złożone</option></select
                >
            </div>
            {#if !workspace.filteredApplications.length}<div class="empty">
                    <p>Brak wniosków dla wybranych filtrów.</p>
                </div>{:else}<section class="panel table-scroll">
                    <table>
                        <thead
                            ><tr
                                ><th>Projekt / firma</th><th>Dotacja</th><th
                                    >Status</th
                                ><th>Działania</th></tr
                            ></thead
                        ><tbody
                            >{#each workspace.filteredApplications.slice(workspace.pageIndex * 10, (workspace.pageIndex + 1) * 10) as a (a.id)}<tr
                                    ><td
                                        ><strong>{a.title}</strong><small
                                            >{a.company.name}</small
                                        ></td
                                    ><td>{money(a.requestedAmount)}</td><td
                                        ><span
                                            class="badge"
                                            class:good={a.state === 'SUBMITTED'}
                                            >{workspace.applicationStatus(
                                                a,
                                            )}</span
                                        ></td
                                    ><td
                                        ><div class="actions">
                                            <button
                                                class="secondary"
                                                onclick={() =>
                                                    workspace.openEditor(
                                                        'application',
                                                        a,
                                                    )}>Otwórz</button
                                            >{#if a.state === 'DRAFT'}<button
                                                    class="quiet danger"
                                                    onclick={() =>
                                                        workspace.remove(
                                                            'application',
                                                            a,
                                                        )}>Usuń</button
                                                >{:else if !workspace.linkedEvaluations.some((e) => e.applicationId === a.id)}<button
                                                    class="primary"
                                                    onclick={() =>
                                                        workspace.startEvaluation(
                                                            a,
                                                        )}>Oceń</button
                                                >{/if}
                                        </div></td
                                    ></tr
                                >{/each}</tbody
                        >
                    </table>
                    <div class="pagination">
                        <button
                            class="quiet"
                            disabled={workspace.pageIndex === 0}
                            onclick={() => workspace.pageIndex--}
                            >Poprzednia</button
                        ><span
                            >Strona {workspace.pageIndex + 1} z {Math.ceil(
                                workspace.filteredApplications.length / 10,
                            )}</span
                        ><button
                            class="quiet"
                            disabled={(workspace.pageIndex + 1) * 10 >=
                                workspace.filteredApplications.length}
                            onclick={() => workspace.pageIndex++}
                            >Następna</button
                        >
                    </div>
                </section>{/if}
        {:else if workspace.view === 'evaluations'}
            {#if !workspace.linkedEvaluations.length}<div class="empty">
                    <h2>Brak ocen</h2>
                    <button
                        class="primary"
                        onclick={() => workspace.navigate('applications')}
                        >Wybierz wniosek do oceny</button
                    >
                </div>{:else}<section class="panel table-scroll">
                    <table>
                        <thead
                            ><tr
                                ><th>Projekt</th><th>Oceniający</th><th
                                    >Punkty</th
                                ><th>Status</th><th>Działania</th></tr
                            ></thead
                        ><tbody
                            >{#each workspace.linkedEvaluations as e (e.id)}<tr
                                    ><td
                                        ><strong
                                            >{workspace.applicationTitle(
                                                e.applicationId,
                                            )}</strong
                                        ><small
                                            >{e.eligible
                                                ? 'Kwalifikowalny'
                                                : 'Niekwalifikowalny'}</small
                                        ></td
                                    ><td>{e.reviewer}</td><td>{e.total}</td><td
                                        ><span
                                            class="badge"
                                            class:good={e.state === 'FINAL'}
                                            >{status(e.state)}</span
                                        ></td
                                    ><td
                                        ><div class="actions">
                                            <button
                                                class="secondary"
                                                onclick={() =>
                                                    workspace.openEditor(
                                                        'evaluation',
                                                        e,
                                                    )}>Otwórz</button
                                            >{#if e.state === 'DRAFT'}<button
                                                    class="primary"
                                                    disabled={workspace.busy}
                                                    onclick={() =>
                                                        workspace.finalizeEvaluation(
                                                            e,
                                                        )}
                                                    >Zatwierdź ocenę</button
                                                ><button
                                                    class="quiet danger"
                                                    onclick={() =>
                                                        workspace.remove(
                                                            'evaluation',
                                                            e,
                                                        )}>Usuń</button
                                                >{/if}<a
                                                rel="external"
                                                href={`/api/documents/evaluation/${e.id}/docx`}
                                                >DOCX</a
                                            ><a
                                                rel="external"
                                                href={`/api/documents/evaluation/${e.id}/xml`}
                                                >XML</a
                                            >
                                        </div></td
                                    ></tr
                                >{/each}</tbody
                        >
                    </table>
                </section>{/if}
        {:else if workspace.view === 'funding'}
            <div class="toolbar">
                <p class="muted">
                    Tylko pełne dotacje; projekty ponad budżet są pomijane.
                </p>
                <button
                    class="primary"
                    disabled={workspace.busy || !workspace.competition}
                    onclick={workspace.loadFundingPreview}
                    >Przelicz ranking</button
                >
            </div>
            {#if workspace.fundingPreview}<section class="panel">
                    <header class="section-heading">
                        <h2>
                            Dostępne: {money(
                                workspace.fundingPreview.available,
                            )}
                        </h2>
                        <button
                            class="primary"
                            disabled={workspace.busy ||
                                !workspace.fundingPreview.rows.length}
                            onclick={() =>
                                workspace.runAction(
                                    () => api.prepareDecisions(),
                                    'Przygotowano szkice decyzji.',
                                )}>Przygotuj decyzje</button
                        >
                    </header>
                    <div class="table-scroll">
                        <table>
                            <thead
                                ><tr
                                    ><th>Miejsce / projekt</th><th>Punkty</th
                                    ><th>Kwota</th><th>Propozycja</th><th
                                        >Powód</th
                                    ></tr
                                ></thead
                            ><tbody
                                >{#each workspace.fundingPreview.rows as row, index (row.applicationId)}<tr
                                        ><td
                                            ><strong
                                                >{index + 1}. {row.title}</strong
                                            ></td
                                        ><td>{row.score}</td><td
                                            >{money(row.requestedAmount)}</td
                                        ><td
                                            ><span
                                                class="badge"
                                                class:good={row.outcome ===
                                                    'AWARD'}
                                                >{status(row.outcome)}</span
                                            ></td
                                        ><td>{row.reason}</td></tr
                                    >{/each}</tbody
                            >
                        </table>
                    </div>
                </section>{:else}<div class="empty">
                    <p>Kliknij "Przelicz ranking", aby zobaczyć propozycje.</p>
                </div>{/if}
        {:else if workspace.view === 'decisions'}
            <div class="toolbar">
                <p class="muted">
                    Szkice nie obciążają budżetu. Wydanie decyzji jest
                    nieodwracalne.
                </p>
                <button
                    class="primary"
                    disabled={workspace.busy ||
                        !workspace.linkedDecisions.some(
                            (d) => d.state === 'DRAFT',
                        )}
                    onclick={workspace.issueDecisions}>Wydaj decyzje</button
                >
            </div>
            {#if !workspace.linkedDecisions.length}<div class="empty">
                    <h2>Brak decyzji</h2>
                    <button
                        class="primary"
                        onclick={() => workspace.navigate('funding')}
                        >Przygotuj finansowanie</button
                    >
                </div>{:else}<section class="panel table-scroll">
                    <table>
                        <thead
                            ><tr
                                ><th>Projekt</th><th>Wynik</th><th>Kwota</th><th
                                    >Status</th
                                ><th>Działania</th></tr
                            ></thead
                        ><tbody
                            >{#each workspace.linkedDecisions as d (d.id)}<tr
                                    ><td
                                        ><strong
                                            >{workspace.applicationTitle(
                                                d.applicationId,
                                            )}</strong
                                        ><small>{d.reason}</small></td
                                    ><td
                                        ><span
                                            class="badge"
                                            class:good={d.outcome === 'AWARD'}
                                            >{status(d.outcome)}</span
                                        ></td
                                    ><td>{money(d.amount)}</td><td
                                        >{status(d.state)}</td
                                    ><td
                                        ><div class="actions">
                                            <button
                                                class="secondary"
                                                onclick={() =>
                                                    workspace.openEditor(
                                                        'decision',
                                                        d,
                                                    )}>Otwórz</button
                                            >{#if d.state === 'DRAFT'}<button
                                                    class="quiet danger"
                                                    onclick={() =>
                                                        workspace.remove(
                                                            'decision',
                                                            d,
                                                        )}>Usuń</button
                                                >{/if}<a
                                                rel="external"
                                                href={`/api/documents/decision/${d.id}/docx`}
                                                >DOCX</a
                                            ><a
                                                rel="external"
                                                href={`/api/documents/decision/${d.id}/xml`}
                                                >XML</a
                                            >
                                        </div></td
                                    ></tr
                                >{/each}</tbody
                        >
                    </table>
                </section>{/if}
        {:else if workspace.view === 'reports'}
            <section class="panel">
                <label
                    ><span>Raport</span><select
                        aria-label="Raport"
                        aria-describedby="report-description"
                        bind:value={workspace.selectedQueryId}
                        onchange={() => (workspace.report = null)}
                        >{#each workspace.queries as q (q.id)}<option
                                value={q.id}>{q.name}</option
                            >{/each}</select
                    ></label
                >
                <p id="report-description">
                    {workspace.selectedQuery?.description}
                </p>
                <div class="toolbar">
                    {#if workspace.selectedQueryId === 'over-budget'}<label
                            ><span>Dotacja większa niż (PLN)</span><input
                                inputmode="decimal"
                                placeholder="np. 50000"
                                bind:value={workspace.reportThreshold}
                                oninput={() => (workspace.report = null)}
                            /></label
                        >{/if}
                    <button
                        class="primary"
                        disabled={workspace.busy || !workspace.competition}
                        onclick={workspace.runReport}>Pokaż wyniki</button
                    >
                </div>
                <details>
                    <summary>Pokaż kod XQuery</summary>
                    <pre class="query-code">{workspace.selectedQuery
                            ?.source}</pre>
                </details>
            </section>
            {#if workspace.report}<section class="panel">
                    <h2>Wyniki: {workspace.report.total}</h2>
                    {#if !workspace.report.rows.length}<p class="muted">
                            Brak danych dla wybranych parametrów.
                        </p>{:else}<div class="table-scroll">
                            <table>
                                <thead
                                    ><tr
                                        >{#each Object.keys(workspace.report.rows[0]) as key (key)}<th
                                                >{reportLabels[key] ?? key}</th
                                            >{/each}</tr
                                    ></thead
                                ><tbody
                                    >{#each workspace.report.rows as row, index (index)}<tr
                                            >{#each Object.entries(row) as [key, value] (key)}<td
                                                    >{formatReportCell(
                                                        key,
                                                        value,
                                                    )}</td
                                                >{/each}</tr
                                        >{/each}</tbody
                                >
                            </table>
                        </div>{/if}
                </section>{/if}
        {/if}
    </main>
</div>
