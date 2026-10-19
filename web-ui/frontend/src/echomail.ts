import { computed, ref } from 'vue';
import type { InjectionKey } from 'vue';
import { t } from './i18n.ts';
import { mailId, quotedReply } from './netmail.ts';
import type { AppState } from './state.ts';
import type { AreaInfo, EchoContext, MailDraft, MailItem } from '@rpc/bindings.mjs';

export type EchoClient = Pick<
    typeof import('./api.ts').api.ui,
    | 'areas'
    | 'echoMessages'
    | 'message'
    | 'markRead'
    | 'echoContext'
    | 'drafts'
    | 'saveDraft'
    | 'deleteDraft'
    | 'send'
>;
export const echoClientKey: InjectionKey<EchoClient> = Symbol('echo-client');

export function createEchomail(
    state: Pick<AppState, 'session' | 'profile' | 'notice'>,
    client: EchoClient,
    createDraft: (value: MailDraft) => MailDraft
) {
    const areas = ref<AreaInfo[]>([]);
    const area = ref('');
    const items = ref<MailItem[]>([]);
    const drafts = ref<MailDraft[]>([]);
    const selected = ref<MailItem | null>(null);
    const context = ref<EchoContext | null>(null);
    const draft = ref<MailDraft | null>(null);
    const hasMore = ref(false);
    const showDrafts = ref(false);
    const currentArea = computed(() => areas.value.find((entry) => entry.name === area.value));
    const authors = computed(() => currentArea.value?.writeAddresses || []);
    let cursor: MailItem['id'] = 0;
    let generation = 0;
    let opening = 0;
    let original = '';
    let requestId = '';

    function discard() {
        return (
            !draft.value ||
            JSON.stringify(draft.value) === original ||
            window.confirm(t('Discard unsaved changes?'))
        );
    }
    async function refreshAreas() {
        const token = state.session.value;
        const result = await client.areas(token);
        if (token !== state.session.value) return;
        areas.value = result;
    }
    async function load(next = false) {
        if (!area.value || !currentArea.value?.readable) return;
        const ticket = ++generation;
        const token = state.session.value;
        const name = area.value;
        const page = await client.echoMessages(token, name, next ? cursor : 0, 50);
        if (ticket !== generation || token !== state.session.value || name !== area.value) return;
        const rows = new Map((next ? items.value : []).map((item) => [mailId(item.id), item]));
        page.items.forEach((item) => rows.set(mailId(item.id), item));
        items.value = [...rows.values()];
        cursor = page.nextId;
        hasMore.value = page.hasMore;
    }
    async function selectArea(name: string) {
        if (!discard()) return;
        generation++;
        opening++;
        area.value = name;
        draft.value = null;
        selected.value = null;
        context.value = null;
        items.value = [];
        showDrafts.value = false;
        cursor = 0;
        hasMore.value = false;
        await load();
    }
    async function start(initial?: MailDraft) {
        await refreshAreas();
        await selectArea(initial?.area || areas.value[0]?.name || '');
        if (initial && currentArea.value) edit(initial);
    }
    async function open(item: MailItem) {
        if (!discard()) return;
        const ticket = ++opening;
        const token = state.session.value;
        const name = area.value;
        const message = await client.message(token, 1, name, item.id);
        if (ticket !== opening || token !== state.session.value || name !== area.value) return;
        const links = await client.echoContext(token, name, item.id, 0, 25);
        if (ticket !== opening || token !== state.session.value || name !== area.value) return;
        await client.markRead(token, 1, name, item.id, true);
        if (ticket !== opening || token !== state.session.value || name !== area.value) return;
        message.read = true;
        const listed = items.value.find((value) => mailId(value.id) === mailId(item.id));
        if (listed) listed.read = true;
        selected.value = message;
        context.value = links;
        draft.value = null;
        showDrafts.value = false;
    }
    async function moreReplies() {
        if (!selected.value || !context.value) return;
        const ticket = opening;
        const token = state.session.value;
        const next = await client.echoContext(
            token,
            area.value,
            selected.value.id,
            context.value.replies.nextId,
            25
        );
        if (ticket !== opening || token !== state.session.value || !context.value) return;
        context.value.replies = {
            ...next.replies,
            items: [...context.value.replies.items, ...next.replies.items]
        };
    }
    function edit(value?: MailDraft, reply?: MailItem) {
        if (!discard() || !authors.value.length) return;
        const profile = state.profile.value!;
        const author = authors.value.includes(profile.defaultAka)
            ? profile.defaultAka
            : authors.value[0]!;
        draft.value = createDraft(
            value
                ? { ...value }
                : {
                      id: crypto.randomUUID(),
                      mailbox: 1,
                      area: area.value,
                      fromName: profile.login,
                      fromAddress: author,
                      toName: reply?.fromName || 'All',
                      toAddress: reply?.fromAddress || author,
                      subject: reply
                          ? /^re:/i.test(reply.subject)
                              ? reply.subject
                              : `Re: ${reply.subject}`
                          : '',
                      body: reply ? quotedReply(reply) : '',
                      ...(reply?.messageId ? { replyTo: reply.messageId } : {})
                  }
        );
        original = JSON.stringify(draft.value);
        requestId = crypto.randomUUID();
    }
    async function save() {
        if (!draft.value) return;
        await client.saveDraft(state.session.value, draft.value);
        original = JSON.stringify(draft.value);
        state.notice.value = t('Draft saved.');
    }
    async function send() {
        if (!draft.value) return;
        const token = state.session.value;
        await client.send(token, draft.value, requestId);
        if (token !== state.session.value) return;
        draft.value = null;
        state.notice.value = t('Message posted to the conference.');
        await refreshAreas();
        await selectArea(area.value);
    }
    async function loadDrafts() {
        if (!discard()) return;
        const ticket = ++generation;
        const token = state.session.value;
        const rows = await client.drafts(token);
        if (token !== state.session.value || ticket !== generation) return;
        drafts.value = rows.filter((value) => value.mailbox === 1);
        draft.value = null;
        showDrafts.value = true;
    }
    async function editDraft(value: MailDraft) {
        await selectArea(value.area);
        if (area.value === value.area) edit(value);
    }
    async function removeDraft(value: MailDraft) {
        if (!window.confirm(t('Delete this draft?'))) return;
        await client.deleteDraft(state.session.value, value.id);
        await loadDrafts();
    }
    return {
        areas,
        area,
        items,
        selected,
        context,
        draft,
        drafts,
        hasMore,
        showDrafts,
        currentArea,
        authors,
        start,
        refreshAreas,
        load,
        selectArea,
        open,
        moreReplies,
        edit,
        save,
        send,
        loadDrafts,
        editDraft,
        removeDraft,
        discard
    };
}
