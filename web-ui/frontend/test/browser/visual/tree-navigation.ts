import { expect } from '@playwright/test';
import type { Page, TestInfo } from '@playwright/test';
import type { Screen } from './screens.ts';

export async function treeNavigation(page: Page, screen: Screen, info: TestInfo) {
    if (screen.treeNavigation) {
        await expect(page.locator('[data-shortcut="mail.next"]')).toBeEnabled();
        await page.keyboard.press('j');
        await expect(page.locator('.mail-body')).toContainText('Thanks Alice');
        await expect(page.locator('.mail-message')).not.toHaveAttribute('aria-busy', 'true');
        await page.keyboard.press('ArrowRight');
        await expect(page.locator('.mail-body')).toContainText('Welcome Bob');
        await expect(page.locator('.mail-message')).not.toHaveAttribute('aria-busy', 'true');
        await expect(page.locator('[data-shortcut="mail.next"]')).toBeDisabled();
    }
}
