import { test, expect } from '@playwright/test';
import { openStory } from './story.ts';

test('one mail font updates both reader and editor previews without replacing text', async ({
    page
}) => {
    test.setTimeout(60000);
    for (const language of ['en', 'uk']) {
        for (const theme of ['light', 'dark']) {
            await openStory(page, 'mail-mailappearancesettings--themes', theme, language);
            const font = page.locator('.mail-typography');
            await expect(font).toHaveCount(1);
            const body = page.locator('.appearance-preview .mail-body');
            await font.locator('select').nth(0).selectOption('dejavu');
            await font.locator('select').nth(1).selectOption('16');
            await font.locator('select').nth(2).selectOption('1');
            await expect(body).toHaveCSS('font-size', '16px');
            await expect(body).toHaveCSS('line-height', '16px');
            await expect(body).toHaveCSS('font-family', '"DejaVu Sans Mono", monospace');
            const lines = body.locator('.fido-line');
            const top = (await lines.nth(0).boundingBox())!;
            const next = (await lines.nth(1).boundingBox())!;
            expect(next.y - top.y).toBeCloseTo(16, 1);
            await page.locator('.appearance-preview .tabs button').nth(1).click();
            await expect(page.locator('.cm-content')).toHaveCSS('font-size', '16px');
            await expect(page.locator('.cm-content')).toHaveCSS(
                'font-family',
                '"DejaVu Sans Mono", monospace'
            );
            await font.locator('select').nth(1).selectOption('18');
            await font.locator('select').nth(2).selectOption('1.2');
            const content = page.locator('.cm-content');
            await expect(content).toHaveCSS('font-size', '18px');
            await expect(content).toHaveCSS('line-height', '21.6px');
            await content.click();
            await page.keyboard.press('Control+End');
            await page.keyboard.type(' FONT-CHECK');
            const text = await content.innerText();
            await font.locator('select').nth(0).selectOption('courier');
            await font.locator('select').nth(1).selectOption('20');
            await expect.poll(() => content.innerText()).toBe(text);
            await expect(content).toHaveCSS('font-size', '20px');
            await content.focus();
            await page.keyboard.press('Control+z');
            await expect(content).not.toContainText('FONT-CHECK');
            await page.locator('.appearance-preview .tabs button').nth(0).click();
            await expect(body).toHaveCSS('font-size', '20px');
            await expect(body).toHaveCSS('font-family', '"Courier New", monospace');
            await expect(body).toHaveCSS('line-height', '24px');
            await font.locator('select').nth(1).selectOption('');
            await font.locator('select').nth(2).selectOption('');
            await expect(body).toHaveCSS('font-size', '14px');
            await expect(body).toHaveCSS('line-height', '22.4px');
        }
    }
});
