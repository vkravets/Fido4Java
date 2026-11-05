import assert from 'node:assert/strict';
import { test } from 'node:test';
import { nextTick, ref } from 'vue';
import { createEchoDiscussion } from '../src/echo-discussion.ts';
import type { EchoContext, MailItem } from '../../build/frontend-generated/bindings.mjs';

test('branch paging deduplicates cycles and retry preserves loaded replies', async () => {
    const root = { id: 1 } as MailItem;
    const child = { id: 2 } as MailItem;
    const selected = ref<MailItem | null>(null);
    const context = ref<EchoContext | null>(null);
    let fail = true;
    const tree = createEchoDiscussion(
        ref('token'),
        ref('test'),
        selected,
        context,
        ref('current'),
        async () => {
            if (fail) throw new Error('offline');
            return { replies: { items: [child, root], nextId: 3, hasMore: false } } as EchoContext;
        }
    );
    selected.value = root;
    context.value = { replies: { items: [child], nextId: 2, hasMore: true } } as EchoContext;
    await nextTick();
    await tree.load(root, true);
    assert.equal(tree.failures.value.has('1'), true);
    assert.deepEqual(
        tree.rows.value.map((row) => row.item.id),
        [1, 2]
    );
    fail = false;
    await tree.load(root, true);
    assert.deepEqual(
        tree.rows.value.map((row) => row.item.id),
        [1, 2]
    );
    assert.equal(tree.failures.value.size, 0);
});

test('discussion keeps the selected path across paginated ancestors and ignores stale branches', async () => {
    const root = { id: 1 } as MailItem;
    const child = { id: 2 } as MailItem;
    const context = (parent: MailItem | undefined, items: MailItem[]): EchoContext =>
        ({ parent, replies: { items, nextId: 3, hasMore: true } }) as EchoContext;
    const selected = ref<MailItem | null>(null);
    const links = ref<EchoContext | null>(null);
    const area = ref('test');
    let finish!: (value: EchoContext) => void;
    const tree = createEchoDiscussion(
        ref('token'),
        area,
        selected,
        links,
        ref('whole'),
        async (_token, _area, id) =>
            id === 1
                ? context(undefined, [])
                : new Promise((resolve) => {
                      finish = resolve;
                  })
    );
    selected.value = child;
    links.value = context(root, []);
    await nextTick();
    await nextTick();
    assert.deepEqual(
        tree.rows.value.map((row) => row.item.id),
        [1, 2]
    );
    const request = tree.load(child, true);
    area.value = 'other';
    finish(context(root, [root]));
    await request;
    assert.equal(tree.links.value.size, 0);
});
