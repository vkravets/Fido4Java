import assert from 'node:assert/strict';
import { test } from 'node:test';
import { createMailAppearance } from '../src/mail-themes.ts';
import {
    defaultMailTypography,
    loadMailTypography,
    mailTypography,
    typographyStyle
} from '../src/mail-typography.ts';

test('font settings allow only safe monospace families and bounded numeric metrics', () => {
    for (const value of [
        { font: 'url(bad)', size: 14, lineHeight: 1 },
        { font: '__proto__', size: 14, lineHeight: 1 },
        { font: 'system', size: '14px', lineHeight: 1 },
        { font: 'system', size: 33, lineHeight: 1 },
        { font: 'system', size: 14, lineHeight: NaN },
        { font: 'system', size: 14, lineHeight: 0.9 }
    ]) {
        assert.throws(() => mailTypography(value));
        assert.deepEqual(loadMailTypography(value), defaultMailTypography());
    }
    assert.deepEqual(typographyStyle({ font: 'consolas', size: 16, lineHeight: 1 }), {
        '--mail-font-family': 'Consolas, monospace',
        '--mail-font-size': '16px',
        '--mail-line-height': '1'
    });
    assert.equal(typographyStyle(defaultMailTypography())['--mail-font-size'], undefined);
});

test('one mail font persists per account and failed saves retain its current value', async () => {
    const rows = new Map<string, string>([['old', '{}']]);
    let fail = false;
    const appearance = createMailAppearance({
        uiDocument: async (token) => rows.get(token) || '{}',
        saveUiDocument: async (token, _key, value) => {
            if (fail) throw new Error('offline');
            rows.set(token, value);
        }
    });
    const reader = { font: 'dejavu', size: 16, lineHeight: 1 } as const;
    const editor = { font: 'consolas', size: 18, lineHeight: 1.2 } as const;
    await appearance.load('one');
    await appearance.save('paper', 'paper', [], 'KOI8-R', reader);
    const stored = JSON.parse(rows.get('one')!);
    assert.deepEqual(stored.typography, reader);
    assert.equal('readerTypography' in stored, false);
    assert.equal('editorTypography' in stored, false);
    await appearance.load('old');
    assert.deepEqual(appearance.typography.value, defaultMailTypography());
    await appearance.load('one');
    assert.deepEqual(appearance.typography.value, reader);
    fail = true;
    await assert.rejects(appearance.save('paper', 'paper', [], 'UTF-8', editor), /offline/);
    assert.deepEqual(appearance.typography.value, reader);
    await appearance.load('');
    assert.deepEqual(appearance.typography.value, defaultMailTypography());
});

test('late font preferences cannot leak across account switches', async () => {
    let finish!: (value: string) => void;
    const appearance = createMailAppearance({
        uiDocument: () =>
            new Promise<string>((resolve) => {
                finish = resolve;
            }),
        saveUiDocument: async () => {}
    });
    const pending = appearance.load('first');
    await appearance.load('');
    finish(JSON.stringify({ typography: { font: 'courier', size: 24, lineHeight: 2 } }));
    await pending;
    assert.deepEqual(appearance.typography.value, defaultMailTypography());
});

test('existing separate font preferences seed one shared font, preferring the reader', async () => {
    const reader = { font: 'dejavu', size: 16, lineHeight: 1 } as const;
    const editor = { font: 'consolas', size: 18, lineHeight: 1.2 } as const;
    const appearance = createMailAppearance({
        uiDocument: async () =>
            JSON.stringify({ readerTypography: reader, editorTypography: editor }),
        saveUiDocument: async () => {}
    });
    await appearance.load('one');
    assert.deepEqual(appearance.typography.value, reader);
});
