import assert from 'node:assert/strict';
import { test } from 'node:test';
import {
    builtInMailThemes,
    createMailAppearance,
    mailTheme,
    themeStyle
} from '../src/mail-themes.ts';
test('theme files only contain bounded names and fixed hex color properties', () => {
    const base = builtInMailThemes[0]!;
    assert.equal(Object.keys(themeStyle(mailTheme(base))).length, 10);
    assert.throws(() =>
        mailTheme({ ...base, colors: { ...base.colors, text: 'url(javascript:bad)' } })
    );
    assert.throws(() => mailTheme({ ...base, id: '../private' }));
});
test('personal theme saves do not modify operator themes and failure retains the old selection', async () => {
    const appearance = createMailAppearance({
        uiDocument: async () => '{}',
        saveUiDocument: async () => {
            throw new Error('offline');
        }
    });
    await appearance.load('session');
    await assert.rejects(appearance.save('graphite', 'terminal', []), /offline/);
    assert.equal(appearance.reader.value, 'paper');
    await assert.rejects(appearance.save('paper', 'paper', [builtInMailThemes[0]!]), /unique/);
});

test('default editor encoding is owner-scoped, persisted and reset on account changes', async () => {
    const rows = new Map<string, string>();
    const appearance = createMailAppearance({
        uiDocument: async (token) => rows.get(token) || '{}',
        saveUiDocument: async (token, _key, value) => {
            rows.set(token, value);
        }
    });
    await appearance.load('first');
    await appearance.save('paper', 'graphite', [], 'KOI8-R');
    assert.equal(appearance.encoding.value, 'KOI8-R');
    await appearance.load('second');
    assert.equal(appearance.encoding.value, 'UTF-8');
    await appearance.load('first');
    assert.equal(appearance.encoding.value, 'KOI8-R');
});
