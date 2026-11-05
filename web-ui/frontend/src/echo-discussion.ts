import { computed, ref, watch } from 'vue';
import type { Ref } from 'vue';
import { mailId } from './netmail.ts';
import type { EchoContext, MailItem } from '@rpc/bindings.mjs';

export function createEchoDiscussion(
    session: Ref<string>,
    area: Ref<string>,
    selected: Ref<MailItem | null>,
    context: Ref<EchoContext | null>,
    view: Ref<'current' | 'whole'>,
    fetch: (
        token: string,
        area: string,
        id: MailItem['id'],
        cursor: MailItem['id'],
        limit: number
    ) => Promise<EchoContext>
) {
    const nodes = ref(new Map<string, MailItem>());
    const links = ref(new Map<string, EchoContext>());
    const expanded = ref(new Set<string>());
    const pending = ref(new Set<string>());
    const failures = ref(new Set<string>());
    let generation = 0;
    function reset() {
        generation++;
        nodes.value.clear();
        links.value.clear();
        expanded.value.clear();
        pending.value.clear();
        failures.value.clear();
    }
    watch([session, area], reset, { flush: 'sync' });
    function remember(item: MailItem, value: EchoContext) {
        nodes.value.set(mailId(item.id), item);
        links.value.set(mailId(item.id), value);
        if (value.parent) nodes.value.set(mailId(value.parent.id), value.parent);
        value.replies.items.forEach((reply) => nodes.value.set(mailId(reply.id), reply));
    }
    async function load(item: MailItem, more = false) {
        const id = mailId(item.id);
        if (pending.value.has(id)) return false;
        const ticket = generation;
        const previous = links.value.get(id);
        pending.value.add(id);
        failures.value.delete(id);
        try {
            const result = await fetch(
                session.value,
                area.value,
                item.id,
                more ? previous?.replies.nextId || 0 : 0,
                25
            );
            if (ticket !== generation) return false;
            if (more && previous) {
                const replies = new Map(
                    previous.replies.items.map((reply) => [mailId(reply.id), reply])
                );
                result.replies.items.forEach((reply) => replies.set(mailId(reply.id), reply));
                result.replies.items = [...replies.values()];
            }
            remember(item, result);
            return true;
        } catch {
            if (ticket === generation) failures.value.add(id);
            return false;
        } finally {
            if (ticket === generation) pending.value.delete(id);
        }
    }
    async function ancestors() {
        const ticket = generation;
        let item = selected.value;
        const seen = new Set<string>();
        while (item && view.value === 'whole' && ticket === generation) {
            const id = mailId(item.id);
            if (seen.has(id)) break;
            seen.add(id);
            expanded.value.add(id);
            if (!links.value.has(id) && !(await load(item))) break;
            item = links.value.get(id)?.parent || null;
        }
    }
    watch([selected, context], () => {
        if (!selected.value || !context.value) {
            reset();
            return;
        }
        if (!nodes.value.has(mailId(selected.value.id))) reset();
        const id = mailId(selected.value.id);
        if (!links.value.has(id)) remember(selected.value, context.value);
        else nodes.value.set(id, selected.value);
        expanded.value.add(id);
        void ancestors();
    });
    watch(view, () => {
        void ancestors();
    });
    const rows = computed(() => {
        const result: { item: MailItem; depth: number; parent: string }[] = [];
        let root = selected.value;
        const seen = new Set<string>();
        if (view.value === 'whole') {
            while (root && !seen.has(mailId(root.id))) {
                seen.add(mailId(root.id));
                const parent = links.value.get(mailId(root.id))?.parent;
                if (!parent || seen.has(mailId(parent.id))) break;
                root = parent;
            }
        }
        seen.clear();
        const queue = root ? [{ item: root, depth: 0, parent: '' }] : [];
        while (queue.length) {
            const row = queue.shift()!;
            const id = mailId(row.item.id);
            if (seen.has(id)) continue;
            seen.add(id);
            result.push(row);
            if (expanded.value.has(id)) {
                const children = new Map(
                    (links.value.get(id)?.replies.items || []).map((item) => [
                        mailId(item.id),
                        item
                    ])
                );
                for (const [child, value] of links.value) {
                    if (value.parent && mailId(value.parent.id) === id && nodes.value.has(child))
                        children.set(child, nodes.value.get(child)!);
                }
                queue.unshift(
                    ...[...children.values()].map((item) => ({
                        item,
                        depth: row.depth + 1,
                        parent: id
                    }))
                );
            }
        }
        return result;
    });
    async function toggle(item: MailItem) {
        const id = mailId(item.id);
        if (expanded.value.has(id)) expanded.value.delete(id);
        else {
            expanded.value.add(id);
            if (!links.value.has(id)) await load(item);
        }
    }
    async function retry(item: MailItem) {
        if (await load(item, links.value.has(mailId(item.id)))) await ancestors();
    }
    return { rows, links, expanded, pending, failures, load, toggle, retry };
}
