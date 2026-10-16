import { ref } from 'vue';
import { ukrainian } from './locales/uk.ts';

export type Locale = 'en' | 'uk';
export type MessageKey = keyof typeof ukrainian;
export const locale = ref<Locale>('en');
const storageKey = 'jftn-language';
const reverseMessages = new Map(
    Object.entries(ukrainian).map(([key, value]) => [value, key as MessageKey])
);
const templates = Object.entries(ukrainian).flatMap(([key, translated]) => {
    if (!key.includes('{')) return [];
    return [key, translated].map((text) => {
        const names: string[] = [];
        const parts = text.split(/(\{\w+\})/g).map((part) => {
            if (part.startsWith('{') && part.endsWith('}')) {
                names.push(part.slice(1, -1));
                return '([\\s\\S]*?)';
            }
            return part.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
        });
        return { key: key as MessageKey, names, pattern: new RegExp(`^${parts.join('')}$`) };
    });
});

export function t(key: MessageKey, values: Record<string, string | number> = {}): string {
    const message = locale.value === 'uk' ? ukrainian[key] : key;
    return message.replace(/\{(\w+)\}/g, (placeholder, name: string) =>
        values[name] === undefined ? placeholder : String(values[name])
    );
}

export function setLocale(value: string): void {
    locale.value = value === 'uk' ? 'uk' : 'en';
    if (typeof document !== 'undefined') {
        document.documentElement.lang = locale.value;
        document.title = t('JFtn · Mail and node');
    }
    try {
        localStorage.setItem(storageKey, locale.value);
    } catch {
        // Language switching remains available when browser storage is blocked.
    }
}

export function initializeLocale(): void {
    let saved = '';
    try {
        saved = localStorage.getItem(storageKey) || '';
    } catch {
        // English is the default even when browser storage is unavailable.
    }
    setLocale(saved);
}

/** Re-translates application notifications while retaining external diagnostic text. */
export function translateMessage(message: string): string {
    if (Object.hasOwn(ukrainian, message)) return t(message as MessageKey);
    const key = reverseMessages.get(message as (typeof ukrainian)[MessageKey]);
    if (key) return t(key);
    for (const template of templates) {
        const match = template.pattern.exec(message);
        if (match) {
            const values = Object.fromEntries(
                template.names.map((name, index) => [name, match[index + 1]!])
            );
            return t(template.key, values);
        }
    }
    return message;
}
