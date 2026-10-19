import { mailId } from '../src/netmail.ts';
import type { EchoClient } from '../src/echomail.ts';
import type { AreaInfo, MailDraft, MailItem } from '@rpc/bindings.mjs';

export const echoMessages: MailItem[] = [
    {
        id: '9007199254740993',
        fromName: 'Alice Example',
        toName: 'All',
        fromAddress: '1:500/101',
        toAddress: '',
        subject: 'Welcome to the conference',
        date: '20 Oct 26  10:00:00',
        messageId: '1:500/101 root',
        read: true,
        body: 'Hello everyone!\n\nThis conference is a place to share news and ask questions.\nPlain text is displayed safely: <script>alert("hello")</script>'
    },
    {
        id: '9007199254740994',
        fromName: 'Bob Example',
        toName: 'Alice Example',
        fromAddress: '1:500/102',
        toAddress: '1:500/101',
        subject: 'Re: Welcome to the conference',
        date: '20 Oct 26  11:00:00',
        messageId: '1:500/102 reply',
        replyTo: '1:500/101 root',
        read: false,
        body: '> Hello everyone!\n\nThanks Alice, glad to be here.'
    },
    {
        id: '9007199254740995',
        fromName: 'Carol Example',
        toName: 'Bob Example',
        fromAddress: '1:500/103',
        toAddress: '1:500/102',
        subject: 'Re: Welcome to the conference',
        date: '20 Oct 26  12:00:00',
        messageId: '1:500/103 reply',
        replyTo: '1:500/102 reply',
        read: false,
        body: 'Welcome Bob!\n\n> Thanks Alice, glad to be here.'
    },
    {
        id: '9007199254740996',
        fromName: 'Dana Example',
        toName: 'All',
        fromAddress: '1:500/104',
        toAddress: '',
        subject: 'A question about packet routing',
        date: '20 Oct 26  13:00:00',
        messageId: '1:500/104 orphan',
        replyTo: '1:500/104 missing',
        read: false,
        body: 'The original message is not in the local archive.\n\nHow do you configure your uplinks?'
    }
];
export const echoDraft: MailDraft = {
    id: 'echo-saved',
    mailbox: 1,
    area: 'general',
    fromName: 'Test Point',
    fromAddress: '1:500/100.1',
    toName: 'All',
    toAddress: '1:500/100.1',
    subject: 'Hello from my point',
    body: 'Hello everyone!\n\nI have just connected my station.'
};

export function echoClient(
    options: { empty?: boolean; readOnly?: boolean; noAreas?: boolean } = {}
): EchoClient {
    const messages = options.empty ? [] : echoMessages.map((item) => ({ ...item }));
    const drafts: MailDraft[] = [{ ...echoDraft }];
    const area = (name: string): AreaInfo => ({
        name,
        messages: messages.length,
        readable: true,
        writeAddresses: options.readOnly ? [] : ['1:500/100.1']
    });
    return {
        areas: async () =>
            options.noAreas
                ? []
                : [area('general'), { ...area('announcements'), messages: 0, writeAddresses: [] }],
        echoMessages: async (_token, name) => ({
            items:
                name === 'general'
                    ? messages
                          .slice()
                          .reverse()
                          .map((item) => ({ ...item }))
                    : [],
            nextId: messages[0]?.id || 0,
            hasMore: false
        }),
        message: async (_token, _box, _area, id) => ({
            ...messages.find((item) => mailId(item.id) === mailId(id))!
        }),
        markRead: async (_token, _box, _area, id) => {
            const item = messages.find((row) => mailId(row.id) === mailId(id));
            if (item) item.read = true;
        },
        echoContext: async (_token, _area, id) => {
            const selected = messages.find((item) => mailId(item.id) === mailId(id))!;
            const parent = messages.find((item) => item.messageId === selected.replyTo);
            return {
                ...(parent ? { parent: { ...parent } } : {}),
                missingParent: !!selected.replyTo && !parent,
                replies: {
                    items: messages
                        .filter((item) => item.replyTo === selected.messageId)
                        .map((item) => ({ ...item })),
                    nextId: 0,
                    hasMore: false
                }
            };
        },
        drafts: async () => drafts.map((draft) => ({ ...draft })),
        saveDraft: async (_token, value) => {
            const index = drafts.findIndex((draft) => draft.id === value.id);
            if (index >= 0) drafts.splice(index, 1);
            drafts.push({ ...value });
        },
        deleteDraft: async (_token, id) => {
            const index = drafts.findIndex((draft) => draft.id === id);
            if (index >= 0) drafts.splice(index, 1);
        },
        send: async (_token, draft) => {
            const id = (BigInt(mailId(messages.at(-1)?.id || '9007199254740993')) + 1n).toString();
            const messageId = `${draft.fromAddress} posted-${id}`;
            messages.push({
                id,
                messageId,
                fromName: draft.fromName,
                fromAddress: draft.fromAddress,
                toName: draft.toName,
                toAddress: draft.toAddress,
                subject: draft.subject,
                body: draft.body,
                replyTo: draft.replyTo,
                date: '20 Oct 26  14:00:00',
                read: false
            });
            return messageId;
        }
    };
}
