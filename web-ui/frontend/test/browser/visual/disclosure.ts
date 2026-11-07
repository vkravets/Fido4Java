import { expect } from '@playwright/test';
import type { Page, TestInfo } from '@playwright/test';
import type { Screen } from './screens.ts';

export async function disclosure(page: Page, screen: Screen, info: TestInfo) {
    if (screen.disclosure && info.project.name === 'desktop')
        await expect(page.locator(screen.disclosure)).toBeVisible();
}
