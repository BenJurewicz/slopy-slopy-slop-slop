import { test, expect } from '@playwright/test';
import { writeFile } from 'node:fs/promises';

test('DOCX upload → edit → submit → evaluate → issue → export', async ({ page, request }, info) => {
    const c=await request.post('/api/competitions',{data:{revision:0,name:'Próba przeglądarkowa',description:'Migracja mikrofirm do chmury',currency:'PLN',budget:'250000.00',maxGrant:'150000.00',threshold:60,stage:'OPEN',criteria:[{code:'technical',name:'Wykonalność',maximum:100}]}});
    expect(c.status()).toBe(201);const competition=await c.json();
    const template=await request.get(`/api/documents/template?competitionId=${competition.id}`);
    expect(template.ok()).toBeTruthy();const input=info.outputPath('application.docx');await writeFile(input,await template.body());
    page.on('dialog',dialog=>dialog.accept());
    await page.goto('/');await page.getByRole('button',{name:'Wnioski',exact:true}).click();
    await page.getByLabel('Importuj DOCX').setInputFiles(input);
    await expect(page.getByLabel('Nazwa firmy')).toHaveValue('Biuro Rachunkowe Żółw');
    await page.getByLabel('Tytuł projektu').fill('Projekt przeglądarkowy');
    await page.getByRole('button',{name:'Zapisz szkic'}).click();
    await expect(page.getByRole('status').filter({hasText:'Dokument zapisany'})).toBeVisible();
    await page.getByRole('button',{name:'Otwórz',exact:true}).click();
    const xmlDownload=page.waitForEvent('download');await page.getByRole('link',{name:'Pobierz XML',exact:true}).click();expect((await xmlDownload).suggestedFilename()).toMatch(/\.xml$/);
    await page.getByRole('button',{name:'Złóż wniosek'}).click();
    await expect(page.getByRole('status').filter({hasText:'Wniosek złożony'})).toBeVisible();
    await page.getByRole('button',{name:'Oceń',exact:true}).click();
    await page.getByLabel('Oceniający').fill('Jan Recenzent');await page.getByLabel('Uzasadnienie kwalifikacji').fill('Mikroprzedsiębiorstwo spełnia warunki');
    await page.getByLabel('Punkty',{exact:true}).fill('80');await page.getByLabel('Uzasadnienie',{exact:true}).fill('Projekt wykonalny i bezpieczny');
    await page.getByRole('button',{name:'Zapisz szkic'}).click();await expect(page.getByRole('status').filter({hasText:'Dokument zapisany'})).toBeVisible();
    await page.getByRole('button',{name:'Zatwierdź ocenę'}).click();await expect(page.getByRole('status').filter({hasText:'Ocena zatwierdzona'})).toBeVisible();
    await page.getByRole('button',{name:'Ranking i dotacje',exact:true}).click();await page.getByRole('button',{name:'Przelicz ranking'}).click();
    await expect(page.getByText('Przyznano',{exact:true})).toBeVisible();
    await page.getByRole('button',{name:'Przygotuj decyzje'}).click();await expect(page.getByRole('status').filter({hasText:'Przygotowano'})).toBeVisible();
    await page.getByRole('button',{name:'Decyzje',exact:true}).click();await page.getByRole('button',{name:'Wydaj decyzje'}).click();await expect(page.getByRole('status').filter({hasText:'Decyzje wydane'})).toBeVisible();
    await expect(page.getByRole('cell',{name:'Wydana',exact:true})).toBeVisible();
    const docxDownload=page.waitForEvent('download');await page.getByRole('link',{name:'DOCX',exact:true}).click();expect((await docxDownload).suggestedFilename()).toMatch(/\.docx$/);
    await page.getByRole('button',{name:'Raporty XQuery',exact:true}).click();await page.getByRole('button',{name:'Wykonaj XQuery'}).click();await expect(page.getByRole('heading',{name:'Wyniki: 1'})).toBeVisible();
    await page.screenshot({path:info.outputPath('desktop.png'),fullPage:true});
    await page.setViewportSize({width:390,height:844});await page.getByRole('button',{name:'Wnioski',exact:true}).click();await page.screenshot({path:info.outputPath('mobile.png'),fullPage:true});
    expect(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth)).toBeTruthy();
});

test('HTTP failures stay JSON/404 and bad uploads are actionable',async({page,request})=>{
    expect((await request.get('/api/not-an-endpoint')).status()).toBe(404);
    expect((await request.get('/_app/immutable/missing.js')).status()).toBe(404);
    await page.goto('/');await page.getByRole('button',{name:'Wnioski',exact:true}).click();
    await page.getByLabel('Importuj DOCX').setInputFiles({name:'invalid.docx',mimeType:'application/vnd.openxmlformats-officedocument.wordprocessingml.document',buffer:Buffer.from('not a docx')});
    await expect(page.getByRole('alert')).toContainText('DOCX');
});

test('draft validation and stale revisions leave saved data unchanged',async({page,request},info)=>{
    const competitions=await (await request.get('/api/competitions')).json();const competition=competitions[0];
    const template=await request.get(`/api/documents/template?competitionId=${competition.id}`);const input=info.outputPath('draft.docx');await writeFile(input,await template.body());
    await page.goto('/');await page.getByRole('button',{name:'Wnioski',exact:true}).click();await page.getByLabel('Importuj DOCX').setInputFiles(input);
    await expect(page.getByLabel('Tytuł projektu')).toBeVisible();await page.getByLabel('Tytuł projektu').fill('Sprawdzanie konfliktu');
    await page.getByRole('button',{name:'Zapisz szkic'}).click();await expect(page.getByRole('status').filter({hasText:'Dokument zapisany'})).toBeVisible();
    await page.getByRole('row').filter({hasText:'Sprawdzanie konfliktu'}).getByRole('button',{name:'Otwórz'}).click();
    await page.getByLabel('Wnioskowana dotacja (PLN)').fill('999999.00');await page.getByRole('button',{name:'Zapisz szkic'}).click();await expect(page.getByRole('alert')).toContainText('Dotacja');
    await page.getByLabel('Wnioskowana dotacja (PLN)').fill('50000.00');
    const apps=await (await request.get('/api/applications')).json();const draft=apps.find((a:{title:string})=>a.title==='Sprawdzanie konfliktu');
    const changed=await request.put(`/api/applications/${draft.id}`,{data:{...draft,title:'Zmiana w drugiej karcie'}});expect(changed.ok()).toBeTruthy();
    await page.getByRole('button',{name:'Zapisz szkic'}).click();await expect(page.getByRole('alert')).toContainText('Odśwież');
    const current=await (await request.get(`/api/applications/${draft.id}`)).json();expect(current.title).toBe('Zmiana w drugiej karcie');
    expect(current.requestedAmount).toBe('50000.00');
});

test('competition creation and deletion work from keyboard-accessible forms',async({page})=>{
    page.on('dialog',dialog=>dialog.accept());
    await page.goto('/');await page.getByRole('button',{name:'Konkursy',exact:true}).click();
    await page.getByRole('button',{name:'Nowy konkurs',exact:true}).focus();await page.keyboard.press('Enter');
    await page.getByLabel('Nazwa konkursu').fill('Konkurs utworzony w formularzu');await page.getByLabel('Nazwa konkursu').press('Tab');
    await expect(page.getByLabel('Opis programu')).toBeFocused();await page.getByLabel('Opis programu').fill('Program wsparcia mikrofirm');
    await page.getByRole('button',{name:'Zapisz szkic'}).click();await expect(page.getByRole('status').filter({hasText:'Dokument zapisany'})).toBeVisible();
    const row=page.getByRole('row').filter({hasText:'Konkurs utworzony w formularzu'});await expect(row).toBeVisible();
    await row.getByRole('button',{name:'Usuń',exact:true}).click();await expect(page.getByRole('status').filter({hasText:'Szkic usunięty'})).toBeVisible();await expect(row).toHaveCount(0);
});

test('known SPA routes render correctly without swallowing API or asset paths',async({page})=>{
    for(const [route,heading] of [['applications','Wnioski'],['evaluations','Oceny'],['funding','Ranking i dotacje'],['decisions','Decyzje'],['reports','Raporty XQuery'],['competitions','Konkursy']]){
        await page.goto('/'+route);await expect(page.getByRole('heading',{name:heading,exact:true,level:1})).toBeVisible();
    }
});
