import { test, expect } from '@playwright/test';
import { openStory } from './story.ts';

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
