import { expect } from '@playwright/test';
import type { Page } from '@playwright/test';

export async function openStory(page: Page, id: string, theme = 'light', locale = 'en') {
    await page.goto(`/iframe.html?id=${id}&viewMode=story&globals=theme:${theme};locale:${locale}`);
    await expect(page.locator('.storybook-stage')).toBeVisible();
    await page.evaluate(() => document.fonts.ready);
}
