import { test, expect } from '@playwright/test';
import type { TestInfo } from '@playwright/test';
import { existsSync } from 'node:fs';
import { openStory } from './story.ts';

import { screens } from './visual/screens.ts';
import { prepare } from './visual/prepare.ts';
import { capture } from './visual/capture.ts';

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
                if (screen.short)
                    await page.setViewportSize(
                        info.project.name === 'desktop'
                            ? { width: 1130, height: 540 }
                            : { width: 375, height: 740 }
                    );
                await attachBaseline(info, filename);
                await page.clock.setFixedTime(new Date('2026-10-16T12:00:00Z'));
                await openStory(page, screen.story, theme, locale);
                await expect(page.locator('html')).toHaveAttribute('data-theme', theme);
                await expect(page.locator('html')).toHaveAttribute('lang', locale);
                await prepare(page, screen, info);
                await capture(page, screen, filename);
            });
        }
    }
}
