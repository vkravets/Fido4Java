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

test('mobile Back restores the previous list scroll position', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    await openStory(page, 'mail-netmailview--long-list');
    const row = page.getByRole('button', { name: /^Discussion 25 / });
    await row.scrollIntoViewIfNeeded();
    const before = await page.evaluate(() => window.scrollY);
    expect(before).toBeGreaterThan(300);
    await row.click();
    await expect(page.locator('.split-list')).toBeHidden();
    await page.getByRole('button', { name: 'Back to messages' }).click();
    await expect.poll(() => page.evaluate(() => window.scrollY)).toBeGreaterThan(before - 20);
});

test('mobile mail uses separate list and detail screens and protects a modified reply', async ({
    page
}) => {
    await page.setViewportSize({ width: 390, height: 844 });
    await openStory(page, 'mail-echomailview--reading');
    await expect(page.locator('.split-list')).toBeHidden();
    await page.getByRole('button', { name: 'Back to messages' }).click();
    await expect(page.locator('.split-list')).toBeVisible();
    await page.getByRole('button', { name: /^Welcome to the conference/ }).click();
    await page.getByRole('button', { name: 'Reply', exact: true }).click();
    await page.getByLabel('Subject', { exact: true }).fill('Unsaved changes');
    page.once('dialog', (dialog) => dialog.dismiss());
    await page.getByRole('button', { name: 'Back to messages' }).click();
    await expect(page.getByLabel('Subject', { exact: true })).toHaveValue('Unsaved changes');
});
