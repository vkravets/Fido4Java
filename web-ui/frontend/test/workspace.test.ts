import assert from 'node:assert/strict';
import { test } from 'node:test';
import { clearReaderSession, createWorkspace, parseWorkspace } from '../src/workspace.ts';

test('reader visibility belongs to the tab session and stays out of account preferences', async () => {
    const original = Object.getOwnPropertyDescriptor(globalThis, 'sessionStorage');
    const data = new Map<string, string>();
    Object.defineProperty(globalThis, 'sessionStorage', {
        configurable: true,
        value: {
            getItem: (key: string) => data.get(key) ?? null,
            setItem: (key: string, value: string) => data.set(key, value),
            removeItem: (key: string) => data.delete(key)
        }
    });
    try {
        const workspace = createWorkspace();
        await workspace.load('first');
        assert.equal(workspace.inlineKludges.value, false);
        workspace.inlineKludges.value = true;
        await workspace.load('first');
        assert.equal(workspace.inlineKludges.value, true);
        assert.equal('inlineKludges' in workspace.value.value, false);
        const reloaded = createWorkspace();
        await reloaded.load('first');
        assert.equal(reloaded.inlineKludges.value, true);
        await reloaded.load('second');
        assert.equal(reloaded.inlineKludges.value, false);
        reloaded.inlineKludges.value = true;
        await reloaded.load('');
        assert.equal(reloaded.inlineKludges.value, false);
        workspace.inlineKludges.value = false;
        workspace.inlineKludges.value = true;
        clearReaderSession();
        const newLogin = createWorkspace();
        await newLogin.load('third');
        assert.equal(newLogin.inlineKludges.value, false);
        Object.defineProperty(globalThis, 'sessionStorage', {
            configurable: true,
            get() {
                throw new Error('blocked');
            }
        });
        const blocked = createWorkspace();
        await blocked.load('blocked');
        blocked.inlineKludges.value = true;
        assert.equal(blocked.inlineKludges.value, true);
        clearReaderSession();
    } finally {
        if (original) Object.defineProperty(globalThis, 'sessionStorage', original);
        else Reflect.deleteProperty(globalThis, 'sessionStorage');
    }
});

test('layout documents normalize unsafe values and keep mailboxes independent', () => {
    const value = parseWorkspace(
        '{"collapsed":true,"netmail":{"preview":"below","ratio":999},"echo":{"preview":"javascript","ratio":-50}}'
    );
    assert.equal(value.netmail.preview, 'below');
    assert.equal(value.netmail.ratio, 75);
    assert.equal(value.echo.preview, 'right');
    assert.equal(value.echo.ratio, 20);
    assert.equal(parseWorkspace('broken').collapsed, true);
    assert.equal(parseWorkspace('{}').collapsed, true);
    assert.equal(parseWorkspace('{"collapsed":false}').collapsed, false);
    assert.equal(parseWorkspace('{}').startupPage, 'netmail');
    assert.equal(parseWorkspace('{"startupPage":"node"}').startupPage, 'netmail');
    assert.equal(parseWorkspace('{"startupPage":"echo"}').startupPage, 'echo');
    assert.equal(parseWorkspace('{}').unknownKludges, 'always');
    assert.equal(parseWorkspace('{"unknownKludges":"bad"}').unknownKludges, 'always');
    assert.equal(parseWorkspace('{"unknownKludges":"toggle"}').unknownKludges, 'toggle');
});
test('late loads cannot replace another account and failed saves retain previous settings', async () => {
    let finish!: (value: string) => void;
    const client = {
        uiDocument: () =>
            new Promise<string>((resolve) => {
                finish = resolve;
            }),
        saveUiDocument: async () => {
            throw new Error('offline');
        }
    };
    const workspace = createWorkspace(client);
    const pending = workspace.load('first');
    const resolve = finish;
    const second = workspace.load('second');
    resolve('{"collapsed":false}');
    await pending;
    assert.equal(workspace.value.value.collapsed, true);
    finish('{"collapsed":false}');
    await second;
    await assert.rejects(workspace.update({ collapsed: true }), /offline/);
    assert.equal(workspace.value.value.collapsed, false);
});

test('concurrent field saves are serialized and preserve independent layout edits', async () => {
    const snapshots: string[] = [];
    const workspace = createWorkspace({
        uiDocument: async () => '{}',
        saveUiDocument: async (_token, _key, value) => {
            snapshots.push(value);
        }
    });
    await workspace.load('account');
    await Promise.all([
        workspace.updateLayout('echo', { density: 'compact' }),
        workspace.updateLayout('echo', { mode: 'flat' }),
        workspace.update({ collapsed: true, startupPage: 'directory', unknownKludges: 'toggle' })
    ]);
    assert.equal(workspace.value.value.echo.mode, 'flat');
    assert.equal(workspace.value.value.echo.density, 'compact');
    assert.equal(JSON.parse(snapshots.at(-1)!).collapsed, true);
    assert.equal(JSON.parse(snapshots.at(-1)!).startupPage, 'directory');
    assert.equal(JSON.parse(snapshots.at(-1)!).unknownKludges, 'toggle');
    const reloaded = createWorkspace({
        uiDocument: async () => snapshots.at(-1)!,
        saveUiDocument: async () => {}
    });
    await reloaded.load('account');
    assert.equal(reloaded.value.value.unknownKludges, 'toggle');
});
