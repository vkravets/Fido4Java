import { test, expect } from '@playwright/test';
import { openStory } from './story.ts';

test('directory CRC settings send scoped compatibility and strict-mode commands', async ({
    page
}) => {
    for (const language of ['en', 'uk']) {
        await openStory(page, 'plugins-pluginsettingspanel--directory-crc', 'dark', language);
        await page
            .getByRole('button', { name: language === 'en' ? 'Edit' : 'Редагувати', exact: true })
            .click();
        const mode = page.getByRole('combobox', {
            name: language === 'en' ? 'CRC mode' : 'Режим CRC',
            exact: true
        });
        await expect(mode).toHaveValue('ARC-CRC');
        await mode.selectOption('XMODEM-CRC');
        await page.locator('.plugin-form button[type="submit"]').click();
        let command = JSON.parse(await page.locator('output').innerText());
        expect(command).toEqual({
            id: 'nodelist',
            section: 'crc',
            values: { source: 'points', mask: 'PNT46REG.*', mode: 'XMODEM-CRC' }
        });
        await mode.selectOption('ARC-CRC');
        await page.locator('.plugin-form button[type="submit"]').click();
        command = JSON.parse(await page.locator('output').innerText());
        expect(command.values.mode).toBe('ARC-CRC');
    }
});

test('externally managed directory CRC settings have no write form', async ({ page }) => {
    await openStory(page, 'plugins-pluginsettingspanel--directory-crc-read-only');
    await expect(
        page.getByText('This section is managed through configuration files.')
    ).toBeVisible();
    await expect(page.locator('.plugin-form')).toHaveCount(0);
    await expect(page.getByText('ARC-CRC', { exact: true })).toBeVisible();
});

test('directory source edits send paths format encoding and addressing as plugin commands', async ({
    page
}) => {
    for (const language of ['en', 'uk']) {
        await openStory(page, 'plugins-pluginsettingspanel--directory-sources', 'dark', language);
        await page
            .getByRole('button', { name: language === 'en' ? 'Edit' : 'Редагувати', exact: true })
            .nth(1)
            .click();
        await expect(
            page.getByRole('textbox', {
                name: language === 'en' ? 'Source ID' : 'ID джерела',
                exact: true
            })
        ).toHaveValue('points');
        await page
            .getByRole('textbox', {
                name: language === 'en' ? 'List file' : 'Файл списку',
                exact: true
            })
            .fill('./lists/new-points.lst');
        await page
            .getByRole('textbox', {
                name: language === 'en' ? 'List encoding' : 'Кодування списку',
                exact: true
            })
            .fill('KOI8-R');
        await page.locator('.plugin-form button[type="submit"]').click();
        expect(JSON.parse(await page.locator('output').innerText())).toEqual({
            id: 'nodelist',
            section: 'sources',
            values: {
                id: 'points',
                format: 'BOSS',
                path: './lists/new-points.lst',
                zone: '0',
                net: '0',
                charset: 'KOI8-R',
                requireChecksum: 'true'
            }
        });
    }
    await openStory(page, 'plugins-pluginsettingspanel--directory-sources-read-only');
    await expect(page.locator('.plugin-form')).toHaveCount(0);
});
