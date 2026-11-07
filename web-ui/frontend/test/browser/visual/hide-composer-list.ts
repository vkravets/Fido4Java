import { expect } from '@playwright/test';
import type { Page, TestInfo } from '@playwright/test';
import type { Screen } from './screens.ts';

export async function hideComposerList(page: Page, screen: Screen, info: TestInfo) {
    if (screen.hideComposerList && info.project.name === 'desktop') {
        await expect(page.locator('.split-list')).toBeHidden();
        await expect(page.locator('.split-handle')).toBeHidden();
    }
}
