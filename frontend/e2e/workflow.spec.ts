import { test, expect } from '@playwright/test';
import { writeFile } from 'node:fs/promises';

test('CloudMigration is initialized automatically and its initial settings are editable', async ({
    page,
    request,
}) => {
    const first = await (await request.get('/api/competition')).json();
    expect(first.name).toBe('CloudMigration');
    expect((await (await request.get('/api/competition')).json()).id).toBe(
        first.id,
    );
    await page.goto('/settings');
    await expect(
        page.getByLabel('Budżet konkursu (PLN)', { exact: true }),
    ).toBeEnabled();
    await page
        .getByLabel('Budżet konkursu (PLN)', { exact: true })
        .fill('260000.00');
    await page
        .getByRole('button', { name: 'Zapisz ustawienia', exact: true })
        .click();
    await expect(page.getByRole('status')).toContainText('Ustawienia zapisane');
    expect((await (await request.get('/api/competition')).json()).budget).toBe(
        '260000.00',
    );
    await page
        .getByLabel('Budżet konkursu (PLN)', { exact: true })
        .fill('250000.00');
    await page
        .getByRole('button', { name: 'Zapisz ustawienia', exact: true })
        .click();
    await expect
        .poll(
            async () =>
                (await (await request.get('/api/competition')).json()).budget,
        )
        .toBe('250000.00');
});

test('DOCX upload -> edit -> submit -> evaluate -> issue -> export', async ({
    page,
    request,
}, info) => {
    const initial = await (await request.get('/api/competition')).json();
    const c = await request.put('/api/competition', {
        data: {
            ...initial,
            criteria: [
                { code: 'technical', name: 'Wykonalność', maximum: 100 },
            ],
        },
    });
    expect(c.status()).toBe(200);
    const template = await request.get('/api/documents/example');
    expect(template.ok()).toBeTruthy();
    const input = info.outputPath('application.docx');
    await writeFile(input, await template.body());
    page.on('dialog', (dialog) => dialog.accept());
    await page.goto('/');
    await page.getByRole('button', { name: 'Wnioski', exact: true }).click();
    await page.getByLabel('Importuj DOCX').setInputFiles(input);
    await expect(page.getByLabel('Nazwa firmy')).toHaveValue(
        'Biuro Rachunkowe Żółw',
    );
    await expect(page.getByLabel('Adres beneficjenta')).toHaveValue(
        'ul. Przykładowa 12, 80-001 Gdańsk',
    );
    await expect(page.getByLabel('REGON', { exact: true })).toHaveValue(
        '123456785',
    );
    await expect(page.getByLabel('Początek realizacji projektu')).toHaveValue(
        '2026-11-01',
    );
    await expect(
        page.getByLabel('Całkowity koszt projektu (PLN)', { exact: true }),
    ).toHaveValue('50000.00');
    await page
        .getByLabel('Reprezentowany przez')
        .fill('Anna Kowalska - prezeska');
    await page
        .getByLabel('Numer projektu', { exact: true })
        .fill('PR/2026/BROWSER');
    await page.getByLabel('Wnioskowana dotacja (PLN)').fill('45000.00');
    await page.getByRole('button', { name: 'Przelicz budżet' }).click();
    await expect(
        page.getByLabel('Wkład własny Beneficjenta (PLN)', { exact: true }),
    ).toHaveValue('5000.00');
    await page.getByLabel('Tytuł projektu').fill('Projekt przeglądarkowy');
    await page.getByRole('button', { name: 'Zapisz szkic' }).click();
    await expect(
        page.getByRole('status').filter({ hasText: 'Dokument zapisany' }),
    ).toBeVisible();
    await page.getByRole('button', { name: 'Otwórz', exact: true }).click();
    await expect(
        page.getByLabel('Numer projektu', { exact: true }),
    ).toHaveValue('PR/2026/BROWSER');
    await expect(page.getByLabel('Reprezentowany przez')).toHaveValue(
        'Anna Kowalska - prezeska',
    );
    const xmlDownload = page.waitForEvent('download');
    await page.getByRole('link', { name: 'Pobierz XML', exact: true }).click();
    expect((await xmlDownload).suggestedFilename()).toMatch(/\.xml$/);
    await page.getByRole('button', { name: 'Złóż wniosek' }).click();
    await expect(
        page.getByRole('status').filter({ hasText: 'Wniosek złożony' }),
    ).toBeVisible();
    await page.getByRole('button', { name: 'Oceń', exact: true }).click();
    await page.getByLabel('Oceniający').fill('Jan Recenzent');
    await page
        .getByLabel('Uzasadnienie kwalifikacji')
        .fill('Mikroprzedsiębiorstwo spełnia warunki');
    await page.getByLabel('Punkty', { exact: true }).fill('80');
    await page
        .getByLabel('Uzasadnienie', { exact: true })
        .fill('Projekt wykonalny i bezpieczny');
    await page.getByRole('button', { name: 'Zapisz szkic' }).click();
    await expect(
        page.getByRole('status').filter({ hasText: 'Dokument zapisany' }),
    ).toBeVisible();
    await page.getByRole('button', { name: 'Zatwierdź ocenę' }).click();
    await expect(
        page.getByRole('status').filter({ hasText: 'Ocena zatwierdzona' }),
    ).toBeVisible();
    await page
        .getByRole('button', { name: 'Ranking i dotacje', exact: true })
        .click();
    await page.getByRole('button', { name: 'Przelicz ranking' }).click();
    await expect(page.getByText('Przyznano', { exact: true })).toBeVisible();
    await page.getByRole('button', { name: 'Przygotuj decyzje' }).click();
    await expect(
        page.getByRole('status').filter({ hasText: 'Przygotowano' }),
    ).toBeVisible();
    await page.getByRole('button', { name: 'Decyzje', exact: true }).click();
    await page.getByRole('button', { name: 'Wydaj decyzje' }).click();
    await expect(
        page.getByRole('status').filter({ hasText: 'Decyzje wydane' }),
    ).toBeVisible();
    await expect(
        page.getByRole('cell', { name: 'Wydana', exact: true }),
    ).toBeVisible();
    const docxDownload = page.waitForEvent('download');
    await page.getByRole('link', { name: 'DOCX', exact: true }).click();
    expect((await docxDownload).suggestedFilename()).toMatch(/\.docx$/);
    await page
        .getByRole('button', { name: 'Raporty XQuery', exact: true })
        .click();
    await page.getByRole('button', { name: 'Pokaż wyniki' }).click();
    await expect(
        page.getByRole('heading', { name: 'Wyniki: 1' }),
    ).toBeVisible();
    await page.screenshot({
        path: info.outputPath('desktop.png'),
        fullPage: true,
    });
    await page.setViewportSize({ width: 390, height: 844 });
    await page.getByRole('button', { name: 'Wnioski', exact: true }).click();
    await page.screenshot({
        path: info.outputPath('mobile.png'),
        fullPage: true,
    });
    expect(
        await page.evaluate(
            () => document.documentElement.scrollWidth <= window.innerWidth,
        ),
    ).toBeTruthy();
});

test('HTTP failures stay JSON/404 and bad uploads are actionable', async ({
    page,
    request,
}) => {
    expect((await request.get('/api/not-an-endpoint')).status()).toBe(404);
    expect((await request.get('/_app/immutable/missing.js')).status()).toBe(
        404,
    );
    await page.goto('/');
    await page.getByRole('button', { name: 'Wnioski', exact: true }).click();
    await page.getByLabel('Importuj DOCX').setInputFiles({
        name: 'invalid.docx',
        mimeType:
            'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
        buffer: Buffer.from('not a docx'),
    });
    await expect(page.getByRole('alert')).toContainText('DOCX');
});

test('draft validation and stale revisions leave saved data unchanged', async ({
    page,
    request,
}, info) => {
    const template = await request.get('/api/documents/example');
    const input = info.outputPath('draft.docx');
    await writeFile(input, await template.body());
    await page.goto('/');
    await page.getByRole('button', { name: 'Wnioski', exact: true }).click();
    await page.getByLabel('Importuj DOCX').setInputFiles(input);
    await expect(page.getByLabel('Tytuł projektu')).toBeVisible();
    await page.getByLabel('Tytuł projektu').fill('Sprawdzanie konfliktu');
    await page.getByRole('button', { name: 'Zapisz szkic' }).click();
    await expect(
        page.getByRole('status').filter({ hasText: 'Dokument zapisany' }),
    ).toBeVisible();
    await page
        .getByRole('row')
        .filter({ hasText: 'Sprawdzanie konfliktu' })
        .getByRole('button', { name: 'Otwórz' })
        .click();
    await page.getByLabel('Wnioskowana dotacja (PLN)').fill('999999.00');
    await page.getByRole('button', { name: 'Zapisz szkic' }).click();
    await expect(page.getByRole('alert')).toContainText('Dotacja');
    await page.getByLabel('Wnioskowana dotacja (PLN)').fill('50000.00');
    const apps = await (await request.get('/api/applications')).json();
    const draft = apps.find(
        (a: { title: string }) => a.title === 'Sprawdzanie konfliktu',
    );
    const changed = await request.put(`/api/applications/${draft.id}`, {
        data: { ...draft, title: 'Zmiana w drugiej karcie' },
    });
    expect(changed.ok()).toBeTruthy();
    await page.getByRole('button', { name: 'Zapisz szkic' }).click();
    await expect(page.getByRole('alert')).toContainText('Odśwież');
    const current = await (
        await request.get(`/api/applications/${draft.id}`)
    ).json();
    expect(current.title).toBe('Zmiana w drugiej karcie');
    expect(current.requestedAmount).toBe('50000.00');
});

test('CloudMigration settings allow stage changes but protect existing applications', async ({
    page,
    request,
}) => {
    const before = await (await request.get('/api/applications')).json();
    await page.goto('/settings');
    await expect(
        page.getByRole('heading', {
            name: 'Ustawienia CloudMigration',
            exact: true,
        }),
    ).toBeVisible();
    await expect(
        page.getByLabel('Budżet konkursu (PLN)', { exact: true }),
    ).toBeDisabled();
    await expect(
        page.getByText(
            'Budżet i kryteria są zablokowane, ponieważ istnieją już wnioski. Możesz zmienić etap konkursu.',
        ),
    ).toHaveCount(0);
    await expect(
        page.getByRole('button', { name: 'Nowy konkurs', exact: true }),
    ).toHaveCount(0);
    await expect(
        page.getByRole('combobox', { name: 'Etap', exact: true }),
    ).toBeEnabled();
    await page.getByRole('combobox', { name: 'Etap', exact: true }).focus();
    await page.keyboard.press('Tab');
    await page
        .getByRole('combobox', { name: 'Etap', exact: true })
        .selectOption('EVALUATION');
    await page
        .getByRole('button', { name: 'Zapisz ustawienia', exact: true })
        .click();
    await expect(page.getByRole('status')).toContainText('Ustawienia zapisane');
    expect((await (await request.get('/api/competition')).json()).stage).toBe(
        'EVALUATION',
    );
    await page
        .getByRole('combobox', { name: 'Etap', exact: true })
        .selectOption('OPEN');
    await page
        .getByRole('button', { name: 'Zapisz ustawienia', exact: true })
        .click();
    await expect(
        page.getByRole('combobox', { name: 'Etap', exact: true }),
    ).toHaveValue('OPEN');
    await expect
        .poll(
            async () =>
                (await (await request.get('/api/competition')).json()).stage,
        )
        .toBe('OPEN');
    expect(await (await request.get('/api/applications')).json()).toEqual(
        before,
    );
});

test('known SPA routes render correctly without swallowing API or asset paths', async ({
    page,
}) => {
    for (const [route, heading] of [
        ['applications', 'Wnioski'],
        ['evaluations', 'Oceny'],
        ['funding', 'Ranking i dotacje'],
        ['decisions', 'Decyzje'],
        ['reports', 'Raporty XQuery'],
        ['settings', 'Ustawienia'],
    ]) {
        await page.goto('/' + route);
        await expect(
            page.getByRole('heading', { name: heading, exact: true, level: 1 }),
        ).toBeVisible();
    }
});

test('blank DOCX and example use CloudMigration without a competition selector', async ({
    page,
    request,
}, info) => {
    const competition = await (await request.get('/api/competition')).json();
    const template = await request.get('/api/documents/template');
    expect(template.headers()['content-disposition']).toContain(
        'wniosek-formularz.docx',
    );
    const blank = await request.post('/api/documents/import', {
        multipart: {
            file: {
                name: 'pusty.docx',
                mimeType:
                    'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
                buffer: await template.body(),
            },
        },
    });
    expect(blank.status()).toBe(422);
    expect((await blank.json()).message).toContain('Beneficjent');
    const example = await request.get('/api/documents/example');
    expect(example.headers()['content-disposition']).toContain(
        'wniosek-przyklad.docx',
    );
    const input = info.outputPath('filled-example.docx');
    await writeFile(input, await example.body());
    await page.goto('/applications');
    await expect(page.getByLabel('Aktywny konkurs')).toHaveCount(0);
    await expect(
        page.getByRole('button', { name: 'Konkursy', exact: true }),
    ).toHaveCount(0);
    await expect(
        page.getByRole('link', { name: 'Pobierz pusty formularz DOCX' }),
    ).toHaveAttribute('href', '/api/documents/template');
    const upload = page.waitForRequest((r) =>
        r.url().includes('/api/documents/import'),
    );
    await page.getByLabel('Importuj DOCX').setInputFiles(input);
    expect(new URL((await upload).url()).search).toBe('');
    await expect(page.getByRole('status')).toContainText('CloudMigration');
    await expect(page.getByLabel('Nazwa firmy')).toHaveValue(
        'Biuro Rachunkowe Żółw',
    );
    const before = await (await request.get('/api/applications')).json();
    await page.getByRole('button', { name: 'Zapisz szkic' }).click();
    await expect(page.getByRole('status')).toContainText('Dokument zapisany');
    const saved = await (await request.get('/api/applications')).json();
    expect(saved).toHaveLength(before.length + 1);
    expect(
        saved.every(
            (a: { competitionId: string }) =>
                a.competitionId === competition.id,
        ),
    ).toBeTruthy();
});

test('new applications inherit CloudMigration and reject a foreign competition ID', async ({
    page,
    request,
}) => {
    const before = await (await request.get('/api/applications')).json();
    const draft = before.find((a: { state: string }) => a.state === 'DRAFT');
    const wrong = await request.post('/api/applications', {
        data: {
            ...draft,
            id: undefined,
            revision: 0,
            competitionId: '99999999-0000-0000-0000-000000000001',
        },
    });
    expect(wrong.status()).toBe(422);
    expect((await request.get('/api/competitions')).status()).toBe(404);
    expect(
        (await request.post('/api/competition', { data: {} })).status(),
    ).toBe(405);
    expect((await request.delete('/api/competition')).status()).toBe(405);
    await page.goto('/applications');
    await page
        .getByRole('button', { name: 'Nowy wniosek', exact: true })
        .click();
    await expect(page.getByLabel('Tytuł projektu')).toBeVisible();
    await expect(page.getByLabel('Aktywny konkurs')).toHaveCount(0);
    expect(await (await request.get('/api/applications')).json()).toEqual(
        before,
    );
});

test('reports explain their purpose and only show the amount filter when relevant', async ({
    page,
    request,
}) => {
    const before = await (await request.get('/api/applications')).json();
    const catalogue = await (await request.get('/api/queries')).json();
    await page.goto('/reports');
    const amount = page.getByLabel('Dotacja większa niż (PLN)', {
        exact: true,
    });
    await expect(page.locator('#report-description')).toContainText(
        'Liczba wniosków',
    );
    await expect(amount).toHaveCount(0);
    for (const report of catalogue) {
        await page
            .getByRole('combobox', { name: 'Raport', exact: true })
            .selectOption(report.id);
        await expect(page.locator('#report-description')).toHaveText(
            report.description,
        );
        if (report.id === 'over-budget') await expect(amount).toBeVisible();
        else await expect(amount).toHaveCount(0);
    }
    await page
        .getByRole('combobox', { name: 'Raport', exact: true })
        .selectOption('over-budget');
    await amount.fill('45000');
    await expect(page.locator('#report-description')).toContainText(
        'dokładnie tę kwotę są pomijane',
    );
    await page
        .getByRole('button', { name: 'Pokaż wyniki', exact: true })
        .click();
    const count = before.filter(
        (a: { requestedAmount: string }) => Number(a.requestedAmount) > 45000,
    ).length;
    await expect(
        page.getByRole('heading', { name: `Wyniki: ${count}`, exact: true }),
    ).toBeVisible();
    await amount.fill('invalid');
    await page
        .getByRole('combobox', { name: 'Raport', exact: true })
        .selectOption('summary');
    await page
        .getByRole('button', { name: 'Pokaż wyniki', exact: true })
        .click();
    await expect(
        page.getByRole('heading', { name: 'Wyniki: 1', exact: true }),
    ).toBeVisible();
    expect(await (await request.get('/api/applications')).json()).toEqual(
        before,
    );
});

test('settings data actions confirm replacement and reset without changing data on cancellation', async ({
    page,
    request,
}) => {
    const data = async () => ({
        competition: await (await request.get('/api/competition')).json(),
        applications: await (await request.get('/api/applications')).json(),
        evaluations: await (await request.get('/api/evaluations')).json(),
        decisions: await (await request.get('/api/decisions')).json(),
    });
    let mutations = 0;
    page.on('request', (req) => {
        if (new URL(req.url()).pathname.startsWith('/api/demo')) mutations++;
    });
    await page.goto('/settings');
    const load = page.getByRole('button', {
        name: 'Wczytaj dane demonstracyjne',
        exact: true,
    });
    const reset = page.getByRole('button', {
        name: 'Usuń wszystkie wnioski, oceny i decyzje',
        exact: true,
    });
    const initial = await data();
    const cancelLoadDialog = page.waitForEvent('dialog');
    const cancelLoad = load.click();
    const cancelled = await cancelLoadDialog;
    expect(cancelled.message()).toBe(
        'Czy napewno chcesz usunąć obecne dane i wczytać dane demonstracyjne?',
    );
    await cancelled.dismiss();
    await cancelLoad;
    expect(mutations).toBe(0);
    expect(await data()).toEqual(initial);
    for (let i = 0; i < 2; i++) {
        const previous = await data();
        const dialogPromise = page.waitForEvent('dialog');
        const click = load.click();
        const dialog = await dialogPromise;
        await dialog.accept();
        await click;
        await expect(page.getByRole('status')).toContainText(
            'Wczytano dane demonstracyjne.',
        );
        const loaded = await data();
        expect(loaded.applications).toHaveLength(20);
        expect(loaded.evaluations).toHaveLength(18);
        expect(loaded.decisions).toHaveLength(0);
        expect(loaded.competition.name).toBe('CloudMigration');
        expect(loaded.competition.budget).toBe('250000.00');
        expect(
            loaded.applications.every(
                (app: { id: string }) =>
                    !previous.applications.some(
                        (old: { id: string }) => old.id === app.id,
                    ),
            ),
        ).toBe(true);
        const ready = loaded.applications.find(
            (app: { title: string }) => app.title === 'Projekt testowy 18',
        );
        expect(
            loaded.evaluations.find(
                (evaluation: { applicationId: string }) =>
                    evaluation.applicationId === ready.id,
            ).state,
        ).toBe('DRAFT');
    }
    const loaded = await data();
    const cancelResetDialog = page.waitForEvent('dialog');
    const cancelReset = reset.click();
    const cancelledReset = await cancelResetDialog;
    expect(cancelledReset.message()).toContain(
        'Ustawienia CloudMigration zostaną zachowane.',
    );
    await cancelledReset.dismiss();
    await cancelReset;
    expect(mutations).toBe(2);
    expect(await data()).toEqual(loaded);
    const resetDialog = page.waitForEvent('dialog');
    const confirmReset = reset.click();
    const confirmed = await resetDialog;
    await confirmed.accept();
    await confirmReset;
    await expect(page.getByRole('status')).toContainText(
        'Usunięto wszystkie wnioski, oceny i decyzje.',
    );
    const empty = await data();
    expect(empty.applications).toHaveLength(0);
    expect(empty.evaluations).toHaveLength(0);
    expect(empty.decisions).toHaveLength(0);
    expect(empty.competition).toEqual(loaded.competition);
    expect(mutations).toBe(3);
    await expect(
        page.getByLabel('Budżet konkursu (PLN)', { exact: true }),
    ).toBeEnabled();
});
