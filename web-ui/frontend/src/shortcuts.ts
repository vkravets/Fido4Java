import { inject, ref } from 'vue';
import type { InjectionKey } from 'vue';
import { defaultBindings, shortcutActions } from './shortcut-actions.ts';
import type { ShortcutAction, ShortcutProfile, ShortcutScope } from './shortcut-actions.ts';

export interface ShortcutPreferences {
    version: 1;
    enabled: boolean;
    profile: ShortcutProfile;
    overrides: Partial<Record<ShortcutAction, string[]>>;
}
const namedKeys = new Set([
    'Enter',
    'Escape',
    'Space',
    'Tab',
    'Home',
    'End',
    'PageUp',
    'PageDown',
    'Insert',
    'Delete',
    'Backspace',
    'ArrowLeft',
    'ArrowRight',
    'ArrowUp',
    'ArrowDown',
    '/',
    '[',
    ']',
    '.',
    ',',
    '-',
    '='
]);
export function normalizeBinding(value: string): string | null {
    const parts = value.trim().split('+');
    const key = parts.pop() || '';
    if (!/^[A-Z0-9]$|^F(?:[1-9]|1[0-2])$/.test(key) && !namedKeys.has(key)) return null;
    if (
        parts.some((part) => !['Ctrl', 'Alt', 'Shift', 'Meta'].includes(part)) ||
        new Set(parts).size !== parts.length
    )
        return null;
    return [...['Ctrl', 'Alt', 'Shift', 'Meta'].filter((part) => parts.includes(part)), key].join(
        '+'
    );
}
export function eventBinding(event: KeyboardEvent): string | null {
    const punctuation: Record<string, string> = {
        Slash: '/',
        BracketLeft: '[',
        BracketRight: ']',
        Period: '.',
        Comma: ',',
        Minus: '-',
        Equal: '='
    };
    const key = /^Key[A-Z]$/.test(event.code)
        ? event.code.slice(3)
        : /^Digit[0-9]$/.test(event.code)
          ? event.code.slice(5)
          : punctuation[event.code] ||
            event.code ||
            (event.key.length === 1 ? event.key.toUpperCase() : event.key);
    return normalizeBinding(
        [
            event.ctrlKey && 'Ctrl',
            event.altKey && 'Alt',
            event.shiftKey && 'Shift',
            event.metaKey && 'Meta',
            key
        ]
            .filter(Boolean)
            .join('+')
    );
}
export function reservedBinding(key: string): boolean {
    return (
        /^(?:Ctrl|Meta)\+(?:Shift\+)?(?:W|T|N|Q|Tab|F4)$/.test(key) ||
        /^(?:Ctrl|Meta)\+(?:L|P|F|R)$/.test(key) ||
        ['Alt+F4', 'Alt+Tab', 'Meta+Space', 'F1', 'F5', 'F11', 'F12'].includes(key)
    );
}
export function shortcutPreferences(input: unknown): ShortcutPreferences {
    const row = input && typeof input === 'object' ? (input as Partial<ShortcutPreferences>) : {};
    const overrides: ShortcutPreferences['overrides'] = {};
    for (const action of shortcutActions) {
        const values = row.overrides?.[action.id];
        if (Array.isArray(values))
            overrides[action.id] = [
                ...new Set(
                    values
                        .filter((value): value is string => typeof value === 'string')
                        .map(normalizeBinding)
                        .filter((value): value is string => !!value && !reservedBinding(value))
                )
            ].slice(0, 4);
    }
    return {
        version: 1,
        enabled: row.enabled !== false,
        profile: row.profile === 'golded' ? 'golded' : 'jftn',
        overrides
    };
}
export const bindingsFor = (document: ShortcutPreferences, action: ShortcutAction) =>
    document.overrides[action] ?? defaultBindings(action, document.profile);
export function overlappingScopes(first: ShortcutScope, second: ShortcutScope): boolean {
    return (
        first === second ||
        first === 'global' ||
        second === 'global' ||
        (first !== 'mail' && second !== 'mail')
    );
}
export function shortcutConflict(document: ShortcutPreferences): string | null {
    for (const [index, first] of shortcutActions.entries()) {
        for (const key of bindingsFor(document, first.id)) {
            if (!normalizeBinding(key) || reservedBinding(key)) return key;
            for (const second of shortcutActions.slice(index + 1)) {
                if (
                    overlappingScopes(first.scope, second.scope) &&
                    bindingsFor(document, second.id).includes(key)
                )
                    return key;
            }
        }
    }
    return null;
}
type Client = {
    uiDocument(token: string, key: string): Promise<string>;
    saveUiDocument(token: string, key: string, document: string): Promise<void>;
};
export function createShortcuts(client?: Client) {
    const value = ref(shortcutPreferences({}));
    const helpOpen = ref(false);
    let session = '';
    let generation = 0;
    async function load(token: string) {
        helpOpen.value = false;
        session = token;
        const ticket = ++generation;
        value.value = shortcutPreferences({});
        if (!client || !token) return;
        const row = shortcutPreferences(JSON.parse(await client.uiDocument(token, 'keyboard')));
        if (ticket === generation && !shortcutConflict(row)) value.value = row;
    }
    let pending = false;
    async function save(document: ShortcutPreferences) {
        if (pending) throw new Error('Shortcut save already in progress');
        if (shortcutConflict(document)) throw new Error('Conflicting or reserved shortcut');
        const ticket = generation;
        const next = shortcutPreferences(document);
        pending = true;
        try {
            if (client && session)
                await client.saveUiDocument(session, 'keyboard', JSON.stringify(next));
            if (ticket === generation) value.value = next;
        } finally {
            pending = false;
        }
    }
    return { value, helpOpen, load, save };
}
export type Shortcuts = ReturnType<typeof createShortcuts>;
export const shortcutsKey: InjectionKey<Shortcuts> = Symbol('shortcuts');
export const useShortcuts = () => inject(shortcutsKey, () => createShortcuts(), true);
