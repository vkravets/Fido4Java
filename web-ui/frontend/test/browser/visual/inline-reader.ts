import { expect } from '@playwright/test';
import type { Page, TestInfo } from '@playwright/test';
import type { Screen } from './screens.ts';

export async function inlineReader(page: Page, screen: Screen, info: TestInfo) {
    if (screen.inlineReader) {
        await expect(page.locator('.reader-kludge-button')).toHaveAttribute('aria-pressed', 'true');
        await expect(page.locator('.mail-body .kludge')).toHaveCount(5);
    }
}
