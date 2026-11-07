import { expect } from '@playwright/test';
import type { Page, TestInfo } from '@playwright/test';
import type { Screen } from './screens.ts';

export async function scrollSelection(page: Page, screen: Screen, info: TestInfo) {
    if (screen.scrollSelection) {
        const fixedHeader =
            screen.name === 'echo-scrolled-selection' && info.project.name === 'desktop'
                ? await Promise.all(
                      ['.echo-list-controls', '.mail-table-head'].map(
                          async (selector) => (await page.locator(selector).boundingBox())?.y
                      )
                  )
                : [];
        for (let index = 1; index <= screen.scrollSelection; index++) {
            await expect(page.locator('[data-shortcut="mail.next"]')).toBeEnabled();
            await page.keyboard.press('ArrowRight');
            await expect(page.locator('[data-shortcut-row]').nth(index)).toHaveAttribute(
                'data-shortcut-selected',
                ''
            );
            await expect(page.locator('.mail-message')).not.toHaveAttribute('aria-busy', 'true');
        }
        if (fixedHeader.length) {
            await expect(page.locator('.echo-list-controls')).toBeInViewport();
            await expect(page.locator('.mail-table-head')).toBeInViewport();
            const updatedHeader = await Promise.all(
                ['.echo-list-controls', '.mail-table-head'].map(
                    async (selector) => (await page.locator(selector).boundingBox())?.y
                )
            );
            expect(updatedHeader).toEqual(fixedHeader);
        }
    }
}
