import { test, expect } from '@playwright/test';
import type { TestInfo } from '@playwright/test';
import { existsSync } from 'node:fs';
import { openStory } from './story.ts';

const screens = [
    { name: 'mail-load-more', story: 'mail-mailloadmore--ready' },
    { name: 'mail-load-more-disabled', story: 'mail-mailloadmore--disabled' },
    { name: 'mail-archive-search', story: 'mail-netmailview--archive-search', mailSearch: true },
    { name: 'mail-local-search', story: 'mail-netmailview--local-search', mailSearch: true },
    { name: 'mail-search-pending', story: 'mail-netmailview--search-pending', mailSearch: true },
    { name: 'mail-search-failure', story: 'mail-netmailview--search-failure', mailSearch: true },
    { name: 'echo-archive-search', story: 'mail-echomailview--archive-search', mailSearch: true },
    {
        name: 'echo-search-continuation',
        story: 'mail-echomailview--search-continuation',
        mailSearch: true
    },
    { name: 'shortcut-help', story: 'navigation-shortcuthelp--default', modal: true },
    { name: 'shortcut-help-golded', story: 'navigation-shortcuthelp--golded', modal: true },
    {
        name: 'netmail-editor-list-toggle',
        story: 'mail-netmailview--editor',
        showComposerList: true
    },
    {
        name: 'netmail-editor-list-hidden',
        story: 'mail-netmailview--editor',
        hideComposerList: true
    },
    {
        name: 'echo-editor-list-hidden',
        story: 'mail-echomailview--compose',
        hideComposerList: true
    },
    {
        name: 'netmail-editor-below-list-hidden',
        story: 'mail-netmailview--editor-below',
        hideComposerList: true
    },
    { name: 'composer-sender-details', story: 'mail-mailcomposer--sender-details' },
    { name: 'composer-echo-compact', story: 'mail-mailcomposer--echo-compact' },
    {
        name: 'composer-echo-recipient-search',
        story: 'mail-mailcomposer--echo-recipient-search',
        suggestions: true
    },
    {
        name: 'composer-address-search',
        story: 'mail-mailcomposer--address-search',
        suggestions: true
    },
    { name: 'golded-template-new', story: 'mail-mailcomposer--golded-new' },
    { name: 'golded-template-reply', story: 'mail-mailcomposer--golded-reply' },
    { name: 'golded-template-quoted', story: 'mail-mailcomposer--golded-quoted' },
    { name: 'golded-template-forward', story: 'mail-mailcomposer--golded-forward' },

    { name: 'editor-focused-caret', story: 'mail-fidoeditor--focused' },
    {
        name: 'composer-recipient-search',
        story: 'mail-mailcomposer--recipient-search',
        suggestions: true
    },
    { name: 'echo-partial-tree-warning', story: 'mail-echomailview--partial-tree' },
    {
        name: 'netmail-scrolled-selection',
        story: 'mail-netmailview--long-reading',
        scrollSelection: 20
    },
    {
        name: 'netmail-scrolled-selection-below',
        story: 'mail-netmailview--long-reading-below',
        scrollSelection: 20
    },
    {
        name: 'echo-scrolled-selection',
        story: 'mail-echomailview--long-tree-reading',
        scrollSelection: 12,
        short: true
    },
    {
        name: 'echo-tree-navigation',
        story: 'mail-echomailview--tree-root-reading',
        treeNavigation: true
    },
    {
        name: 'echo-switching-flat-message',
        story: 'mail-echomailview--switching-flat-message',
        switching: true
    },
    {
        name: 'netmail-switching-message',
        story: 'mail-netmailview--switching-message',
        switching: true
    },
    {
        name: 'echo-switching-message',
        story: 'mail-echomailview--switching-message',
        switching: true
    },
    { name: 'dos-graphics-reader', story: 'mail-fidotext--dos-graphics' },
    { name: 'dos-graphics-editor', story: 'mail-fidoeditor--dos-graphics' },
    { name: 'keyboard-settings', story: 'settings-accountsettings--keyboard' },
    { name: 'keyboard-settings-golded', story: 'settings-accountsettings--golded-keyboard' },
    { name: 'navigation-collapsed-waiting', story: 'navigation-sidebar--collapsed-awaiting-send' },
    { name: 'auth-login', story: 'authentication-authscreen--login' },
    { name: 'auth-login-short', story: 'authentication-authscreen--login', short: true },
    { name: 'auth-password', story: 'authentication-authscreen--password-only' },
    { name: 'auth-busy', story: 'authentication-authscreen--busy' },
    { name: 'auth-registration', story: 'authentication-authscreen--registration' },
    { name: 'polling-page', story: 'connections-connections--queue' },
    { name: 'polling-page-empty', story: 'connections-connections--empty' },
    { name: 'directory-page', story: 'directory-nodedirectory--results' },
    { name: 'directory-page-empty', story: 'directory-nodedirectory--empty' },
    { name: 'routing-rules-adding', story: 'routing-netmailroutespanel--adding' },
    { name: 'personal-profile', story: 'settings-accountsettings--profile' },
    {
        name: 'profile-directory-names',
        story: 'settings-accountsettings--profile-directory',
        profileDirectory: true
    },
    { name: 'header-display-name', story: 'navigation-applicationheader--display-name' },
    { name: 'sidebar-display-name', story: 'navigation-sidebar--display-name' },
    { name: 'personal-addresses', story: 'settings-accountsettings--addresses' },
    { name: 'personal-security', story: 'settings-accountsettings--security' },
    { name: 'personal-appearance', story: 'settings-accountsettings--appearance' },
    { name: 'personal-mail-appearance', story: 'settings-accountsettings--mail-appearance' },
    { name: 'directory-sources', story: 'plugins-pluginsettingspanel--directory-sources' },
    {
        name: 'directory-sources-read-only',
        story: 'plugins-pluginsettingspanel--directory-sources-read-only'
    },
    { name: 'directory-crc-settings', story: 'plugins-pluginsettingspanel--directory-crc' },
    {
        name: 'directory-crc-read-only',
        story: 'plugins-pluginsettingspanel--directory-crc-read-only'
    },
    { name: 'mail-typography-reader', story: 'mail-mailappearancesettings--box-drawing' },
    { name: 'mail-typography-editor', story: 'mail-mailappearancesettings--box-drawing-editor' },
    { name: 'collapsed-list-echo-tree', story: 'layout-maillistworkspace--echo-tree' },
    { name: 'collapsed-list-echo-flat', story: 'layout-maillistworkspace--echo-flat' },
    { name: 'collapsed-list-echo-compact', story: 'layout-maillistworkspace--echo-compact' },
    { name: 'collapsed-list-netmail', story: 'layout-maillistworkspace--netmail' },
    { name: 'echo-focused-desktop', story: 'mail-echomailview--focused-desktop' },
    { name: 'mail-reader-actions', story: 'mail-netmailmessage--reader-actions' },
    {
        name: 'mail-reader-more',
        story: 'mail-netmailmessage--more-actions',
        disclosure: '.reader-more-panel'
    },
    {
        name: 'mail-display-options',
        story: 'mail-netmailview--display-options',
        disclosure: '.mail-options-panel'
    },
    { name: 'netmail-application', story: 'mail-netmailview--application-workspace' },
    { name: 'echo-application', story: 'mail-echomailview--application-workspace' },
    { name: 'application-header', story: 'navigation-applicationheader--signed-in' },
    { name: 'mail-workspace-reading', story: 'mail-netmailview--reading' },
    { name: 'mail-workspace-below', story: 'mail-netmailview--below' },
    { name: 'mail-workspace-separate', story: 'mail-netmailview--separate' },
    { name: 'echo-below', story: 'mail-echomailview--below' },
    { name: 'echo-separate', story: 'mail-echomailview--separate' },
    { name: 'echo-compact', story: 'mail-echomailview--compact' },
    { name: 'fido-highlighted', story: 'mail-fidotext--highlighted' },
    { name: 'fido-source', story: 'mail-fidotext--source' },
    { name: 'editor-writing', story: 'mail-fidoeditor--writing' },
    { name: 'editor-read-only', story: 'mail-fidoeditor--read-only' },
    { name: 'editor-source', story: 'mail-fidoeditor--source' },
    { name: 'editor-preview', story: 'mail-fidoeditor--preview' },
    { name: 'mail-themes', story: 'mail-mailappearancesettings--themes' },
    { name: 'mail-themes-editor-preview', story: 'mail-mailappearancesettings--editor-preview' },
    { name: 'mail-themes-custom-preview', story: 'mail-mailappearancesettings--custom-preview' },
    { name: 'mail-themes-busy', story: 'mail-mailappearancesettings--busy' },
    { name: 'mail-themes-file', story: 'mail-mailappearancesettings--file-theme' },
    { name: 'mail-themes-error', story: 'mail-mailappearancesettings--invalid-import' },
    { name: 'mail-templates', story: 'mail-mailtemplatesettings--templates' },
    { name: 'personal-point-templates', story: 'settings-accountsettings--point-templates' },
    {
        name: 'personal-admin-templates',
        story: 'settings-accountsettings--administrator-templates'
    },
    { name: 'mail-templates-invalid', story: 'mail-mailtemplatesettings--invalid-token' },

    { name: 'split-right', story: 'layout-splitpane--right' },
    { name: 'split-below', story: 'layout-splitpane--below' },
    { name: 'split-focused-right', story: 'layout-splitpane--focused-right' },
    { name: 'split-focused-below', story: 'layout-splitpane--focused-below' },
    { name: 'navigation-collapsed', story: 'navigation-sidebar--collapsed' },
    { name: 'navigation-collapsed-point', story: 'navigation-sidebar--collapsed-point' },
    { name: 'navigation-station', story: 'navigation-sidebar--station' },
    { name: 'navigation-station-collapsed', story: 'navigation-sidebar--collapsed-station' },
    { name: 'navigation-polling', story: 'navigation-sidebar--polling' },
    { name: 'navigation-polling-collapsed', story: 'navigation-sidebar--collapsed-polling' },
    { name: 'echo-tree', story: 'mail-echomailview--tree' },
    { name: 'echo-flat', story: 'mail-echomailview--flat' },
    { name: 'echo-reading', story: 'mail-echomailview--reading' },
    { name: 'echo-conversation', story: 'mail-echomailview--conversation', conversation: true },
    { name: 'echo-missing-parent', story: 'mail-echomailview--missing-parent' },
    { name: 'echo-compose', story: 'mail-echomailview--compose' },
    { name: 'echo-reply', story: 'mail-echomailview--reply' },
    { name: 'echo-read-only', story: 'mail-echomailview--read-only' },
    { name: 'echo-empty', story: 'mail-echomailview--empty' },
    { name: 'echo-no-access', story: 'mail-echomailview--no-access' },
    { name: 'routing-policy', story: 'routing-netmailroutingpolicyform--database' },
    { name: 'routing-transit', story: 'routing-netmailroutingpolicyform--transit' },
    { name: 'routing-busy', story: 'routing-netmailroutingpolicyform--busy' },
    { name: 'routing-rules', story: 'routing-netmailroutespanel--database' },
    { name: 'routing-file-managed', story: 'routing-netmailroutespanel--file-managed' },
    { name: 'routing-empty', story: 'routing-netmailroutespanel--empty' },
    { name: 'routing-preview', story: 'routing-netmailroutepreviewform--matching-rule' },
    { name: 'routing-no-route', story: 'routing-netmailroutepreviewform--no-route' },
    { name: 'mail-workspace', story: 'mail-netmailview--inbox' },
    { name: 'mail-empty-reader-below', story: 'mail-netmailview--empty-reader-below' },
    { name: 'mail-empty-drafts-workspace', story: 'mail-netmailview--empty-drafts' },
    { name: 'mail-queue-waiting', story: 'mail-mailqueuecontrol--waiting' },
    { name: 'mail-queue-empty', story: 'mail-mailqueuecontrol--empty' },
    { name: 'mail-queue-busy', story: 'mail-mailqueuecontrol--busy' },
    { name: 'mail-navigation-waiting', story: 'navigation-sidebar--awaiting-send' },
    { name: 'mail-navigation-preparing', story: 'navigation-sidebar--preparing-mail' },
    { name: 'mail-drafts', story: 'mail-netmaildrafts--saved' },
    { name: 'mail-drafts-empty', story: 'mail-netmaildrafts--empty' },
    { name: 'mail-inbox', story: 'mail-netmaillist--inbox' },
    { name: 'mail-outbox', story: 'mail-netmaillist--outbox' },
    { name: 'mail-empty', story: 'mail-netmaillist--empty' },
    { name: 'mail-loading', story: 'mail-netmaillist--loading' },
    { name: 'mail-reading', story: 'mail-netmailmessage--reading' },
    { name: 'mail-delete-busy', story: 'mail-netmailmessage--deleting' },
    { name: 'mail-long-text', story: 'mail-netmailmessage--long-text' },
    { name: 'mail-reply', story: 'mail-mailcomposer--reply' },
    { name: 'mail-compact-reply', story: 'mail-mailcomposer--compact-reply' },
    { name: 'mail-workspace-reply', story: 'mail-mailcomposer--workspace-reply' },
    { name: 'mail-compose', story: 'mail-mailcomposer--new-message' },
    { name: 'mail-sending', story: 'mail-mailcomposer--sending' },
    { name: 'plugin-areas', story: 'plugins-pluginsettingspanel--areas' },
    { name: 'plugin-access', story: 'plugins-pluginsettingspanel--access' },
    { name: 'plugin-creation', story: 'plugins-pluginsettingspanel--creation' },
    { name: 'plugin-robots', story: 'plugins-pluginsettingspanel--robots' },
    { name: 'plugin-files', story: 'plugins-pluginsettingspanel--file-managed' },
    { name: 'plugin-empty', story: 'plugins-pluginsettingspanel--empty' },
    { name: 'station-tabs-plugins', story: 'settings-stationsettingstabs--plugins' },
    { name: 'station-tabs-routing', story: 'settings-stationsettingstabs--routing' },
    { name: 'station-tabs-logs', story: 'settings-stationsettingstabs--logs' },
    { name: 'station-workspace-logs', story: 'settings-stationsettingstabs--workspace-logs' },
    {
        name: 'station-header-scrolled',
        story: 'settings-stationsettingstabs--long-workspace-logs',
        scroll: 350
    },
    {
        name: 'station-header-collapsed-scrolled',
        story: 'settings-stationsettingstabs--long-collapsed-workspace-logs',
        scroll: 350
    },
    {
        name: 'station-workspace-collapsed',
        story: 'settings-stationsettingstabs--collapsed-workspace-logs'
    },
    { name: 'station-tabs-robots', story: 'settings-stationsettingstabs--robots' },
    { name: 'station-tabs-accounts', story: 'settings-stationsettingstabs--accounts' },
    { name: 'directory', story: 'directory-directorypanel--nodes-and-points' },
    { name: 'directory-empty', story: 'directory-directorypanel--empty' },
    { name: 'directory-loading', story: 'directory-directorypanel--loading' },
    { name: 'address-suggestions', story: 'directory-addressinput--matches', suggestions: true },
    { name: 'address-nodes-only', story: 'directory-addressinput--nodes-only', suggestions: true },
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
                if (screen.mailSearch) {
                    await page.locator('.mail-query input').fill('Welcome');
                    await expect(page.locator('.mail-search-status')).toBeVisible();
                    if (screen.name !== 'mail-search-pending') {
                        await expect(page.locator('.mail-search-status')).not.toContainText(
                            /Searching messages|Пошук листів/
                        );
                    }
                }
                if (screen.hideComposerList && info.project.name === 'desktop') {
                    await expect(page.locator('.split-list')).toBeHidden();
                    await expect(page.locator('.split-handle')).toBeHidden();
                }
                if (screen.showComposerList && info.project.name === 'desktop') {
                    await page.locator('.composer-list-toggle').click();
                    await expect(page.locator('.split-list')).toBeVisible();
                }
                if (screen.profileDirectory) {
                    await page.locator('.profile-name-search').click();
                    await expect(page.locator('.profile-name-matches button')).toHaveCount(2);
                }
                if (screen.scrollSelection) {
                    for (let index = 1; index <= screen.scrollSelection; index++) {
                        await expect(page.locator('[data-shortcut="mail.next"]')).toBeEnabled();
                        await page.keyboard.press('ArrowRight');
                        await expect(
                            page.locator('[data-shortcut-row]').nth(index)
                        ).toHaveAttribute('data-shortcut-selected', '');
                        await expect(page.locator('.mail-message')).not.toHaveAttribute(
                            'aria-busy',
                            'true'
                        );
                    }
                }
                if (screen.treeNavigation) {
                    await expect(page.locator('[data-shortcut="mail.next"]')).toBeEnabled();
                    await page.keyboard.press('j');
                    await expect(page.locator('.mail-body')).toContainText('Thanks Alice');
                    await expect(page.locator('.mail-message')).not.toHaveAttribute(
                        'aria-busy',
                        'true'
                    );
                    await page.keyboard.press('ArrowRight');
                    await expect(page.locator('.mail-body')).toContainText('Welcome Bob');
                    await expect(page.locator('.mail-message')).not.toHaveAttribute(
                        'aria-busy',
                        'true'
                    );
                    await expect(page.locator('[data-shortcut="mail.next"]')).toBeDisabled();
                }
                if (screen.switching) {
                    const next = page.locator('[data-shortcut="mail.next"]');
                    await expect(next).toBeEnabled();
                    if (info.project.name === 'desktop')
                        await page.locator('[data-shortcut-selected]').focus();
                    await page.keyboard.press('j');
                    await expect(next).toBeDisabled();
                    await expect(page.locator('.mail-message')).toBeFocused();
                }
                if (screen.disclosure && info.project.name === 'desktop')
                    await expect(page.locator(screen.disclosure)).toBeVisible();
                if (screen.suggestions)
                    await expect(
                        page.locator('.address-options').getByRole('option').first()
                    ).toBeVisible();
                if (screen.conversation) {
                    await page.locator('.echo-conversation > summary').click();
                    await expect(page.locator('.echo-conversation li time')).toBeVisible();
                }
                if (screen.unavailable)
                    await expect(page.locator('.address-options [role=status]')).toBeVisible();
                if (screen.notification) {
                    await expect(page.locator('.message')).toBeVisible();
                    await expect(page).toHaveScreenshot(filename);
                    return;
                }
                if (screen.modal) {
                    await expect(page.getByRole('dialog')).toBeVisible();
                    await expect(page).toHaveScreenshot(filename);
                    return;
                }
                if (screen.scroll) {
                    await page.evaluate((offset) => window.scrollTo(0, offset), screen.scroll);
                    await expect
                        .poll(() => page.evaluate(() => window.scrollY))
                        .toBe(screen.scroll);
                    await expect(page.locator('.sb-errordisplay')).toBeHidden();
                    await expect(page).toHaveScreenshot(filename);
                    return;
                }
                await expect(page.locator('.storybook-stage')).toHaveScreenshot(filename);
                await expect(page.locator('.sb-errordisplay')).toBeHidden();
            });
        }
    }
}
