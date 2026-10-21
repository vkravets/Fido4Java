import { test, expect } from '@playwright/test';
import { openStory } from './story.ts';
import type { Page } from '@playwright/test';

test('send queue icon has the same box and centering in both desktop menu states', async ({
    page
}) => {
    await page.setViewportSize({ width: 1280, height: 900 });
    const dimensions = [];
    for (const story of [
        'navigation-sidebar--awaiting-send',
        'navigation-sidebar--collapsed-awaiting-send'
    ]) {
        await openStory(page, story);
        const button = page.getByRole('button', { name: 'Send queued mail', exact: true });
        await expect(button).toBeEnabled();
        const box = (await button.boundingBox())!;
        const icon = (await button.locator('.app-icon').boundingBox())!;
        expect(Math.abs(icon.x + icon.width / 2 - box.x - box.width / 2)).toBeLessThanOrEqual(1);
        expect(Math.abs(icon.y + icon.height / 2 - box.y - box.height / 2)).toBeLessThanOrEqual(1);
        dimensions.push({ width: box.width, height: box.height, icon: icon.width });
    }
    expect(dimensions[0]).toEqual(dimensions[1]);
});

test('login form fits short screens with password, passkey and registration controls', async ({
    page
}) => {
    for (const viewport of [
        { width: 1130, height: 540 },
        { width: 1366, height: 768 },
        { width: 390, height: 740 }
    ]) {
        await page.setViewportSize(viewport);
        for (const language of ['en', 'uk']) {
            await openStory(page, 'authentication-authscreen--login', 'dark', language);
            const card = page.locator('.auth-card');
            const bounds = (await card.boundingBox())!;
            expect(bounds.width).toBeCloseTo(310, 0);
            expect(bounds.y + bounds.height).toBeLessThanOrEqual(viewport.height);
            await expect(card.locator('input')).toHaveCount(2);
            await expect(card.locator('button')).toHaveCount(3);
            const submit = card.locator('button[type="submit"]');
            const passkey = card.locator('.auth-passkey');
            await expect(submit).toHaveCSS('justify-content', 'center');
            await expect(passkey).toHaveAttribute('type', 'button');
            await expect(passkey).toHaveAccessibleName(/passkey|ключа доступу/);
            const submitBox = (await submit.boundingBox())!;
            const passkeyBox = (await passkey.boundingBox())!;
            expect(passkeyBox.width).toBeCloseTo(38, 0);
            expect(passkeyBox.height).toBeCloseTo(submitBox.height, 0);
            expect(passkeyBox.y).toBeCloseTo(submitBox.y, 0);
            expect(
                await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)
            ).toBe(true);
        }
    }
});

test('polling and directory use the compact full-width page with icon refresh', async ({
    page
}) => {
    for (const width of [1280, 390]) {
        await page.setViewportSize({ width, height: 900 });
        for (const story of [
            'connections-connections--queue',
            'directory-nodedirectory--results'
        ]) {
            await openStory(page, story);
            const header = page.locator('.settings-page-header');
            await expect(header.locator('h1')).toHaveCount(1);
            await expect(page.locator('.settings-page .eyebrow')).toHaveCount(0);
            await expect(page.locator('.settings-navigation')).toHaveCount(0);
            const refresh = header.getByRole('button', { name: 'Refresh', exact: true });
            await expect(refresh).toBeEnabled();
            expect(await refresh.innerText()).toBe('');
            await refresh.click();
            if (story.startsWith('directory-')) {
                await page.getByRole('searchbox').fill('First Point');
                await expect(page.locator('.directory-table tbody tr')).toHaveCount(1);
                const input = await page.getByRole('searchbox').boundingBox();
                const search = await page
                    .getByRole('button', { name: 'Search', exact: true })
                    .boundingBox();
                expect(Math.abs(input!.y - search!.y)).toBeLessThanOrEqual(3);
                await refresh.click();
                await expect(page.getByRole('searchbox')).toHaveValue('First Point');
                await expect(page.locator('.directory-table tbody tr')).toHaveCount(1);
            }
            expect(
                await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)
            ).toBe(true);
        }
    }
});

test('settings use one compact heading and keep only active page actions in its toolbar', async ({
    page
}) => {
    for (const language of ['en', 'uk']) {
        await openStory(page, 'settings-stationsettingstabs--accounts', 'dark', language);
        const header = page.locator('.settings-page-header');
        await expect(header.locator('h1')).toHaveCount(1);
        await expect(page.locator('.settings-body h1')).toHaveCount(0);
        await expect(page.locator('.settings-page .eyebrow')).toHaveCount(0);
        await expect(header.getByRole('button')).toHaveCount(2);
        for (const action of await header.getByRole('button').all()) {
            await expect(action).toHaveAttribute('title', /.+/);
            expect(await action.innerText()).toBe('');
        }
        const navigation = page.locator('.station-tabs');
        await navigation.getByRole('tab').first().click();
        await expect(header.getByRole('button')).toHaveCount(1);
        await navigation
            .getByRole('tab')
            .filter({ hasText: language === 'en' ? 'Mail robots' : 'Поштові роботи' })
            .click();
        await expect(header.getByRole('button')).toHaveCount(2);
        await expect(page.locator('.settings-body h1')).toHaveCount(0);
    }
});

test('personal settings share the compact workspace and support keyboard navigation', async ({
    page
}) => {
    await openStory(page, 'settings-accountsettings--profile');
    const navigation = page.getByRole('tablist', { name: 'Personal settings' });
    await navigation.getByRole('tab', { name: 'Profile', exact: true }).focus();
    await page.keyboard.press('ArrowDown');
    await expect(navigation.getByRole('tab', { name: 'My AKAs' })).toBeFocused();
    await expect(page.locator('.settings-page-header h1')).toHaveText('My AKAs');
    await expect(page.locator('.settings-body h1')).toHaveCount(0);
});

test('narrow settings navigation reveals the selected section when opened and changed', async ({
    page
}) => {
    await page.setViewportSize({ width: 390, height: 844 });
    for (const story of [
        'settings-stationsettingstabs--routing',
        'settings-accountsettings--point-templates'
    ]) {
        await openStory(page, story, 'dark', 'uk');
        const tabs = page.locator('.settings-navigation .tabs');
        const selected = tabs.getByRole('tab', { selected: true });
        const assertVisible = async () => {
            const viewport = (await tabs.boundingBox())!;
            const item = (await selected.boundingBox())!;
            expect(item.x).toBeGreaterThanOrEqual(viewport.x - 1);
            expect(item.x + item.width).toBeLessThanOrEqual(viewport.x + viewport.width + 1);
        };
        await assertVisible();
        await selected.press('Home');
        await assertVisible();
        await selected.press('End');
        await assertVisible();
    }
});

async function checkMailBoundary(page: Page) {
    const sidebar = (await page.locator('.workspace > .sidebar').boundingBox())!;
    const list = page.locator('.split-list');
    const table = (await list.locator('.mail-table-head').boundingBox())!;
    const selected = list.locator('.mail-row.active, li.selected').first();
    await selected.hover();
    const row = (await selected.boundingBox())!;
    const boundary = sidebar.x + sidebar.width;
    expect(table.x).toBeCloseTo(boundary, 0);
    expect(row.x).toBeCloseTo(boundary, 0);
    expect(
        await page.evaluate(({ x, y }) => !!document.elementFromPoint(x, y)?.closest('.sidebar'), {
            x: boundary - 2,
            y: row.y + row.height / 2
        })
    ).toBe(true);
    expect(
        await page.evaluate(
            ({ x, y }) => !!document.elementFromPoint(x, y)?.closest('.split-list'),
            { x: boundary + 4, y: row.y + row.height / 2 }
        )
    ).toBe(true);
    for (const dot of await list.locator('.unread .mail-unread-dot').all()) {
        const mark = (await dot.boundingBox())!;
        expect(mark.x - boundary).toBeGreaterThanOrEqual(24);
        await expect(dot).toHaveCSS('opacity', '1');
    }
    expect(await list.locator('.unread .mail-unread-dot').count()).toBeGreaterThan(0);
}

test('mail rows and unread markers stay inside their pane when navigation collapses', async ({
    page
}) => {
    test.setTimeout(60000);
    await page.setViewportSize({ width: 1280, height: 900 });
    for (const theme of ['light', 'dark'] as const) {
        for (const language of ['en', 'uk'] as const) {
            for (const name of ['echo-tree', 'echo-flat', 'echo-compact', 'netmail']) {
                await openStory(page, 'layout-maillistworkspace--' + name, theme, language);
                await checkMailBoundary(page);
                await page.locator('.sidebar-toggle').click();
                await expect(page.locator('.workspace')).not.toHaveClass(/navigation-collapsed/);
                await checkMailBoundary(page);
                await page.locator('.sidebar-toggle').click();
                await expect(page.locator('.workspace')).toHaveClass(/navigation-collapsed/);
                await checkMailBoundary(page);
            }
        }
    }
});

test('application header stays above scrolling content and aligned with navigation', async ({
    page
}) => {
    test.setTimeout(60000);
    for (const width of [1280, 735, 390]) {
        await page.setViewportSize({ width, height: 900 });
        for (const theme of ['light', 'dark'] as const) {
            for (const language of ['en', 'uk'] as const) {
                for (const story of ['long-workspace-logs', 'long-collapsed-workspace-logs']) {
                    await openStory(
                        page,
                        'settings-stationsettingstabs--' + story,
                        theme,
                        language
                    );
                    await page.evaluate(() => window.scrollTo(0, 350));
                    await expect.poll(() => page.evaluate(() => window.scrollY)).toBe(350);
                    const header = (await page.locator('.topbar').boundingBox())!;
                    expect(header.y).toBe(0);
                    expect(header.height).toBe(width > 700 ? 44 : 65);
                    expect(
                        await page.evaluate(
                            () =>
                                !!document.elementFromPoint(innerWidth / 2, 20)?.closest('.topbar')
                        )
                    ).toBe(true);
                    if (width > 700) {
                        const sidebar = (await page
                            .locator('.workspace > .sidebar')
                            .boundingBox())!;
                        expect(sidebar.y).toBe(header.height);
                        expect(sidebar.y + sidebar.height).toBe(900);
                    } else {
                        await expect(page.locator('.mobile-menu-toggle')).toBeInViewport();
                    }
                }
            }
        }
    }
});

test('station content fills the released sidebar width when navigation collapses', async ({
    page
}) => {
    for (const width of [1280, 1920]) {
        await page.setViewportSize({ width, height: 900 });
        for (const theme of ['light', 'dark'] as const) {
            for (const language of ['en', 'uk'] as const) {
                await openStory(
                    page,
                    'settings-stationsettingstabs--workspace-logs',
                    theme,
                    language
                );
                const availableWidth = await page.evaluate(
                    () => document.documentElement.clientWidth
                );
                const content = page.locator('.workspace > .content');
                const panel = page.getByRole('tabpanel');
                const expanded = (await content.boundingBox())!;
                const expandedPanel = (await panel.boundingBox())!;
                expect(expanded.x + expanded.width).toBeCloseTo(availableWidth, 0);
                await page.locator('.sidebar-toggle').click();
                await expect(page.locator('.workspace')).toHaveClass(/navigation-collapsed/);
                await expect
                    .poll(async () => (await content.boundingBox())!.width)
                    .toBeCloseTo(expanded.width + 160, 0);
                const collapsed = (await content.boundingBox())!;
                const sidebar = (await page.locator('.workspace > .sidebar').boundingBox())!;
                expect(sidebar.x + sidebar.width).toBeCloseTo(collapsed.x, 0);
                expect(collapsed.x + collapsed.width).toBeCloseTo(availableWidth, 0);
                expect((await panel.boundingBox())!.width).toBeCloseTo(
                    expandedPanel.width + 160,
                    0
                );
                expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBe(
                    availableWidth
                );
                await page.locator('.sidebar-toggle').click();
                await expect
                    .poll(async () => (await content.boundingBox())!.width)
                    .toBeCloseTo(expanded.width, 0);
            }
        }
    }
});

test('message header actions use icons and translated tooltips in the reader and appearance preview', async ({
    page
}) => {
    for (const width of [1280, 390]) {
        await page.setViewportSize({ width, height: 900 });
        for (const theme of ['light', 'dark'] as const) {
            for (const language of ['en', 'uk'] as const) {
                for (const name of [
                    'mail-netmailmessage--reader-actions',
                    'mail-mailappearancesettings--themes'
                ]) {
                    await openStory(page, name, theme, language);
                    const toolbar = page.locator('.reader-actions');
                    await expect(toolbar).toBeVisible();
                    const controls = toolbar.locator(
                        ':scope > button:visible, .reader-direct-actions > button:visible, .reader-more > summary:visible'
                    );
                    expect(await controls.count()).toBeGreaterThanOrEqual(3);
                    for (const control of await controls.all()) {
                        await expect(control).toHaveText('');
                        await expect(control).toHaveAttribute('aria-label', /.+/);
                        await expect(control).toHaveAttribute('title', /.+/);
                        await expect(control.locator('svg')).toBeVisible();
                    }
                }
            }
        }
    }
});

test('reader and editor cycle quote colors for levels four through six', async ({ page }) => {
    for (const theme of ['light', 'dark'] as const) {
        for (const name of [
            'mail-fidotext--highlighted',
            'mail-fidoeditor--writing',
            'mail-fidotext--source',
            'mail-fidoeditor--preview'
        ]) {
            await openStory(page, name, theme);
            const quotes = page.locator('.quote1:visible, .quote2:visible, .quote3:visible');
            await expect(quotes).toHaveCount(6);
            const colors = await quotes.evaluateAll((lines) =>
                lines.map((line) => getComputedStyle(line).color)
            );
            expect(new Set(colors.slice(0, 3)).size).toBe(3);
            expect(colors.slice(3)).toEqual(colors.slice(0, 3));
        }
    }
});

test('mobile Back restores the previous list scroll position', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    await openStory(page, 'mail-netmailview--long-list');
    const row = page.getByRole('button', { name: /^Discussion 25 / });
    await row.scrollIntoViewIfNeeded();
    const before = await page.evaluate(() => window.scrollY);
    expect(before).toBeGreaterThan(300);
    await row.click();
    await expect(page.locator('.split-list')).toBeHidden();
    await page.getByRole('button', { name: 'Back to messages' }).click();
    await expect.poll(() => page.evaluate(() => window.scrollY)).toBeGreaterThan(before - 20);
});

test('visual editor preserves FIDO source through formatting, undo and mode changes', async ({
    page
}) => {
    await openStory(page, 'mail-fidoeditor--writing');
    const editor = page.getByRole('textbox', { name: 'Message text', exact: true });
    await editor.fill('Hello Bob');
    await editor.press('ControlOrMeta+a');
    await page.getByRole('button', { name: 'Bold', exact: true }).click();
    await expect(editor).toHaveText('*Hello Bob*');
    await page.getByRole('button', { name: 'Undo', exact: true }).click();
    await expect(editor).toHaveText('Hello Bob');
    await page.getByRole('button', { name: 'Redo', exact: true }).click();
    await expect(editor).toHaveText('*Hello Bob*');
    await page.getByRole('button', { name: 'Source text', exact: true }).click();
    await expect(editor).toHaveText('*Hello Bob*');
    await page.getByRole('button', { name: 'Preview', exact: true }).click();
    await expect(page.locator('.mail-body .bold')).toHaveText('Hello Bob');
});
test('mail appearance previews unsaved themes beside controls without changing saved preferences', async ({
    page
}) => {
    await page.setViewportSize({ width: 1440, height: 900 });
    await openStory(page, 'mail-mailappearancesettings--themes');
    const controls = page.locator('.appearance-layout > fieldset');
    const preview = page.getByRole('complementary', { name: 'Live mail preview' });
    const left = await controls.boundingBox();
    const right = await preview.boundingBox();
    expect(right!.x).toBeGreaterThan(left!.x + left!.width);
    const reading = page.getByRole('combobox', { name: 'Reading theme', exact: true });
    await reading.selectOption('graphite');
    await expect(preview.locator('.mail-body')).toHaveCSS('background-color', 'rgb(29, 32, 35)');
    await reading.selectOption('terminal');
    await expect(preview.locator('.mail-body')).toHaveCSS('background-color', 'rgb(16, 24, 16)');
    await page.getByRole('combobox', { name: 'Editor theme', exact: true }).selectOption('paper');
    await expect(preview.locator('.cm-editor')).toBeVisible();
    await expect(preview.locator('.fido-editor')).toHaveCSS('--mail-background', '#fffdf7');
    await page.getByRole('combobox', { name: 'Default outgoing encoding' }).selectOption('KOI8-R');
    await expect(preview.locator('.fido-editor small')).toContainText('KOI8-R');
    await page.getByLabel('Background', { exact: true }).fill('#123456');
    await expect(preview.locator('.fido-editor')).toHaveCSS('--mail-background', '#123456');
    await expect(preview.getByRole('button', { name: 'Save draft', exact: true })).toBeDisabled();
    await preview.getByRole('button', { name: 'Reading theme', exact: true }).click();
    await expect(preview.locator('.mail-body')).toHaveCSS('background-color', 'rgb(16, 24, 16)');
    await openStory(page, 'mail-mailappearancesettings--themes');
    await expect(reading).toHaveValue('paper');
    await expect(page.getByRole('combobox', { name: 'Default outgoing encoding' })).toHaveValue(
        'UTF-8'
    );
    await page.setViewportSize({ width: 390, height: 844 });
    const stackedControls = await controls.boundingBox();
    const stackedPreview = await preview.boundingBox();
    expect(stackedPreview!.y).toBeGreaterThanOrEqual(stackedControls!.y + stackedControls!.height);
});

test('mail appearance saves personal copies without overwriting operator palettes', async ({
    page
}) => {
    await openStory(page, 'mail-mailappearancesettings--themes');
    await page
        .getByRole('combobox', { name: 'Customize theme', exact: true })
        .selectOption('operator-green');
    await page.getByLabel('Theme name', { exact: true }).fill('My green');
    await page.getByRole('button', { name: 'Save personal theme' }).click();
    const choices = page.getByRole('combobox', { name: 'Editor theme', exact: true });
    await expect(choices.locator('option', { hasText: 'Operator green' })).toHaveCount(1);
    await expect(choices.locator('option', { hasText: 'My green' })).toHaveCount(1);
    expect(await choices.inputValue()).toMatch(/^custom-/);
});
test('template preview expands tokens and rejects unsupported file directives', async ({
    page
}) => {
    await openStory(page, 'mail-mailtemplatesettings--templates');
    const body = page.getByRole('textbox', { name: 'Template body', exact: true });
    await body.fill('Hello @tname.\n@quote\n@position');
    await expect(page.locator('.mail-body')).toContainText('Hello Bob.');
    await expect(page.locator('.mail-body')).toContainText('> Welcome to the conference!');
    await body.fill('@include /private');
    await expect(page.getByRole('button', { name: 'Save template' })).toBeDisabled();
    await expect(page.getByRole('status')).toContainText('Unsupported template token');
});

test('netmail preview switches between right, below and separate without losing the message', async ({
    page
}) => {
    await openStory(page, 'mail-netmailview--reading');
    await page.getByRole('button', { name: 'Display options', exact: true }).click();
    const position = page.getByRole('combobox', { name: 'Preview position', exact: true });
    await expect(page.locator('.split-pane')).toHaveClass(/right/);
    await position.selectOption('below');
    await expect(page.locator('.split-pane')).toHaveClass(/below/);
    await position.selectOption('separate');
    await expect(page.locator('.mail-body')).toBeVisible();
    await expect(page.locator('.separate-list')).toBeHidden();
    await page.getByRole('button', { name: 'Back to messages' }).click();
    await expect(page.locator('.separate-list')).toBeVisible();
});
test('mobile mail uses separate list and detail screens and protects a modified reply', async ({
    page
}) => {
    await page.setViewportSize({ width: 390, height: 844 });
    await openStory(page, 'mail-echomailview--reading');
    await expect(page.locator('.split-list')).toBeHidden();
    await page.getByRole('button', { name: 'Back to messages' }).click();
    await expect(page.locator('.split-list')).toBeVisible();
    await page.getByRole('button', { name: /^Welcome to the conference/ }).click();
    await page.getByRole('button', { name: 'Reply', exact: true }).click();
    await page.getByLabel('Subject', { exact: true }).fill('Unsaved changes');
    page.once('dialog', (dialog) => dialog.dismiss());
    await page.getByRole('button', { name: 'Back to messages' }).click();
    await expect(page.getByLabel('Subject', { exact: true })).toHaveValue('Unsaved changes');
});

test('conversation shows original dates for the parent and replies and opens a reply', async ({
    page
}) => {
    await openStory(page, 'mail-echomailview--conversation');
    const conversation = page.locator('.echo-conversation');
    await conversation.locator('summary').click();
    await expect(conversation.locator('button time')).toHaveText('20 Oct 26  10:00:00');
    await expect(conversation.locator('li time')).toHaveText('20 Oct 26  12:00:00');
    await conversation.getByRole('button', { name: /Carol Example/ }).click();
    await expect(page.locator('.mail-envelope')).toContainText('Carol Example');
});

test('split panes resize by keyboard, clamp boundaries and expose orientation', async ({
    page
}) => {
    await openStory(page, 'layout-splitpane--right');
    const separator = page.getByRole('separator');
    await expect(separator).toHaveAttribute('aria-orientation', 'vertical');
    await separator.focus();
    await separator.press('ArrowRight');
    await expect(separator).toHaveAttribute('aria-valuenow', '40');
    await separator.press('Home');
    await expect(separator).toHaveAttribute('aria-valuenow', '20');
    await separator.press('ArrowLeft');
    await expect(separator).toHaveAttribute('aria-valuenow', '20');
    await openStory(page, 'layout-splitpane--below');
    await expect(page.getByRole('separator')).toHaveAttribute('aria-orientation', 'horizontal');
});
test('collapsed navigation retains accessible names and point restrictions', async ({ page }) => {
    await openStory(page, 'navigation-sidebar--collapsed-point');
    await expect(page.getByRole('button', { name: 'Expand menu' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Echomail', exact: true })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Station', exact: true })).toHaveCount(0);
    await expect(page.getByRole('button', { name: 'Polling', exact: true })).toHaveCount(0);
});

test('polling is a main navigation destination in expanded and collapsed menus', async ({
    page
}) => {
    for (const language of ['en', 'uk']) {
        const name = language === 'en' ? 'Polling' : 'Пулінг';
        await openStory(page, 'navigation-sidebar--switching-pages', 'dark', language);
        const button = page.getByRole('button', { name, exact: true });
        await button.click();
        await expect(button).toHaveClass(/active/);
        await openStory(page, 'navigation-sidebar--collapsed-polling', 'dark', language);
        const collapsed = page.getByRole('button', { name, exact: true });
        await expect(collapsed).toHaveClass(/active/);
        await expect(collapsed).toHaveAttribute('title', `${name} (Alt+8)`);
        await expect(collapsed.locator('svg')).toBeVisible();
        await expect(collapsed.locator('.nav-text')).toBeHidden();
    }
});

test('forwarding copies original text into a new draft without reusing reply identifiers', async ({
    page
}) => {
    await openStory(page, 'mail-netmailview--reading');
    await page.locator('.reader-more > summary').click();
    await page.getByRole('button', { name: 'Forward', exact: true }).click();
    await expect(page.getByLabel('Subject', { exact: true })).toHaveValue(/^Fwd:/);
    await expect(page.getByRole('textbox', { name: 'Message text', exact: true })).toContainText(
        'Forwarded from'
    );
    await expect(page.locator('.mail-composer')).not.toContainText('Reply to message:');
    await expect(page.getByLabel('Recipient name', { exact: true })).toHaveValue('');
});

for (const width of [1280, 820, 375]) {
    test(`application mail panes fit a ${width}px viewport`, async ({ page }) => {
        await page.setViewportSize({ width, height: 900 });
        await openStory(page, 'mail-netmailview--application-workspace');
        await expect(page.locator('.mail-body')).toBeVisible();
        const overflow = await page.evaluate(
            () => document.documentElement.scrollWidth > window.innerWidth
        );
        expect(overflow).toBe(false);
    });
}

test('starting another draft reinitializes its template without retaining previous edits', async ({
    page
}) => {
    await openStory(page, 'mail-netmailview--reading');
    await page.getByRole('button', { name: 'Reply', exact: true }).click();
    const editor = page.getByRole('textbox', { name: 'Message text', exact: true });
    await editor.fill('Changed reply');
    page.once('dialog', (dialog) => dialog.accept());
    await page.getByRole('button', { name: 'New netmail', exact: true }).click();
    await expect(editor).toContainText('Hello there.');
    await expect(editor).not.toContainText('Changed reply');
    await expect(page.locator('.mail-composer')).not.toContainText('Reply to message:');
});

test('visual editor applies the shared link and text style palette', async ({ page }) => {
    await openStory(page, 'mail-fidoeditor--writing');
    await expect(page.locator('.editor-host .link')).toHaveCSS('color', 'rgb(128, 86, 0)');
    await expect(page.locator('.editor-host .bold')).toHaveCSS('font-weight', '750');
});

for (const kind of ['netmail', 'echomail']) {
    test(`${kind} composition fits the desktop reading pane`, async ({ page }) => {
        await page.setViewportSize({ width: 1280, height: 900 });
        await openStory(page, `mail-${kind}view--application-workspace`);
        await page.getByRole('button', { name: 'Reply', exact: true }).click();
        await expect(
            page.getByRole('textbox', { name: 'Message text', exact: true })
        ).toBeVisible();
        const fits = await page
            .locator('.mail-composer')
            .evaluate((element) => element.scrollWidth <= element.clientWidth);
        expect(fits).toBe(true);
        expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(
            true
        );
    });
}

test('desktop workspace uses compact icon controls and aligned message columns', async ({
    page
}) => {
    await page.setViewportSize({ width: 1536, height: 900 });
    await openStory(page, 'mail-echomailview--focused-desktop');
    await expect(page.locator('.topbar')).toHaveCSS('height', '44px');
    await expect(page.locator('.sidebar')).toHaveCSS('width', '216px');
    const compose = page.getByRole('button', { name: 'New echomail', exact: true });
    expect((await compose.boundingBox())!.height).toBeLessThanOrEqual(32);
    await expect(compose.locator('svg')).toBeVisible();
    await expect(page.locator('.mail-table-head')).toBeVisible();
    await expect(
        page.getByRole('button', { name: 'Conversation tree', exact: true })
    ).toHaveAttribute('aria-pressed', 'true');
    await page.getByRole('button', { name: 'Flat list', exact: true }).click();
    await expect(page.getByRole('treeitem')).toHaveCount(0);
    await expect(page.locator('.mail-date').first()).toBeVisible();
});

test('desktop More menu supports short replies and dismisses with Escape', async ({ page }) => {
    await openStory(page, 'mail-netmailview--reading');
    const menu = page.locator('.reader-more');
    await menu.locator('summary').click();
    await menu.locator('summary').press('Escape');
    await expect(menu).not.toHaveAttribute('open');
    await menu.locator('summary').click();
    await page.getByRole('button', { name: 'Short reply', exact: true }).click();
    const text = page.getByRole('textbox', { name: 'Message text', exact: true });
    await expect(text).toContainText('Hello Alice Example.');
    await expect(text).not.toContainText('Your station is ready');
    await expect(page.locator('.mail-composer')).toContainText('Reply to message');
});

for (const width of [1280, 390]) {
    test(`display options remain accessible after Escape at ${width}px`, async ({ page }) => {
        await page.setViewportSize({ width, height: 900 });
        await openStory(page, 'mail-netmailview--inbox');
        const options = page.locator('.mail-view-options');
        if (width > 700) await options.locator('summary').click();
        const position = page.getByRole('combobox', { name: 'Preview position', exact: true });
        await position.press('Escape');
        if (width > 700) {
            await expect(options).not.toHaveAttribute('open');
            await expect(options.locator('summary')).toBeFocused();
            await options.locator('summary').click();
        }
        await expect(position).toBeVisible();
        await position.selectOption('below');
        await expect(page.locator('.split-pane')).toHaveClass(/below/);
    });
}
