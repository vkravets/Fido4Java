import { expect } from '@playwright/test';
import type { Page, TestInfo } from '@playwright/test';
import type { Screen } from './screens.ts';

export async function mailSearch(page: Page, screen: Screen, info: TestInfo) {
    if (screen.mailSearch) {
        const search = page.locator('.mail-query input');
        await search.focus();
        await search.pressSequentially('Welcome');
        await expect(page.locator('.mail-search-status')).toBeVisible();
        await expect(search).toBeFocused();
        if (screen.name !== 'mail-search-pending') {
            await expect(page.locator('.mail-search-status')).not.toContainText(
                /Searching messages|Пошук листів/
            );
        }
    }
}
