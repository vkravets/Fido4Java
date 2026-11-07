import { expect } from '@playwright/test';
import type { Page, TestInfo } from '@playwright/test';
import type { Screen } from './screens.ts';

export async function conversation(page: Page, screen: Screen, info: TestInfo) {
    if (screen.conversation) {
        await page.locator('.echo-conversation > summary').click();
        await expect(page.locator('.echo-conversation li time').first()).toBeVisible();
    }
}
