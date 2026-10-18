import { ref } from 'vue';
import type { InjectionKey } from 'vue';
import type { DirectoryNode, DirectoryPage } from '@rpc/bindings.mjs';

export type DirectorySearch = (
    query: string,
    offset: number,
    limit: number,
    includePoints: boolean
) => Promise<DirectoryPage>;
export const directorySearchKey: InjectionKey<DirectorySearch> = Symbol('directory-search');

export function addressToken(value: string, caret: number, multiple: boolean) {
    if (!multiple) return { start: 0, end: value.length, query: value.trim() };
    const before = value.slice(0, caret);
    const start = before.search(/\S*$/);
    const next = value.slice(caret).search(/\s/);
    const end = next < 0 ? value.length : caret + next;
    return { start, end, query: value.slice(start, end) };
}

export function createDirectorySearch(
    search: DirectorySearch,
    limit = 25,
    includePoints = () => true
) {
    const items = ref<DirectoryNode[]>([]);
    const busy = ref(false);
    const hasMore = ref(false);
    const offset = ref(0);
    const nextOffset = ref(0);
    let generation = 0;
    let disposed = false;
    async function load(query: string, start = 0) {
        if (disposed) return;
        const version = ++generation;
        busy.value = true;
        items.value = [];
        hasMore.value = false;
        try {
            const page = await search(query, start, limit, includePoints());
            if (disposed || version !== generation) return;
            items.value = page.items;
            hasMore.value = page.hasMore;
            offset.value = start;
            nextOffset.value = page.nextOffset;
        } catch (failure) {
            if (!disposed && version === generation) throw failure;
        } finally {
            if (version === generation) busy.value = false;
        }
    }
    function clear() {
        generation++;
        items.value = [];
        busy.value = false;
        hasMore.value = false;
    }
    function dispose() {
        disposed = true;
        clear();
    }
    return { items, busy, hasMore, offset, nextOffset, load, clear, dispose };
}
