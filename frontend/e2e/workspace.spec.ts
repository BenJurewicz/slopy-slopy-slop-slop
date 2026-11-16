import { test, expect } from '@playwright/test';

function draftRow(index: number) {
    return {
        id: `application-${index}`,
        revision: 1,
        state: 'DRAFT',
        title: `Projekt ${index}`,
        company: { name: `Firma ${index}`, size: 'MICRO' },
        requestedAmount: '100.00',
    };
}

test('a rejected submission preserves the editor', async ({ page }) => {
    await page.route('**/api/applications', (route) =>
        route.fulfill({ json: [draftRow(1)] }),
    );
    await page.route('**/api/applications/application-1/submit', (route) =>
        route.fulfill({
            status: 409,
            json: { message: 'Submission rejected' },
        }),
    );
    page.on('dialog', (dialog) => dialog.accept());
    await page.goto('/applications');
    await page.getByRole('button', { name: 'Otwórz', exact: true }).click();

    await page
        .getByRole('button', { name: 'Złóż wniosek', exact: true })
        .click();

    await expect(page.getByRole('alert')).toContainText('Submission rejected');
    await expect(
        page.getByLabel('Tytuł projektu', { exact: true }),
    ).toHaveValue('Projekt 1');
});

test('deleting the last row returns to the previous available page', async ({
    page,
}) => {
    const applications = Array.from({ length: 11 }, (_, index) =>
        draftRow(index + 1),
    );
    await page.route('**/api/applications', (route) =>
        route.fulfill({ json: applications }),
    );
    await page.route(
        (url) => url.pathname === '/api/applications/application-11',
        (route) => {
            applications.pop();
            return route.fulfill({ status: 204 });
        },
    );
    page.on('dialog', (dialog) => dialog.accept());
    await page.goto('/applications');
    await page.getByRole('button', { name: 'Następna', exact: true }).click();
    await expect(page.getByText('Strona 2 z 2', { exact: true })).toBeVisible();

    await page.getByRole('button', { name: 'Usuń', exact: true }).click();

    await expect(page.getByRole('status')).toContainText('Szkic usunięty.');
    await expect(page.getByText('Strona 1 z 1', { exact: true })).toBeVisible();
    await expect(page.locator('tbody tr')).toHaveCount(10);
    await expect(
        page.getByRole('button', { name: 'Poprzednia', exact: true }),
    ).toBeDisabled();
});

test('submission is blocked until visible application edits are saved or undone', async ({
    page,
}) => {
    let submissions = 0;
    await page.route('**/api/applications', (route) =>
        route.fulfill({ json: [draftRow(1)] }),
    );
    await page.route('**/api/applications/application-1/submit', (route) => {
        submissions++;
        return route.fulfill({
            status: 409,
            json: { message: 'Unexpected submission' },
        });
    });
    await page.goto('/applications');
    await page.getByRole('button', { name: 'Otwórz', exact: true }).click();
    const submit = page.getByRole('button', {
        name: 'Złóż wniosek',
        exact: true,
    });
    await expect(submit).toBeEnabled();
    await page
        .getByLabel('Tytuł projektu', { exact: true })
        .fill('Unsaved changes');
    await expect(submit).toBeDisabled();
    await expect(
        page.getByText('Zapisz szkic przed złożeniem wniosku.', {
            exact: true,
        }),
    ).toBeVisible();
    expect(submissions).toBe(0);
    await page.getByLabel('Tytuł projektu', { exact: true }).fill('Projekt 1');
    await expect(submit).toBeEnabled();
});
