import { onBeforeUnmount, ref, watch } from 'vue';
import type { Ref, WatchSource } from 'vue';
import type { AppState } from './state.ts';
import type { MailDraft, MailItem } from '@rpc/bindings.mjs';

/** Reader requests leave the surrounding workspace interactive. */
export function useMailReading(
    state: Pick<AppState, 'busy' | 'run'>,
    reader: {
        open(item: MailItem): Promise<void>;
        cancelOpen(): void;
        draft: Ref<MailDraft | null>;
    },
    location: WatchSource<unknown>
) {
    const pending = ref(false);
    let generation = 0;
    function cancel() {
        generation++;
        pending.value = false;
        reader.cancelOpen();
    }
    watch(location, cancel, { flush: 'sync' });
    watch(
        reader.draft,
        (draft) => {
            if (draft) cancel();
        },
        { flush: 'sync' }
    );
    onBeforeUnmount(cancel);
    async function run(operation: () => Promise<void>) {
        if (pending.value || state.busy.value) return;
        const ticket = ++generation;
        pending.value = true;
        try {
            await state.run(
                async () => {
                    try {
                        await operation();
                    } catch (failure) {
                        if (ticket === generation) throw failure;
                    }
                },
                { background: true }
            );
        } finally {
            if (ticket === generation) pending.value = false;
        }
    }
    return { pending, cancel, run, open: (item: MailItem) => run(() => reader.open(item)) };
}
