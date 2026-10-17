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

test('batch validates every source before releasing anything and cancellation changes no mail', async () => {
    const { editor, item, calls } = fixture();
    await editor.refresh();
    editor.checked.value = [item.id];
    editor.selectBatch();
    await assert.rejects(editor.confirm(), /expected source/);
    assert.equal(calls.length, 1);
    assert.equal(editor.batch.value.length, 1);
    editor.cancel();
    await editor.confirm();
    assert.equal(calls.length, 1);
});

test('batch keeps recorded peers, continues after errors and reports each packet', async () => {
    const { editor, item, calls, fail } = fixture();
    await editor.refresh();
    const known = { ...item, id: 'c'.repeat(64), name: 'known.bad', peer: '1:500/101.0' };
    editor.page.value!.items.push(known);
    editor.checked.value = [item.id, known.id];
    editor.peers.value = { [item.id]: '1:500/101.0', [known.id]: '1:500/999.0' };
    editor.selectBatch();
    fail();
    await editor.confirm();
    assert.deepEqual(calls.slice(1, 3), [
        ['retry', 'admin', item.id, item.checksum, '1:500/101.0'],
        ['retry', 'admin', known.id, known.checksum, '1:500/101.0']
    ]);
    assert.equal(editor.outcomes.value.length, 2);
    assert.ok(
        editor.outcomes.value.every((outcome) => !outcome.accepted && outcome.detail === 'Failed')
    );
    assert.equal(editor.batch.value.length, 0);
    assert.deepEqual(editor.checked.value, []);
    assert.match(editor.result.value!.detail, /0 accepted, 2 failed/);
});

test('invalid source or missing checksum on a later packet prevents the entire batch', async () => {
    const { editor, item, calls } = fixture();
    await editor.refresh();
    item.peer = '1:500/101.0';
    const second = { ...item, id: 'c'.repeat(64), peer: '1:500/999.0' };
    editor.page.value!.items.push(second);
    editor.checked.value = [item.id, second.id];
    editor.selectBatch();
    await assert.rejects(editor.confirm(), /expected source/);
    assert.equal(calls.length, 1);
    editor.batch.value[1]!.peer = item.peer;
    editor.batch.value[1]!.checksum = '';
    await assert.rejects(editor.confirm(), /manual inspection/);
    assert.equal(calls.length, 1);
});

test('batch reports accepted and rejected results without retrying unselected packets', async () => {
    const retried: string[] = [];
    const items = ['first', 'second', 'unselected'].map(
        (id) =>
            ({
                id,
                name: id + '.bad',
                checksum: id + '-hash',
                peer: '1:500/101.0'
            }) as QuarantineItem
    );
    const editor = createQuarantine(
        {
            async quarantine() {
                return { items, peers: ['1:500/101.0'], hasMore: false, nextCursor: '' };
            },
            async retryQuarantine(_session, id) {
                retried.push(id);
                return {
                    accepted: id === 'first',
                    detail: id === 'first' ? 'Accepted' : 'Rejected'
                };
            },
            async archiveQuarantine() {
                assert.fail('Batch retry must not archive packets');
            }
        },
        () => 'admin'
    );
    await editor.refresh();
    editor.checked.value = ['first', 'second'];
    editor.selectBatch();
    await editor.confirm();
    assert.deepEqual(retried, ['first', 'second']);
    assert.deepEqual(
        editor.outcomes.value.map((outcome) => outcome.accepted),
        [true, false]
    );
    assert.match(editor.result.value!.detail, /1 accepted, 1 failed/);
});
