import { ref } from 'vue';
import { t, translateMessage } from './i18n.ts';
import type { QuarantineItem, QuarantinePage, UiServiceClient } from '@rpc/bindings.mjs';

export function quarantineReason(reason: string): string {
    for (const prefix of ['Unknown echo area: ', 'WRITE access denied for echo area: '] as const) {
        if (reason.startsWith(prefix)) return t(prefix) + reason.slice(prefix.length);
    }
    return translateMessage(reason);
}

export function createQuarantine(
    client: Pick<UiServiceClient, 'quarantine' | 'retryQuarantine' | 'archiveQuarantine'>,
    session: () => string
) {
    const page = ref<QuarantinePage | null>(null);
    const selected = ref<QuarantineItem | null>(null);
    const action = ref<'retry' | 'archive'>('retry');
    const peer = ref('');
    const checked = ref<string[]>([]);
    const peers = ref<Record<string, string>>({});
    const batch = ref<QuarantineItem[]>([]);
    const outcomes = ref<{ id: string; name: string; accepted: boolean; detail: string }[]>([]);
    const result = ref<{ accepted: boolean; detail: string } | null>(null);
    async function refresh(after = '') {
        page.value = await client.quarantine(session(), after, 25);
        selected.value = null;
        checked.value = [];
        batch.value = [];
        peers.value = {};
    }
    function select(item: QuarantineItem, operation: 'retry' | 'archive') {
        selected.value = item;
        batch.value = [];
        action.value = operation;
        peer.value = item.peer;
        result.value = null;
    }
    function selectBatch() {
        batch.value = page.value?.items.filter((item) => checked.value.includes(item.id)) || [];
        selected.value = null;
        action.value = 'retry';
        result.value = null;
    }
    function expectedPeer(item: QuarantineItem) {
        return item.peer || peers.value[item.id] || (batch.value.length ? '' : peer.value);
    }
    function validate(item: QuarantineItem) {
        if (!item.checksum) throw new Error(t('This packet requires manual inspection.'));
        if (!page.value?.peers.includes(expectedPeer(item))) {
            throw new Error(t('Select an expected source link.'));
        }
    }
    async function confirmBatch() {
        const items = [...batch.value];
        // Validate the entire selection before releasing any packet.
        items.forEach(validate);
        outcomes.value = [];
        for (const item of items) {
            await retryItem(item);
        }
        const accepted = outcomes.value.filter((outcome) => outcome.accepted).length;
        result.value = {
            accepted: accepted === items.length,
            detail: t('Batch complete: {accepted} accepted, {failed} failed.', {
                accepted,
                failed: items.length - accepted
            })
        };
        await refresh();
    }
    async function retryItem(item: QuarantineItem) {
        try {
            const outcome = await client.retryQuarantine(
                session(),
                item.id,
                item.checksum,
                expectedPeer(item)
            );
            outcomes.value.push({ id: item.id, name: item.name, ...outcome });
        } catch (failure) {
            outcomes.value.push({
                id: item.id,
                name: item.name,
                accepted: false,
                detail: (failure as Error)?.message || t('The request failed. Please try again.')
            });
        }
    }
    function cancel() {
        selected.value = null;
        batch.value = [];
    }
    async function confirm() {
        if (batch.value.length) return confirmBatch();
        const item = selected.value;
        if (!item) return;
        if (!item.checksum) throw new Error(t('This packet requires manual inspection.'));
        result.value = null;
        if (action.value === 'retry') {
            const expected = expectedPeer(item);
            if (!expected || !page.value?.peers.includes(expected)) {
                throw new Error(t('Select an expected source link.'));
            }
            result.value = await client.retryQuarantine(
                session(),
                item.id,
                item.checksum,
                expected
            );
        } else {
            await client.archiveQuarantine(session(), item.id, item.checksum);
            result.value = {
                accepted: true,
                detail: t('Packet archived. The original was retained.')
            };
        }
        selected.value = null;
        await refresh();
    }
    return {
        page,
        selected,
        action,
        peer,
        result,
        checked,
        peers,
        batch,
        outcomes,
        refresh,
        select,
        selectBatch,
        cancel,
        confirm
    };
}
