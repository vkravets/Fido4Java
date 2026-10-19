import { mailId } from './netmail.ts';
import type { MailItem } from '@rpc/bindings.mjs';

export interface EchoThreadRow {
    item: MailItem;
    depth: number;
    children: boolean;
    parent: string;
}

/** Only exact MSGID/REPLY relationships; absent/duplicate IDs and cycles cannot hide mail. */
export function echoThreads(
    items: MailItem[],
    collapsed: ReadonlySet<string> = new Set()
): EchoThreadRow[] {
    const unique = new Map(items.map((item) => [mailId(item.id), item]));
    const byMessageId = new Map<string, string>();
    for (const item of unique.values()) {
        if (item.messageId && !byMessageId.has(item.messageId))
            byMessageId.set(item.messageId, mailId(item.id));
    }
    const parents = new Map<string, string>();
    for (const item of unique.values()) {
        const id = mailId(item.id);
        const parent = item.replyTo ? byMessageId.get(item.replyTo) : undefined;
        if (!parent || parent === id) continue;
        let cursor: string | undefined = parent;
        const visited = new Set([id]);
        while (cursor && !visited.has(cursor)) {
            visited.add(cursor);
            cursor = parents.get(cursor);
        }
        if (!cursor) parents.set(id, parent);
    }
    const children = new Map<string, MailItem[]>();
    for (const item of unique.values()) {
        const parent = parents.get(mailId(item.id)) || '';
        const siblings = children.get(parent) || [];
        siblings.push(item);
        children.set(parent, siblings);
    }
    const result: EchoThreadRow[] = [];
    const stack = (children.get('') || [])
        .slice()
        .reverse()
        .map((item) => ({ item, depth: 0 }));
    while (stack.length) {
        const current = stack.pop()!;
        const id = mailId(current.item.id);
        const replies = children.get(id) || [];
        result.push({ ...current, children: replies.length > 0, parent: parents.get(id) || '' });
        if (!collapsed.has(id)) {
            for (const item of replies.slice().reverse())
                stack.push({ item, depth: current.depth + 1 });
        }
    }
    return result;
}
