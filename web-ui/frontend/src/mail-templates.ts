import { computed, inject, ref } from 'vue';
import type { InjectionKey } from 'vue';
export type ComposeKind = 'new' | 'reply' | 'quoted' | 'forward';
export interface MailTemplate {
    id: string;
    name: string;
    kind: ComposeKind;
    body: string;
}
export interface TemplateRule {
    kind: ComposeKind;
    aka: string;
    area: string;
    template: string;
}
export interface TemplateContext {
    kind: ComposeKind;
    echo: boolean;
    fromName: string;
    fromAddress: string;
    toName: string;
    toAddress: string;
    subject: string;
    area: string;
    originalName?: string;
    originalAddress?: string;
    originalDate?: string;
    originalBody?: string;
    date?: Date;
}
export const builtInTemplates: MailTemplate[] = [
    { id: 'new', name: 'New message', kind: 'new', body: 'Hello @tname.\n\n@position\n\n@cname' },
    {
        id: 'reply',
        name: 'Short reply',
        kind: 'reply',
        body: 'Hello @oname.\n\n@position\n\n@cname'
    },
    {
        id: 'quoted',
        name: 'Quoted reply',
        kind: 'quoted',
        body: 'Hello @oname.\n\n@quote\n\n@position\n\n@cname'
    },
    {
        id: 'forward',
        name: 'Forward',
        kind: 'forward',
        body: 'Forwarded from @oname (@oaddr), @odate:\n\n@message\n\n@position\n\n@cname'
    }
];
export function mailTemplate(value: unknown): MailTemplate {
    if (!value || typeof value !== 'object') throw new Error('Invalid mail template');
    const row = value as Partial<MailTemplate>;
    if (
        typeof row.id !== 'string' ||
        !/^[a-zA-Z0-9_-]{1,64}$/.test(row.id) ||
        typeof row.name !== 'string' ||
        !row.name.trim() ||
        row.name.length > 80 ||
        !['new', 'reply', 'quoted', 'forward'].includes(row.kind || '') ||
        typeof row.body !== 'string' ||
        row.body.length > 6000 ||
        /[\x00-\x08\x0b\x0c\x0e-\x1f]/.test(row.body)
    )
        throw new Error('Invalid mail template');
    return {
        id: row.id,
        name: row.name,
        kind: row.kind!,
        body: row.body.replaceAll('\r\n', '\n').replaceAll('\r', '\n')
    };
}
export function expandTemplate(template: MailTemplate, context: TemplateContext) {
    const now = context.date || new Date();
    const original = context.originalBody || '';
    const values: Record<string, string> = {
        cname: context.fromName,
        cfname: context.fromName.split(' ')[0]!,
        caddr: context.fromAddress,
        fname: context.fromName,
        faddr: context.fromAddress,
        tname: context.toName,
        tfname: context.toName.split(' ')[0]!,
        taddr: context.toAddress,
        dname: context.toName,
        daddr: context.toAddress,
        oname: context.originalName || context.toName,
        ofname: (context.originalName || context.toName).split(' ')[0]!,
        oaddr: context.originalAddress || context.toAddress,
        odate: context.originalDate || '',
        subject: context.subject,
        cecho: context.area,
        areaname: context.area,
        cdate: now.toLocaleDateString('en-GB'),
        ctime: now.toLocaleTimeString('en-GB', { hour12: false }),
        quote: original
            .split(/\r\n|\r|\n/)
            .map((line) => '> ' + line)
            .join('\n'),
        message: original
    };
    let cursor = -1;
    let result = '';
    const conditions = new Set(['new', 'reply', 'quoted', 'forward', 'echo', 'net']);
    for (const originalLine of mailTemplate(template).body.split('\n')) {
        if (originalLine.startsWith(';')) continue;
        let line = originalLine;
        let keep = true;
        while (/^@(new|reply|quoted|forward|echo|net)/i.test(line)) {
            const match = line.match(/^@(new|reply|quoted|forward|echo|net)/i)!;
            const condition = match[1]!.toLowerCase();
            keep &&=
                condition === 'echo'
                    ? context.echo
                    : condition === 'net'
                      ? !context.echo
                      : condition === context.kind;
            line = line.slice(match[0].length);
        }
        if (!keep) continue;
        const expanded = line.replace(
            /@@|(?<![\w])@([a-z][a-z0-9_]*)/gi,
            (token, key: string, offset: number) => {
                if (token === '@@') return '@';
                const name = key.toLowerCase();
                if (name === 'position') {
                    if (cursor < 0) cursor = result.length + offset;
                    return '';
                }
                if (!Object.hasOwn(values, name) || conditions.has(name))
                    throw new Error('Unsupported template token: ' + token);
                return values[name]!;
            }
        );
        // Cursor offsets must use expanded preceding tokens, not source token lengths.
        if (/@position/i.test(line)) {
            const prefix = line.split(/@position/i)[0]!;
            const before = prefix.replace(
                /@@|(?<![\w])@([a-z][a-z0-9_]*)/gi,
                (token, key: string) => (token === '@@' ? '@' : values[key.toLowerCase()] || '')
            );
            if (cursor >= result.length) cursor = result.length + before.length;
        }
        result += expanded + '\n';
    }
    const body = result.endsWith('\n') ? result.slice(0, -1) : result;
    if (new TextEncoder().encode(body).length > 60000)
        throw new Error('Expanded template exceeds the mail size limit');
    return { body, cursor: cursor < 0 ? body.length : Math.min(cursor, body.length) };
}
type Client = {
    uiDocument(token: string, key: string): Promise<string>;
    saveUiDocument(token: string, key: string, document: string): Promise<void>;
};
export function createMailTemplates(client?: Client) {
    const custom = ref<MailTemplate[]>([]);
    const rules = ref<TemplateRule[]>([]);
    const all = computed(() => [...builtInTemplates, ...custom.value]);
    let token = '';
    let generation = 0;
    function validate(values: MailTemplate[], defaults: TemplateRule[]) {
        if (values.length > 20 || defaults.length > 64)
            throw new Error('Too many mail templates or rules');
        const normalized = values.map(mailTemplate);
        const ids = [...builtInTemplates, ...normalized].map((value) => value.id);
        if (new Set(ids).size !== ids.length) throw new Error('Template IDs must be unique');
        for (const rule of defaults) {
            if (
                !ids.includes(rule.template) ||
                !['new', 'reply', 'quoted', 'forward'].includes(rule.kind) ||
                typeof rule.aka !== 'string' ||
                rule.aka.length > 40 ||
                typeof rule.area !== 'string' ||
                rule.area.length > 255
            )
                throw new Error('Invalid template default');
        }
        return normalized;
    }
    async function load(session: string) {
        token = session;
        const ticket = ++generation;
        custom.value = [];
        rules.value = [];
        if (!client || !session) return;
        const row = JSON.parse(await client.uiDocument(session, 'mail-templates'));
        const values = validate(row.custom || [], row.rules || []);
        if (ticket !== generation) return;
        custom.value = values;
        rules.value = row.rules || [];
    }
    async function save(values: MailTemplate[], defaults: TemplateRule[]) {
        const next = validate(values, defaults);
        const ticket = generation;
        if (client && token)
            await client.saveUiDocument(
                token,
                'mail-templates',
                JSON.stringify({ version: 1, custom: next, rules: defaults })
            );
        if (ticket === generation) {
            custom.value = next;
            rules.value = defaults.map((rule) => ({ ...rule }));
        }
    }
    function choose(kind: ComposeKind, aka: string, area: string) {
        const matches = rules.value.filter(
            (rule) =>
                rule.kind === kind &&
                (!rule.aka || rule.aka === aka) &&
                (!rule.area || rule.area.toLowerCase() === area.toLowerCase())
        );
        matches.sort(
            (a, b) =>
                Number(!!b.aka) + 2 * Number(!!b.area) - (Number(!!a.aka) + 2 * Number(!!a.area))
        );
        return all.value.find((template) => template.id === (matches[0]?.template || kind))!;
    }
    return { custom, rules, all, load, save, choose };
}
export type MailTemplates = ReturnType<typeof createMailTemplates>;
export const mailTemplatesKey: InjectionKey<MailTemplates> = Symbol('mail-templates');
export const useMailTemplates = () => inject(mailTemplatesKey, () => createMailTemplates(), true);
