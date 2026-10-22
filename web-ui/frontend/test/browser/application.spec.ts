import { test, expect } from '@playwright/test';
import type { Page } from '@playwright/test';

test('keyboard reading updates the preview without dimming the list or starting global progress', async ({
    page
}) => {
    await page.goto('/');
    await signIn(page, 'admin', 'preview-admin-password');
    await page.getByRole('button', { name: 'Echomail', exact: true }).click();
    await page.getByRole('button', { name: /^Welcome to general/ }).click();
    await expect(page.locator('.reader-heading h2')).toBeVisible();
    await expect(page.locator('.progress')).toHaveCount(0);
    const title = await page.locator('.reader-heading h2').textContent();
    const list = (await page.locator('.split-list').elementHandle())!;
    const body = (await page.locator('.mail-body').elementHandle())!;
    const requests: string[] = [];
    await page.route('**/rpc/ui', async (route) => {
        const method = (route.request().postDataJSON() as unknown[])[1] as string;
        requests.push(method);
        if (['message', 'echoContext', 'markRead'].includes(method))
            await new Promise((resolve) => setTimeout(resolve, 120));
        await route.continue();
    });
    await page.evaluate(() => {
        const report = { progress: false, globalDisabled: false };
        const observer = new MutationObserver((records) => {
            if (document.querySelector('.progress')) report.progress = true;
            if (
                records.some(
                    (record) =>
                        record.attributeName === 'disabled' &&
                        (record.target as Element).closest('.split-list, .sidebar, .topbar')
                )
            )
                report.globalDisabled = true;
        });
        observer.observe(document.body, {
            childList: true,
            subtree: true,
            attributes: true,
            attributeFilter: ['disabled']
        });
        Object.assign(window, { navigationCheck: { report, observer } });
    });
    await page.keyboard.press('j');
    await expect(page.locator('.reader-heading h2')).not.toHaveText(title!);
    await expect(page.locator('[data-shortcut="mail.reply"]').first()).toBeEnabled();
    const report = await page.evaluate(() => {
        const check = (
            window as unknown as {
                navigationCheck: {
                    report: { progress: boolean; globalDisabled: boolean };
                    observer: MutationObserver;
                };
            }
        ).navigationCheck;
        check.observer.disconnect();
        return check.report;
    });
    expect(report).toEqual({ progress: false, globalDisabled: false });
    expect(await list.evaluate((element) => element.isConnected)).toBe(true);
    expect(await body.evaluate((element) => element.isConnected)).toBe(true);
    expect(requests).not.toContain('echoMessages');
    expect(requests).not.toContain('areas');
});

test('page reload restores the validated session while stale tokens return to sign-in', async ({
    page
}) => {
    await page.goto('/');
    await signIn(page, 'admin', 'preview-admin-password');
    const original = await page.evaluate(() => sessionStorage.getItem('jftn-session'));
    expect(!!original).toBe(true);
    await page
        .locator('.sidebar')
        .getByRole('button', { name: 'My settings', exact: true })
        .click();
    await page.reload();
    await expectSignedIn(page);
    expect(
        await page.evaluate((token) => sessionStorage.getItem('jftn-session') === token, original)
    ).toBe(true);
    await page.getByRole('button', { name: 'Sign out', exact: true }).click();
    expect(await page.evaluate(() => sessionStorage.getItem('jftn-session') === null)).toBe(true);
    await page.reload();
    await expect(page.getByRole('heading', { name: 'Sign in to JFtn' })).toBeVisible();
    await page.evaluate(() => sessionStorage.setItem('jftn-session', 'invalid-old-session'));
    await page.reload();
    await expect(page.getByRole('heading', { name: 'Sign in to JFtn' })).toBeVisible();
    expect(await page.evaluate(() => sessionStorage.getItem('jftn-session') === null)).toBe(true);
});

test('personal keyboard assignments survive a new login and invoke the echomail reply action', async ({
    page
}) => {
    page.on('dialog', (dialog) => dialog.accept());
    await page.goto('/');
    await signIn(page, 'admin', 'preview-admin-password');
    await page
        .locator('.sidebar')
        .getByRole('button', { name: 'My settings', exact: true })
        .click();
    await page.getByRole('tab', { name: 'Keyboard shortcuts', exact: true }).click();
    const field = page.getByRole('textbox', { name: 'Quoted reply', exact: true });
    await field.click();
    await field.press('Alt+Shift+q');
    await page.getByRole('button', { name: 'Save preferences', exact: true }).click();
    await expect(
        page.getByRole('button', { name: 'Save preferences', exact: true })
    ).toBeDisabled();
    await page.getByRole('button', { name: 'Sign out', exact: true }).click();
    await signIn(page, 'admin', 'preview-admin-password');
    await page
        .locator('.sidebar')
        .getByRole('button', { name: 'My settings', exact: true })
        .click();
    await page.getByRole('tab', { name: 'Keyboard shortcuts', exact: true }).click();
    await expect(field).toHaveValue('Alt+Shift+Q');
    await page.locator('.sidebar').getByRole('button', { name: 'Echomail', exact: true }).click();
    await page.locator('.echo-message-open').first().click();
    const reply = page
        .locator('.reader-actions')
        .getByRole('button', { name: 'Reply', exact: true });
    await expect(reply).toBeEnabled();
    await expect(reply).toHaveAttribute('aria-keyshortcuts', 'Alt+Shift+Q');
    await page.keyboard.press('r');
    await expect(page.locator('.mail-composer')).toHaveCount(0);
    await page.keyboard.press('Alt+Shift+q');
    await expect(page.locator('.mail-composer')).toBeVisible();
    await page.keyboard.press('Escape');
    await page
        .locator('.sidebar')
        .getByRole('button', { name: 'My settings', exact: true })
        .click();
    await page.getByRole('tab', { name: 'Keyboard shortcuts', exact: true }).click();
    await page.getByRole('button', { name: 'Restore preset', exact: true }).click();
    await page.getByRole('button', { name: 'Save preferences', exact: true }).click();
    await expect(
        page.getByRole('button', { name: 'Save preferences', exact: true })
    ).toBeDisabled();
});

function notification(page: Page) {
    return page.getByRole('region', { name: 'Notifications' }).getByRole('status');
}

async function signIn(page: Page, username: string, password: string) {
    await page.getByRole('textbox', { name: 'Username', exact: true }).fill(username);
    await page.getByLabel('Password', { exact: true }).fill(password);
    await page.locator('form').getByRole('button', { name: 'Sign in', exact: true }).click();
    await expectSignedIn(page);
}

async function expectSignedIn(page: Page) {
    await expect(page.getByRole('heading', { name: 'Netmail', exact: true })).toBeVisible();
}

async function stationSection(page: Page, name: string) {
    await page.locator('.sidebar').getByRole('button', { name: 'Station', exact: true }).click();
    await page
        .getByRole('tablist', { name: 'Station settings', exact: true })
        .getByRole('tab', { name, exact: true })
        .click();
}

test('mobile application exposes navigation as a screen without horizontal overflow', async ({
    page
}) => {
    await page.setViewportSize({ width: 375, height: 812 });
    await page.goto('/');
    await signIn(page, 'admin', 'preview-admin-password');
    expect(
        await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)
    ).toBe(true);
    await page.getByRole('button', { name: 'Open menu', exact: true }).click();
    await expect(page.getByRole('button', { name: 'Echomail', exact: true })).toBeVisible();
    await page.getByRole('button', { name: 'Echomail', exact: true }).click();
    await expect(page.getByRole('heading', { name: 'Echomail', exact: true })).toBeVisible();
    await expect(page.locator('.workspace > .sidebar')).toBeHidden();
    await page.getByRole('button', { name: /^Welcome to general/ }).click();
    await expect(page.locator('.mail-body')).toBeVisible();
    await page.getByRole('button', { name: 'Back to messages' }).click();
    await expect(page.getByRole('treeitem').first()).toBeVisible();
});

test('registration suggests imported points before authentication', async ({ page }) => {
    await page.goto('/');
    await page.getByRole('button', { name: 'No account? Register' }).click();
    const input = page.getByRole('combobox', { name: 'Point address' });
    await input.fill('1:500/100.');
    await page.getByRole('option').filter({ hasText: 'First Point' }).click();
    await expect(input).toHaveValue('1:500/100.1');
    await expect(input).toHaveAttribute('aria-expanded', 'false');
    await expect(page.getByRole('alert')).toHaveCount(0);
});

test('echomail reads a thread, saves a reply draft and posts it through HTTP RPC', async ({
    page
}) => {
    await page.goto('/');
    await signIn(page, 'admin', 'preview-admin-password');
    await page.getByRole('button', { name: /Echomail/, exact: false }).click();
    await expect(page.getByRole('treeitem')).toHaveCount(2);
    await page.getByRole('button', { name: /^Welcome to general/ }).click();
    await expect(page.locator('.mail-body')).toContainText("<script>alert('mail')</script>");
    await page.locator('.echo-conversation > summary').click();
    await page
        .getByRole('region', { name: 'Conversation' })
        .getByRole('button', { name: /Bob/ })
        .click();
    await expect(
        page.getByRole('heading', { name: 'Re: Welcome to general', exact: true })
    ).toBeVisible();
    await page.getByRole('button', { name: /^In reply to/ }).click();
    await page.getByRole('button', { name: 'Reply', exact: true }).click();
    await expect(page.getByLabel('Recipient name', { exact: true })).toHaveValue('Alice');
    await expect(page.getByRole('textbox', { name: 'Message text', exact: true })).toContainText(
        '> Hello everyone!'
    );
    await page.getByLabel('Subject', { exact: true }).fill('Browser echo reply');
    await page.getByLabel('Message text', { exact: true }).fill('A reply from the browser.');
    await page.getByRole('button', { name: 'Save draft', exact: true }).click();
    await expect(notification(page)).toContainText('Draft saved.');
    await page.getByRole('button', { name: 'Cancel', exact: true }).click();
    await page.getByRole('button', { name: /Drafts/, exact: true }).click();
    await page.getByRole('button', { name: /Browser echo reply/ }).click();
    await expect(page.getByLabel('Subject', { exact: true })).toHaveValue('Browser echo reply');
    await page.getByRole('button', { name: 'Post message', exact: true }).click();
    await expect(notification(page)).toContainText('Message posted to the conference.');
    await page.getByRole('button', { name: /^Browser echo reply/ }).click();
    await expect(page.locator('.mail-body')).toHaveText('A reply from the browser.');
    await page.locator('.echo-conversation > summary').click();
    await expect(page.getByRole('button', { name: /^In reply to/ })).toContainText(
        'Welcome to general'
    );
    await expect(page.getByRole('alert')).toHaveCount(0);
});

test('station address suggestions preserve other AKAs while adding successive addresses', async ({
    page
}) => {
    await page.goto('/');
    await signIn(page, 'admin', 'preview-admin-password');
    await page.getByRole('button', { name: /Station/ }).click();
    const aliases = page.getByRole('combobox', { name: 'Additional AKAs', exact: true });
    await aliases.fill('1:500/100.1 1:500/10');
    await page.getByRole('option').filter({ hasText: '1:500/101.0' }).click();
    await expect(aliases).toHaveValue('1:500/100.1 1:500/101.0');
    await aliases.pressSequentially(' 1:500/2');
    await page.getByRole('option').filter({ hasText: '1:500/200.0' }).click();
    await expect(aliases).toHaveValue('1:500/100.1 1:500/101.0 1:500/200.0');
    await expect(page.getByRole('alert')).toHaveCount(0);
});

test('administrator browses paged lists and searches addresses names and IPs through RPC', async ({
    page
}) => {
    await page.goto('/');
    await signIn(page, 'admin', 'preview-admin-password');
    await page.getByRole('button', { name: /Node directory/ }).click();
    await expect(page.getByText('Showing 25 entries', { exact: true })).toBeVisible();
    await page.getByRole('button', { name: 'Next page', exact: true }).click();
    await expect(page.getByText('Showing 7 entries', { exact: true })).toBeVisible();
    await page.getByRole('button', { name: 'Previous page', exact: true }).click();
    await expect(page.getByText('Showing 25 entries', { exact: true })).toBeVisible();
    const input = page.getByRole('searchbox', { name: 'Search the directory' });
    await input.fill('192.0.2.10');
    await expect(page.getByText('Showing 1 entries', { exact: true })).toBeVisible();
    await expect(page.getByRole('cell', { name: /Kyiv Station/ })).toBeVisible();
    await input.fill('JANE DOE');
    await expect(page.getByRole('cell', { name: /Kyiv Station/ })).toBeVisible();
    await input.fill('1:500/100.1');
    await expect(page.getByRole('cell', { name: /First Point/ })).toBeVisible();
    await input.fill('missing-node');
    await expect(
        page.getByText('No matching nodes. Import a nodelist or pointlist, or change your search.')
    ).toBeVisible();
    await expect(page.getByRole('alert')).toHaveCount(0);
});

test('administrator sees incoming and outgoing binkp logs through HTTP RPC', async ({ page }) => {
    await page.goto('/');
    await signIn(page, 'admin', 'preview-admin-password');
    await page.locator('.sidebar').getByRole('button', { name: 'Polling', exact: true }).click();
    await expect(page.getByRole('heading', { name: 'Polling', exact: true })).toBeVisible();
    await expect(page.getByRole('tablist', { name: 'Station settings', exact: true })).toHaveCount(
        0
    );
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
    await expect(
        page
            .locator('.settings-page-header')
            .getByRole('heading', { name: 'Echo areas and link access' })
    ).toBeVisible();
    await expect(panel).toBeVisible();
    await panel.getByLabel('Echo area', { exact: true }).fill('NEW.ECHO');
    await panel.getByRole('button', { name: 'Save echo access', exact: true }).click();
    const row = panel
        .locator('.rows > li')
        .filter({ has: page.getByText('new.echo', { exact: true }) });
    await expect(row).toContainText('WRITE');
    await expect(notification(page)).toContainText('No restart required.');
    await row.getByRole('button', { name: 'Edit', exact: true }).click();
    await expect(panel.getByLabel('Echo area', { exact: true })).toHaveAttribute('readonly');
    await panel.getByLabel('Echo access rights').selectOption('BOTH');
    await panel.getByRole('button', { name: 'Save echo access', exact: true }).click();
    await expect(row).toContainText('BOTH');
    await page.reload();
    await expectSignedIn(page);
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
    const panel = page.locator('#packet-quarantine');
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
    await expect(notification(page)).toContainText('Original retained');
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
    await expect(notification(page)).toContainText('Configuration applied.');
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
    await expectSignedIn(page);
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
    await expect(notification(page)).toContainText('Configuration applied.');
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
    await expect(notification(page)).toContainText('Configuration applied.');
    await expect(page.locator('fieldset.mail-link-settings')).toHaveCount(3);
    await expect(page.getByLabel('Link address', { exact: true }).last()).toHaveAttribute(
        'readonly'
    );
    await page.getByRole('tab', { name: 'General', exact: true }).click();
    await page.getByLabel('Station name', { exact: true }).fill('Station with three uplinks');
    await page.getByRole('button', { name: 'Save configuration', exact: true }).click();
    await expect(notification(page)).toContainText('Configuration applied.');
    await page.reload();
    await expectSignedIn(page);
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
    await page
        .locator('.sidebar')
        .getByRole('button', { name: 'My settings', exact: true })
        .click();
    await page.getByRole('tab', { name: 'Appearance', exact: true }).click();
    await page.getByRole('button', { name: 'Dark · Night shift' }).click();
    await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark');
    await expect(notification(page)).toContainText('Settings saved.');
    await page.getByRole('button', { name: 'Dismiss notification' }).click();
    await expect(notification(page)).toHaveCount(0);

    const pointContext = await browser.newContext({ baseURL: process.env.UI_BASE_URL });
    try {
        const point = await pointContext.newPage();
        await point.goto('/');
        await point.getByRole('button', { name: 'No account? Register' }).click();
        await point.getByRole('textbox', { name: 'Username', exact: true }).fill('new-point');
        await point.getByLabel('Password', { exact: true }).fill('example-point-password');
        await point.getByLabel('Confirm password', { exact: true }).fill('example-point-password');
        await point.getByRole('combobox', { name: 'Point address' }).fill('1:500/100.1');
        await point
            .locator('form')
            .getByRole('button', { name: /^Submit request/ })
            .click();
        await expect(point.getByRole('heading', { name: 'Awaiting confirmation' })).toBeVisible();

        await stationSection(page, 'Accounts and requests');
        const account = page
            .locator('.account-row')
            .filter({ has: page.getByRole('heading', { name: 'new-point', exact: true }) });
        await account.getByRole('button', { name: 'Approve', exact: true }).click();
        await expect(account).toContainText('Approved');
        await point.getByRole('button', { name: 'Back to sign-in' }).click();
        await signIn(point, 'new-point', 'example-point-password');
        await expect(point.getByRole('button', { name: /Station/ })).toHaveCount(0);
        await expect(point.getByRole('button', { name: /Polling/ })).toHaveCount(0);
        await expect(
            point.locator('.sidebar').getByRole('button', { name: 'Logs', exact: true })
        ).toHaveCount(0);
        await expect(
            point.locator('.sidebar').getByRole('button', { name: 'Mail links', exact: true })
        ).toHaveCount(0);
        await expect(point.getByRole('button', { name: /Accounts and requests/ })).toHaveCount(0);
        await expect(point.getByRole('button', { name: /Netmail/ })).toBeEnabled();
        await point.getByRole('button', { name: /Node directory/ }).click();
        await point.getByRole('searchbox', { name: 'Search the directory' }).fill('POINT OWNER');
        await expect(point.getByRole('cell', { name: /First Point/ })).toBeVisible();
        await expect(point.getByRole('cell', { name: /1:500\/100\.1/ })).toBeVisible();
        await expect(point.getByRole('alert')).toHaveCount(0);
    } finally {
        await pointContext.close();
    }
    await page.getByRole('button', { name: 'Sign out', exact: true }).click();
    await expect(page.getByRole('heading', { name: 'Sign in to JFtn' })).toBeVisible();
});

test('administrator sidebar shortcuts open links and the connection log directly', async ({
    page
}) => {
    await page.goto('/');
    await signIn(page, 'admin', 'preview-admin-password');
    await page.locator('.sidebar').getByRole('button', { name: 'Mail links', exact: true }).click();
    await expect(page.getByRole('tab', { name: 'Mail links', exact: true })).toHaveAttribute(
        'aria-selected',
        'true'
    );
    await stationSection(page, 'Logs');
    await expect(page.getByRole('tab', { name: 'Binkp log', exact: true })).toHaveAttribute(
        'aria-selected',
        'true'
    );
    await expect(page.locator('pre[aria-label="Binkp log"]')).toBeVisible();
});
