import type { InjectionKey } from 'vue';

export type SaveDisplayName = (session: string, name: string) => Promise<void>;
export const saveDisplayNameKey: InjectionKey<SaveDisplayName> = Symbol('save-display-name');
