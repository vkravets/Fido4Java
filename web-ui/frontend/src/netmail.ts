import { ref } from 'vue';
import type { InjectionKey } from 'vue';
import { t } from './i18n.ts';
import type { AppState } from './state.ts';
import type { MailDraft, MailItem, Mailbox } from '@rpc/bindings.mjs';
type NetmailClient = Pick<
    typeof import('./api.ts').api.ui,
    | 'drafts'
    | 'messages'
    | 'message'
    | 'markRead'
    | 'saveDraft'
    | 'send'
    | 'deleteDraft'
    | 'deleteMessage'
>;
export const netmailClientKey: InjectionKey<NetmailClient> = Symbol('netmail-client');

export type MailFolder = 'inbox' | 'outbox' | 'drafts';

/** Mail IDs remain decimal strings: Thrift i64 values can exceed JS safe integers. */
export function mailId(value: unknown): string {
    if (
        value &&
        typeof value === 'object' &&
        'toOctetString' in value &&
        typeof value.toOctetString === 'function'
    ) {
        return BigInt.asIntN(64, BigInt(`0x${value.toOctetString()}`)).toString();
    }
    return String(value);
}

export function quotedReply(item: MailItem): string {
    return (item.body || '')
        .replaceAll('\r\n', '\n')
        .replaceAll('\r', '\n')
        .split('\n')
        .map((line) => `> ${line}`)
        .join('\n');
}

export function createNetmail(
    state: Pick<AppState, 'session' | 'profile' | 'notice'>,
    client: NetmailClient,
    createDraft: (value: MailDraft) => MailDraft,
    queueChanged: () => void = () => {}
) {
    const folder = ref<MailFolder>('inbox');
    const items = ref<MailItem[]>([]);
    const drafts = ref<MailDraft[]>([]);
    const selected = ref<MailItem | null>(null);
    const draft = ref<MailDraft | null>(null);
    const hasMore = ref(false);
    let cursor: MailItem['id'] = 0;
    let requestId = '';
    let original = '';
    let generation = 0;
    let opening = 0;

    function cancelOpen() {
        opening++;
    }

    function mailbox(): Mailbox {
        return folder.value === 'outbox' ? 3 : 2;
    }

    function discard(): boolean {
        return (
            !draft.value ||
            JSON.stringify(draft.value) === original ||
            window.confirm(t('Discard unsaved changes?'))
        );
    }

    async function load(next = false) {
        const ticket = ++generation;
        const token = state.session.value;
        if (folder.value === 'drafts') {
            const result = await client.drafts(token);
            if (ticket === generation && token === state.session.value) {
                drafts.value = result;
            }
            return;
        }
        const page = await client.messages(token, mailbox(), '', next ? cursor : 0, 25);
        if (ticket !== generation || token !== state.session.value) return;
        items.value = next ? [...items.value, ...page.items] : page.items;
        cursor = page.nextId;
        hasMore.value = page.hasMore;
    }

    async function navigate(value: MailFolder) {
        if (!discard()) return;
        cancelOpen();
        folder.value = value;
        draft.value = null;
        selected.value = null;
        items.value = [];
        hasMore.value = false;
        cursor = 0;
        await load();
    }

    async function open(item: MailItem) {
        if (!discard()) return;
        const ticket = ++opening;
        const token = state.session.value;
        const box = mailbox();
        const message = await client.message(token, box, '', item.id);
        if (ticket !== opening || token !== state.session.value) return;
        await client.markRead(token, box, '', item.id, true);
        if (ticket !== opening || token !== state.session.value) return;
        message.read = true;
        item.read = true;
        selected.value = message;
        draft.value = null;
    }

    function edit(value?: MailDraft, reply?: MailItem) {
        if (!discard()) return;
        cancelOpen();
        const profile = state.profile.value!;
        const addresses = profile.allowedAkas;
        const from =
            reply && addresses.includes(reply.toAddress)
                ? reply.toAddress
                : profile.defaultAka || addresses[0] || '';
        draft.value = value
            ? createDraft({ ...value })
            : createDraft({
                  id: crypto.randomUUID(),
                  composeKind: reply ? 'quoted' : 'new',
                  templateInitialized: false,
                  ...(reply
                      ? {
                            originalBody: reply.body || '',
                            originalDate: reply.date,
                            originalName: reply.fromName,
                            originalAddress: reply.fromAddress
                        }
                      : {}),
                  mailbox: 3,
                  area: '',
                  fromName: profile.displayName || profile.login,
                  fromAddress: from,
                  toName: reply?.fromName || '',
                  toAddress: reply?.fromAddress || '',
                  subject: reply
                      ? /^re:/i.test(reply.subject)
                          ? reply.subject
                          : `Re: ${reply.subject}`
                      : '',
                  body: reply ? quotedReply(reply) : '',
                  ...(reply?.messageId ? { replyTo: reply.messageId } : {})
              });
        original = JSON.stringify(draft.value);
        requestId = crypto.randomUUID();
    }

    function initializeDraft() {
        original = JSON.stringify(draft.value);
    }
    async function save() {
        if (!draft.value) return;
        await client.saveDraft(state.session.value, draft.value);
        original = JSON.stringify(draft.value);
        state.notice.value = t('Draft saved.');
    }

    async function send() {
        if (!draft.value) return;
        await client.send(state.session.value, draft.value, requestId);
        draft.value = null;
        selected.value = null;
        state.notice.value = t('Message queued for delivery.');
        await navigate('outbox');
        queueChanged();
    }

    async function removeMessage() {
        const item = selected.value;
        if (
            !item ||
            !window.confirm(
                t('Delete this message? Waiting mail is cancelled; existing packets remain.')
            )
        )
            return;
        const token = state.session.value;
        await client.deleteMessage(token, mailbox(), item.id);
        if (token !== state.session.value) return;
        selected.value = null;
        state.notice.value = t('Message deleted.');
        await load();
        queueChanged();
    }

    async function remove(value: MailDraft) {
        if (!window.confirm(t('Delete this draft?'))) return;
        await client.deleteDraft(state.session.value, value.id);
        if (draft.value?.id === value.id) draft.value = null;
        await load();
    }

    return {
        folder,
        items,
        drafts,
        selected,
        draft,
        hasMore,
        load,
        navigate,
        open,
        cancelOpen,
        edit,
        initializeDraft,
        save,
        send,
        remove,
        removeMessage,
        discard
    };
}

export function createMailQueue(
    state: Pick<AppState, 'session' | 'notice'>,
    client: Pick<typeof import('./api.ts').api.ui, 'pendingNetmail' | 'sendPendingNetmail'>
) {
    const count = ref(0);
    let generation = 0;
    async function refresh() {
        const token = state.session.value;
        const ticket = ++generation;
        if (!token) {
            count.value = 0;
            return;
        }
        const pending = await client.pendingNetmail(token);
        if (ticket === generation && token === state.session.value) count.value = pending;
    }
    async function send() {
        const token = state.session.value;
        await client.sendPendingNetmail(token);
        if (token !== state.session.value) return;
        state.notice.value = t('Queued mail released for delivery.');
        await refresh();
    }
    return { count, refresh, send };
}
