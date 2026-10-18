import { test, expect } from '@playwright/test';
import type { TestInfo } from '@playwright/test';
import { existsSync } from 'node:fs';
import { openStory } from './story.ts';

const screens = [
    { name: 'plugin-areas', story: 'plugins-pluginsettingspanel--areas' },
    { name: 'plugin-access', story: 'plugins-pluginsettingspanel--access' },
    { name: 'plugin-creation', story: 'plugins-pluginsettingspanel--creation' },
    { name: 'plugin-robots', story: 'plugins-pluginsettingspanel--robots' },
    { name: 'plugin-files', story: 'plugins-pluginsettingspanel--file-managed' },
    { name: 'plugin-empty', story: 'plugins-pluginsettingspanel--empty' },
    { name: 'station-tabs-plugins', story: 'settings-stationsettingstabs--plugins' },
    { name: 'directory', story: 'directory-directorypanel--nodes-and-points' },
    { name: 'directory-empty', story: 'directory-directorypanel--empty' },
    { name: 'directory-loading', story: 'directory-directorypanel--loading' },
    { name: 'address-suggestions', story: 'directory-addressinput--matches', suggestions: true },
    { name: 'address-multiple', story: 'directory-addressinput--multiple-akas', suggestions: true },
    {
        name: 'address-unavailable',
        story: 'directory-addressinput--unavailable',
        unavailable: true
    },
    { name: 'administrator-navigation', story: 'navigation-sidebar--administrator' },
    { name: 'point-navigation', story: 'navigation-sidebar--point' },
    { name: 'station-settings', story: 'settings-nodeform--settings' },
    { name: 'station-tabs-general', story: 'settings-stationsettingstabs--general' },
    { name: 'station-tabs-links', story: 'settings-stationsettingstabs--links' },
    { name: 'station-tabs-tosser', story: 'settings-stationsettingstabs--tosser' },
    { name: 'station-tabs-echoes', story: 'settings-stationsettingstabs--echoes' },
    { name: 'station-tabs-quarantine', story: 'settings-stationsettingstabs--quarantine' },
    { name: 'station-tabs-busy', story: 'settings-stationsettingstabs--busy' },
    { name: 'initial-station-setup', story: 'settings-setupstationform--multiple-uplinks' },
    { name: 'adding-uplink', story: 'settings-maillinkseditor--adding-uplink' },
    { name: 'saving-uplinks', story: 'settings-maillinkseditor--saving' },
    {
        name: 'duplicate-uplink',
        story: 'feedback-notificationbanner--duplicate-uplink',
        notification: true
    },
    { name: 'station-reload', story: 'settings-stationsettingsheader--ready' },
    { name: 'station-reload-busy', story: 'settings-stationsettingsheader--busy' },
    { name: 'mail-link', story: 'settings-maillinkform--existing' },
    { name: 'mail-link-pkt-password', story: 'settings-maillinkform--separate-packet-password' },
    { name: 'tosser-settings', story: 'settings-tosserform--defaults' },
    { name: 'echo-creation-link', story: 'settings-maillinkform--echo-creation' },
    { name: 'echo-creation-enabled', story: 'settings-tosserform--automatic-creation' },
    { name: 'echo-access', story: 'settings-echoaccesspanel--database' },
    { name: 'echo-access-files', story: 'settings-echoaccesspanel--file-managed' },
    { name: 'echo-access-empty', story: 'settings-echoaccesspanel--empty' },
    { name: 'echo-access-revocation', story: 'settings-echoaccesspanel--revocation' },
    { name: 'quarantine', story: 'settings-quarantinepanel--packets' },
    { name: 'quarantine-batch', story: 'settings-quarantinepanel--batch-retry' },
    { name: 'quarantine-batch-ready', story: 'settings-quarantinepanel--batch-ready' },
    { name: 'quarantine-batch-results', story: 'settings-quarantinepanel--batch-results' },
    { name: 'quarantine-legacy-retry', story: 'settings-quarantinepanel--legacy-retry' },
    { name: 'quarantine-archive', story: 'settings-quarantinepanel--archive' },
    { name: 'quarantine-empty', story: 'settings-quarantinepanel--empty' },
    { name: 'quarantine-long-filename', story: 'settings-quarantinepanel--long-filename' },
    { name: 'quarantine-unavailable', story: 'settings-quarantinepanel--unavailable' },
    {
        name: 'configuration-applied',
        story: 'feedback-notificationbanner--configuration-applied',
        notification: true
    },
    { name: 'poll-queue', story: 'connections-pollattempts--mixed-states' },
    { name: 'poll-details', story: 'connections-polldetails--failed' },
    { name: 'poll-protocol', story: 'connections-polldetails--transferring' },
    { name: 'binkp-log', story: 'connections-binkplogpanel--connections' },
    { name: 'binkp-log-empty', story: 'connections-binkplogpanel--empty' },
    { name: 'binkp-log-rotated', story: 'connections-binkplogpanel--rotated' },
    { name: 'binkp-log-busy', story: 'connections-binkplogpanel--busy' },
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
                if (screen.suggestions)
                    await expect(page.getByRole('option').first()).toBeVisible();
                if (screen.unavailable)
                    await expect(page.locator('.address-options [role=status]')).toBeVisible();
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
