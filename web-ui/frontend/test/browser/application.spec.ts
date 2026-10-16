import { test, expect } from '@playwright/test';
import type { Page } from '@playwright/test';

async function signIn(page: Page, username: string, password: string) {
    await page.getByRole('textbox', { name: 'Username', exact: true }).fill(username);
    await page.getByLabel('Password', { exact: true }).fill(password);
    await page
        .locator('form')
        .getByRole('button', { name: /^Sign in/ })
        .click();
    await expect(page.getByRole('heading', { name: 'Settings', exact: true })).toBeVisible();
}

test('real HTTP RPC supports language, theme, registration, approval and point access', async ({
    page,
    browser
}) => {
    await page.goto('/');
    await expect(page.getByRole('heading', { name: 'Sign in to JFtn' })).toBeVisible();
    await page.getByRole('combobox', { name: 'Language' }).selectOption('uk');
    await expect(page.getByRole('heading', { name: 'Вхід до JFtn' })).toBeVisible();
    await page.reload();
    await expect(page.getByRole('heading', { name: 'Вхід до JFtn' })).toBeVisible();
    await page.getByRole('combobox', { name: 'Мова' }).selectOption('en');
    await signIn(page, 'admin', 'preview-admin-password');
    await page.getByRole('button', { name: 'Appearance', exact: true }).click();
    await page.getByRole('button', { name: 'Dark · Night shift' }).click();
    await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark');
    await expect(page.getByRole('status')).toContainText('Settings saved.');
    await page.getByRole('button', { name: 'Dismiss notification' }).click();
    await expect(page.getByRole('status')).toHaveCount(0);

    const pointContext = await browser.newContext({ baseURL: process.env.UI_BASE_URL });
    try {
        const point = await pointContext.newPage();
        await point.goto('/');
        await point.getByRole('button', { name: 'No account? Register' }).click();
        await point.getByRole('textbox', { name: 'Username', exact: true }).fill('new-point');
        await point.getByLabel('Password', { exact: true }).fill('example-point-password');
        await point.getByLabel('Confirm password', { exact: true }).fill('example-point-password');
        await point.getByRole('textbox', { name: 'Point address' }).fill('1:500/100.1');
        await point
            .locator('form')
            .getByRole('button', { name: /^Submit request/ })
            .click();
        await expect(point.getByRole('heading', { name: 'Awaiting confirmation' })).toBeVisible();

        await page.getByRole('button', { name: /Accounts and requests/ }).click();
        const account = page
            .locator('.account-row')
            .filter({ has: page.getByRole('heading', { name: 'new-point', exact: true }) });
        await account.getByRole('button', { name: 'Approve', exact: true }).click();
        await expect(account).toContainText('Approved');
        await point.getByRole('button', { name: 'Back to sign-in' }).click();
        await signIn(point, 'new-point', 'example-point-password');
        await expect(point.getByRole('button', { name: /Station/ })).toHaveCount(0);
        await expect(point.getByRole('button', { name: /Connections/ })).toHaveCount(0);
        await expect(point.getByRole('button', { name: /Accounts and requests/ })).toHaveCount(0);
        await expect(point.getByRole('button', { name: /Netmail/ })).toBeDisabled();
    } finally {
        await pointContext.close();
    }
    await page.getByRole('button', { name: 'Sign out', exact: true }).click();
    await expect(page.getByRole('heading', { name: 'Sign in to JFtn' })).toBeVisible();
});
