import { expect } from '@playwright/test';
import type { Page } from '@playwright/test';
import type { Screen } from './screens.ts';

export async function capture(page: Page, screen: Screen, filename: string) {
    if (screen.notification) {
        await expect(page.locator('.message')).toBeVisible();
        await expect(page).toHaveScreenshot(filename);
        return;
    }
    if (screen.modal) {
        await expect(page.getByRole('dialog')).toBeVisible();
        await expect(page).toHaveScreenshot(filename);
        return;
    }
    if (screen.scroll) {
        await page.evaluate((offset) => window.scrollTo(0, offset), screen.scroll);
        await expect.poll(() => page.evaluate(() => window.scrollY)).toBe(screen.scroll);
        await expect(page.locator('.sb-errordisplay')).toBeHidden();
        await expect(page).toHaveScreenshot(filename);
        return;
    }
    if (screen.name === 'mail-local-search') {
        const search = page.locator('.mail-query input');
        await search.focus();
        await expect(search).toBeFocused();
    }
    await expect(page.locator('.storybook-stage')).toHaveScreenshot(filename);
    await expect(page.locator('.sb-errordisplay')).toBeHidden();
}
