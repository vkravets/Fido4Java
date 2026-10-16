import { t } from './i18n.ts';
import { Thrift, Int64 } from '@rpc/runtime.mjs';
import { createBindings } from '@rpc/bindings.mjs';
import { createRpcClient } from '@rpc/client.mjs';
import type { LoginResponse } from '@rpc/bindings.mjs';

export const bindings = createBindings(Thrift, Int64);
export const api = {
    ui: createRpcClient(Thrift, bindings.UiServiceClient, '/rpc/ui'),
    login: createRpcClient(Thrift, bindings.LoginServiceClient, '/rpc/login'),
    auth: createRpcClient(Thrift, bindings.AuthServiceClient, '/rpc/auth'),
    profile: createRpcClient(Thrift, bindings.ProfileServiceClient, '/rpc/profile'),
    settings: createRpcClient(Thrift, bindings.SettingsServiceClient, '/rpc/settings', 120000)
};

export function loginSession(response: LoginResponse): string {
    if (response.respose_status !== bindings.Response.OK || !response.session_id) {
        throw new Error(t('Sign-in failed. Check your password and account approval.'));
    }
    return response.session_id;
}
