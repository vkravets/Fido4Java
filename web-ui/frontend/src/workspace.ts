import { inject, ref } from 'vue';
import type { InjectionKey } from 'vue';

export type PreviewPosition = 'right' | 'below' | 'separate';
export type MailKind = 'netmail' | 'echo';
export interface MailLayout {
    preview: PreviewPosition;
    ratio: number;
    density: 'compact' | 'comfortable';
    mode: 'flat' | 'tree';
}
export interface WorkspacePreferences {
    version: 1;
    collapsed: boolean;
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
        collapsed: row.collapsed === true,
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
    async function load(session: string) {
        token = session;
        const ticket = ++generation;
        value.value = workspacePreferences({});
        if (!session || !client) return;
        const result = await client.uiDocument(session, 'workspace');
        if (ticket === generation) value.value = parseWorkspace(result);
    }
    async function update(patch: Partial<WorkspacePreferences>) {
        const ticket = generation;
        const next = workspacePreferences({ ...value.value, ...patch });
        if (client && token) await client.saveUiDocument(token, 'workspace', JSON.stringify(next));
        if (ticket === generation) value.value = next;
    }
    return { value, load, update };
}
export type Workspace = ReturnType<typeof createWorkspace>;
export const workspaceKey: InjectionKey<Workspace> = Symbol('workspace');
export function useWorkspace() {
    return inject(workspaceKey, () => createWorkspace(), true);
}
