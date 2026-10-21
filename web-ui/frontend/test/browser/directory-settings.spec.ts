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
        await expect(mode).toHaveValue('new-point-keeper');
        await mode.selectOption('standard');
        await page.locator('.plugin-form button[type="submit"]').click();
        let command = JSON.parse(await page.locator('output').innerText());
        expect(command).toEqual({
            id: 'nodelist',
            section: 'crc',
            values: { source: 'points', mask: 'PNT46REG.*', mode: 'standard' }
        });
        await mode.selectOption('new-point-keeper');
        await page.locator('.plugin-form button[type="submit"]').click();
        command = JSON.parse(await page.locator('output').innerText());
        expect(command.values.mode).toBe('new-point-keeper');
    }
});

test('externally managed directory CRC settings have no write form', async ({ page }) => {
    await openStory(page, 'plugins-pluginsettingspanel--directory-crc-read-only');
    await expect(
        page.getByText('This section is managed through configuration files.')
    ).toBeVisible();
    await expect(page.locator('.plugin-form')).toHaveCount(0);
    await expect(page.getByText('New Point Keeper (XMODEM / ARC)', { exact: true })).toBeVisible();
});
