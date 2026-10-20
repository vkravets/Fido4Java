import { inject, ref } from 'vue';
import type { InjectionKey, Ref } from 'vue';
import type { MailItem } from '@rpc/bindings.mjs';
export const mailSearchKey: InjectionKey<Ref<string>> = Symbol('mail-search');
export const useMailSearch = () => inject(mailSearchKey, () => ref(''), true);
export function matchesMail(item: MailItem, query: string) {
    const value = query.trim().toLocaleLowerCase();
    return (
        !value ||
        [
            item.subject,
            item.fromName,
            item.toName,
            item.fromAddress,
            item.toAddress,
            item.excerpt || item.body || ''
        ].some((text) => text.toLocaleLowerCase().includes(value))
    );
}
export function highlightText(text: string, query: string) {
    if (!query.trim()) return [{ text, match: false }];
    const pattern = new RegExp(query.trim().replace(/[.*+?^${}()|[\]\\]/g, '\\$&'), 'giu');
    const result: { text: string; match: boolean }[] = [];
    let offset = 0;
    for (const match of text.matchAll(pattern)) {
        if (match.index! > offset)
            result.push({ text: text.slice(offset, match.index), match: false });
        result.push({ text: match[0], match: true });
        offset = match.index! + match[0].length;
    }
    if (offset < text.length) result.push({ text: text.slice(offset), match: false });
    return result;
}
