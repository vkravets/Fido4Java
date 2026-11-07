import { expect } from '@playwright/test';
import type { Page, TestInfo } from '@playwright/test';
import type { Screen } from './screens.ts';

export async function showComposerList(page: Page, screen: Screen, info: TestInfo) {
    if (screen.showComposerList && info.project.name === 'desktop') {
        await page.locator('.composer-list-toggle').click();
        await expect(page.locator('.split-list')).toBeVisible();
    }
}
