import { expect } from '@playwright/test';
import type { Page, TestInfo } from '@playwright/test';
import type { Screen } from './screens.ts';

export async function profileDirectory(page: Page, screen: Screen, info: TestInfo) {
    if (screen.profileDirectory) {
        await page.locator('.profile-name-search').click();
        await expect(page.locator('.profile-name-matches button')).toHaveCount(2);
    }
}
