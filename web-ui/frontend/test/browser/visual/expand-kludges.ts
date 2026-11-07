import { expect } from '@playwright/test';
import type { Page, TestInfo } from '@playwright/test';
import type { Screen } from './screens.ts';

export async function expandKludges(page: Page, screen: Screen, info: TestInfo) {
    if (screen.expandKludges) {
        await page.locator('.mail-kludge-list > summary').click();
        await expect(page.locator('.mail-kludge-list')).toHaveAttribute('open', '');
        const firstKludge = page.locator('.mail-kludge-list code').nth(1);
        await expect(firstKludge).toHaveText('^ANOTE: <img src=x onerror=alert(1)>');
        await expect(page.locator('.mail-kludge-list img')).toHaveCount(0);
        await expect(page.locator('.mail-kludge-list code').nth(2)).toHaveText(
            '^ACTRL: NUL=^@ BEL=^G ESC=^[ DEL=^? C1=\\x85'
        );
        const selectable = await firstKludge.evaluate((element) => {
            const range = document.createRange();
            range.selectNodeContents(element);
            const selection = window.getSelection();
            selection?.removeAllRanges();
            selection?.addRange(range);
            const selected = selection?.toString();
            selection?.removeAllRanges();
            return selected;
        });
        expect(selectable).toBe('^ANOTE: <img src=x onerror=alert(1)>');
    }
}
