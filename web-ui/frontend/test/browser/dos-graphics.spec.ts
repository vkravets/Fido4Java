import { test, expect } from '@playwright/test';
import { openStory } from './story.ts';
import { dosMail } from '../../stories/dos-mail-fixture.ts';

test('reader displays DOS symbols together with quotes and box drawing', async ({ page }) => {
    await openStory(page, 'mail-fidotext--dos-graphics');
    await expect(page.locator('.mail-body')).toContainText('♦ Sessions History');
    await expect(page.locator('.fido-text')).toContainText('☺ ☻ ♥ ♦ ♣ ♠ • ◘ ♂ ♀ ♫ ☼');
    await expect(page.locator('.quote1 .bold')).toHaveText('Quoted history');
    await expect(page.locator('.fido-text')).toContainText('┌──────────────┐');
});

test('editor displays DOS glyphs without changing source, deletion and undo preserve control codes', async ({
    page
}) => {
    await openStory(page, 'mail-fidoeditor--dos-graphics');
    const original = page.getByTestId('dos-original');
    const editor = page.getByRole('textbox', { name: 'Message text', exact: true });
    await expect(editor.locator('.dos-graphic').first()).toHaveText('♦');
    await expect(original).toHaveValue(dosMail);
    await editor.click();
    await editor.press('Control+Home');
    await editor.press('Shift+ArrowRight');
    await editor.press('Backspace');
    await expect(original).toHaveValue(dosMail.slice(1));
    await editor.press('Control+z');
    await expect(original).toHaveValue(dosMail);
    await page.getByRole('button', { name: 'Source text', exact: true }).click();
    await expect(editor.locator('.dos-graphic').first()).toHaveText('♦');
    await expect(original).toHaveValue(dosMail);
    await page.getByRole('button', { name: 'Preview', exact: true }).click();
    await expect(page.locator('.mail-body')).toContainText('♦ Sessions History');
    await expect(original).toHaveValue(dosMail);
});
