import { expect } from '@playwright/test';
import type { Page, TestInfo } from '@playwright/test';
import type { Screen } from './screens.ts';

export async function suggestions(page: Page, screen: Screen, info: TestInfo) {
    if (screen.suggestions)
        await expect(page.locator('.address-options').getByRole('option').first()).toBeVisible();
}
