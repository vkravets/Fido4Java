import { test, expect } from '@playwright/test';
import { openStory } from './story.ts';

test('fileecho settings are localized without server translation dictionaries', async ({
    page
}) => {
    for (const language of ['en', 'uk']) {
        await openStory(page, 'plugins-pluginsettingspanel--access', 'dark', language);
        const access = page.getByRole('combobox', {
            name: language === 'en' ? 'Access' : 'Доступ',
            exact: true
        });
        await expect(access.locator('option[value="READ"]')).toHaveText(
            language === 'en' ? 'Receives from us' : 'Отримує від нас'
        );
        await expect(access.locator('option[value="WRITE"]')).toHaveText(
            language === 'en' ? 'Sends to us' : 'Надсилає нам'
        );
        await expect(
            page.getByRole('combobox', {
                name: language === 'en' ? 'Area' : 'Область',
                exact: true
            })
        ).toHaveValue('lists.nodes');
        await access.selectOption('BOTH');
        await page.locator('.plugin-form button[type="submit"]').click();
        expect(JSON.parse(await page.locator('output').innerText()).values.access).toBe('BOTH');
    }
});
