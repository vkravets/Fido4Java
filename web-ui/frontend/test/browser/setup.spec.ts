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
    await expect(page.getByLabel('Link address', { exact: true })).toBeVisible();
    await expect(page.getByLabel('binkp server', { exact: true })).toBeVisible();
    await expect(page.getByLabel('binkp session password', { exact: true })).toBeVisible();
    await page.getByLabel('Default AKA', { exact: true }).fill('1:500/100');
    await page.getByLabel('Additional AKAs', { exact: true }).fill('1:500/100.1');
    await page.getByLabel('Sysop', { exact: true }).fill('Example Sysop');
    await page.getByLabel('Link address', { exact: true }).fill('1:500/101');
    await page.getByLabel('binkp server', { exact: true }).fill('first.example.org');
    await page.getByLabel('binkp session password', { exact: true }).fill('firstpwd');
    await page.getByRole('button', { name: 'Add link', exact: true }).click();
    const second = page.locator('fieldset.mail-link-settings').nth(1);
    await second.getByLabel('Link address', { exact: true }).fill('1:500/101.0');
    await second.getByLabel('binkp server', { exact: true }).fill('second.example.org');
    await second
        .getByLabel('binkp session password', { exact: true })
        .fill('long-session-password');
    await second.getByLabel('PKT password mode').selectOption('SEPARATE');
    await second.getByLabel('Separate PKT password').fill('pktpass');
    await page.getByRole('button', { name: /^Continue/ }).click();
    await page.getByRole('button', { name: /^Continue/ }).click();
    await expect(page.getByText('second.example.org:24554', { exact: false })).toBeVisible();
    await page.getByRole('button', { name: 'Save and start' }).click();
    await expect(page.getByRole('alert')).toContainText('Duplicate link address');
    await page.getByRole('button', { name: 'Back', exact: true }).click();
    await page.getByLabel('Address', { exact: true }).fill('1:500/100.2');
    await page.getByRole('button', { name: 'Back', exact: true }).click();
    await expect(second.getByLabel('Local AKAs for this link')).toHaveValue(
        '1:500/100.2 1:500/100.1'
    );
    await second.getByLabel('Link address', { exact: true }).fill('1:500/102');
    await page.getByRole('button', { name: /^Continue/ }).click();
    await page.getByRole('button', { name: /^Continue/ }).click();
    await page.getByRole('button', { name: 'Save and start' }).click();
    await expect(page.getByRole('heading', { name: 'All set', exact: true })).toBeVisible();
    await expect(page.getByRole('alert')).toHaveCount(0);
});
