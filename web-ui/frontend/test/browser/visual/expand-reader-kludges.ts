import { expect } from '@playwright/test';
import type { Page, TestInfo } from '@playwright/test';
import type { Screen } from './screens.ts';

export async function expandReaderKludges(page: Page, screen: Screen, info: TestInfo) {
    if (screen.expandReaderKludges) {
        await expect(page.locator('.reader-kludge-button')).toHaveAttribute('aria-label', /.+/);
        await expect(page.locator('.mail-kludge-list code')).toHaveCount(5);
        await expect(page.locator('.mail-kludge-list code').last()).toContainText(
            '<b>*literal*</b> @quote long-value-'
        );
    }
}
