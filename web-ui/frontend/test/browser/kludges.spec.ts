import { test, expect } from '@playwright/test';
import { ukrainian } from '../../src/locales/uk.ts';
import { openStory } from './story.ts';
import { inlineKludgeBody } from '../../src/mail-kludges.ts';
import type { MailDraft, MailItem } from '../../../build/frontend-generated/bindings.mjs';
import type { Page } from '@playwright/test';

async function capture<T>(page: Page, id: string): Promise<T> {
    return JSON.parse(await page.getByTestId(id).inputValue());
}

async function reopenSavedDraft(
    page: Page,
    echo: boolean,
    label: (key: keyof typeof ukrainian) => string
) {
    await page.locator('[data-shortcut="compose.cancel"]').click();
    const back = page.locator('.mail-back:visible');
    if (await back.count()) await back.first().click();
    if (echo) await page.getByRole('button', { name: label('Echo drafts'), exact: true }).click();
    else
        await page
            .getByRole('navigation', { name: label('Mail folders') })
            .getByRole('button', { name: label('Drafts'), exact: true })
            .click();
    await page.locator('.mail-draft-row [data-shortcut-row]').click();
    await expect(page.locator('.mail-composer')).toBeVisible();
}

for (const mailKind of ['netmail', 'echomail']) {
    for (const action of ['reply', 'quoted', 'forward']) {
        for (const width of [1280, 390]) {
            if (width === 390 && action === 'reply') continue;
            test(`${mailKind} ${action} keeps source kludges through save/reopen at ${width}px`, async ({
                page
            }) => {
                const label = (key: keyof typeof ukrainian) =>
                    width === 390 ? ukrainian[key] : key;
                await page.setViewportSize({ width, height: 1000 });
                await openStory(
                    page,
                    `mail-kludgeworkflow--${mailKind}`,
                    width === 390 ? 'dark' : 'light',
                    width === 390 ? 'uk' : 'en'
                );
                const source = await capture<MailItem>(page, 'source-state');
                const block = page.locator('.mail-kludge-list');
                await expect(block).toBeVisible();
                await expect(block.locator('code')).toHaveCount(4);
                await expect(block.locator('code').last()).toHaveText(
                    '^ACUSTOM: REFERENCE_ONLY @quote <b>literal</b>'
                );
                await expect(
                    block.locator('input, textarea, [contenteditable="true"], b')
                ).toHaveCount(0);
                await page.getByRole('button', { name: label('Show kludges inline') }).click();
                await expect(page.locator('.mail-body .kludge')).toHaveCount(4);
                const shortcut =
                    action === 'reply'
                        ? 'mail.shortReply'
                        : action === 'quoted'
                          ? 'mail.reply'
                          : 'mail.forward';
                if (width === 390) {
                    await page
                        .locator(`.reader-actions [data-shortcut="${shortcut}"]`)
                        .filter({ visible: true })
                        .first()
                        .click();
                } else {
                    await page.locator('.reader-more > summary').click();
                    await page.locator(`.reader-more [data-shortcut="${shortcut}"]`).click();
                }
                await expect(page.locator('.mail-composer')).toBeVisible();
                if (action === 'forward') {
                    await page
                        .getByRole('combobox', { name: label('Recipient name'), exact: true })
                        .fill('Forward Reader');
                    await page
                        .getByRole('combobox', { name: label('Recipient address'), exact: true })
                        .fill('1:500/102');
                }
                await expect(block.locator('summary')).toHaveText(
                    label('Original message kludges')
                );
                await expect(block).not.toHaveAttribute('open');
                await block.locator('summary').click();
                await expect(block.locator('code')).toHaveCount(4);
                await page.getByRole('button', { name: label('Source text'), exact: true }).click();
                const editor = page.locator('.cm-content');
                if (action === 'quoted')
                    await expect(editor).toContainText('^ACUSTOM: REFERENCE_ONLY');
                else await expect(editor).not.toContainText('REFERENCE_ONLY');
                await editor.press('ControlOrMeta+End');
                await editor.press('Enter');
                await editor.pressSequentially('Typed response');
                await page.locator('[data-shortcut="compose.save"]').click();
                await expect(page.getByTestId('saved-draft')).not.toHaveValue('');
                const saved = await capture<MailDraft>(page, 'saved-draft');
                expect(saved.composeKind).toBe(action);
                expect(saved.mailbox).toBe(mailKind === 'echomail' && action !== 'forward' ? 1 : 3);
                expect(saved.originalKludges).toEqual(source.kludges);
                expect(saved.originalBody).toBe(
                    action === 'quoted' ? inlineKludgeBody(source) : source.body
                );
                if (action === 'quoted')
                    expect(saved.body).toContain('> ^AMSGID: 1:500/101.2 source');
                expect(saved.body).not.toContain('\u0001');
                if (action !== 'quoted') expect(saved.body).not.toContain('@quote');
                if (action === 'forward') expect(saved.body).not.toContain('REFERENCE_ONLY');
                expect(saved.body.match(/ORIGINAL_BODY_ONLY/g) || []).toHaveLength(
                    action === 'reply' ? 0 : 1
                );
                expect(saved.body).toContain('Typed response');
                const reads = Number(await page.getByTestId('draft-reads').inputValue());
                await reopenSavedDraft(
                    page,
                    mailKind === 'echomail' && action !== 'forward',
                    label
                );
                expect(Number(await page.getByTestId('draft-reads').inputValue())).toBeGreaterThan(
                    reads
                );
                await expect(block.locator('summary')).toHaveText(
                    label('Original message kludges')
                );
                await block.locator('summary').click();
                await expect(block.locator('code')).toHaveCount(4);
                await expect(page.locator('.fido-editor .mail-kludge-list')).toHaveCount(0);
                await page.getByRole('button', { name: label('Source text'), exact: true }).click();
                await expect(editor).toContainText('Typed response');
                if (action === 'quoted')
                    await expect(editor).toContainText('^ACUSTOM: REFERENCE_ONLY');
                else await expect(editor).not.toContainText('REFERENCE_ONLY');
                await page.locator('[data-shortcut="compose.send"]').click();
                await expect(page.getByTestId('submitted-draft')).not.toHaveValue('');
                const submitted = await capture<MailDraft>(page, 'submitted-draft');
                expect(submitted.id).toBe(saved.id);
                expect(submitted.body).toBe(saved.body);
                expect(submitted.originalKludges).toEqual(saved.originalKludges);
                expect(submitted.originalBody).toBe(
                    action === 'quoted' ? inlineKludgeBody(source) : source.body
                );
                expect(submitted.body).not.toContain('\u0001');
                if (action !== 'quoted') expect(submitted.body).not.toContain('@quote');
                if (action === 'forward') expect(submitted.body).not.toContain('REFERENCE_ONLY');
                expect(await capture<MailItem>(page, 'source-state')).toEqual(source);
            });
        }
    }
}

test('disabled profile setting hides original kludge block in composer but keeps quote content', async ({
    page
}) => {
    await openStory(page, 'mail-kludgeworkflow--kludge-block-hidden');
    await page.getByRole('button', { name: 'Show kludges inline' }).click();
    await page.locator('.reader-more > summary').click();
    await page.locator('.reader-more [data-shortcut="mail.reply"]').click();
    await expect(page.locator('.mail-composer')).toBeVisible();
    await expect(page.locator('.mail-composer .mail-kludge-list')).toHaveCount(0);
    await expect(page.locator('.cm-content')).toContainText('^AMSGID: 1:500/101.2 source');
});

for (const snapshot of ['without-controls', 'truncated', 'omitted']) {
    test(`${snapshot} reference state survives the full draft reopening workflow`, async ({
        page
    }) => {
        await openStory(page, `mail-kludgeworkflow--${snapshot}`);
        if (snapshot === 'without-controls') {
            await expect(page.locator('.mail-kludge-list')).toHaveCount(0);
            await expect(page.getByRole('button', { name: 'Show kludges inline' })).toBeVisible();
        }
        await page.locator('.reader-more > summary').click();
        await page.locator('.reader-more [data-shortcut="mail.shortReply"]').click();
        await page.locator('[data-shortcut="compose.save"]').click();
        await expect(page.getByTestId('saved-draft')).not.toHaveValue('');
        const saved = await capture<MailDraft>(page, 'saved-draft');
        if (snapshot === 'without-controls') {
            expect(saved.originalKludges ?? undefined).toBeUndefined();
            expect(saved.originalKludgesTruncated ?? undefined).toBeUndefined();
        } else {
            expect(saved.originalKludgesTruncated).toBe(true);
            expect(saved.originalKludges).toEqual(
                snapshot === 'truncated' ? ['\u0001MSGID: 1:500/101.2 source'] : []
            );
        }
        await reopenSavedDraft(page, false, (key) => key);
        await expect(page.locator('.composer-kludge-notice')).toHaveCount(
            snapshot === 'without-controls' ? 0 : 1
        );
        await expect(page.locator('.mail-kludge-list')).toHaveCount(
            snapshot === 'truncated' ? 1 : 0
        );
        await page.locator('[data-shortcut="compose.send"]').click();
        await expect(page.getByTestId('submitted-draft')).not.toHaveValue('');
        const submitted = await capture<MailDraft>(page, 'submitted-draft');
        expect(submitted.id).toBe(saved.id);
        expect(submitted.body).toBe(saved.body);
        expect(submitted.body).not.toMatch(/\u0001|REFERENCE_ONLY/);
        expect(submitted.originalKludges).toEqual(saved.originalKludges);
        expect(submitted.originalKludgesTruncated).toBe(saved.originalKludgesTruncated);
    });
}

test('new messages have no original kludge disclosure', async ({ page }) => {
    await openStory(page, 'mail-mailcomposer--new-message');
    await expect(page.locator('.mail-composer')).toBeVisible();
    await expect(page.locator('.mail-kludge-list, .composer-kludge-notice')).toHaveCount(0);
});

for (const width of [1280, 390]) {
    test(`reader toggles ordered known kludges without interpreting controls at ${width}px`, async ({
        page
    }) => {
        const locale = width === 390 ? 'uk' : 'en';
        await page.setViewportSize({ width, height: 1000 });
        await openStory(
            page,
            'mail-messageview--kludge-reading',
            width === 390 ? 'dark' : 'light',
            locale
        );
        const toggle = page.getByRole('button', {
            name: locale === 'uk' ? ukrainian['Show kludges inline'] : 'Show kludges inline'
        });
        const body = page.locator('.mail-body');
        await expect(toggle).toHaveAttribute('aria-pressed', 'false');
        await expect(body.locator('.kludge')).toHaveText('^ACUSTOM: visible by default');
        const plain = await body.innerText();
        await toggle.focus();
        await page.keyboard.press('Space');
        await expect(toggle).toHaveAttribute('aria-pressed', 'true');
        await expect(body.locator('.fido-line')).toHaveText([
            '^AMSGID: 1:500/101.2 first',
            'First paragraph',
            '^ACUSTOM: visible by default',
            '^ACHRS: UTF-8 4',
            'AE> Quoted text',
            '^ACHRS: CP866 2',
            '^APID: <b>*literal*</b> @quote ' + 'long-value-'.repeat(16),
            '-- ',
            'Signature'
        ]);
        await expect(body.locator('.kludge a, .kludge b, .kludge .bold')).toHaveCount(0);
        await expect(body).toContainText('CUSTOM');
        await expect(page.locator('.mail-kludge-list')).toBeHidden();
        await expect(body.locator('.fido-line:not(.kludge)')).toHaveCount(4);
        expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(
            width
        );
        await toggle.click();
        await expect.poll(() => body.innerText()).toBe(plain);
        await toggle.click();
        await page.locator('[data-shortcut="mail.next"]').click();
        await expect(toggle).toHaveAttribute('aria-pressed', 'true');
        await expect(body).toContainText('Second body');
        await expect(body.locator('.kludge')).toHaveText('^ACHRS: CP866 2');
        await expect(body).not.toContainText('MSGID');
        await page.locator('[data-shortcut="mail.next"]').click();
        await expect(body).toContainText('Body without kludges');
        await expect(toggle).toBeVisible();
        await expect(toggle).toHaveAttribute('aria-pressed', 'true');
    });
}

test('reader inline button stays in the toolbar and the profile shows the full-width list', async ({
    page
}) => {
    for (const width of [1280, 390]) {
        await page.setViewportSize({ width, height: 900 });
        for (const theme of ['light', 'dark'] as const) {
            for (const locale of ['en', 'uk'] as const) {
                await openStory(page, 'mail-messageview--full-kludge-block', theme, locale);
                const button = page.getByRole('button', {
                    name:
                        locale === 'uk' ? ukrainian['Show kludges inline'] : 'Show kludges inline',
                    exact: true
                });
                await expect(button).toBeVisible();
                await expect(button.locator('xpath=..')).toHaveClass(/reader-actions/);
                await expect(button.locator('svg')).toBeVisible();
                await expect(page.locator('.mail-kludge-list')).toBeVisible();
                if ((await button.getAttribute('aria-pressed')) === 'true') await button.click();
                await button.click();
                await expect(button).toHaveAttribute('aria-pressed', 'true');
                await expect(page.locator('.mail-kludge-list > summary')).toHaveCount(0);
                const list = (await page.locator('.mail-kludge-list ul').boundingBox())!;
                expect(list.width).toBeGreaterThan(width === 390 ? 300 : 700);
            }
        }
    }
});

test('reader kludge toggle stays mounted while navigating to a message without kludges', async ({
    page
}) => {
    await openStory(page, 'mail-messageview--kludge-reading');
    const toggle = page.getByRole('button', { name: 'Show kludges inline' });
    const element = await toggle.elementHandle();
    await expect(toggle).toBeVisible();
    await toggle.click();

    await page.locator('[data-shortcut="mail.next"]').click();
    await expect(page.locator('.mail-body')).toContainText('Second body');
    await expect(toggle).toHaveAttribute('aria-pressed', 'true');
    await page.locator('[data-shortcut="mail.next"]').click();
    await expect(page.locator('.mail-body')).toContainText('Body without kludges');
    await expect(toggle).toBeVisible();
    await expect(toggle).toHaveAttribute('aria-pressed', 'true');
    expect(
        await element!.evaluate((button) =>
            button.isSameNode(document.querySelector('.reader-kludge-button'))
        )
    ).toBe(true);
});

test('reader toggle survives remounts and tab reloads for the current session', async ({
    page
}) => {
    await openStory(page, 'mail-messageview--session-navigation');
    const toggle = page.getByRole('button', { name: 'Show kludges inline' });
    await expect(toggle).toHaveAttribute('aria-pressed', 'false');
    await toggle.click();
    await page.getByTestId('remount-reader').click();
    await expect(toggle).toHaveCount(0);
    await page.getByTestId('remount-reader').click();
    await expect(toggle).toHaveAttribute('aria-pressed', 'true');
    await page.reload();
    await expect(toggle).toHaveAttribute('aria-pressed', 'true');
    await toggle.click();
    await page.reload();
    await expect(toggle).toHaveAttribute('aria-pressed', 'false');
});

test('unknown controls follow the personal toggle policy and remain accessible in the full list', async ({
    page
}) => {
    await openStory(page, 'mail-messageview--unknown-kludges-on-toggle');
    const body = page.locator('.mail-body');
    const toggle = page.getByRole('button', { name: 'Show kludges inline' });
    await expect(body.locator('.kludge')).toHaveCount(0);
    await expect(page.locator('.mail-kludge-list')).toBeHidden();
    await toggle.click();
    await expect(body.locator('.kludge')).toHaveCount(5);
    await expect(body).toContainText('CUSTOM');
    await toggle.click();
    await expect(body.locator('.kludge')).toHaveCount(0);
    await openStory(page, 'mail-messageview--unknown-kludges-only');
    await expect(body.locator('.kludge')).toHaveText('^ACUSTOM: visible by default');
    await expect(toggle).toBeVisible();
});

test('unknown control visibility is saved in personal appearance settings', async ({ page }) => {
    await openStory(page, 'settings-accountsettings--appearance');
    const block = page.getByRole('checkbox', { name: 'Show kludge block' });
    await expect(block).not.toBeChecked();
    await block.check();
    await expect(block).toBeChecked();
    const select = page.getByRole('combobox', { name: 'Unknown kludges in messages' });
    await expect(select).toHaveValue('always');
    await select.selectOption('toggle');
    await expect(select).toHaveValue('toggle');
    await page.getByRole('tab', { name: 'Profile', exact: true }).click();
    await page.getByRole('tab', { name: 'Appearance', exact: true }).click();
    await expect(block).toBeChecked();
    await expect(select).toHaveValue('toggle');
    await select.selectOption('always');
    await expect(select).toHaveValue('always');
});

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
