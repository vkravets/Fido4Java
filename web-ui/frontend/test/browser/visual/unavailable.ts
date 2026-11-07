import { expect } from '@playwright/test';
import type { Page, TestInfo } from '@playwright/test';
import type { Screen } from './screens.ts';

export async function unavailable(page: Page, screen: Screen, info: TestInfo) {
    if (screen.unavailable)
        await expect(page.locator('.address-options [role=status]')).toBeVisible();
}
