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
    const result = ref<{ accepted: boolean; detail: string } | null>(null);
    async function refresh(after = '') {
        page.value = await client.quarantine(session(), after, 25);
        selected.value = null;
    }
    function select(item: QuarantineItem, operation: 'retry' | 'archive') {
        selected.value = item;
        action.value = operation;
        peer.value = item.peer;
        result.value = null;
    }
    async function confirm() {
        const item = selected.value;
        if (!item) return;
        if (!item.checksum) throw new Error(t('This packet requires manual inspection.'));
        result.value = null;
        if (action.value === 'retry') {
            const expected = item.peer || peer.value;
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
    return { page, selected, action, peer, result, refresh, select, confirm };
}
