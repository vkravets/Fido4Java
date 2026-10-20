import { test, expect } from '@playwright/test';
import { openStory } from './story.ts';

test('split panes resize by keyboard, clamp boundaries and expose orientation', async ({
    page
}) => {
    await openStory(page, 'layout-splitpane--right');
    const separator = page.getByRole('separator');
    await expect(separator).toHaveAttribute('aria-orientation', 'vertical');
    await separator.focus();
    await separator.press('ArrowRight');
    await expect(separator).toHaveAttribute('aria-valuenow', '40');
    await separator.press('Home');
    await expect(separator).toHaveAttribute('aria-valuenow', '20');
    await separator.press('ArrowLeft');
    await expect(separator).toHaveAttribute('aria-valuenow', '20');
    await openStory(page, 'layout-splitpane--below');
    await expect(page.getByRole('separator')).toHaveAttribute('aria-orientation', 'horizontal');
});
test('collapsed navigation retains accessible names and point restrictions', async ({ page }) => {
    await openStory(page, 'navigation-sidebar--collapsed-point');
    await expect(page.getByRole('button', { name: 'Expand menu' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Echomail', exact: true })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Station', exact: true })).toHaveCount(0);
});

test('netmail preview switches between right, below and separate without losing the message', async ({
    page
}) => {
    await openStory(page, 'mail-netmailview--reading');
    const position = page.getByRole('combobox', { name: 'Preview position', exact: true });
    await expect(page.locator('.split-pane')).toHaveClass(/right/);
    await position.selectOption('below');
    await expect(page.locator('.split-pane')).toHaveClass(/below/);
    await position.selectOption('separate');
    await expect(page.locator('.mail-body')).toBeVisible();
    await expect(page.locator('.separate-list')).toBeHidden();
    await page.getByRole('button', { name: 'Back to messages' }).click();
    await expect(page.locator('.separate-list')).toBeVisible();
});
