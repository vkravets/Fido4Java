import { computed, inject, ref } from 'vue';
import type { InjectionKey } from 'vue';
export const mailColors = [
    'background',
    'text',
    'quote1',
    'quote2',
    'quote3',
    'ending',
    'link',
    'selection',
    'caret',
    'search'
] as const;
export type MailColor = (typeof mailColors)[number];
export interface MailTheme {
    id: string;
    name: string;
    colors: Record<MailColor, string>;
}
const palette = (id: string, name: string, values: string[]): MailTheme => ({
    id,
    name,
    colors: Object.fromEntries(mailColors.map((key, i) => [key, values[i]!])) as MailTheme['colors']
});
export const builtInMailThemes = [
    palette('paper', 'Paper', [
        '#fffdf7',
        '#242424',
        '#116577',
        '#6e4090',
        '#82551e',
        '#66615b',
        '#805600',
        '#f4d38c',
        '#242424',
        '#f4d38c'
    ]),
    palette('graphite', 'Graphite', [
        '#1d2023',
        '#ededdf',
        '#7bced4',
        '#c6a4ee',
        '#e4bb7b',
        '#a8aaa4',
        '#f2c66b',
        '#60502f',
        '#f2c66b',
        '#f4d38c'
    ]),
    palette('terminal', 'Terminal', [
        '#101810',
        '#d1e6c6',
        '#8dd19c',
        '#b7c78a',
        '#e4cc9a',
        '#9db49a',
        '#c5df7b',
        '#355a32',
        '#b4ee93',
        '#b4ee93'
    ])
];
export function mailTheme(value: unknown): MailTheme {
    if (!value || typeof value !== 'object') throw new Error('Invalid mail theme');
    const row = value as Partial<MailTheme>;
    if (
        typeof row.id !== 'string' ||
        !/^[a-z0-9_-]{1,48}$/.test(row.id) ||
        typeof row.name !== 'string' ||
        !row.name.trim() ||
        row.name.length > 80
    )
        throw new Error('Invalid mail theme');
    const colors = {} as MailTheme['colors'];
    for (const key of mailColors) {
        const color = row.colors?.[key];
        if (typeof color !== 'string' || !/^#[a-f0-9]{6}$/i.test(color))
            throw new Error('Invalid mail theme color');
        colors[key] = color;
    }
    return { id: row.id, name: row.name, colors };
}
export function themeStyle(theme: MailTheme) {
    return Object.fromEntries(mailColors.map((key) => [`--mail-${key}`, theme.colors[key]]));
}
type Client = {
    uiDocument(token: string, key: string): Promise<string>;
    saveUiDocument(token: string, key: string, document: string): Promise<void>;
};
export function createMailAppearance(client?: Client) {
    const initial =
        typeof document !== 'undefined' && document.documentElement.dataset.theme === 'dark'
            ? 'graphite'
            : 'paper';
    const reader = ref(initial);
    const editor = ref(initial);
    const custom = ref<MailTheme[]>([]);
    const operator = ref<MailTheme[]>([]);
    const themes = computed(() => [...builtInMailThemes, ...operator.value, ...custom.value]);
    const find = (id: string) =>
        themes.value.find((theme) => theme.id === id) || builtInMailThemes[0]!;
    let token = '';
    let generation = 0;
    async function load(session: string) {
        token = session;
        const ticket = ++generation;
        custom.value = [];
        reader.value = editor.value =
            typeof document !== 'undefined' && document.documentElement.dataset.theme === 'dark'
                ? 'graphite'
                : 'paper';
        if (!client || !session) return;
        const row = JSON.parse(await client.uiDocument(session, 'mail-themes'));
        if (ticket !== generation) return;
        custom.value = Array.isArray(row.custom) ? row.custom.slice(0, 20).map(mailTheme) : [];
        if (typeof row.reader === 'string') reader.value = row.reader;
        if (typeof row.editor === 'string') editor.value = row.editor;
    }
    async function files() {
        const response = await fetch('/ui/mail-themes.json');
        if (!response.ok) return;
        const values: unknown = await response.json();
        if (!Array.isArray(values)) return;
        const result: MailTheme[] = [];
        for (const value of values.slice(0, 32)) {
            try {
                const theme = mailTheme(value);
                if (
                    !builtInMailThemes.some((builtin) => builtin.id === theme.id) &&
                    !result.some((other) => other.id === theme.id)
                )
                    result.push(theme);
            } catch {
                /* A malformed operator theme never disables the reader. */
            }
        }
        operator.value = result;
    }
    async function save(nextReader: string, nextEditor: string, nextCustom: MailTheme[]) {
        const ticket = generation;
        const values = nextCustom.map(mailTheme);
        if (values.length > 20 || new Set(values.map((value) => value.id)).size !== values.length)
            throw new Error('Too many or duplicate personal themes');
        const occupied = [...builtInMailThemes, ...operator.value].map((value) => value.id);
        if (values.some((value) => occupied.includes(value.id)))
            throw new Error('Personal theme must have a unique name');
        const ids = [...occupied, ...values.map((value) => value.id)];
        if (!ids.includes(nextReader) || !ids.includes(nextEditor))
            throw new Error('Unknown mail theme');
        const row = { version: 1, reader: nextReader, editor: nextEditor, custom: values };
        if (client && token) await client.saveUiDocument(token, 'mail-themes', JSON.stringify(row));
        if (ticket !== generation) return;
        reader.value = nextReader;
        editor.value = nextEditor;
        custom.value = values;
    }
    return { reader, editor, custom, operator, themes, find, load, files, save };
}
export type MailAppearance = ReturnType<typeof createMailAppearance>;
export const mailAppearanceKey: InjectionKey<MailAppearance> = Symbol('mail-appearance');
export const useMailAppearance = () =>
    inject(mailAppearanceKey, () => createMailAppearance(), true);
