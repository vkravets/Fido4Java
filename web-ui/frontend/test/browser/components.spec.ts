import { test, expect } from '@playwright/test';
import type { Page } from '@playwright/test';
import { openStory as story } from './story.ts';

test('link password modes expose a separate PKT field without revealing stored secrets', async ({
    page
}) => {
    await story(page, 'settings-maillinkform--existing');
    await expect(page.getByLabel('Link address', { exact: true })).toHaveAttribute('readonly');
    await expect(page.getByLabel('binkp session password')).toHaveValue('');
    await expect(page.getByLabel('Separate PKT password')).toHaveCount(0);
    await page.getByLabel('PKT password mode').selectOption('SEPARATE');
    const packet = page.getByLabel('Separate PKT password');
    await expect(packet).toHaveValue('');
    await expect(packet).toHaveAttribute('type', 'password');
    await expect(packet).toHaveAttribute('maxlength', '8');
    await page.getByLabel('binkp session password').fill('session-secret');
    await packet.fill('pktpass');
    await expect(page.getByLabel('binkp session password')).toHaveValue('session-secret');
    await page.getByLabel('PKT password mode').selectOption('UNPROTECTED');
    await expect(packet).toHaveCount(0);
    await expect(page.getByText('Incoming packets for this link', { exact: false })).toBeVisible();
});

test('tosser defaults keep echo access checks enabled', async ({ page }) => {
    await story(page, 'settings-tosserform--defaults');
    await expect(page.getByLabel('Processing interval (seconds)')).toHaveValue('5');
    await expect(page.getByLabel('Check echomail permissions for each link')).toBeChecked();
    await expect(page.getByLabel('Create unknown echo areas automatically')).not.toBeChecked();
    await page.getByLabel('Processing interval (seconds)').fill('0');
    expect(
        await page
            .getByLabel('Processing interval (seconds)')
            .evaluate((input: HTMLInputElement) => input.validity.valid)
    ).toBe(false);
});

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

async function checkNotificationSize(page: Page, name: string, theme: string, locale: string) {
    await story(page, `feedback-notificationbanner--${name}`, theme, locale);
    const banner = page.getByRole('status');
    const button = banner.getByRole('button');
    await expect(button).toBeVisible();
    const before = (await banner.boundingBox())!;
    const action = (await button.boundingBox())!;
    const text = (await banner.locator('.message-text').boundingBox())!;
    expect(action.x).toBeGreaterThanOrEqual(text.x + text.width);
    expect(action.y).toBeGreaterThanOrEqual(before.y);
    expect(action.y + action.height).toBeLessThanOrEqual(before.y + before.height);
    // Keep the same content/width and remove only the close control.
    await button.evaluate((element) => element.remove());
    const after = (await banner.boundingBox())!;
    expect(after.height).toBeCloseTo(before.height, 1);
    expect(after.width).toBeCloseTo(before.width, 1);
}

for (const theme of ['light', 'dark']) {
    for (const locale of ['en', 'uk']) {
        test(`${theme}/${locale}: close buttons do not resize notifications`, async ({ page }) => {
            for (const width of [1280, 375]) {
                await page.setViewportSize({ width, height: 900 });
                await checkNotificationSize(page, 'information', theme, locale);
                await checkNotificationSize(page, 'warning', theme, locale);
            }
        });
    }
}
