import { test, expect } from '@playwright/test';
import { openStory } from './story.ts';

test('question mark opens modal help, traps focus and preserves typing and underlying actions', async ({
    page
}) => {
    await openStory(page, 'navigation-shortcuthelp--closed');
    const open = page.getByRole('button', { name: 'Open help', exact: true });
    await open.focus();
    await page.keyboard.press('?');
    const dialog = page.getByRole('dialog', { name: 'Keyboard shortcuts', exact: true });
    await expect(dialog).toBeVisible();
    const close = dialog.getByRole('button', { name: 'Close shortcut help' });
    await expect(close).toBeFocused();
    await page.keyboard.press('Tab');
    await expect(dialog.locator('.help-content')).toBeFocused();
    await page.keyboard.press('Tab');
    await expect(close).toBeFocused();
    await page.keyboard.press('c');
    await expect(page.getByText('Actions: 0', { exact: true })).toBeVisible();
    await page.keyboard.press('Escape');
    await expect(dialog).not.toBeVisible();
    await expect(open).toBeFocused();
    for (const label of ['Message text', 'Rich text']) {
        const field = page.getByRole('textbox', { name: label, exact: true });
        await field.fill('');
        await field.press('?');
        await expect(dialog).not.toBeVisible();
        if (label === 'Message text') await expect(field).toHaveValue('?');
        else await expect(field).toHaveText('?');
    }
    await open.focus();
    await page.keyboard.press('?');
    await close.click();
    await expect(dialog).not.toBeVisible();
    await expect(open).toBeFocused();
});

test('help shows personal and GoldED bindings, and remains available when shortcuts are disabled', async ({
    page
}) => {
    await openStory(page, 'navigation-shortcuthelp--personal');
    const dialog = page.getByRole('dialog', { name: 'Keyboard shortcuts', exact: true });
    await expect(dialog.locator('[data-help-action="mail.reply"]')).toContainText('Alt+Shift+Q');
    await expect(dialog.locator('[data-help-action="mail.delete"]')).toContainText('Not assigned');
    await expect(dialog.locator('[data-help-action="nav.station"]')).toHaveCount(0);
    await openStory(page, 'navigation-shortcuthelp--golded');
    await expect(dialog.locator('[data-help-action="mail.reply"] kbd')).toHaveText([
        'Q',
        'F4',
        'Alt+Q'
    ]);
    await openStory(page, 'navigation-shortcuthelp--disabled');
    await page.getByRole('button', { name: 'Open help', exact: true }).focus();
    await page.keyboard.press('?');
    await expect(dialog).toContainText('Keyboard shortcuts are disabled.');
    await page.keyboard.press('Escape');
    await page.keyboard.press('c');
    await expect(page.getByText('Actions: 0', { exact: true })).toBeVisible();
});

test('help stays centered within a short mobile viewport and scrolls its own content', async ({
    page
}) => {
    await page.setViewportSize({ width: 375, height: 540 });
    await openStory(page, 'navigation-shortcuthelp--default', 'dark', 'uk');
    const dialog = page.getByRole('dialog');
    await expect(dialog).toBeVisible();
    const bounds = await dialog.boundingBox();
    expect(Math.abs(bounds!.x + bounds!.width / 2 - 375 / 2)).toBeLessThan(2);
    expect(Math.abs(bounds!.y + bounds!.height / 2 - 540 / 2)).toBeLessThan(2);
    expect(bounds!.y).toBeGreaterThanOrEqual(15);
    expect(bounds!.height).toBeLessThanOrEqual(510);
    const content = dialog.locator('.help-content');
    await content.evaluate((element) => {
        element.scrollTop = element.scrollHeight;
    });
    expect(await content.evaluate((element) => element.scrollTop)).toBeGreaterThan(0);
    await expect(dialog.locator('[data-help-action="editor.preview"]')).toBeInViewport();
    await expect(dialog.getByRole('button')).toBeInViewport();
});

for (const story of [
    'mail-netmailview--long-reading',
    'mail-netmailview--long-reading-below',
    'mail-echomailview--focused-desktop'
]) {
    test(`keyboard selection stays visible without scrolling the reader: ${story}`, async ({
        page
    }) => {
        await page.setViewportSize({
            width: 1280,
            height: story.includes('echomail') ? 540 : 700
        });
        await openStory(page, story);
        const rows = page.locator('[data-shortcut-row]');
        await rows.first().click();
        const reader = page.locator('.mail-message');
        await expect(reader).not.toHaveAttribute('aria-busy', 'true');
        const list = page.locator('.split-list');
        const detail = page.locator('.split-detail');
        await detail.evaluate((element) => (element.scrollTop = 20));
        const detailTop = await detail.evaluate((element) => element.scrollTop);
        const pageTop = await page.evaluate(() => window.scrollY);
        const count = Math.min(await rows.count(), 22);
        const selectionVisible = () =>
            list.evaluate((element) => {
                const row = element
                    .querySelector('[data-shortcut-selected]')!
                    .getBoundingClientRect();
                const bounds = element.getBoundingClientRect();
                const tools = element.querySelector('.mail-list-tools')!.getBoundingClientRect();
                return (
                    row.top >= Math.max(bounds.top, tools.bottom) - 1 &&
                    row.bottom <= bounds.top + element.clientTop + element.clientHeight + 1
                );
            });
        for (const [key, indices] of [
            ['ArrowRight', Array.from({ length: count - 1 }, (_, i) => i + 1)],
            ['ArrowLeft', Array.from({ length: count - 1 }, (_, i) => count - i - 2)]
        ] as const) {
            for (const index of indices) {
                await page.keyboard.press(key);
                await expect(rows.nth(index)).toHaveAttribute('data-shortcut-selected', '');
                await expect(reader).not.toHaveAttribute('aria-busy', 'true');
                await expect.poll(selectionVisible).toBe(true);
                await expect(reader).toBeFocused();
                expect(await detail.evaluate((element) => element.scrollTop)).toBe(detailTop);
                expect(await page.evaluate(() => window.scrollY)).toBe(pageTop);
            }
            if (key === 'ArrowRight')
                expect(await list.evaluate((element) => element.scrollTop)).toBeGreaterThan(0);
        }
    });
}

test('keyboard message changes keep the list enabled and previous preview mounted during a slow read', async ({
    page
}) => {
    for (const [story, key] of [
        ['mail-netmailview--switching-message', 'ArrowRight'],
        ['mail-echomailview--switching-message', 'ArrowRight'],
        ['mail-echomailview--switching-flat-message', 'ArrowRight']
    ] as const) {
        await openStory(page, story);
        const next = page.locator('[data-shortcut="mail.next"]');
        await expect(next).toBeEnabled();
        const body = page.locator('.mail-body');
        const original = await body.textContent();
        const list = page.locator('.split-list');
        const listNode = (await list.elementHandle())!;
        const reader = page.locator('.mail-message');
        const controls = reader.locator('button');
        const appearance = () =>
            controls.evaluateAll((buttons) =>
                buttons.map((button) => {
                    const style = getComputedStyle(button);
                    return {
                        opacity: style.opacity,
                        color: style.color,
                        background: style.backgroundColor,
                        disabled: (button as HTMLButtonElement).disabled
                    };
                })
            );
        const originalAppearance = await appearance();
        await list.locator('[data-shortcut-selected]').focus();
        await page.keyboard.press(key);
        await expect(next).toBeDisabled();
        await expect(reader).toBeFocused();
        await expect(reader).toHaveAttribute('aria-busy', 'true');
        await expect(list.locator(':focus-visible')).toHaveCount(0);
        const consumed = await reader.evaluate((element) =>
            [
                { key: 'ArrowRight', code: 'ArrowRight', repeat: true },
                { key: 'u', code: 'KeyU', repeat: false }
            ].map((keys) => {
                const event = new KeyboardEvent('keydown', {
                    ...keys,
                    bubbles: true,
                    cancelable: true
                });
                element.dispatchEvent(event);
                return event.defaultPrevented;
            })
        );
        expect(consumed).toEqual([true, true]);
        expect(await appearance()).toEqual(originalAppearance);
        await expect(list.locator('button:disabled')).toHaveCount(0);
        await expect(body).toHaveText(original!);
        expect(await listNode.evaluate((element) => element.isConnected)).toBe(true);
        await page.keyboard.press('r');
        await expect(page.locator('.mail-composer')).toHaveCount(0);
        await reader
            .locator('[data-shortcut="mail.reply"]')
            .first()
            .evaluate((button) => (button as HTMLButtonElement).click());
        await expect(page.locator('.mail-composer')).toHaveCount(0);
        await page.keyboard.press('c');
        await expect(page.locator('.mail-composer')).toBeVisible();
        await expect(
            page.getByRole('textbox', { name: 'Message text', exact: true })
        ).toHaveAttribute('aria-readonly', 'false');
    }
});

test('reader focus follows consecutive message changes without leaving a second list highlight', async ({
    page
}) => {
    await openStory(page, 'mail-echomailview--reading');
    const selected = page.locator('[data-shortcut-selected]');
    const original = await selected.textContent();
    await selected.focus();
    await page.keyboard.press('ArrowRight');
    await expect(selected).not.toHaveText(original!);
    const reader = page.locator('.mail-message');
    await expect(reader).not.toHaveAttribute('aria-busy', 'true');
    await expect(reader).toBeFocused();
    await page.keyboard.press('ArrowLeft');
    await expect(selected).toHaveText(original!);
    await expect(reader).not.toHaveAttribute('aria-busy', 'true');
    await expect(reader).toBeFocused();
    await expect(page.locator('.split-list :focus-visible')).toHaveCount(0);
});

test('echo reader buttons and shortcuts traverse the visible tree in both directions', async ({
    page
}) => {
    await openStory(page, 'mail-echomailview--tree');
    const rows = page.locator('[data-mail-row]');
    const order = await rows.evaluateAll((elements) =>
        elements.map((row) => row.getAttribute('data-mail-row'))
    );
    expect(order).toEqual([
        '9007199254740996',
        '9007199254740993',
        '9007199254740994',
        '9007199254740995'
    ]);
    await rows.first().locator('[data-shortcut-row]').click();
    const selected = page.locator('[data-mail-row].selected');
    const reader = page.locator('.mail-message');
    const next = reader.locator('[data-shortcut="mail.next"]');
    const previous = reader.locator('[data-shortcut="mail.previous"]');
    for (let index = 0; index < order.length; index++) {
        await expect(selected).toHaveAttribute('data-mail-row', order[index]!);
        await expect(reader).not.toHaveAttribute('aria-busy', 'true');
        if (index === order.length - 1) break;
        if (index % 2) await next.click();
        else await page.keyboard.press('ArrowRight');
    }
    await expect(next).toBeDisabled();
    for (let index = order.length - 2; index >= 0; index--) {
        if (index % 2) await previous.click();
        else await page.keyboard.press('ArrowLeft');
        await expect(selected).toHaveAttribute('data-mail-row', order[index]!);
        await expect(reader).not.toHaveAttribute('aria-busy', 'true');
    }
    await expect(previous).toBeDisabled();
});

test('echo reader navigation follows collapsed branches, list mode and search results', async ({
    page
}) => {
    await openStory(page, 'mail-echomailview--tree');
    await page.getByRole('button', { name: 'Collapse conversation' }).first().click();
    await page.locator('[data-shortcut-row]').first().click();
    const reader = page.locator('.mail-message');
    const selected = page.locator('[data-mail-row].selected');
    const next = reader.locator('[data-shortcut="mail.next"]');
    await page.keyboard.press('j');
    await expect(selected).toHaveAttribute('data-mail-row', '9007199254740993');
    await expect(reader).not.toHaveAttribute('aria-busy', 'true');
    await expect(next).toBeDisabled();
    await page.getByRole('button', { name: 'Flat list', exact: true }).click();
    await expect(page.getByRole('treeitem')).toHaveCount(0);
    await expect(next).toBeDisabled();
    await page.keyboard.press('ArrowLeft');
    await expect(selected).toHaveAttribute('data-mail-row', '9007199254740994');
    await expect(reader).not.toHaveAttribute('aria-busy', 'true');
    await page.getByRole('button', { name: 'Conversation tree', exact: true }).click();
    await expect(page.getByRole('treeitem')).toHaveCount(2);
    await expect(next).toBeDisabled();
    await expect(reader.locator('[data-shortcut="mail.previous"]')).toBeDisabled();
    await page.getByRole('button', { name: 'Expand conversation' }).click();
    await expect(next).toBeEnabled();
    await page.getByRole('searchbox', { name: 'Search messages' }).fill('Welcome');
    await expect(page.locator('[data-mail-row]')).toHaveCount(3);
    await next.click();
    await expect(selected).toHaveAttribute('data-mail-row', '9007199254740995');
    await expect(reader).not.toHaveAttribute('aria-busy', 'true');
    await expect(next).toBeDisabled();
    await page.keyboard.press('ArrowLeft');
    await expect(selected).toHaveAttribute('data-mail-row', '9007199254740994');
});

test('deleting from a shortcut keeps the existing confirmation guard', async ({ page }) => {
    await openStory(page, 'mail-netmailview--reading');
    const remove = page.locator('[data-shortcut="mail.delete"]').first();
    await expect(remove).toBeEnabled();
    let confirmed = false;
    page.once('dialog', async (dialog) => {
        confirmed = true;
        await dialog.dismiss();
    });
    await page.keyboard.press('Delete');
    expect(confirmed).toBe(true);
    await expect(page.locator('.mail-message')).toBeVisible();
    await expect(page.locator('.mail-row')).toHaveCount(1);
});

test('GoldED function keys use short reply and submit the existing composer', async ({ page }) => {
    await openStory(page, 'mail-netmailview--golded-reading');
    await expect(
        page.locator('.reader-actions').getByRole('button', { name: 'Reply', exact: true })
    ).toBeEnabled();
    await page.keyboard.press('F3');
    const editor = page.getByRole('textbox', { name: 'Message text', exact: true });
    await expect(editor).toBeVisible();
    await expect(editor).not.toContainText('> ');
    await editor.fill('A short reply.');
    await page.keyboard.press('F2');
    await expect(page.locator('.mail-composer')).toHaveCount(0);
    await expect(page.getByRole('button', { name: 'Outbox', exact: true })).toHaveAttribute(
        'aria-current',
        'page'
    );
});

test('reader shortcuts reuse reply and cancel actions without firing while typing', async ({
    page
}) => {
    await openStory(page, 'mail-netmailview--reading');
    const search = page.getByRole('searchbox', { name: 'Search messages', exact: true });
    await search.fill('');
    await search.press('r');
    await expect(page.locator('.mail-composer')).toHaveCount(0);
    await search.fill('');
    await search.blur();
    await page.keyboard.press('r');
    await expect(page.locator('.mail-composer')).toBeVisible();
    await expect(page.getByRole('textbox', { name: 'Message text', exact: true })).toContainText(
        '> '
    );
    page.on('dialog', (dialog) => dialog.accept());
    await page.keyboard.press('Escape');
    await expect(page.locator('.mail-composer')).toHaveCount(0);
});
test('GoldED quoted reply and browser-history chord work with the web reader', async ({ page }) => {
    await openStory(page, 'mail-netmailview--golded-reading');
    const reply = page
        .locator('.reader-actions')
        .getByRole('button', { name: 'Reply', exact: true });
    await expect(reply).toHaveAttribute('aria-keyshortcuts', 'Q F4 Alt+Q');
    await page.keyboard.press('q');
    await expect(page.locator('.mail-composer')).toBeVisible();
    await expect(page.getByRole('textbox', { name: 'Message text', exact: true })).toContainText(
        '> '
    );
    page.on('dialog', (dialog) => dialog.accept());
    await page.keyboard.press('Escape');
    const before = page.url();
    await page.keyboard.press('Alt+ArrowRight');
    await expect(page.locator('.reader-heading h2')).toHaveText('Discussion 1');
    expect(page.url()).toBe(before);
});
test('editable assignments detect conflicts, can be cleared and preserve unsaved-change guards', async ({
    page
}) => {
    await openStory(page, 'settings-accountsettings--keyboard');
    const field = page.getByRole('textbox', { name: 'Quoted reply', exact: true });
    await field.focus();
    await field.press('Alt+1');
    await expect(
        page.getByRole('button', { name: 'Save preferences', exact: true })
    ).toBeDisabled();
    await expect(
        page.getByText('Shortcut is reserved or conflicts with another action: Alt+1')
    ).toBeVisible();
    await field.click();
    await field.press('Alt+Shift+q');
    await expect(page.getByRole('button', { name: 'Save preferences', exact: true })).toBeEnabled();
    page.once('dialog', (dialog) => dialog.dismiss());
    await page.getByRole('tab', { name: 'Profile', exact: true }).click();
    await expect(page.locator('.keyboard-settings')).toBeVisible();
    await page.getByRole('button', { name: 'Save preferences', exact: true }).click();
    await expect(
        page.getByRole('button', { name: 'Save preferences', exact: true })
    ).toBeDisabled();
    await page.getByRole('button', { name: 'Clear shortcut: Quoted reply', exact: true }).click();
    await expect(field).toHaveValue('');
});
test('editor formatting follows the configured toolbar and save uses the draft button', async ({
    page
}) => {
    await openStory(page, 'mail-netmailview--reading');
    await page.keyboard.press('r');
    const editor = page.getByRole('textbox', { name: 'Message text', exact: true });
    await editor.fill('test');
    await editor.press('Control+a');
    await editor.press('Control+b');
    await expect(editor).toContainText('*test*');
    await editor.press('Control+s');
    await expect(page.getByRole('button', { name: 'Save draft', exact: true })).toBeEnabled();
    await page.keyboard.press('Escape');
    await expect(page.locator('.mail-composer')).toHaveCount(0);
    await page.getByRole('button', { name: 'Drafts', exact: true }).click();
    await expect(page.locator('.mail-draft-row')).toHaveCount(2);
});

test('echomail shortcuts honor posting rights and the editor does not run reader letters', async ({
    page
}) => {
    await openStory(page, 'mail-echomailview--reading');
    page.on('dialog', (dialog) => dialog.accept());
    await page.keyboard.press('r');
    await expect(page.locator('.mail-composer')).toBeVisible();
    const editor = page.getByRole('textbox', { name: 'Message text', exact: true });
    await editor.fill('rqc');
    await expect(editor).toContainText('rqc');
    await expect(page.locator('.mail-composer')).toHaveCount(1);
    await openStory(page, 'mail-echomailview--read-only');
    await page.keyboard.press('c');
    await expect(page.locator('.mail-composer')).toHaveCount(0);
});

test('navigation shortcuts follow the same permitted sidebar destinations', async ({ page }) => {
    await openStory(page, 'navigation-sidebar--switching-pages');
    await page.keyboard.press('Alt+2');
    await expect(page.getByRole('button', { name: 'Echomail', exact: true })).toHaveClass(/active/);
    await openStory(page, 'navigation-sidebar--point');
    await page.keyboard.press('Alt+6');
    await expect(page.getByRole('button', { name: 'Station', exact: true })).toHaveCount(0);
});
