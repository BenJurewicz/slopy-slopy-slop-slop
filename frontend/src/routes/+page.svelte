<script lang="ts">
	import { onMount } from 'svelte';
	import Editor from '../lib/components/Editor.svelte';
	import { api, money, status, type Competition, type Application, type Evaluation, type Decision, type Kind, type Query, type Preview } from '../lib/api';
	import { newCompetition, newApplication, newEvaluation, type Data } from '../lib/forms';
	const reportLabels:Record<string,string>={applications:'Wnioski',evaluations:'Oceny',averageScore:'Średnia punktów',awarded:'Przyznane środki',remaining:'Pozostałe środki',applicationId:'Wniosek',evaluationId:'Ocena',title:'Projekt',company:'Firma',outcome:'Wynik',amount:'Kwota',requestedAmount:'Wnioskowana kwota',budgetAllows:'Budżet pozwala na dotację',remainingAfter:'Pozostały budżet',qualified:'Zakwalifikowany',score:'Punkty',provider:'Dostawca chmury',projects:'Projekty',state:'Status',id:'Identyfikator'};
	type View = 'overview' | 'competitions' | 'applications' | 'evaluations' | 'funding' | 'decisions' | 'reports';
	const nav: {id:View;label:string}[] = [{id:'overview',label:'Przegląd'},{id:'applications',label:'Wnioski'},{id:'evaluations',label:'Oceny'},{id:'funding',label:'Ranking i dotacje'},{id:'decisions',label:'Decyzje'},{id:'reports',label:'Raporty XQuery'},{id:'competitions',label:'Konkursy'}];
	let view = $state<View>('overview');let loading = $state(true);let busy = $state(false);let error = $state('');let message = $state('');
	let competitions = $state<Competition[]>([]);let applications = $state<Application[]>([]);let evaluations = $state<Evaluation[]>([]);let decisions = $state<Decision[]>([]);let queries = $state<Query[]>([]);
	let competitionId = $state('');let search = $state('');let filter = $state('');let page = $state(0);
	let editor = $state<{kind:Kind;value:Data}|null>(null);let preview = $state<Preview|null>(null);
	let summaries = $state<Record<string,Record<string,unknown>>>({});
	let queryId = $state('summary');let threshold = $state('0');let report = $state<{total:number;rows:Record<string,unknown>[]} | null>(null);
	const competition = $derived(competitions.find(c=>c.id===competitionId));
	const apps = $derived(applications.filter(a=>a.competitionId===competitionId));
	const appIds = $derived(new Set(apps.map(a=>a.id)));
	const evals = $derived(evaluations.filter(e=>appIds.has(e.applicationId)));
	const decs = $derived(decisions.filter(d=>appIds.has(d.applicationId)));
	const filtered = $derived(apps.filter(a=>(!filter||a.state===filter)&&`${a.title} ${a.company.name}`.toLocaleLowerCase('pl').includes(search.toLocaleLowerCase('pl'))));

	// Financial totals come from the parameterized BaseX summary query.
	const awardedDisplay = $derived(String(summaries[competitionId]?.awarded??'0'));
	const remainingDisplay = $derived(String(summaries[competitionId]?.remaining??competition?.budget??'0'));
	const selectedQuery = $derived(queries.find(q=>q.id===queryId));
	const selectedApp = $derived(editor?.kind==='application' ? applications.find(a=>a.id===editor?.value.id) : undefined);
	onMount(() => {
        const route=window.location.pathname.slice(1);
        if(nav.some(item=>item.id===route))view=route as View;
        void refresh();
    });
	async function refresh() {
		loading=true;error='';
		try {
			const [c,a,e,d,q] = await Promise.all([api.listCompetitions(),api.listApplications(),api.listEvaluations(),api.listDecisions(),api.listQueries()]);
			competitions=c;applications=a;evaluations=e;decisions=d;queries=q;
			summaries=Object.fromEntries(await Promise.all(c.map(async item=>[item.id,(await api.executeQuery('summary',{competitionId:item.id,threshold:'0',offset:0,limit:1})).rows[0]])));
			if(!competitions.some(c=>c.id===competitionId))competitionId=c[0]?.id??'';
		} catch(e){error=(e as Error).message;}finally{loading=false;}
	}
	function navigate(next:View){view=next;editor=null;error='';message='';preview=null;report=null;page=0;}
	async function run(action:()=>Promise<unknown>,success:string){
		busy=true;error='';message='';try{await action();await refresh();message=success;}catch(e){error=(e as Error).message;}finally{busy=false;}
	}
	function open(kind:Kind,value:object){editor={kind,value:structuredClone($state.snapshot(value)) as Data};message='';error='';window.scrollTo({top:0,behavior:'smooth'});}
	async function save(value:Data){
		if(!editor)return;busy=true;message='';
		try{const saved=await api.saveDocument(editor.kind,value);if(editor.kind==='competition')competitionId=String(saved.id);editor=null;await refresh();message='Dokument zapisany.';}finally{busy=false;}
	}
	async function remove(kind:Kind,value:{id:string;revision:number}){
		if(!window.confirm('Usunąć szkic? Tej operacji nie można cofnąć.'))return;
		await run(()=>api.removeDocument(kind,value.id,value.revision),'Szkic usunięty.');
	}
	async function submit(a:Application){if(!window.confirm('Złożyć wniosek? Po złożeniu nie będzie można zmienić danych.'))return;await run(()=>api.submitApplication(a.id,a.revision),'Wniosek złożony.');editor=null;}
	async function finalize(e:Evaluation){if(!window.confirm('Zatwierdzić ocenę? Punktacja zostanie zablokowana.'))return;await run(()=>api.finalizeEvaluation(e.id,e.revision),'Ocena zatwierdzona.');}
	function evaluate(a:Application){const c=competitions.find(c=>c.id===a.competitionId);if(c){view='evaluations';open('evaluation',newEvaluation(a,c));}}
	async function upload(event:Event){
		const input=event.currentTarget as HTMLInputElement;const file=input.files?.[0];if(!file)return;
		if(!competitionId){error='Wybierz konkurs przed importem wniosku.';input.value='';return;}
		busy=true;error='';message='';try{const result=await api.importApplication(file,competitionId);const data={...result.application,id:undefined,revision:0};open('application',data);message=result.diagnostics.join(' ');}catch(e){error=(e as Error).message;}finally{busy=false;input.value='';}
	}
	async function loadPreview(){busy=true;error='';try{preview=await api.previewFunding(competitionId);}catch(e){error=(e as Error).message;}finally{busy=false;}}
	async function issue(){
		const draft=decs.filter(d=>d.state==='DRAFT');if(!draft.length)return;
		if(!window.confirm(`Wydać ${draft.length} decyzji? Wydanych decyzji nie można edytować.`))return;
		await run(()=>api.issueDecisions(draft.map(d=>({id:d.id,revision:d.revision}))),'Decyzje wydane.');preview=null;
	}
	async function executeQuery(){busy=true;error='';try{report=await api.executeQuery(queryId,{competitionId,threshold,offset:0,limit:500});}catch(e){error=(e as Error).message;}finally{busy=false;}}
	function title(id:string){return applications.find(a=>a.id===id)?.title??id;}
	function appState(a:Application){return decs.some(d=>d.applicationId===a.id&&d.state==='ISSUED')?'Decyzja wydana':evals.some(e=>e.applicationId===a.id&&e.state==='FINAL')?'Oceniony':status(a.state);}
</script>

<svelte:head><title>CloudMigration — konkursy grantowe</title><meta name="description" content="Obsługa wniosków, ocen i finansowania projektów migracji do chmury."/></svelte:head>
<div class="workspace">
	<header class="sidebar"><strong>CloudMigration</strong>
		<nav aria-label="Główna nawigacja">{#each nav as item}<button class:active={view===item.id} aria-current={view===item.id?'page':undefined} onclick={()=>navigate(item.id)}>{item.label}</button>{/each}</nav>
		<a href="/swagger-ui.html" target="_blank" rel="noreferrer">API</a>
	</header>
	<main class="main"><header class="topbar"><h1>{nav.find(n=>n.id===view)?.label}</h1><label><span>Konkurs</span><select aria-label="Aktywny konkurs" disabled={busy||loading} bind:value={competitionId} onchange={()=>{editor=null;preview=null;report=null;page=0;}}><option value="" disabled>Wybierz konkurs</option>{#each competitions as c}<option value={c.id}>{c.name}</option>{/each}</select></label></header>
		{#if error}<div class="error" role="alert">{error} <button class="quiet" onclick={refresh}>Odśwież dane</button></div>{/if}
		{#if message}<p class="success" role="status">{message}</p>{/if}
		{#if loading}<p class="notice" role="status">Wczytywanie dokumentów…</p>{/if}
		{#if editor}
			{#if selectedApp}<div class="toolbar"><span class="badge">{appState(selectedApp)}</span><div class="actions"><a href={`/api/documents/application/${selectedApp.id}/xml`}>Pobierz XML</a><a href={`/api/documents/application/${selectedApp.id}/docx`}>Pobierz DOCX</a>{#if selectedApp.state==='DRAFT'}<button class="primary" disabled={busy} onclick={()=>submit(selectedApp!)}>Złóż wniosek</button>{:else if !evals.some(e=>e.applicationId===selectedApp?.id)}<button class="primary" onclick={()=>evaluate(selectedApp!)}>Oceń wniosek</button>{/if}</div></div>{/if}
			{#if editor.kind==='evaluation'&&competition}<p class="notice">{competition.criteria.map(c=>`${c.name}: maks. ${c.maximum} pkt`).join(' · ')}</p>{/if}
            {#if selectedApp}<div class="actions">{#each evals.filter(e=>e.applicationId===selectedApp?.id) as e}<button class="secondary" onclick={()=>open('evaluation',e)}>Powiązana ocena</button>{/each}{#each decs.filter(d=>d.applicationId===selectedApp?.id) as d}<button class="secondary" onclick={()=>open('decision',d)}>Powiązana decyzja</button>{/each}</div>{/if}
            {#key `${editor.kind}-${editor.value.id??'new'}`}<Editor kind={editor.kind} value={editor.value} {save} close={()=>editor=null} {busy}/>{/key}
		{:else if !loading && competitions.length===0 && view!=='competitions'}
			<section class="empty"><h2>Brak konkursów</h2><div class="actions"><button onclick={()=>{view='competitions';open('competition',newCompetition());}}>Utwórz konkurs</button><button disabled={busy} onclick={()=>run(()=>api.loadDemo(),'Wczytano dane demonstracyjne.')}>Wczytaj przykład</button></div></section>
		{:else if view==='overview' && competition}
			<dl class="summary">
				<dt>Etap</dt><dd>{status(competition.stage)}</dd>
				<dt>Pozostały budżet</dt><dd>{money(remainingDisplay)}</dd>
				<dt>Przyznane środki</dt><dd>{money(awardedDisplay)}</dd>
				<dt>Wnioski</dt><dd>{apps.length}</dd>
				<dt>Szkice wniosków</dt><dd>{apps.filter(a=>a.state==='DRAFT').length}</dd>
				<dt>Oceny końcowe</dt><dd>{evals.filter(e=>e.state==='FINAL').length}</dd>
				<dt>Wydane decyzje</dt><dd>{decs.filter(d=>d.state==='ISSUED').length}</dd>
			</dl>
		{:else if view==='competitions'}
			<div class="toolbar"><button class="primary" onclick={()=>open('competition',newCompetition())}>Nowy konkurs</button></div>
			<section class="panel table-scroll"><table><thead><tr><th>Konkurs</th><th>Budżet</th><th>Limit dotacji</th><th>Etap</th><th>Działania</th></tr></thead><tbody>{#each competitions as c}<tr><td><strong>{c.name}</strong><small>Próg: {c.threshold} pkt</small></td><td>{money(c.budget)}</td><td>{money(c.maxGrant)}</td><td><span class="badge">{status(c.stage)}</span></td><td><div class="actions"><button class="secondary" onclick={()=>open('competition',c)}>Edytuj</button><button class="quiet danger" disabled={busy} onclick={()=>remove('competition',c)}>Usuń</button></div></td></tr>{/each}</tbody></table></section><p class="footer-note">Po dodaniu wniosku można zmienić etap, ale nie budżet ani kryteria konkursu.</p>
		{:else if view==='applications'}
			<div class="toolbar"><div class="actions"><a class="secondary" href={`/api/documents/template?competitionId=${competitionId}`}>Pobierz pusty formularz DOCX</a><a class="secondary" href={`/api/documents/example?competitionId=${competitionId}`}>Zobacz wypełniony przykład</a><label class="secondary file-picker"><span>Importuj DOCX</span><input aria-label="Importuj DOCX" type="file" accept=".docx" disabled={busy||!competition} onchange={upload}/></label></div><button class="primary" disabled={!competition} onclick={()=>open('application',newApplication(competitionId))}>Nowy wniosek</button></div>

			<div class="toolbar"><input class="search" aria-label="Szukaj wniosków" placeholder="Szukaj projektu lub firmy…" bind:value={search} oninput={()=>page=0}/><select style="max-width:200px" aria-label="Status wniosku" bind:value={filter} onchange={()=>page=0}><option value="">Wszystkie statusy</option><option value="DRAFT">Szkice</option><option value="SUBMITTED">Złożone</option></select></div>
			{#if !filtered.length}<div class="empty"><p>Brak wniosków dla wybranych filtrów.</p></div>{:else}<section class="panel table-scroll"><table><thead><tr><th>Projekt / firma</th><th>Dotacja</th><th>Status</th><th>Działania</th></tr></thead><tbody>{#each filtered.slice(page*10,(page+1)*10) as a}<tr><td><strong>{a.title}</strong><small>{a.company.name}</small></td><td>{money(a.requestedAmount)}</td><td><span class="badge" class:good={a.state==='SUBMITTED'}>{appState(a)}</span></td><td><div class="actions"><button class="secondary" onclick={()=>open('application',a)}>Otwórz</button>{#if a.state==='DRAFT'}<button class="quiet danger" onclick={()=>remove('application',a)}>Usuń</button>{:else if !evals.some(e=>e.applicationId===a.id)}<button class="primary" onclick={()=>evaluate(a)}>Oceń</button>{/if}</div></td></tr>{/each}</tbody></table><div class="pagination"><button class="quiet" disabled={page===0} onclick={()=>page--}>Poprzednia</button><span>Strona {page+1} z {Math.ceil(filtered.length/10)}</span><button class="quiet" disabled={(page+1)*10>=filtered.length} onclick={()=>page++}>Następna</button></div></section>{/if}
		{:else if view==='evaluations'}

			{#if !evals.length}<div class="empty"><h2>Brak ocen</h2><button class="primary" onclick={()=>navigate('applications')}>Wybierz wniosek do oceny</button></div>{:else}<section class="panel table-scroll"><table><thead><tr><th>Projekt</th><th>Oceniający</th><th>Punkty</th><th>Status</th><th>Działania</th></tr></thead><tbody>{#each evals as e}<tr><td><strong>{title(e.applicationId)}</strong><small>{e.eligible?'Kwalifikowalny':'Niekwalifikowalny'}</small></td><td>{e.reviewer}</td><td>{e.total}</td><td><span class="badge" class:good={e.state==='FINAL'}>{status(e.state)}</span></td><td><div class="actions"><button class="secondary" onclick={()=>open('evaluation',e)}>Otwórz</button>{#if e.state==='DRAFT'}<button class="primary" disabled={busy} onclick={()=>finalize(e)}>Zatwierdź ocenę</button><button class="quiet danger" onclick={()=>remove('evaluation',e)}>Usuń</button>{/if}<a href={`/api/documents/evaluation/${e.id}/docx`}>DOCX</a><a href={`/api/documents/evaluation/${e.id}/xml`}>XML</a></div></td></tr>{/each}</tbody></table></section>{/if}
		{:else if view==='funding'}
			<div class="toolbar"><p class="muted">Tylko pełne dotacje; projekty ponad budżet są pomijane.</p><button class="primary" disabled={busy||!competition} onclick={loadPreview}>Przelicz ranking</button></div>
			{#if preview}<section class="panel"><header class="section-heading"><h2>Dostępne: {money(preview.available)}</h2><button class="primary" disabled={busy||!preview.rows.length} onclick={()=>run(()=>api.prepareDecisions(competitionId),'Przygotowano szkice decyzji.')}>Przygotuj decyzje</button></header><div class="table-scroll"><table><thead><tr><th>Miejsce / projekt</th><th>Punkty</th><th>Kwota</th><th>Propozycja</th><th>Powód</th></tr></thead><tbody>{#each preview.rows as row,index}<tr><td><strong>{index+1}. {row.title}</strong></td><td>{row.score}</td><td>{money(row.requestedAmount)}</td><td><span class="badge" class:good={row.outcome==='AWARD'}>{status(row.outcome)}</span></td><td>{row.reason}</td></tr>{/each}</tbody></table></div></section>{:else}<div class="empty"><p>Kliknij "Przelicz ranking", aby zobaczyć propozycje.</p></div>{/if}
		{:else if view==='decisions'}
			<div class="toolbar"><p class="muted">Szkice nie obciążają budżetu. Wydanie decyzji jest nieodwracalne.</p><button class="primary" disabled={busy||!decs.some(d=>d.state==='DRAFT')} onclick={issue}>Wydaj decyzje</button></div>
			{#if !decs.length}<div class="empty"><h2>Brak decyzji</h2><button class="primary" onclick={()=>navigate('funding')}>Przygotuj finansowanie</button></div>{:else}<section class="panel table-scroll"><table><thead><tr><th>Projekt</th><th>Wynik</th><th>Kwota</th><th>Status</th><th>Działania</th></tr></thead><tbody>{#each decs as d}<tr><td><strong>{title(d.applicationId)}</strong><small>{d.reason}</small></td><td><span class="badge" class:good={d.outcome==='AWARD'}>{status(d.outcome)}</span></td><td>{money(d.amount)}</td><td>{status(d.state)}</td><td><div class="actions"><button class="secondary" onclick={()=>open('decision',d)}>Otwórz</button>{#if d.state==='DRAFT'}<button class="quiet danger" onclick={()=>remove('decision',d)}>Usuń</button>{/if}<a href={`/api/documents/decision/${d.id}/docx`}>DOCX</a><a href={`/api/documents/decision/${d.id}/xml`}>XML</a></div></td></tr>{/each}</tbody></table></section>{/if}
		{:else if view==='reports'}
			<section class="panel"><div class="toolbar"><label><span>Zapytanie</span><select bind:value={queryId} onchange={()=>report=null}>{#each queries as q}<option value={q.id}>{q.name}</option>{/each}</select></label><label><span>Próg kwoty (PLN)</span><input bind:value={threshold}/></label><button class="primary" disabled={busy||!competition} onclick={executeQuery}>Wykonaj XQuery</button></div><details><summary>Pokaż kod XQuery</summary><pre class="query-code">{selectedQuery?.source}</pre></details></section>
			{#if report}<section class="panel"><h2>Wyniki: {report.total}</h2>{#if !report.rows.length}<p class="muted">Brak danych dla wybranych parametrów.</p>{:else}<div class="table-scroll"><table><thead><tr>{#each Object.keys(report.rows[0]) as key}<th>{reportLabels[key]??key}</th>{/each}</tr></thead><tbody>{#each report.rows as row}<tr>{#each Object.entries(row) as [key,value]}<td>{['awarded','remaining','remainingAfter','amount','requestedAmount'].includes(key)?money(String(value)):['state','outcome'].includes(key)?status(String(value)):typeof value==='boolean'?(value?'Tak':'Nie'):String(value)}</td>{/each}</tr>{/each}</tbody></table></div>{/if}</section>{/if}
		{/if}
	</main>
</div>
