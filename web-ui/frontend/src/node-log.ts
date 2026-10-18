import { ref } from 'vue';
import type { UiServiceClient } from '@rpc/bindings.mjs';

export function createNodeLog(client: Pick<UiServiceClient, 'logs'>, session: () => string) {
    const lines = ref<string[]>([]);
    const busy = ref(false);
    const clipped = ref(false);
    const rotated = ref(false);
    const more = ref(false);
    let offset = -1;
    let generation = 0;
    let disposed = false;
    let pending: Promise<void> | null = null;
    function read(): Promise<void> {
        if (disposed) return Promise.resolve();
        if (pending) return pending;
        const version = generation;
        const cursor = offset;
        busy.value = true;
        pending = client
            .logs(session(), cursor, 200)
            .then((page) => {
                if (disposed || version !== generation) return;
                if (page.rotated) {
                    lines.value = [];
                    clipped.value = false;
                    rotated.value = true;
                }
                const combined = [...lines.value, ...page.lines];
                clipped.value ||= combined.length > 1000;
                lines.value = combined.slice(-1000);
                offset = Number(page.nextOffset.toString());
                more.value = page.lines.length > 0 && cursor !== offset;
            })
            .finally(() => {
                pending = null;
                busy.value = false;
            });
        return pending;
    }
    async function latest() {
        if (pending) await pending;
        if (disposed) return;
        generation++;
        offset = -1;
        lines.value = [];
        clipped.value = false;
        rotated.value = false;
        more.value = false;
        await read();
    }
    function dispose() {
        disposed = true;
        generation++;
    }
    return { lines, busy, clipped, rotated, more, read, latest, dispose };
}
