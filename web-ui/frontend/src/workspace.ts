import { inject, ref } from 'vue';
import type { ComputedRef, InjectionKey, Ref } from 'vue';

export interface ComposerListControl {
    available: ComputedRef<boolean>;
    hidden: Ref<boolean>;
    toggle(): void;
}
export const composerListKey: InjectionKey<ComposerListControl> = Symbol('composer-list');

export type PreviewPosition = 'right' | 'below' | 'separate';
export type MailKind = 'netmail' | 'echo';
export const startupPages = [
    { id: 'netmail', label: 'Netmail' },
    { id: 'echo', label: 'Echomail' },
    { id: 'drafts', label: 'Drafts' },
    { id: 'directory', label: 'Node directory' },
    { id: 'settings', label: 'Profile' }
] as const;
export type StartupPage = (typeof startupPages)[number]['id'];
export interface MailLayout {
    preview: PreviewPosition;
    ratio: number;
    density: 'compact' | 'comfortable';
    mode: 'flat' | 'tree';
}
export interface WorkspacePreferences {
    version: 1;
    collapsed: boolean;
    startupPage: StartupPage;
    netmail: MailLayout;
    echo: MailLayout;
}
function layout(value: unknown): MailLayout {
    const row = value && typeof value === 'object' ? (value as Partial<MailLayout>) : {};
    return {
        preview: row.preview === 'below' || row.preview === 'separate' ? row.preview : 'right',
        ratio:
            typeof row.ratio === 'number' && Number.isFinite(row.ratio)
                ? Math.max(20, Math.min(75, row.ratio))
                : 38,
        density: row.density === 'compact' ? 'compact' : 'comfortable',
        mode: row.mode === 'flat' ? 'flat' : 'tree'
    };
}
export function workspacePreferences(value: unknown): WorkspacePreferences {
    const row = value && typeof value === 'object' ? (value as Partial<WorkspacePreferences>) : {};
    return {
        version: 1,
        collapsed: row.collapsed !== false,
        startupPage: startupPages.some((page) => page.id === row.startupPage)
            ? row.startupPage!
            : 'netmail',
        netmail: layout(row.netmail),
        echo: layout(row.echo)
    };
}
export function parseWorkspace(document: string) {
    try {
        return workspacePreferences(JSON.parse(document));
    } catch {
        return workspacePreferences({});
    }
}
type Client = {
    uiDocument(token: string, key: string): Promise<string>;
    saveUiDocument(token: string, key: string, document: string): Promise<void>;
};
export function createWorkspace(client?: Client) {
    const value = ref(workspacePreferences({}));
    let token = '';
    let generation = 0;
    let queue: Promise<void> = Promise.resolve();
    async function load(session: string) {
        token = session;
        const ticket = ++generation;
        value.value = workspacePreferences({});
        if (!session || !client) return;
        const result = await client.uiDocument(session, 'workspace');
        if (ticket === generation) value.value = parseWorkspace(result);
    }
    function change(patch: (current: WorkspacePreferences) => WorkspacePreferences) {
        const ticket = generation;
        const session = token;
        const operation = queue
            .catch(() => {})
            .then(async () => {
                if (ticket !== generation) return;
                const next = workspacePreferences(patch(value.value));
                if (client && session)
                    await client.saveUiDocument(session, 'workspace', JSON.stringify(next));
                if (ticket === generation) value.value = next;
            });
        queue = operation;
        return operation;
    }
    const update = (patch: Partial<WorkspacePreferences>) =>
        change((current) => ({ ...current, ...patch }));
    const updateLayout = (kind: MailKind, patch: Partial<MailLayout>) =>
        change((current) => ({ ...current, [kind]: { ...current[kind], ...patch } }));
    return { value, load, update, updateLayout };
}
export type Workspace = ReturnType<typeof createWorkspace>;
export const workspaceKey: InjectionKey<Workspace> = Symbol('workspace');
export function useWorkspace() {
    return inject(workspaceKey, () => createWorkspace(), true);
}
