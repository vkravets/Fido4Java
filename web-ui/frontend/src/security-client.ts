import type { InjectionKey } from 'vue';
import type { PasskeyInfo } from '@rpc/bindings.mjs';

export const passkeyListKey: InjectionKey<(session: string) => Promise<PasskeyInfo[]>> =
    Symbol('passkey-list');
