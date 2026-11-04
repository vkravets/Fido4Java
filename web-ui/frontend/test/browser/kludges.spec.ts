import { test, expect } from '@playwright/test';
import { ukrainian } from '../../src/locales/uk.ts';
import { openStory } from './story.ts';
import type { MailDraft } from '../../../build/frontend-generated/bindings.mjs';

for (const width of [1280, 390]) {
    test(`reader toggles ordered known kludges without interpreting controls at ${width}px`, async ({
        page
    }) => {
        const locale = width === 390 ? 'uk' : 'en';
        await page.setViewportSize({ width, height: 1000 });
        await openStory(
            page,
            'mail-netmailmessage--kludge-reading',
            width === 390 ? 'dark' : 'light',
            locale
        );
        const toggle = page.getByRole('checkbox', {
            name:
                locale === 'uk'
                    ? ukrainian['Show known kludges inline']
                    : 'Show known kludges inline'
        });
        const body = page.locator('.mail-body');
        await expect(toggle).not.toBeChecked();
        await expect(body.locator('.kludge')).toHaveCount(0);
        const plain = await body.innerText();
        await toggle.focus();
        await page.keyboard.press('Space');
        await expect(toggle).toBeChecked();
        await expect(body.locator('.fido-line')).toHaveText([
            '^AMSGID: 1:500/101.2 first',
            'First paragraph',
            '^ACHRS: UTF-8 4',
            'AE> Quoted text',
            '^ACHRS: CP866 2',
            '^APID: <b>*literal*</b> @quote ' + 'long-value-'.repeat(16),
            '-- ',
            'Signature'
        ]);
        await expect(body.locator('.kludge a, .kludge b, .kludge .bold')).toHaveCount(0);
        await expect(body).not.toContainText('X-UNKNOWN');
        await page.locator('.mail-kludge-list > summary').click();
        await expect(page.locator('.mail-kludge-list')).toContainText('X-UNKNOWN');
        await expect(body.locator('.fido-line:not(.kludge)')).toHaveCount(4);
        expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(
            width
        );
        await toggle.uncheck();
        await expect.poll(() => body.innerText()).toBe(plain);
        await toggle.check();
        await page.locator('[data-shortcut="mail.next"]').click();
        await expect(toggle).not.toBeChecked();
        await expect(body).toHaveText('Second body');
        await toggle.check();
        await expect(body.locator('.kludge')).toHaveText('^ACHRS: CP866 2');
        await expect(body).not.toContainText('MSGID');
    });
}

for (const state of ['truncated', 'omitted']) {
    test(`oversized ${state} kludge references allow saving and sending`, async ({ page }) => {
        await openStory(page, `mail-mailcomposer--${state}-reference`);
        await expect(page.locator('.composer-kludge-notice')).toBeVisible();
        await expect(page.locator('.mail-kludge-list')).toHaveCount(state === 'truncated' ? 1 : 0);
        for (const action of ['save', 'send']) {
            await page.locator(`[data-shortcut="compose.${action}"]`).click();
            const capture = page.getByTestId(action === 'save' ? 'saved-draft' : 'submitted-draft');
            await expect(capture).not.toHaveValue('');
            const draft = JSON.parse(await capture.inputValue()) as MailDraft;
            expect(draft.originalKludgesTruncated).toBe(true);
            const bytes = draft.originalKludges!.reduce(
                (sum, line) => sum + new TextEncoder().encode(line).length,
                0
            );
            expect(bytes).toBeLessThanOrEqual(60000);
            expect(draft.body).not.toContain('\u0001');
        }
    });
}

for (const kind of ['reply', 'quoted', 'forward', 'echo']) {
    for (const width of [1280, 390]) {
        test(`reference kludges stay outside ${kind} composition at ${width}px`, async ({
            page
        }) => {
            const locale = width === 390 ? 'uk' : 'en';
            const theme = width === 390 ? 'dark' : 'light';
            const label = (key: keyof typeof ukrainian) => (locale === 'uk' ? ukrainian[key] : key);
            await page.setViewportSize({ width, height: 1100 });
            await openStory(page, `mail-mailcomposer--reference-${kind}`, theme, locale);
            const readDraft = async (id = 'draft-state'): Promise<MailDraft> =>
                JSON.parse(await page.getByTestId(id).inputValue());
            const initial = await readDraft();
            expect(initial.originalKludges).toHaveLength(4);
            expect(initial.body).not.toMatch(/\u0001|REFERENCE_ONLY|@quote/);

            const block = page.locator('.mail-kludge-list');
            const summary = block.locator('summary');
            await expect(summary).toHaveText(label('Original message kludges'));
            await expect(block).not.toHaveAttribute('open');
            await summary.focus();
            await page.keyboard.press('Enter');
            await expect(block).toHaveAttribute('open', '');
            await expect(block.locator('code').last()).toContainText('@quote <b>literal text</b>');
            await expect(block.locator('input, textarea, [contenteditable="true"], b')).toHaveCount(
                0
            );
            await expect(page.locator('.fido-editor .mail-kludge-list')).toHaveCount(0);
            expect(
                await page.evaluate(() => document.documentElement.scrollWidth)
            ).toBeLessThanOrEqual(width);

            const editor = page.locator('.cm-content');
            await page.getByRole('button', { name: label('Source text'), exact: true }).click();
            await editor.fill('Typed response');
            await editor.press('ControlOrMeta+a');
            await page.locator('[data-shortcut="editor.quote"]').click();
            await expect.poll(async () => (await readDraft()).body).toBe('> Typed response');
            await page.getByRole('button', { name: label('Preview'), exact: true }).click();
            await expect(page.locator('.fido-editor .mail-body')).not.toContainText(
                'REFERENCE_ONLY'
            );
            await page.getByRole('button', { name: label('Visual'), exact: true }).click();
            await expect.poll(async () => (await readDraft()).body).toBe('> Typed response');

            await page.locator('.composer-template select').selectOption('quoted');
            page.once('dialog', (dialog) => dialog.accept());
            await page.locator('[data-shortcut="compose.template"]').click();
            await expect
                .poll(async () => (await readDraft()).body)
                .toContain('> Welcome to FidoNet!');
            const expanded = await readDraft();
            expect(expanded.body).not.toMatch(/\u0001|REFERENCE_ONLY|@quote/);
            expect(expanded.originalKludges).toEqual(initial.originalKludges);

            await page.locator('[data-shortcut="compose.save"]').click();
            await expect(page.getByTestId('saved-draft')).not.toHaveValue('');
            const saved = await readDraft('saved-draft');
            expect(saved.body).toBe(expanded.body);
            expect(saved.originalKludges).toEqual(initial.originalKludges);
            await page.locator('[data-shortcut="compose.send"]').click();
            await expect(page.getByTestId('submitted-draft')).not.toHaveValue('');
            const submitted = await readDraft('submitted-draft');
            expect(submitted.body).toBe(expanded.body);
            expect(submitted.body).not.toMatch(/\u0001|REFERENCE_ONLY/);
            expect(submitted.originalKludges).toEqual(initial.originalKludges);
        });
    }
}
