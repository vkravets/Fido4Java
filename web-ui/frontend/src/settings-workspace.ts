import type { InjectionKey, Ref } from 'vue';

export interface SettingsWorkspaceContext {
    title: Readonly<Ref<string>>;
    actions: Ref<HTMLElement | null>;
}

export const settingsWorkspaceKey: InjectionKey<SettingsWorkspaceContext> =
    Symbol('settings-workspace');
