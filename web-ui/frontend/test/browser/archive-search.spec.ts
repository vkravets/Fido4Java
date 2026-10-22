import { test, expect } from '@playwright/test';
import { openStory } from './story.ts';

for (const kind of ['netmail', 'echo']) {
    const prefix = kind === 'echo' ? 'mail-echomailview' : 'mail-netmailview';
    for (const mode of ['archive', 'local']) {
        test(`${kind} ${mode} search keeps opening, keyboard navigation and clearing consistent`, async ({
            page
        }) => {
            await openStory(page, `${prefix}--${mode}-search`);
            const input = page.locator('.mail-query input');
            const rows = page.locator('[data-shortcut-row]');
            await expect(rows.first()).toBeVisible();
            const initialCount = await rows.count();
            await input.fill('Welcome');
            await expect(page.locator('.mail-search-status')).toContainText(
                mode === 'archive' ? 'Archive search complete.' : 'Searching loaded messages only.'
            );
            await expect(rows).toHaveCount(mode === 'archive' ? 1 : kind === 'echo' ? 3 : 1);
            await rows.first().click();
            await expect(page.locator('.mail-body')).toBeVisible();
            if (mode === 'archive') {
                await expect(page.locator('.mail-body')).toContainText('Welcome from the archive.');
                await expect(page.locator('.mail-body mark')).toContainText('Welcome');
            }
            await expect(page.locator('[data-shortcut="mail.previous"]')).toBeDisabled();
            if (mode === 'archive' || kind === 'netmail') {
                await expect(page.locator('[data-shortcut="mail.next"]')).toBeDisabled();
            } else {
                await page.keyboard.press('ArrowRight');
                await expect(rows.nth(1)).toHaveAttribute('data-shortcut-selected', '');
            }
            await input.fill('');
            await expect(rows).toHaveCount(initialCount);
            await expect(page.locator('.mail-search-status')).toHaveCount(0);
        });
    }
    test(`${kind} search continues after an empty bounded page`, async ({ page }) => {
        await openStory(page, `${prefix}--search-continuation`);
        await page.locator('.mail-query input').fill('Welcome');
        await expect(page.locator('.mail-search-status')).toContainText('Load more to continue.');
        await expect(page.locator('[data-shortcut-row]')).toHaveCount(0);
        await page.getByRole('button', { name: 'Load more', exact: true }).click();
        await expect(page.locator('[data-shortcut-row]')).toHaveCount(1);
        await expect(page.locator('.mail-search-status')).toHaveText('Archive search complete.');
    });
}

test('search errors allow retry and keep normal controls enabled', async ({ page }) => {
    await openStory(page, 'mail-netmailview--search-failure');
    await page.locator('.mail-query input').fill('Welcome');
    await expect(page.locator('.mail-search-status')).toContainText('Search failed.');
    await expect(page.getByText('No matching messages.', { exact: true })).toHaveCount(0);
    await expect(page.getByRole('button', { name: 'Load more', exact: true })).toBeEnabled();
    await expect(page.getByRole('button', { name: 'New netmail', exact: true })).toBeEnabled();
    await page.locator('.mail-query input').fill('');
    await expect(page.locator('[data-shortcut-row]')).toHaveCount(1);
});

test('an in-flight archive search can be cleared without blocking the reader', async ({ page }) => {
    await openStory(page, 'mail-netmailview--search-pending');
    await page.clock.install();
    await page.locator('.mail-query input').fill('Welcome');
    await page.clock.runFor(350);
    await expect(page.locator('.mail-search-status')).toHaveText('Searching messages…');
    await expect(page.getByText('No matching messages.', { exact: true })).toHaveCount(0);
    await expect(page.getByRole('button', { name: 'New netmail', exact: true })).toBeEnabled();
    await page.locator('.mail-query input').fill('');
    await expect(page.locator('[data-shortcut-row]')).toHaveCount(1);
    await page.locator('[data-shortcut-row]').click();
    await expect(page.locator('.mail-body')).toBeVisible();
});
