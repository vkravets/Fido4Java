import { test, expect } from '@playwright/test';
import type { Page } from '@playwright/test';
import { openStory as story } from './story.ts';

test('editor toolbar uses labelled icons and tooltips without visible button text', async ({
    page
}) => {
    for (const width of [1280, 390]) {
        await page.setViewportSize({ width, height: 900 });
        await story(page, 'mail-fidoeditor--writing');
        const controls = page.getByRole('toolbar').getByRole('button');
        await expect(controls).toHaveCount(10);
        for (const control of await controls.all()) {
            await expect(control).toHaveAttribute('aria-label', /.+/);
            await expect(control).toHaveAttribute('title', /.+/);
            await expect(control.locator('svg')).toBeVisible();
            await expect(control).toHaveText('');
        }
    }
});

test('composer actions retain bottom spacing after scrolling the mail pane', async ({ page }) => {
    for (const theme of ['light', 'dark'] as const) {
        for (const language of ['en', 'uk'] as const) {
            await page.setViewportSize({ width: 1280, height: 720 });
            await story(page, 'mail-mailcomposer--workspace-reply', theme, language);
            await page.locator('.composer-actions button').last().scrollIntoViewIfNeeded();
            const gap = await page.locator('.mail-composer').evaluate((form) => {
                const buttons = [...form.querySelectorAll('.composer-actions button')];
                const lastBottom = Math.max(
                    ...buttons.map((button) => button.getBoundingClientRect().bottom)
                );
                return form.getBoundingClientRect().bottom - lastBottom;
            });
            expect(gap).toBeGreaterThanOrEqual(16);
        }
    }
});

test('compact desktop composer aligns template controls and keeps the editor toolbar on one row', async ({
    page
}) => {
    for (const width of [1280, 820]) {
        await page.setViewportSize({ width, height: 900 });
        await story(page, 'mail-mailcomposer--compact-reply', 'dark', 'uk');
        const select = await page
            .getByRole('combobox', { name: 'Шаблон', exact: true })
            .boundingBox();
        const apply = await page
            .getByRole('button', { name: 'Застосувати шаблон', exact: true })
            .boundingBox();
        expect(Math.abs(select!.y + select!.height - apply!.y - apply!.height)).toBeLessThan(1);
        const controls = await page
            .locator('.editor-toolbar button')
            .evaluateAll((buttons) => buttons.map((button) => button.getBoundingClientRect().y));
        expect(Math.max(...controls) - Math.min(...controls)).toBeLessThan(1);
        const form = await page.locator('.mail-composer').boundingBox();
        const editor = await page.locator('.editor-toolbar').boundingBox();
        expect(editor!.y - form!.y).toBeLessThan(180);
        await expect(
            page.getByRole('button', { name: 'Скасувати зміну', exact: true })
        ).toHaveAttribute('title', /Z/);
    }
});

test('personal template settings are editable by points and administrators and protect unsaved edits', async ({
    page
}) => {
    for (const role of ['point', 'administrator']) {
        await story(page, `settings-accountsettings--${role}-templates`);
        const navigation = page.getByRole('tablist', { name: 'Personal settings', exact: true });
        const body = page.getByRole('textbox', { name: 'Template body', exact: true });
        await body.fill('Personal greeting @tname.\n@position');
        page.once('dialog', (dialog) => dialog.dismiss());
        await navigation.getByRole('tab', { name: 'Profile', exact: true }).click();
        await expect(body).toHaveValue('Personal greeting @tname.\n@position');
        await page.getByRole('button', { name: 'Save template', exact: true }).click();
        await navigation.getByRole('tab', { name: 'Profile', exact: true }).click();
        await expect(body).toHaveCount(0);
        await navigation.getByRole('tab', { name: 'Mail templates', exact: true }).click();
        const choices = page.getByRole('combobox', { name: 'Template', exact: true }).first();
        const personal = await choices.locator('option[value^="custom-"]').getAttribute('value');
        await choices.selectOption(personal!);
        await expect(body).toHaveValue('Personal greeting @tname.\n@position');
    }
});

test('sidebar labels keep their position and wrapping when the active page changes', async ({
    page
}) => {
    await page.setViewportSize({ width: 1280, height: 900 });
    for (const theme of ['light', 'dark']) {
        for (const locale of ['en', 'uk']) {
            await story(page, 'navigation-sidebar--switching-pages', theme, locale);
            const labels = page.locator('.sidebar nav button .nav-text');
            const positions = () =>
                labels.evaluateAll((items) =>
                    items.map((item) => {
                        const { x, y, width, height } = item.getBoundingClientRect();
                        return { x, y, width, height };
                    })
                );
            const before = await positions();
            await page
                .getByRole('button', {
                    name: locale === 'uk' ? 'Каталог вузлів' : 'Node directory',
                    exact: true
                })
                .click();
            await expect(page.locator('.sidebar nav button.active .nav-text')).toHaveText(
                locale === 'uk' ? 'Каталог вузлів' : 'Node directory'
            );
            expect(await positions()).toEqual(before);
        }
    }
});

test('empty drafts are centered below the mail toolbar with compact desktop buttons', async ({
    page
}) => {
    await page.setViewportSize({ width: 1280, height: 900 });
    await story(page, 'mail-netmailview--empty-drafts');
    const message = page.locator('.mail-list-empty p');
    await expect(message).toHaveText('No saved drafts.');
    const pane = await page.locator('.split-list').boundingBox();
    const toolbar = await page.locator('.mail-list-tools').boundingBox();
    const text = await message.boundingBox();
    const contentTop = toolbar!.y + toolbar!.height;
    expect(Math.abs(text!.x + text!.width / 2 - (pane!.x + pane!.width / 2))).toBeLessThan(2);
    expect(
        Math.abs(text!.y + text!.height / 2 - (contentTop + pane!.y + pane!.height) / 2)
    ).toBeLessThan(2);
    const button = await page
        .getByRole('button', { name: 'New netmail', exact: true })
        .boundingBox();
    expect(button!.height).toBeLessThanOrEqual(28);
});

test('empty mail reader is centered in both split directions and mail views', async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 900 });
    for (const id of [
        'mail-netmailview--inbox',
        'mail-netmailview--empty-reader-below',
        'mail-echomailview--tree'
    ]) {
        await story(page, id);
        const placeholder = page.locator('.mail-reader-empty p');
        await expect(placeholder).toBeVisible();
        const pane = await page.locator('.split-detail').boundingBox();
        const text = await placeholder.boundingBox();
        expect(pane).not.toBeNull();
        expect(text).not.toBeNull();
        expect(Math.abs(text!.x + text!.width / 2 - (pane!.x + pane!.width / 2))).toBeLessThan(2);
        expect(Math.abs(text!.y + text!.height / 2 - (pane!.y + pane!.height / 2))).toBeLessThan(2);
    }
});

test('echo tree collapses conversations, supports keyboard navigation and switches to a flat list', async ({
    page
}) => {
    await story(page, 'mail-echomailview--tree');
    await expect(page.getByRole('treeitem')).toHaveCount(4);
    await page.getByRole('button', { name: 'Collapse conversation' }).first().click();
    await expect(page.getByRole('treeitem')).toHaveCount(2);
    await page.getByRole('button', { name: 'Expand conversation' }).click();
    const root = page.locator('[data-mail-row="9007199254740993"]');
    await root.focus();
    await root.press('ArrowRight');
    await expect(page.locator('[data-mail-row="9007199254740994"]')).toBeFocused();
    await page.getByRole('button', { name: 'Flat list', exact: true }).click();
    await expect(page.getByRole('treeitem')).toHaveCount(0);
    await expect(page.locator('[data-mail-row]')).toHaveCount(4);
    await page.getByRole('button', { name: /^Welcome to the conference/ }).click();
    await expect(
        page.getByRole('heading', { name: 'Welcome to the conference', exact: true })
    ).toBeVisible();
    await expect(page.locator('.mail-body')).toContainText('<script>alert("hello")</script>');
    await expect(page.getByRole('button', { name: 'Delete message', exact: true })).toHaveCount(0);
    await page.locator('.echo-conversation > summary').click();
    await page
        .getByRole('region', { name: 'Conversation' })
        .getByRole('button', { name: /Bob Example/ })
        .click();
    await expect(
        page.getByRole('heading', { name: 'Re: Welcome to the conference', exact: true })
    ).toBeVisible();
    await page.getByRole('button', { name: /^In reply to/ }).click();
    await expect(
        page.getByRole('heading', { name: 'Welcome to the conference', exact: true })
    ).toBeVisible();
});

test('echo composer posts to its conference and read-only areas cannot create or reply', async ({
    page
}) => {
    await story(page, 'mail-echomailview--compose');
    await page.getByRole('button', { name: 'Edit sender', exact: true }).click();
    await expect(page.getByRole('combobox', { name: 'Sender AKA', exact: true })).toHaveValue(
        '1:500/100.1'
    );
    await expect(
        page.getByRole('combobox', { name: 'Recipient address', exact: true })
    ).toHaveCount(0);
    await page.getByLabel('Subject', { exact: true }).fill('A new conference message');
    await page.getByRole('button', { name: 'Save draft', exact: true }).click();
    await expect(page.getByLabel('Subject', { exact: true })).toHaveValue(
        'A new conference message'
    );
    await page.getByRole('button', { name: 'Post message', exact: true }).click();
    await expect(page.getByRole('button', { name: /^A new conference message/ })).toBeVisible();
    await story(page, 'mail-echomailview--read-only');
    await expect(page.getByRole('button', { name: 'New echomail', exact: true })).toBeDisabled();
    await expect(
        page.getByRole('heading', { name: 'Welcome to the conference', exact: true })
    ).toBeVisible();
    await expect(page.getByRole('button', { name: 'Reply', exact: true })).toHaveCount(0);
});

test('echo reader remains usable on a narrow screen in both themes', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    for (const theme of ['light', 'dark']) {
        await story(page, 'mail-echomailview--reading', theme, 'uk');
        await expect(
            page.getByRole('heading', { name: 'Re: Welcome to the conference', exact: true })
        ).toBeVisible();
        expect(
            await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)
        ).toBe(true);
        await expect(page.getByRole('button', { name: 'Відповісти', exact: true })).toBeVisible();
    }
});

test('routing rules are read-only in file mode and edit only configured next hops', async ({
    page
}) => {
    await story(page, 'routing-netmailroutespanel--file-managed');
    await expect(page.getByRole('button', { name: 'Save rule', exact: true })).toHaveCount(0);
    await story(page, 'routing-netmailroutespanel--database');
    await expect(page.getByRole('textbox', { name: 'Rule ID', exact: true })).toHaveCount(0);
    await page.getByRole('button', { name: 'Add routing rule', exact: true }).click();
    await expect(page.getByRole('textbox', { name: 'Rule ID', exact: true })).toBeVisible();
    await page.getByRole('button', { name: 'Edit rule', exact: true }).click();
    await expect(page.getByRole('textbox', { name: 'Rule ID', exact: true })).toHaveValue(
        'regional'
    );
    await expect(page.getByRole('textbox', { name: 'Rule ID', exact: true })).toHaveAttribute(
        'readonly',
        ''
    );
    await expect(
        page.getByRole('combobox', { name: 'Next hop', exact: true }).locator('option')
    ).toHaveCount(2);
});

test('routing policy exposes explicit transit controls and prevents edits while saving', async ({
    page
}) => {
    await story(page, 'routing-netmailroutingpolicyform--database');
    await expect(page.getByLabel('Allow netmail transit', { exact: true })).not.toBeChecked();
    await page.getByLabel('Allow netmail transit', { exact: true }).check();
    const masks = page.getByLabel('Transit sender masks (one per line)', { exact: true });
    await masks.fill('1:500/101\n1:500/102');
    await masks.press('Tab');
    await expect(masks).toHaveValue('1:500/101\n1:500/102');
    await page.getByLabel('Allow netmail transit', { exact: true }).uncheck();
    await expect(masks).toBeHidden();
    await page.getByLabel('Allow netmail transit', { exact: true }).check();
    await expect(masks).toHaveValue('1:500/101\n1:500/102');
    await story(page, 'routing-netmailroutingpolicyform--busy');
    await expect(
        page.getByRole('button', { name: 'Save routing policy', exact: true })
    ).toBeDisabled();
});

test('point netmail workflow opens a letter, replies, saves a draft and sends', async ({
    page
}) => {
    await story(page, 'mail-netmailview--inbox');
    await page.getByRole('button', { name: /Welcome to FidoNet/ }).click();
    await expect(page.locator('.mail-body')).toContainText('Your station is ready');
    await page.getByRole('button', { name: 'Reply', exact: true }).click();
    await page.getByRole('button', { name: 'Edit sender', exact: true }).click();
    await expect(page.getByRole('combobox', { name: 'Sender AKA', exact: true })).toHaveValue(
        '1:500/100.1'
    );
    await expect(
        page.getByRole('combobox', { name: 'Recipient address', exact: true })
    ).toHaveValue('1:500/101.2');
    await page.getByRole('button', { name: 'Save draft', exact: true }).click();
    await page.getByRole('button', { name: 'Cancel', exact: true }).click();
    await page.getByRole('button', { name: 'Drafts', exact: true }).click();
    await page
        .getByRole('button', { name: /Re: Welcome to FidoNet/ })
        .last()
        .click();
    await page.getByRole('button', { name: 'Add to send queue', exact: true }).click();
    await expect(page.getByRole('button', { name: 'Outbox', exact: true })).toHaveAttribute(
        'aria-current',
        'page'
    );
});

test('netmail displays received HTML as literal text and offers reply', async ({ page }) => {
    await story(page, 'mail-netmailmessage--reading');
    await expect(page.locator('.mail-body')).toContainText('<script>alert("example")</script>');
    await expect(page.locator('.mail-body script')).toHaveCount(0);
    await expect(page.getByRole('button', { name: 'Reply', exact: true })).toBeEnabled();
});

test('netmail deletes a selected letter after confirmation', async ({ page }) => {
    await story(page, 'mail-netmailview--inbox');
    await page.getByRole('button', { name: /Welcome to FidoNet/ }).click();
    page.once('dialog', (dialog) => dialog.accept());
    await page.locator('.reader-more > summary').click();
    await page.getByRole('button', { name: 'Delete message', exact: true }).click();
    await expect(page.getByText('No messages in this folder.', { exact: true })).toBeVisible();
    await expect(page.locator('.mail-body')).toHaveCount(0);
});

test('mail queue waits for an explicit send and disables empty or busy queues', async ({
    page
}) => {
    await story(page, 'mail-mailqueuecontrol--waiting');
    await expect(page.getByRole('status')).toContainText('Awaiting send: 7');
    await page.getByRole('button', { name: 'Send queued mail', exact: true }).click();
    await expect(page.getByRole('status')).toContainText('Awaiting send: 0');
    await expect(
        page.getByRole('button', { name: 'Send queued mail', exact: true })
    ).toBeDisabled();
    await story(page, 'mail-mailqueuecontrol--busy');
    await expect(
        page.getByRole('button', { name: 'Send queued mail', exact: true })
    ).toBeDisabled();
});

test('netmail composer restricts sender AKA and disables controls during send', async ({
    page
}) => {
    await story(page, 'mail-mailcomposer--new-message');
    await page.getByRole('button', { name: 'Edit sender', exact: true }).click();
    const sender = page.getByRole('combobox', { name: 'Sender AKA', exact: true });
    await expect(sender.locator('option')).toHaveCount(2);
    await sender.selectOption('1:500/100.1');
    await expect(sender).toHaveValue('1:500/100.1');
    await page
        .getByRole('textbox', { name: 'Message text', exact: true })
        .fill('Plain text message');
    await story(page, 'mail-mailcomposer--sending');
    await expect(
        page.getByRole('button', { name: 'Add to send queue', exact: true })
    ).toBeDisabled();
    await expect(page.getByRole('button', { name: 'Save draft', exact: true })).toBeDisabled();
});

test('node-only address suggestions exclude points', async ({ page }) => {
    await story(page, 'directory-addressinput--nodes-only');
    await expect(page.getByRole('option')).toHaveCount(2);
    await expect(page.getByRole('option').filter({ hasText: 'First Point' })).toHaveCount(0);
});

test('address suggestions support the keyboard and replace only the current AKA', async ({
    page
}) => {
    await story(page, 'directory-addressinput--matches');
    const input = page.getByRole('combobox', { name: 'Address', exact: true });
    await expect(page.getByRole('option')).toHaveCount(3);
    await input.press('ArrowDown');
    await input.press('Enter');
    await expect(input).toHaveValue('1:500/101.0');
    await expect(input).toHaveAttribute('aria-expanded', 'false');
    await story(page, 'directory-addressinput--multiple-akas');
    await expect(page.getByRole('option')).toHaveCount(3);
    await input.press('ArrowUp');
    await input.press('Enter');
    await expect(input).toHaveValue('1:500/100 1:500/102.0 ');
    await input.fill('1:500/100 1:500/10 1:500/999');
    await input.evaluate((element: HTMLInputElement) => element.setSelectionRange(15, 15));
    await input.click({ position: { x: 10, y: 10 } });
    await input.evaluate((element: HTMLInputElement) => {
        element.setSelectionRange(15, 15);
        element.dispatchEvent(new Event('input', { bubbles: true }));
    });
    await expect(page.getByRole('option')).toHaveCount(3);
    await page.getByRole('option').filter({ hasText: 'First Point' }).click();
    await expect(input).toHaveValue('1:500/100 1:500/101.1 1:500/999');
});

test('address suggestions close on Escape and permit manual entry while the catalog is unavailable', async ({
    page
}) => {
    await story(page, 'directory-addressinput--matches');
    const input = page.getByRole('combobox', { name: 'Address', exact: true });
    await expect(page.getByRole('option').first()).toBeVisible();
    await input.press('Escape');
    await expect(page.getByRole('listbox')).toHaveCount(0);
    await story(page, 'directory-addressinput--unavailable');
    await expect(
        page.getByText('Directory search is unavailable. You can enter an address manually.')
    ).toBeVisible();
    await input.fill('1:500/999');
    await input.press('Tab');
    await expect(input).toHaveValue('1:500/999');
});

test('multiple uplink editor protects saved addresses and disables edits while saving', async ({
    page
}) => {
    await story(page, 'settings-maillinkseditor--adding-uplink');
    await expect(page.getByLabel('Link address', { exact: true })).toHaveCount(2);
    await expect(page.getByLabel('Link address', { exact: true }).first()).toHaveAttribute(
        'readonly'
    );
    await expect(page.getByLabel('Link address', { exact: true }).last()).not.toHaveAttribute(
        'readonly'
    );
    await expect(page.getByRole('button', { name: 'Cancel', exact: true })).toHaveCount(1);
    await story(page, 'settings-maillinkseditor--saving');
    await expect(page.getByLabel('Link address', { exact: true }).last()).toBeDisabled();
    await expect(page.getByRole('button', { name: 'Add link', exact: true })).toBeDisabled();
    await story(page, 'settings-setupstationform--multiple-uplinks');
    await expect(page.getByLabel('Local AKAs for this link').first()).toHaveAttribute('readonly');
    await expect(page.getByLabel('Link address', { exact: true }).first()).not.toHaveAttribute(
        'readonly'
    );
});

test('echo permissions explain direction, validate creation and require revocation confirmation', async ({
    page
}) => {
    await story(page, 'settings-echoaccesspanel--database');
    await expect(page.getByLabel('Echo access rights')).toHaveValue('WRITE');
    await expect(
        page.getByText('WRITE receives mail from the link.', { exact: false })
    ).toBeVisible();
    const area = page.getByLabel('Echo area', { exact: true });
    expect(await area.evaluate((input: HTMLInputElement) => input.validity.valid)).toBe(false);
    await area.fill('NEW.ECHO');
    expect(await area.evaluate((input: HTMLInputElement) => input.validity.valid)).toBe(true);
    await page.getByLabel('Echo access rights').selectOption('BOTH');
    await expect(page.getByLabel('Echo access rights')).toHaveValue('BOTH');
    await page.getByLabel('Filter echo areas or links').fill('missing');
    await expect(page.getByText('No matching echo permissions.')).toBeVisible();
    await story(page, 'settings-echoaccesspanel--revocation');
    await expect(page.getByRole('alert')).toContainText(
        'Stored messages and prepared outbound packets are retained.'
    );
    await expect(page.getByRole('button', { name: 'Confirm revocation' })).toBeVisible();
    await story(page, 'settings-echoaccesspanel--file-managed');
    await expect(page.getByText('Echo permissions are read-only.', { exact: false })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Save echo access' })).toHaveCount(0);
    await story(page, 'settings-echoaccesspanel--empty');
    await expect(page.getByText('Configure a station link first.')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Save echo access' })).toBeDisabled();
});

test('link password modes expose a separate PKT field without revealing stored secrets', async ({
    page
}) => {
    await story(page, 'settings-maillinkform--existing');
    await expect(page.getByLabel('Link address', { exact: true })).toHaveAttribute('readonly');
    await expect(page.getByLabel('binkp session password')).toHaveValue('');
    await expect(page.getByLabel('Separate PKT password')).toHaveCount(0);
    await page.getByLabel('PKT password mode').selectOption('SEPARATE');
    const packet = page.getByLabel('Separate PKT password');
    await expect(packet).toHaveValue('');
    await expect(packet).toHaveAttribute('type', 'password');
    await expect(packet).toHaveAttribute('maxlength', '8');
    await page.getByLabel('binkp session password').fill('session-secret');
    await packet.fill('pktpass');
    await expect(page.getByLabel('binkp session password')).toHaveValue('session-secret');
    await page.getByLabel('PKT password mode').selectOption('UNPROTECTED');
    await expect(packet).toHaveCount(0);
    await expect(page.getByText('Incoming packets for this link', { exact: false })).toBeVisible();
});

test('tosser defaults keep echo access checks enabled', async ({ page }) => {
    await story(page, 'settings-tosserform--defaults');
    await expect(page.getByLabel('Processing interval (seconds)')).toHaveValue('5');
    await expect(page.getByLabel('Check echomail permissions for each link')).toBeChecked();
    await expect(page.getByLabel('Create unknown echo areas automatically')).not.toBeChecked();
    await page.getByLabel('Processing interval (seconds)').fill('0');
    expect(
        await page
            .getByLabel('Processing interval (seconds)')
            .evaluate((input: HTMLInputElement) => input.validity.valid)
    ).toBe(false);
});

test('echo creation requires an explicit per-link grant and keeps receiving rights in both allowed modes', async ({
    page
}) => {
    await story(page, 'settings-maillinkform--existing');
    const policy = page.getByLabel('Create new echoes from this link', { exact: true });
    await expect(policy).toHaveValue('NONE');
    await expect(policy.locator('option')).toHaveCount(3);
    await expect(policy.locator('option[value="READ"]')).toHaveCount(0);
    await policy.selectOption('WRITE');
    await expect(policy).toHaveValue('WRITE');
    await expect(
        page.getByText('Requires automatic echo creation', { exact: false })
    ).toBeVisible();
    await story(page, 'settings-maillinkform--echo-creation');
    await expect(policy).toHaveValue('BOTH');
    await story(page, 'settings-tosserform--automatic-creation');
    await expect(page.getByLabel('Create unknown echo areas automatically')).toBeChecked();
    await expect(page.getByText('Only links explicitly allowed', { exact: false })).toBeVisible();
});

const catalogueBatches = 8;
for (let batch = 0; batch < catalogueBatches; batch++) {
    test(`catalogue stories render without browser errors (batch ${batch + 1})`, async ({
        page,
        request
    }) => {
        const errors: string[] = [];
        page.on('pageerror', (error) => errors.push(error.message));
        const response = await request.get('/index.json');
        expect(response.ok()).toBeTruthy();
        const index = (await response.json()) as {
            entries: Record<string, { id: string; type: string }>;
        };
        const stories = Object.values(index.entries).filter((entry) => entry.type === 'story');
        expect(stories.length).toBeGreaterThanOrEqual(16);
        const batchStories = stories.filter((_entry, index) => index % catalogueBatches === batch);
        test.setTimeout(15000 + batchStories.length * 5000);
        for (const entry of batchStories) {
            await story(page, entry.id);
            await expect(page.locator('#storybook-root')).not.toBeEmpty();
            expect(errors, entry.id).toEqual([]);
        }
    });
}

test('quarantine batch uses a source selector only for unknown peers and validates confirmation', async ({
    page
}) => {
    await story(page, 'settings-quarantinepanel--batch-retry');
    await expect(page.getByRole('table')).toBeVisible();
    await expect(page.getByRole('table').getByRole('combobox')).toHaveCount(1);
    await expect(page.locator('form').getByRole('combobox')).toHaveCount(1);
    await expect(page.getByRole('button', { name: 'Confirm', exact: true })).toBeDisabled();
    await page.locator('form').getByLabel('Expected source link').selectOption('1:500/102.0');
    await expect(page.getByRole('table').getByRole('combobox')).toHaveValue('1:500/102.0');
    await expect(page.getByRole('button', { name: 'Confirm', exact: true })).toBeEnabled();
    await page.getByRole('button', { name: 'Cancel', exact: true }).click();
    await page.getByRole('checkbox', { name: 'Select all on this page' }).uncheck();
    await expect(
        page.getByRole('button', { name: 'Restore selected (0)', exact: true })
    ).toBeDisabled();
    await page.getByRole('checkbox', { name: 'Select packet unknown.bad', exact: true }).check();
    await page.getByRole('button', { name: 'Restore selected (1)', exact: true }).click();
    await expect(page.getByText('Selected packets: 1')).toBeVisible();
});

test('station groups open operational panels without losing general configuration edits', async ({
    page
}) => {
    await story(page, 'settings-stationsettingstabs--general');
    await page.getByLabel('Station name', { exact: true }).fill('Unsaved station');
    const navigation = page.getByRole('tablist', { name: 'Station settings', exact: true });
    await expect(navigation.getByRole('tab', { name: 'Polling', exact: true })).toHaveCount(0);
    for (const [tab, heading] of [
        ['Logs', 'Binkp log'],
        ['Mail robots', 'Mail robots'],
        ['Accounts and requests', 'Accounts and requests']
    ]) {
        await navigation.getByRole('tab', { name: tab, exact: true }).click();
        await expect(page.getByRole('heading', { name: heading, exact: true })).toBeVisible();
    }
    await navigation.getByRole('tab', { name: 'Netmail routing', exact: true }).click();
    await expect(page.getByLabel('Allow netmail transit', { exact: true })).toBeVisible();
    await navigation.getByRole('tab', { name: 'General', exact: true }).click();
    await expect(page.getByLabel('Station name', { exact: true })).toHaveValue('Unsaved station');
});

test('station groups support keyboard navigation and keep unsaved form values', async ({
    page
}) => {
    await story(page, 'settings-stationsettingstabs--general');
    const general = page.getByRole('tab', { name: 'General', exact: true });
    await page.getByLabel('Station name', { exact: true }).fill('Unsaved station');
    await general.focus();
    await general.press('ArrowRight');
    await expect(
        page.getByRole('tab', { name: 'Accounts and requests', exact: true })
    ).toBeFocused();
    await expect(page.getByLabel('Station name', { exact: true })).toBeHidden();
    await page.getByRole('tab', { selected: true }).press('End');
    await expect(page.getByRole('tab', { name: 'Plugins', exact: true })).toHaveAttribute(
        'aria-selected',
        'true'
    );
    await page.getByRole('tab', { selected: true }).press('Home');
    await expect(page.getByLabel('Station name', { exact: true })).toHaveValue('Unsaved station');
    await story(page, 'settings-stationsettingstabs--busy');
    for (const tab of await page.getByRole('tab').all()) await expect(tab).toBeDisabled();
});

for (const theme of ['light', 'dark']) {
    for (const locale of ['en', 'uk']) {
        test(`${theme}/${locale}: viewport notifications and role navigation`, async ({
            page
        }, info) => {
            await story(page, 'feedback-notificationbanner--information', theme, locale);
            const notification = page.getByRole('status');
            await expect(notification).toHaveText(
                locale === 'en' ? /Settings saved/ : /Налаштування збережено/
            );
            const before = await notification.boundingBox();
            await page.evaluate(() => window.scrollTo(0, 1000));
            await expect.poll(() => page.evaluate(() => window.scrollY)).toBeGreaterThan(500);
            const after = await notification.boundingBox();
            expect(Math.abs(after!.y - before!.y)).toBeLessThan(1);
            expect(after!.y).toBeLessThan(25);
            await notification.getByRole('button').click();
            await expect(notification).toHaveCount(0);

            await story(page, 'feedback-notificationbanner--warning', theme, locale);
            await page.getByRole('status').getByRole('button').click();
            await expect(page.getByRole('status')).toHaveCount(0);
            await story(page, 'feedback-notificationbanner--error', theme, locale);
            await expect(page.getByRole('alert')).toBeVisible();
            await expect(page.getByRole('alert').getByRole('button')).toHaveCount(0);

            await story(page, 'navigation-sidebar--administrator', theme, locale);
            await expect(
                page.getByRole('button', {
                    name: locale === 'en' ? 'Station' : 'Станція',
                    exact: true
                })
            ).toBeVisible();
            await expect(page.locator('html')).toHaveAttribute('data-theme', theme);
            await expect(page.locator('html')).toHaveAttribute('lang', locale);
            await info.attach('administrator-navigation', {
                body: await page.screenshot(),
                contentType: 'image/png'
            });
            await story(page, 'navigation-sidebar--point', theme, locale);
            await expect(
                page.getByRole('button', {
                    name: locale === 'en' ? 'Station' : 'Станція',
                    exact: true
                })
            ).toHaveCount(0);
        });
    }
}

test('global binkp logs show both connection roles, support tabs and follow controls', async ({
    page
}) => {
    await story(page, 'connections-binkplogpanel--connections');
    const log = page.locator('pre[aria-label="Binkp log"]');
    await expect(log).toContainText('role=SERVER');
    await expect(log).toContainText('role=CLIENT');
    await expect(log).toContainText('PWD [redacted]');
    await page.getByRole('checkbox', { name: 'Follow new events' }).uncheck();
    await page.getByRole('tab', { name: 'Outgoing polls', exact: true }).click();
    await expect(log).toBeHidden();
    await page.getByRole('tab', { name: 'Binkp log', exact: true }).click();
    await expect(log).toBeVisible();
    await expect(page.getByRole('checkbox', { name: 'Follow new events' })).not.toBeChecked();
    await story(page, 'connections-binkplogpanel--busy');
    await expect(page.getByRole('button', { name: 'Show latest events' })).toBeDisabled();
});

test('journal text is escaped and busy queue actions cannot be clicked', async ({ page }) => {
    await story(page, 'connections-polldetails--failed');
    const journal = page.getByLabel('Selected poll log');
    await expect(journal).toContainText('<script>This is plain journal text</script>');
    await expect(journal.locator('script')).toHaveCount(0);
    await story(page, 'connections-pollattempts--busy');
    const actions = page.locator('.storybook-stage .attempts-scroll button');
    await expect(actions).toHaveCount(4);
    for (const button of await actions.all()) {
        await expect(button).toBeDisabled();
    }
});

test('station form remains usable at mobile width in both themes', async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 812 });
    for (const theme of ['light', 'dark']) {
        await story(page, 'settings-nodeform--settings', theme, 'uk');
        const archive = page.getByRole('checkbox', { name: /Зберігати оригінали/ });
        await expect(archive).toBeChecked();
        await archive.uncheck();
        await expect(archive).not.toBeChecked();
        expect(
            await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)
        ).toBeTruthy();
    }
});

async function checkNotificationSize(page: Page, name: string, theme: string, locale: string) {
    await story(page, `feedback-notificationbanner--${name}`, theme, locale);
    const banner = page.getByRole('status');
    const button = banner.getByRole('button');
    await expect(button).toBeVisible();
    const before = (await banner.boundingBox())!;
    const action = (await button.boundingBox())!;
    const text = (await banner.locator('.message-text').boundingBox())!;
    expect(action.x).toBeGreaterThanOrEqual(text.x + text.width);
    expect(action.y).toBeGreaterThanOrEqual(before.y);
    expect(action.y + action.height).toBeLessThanOrEqual(before.y + before.height);
    // Keep the same content/width and remove only the close control.
    await button.evaluate((element) => element.remove());
    const after = (await banner.boundingBox())!;
    expect(after.height).toBeCloseTo(before.height, 1);
    expect(after.width).toBeCloseTo(before.width, 1);
}

test('quarantine separates retry from archival and requires explicit peer selection for legacy packets', async ({
    page
}, info) => {
    await story(page, 'settings-quarantinepanel--packets');
    await expect(page.getByRole('button', { name: 'Release and retry' })).toBeEnabled();
    await expect(page.getByRole('button', { name: 'Archive packet' })).toBeEnabled();
    await story(page, 'settings-quarantinepanel--legacy-retry');
    await expect(page.getByRole('button', { name: 'Confirm', exact: true })).toBeDisabled();
    await expect(page.locator('form').getByLabel('Expected source link')).toHaveValue('');
    await expect(
        page.locator('form').getByLabel('Expected source link').locator('option')
    ).toHaveCount(2);
    await story(page, 'settings-quarantinepanel--archive', 'dark', 'uk');
    await expect(page.getByRole('button', { name: 'Підтвердити', exact: true })).toBeEnabled();
    await expect(page.getByRole('combobox')).toHaveCount(0);
    const image = await page.screenshot({
        path: info.outputPath('quarantine-dark-uk.png'),
        fullPage: true
    });
    await info.attach('quarantine-dark-uk', { body: image, contentType: 'image/png' });
    await page.setViewportSize({ width: 375, height: 812 });
    await story(page, 'settings-quarantinepanel--long-filename');
    expect(
        await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)
    ).toBeTruthy();
    await story(page, 'settings-quarantinepanel--empty');
    await expect(page.getByText('No quarantined packets.')).toBeVisible();
});

for (const theme of ['light', 'dark']) {
    for (const locale of ['en', 'uk']) {
        test(`${theme}/${locale}: close buttons do not resize notifications`, async ({ page }) => {
            for (const width of [1280, 375]) {
                await page.setViewportSize({ width, height: 900 });
                await checkNotificationSize(page, 'information', theme, locale);
                await checkNotificationSize(page, 'warning', theme, locale);
            }
        });
    }
}

test('plugin settings edit permissions and preserve empty password edits', async ({ page }) => {
    await story(page, 'plugins-pluginsettingspanel--areas');
    await page.getByRole('button', { name: 'Edit', exact: true }).first().click();
    await expect(page.getByLabel('Area', { exact: true })).toHaveValue('lists.nodes');
    await page.getByLabel('Description', { exact: true }).fill('Updated lists');
    await page.getByRole('button', { name: 'Save', exact: true }).click();
    await expect(page.getByLabel('Last saved entry')).toContainText('Updated lists');
    await story(page, 'plugins-pluginsettingspanel--robots');
    await page.getByRole('button', { name: 'Edit', exact: true }).click();
    await expect(page.getByLabel('Password', { exact: true })).toHaveValue('');
    await page.getByRole('button', { name: 'Save', exact: true }).click();
    await expect(page.getByLabel('Last saved entry')).toContainText('"password":""');
    await story(page, 'plugins-pluginsettingspanel--file-managed');
    await expect(page.getByRole('button', { name: 'Save', exact: true })).toHaveCount(0);
    await expect(
        page.getByText('This section is managed through configuration files.')
    ).toBeVisible();
    await page.setViewportSize({ width: 375, height: 812 });
    expect(
        await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)
    ).toBeTruthy();
    await story(page, 'plugins-pluginsettingspanel--empty');
    await expect(page.getByText('No active settings extensions.')).toBeVisible();
});

test('recipient lookup fills point names and netmail addresses while keeping echo routing unchanged', async ({
    page
}) => {
    await story(page, 'mail-mailcomposer--recipient-search');
    const name = page.getByRole('combobox', { name: 'Recipient name', exact: true });
    const address = page.getByRole('combobox', { name: 'Recipient address', exact: true });
    await expect(page.getByRole('option').filter({ hasText: 'Point Owner' })).toBeVisible();
    await name.press('ArrowDown');
    await name.press('Enter');
    await expect(name).toHaveValue('Point Owner');
    await expect(address).toHaveValue('1:500/101.1');
    await name.fill('Jane');
    await page.getByRole('option').filter({ hasText: 'Jane Doe' }).click();
    await expect(name).toHaveValue('Jane Doe');
    await expect(address).toHaveValue('1:500/101.0');
    await name.fill('Manual Recipient');
    await name.press('Escape');
    await expect(name).toHaveValue('Manual Recipient');
    await expect(address).toHaveValue('1:500/101.0');
    await story(page, 'mail-mailcomposer--echo-recipient-search');
    await page.getByRole('option').filter({ hasText: 'Point Owner' }).click();
    await expect(name).toHaveValue('Point Owner');
    await expect(
        page.getByRole('combobox', { name: 'Recipient address', exact: true })
    ).toHaveCount(0);
});

test('focused editor draws a themed caret that follows keyboard navigation and typing', async ({
    page
}) => {
    for (const theme of ['light', 'dark']) {
        await story(page, 'mail-fidoeditor--focused', theme);
        const content = page.locator('.cm-content');
        const caret = page.locator('.cm-cursor-primary');
        await expect(content).toBeFocused();
        await expect(caret).toBeVisible();
        await expect(caret).toHaveCSS(
            'border-left-color',
            theme === 'dark' ? 'rgb(242, 198, 107)' : 'rgb(36, 36, 36)'
        );
        const start = (await caret.boundingBox())!;
        await content.press('ArrowRight');
        await expect.poll(async () => (await caret.boundingBox())!.x).toBeGreaterThan(start.x);
        await content.press('Control+End');
        await content.press('Enter');
        await content.press('X');
        await expect(content.locator('.cm-line').last()).toHaveText('X');
        await expect.poll(async () => (await caret.boundingBox())!.y).toBeGreaterThan(start.y);
        await page.getByRole('button', { name: 'Source text', exact: true }).click();
        await content.focus();
        await expect(caret).toBeVisible();
        await expect(caret).toHaveCSS(
            'border-left-color',
            theme === 'dark' ? 'rgb(242, 198, 107)' : 'rgb(36, 36, 36)'
        );
        await page.getByRole('button', { name: 'Bold', exact: true }).focus();
        await expect(page.locator('.cm-editor')).not.toHaveClass(/cm-focused/);
        await expect(caret).not.toBeVisible();
    }
});

test('compact netmail preserves sender edits and searches both recipient fields without stealing focus', async ({
    page
}) => {
    await story(page, 'mail-mailcomposer--golded-new');
    const senderToggle = page.getByRole('button', { name: 'Edit sender', exact: true });
    await expect(senderToggle).toHaveAttribute('aria-expanded', 'false');
    await senderToggle.click();
    const sender = page.getByLabel('Sender name', { exact: true });
    await sender.fill('Other Sender');
    await senderToggle.click();
    await expect(senderToggle).toContainText('Other Sender');
    await expect(sender).toBeHidden();
    const name = page.getByRole('combobox', { name: 'Recipient name', exact: true });
    const address = page.getByRole('combobox', { name: 'Recipient address', exact: true });
    await name.fill('Jane');
    await expect(name).toBeFocused();
    await page.getByRole('option').filter({ hasText: 'Jane Doe' }).click();
    await expect(address).toHaveValue('1:500/101.0');
    await address.fill('1:500/101.1');
    await expect(address).toBeFocused();
    await page.getByRole('option').filter({ hasText: 'Point Owner' }).click();
    await expect(name).toHaveValue('Point Owner');
    await expect(address).toHaveValue('1:500/101.1');
    await expect(address).toBeFocused();
    await expect(page.locator('.cm-content')).toContainText('Hello Point!');
    await expect(page.locator('.cm-content')).toContainText('Other');
    await senderToggle.click();
    await expect(sender).toHaveValue('Other Sender');
});

test('hidden invalid sender fields expand for native validation', async ({ page }) => {
    await story(page, 'mail-mailcomposer--golded-new');
    const toggle = page.getByRole('button', { name: 'Edit sender', exact: true });
    await toggle.click();
    await page.getByLabel('Sender name', { exact: true }).fill('');
    await toggle.click();
    await page.getByRole('button', { name: 'Add to send queue', exact: true }).click();
    await expect(toggle).toHaveAttribute('aria-expanded', 'true');
    await expect(page.getByLabel('Sender name', { exact: true })).toBeFocused();
});

test('compact echo and netmail headers fit desktop and mobile panes', async ({ page }) => {
    for (const width of [1280, 375]) {
        await page.setViewportSize({ width, height: 900 });
        for (const mode of ['echo-compact', 'new-message']) {
            await story(page, `mail-mailcomposer--${mode}`, 'dark', 'uk');
            const form = page.locator('.mail-composer');
            const name = page.locator('.composer-fields label').nth(0);
            const other = page.locator('.composer-fields label').nth(1);
            const first = (await name.boundingBox())!;
            const second = (await other.boundingBox())!;
            if (width > 700) expect(Math.abs(first.y - second.y)).toBeLessThan(1);
            else expect(second.y).toBeGreaterThanOrEqual(first.y + first.height);
            const geometry = await form.evaluate((el) => ({
                width: el.clientWidth,
                scroll: el.scrollWidth,
                top: el.getBoundingClientRect().top
            }));
            expect(geometry.scroll).toBeLessThanOrEqual(geometry.width);
            const toolbar = (await page.locator('.editor-toolbar').boundingBox())!;
            expect(toolbar.y - geometry.top).toBeLessThan(width > 700 ? 180 : 340);
        }
    }
});
