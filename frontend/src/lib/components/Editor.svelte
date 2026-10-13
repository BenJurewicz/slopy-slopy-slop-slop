<script lang="ts">
	import Fields from './Fields.svelte';
	import { fields, type Data } from '../forms';
	import { api, type Kind } from '../api';
	let { kind, value, save, close, busy = false }: { kind: Kind; value: Data; save: (value:Data)=>Promise<void>; close:()=>void; busy?:boolean } = $props();
	let draft = $state<Data>({});
	$effect(() => { draft = structuredClone($state.snapshot(value)); });
	let error = $state('');let calculating = $state(false);
		$effect(() => { if(kind==='evaluation' && draft.scores)draft.total=(draft.scores as {points:number}[]).reduce((sum,s)=>sum+s.points,0); });
	const readonly = $derived(value.state && value.state !== 'DRAFT');
	async function calculate() {
		calculating=true;error='';
		try { const result=await api.calculateBudget(draft.budget as import('../generated/api/types.gen').BudgetLine[]);draft={...draft,budget:result.lines}; }
		catch(e){error=(e as Error).message;}finally{calculating=false;}
	}
	async function submit(event:SubmitEvent) {
		event.preventDefault();error='';
		if(kind==='evaluation')draft={...draft,total:(draft.scores as {points:number}[]).reduce((sum,s)=>sum+s.points,0)};
		try {await save(draft);}catch(e){error=(e as Error).message;}
	}
</script>
<section class="panel editor">
	<header class="section-heading"><div><p class="eyebrow">{value.id?'Dokument':'Nowy dokument'}</p><h2>{({competition:'Konkurs',application:'Wniosek',evaluation:'Ocena',decision:'Decyzja'})[kind]}</h2></div><button class="quiet" onclick={close}>Zamknij</button></header>
	{#if readonly}<p class="notice">Dokument został zatwierdzony. Dane są dostępne tylko do odczytu.</p>{/if}
	{#if error}<p class="error" role="alert">{error}</p>{/if}
	<form onsubmit={submit}>
		<Fields bind:value={draft} spec={fields[kind]} disabled={Boolean(readonly)||busy}/>
		{#if kind==='application'&&!readonly}<div class="notice">Kwoty netto w PLN. Po zmianie ilości lub kosztów przelicz pozycje budżetu. <button type="button" class="secondary" disabled={calculating||busy} onclick={calculate}>{calculating?'Przeliczanie…':'Przelicz budżet'}</button></div>{/if}
		{#if !readonly}<div class="form-actions"><button class="primary" disabled={busy||calculating} type="submit">{busy?'Zapisywanie…':'Zapisz szkic'}</button><button class="secondary" type="button" onclick={close}>Anuluj</button></div>{/if}
	</form>
</section>
