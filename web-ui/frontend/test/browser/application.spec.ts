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

test('administrator sees incoming and outgoing binkp logs through HTTP RPC', async ({ page }) => {
    await page.goto('/');
    await signIn(page, 'admin', 'preview-admin-password');
    await page.getByRole('button', { name: /Connections/ }).click();
    await page.getByRole('tab', { name: 'Binkp log', exact: true }).click();
    const log = page.locator('pre[aria-label="Binkp log"]');
    await expect(log).toContainText('role=SERVER');
    await expect(log).toContainText('PWD [redacted]');
    await expect(log).toContainText('binkp >>>');
    await expect(log).toContainText('binkp <<<');
    await expect(log).toContainText('id=outgoing');
    await page.getByRole('button', { name: 'Show latest events', exact: true }).click();
    await expect(log).toContainText('role=SERVER');
    await expect(page.getByRole('alert')).toHaveCount(0);
});

test('station tabs preserve unsaved station, link and tosser values across groups', async ({
    page
}) => {
    await page.goto('/');
    await signIn(page, 'admin', 'preview-admin-password');
    await page.getByRole('button', { name: /Station/, exact: false }).click();
    await page.getByLabel('Station name', { exact: true }).fill('Unsaved identity');
    await page.getByRole('tab', { name: 'Mail links', exact: true }).click();
    await page.getByLabel('binkp server', { exact: true }).first().fill('unsaved.example.org');
    await page.getByRole('tab', { name: 'Tosser settings', exact: true }).click();
    await page.getByLabel('Processing interval (seconds)').fill('23');
    await page.getByRole('tab', { name: 'Echo areas and link access', exact: true }).click();
    await expect(page.locator('#echo-access')).toBeVisible();
    await page.getByRole('tab', { name: 'Packet quarantine', exact: true }).click();
    await expect(
        page.getByRole('heading', { name: 'Packet quarantine', exact: true })
    ).toBeVisible();
    await page.getByRole('tab', { name: 'Tosser settings', exact: true }).click();
    await expect(page.getByLabel('Processing interval (seconds)')).toHaveValue('23');
    await page.getByRole('tab', { name: 'Mail links', exact: true }).click();
    await expect(page.getByLabel('binkp server', { exact: true }).first()).toHaveValue(
        'unsaved.example.org'
    );
    await page.getByRole('tab', { name: 'General', exact: true }).click();
    await expect(page.getByLabel('Station name', { exact: true })).toHaveValue('Unsaved identity');
});

test('administrator grants, edits and revokes echo access through the station screen', async ({
    page
}) => {
    await page.goto('/');
    await signIn(page, 'admin', 'preview-admin-password');
    await page.getByRole('button', { name: /Station/, exact: false }).click();
    const panel = page.locator('#echo-access');
    await page.getByRole('tab', { name: 'Echo areas and link access', exact: true }).click();
    await expect(panel.getByRole('heading', { name: 'Echo areas and link access' })).toBeVisible();
    await panel.getByLabel('Echo area', { exact: true }).fill('NEW.ECHO');
    await panel.getByRole('button', { name: 'Save echo access', exact: true }).click();
    const row = panel
        .locator('.rows > li')
        .filter({ has: page.getByText('new.echo', { exact: true }) });
    await expect(row).toContainText('WRITE');
    await expect(page.getByRole('status')).toContainText('No restart required.');
    await row.getByRole('button', { name: 'Edit', exact: true }).click();
    await expect(panel.getByLabel('Echo area', { exact: true })).toHaveAttribute('readonly');
    await panel.getByLabel('Echo access rights').selectOption('BOTH');
    await panel.getByRole('button', { name: 'Save echo access', exact: true }).click();
    await expect(row).toContainText('BOTH');
    await page.reload();
    await signIn(page, 'admin', 'preview-admin-password');
    await page.getByRole('button', { name: /Station/, exact: false }).click();
    await page.getByRole('tab', { name: 'Echo areas and link access', exact: true }).click();
    await expect(row).toContainText('BOTH');
    await row.getByRole('button', { name: 'Revoke access', exact: true }).click();
    await panel.getByRole('button', { name: 'Cancel', exact: true }).click();
    await expect(row).toContainText('BOTH');
    await row.getByRole('button', { name: 'Revoke access', exact: true }).click();
    await panel.getByRole('button', { name: 'Confirm revocation', exact: true }).click();
    await expect(panel.getByText('No echo permissions yet.', { exact: false })).toBeVisible();
    await expect(page.getByRole('alert')).toHaveCount(0);
});

test('administrator releases a legacy quarantine packet through HTTP RPC after choosing a peer', async ({
    page
}) => {
    await page.goto('/');
    await signIn(page, 'admin', 'preview-admin-password');
    await page.getByRole('button', { name: /Station/, exact: false }).click();
    const panel = page
        .locator('section')
        .filter({ has: page.getByRole('heading', { name: 'Packet quarantine', exact: true }) });
    await page.getByRole('tab', { name: 'Packet quarantine', exact: true }).click();
    await expect(panel.getByText('legacy.bad', { exact: true })).toBeVisible();
    await panel.getByRole('button', { name: 'Release and retry' }).click();
    await expect(panel.getByRole('button', { name: 'Confirm', exact: true })).toBeDisabled();
    await panel.getByRole('button', { name: 'Cancel', exact: true }).click();
    await expect(panel.getByRole('heading', { name: 'Confirm packet retry' })).toHaveCount(0);
    await panel.getByRole('button', { name: 'Release and retry' }).click();
    await panel.locator('form').getByLabel('Expected source link').selectOption('1:500/101.0');
    await panel.getByRole('button', { name: 'Confirm', exact: true }).click();
    await expect(panel.getByText('No quarantined packets.')).toBeVisible();
    await expect(page.getByRole('status')).toContainText('Original retained');
    await expect(page.getByRole('alert')).toHaveCount(0);
});

test('administrator persists separate PKT passwords and tosser options through real RPC', async ({
    page
}) => {
    await page.goto('/');
    await signIn(page, 'admin', 'preview-admin-password');
    await page.getByRole('button', { name: /Station/, exact: false }).click();
    await page.getByRole('tab', { name: 'Mail links', exact: true }).click();
    await expect(page.getByRole('heading', { name: 'Mail links', exact: true })).toBeVisible();
    await page.getByLabel('PKT password mode').selectOption('SEPARATE');
    await page
        .getByLabel('Create new echoes from this link', { exact: true })
        .selectOption('WRITE');
    await page.getByLabel('Separate PKT password').fill('pktpass');
    await page.getByRole('button', { name: 'Save links', exact: true }).click();
    await expect(page.getByRole('status')).toContainText('Configuration applied.');
    await expect(page.getByLabel('Separate PKT password')).toHaveValue('');
    await expect(page.getByLabel('PKT password mode')).toHaveValue('SEPARATE');
    await page.getByRole('tab', { name: 'Tosser settings', exact: true }).click();
    await expect(page.getByRole('heading', { name: 'Tosser settings', exact: true })).toBeVisible();
    await page.getByLabel('Processing interval (seconds)').fill('25');
    await page.getByLabel('Create unknown echo areas automatically').check();
    await page.getByRole('button', { name: 'Save tosser settings', exact: true }).click();
    await expect(
        page.getByRole('button', { name: 'Save tosser settings', exact: true })
    ).toBeEnabled();
    await page.reload();
    await signIn(page, 'admin', 'preview-admin-password');
    await page.getByRole('button', { name: /Station/, exact: false }).click();
    await page.getByRole('tab', { name: 'Mail links', exact: true }).click();
    await expect(page.getByLabel('PKT password mode')).toHaveValue('SEPARATE');
    await expect(page.getByLabel('Separate PKT password')).toHaveValue('');
    await expect(page.getByLabel('Create new echoes from this link', { exact: true })).toHaveValue(
        'WRITE'
    );
    await page.getByRole('tab', { name: 'Tosser settings', exact: true }).click();
    await expect(page.getByLabel('Processing interval (seconds)')).toHaveValue('25');
    await expect(page.getByLabel('Create unknown echo areas automatically')).toBeChecked();
    await page.getByRole('button', { name: 'Reload configuration files' }).click();
    await expect(page.getByRole('status')).toContainText('Configuration applied.');
    await expect(page.getByRole('alert')).toHaveCount(0);
});

test('administrator adds multiple uplinks and preserves them when saving station details', async ({
    page
}) => {
    await page.goto('/');
    await signIn(page, 'admin', 'preview-admin-password');
    await page.getByRole('button', { name: /Station/, exact: false }).click();
    await page.getByRole('tab', { name: 'Mail links', exact: true }).click();
    for (const node of [102, 103]) {
        await page.getByRole('button', { name: 'Add link', exact: true }).click();
        const link = page.locator('fieldset.mail-link-settings').last();
        await link.getByLabel('Link address', { exact: true }).fill(`1:500/${node}`);
        await link.getByLabel('binkp server', { exact: true }).fill(`peer${node}.example.org`);
        await link.getByLabel('binkp session password').fill('linkpass');
    }
    await page.getByRole('button', { name: 'Save links', exact: true }).click();
    await expect(
        page.getByRole('region', { name: 'Notifications' }).getByRole('status')
    ).toContainText('Configuration applied.');
    await expect(page.locator('fieldset.mail-link-settings')).toHaveCount(3);
    await expect(page.getByLabel('Link address', { exact: true }).last()).toHaveAttribute(
        'readonly'
    );
    await page.getByRole('tab', { name: 'General', exact: true }).click();
    await page.getByLabel('Station name', { exact: true }).fill('Station with three uplinks');
    await page.getByRole('button', { name: 'Save configuration', exact: true }).click();
    await expect(
        page.getByRole('region', { name: 'Notifications' }).getByRole('status')
    ).toContainText('Configuration applied.');
    await page.reload();
    await signIn(page, 'admin', 'preview-admin-password');
    await page.getByRole('button', { name: /Station/, exact: false }).click();
    await page.getByRole('tab', { name: 'Mail links', exact: true }).click();
    await expect(page.locator('fieldset.mail-link-settings')).toHaveCount(3);
    await expect(page.getByLabel('binkp server', { exact: true }).last()).toHaveValue(
        'peer103.example.org'
    );
    await expect(page.getByLabel('binkp session password').last()).toHaveValue('');
    await expect(page.getByRole('alert')).toHaveCount(0);
});

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
