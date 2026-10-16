import { test, expect } from '@playwright/test';
import type { Page } from '@playwright/test';

async function story(page: Page, id: string, theme = 'light', locale = 'en') {
    await page.goto(`/iframe.html?id=${id}&viewMode=story&globals=theme:${theme};locale:${locale}`);
    await expect(page.locator('.storybook-stage')).toBeVisible();
}

test('every catalogue story renders without browser errors', async ({ page, request }) => {
    const errors: string[] = [];
    page.on('pageerror', (error) => errors.push(error.message));
    const response = await request.get('/index.json');
    expect(response.ok()).toBeTruthy();
    const index = (await response.json()) as {
        entries: Record<string, { id: string; type: string }>;
    };
    const stories = Object.values(index.entries).filter((entry) => entry.type === 'story');
    expect(stories.length).toBeGreaterThanOrEqual(16);
    for (const entry of stories) {
        await story(page, entry.id);
        await expect(page.locator('#storybook-root')).not.toBeEmpty();
        expect(errors, entry.id).toEqual([]);
    }
});

for (const theme of ['light', 'dark']) {
    for (const locale of ['en', 'uk']) {
        test(`${theme}/${locale}: viewport notifications and role navigation`, async ({
            page
        }, info) => {
            await story(page, 'feedback-notificationbanner--information', theme, locale);
            const notification = page.getByRole('status');
            await expect(notification).toHaveText(
                locale === 'en' ? /Settings saved/ : /Налаштування збережено/
            );
            const before = await notification.boundingBox();
            await page.evaluate(() => window.scrollTo(0, 1000));
            await expect.poll(() => page.evaluate(() => window.scrollY)).toBeGreaterThan(500);
            const after = await notification.boundingBox();
            expect(Math.abs(after!.y - before!.y)).toBeLessThan(1);
            expect(after!.y).toBeLessThan(25);
            await notification.getByRole('button').click();
            await expect(notification).toHaveCount(0);

            await story(page, 'feedback-notificationbanner--warning', theme, locale);
            await page.getByRole('status').getByRole('button').click();
            await expect(page.getByRole('status')).toHaveCount(0);
            await story(page, 'feedback-notificationbanner--error', theme, locale);
            await expect(page.getByRole('alert')).toBeVisible();
            await expect(page.getByRole('alert').getByRole('button')).toHaveCount(0);

            await story(page, 'navigation-sidebar--administrator', theme, locale);
            await expect(
                page.getByRole('button', { name: locale === 'en' ? 'Connections' : 'З’єднання' })
            ).toBeVisible();
            await expect(page.locator('html')).toHaveAttribute('data-theme', theme);
            await expect(page.locator('html')).toHaveAttribute('lang', locale);
            await info.attach('administrator-navigation', {
                body: await page.screenshot(),
                contentType: 'image/png'
            });
            await story(page, 'navigation-sidebar--point', theme, locale);
            await expect(
                page.getByRole('button', { name: locale === 'en' ? 'Connections' : 'З’єднання' })
            ).toHaveCount(0);
        });
    }
}

test('journal text is escaped and busy queue actions cannot be clicked', async ({ page }) => {
    await story(page, 'connections-polldetails--failed');
    const journal = page.getByLabel('Selected poll log');
    await expect(journal).toContainText('<script>This is plain journal text</script>');
    await expect(journal.locator('script')).toHaveCount(0);
    await story(page, 'connections-pollattempts--busy');
    const actions = page.locator('.storybook-stage .attempts-scroll button');
    await expect(actions).toHaveCount(4);
    for (const button of await actions.all()) {
        await expect(button).toBeDisabled();
    }
});

test('station form remains usable at mobile width in both themes', async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 812 });
    for (const theme of ['light', 'dark']) {
        await story(page, 'settings-nodeform--settings', theme, 'uk');
        const archive = page.getByRole('checkbox', { name: /Зберігати оригінали/ });
        await expect(archive).toBeChecked();
        await archive.uncheck();
        await expect(archive).not.toBeChecked();
        expect(
            await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)
        ).toBeTruthy();
    }
});
