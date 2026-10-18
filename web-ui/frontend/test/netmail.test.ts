import assert from 'node:assert/strict';
import { test } from 'node:test';
import { ref } from 'vue';
import { createNetmail, mailId, quotedReply } from '../src/netmail.ts';
import type {
    MailDraft,
    MailItem,
    MailPage,
    UserProfile
} from '../../build/frontend-generated/bindings.mjs';

const message = (): MailItem => ({
    id: '9007199254740993',
    fromName: 'Alice',
    toName: 'Point',
    fromAddress: '1:500/101',
    toAddress: '1:500/100.1',
    subject: 'Hello',
    date: '18 Oct 26',
    messageId: '1:500/101 abc',
    read: false,
    body: 'First\rSecond\r\nThird'
});

function fixture() {
    const calls: { id: unknown; request?: string }[] = [];
    const state = {
        session: ref('session'),
        notice: ref(''),
        profile: ref({
            login: 'point',
            defaultAka: '1:500/100.1',
            allowedAkas: ['1:500/100.1']
        } as UserProfile)
    };
    const client = {
        messages: async (
            _token: string,
            _box: unknown,
            _area: string,
            id: unknown
        ): Promise<MailPage> => {
            calls.push({ id });
            return { items: [message()], nextId: message().id, hasMore: false };
        },
        message: async () => message(),
        markRead: async () => {},
        drafts: async () => [],
        saveDraft: async () => {},
        deleteDraft: async () => {},
        send: async (_token: string, value: MailDraft, request: string) => {
            calls.push({ id: value.id, request });
            throw new Error('response lost');
        }
    };
    const reader = createNetmail(
        state,
        client as Parameters<typeof createNetmail>[1],
        (value) => value
    );
    return { state, client, reader, calls };
}

test('preserves i64 identifiers and quotes reply text without HTML interpretation', () => {
    assert.equal(mailId({ toOctetString: () => '0020000000000001' }), '9007199254740993');
    assert.equal(quotedReply(message()), '> First\n> Second\n> Third');
});

test('opens the full message, marks it read and replies from the addressed approved AKA', async () => {
    const { reader } = fixture();
    await reader.open(message());
    assert.equal(reader.selected.value?.read, true);
    reader.edit(undefined, reader.selected.value!);
    assert.equal(reader.draft.value?.fromAddress, '1:500/100.1');
    assert.equal(reader.draft.value?.toAddress, '1:500/101');
    assert.equal(reader.draft.value?.replyTo, '1:500/101 abc');
    assert.equal(reader.draft.value?.subject, 'Re: Hello');
});

test('keeps the request identity after a lost send response so retry cannot duplicate mail', async () => {
    const { reader, calls } = fixture();
    reader.edit();
    await assert.rejects(reader.send, /response lost/);
    await assert.rejects(reader.send, /response lost/);
    assert.equal(calls[0]?.request, calls[1]?.request);
    assert.ok(reader.draft.value);
});

test('does not accept a list response after logout', async () => {
    const { reader, client, state } = fixture();
    let finish!: (page: MailPage) => void;
    client.messages = () =>
        new Promise((resolve) => {
            finish = resolve;
        });
    const request = reader.load();
    state.session.value = '';
    finish({ items: [message()], nextId: message().id, hasMore: false });
    await request;
    assert.deepEqual(reader.items.value, []);
});
