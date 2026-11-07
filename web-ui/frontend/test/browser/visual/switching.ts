import { expect } from '@playwright/test';
import type { Page, TestInfo } from '@playwright/test';
import type { Screen } from './screens.ts';

export async function switching(page: Page, screen: Screen, info: TestInfo) {
    if (screen.switching) {
        const next = page.locator('[data-shortcut="mail.next"]');
        await expect(next).toBeEnabled();
        if (info.project.name === 'desktop') await page.locator('[data-shortcut-selected]').focus();
        await page.keyboard.press('j');
        await expect(next).toBeDisabled();
        await expect(page.locator('.mail-message')).toBeFocused();
    }
}
