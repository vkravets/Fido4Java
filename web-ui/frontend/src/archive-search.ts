import { computed, onScopeDispose, ref, watch } from 'vue';
import type { Ref } from 'vue';
import type { MailItem, MailSearchPage, Mailbox } from '@rpc/bindings.mjs';
import { mailId } from './netmail.ts';

export type SearchClient = Partial<Pick<typeof import('./api.ts').api.ui, 'searchMessages'>>;
export interface SearchScope {
    mailbox: Mailbox;
    area: string;
}

/** One search interaction, with an optional archive provider and a separate mailbox cache. */
export function createArchiveSearch(
    session: Ref<string>,
    scope: Readonly<Ref<SearchScope | null>>,
    query: Ref<string>,
    source: Ref<MailItem[]>,
    client: SearchClient,
    authenticationFailed: (failure: unknown) => void,
    delay = 300
) {
    const results = ref<MailItem[]>([]);
    const remote = ref(false);
    const pending = ref(false);
    const queued = ref(false);
    const hasMore = ref(false);
    const failure = ref(false);
    const scanned = ref(0);
    const unavailable = ref('');
    let available = false;
    const active = computed(() => !!scope.value && !!query.value.trim());
    const eligible = computed(() => {
        const text = query.value.trim();
        return (
            active.value &&
            text.length >= 2 &&
            text.length <= 160 &&
            !/[\u0000-\u001f\u007f-\u009f]/u.test(text) &&
            !!client.searchMessages &&
            unavailable.value !== session.value
        );
    });
    let cursor: MailItem['id'] = 0;
    let generation = 0;
    let timer: ReturnType<typeof setTimeout> | undefined;
    let disposed = false;

    function reset() {
        generation++;
        clearTimeout(timer);
        pending.value = false;
        queued.value = false;
        failure.value = false;
        results.value = [];
        cursor = 0;
        scanned.value = 0;
        hasMore.value = false;
        // Keep immediate local filtering while first discovering the optional provider.
        remote.value = eligible.value && available;
    }

    async function loadMore() {
        if (disposed || pending.value || !eligible.value) return;
        clearTimeout(timer);
        queued.value = false;
        const ticket = generation;
        const token = session.value;
        const location = scope.value!;
        const text = query.value.trim();
        pending.value = true;
        failure.value = false;
        try {
            const page: MailSearchPage = await client.searchMessages!(
                token,
                location.mailbox,
                location.area,
                text,
                cursor,
                50
            );
            if (disposed || ticket !== generation || token !== session.value) return;
            available = true;
            remote.value = true;
            if (page.hasMore && mailId(page.nextId) === mailId(cursor)) {
                throw new Error('Search cursor did not advance');
            }
            const rows = new Map(results.value.map((item) => [mailId(item.id), item]));
            const loaded = new Map(source.value.map((item) => [mailId(item.id), item]));
            page.items.forEach((item) => {
                rows.set(mailId(item.id), item);
                const cached = loaded.get(mailId(item.id));
                if (cached) cached.read = item.read;
            });
            results.value = [...rows.values()];
            cursor = page.nextId;
            scanned.value += page.scanned;
            hasMore.value = page.hasMore;
        } catch (error) {
            if (disposed || ticket !== generation || token !== session.value) return;
            const code = (error as { code?: string } | null)?.code;
            if (code === 'FEATURE_DISABLED') {
                unavailable.value = token;
                remote.value = false;
                results.value = [];
                hasMore.value = false;
            } else {
                remote.value = true;
                failure.value = true;
                hasMore.value = true;
                if (code === 'UNAUTHORIZED' || code === 'SESSION_EXPIRED')
                    authenticationFailed(error);
            }
        } finally {
            if (ticket === generation && !disposed) pending.value = false;
        }
    }

    async function refresh() {
        reset();
        await loadMore();
    }

    watch(
        [session, scope, query],
        () => {
            reset();
            if (eligible.value) {
                queued.value = true;
                timer = setTimeout(() => void loadMore(), delay);
            }
        },
        { flush: 'sync' }
    );
    onScopeDispose(() => {
        disposed = true;
        generation++;
        clearTimeout(timer);
    }, true);

    const items = computed(() => {
        if (!remote.value) return source.value;
        const loaded = new Map(source.value.map((item) => [mailId(item.id), item]));
        return results.value.map((item) => {
            const cached = loaded.get(mailId(item.id));
            return cached ? { ...item, read: cached.read } : item;
        });
    });
    const searching = computed(() => pending.value || queued.value);
    return {
        items,
        active,
        remote,
        pending: searching,
        hasMore,
        failure,
        scanned,
        loadMore,
        refresh
    };
}
