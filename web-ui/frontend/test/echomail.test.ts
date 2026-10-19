import assert from 'node:assert/strict';
import { test } from 'node:test';
import { ref } from 'vue';
import { createEchomail } from '../src/echomail.ts';
import { echoThreads } from '../src/echo-threads.ts';
import { mailId } from '../src/netmail.ts';
import type { EchoClient } from '../src/echomail.ts';
import type { MailDraft, MailItem, UserProfile } from '../../build/frontend-generated/bindings.mjs';

function message(id: string, messageId: string, replyTo?: string): MailItem {
    return {
        id,
        messageId,
        replyTo,
        fromName: 'Alice',
        fromAddress: '1:500/101',
        toName: 'All',
        toAddress: '',
        subject: 'A discussion',
        date: '20 Oct 26',
        read: false,
        body: 'First\rSecond'
    };
}

test('thread forest preserves large IDs, missing parents, duplicate MSGIDs and cyclic mail', () => {
    const rows = [
        message('9007199254740993', 'root'),
        message('9007199254740994', 'child', 'root'),
        message('9007199254740995', 'orphan', 'missing'),
        message('9007199254740996', 'loop-a', 'loop-b'),
        message('9007199254740997', 'loop-b', 'loop-a'),
        message('9007199254740998', 'root')
    ];
    const tree = echoThreads(rows);
    assert.equal(tree.length, rows.length);
    assert.equal(tree[1]!.depth, 1);
    assert.equal(tree[1]!.parent, '9007199254740993');
    assert.equal(tree.find((row) => row.item.messageId === 'orphan')!.depth, 0);
    assert.equal(echoThreads(rows, new Set(['9007199254740993'])).length, rows.length - 1);
    assert.equal(new Set(tree.map((row) => mailId(row.item.id))).size, rows.length);
});

function fixture() {
    const state = {
        session: ref('token'),
        notice: ref(''),
        profile: ref({
            login: 'Point',
            allowedAkas: ['1:500/100.1', '1:500/100.2'],
            defaultAka: '1:500/100.2'
        } as UserProfile)
    };
    const first = message('9007199254740993', 'root');
    const sent: { draft: MailDraft; request: string }[] = [];
    const read: string[] = [];
    const client: EchoClient = {
        areas: async () => [
            { name: 'test', messages: 1, readable: true, writeAddresses: ['1:500/100.1'] },
            { name: 'read.only', messages: 0, readable: true, writeAddresses: [] }
        ],
        echoMessages: async () => ({ items: [first], nextId: first.id, hasMore: false }),
        message: async () => ({ ...first }),
        markRead: async (_token, _box, _area, id) => {
            read.push(mailId(id));
        },
        echoContext: async () => ({
            replies: { items: [], nextId: 0, hasMore: false },
            missingParent: false
        }),
        drafts: async () => [],
        saveDraft: async () => {},
        deleteDraft: async () => {},
        send: async (_token, draft, request) => {
            sent.push({ draft: { ...draft }, request });
            return 'sent';
        }
    };
    const reader = createEchomail(state, client, (value) => value);
    return { state, client, reader, first, sent, read };
}

test('reply stays in its echo and preserves REPLY, quoting, permitted AKA and request identity', async () => {
    const { reader, sent, read, first, client } = fixture();
    await reader.start();
    await reader.open(first);
    assert.deepEqual(read, [mailId(first.id)]);
    assert.equal(reader.selected.value!.read, true);
    reader.edit(undefined, reader.selected.value!);
    assert.equal(reader.draft.value!.mailbox, 1);
    assert.equal(reader.draft.value!.area, 'test');
    assert.equal(reader.draft.value!.fromAddress, '1:500/100.1');
    assert.equal(reader.draft.value!.replyTo, 'root');
    assert.equal(reader.draft.value!.toName, 'Alice');
    assert.equal(reader.draft.value!.body, '> First\n> Second');
    const send = client.send;
    let failed = true;
    client.send = async (token: string, draft: MailDraft, request: string) => {
        await send(token, draft, request);
        if (failed) throw new Error('retry');
        return 'sent';
    };
    await assert.rejects(reader.send(), /retry/);
    failed = false;
    await reader.send();
    assert.equal(sent[0]!.request, sent[1]!.request);
    assert.equal(reader.draft.value, null);
    await reader.selectArea('read.only');
    reader.edit();
    assert.equal(reader.draft.value, null);
});

test('late message responses cannot replace another area or another session', async () => {
    const { reader, first, state, client, read } = fixture();
    await reader.start();
    let finish!: (value: MailItem) => void;
    client.message = () =>
        new Promise((resolve) => {
            finish = resolve;
        });
    const pending = reader.open(first);
    await reader.selectArea('read.only');
    finish(first);
    await pending;
    assert.equal(reader.selected.value, null);
    await reader.selectArea('test');
    const second = reader.open(first);
    state.session.value = 'another-token';
    finish(first);
    await second;
    assert.equal(reader.selected.value, null);
    assert.deepEqual(read, []);
});

test('saved echo drafts keep their conference and recipients and are separate from netmail', async () => {
    const { reader, client } = fixture();
    const draft: MailDraft = {
        id: 'saved',
        mailbox: 1,
        area: 'test',
        fromName: 'Point',
        fromAddress: '1:500/100.1',
        toName: 'All',
        toAddress: '1:500/100',
        subject: 'Saved',
        body: 'Text'
    };
    client.drafts = async () => [draft, { ...draft, id: 'netmail', mailbox: 3, area: '' }];
    await reader.start(draft);
    assert.equal(reader.draft.value!.subject, 'Saved');
    await reader.loadDrafts();
    assert.equal(reader.drafts.value.length, 1);
    await reader.editDraft(reader.drafts.value[0]!);
    assert.equal(reader.area.value, 'test');
    assert.equal(reader.draft.value!.toName, 'All');
});
