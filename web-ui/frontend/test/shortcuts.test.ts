import assert from 'node:assert/strict';
import { test } from 'node:test';
import {
    bindingsFor,
    createShortcuts,
    eventBinding,
    normalizeBinding,
    reservedBinding,
    shortcutConflict,
    shortcutPreferences
} from '../src/shortcuts.ts';

test('both presets are conflict-free and GoldED uses the documented reader commands', () => {
    for (const profile of ['jftn', 'golded'])
        assert.equal(shortcutConflict(shortcutPreferences({ profile })), null);
    const golded = shortcutPreferences({ profile: 'golded' });
    assert.deepEqual(bindingsFor(golded, 'mail.reply'), ['Q', 'F4', 'Alt+Q']);
    assert.deepEqual(bindingsFor(golded, 'mail.shortReply'), ['R', 'F3', 'Alt+R']);
    assert.deepEqual(bindingsFor(golded, 'compose.send'), ['F2', 'Alt+S']);
    assert.equal(reservedBinding('Ctrl+L'), true);
    assert.equal(reservedBinding('Alt+F4'), true);
    assert.equal(reservedBinding('Alt+ArrowRight'), false);
});
test('physical letter keys work with Cyrillic layout and modifiers normalize consistently', () => {
    assert.equal(
        eventBinding({
            code: 'KeyQ',
            key: 'й',
            ctrlKey: false,
            altKey: false,
            metaKey: false,
            shiftKey: false
        } as KeyboardEvent),
        'Q'
    );
    assert.equal(normalizeBinding('Shift+Ctrl+R'), 'Ctrl+Shift+R');
    assert.equal(normalizeBinding('Ctrl+Ctrl+R'), null);
    assert.equal(normalizeBinding('Alt'), null);
    const value = shortcutPreferences({
        overrides: { 'mail.reply': ['Ctrl+W', 'F4', 'F4', 'invalid'], unknown: ['Q'] }
    });
    assert.deepEqual(value.overrides, { 'mail.reply': ['F4'] });
});
test('conflicts span global and editor contexts while reading and composing can share Escape', () => {
    const value = shortcutPreferences({});
    value.overrides['mail.reply'] = ['Alt+1'];
    assert.equal(shortcutConflict(value), 'Alt+1');
    value.overrides['mail.reply'] = ['Ctrl+S'];
    assert.equal(shortcutConflict(value), null);
    value.overrides['editor.bold'] = ['Ctrl+S'];
    assert.equal(shortcutConflict(value), 'Ctrl+S');
});
test('personal bindings are saved through RPC, failed writes retain previous settings and late loads do not cross accounts', async () => {
    const documents = new Map<string, string>();
    let fail = false;
    const model = createShortcuts({
        uiDocument: async (token) => documents.get(token) || '{}',
        saveUiDocument: async (token, key, value) => {
            assert.equal(key, 'keyboard');
            if (fail) throw new Error('offline');
            documents.set(token, value);
        }
    });
    await model.load('first');
    await model.save(
        shortcutPreferences({ profile: 'golded', overrides: { 'mail.reply': ['Alt+Shift+Q'] } })
    );
    await model.load('second');
    assert.equal(model.value.value.profile, 'jftn');
    await model.load('first');
    assert.deepEqual(bindingsFor(model.value.value, 'mail.reply'), ['Alt+Shift+Q']);
    fail = true;
    await assert.rejects(model.save(shortcutPreferences({})), /offline/);
    assert.equal(model.value.value.profile, 'golded');
    let resolve!: (value: string) => void;
    const delayed = createShortcuts({
        uiDocument: () =>
            new Promise((done) => {
                resolve = done;
            }),
        saveUiDocument: async () => {}
    });
    const pending = delayed.load('old');
    await delayed.load('');
    resolve('{"profile":"golded"}');
    await pending;
    assert.equal(delayed.value.value.profile, 'jftn');
});
