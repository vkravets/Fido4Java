import { test, expect } from '@playwright/test';
import { openStory } from './story.ts';

test('keyboard message changes keep the list enabled and previous preview mounted during a slow read', async ({
    page
}) => {
    for (const [story, key] of [
        ['mail-netmailview--switching-message', 'ArrowRight'],
        ['mail-echomailview--switching-message', 'j'],
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
    await page.keyboard.press('j');
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
    const search = page.getByRole('searchbox', { name: 'Search loaded messages', exact: true });
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
