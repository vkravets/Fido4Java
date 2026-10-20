import assert from 'node:assert/strict';
import { test } from 'node:test';
import { createWorkspace, parseWorkspace } from '../src/workspace.ts';

test('layout documents normalize unsafe values and keep mailboxes independent', () => {
    const value = parseWorkspace(
        '{"collapsed":true,"netmail":{"preview":"below","ratio":999},"echo":{"preview":"javascript","ratio":-50}}'
    );
    assert.equal(value.netmail.preview, 'below');
    assert.equal(value.netmail.ratio, 75);
    assert.equal(value.echo.preview, 'right');
    assert.equal(value.echo.ratio, 20);
    assert.equal(parseWorkspace('broken').collapsed, false);
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
    resolve('{"collapsed":true}');
    await pending;
    assert.equal(workspace.value.value.collapsed, false);
    finish('{}');
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
        workspace.update({ collapsed: true })
    ]);
    assert.equal(workspace.value.value.echo.mode, 'flat');
    assert.equal(workspace.value.value.echo.density, 'compact');
    assert.equal(JSON.parse(snapshots.at(-1)!).collapsed, true);
});
