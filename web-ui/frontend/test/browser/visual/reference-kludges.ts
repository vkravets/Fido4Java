import { expect } from '@playwright/test';
import type { Page, TestInfo } from '@playwright/test';
import type { Screen } from './screens.ts';

export async function referenceKludges(page: Page, screen: Screen, info: TestInfo) {
    if (screen.referenceKludges) {
        await page.locator('.mail-kludge-list > summary').click();
        await expect(page.locator('.mail-kludge-list code').last()).toContainText('REFERENCE_ONLY');
        await expect(page.locator('.cm-content')).not.toContainText('REFERENCE_ONLY');
    }
}
