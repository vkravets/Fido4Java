import { test, expect } from '@playwright/test';

test('first-run setup loads with its token, switches language and validates passwords', async ({
    page
}) => {
    await page.goto(`/#setup=${process.env.UI_SETUP_TOKEN}`);
    await expect(page.getByRole('heading', { name: 'Your administrator account' })).toBeVisible();
    await page.getByRole('combobox', { name: 'Language' }).selectOption('uk');
    await expect(
        page.getByRole('heading', { name: 'Ваш обліковий запис адміністратора' })
    ).toBeVisible();
    await page.getByRole('combobox', { name: 'Мова' }).selectOption('en');
    await page.getByRole('textbox', { name: 'Username', exact: true }).fill('new-admin');
    await page.getByLabel('Password', { exact: true }).fill('example-admin-password');
    await page.getByLabel('Confirm password', { exact: true }).fill('a-different-password');
    await page.getByRole('button', { name: 'Continue →' }).click();
    const error = page.getByRole('alert');
    await expect(error).toContainText('Passwords must match');
    await expect(error.getByRole('button')).toHaveCount(0);
    await page.getByLabel('Confirm password', { exact: true }).fill('example-admin-password');
    await page.getByRole('button', { name: 'Continue →' }).click();
    await expect(page.getByRole('heading', { name: 'Station and connection' })).toBeVisible();
    await expect(page.getByRole('alert')).toHaveCount(0);
});
