import assert from 'node:assert/strict';
import { test } from 'node:test';
import { effectScope, ref } from 'vue';
import { createArchiveSearch } from '../src/archive-search.ts';
import type { SearchClient, SearchScope } from '../src/archive-search.ts';
import type { MailItem, MailSearchPage } from '../../build/frontend-generated/bindings.mjs';

const item = (id = '9007199254740993'): MailItem => ({
    id,
    fromName: 'Alice',
    toName: 'All',
    fromAddress: '1:500/101',
    toAddress: '',
    subject: 'Notice',
    date: '',
    messageId: '',
    read: false,
    excerpt: 'Opening words'
});
const page = (
    items: MailItem[] = [],
    nextId: MailItem['id'] = '10',
    hasMore = false
): MailSearchPage => ({ items, nextId, hasMore, scanned: 1000 });
function fixture(client: SearchClient) {
    const scope = effectScope();
    const session = ref('account-a');
    const location = ref<SearchScope | null>({ mailbox: 1, area: 'general' });
    const query = ref('');
    const source = ref([item()]);
    const authFailures: unknown[] = [];
    const search = scope.run(() =>
        createArchiveSearch(
            session,
            location,
            query,
            source,
            client,
            (error) => authFailures.push(error),
            60_000
        )
    )!;
    return { scope, session, location, query, source, search, authFailures };
}

test('disabled archive search falls back only on FEATURE_DISABLED and remembers it for that session', async () => {
    let calls = 0;
    const f = fixture({
        searchMessages: async () => {
            calls++;
            throw { code: 'FEATURE_DISABLED' };
        }
    });
    try {
        f.query.value = 'Notice';
        assert.deepEqual(f.search.items.value, f.source.value);
        await f.search.refresh();
        assert.equal(f.search.remote.value, false);
        assert.deepEqual(f.search.items.value, f.source.value);
        assert.equal(f.search.failure.value, false);
        f.query.value = 'Alice';
        await f.search.refresh();
        assert.equal(calls, 1);
        f.session.value = 'account-b';
        await f.search.refresh();
        assert.equal(calls, 2);
    } finally {
        f.scope.stop();
    }
});

test('empty continuation pages retain their cursor and archive-only matches leave the loaded cache intact', async () => {
    const cursors: string[] = [];
    const f = fixture({
        searchMessages: async (_token, _box, _area, _query, after) => {
            cursors.push(String(after));
            return cursors.length === 1
                ? page([], '9007199254740998', true)
                : page([item('9007199254740999')], '9007199254740999');
        }
    });
    try {
        f.query.value = 'found deep in body';
        await f.search.refresh();
        assert.equal(f.search.remote.value, true);
        assert.equal(f.search.hasMore.value, true);
        assert.equal(f.search.items.value.length, 0);
        await f.search.loadMore();
        assert.deepEqual(cursors, ['0', '9007199254740998']);
        assert.equal(f.search.items.value[0]!.id, '9007199254740999');
        assert.equal(f.source.value[0]!.id, '9007199254740993');
        assert.equal(f.search.scanned.value, 2000);
        f.query.value = '';
        assert.deepEqual(f.search.items.value, f.source.value);
        assert.equal(f.search.remote.value, false);
    } finally {
        f.scope.stop();
    }
});

test('late searches cannot cross queries, mailboxes, accounts or disposal', async () => {
    const replies: ((page: MailSearchPage) => void)[] = [];
    const f = fixture({
        searchMessages: async () => new Promise<MailSearchPage>((resolve) => replies.push(resolve))
    });
    try {
        f.query.value = 'first';
        const old = f.search.refresh();
        f.query.value = 'second';
        const current = f.search.refresh();
        replies[1]!(page([item('22')], '22'));
        await current;
        replies[0]!(page([item('11')], '11'));
        await old;
        assert.equal(f.search.items.value[0]!.id, '22');
        const changing = f.search.refresh();
        f.location.value = { mailbox: 2, area: '' };
        replies[2]!(page([item('33')], '33'));
        await changing;
        assert.equal(f.search.items.value.length, 0);
        const switching = f.search.refresh();
        f.session.value = 'account-b';
        replies[3]!(page([item('44')], '44'));
        await switching;
        assert.equal(f.search.items.value.length, 0);
        const disposing = f.search.refresh();
        f.scope.stop();
        replies[4]!(page([item('55')], '55'));
        await disposing;
        assert.equal(f.search.items.value.length, 0);
    } finally {
        f.scope.stop();
    }
});

test('access and network failures stay visible, can be retried and never silently fall back', async () => {
    let calls = 0;
    const f = fixture({
        searchMessages: async () => {
            if (++calls === 1) throw { code: 'FORBIDDEN' };
            return page([{ ...item(), read: true }]);
        }
    });
    try {
        f.query.value = 'Alice';
        await f.search.refresh();
        assert.equal(f.search.remote.value, true);
        assert.equal(f.search.failure.value, true);
        assert.equal(f.search.hasMore.value, true);
        assert.equal(f.search.items.value.length, 0);
        await f.search.loadMore();
        assert.equal(f.search.failure.value, false);
        assert.equal(f.search.items.value.length, 1);
        assert.equal(f.search.items.value[0]!.read, true);
        assert.equal(f.source.value[0]!.read, true);
    } finally {
        f.scope.stop();
    }
});

test('short queries and drafts remain local and authentication failures reach session handling', async () => {
    let calls = 0;
    const f = fixture({
        searchMessages: async () => {
            calls++;
            throw { code: 'UNAUTHORIZED' };
        }
    });
    try {
        f.query.value = 'a';
        await f.search.refresh();
        assert.equal(calls, 0);
        f.location.value = null;
        f.query.value = 'Alice';
        await f.search.refresh();
        assert.equal(calls, 0);
        f.location.value = { mailbox: 3, area: '' };
        await f.search.refresh();
        assert.equal(f.authFailures.length, 1);
    } finally {
        f.scope.stop();
    }
});

test('debounce issues one request for rapid input and marks read remain current', async () => {
    const scope = effectScope();
    const query = ref('');
    const source = ref([item()]);
    let calls = 0;
    const search = scope.run(() =>
        createArchiveSearch(
            ref('token'),
            ref({ mailbox: 2, area: '' }),
            query,
            source,
            {
                searchMessages: async () => {
                    calls++;
                    return page([item()]);
                }
            },
            () => {},
            5
        )
    )!;
    try {
        query.value = 'Al';
        query.value = 'Ali';
        query.value = 'Alice';
        await new Promise((resolve) => setTimeout(resolve, 30));
        assert.equal(calls, 1);
        source.value[0]!.read = true;
        assert.equal(search.items.value[0]!.read, true);
    } finally {
        scope.stop();
    }
});
