import assert from 'node:assert/strict';
import { Thrift, Int64 } from '../../build/frontend-generated/runtime.mjs';
import { createBindings } from '../../build/frontend-generated/bindings.mjs';
import { createRpcClient } from '../../build/frontend-generated/client.mjs';

const bindings = createBindings(Thrift, Int64);
const origin = process.argv[2];
const clients = {
    login: createRpcClient(Thrift, bindings.LoginServiceClient, `${origin}/rpc/login`),
    auth: createRpcClient(Thrift, bindings.AuthServiceClient, `${origin}/rpc/auth`),
    profile: createRpcClient(Thrift, bindings.ProfileServiceClient, `${origin}/rpc/profile`),
    settings: createRpcClient(Thrift, bindings.SettingsServiceClient, `${origin}/rpc/settings`)
};
function isCode(code: string) {
    return (error: unknown) => error instanceof bindings.UiError && error.code === code;
}
const admin = await clients.login.login(
    new bindings.Credential({ login: 'admin', password: 'preview-admin-password' })
);
assert.equal(admin.respose_status, bindings.Response.OK);
assert.equal((await clients.profile.me(admin.session_id)).role, 'ADMIN');
await clients.profile.register('point', 'point-password', '1:500/100.1');
let point = await clients.login.login(
    new bindings.Credential({ login: 'point', password: 'point-password' })
);
assert.equal(point.respose_status, bindings.Response.AUTHORIZATION_FAILED);
const users = await clients.profile.accounts(admin.session_id);
const registered = users.find((user) => user.login === 'point');
assert.ok(registered);
assert.equal(registered.enabled, false);
await clients.profile.decideAka(admin.session_id, 'point', '1:500/100.1', true);
point = await clients.login.login(
    new bindings.Credential({ login: 'point', password: 'point-password' })
);
assert.equal(point.respose_status, bindings.Response.OK);
await clients.profile.savePreferences(point.session_id, 'dark', '1:500/100.1');
let profile = await clients.profile.me(point.session_id);
assert.equal(profile.theme, 'dark');
assert.deepEqual(profile.allowedAkas, ['1:500/100.1']);
await assert.rejects(clients.profile.accounts(point.session_id), isCode('FORBIDDEN'));
await assert.rejects(clients.settings.current(point.session_id), isCode('FORBIDDEN'));
await assert.rejects(
    clients.profile.savePreferences(point.session_id, 'dark', '1:500/100.2'),
    isCode('INVALID_ARGUMENT')
);
await clients.profile.requestAka(point.session_id, '1:500/100.2');
await clients.profile.decideAka(admin.session_id, 'point', '1:500/100.2', true);
profile = await clients.profile.me(point.session_id);
assert.deepEqual(profile.allowedAkas, ['1:500/100.1', '1:500/100.2']);
assert.equal((await clients.auth.methods()).password, true);
assert.equal((await clients.settings.current(admin.session_id)).password, '');
await clients.login.logout(point.session_id);
await assert.rejects(clients.profile.me(point.session_id), isCode('UNAUTHORIZED'));
console.log('Real HTTP browser RPC workflow passed');
