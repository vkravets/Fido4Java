import { locale, t } from './i18n.ts';
import { ref } from 'vue';
import type { LinkInfo, PollInfo, QueueItem, UiServiceClient } from '@rpc/bindings.mjs';
import type { InjectionKey } from 'vue';

export type PollClient = Pick<
    UiServiceClient,
    | 'poll'
    | 'cancelPoll'
    | 'pollQueue'
    | 'pollDetails'
    | 'pollHistory'
    | 'pollLog'
    | 'links'
    | 'outbound'
>;
export const pollClientKey: InjectionKey<PollClient> = Symbol('poll-client');

export function activePoll(state: string): boolean {
    return ['QUEUED', 'CONNECTING', 'AUTHENTICATING', 'TRANSFERRING'].includes(state);
}

export function pollState(state: string): string {
    const names: Record<string, string> = {
        QUEUED: t('Queued'),
        CONNECTING: t('Connecting'),
        AUTHENTICATING: t('Authenticating'),
        TRANSFERRING: t('Transferring'),
        SUCCEEDED: t('Succeeded'),
        BUSY: t('Node busy'),
        TIMED_OUT: t('Timed out'),
        FAILED: t('Failed'),
        CANCELLED: t('Cancelled'),
        INTERRUPTED: t('Interrupted')
    };
    return names[state] || state;
}

export function pollTime(value: unknown): string {
    const stamp = Number(String(value || 0));
    return stamp ? new Date(stamp).toLocaleString(locale.value === 'uk' ? 'uk-UA' : 'en-GB') : '—';
}

export function createPolling(client: PollClient, session: () => string) {
    const queue = ref<PollInfo[]>([]);
    const history = ref<PollInfo[]>([]);
    const links = ref<LinkInfo[]>([]);
    const selected = ref<PollInfo | null>(null);
    const files = ref<QueueItem[]>([]);
    const lines = ref<string[]>([]);
    const hasMoreLogs = ref(false);
    const clipped = ref(false);
    const hasOlder = ref(false);
    const before = ref('');
    const loaded = ref(false);
    const refreshing = ref(false);
    let nextCursor = '';
    let offset = 0;
    let generation = 0;
    let disposed = false;
    let pending: Promise<void> | null = null;

    async function readSelected() {
        const id = selected.value?.id;
        if (!id) return;
        const version = generation;
        const cursor = offset;
        const [info, log, outbound] = await Promise.all([
            client.pollDetails(session(), id),
            client.pollLog(session(), id, cursor, 100),
            client.outbound(session(), selected.value!.address, 50)
        ]);
        if (disposed || version !== generation || selected.value?.id !== id) return;
        selected.value = info;
        files.value = outbound;
        if (offset !== cursor) return;
        offset = Number(log.nextOffset.toString());
        hasMoreLogs.value = log.hasMore;
        if (!log.lines.length) return;
        const combined = [...lines.value, ...log.lines];
        clipped.value ||= combined.length > 1000;
        lines.value = combined.slice(-1000);
    }

    async function load() {
        const cursor = before.value;
        const [active, page, peers] = await Promise.all([
            client.pollQueue(session()),
            client.pollHistory(session(), cursor, 50),
            client.links(session())
        ]);
        if (disposed || cursor !== before.value) return;
        queue.value = active;
        history.value = page.items;
        hasOlder.value = page.hasMore;
        nextCursor = page.nextCursor;
        links.value = peers;
        loaded.value = true;
        await readSelected();
    }

    function refresh(): Promise<void> {
        if (disposed) return Promise.resolve();
        if (pending) return pending;
        refreshing.value = true;
        pending = load().finally(() => {
            pending = null;
            refreshing.value = false;
        });
        return pending;
    }

    async function select(info: PollInfo) {
        generation++;
        selected.value = info;
        lines.value = [];
        files.value = [];
        offset = 0;
        clipped.value = false;
        hasMoreLogs.value = false;
        await readSelected();
    }

    async function start(address: string) {
        if (pending) await pending;
        const info = await client.poll(session(), address);
        if (disposed) return;
        before.value = '';
        await select(info);
        await refresh();
    }

    async function cancel(id: string) {
        if (pending) await pending;
        await client.cancelPoll(session(), id);
        await refresh();
    }

    async function older() {
        if (pending) await pending;
        if (!hasOlder.value) return;
        before.value = nextCursor;
        await refresh();
    }

    async function newest() {
        if (pending) await pending;
        before.value = '';
        await refresh();
    }

    function dispose() {
        disposed = true;
        generation++;
    }

    return {
        queue,
        history,
        links,
        selected,
        files,
        lines,
        hasMoreLogs,
        clipped,
        hasOlder,
        before,
        loaded,
        refreshing,
        refresh,
        select,
        start,
        cancel,
        older,
        newest,
        readSelected,
        dispose
    };
}

export interface RefreshTimer {
    schedule(task: () => void): unknown;
    clear(handle: unknown): void;
}

/** Schedules the next refresh only after the preceding request has settled. */
export function startPollRefresh(
    refresh: () => Promise<unknown>,
    onError: (error: unknown) => void,
    timer: RefreshTimer = {
        schedule: (task) => setTimeout(task, 2000),
        clear: (handle) => clearTimeout(handle as ReturnType<typeof setTimeout>)
    }
) {
    let stopped = false;
    let handle: unknown;
    async function tick() {
        if (stopped) return;
        try {
            await refresh();
        } catch (error) {
            if (!stopped) onError(error);
        } finally {
            if (!stopped)
                handle = timer.schedule(() => {
                    void tick();
                });
        }
    }
    void tick();
    return () => {
        stopped = true;
        if (handle !== undefined) timer.clear(handle);
    };
}
