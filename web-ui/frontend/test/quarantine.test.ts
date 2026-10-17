import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createQuarantine } from '../src/quarantine.ts';
import type { QuarantineItem } from '../../build/frontend-generated/bindings.mjs';

function fixture() {
    const calls: string[][] = [];
    let fail = false;
    const item = { id: 'a'.repeat(64), checksum: 'b'.repeat(64), peer: '' } as QuarantineItem;
    const client = {
        async quarantine(session: string, cursor: string) {
            calls.push(['list', session, cursor]);
            return { items: [item], peers: ['1:500/101.0'], nextCursor: item.id, hasMore: true };
        },
        async retryQuarantine(...args: string[]) {
            calls.push(['retry', ...args]);
            if (fail) throw new Error('Failed');
            return { accepted: false, detail: 'PKT password mismatch' };
        },
        async archiveQuarantine(...args: string[]) {
            calls.push(['archive', ...args]);
        }
    };
    const editor = createQuarantine(
        client as unknown as Parameters<typeof createQuarantine>[0],
        () => 'admin'
    );
    return {
        editor,
        calls,
        item,
        fail: () => {
            fail = true;
        }
    };
}
test('legacy retry needs explicit configured peer and sends the original snapshot checksum', async () => {
    const { editor, item, calls } = fixture();
    await editor.refresh();
    editor.select(item, 'retry');
    await assert.rejects(editor.confirm(), /expected source/);
    editor.peer.value = '1:500/101.0';
    await editor.confirm();
    assert.deepEqual(calls[1], ['retry', 'admin', item.id, item.checksum, '1:500/101.0']);
    assert.equal(editor.result.value?.accepted, false);
    assert.equal(editor.selected.value, null);
});
test('recorded peer cannot be replaced; failed retry preserves selection', async () => {
    const { editor, item, calls, fail } = fixture();
    await editor.refresh();
    item.peer = '1:500/101.0';
    editor.select(item, 'retry');
    editor.peer.value = '1:500/999.0';
    fail();
    await assert.rejects(editor.confirm(), /Failed/);
    assert.equal(calls[1]?.[4], item.peer);
    assert.equal(editor.selected.value?.id, item.id);
});
test('archive requires confirmation, never retries mail and resets pagination after removal', async () => {
    const { editor, item, calls } = fixture();
    await editor.refresh(item.id);
    await editor.confirm();
    assert.equal(calls.length, 1);
    editor.select(item, 'archive');
    await editor.confirm();
    assert.deepEqual(calls[1], ['archive', 'admin', item.id, item.checksum]);
    assert.deepEqual(calls[2], ['list', 'admin', '']);
});
