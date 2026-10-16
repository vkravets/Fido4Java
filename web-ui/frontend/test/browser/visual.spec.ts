import { test, expect } from '@playwright/test';
import type { TestInfo } from '@playwright/test';
import { existsSync } from 'node:fs';
import { openStory } from './story.ts';

const screens = [
    { name: 'administrator-navigation', story: 'navigation-sidebar--administrator' },
    { name: 'point-navigation', story: 'navigation-sidebar--point' },
    { name: 'station-settings', story: 'settings-nodeform--settings' },
    { name: 'poll-queue', story: 'connections-pollattempts--mixed-states' },
    { name: 'poll-details', story: 'connections-polldetails--failed' },
    { name: 'information', story: 'feedback-notificationbanner--information', notification: true },
    { name: 'warning', story: 'feedback-notificationbanner--warning', notification: true },
    { name: 'error', story: 'feedback-notificationbanner--error', notification: true }
];

async function attachBaseline(info: TestInfo, filename: string) {
    const path = info.snapshotPath(filename);
    if (!existsSync(path)) return;
    await info.attach('Baseline', { path, contentType: 'image/png' });
}

for (const theme of ['light', 'dark']) {
    for (const locale of ['en', 'uk']) {
        for (const screen of screens) {
            test(`${theme}/${locale}/${screen.name}`, async ({ page }, info) => {
                const filename = `${screen.name}-${theme}-${locale}.png`;
                await attachBaseline(info, filename);
                await page.clock.setFixedTime(new Date('2026-10-16T12:00:00Z'));
                await openStory(page, screen.story, theme, locale);
                await expect(page.locator('html')).toHaveAttribute('data-theme', theme);
                await expect(page.locator('html')).toHaveAttribute('lang', locale);
                if (screen.notification) {
                    await expect(page.locator('.message')).toBeVisible();
                    await expect(page).toHaveScreenshot(filename);
                    return;
                }
                await expect(page.locator('.storybook-stage')).toHaveScreenshot(filename);
            });
        }
    }
}
