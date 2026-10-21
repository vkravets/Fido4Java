import { test } from 'node:test';
import type { TestContext } from 'node:test';
import assert from 'node:assert/strict';
import { createServer } from 'vite';
import { watch } from 'vue';

async function createStartupServer() {
    const apiModule = '\0startup-test-api';
    return createServer({
        configFile: false,
        server: { middlewareMode: true, ws: false },
        appType: 'custom',
        plugins: [
            {
                name: 'startup-test-api',
                enforce: 'pre',
                resolveId(source) {
                    if (source === apiModule || source.endsWith('/api.ts')) {
                        return apiModule;
                    }
                },
                load(id) {
                    if (id === apiModule)
                        return `
                            export const api = { settings: {}, auth: {}, profile: {}, login: {} };
                            export const bindings = {
                                Credential: class { constructor(value) { Object.assign(this, value); } }
                            };
                            export const loginSession = (response) => response.session_id;
                        `;
                }
            }
        ]
    });
}

function browserGlobals(context: TestContext) {
    const originalStorage = Object.getOwnPropertyDescriptor(globalThis, 'localStorage');
    const originalDocument = Object.getOwnPropertyDescriptor(globalThis, 'document');
    const originalSessionStorage = Object.getOwnPropertyDescriptor(globalThis, 'sessionStorage');
    const sessions = new Map<string, string>();
    Object.defineProperty(globalThis, 'sessionStorage', {
        configurable: true,
        value: {
            getItem: (key: string) => sessions.get(key) || null,
            setItem: (key: string, value: string) => sessions.set(key, value),
            removeItem: (key: string) => sessions.delete(key)
        }
    });
    Object.defineProperty(globalThis, 'localStorage', {
        configurable: true,
        value: { getItem: () => null, setItem: () => {} }
    });
    Object.defineProperty(globalThis, 'document', {
        configurable: true,
        value: { documentElement: { dataset: {} } }
    });
    context.after(() => {
        if (originalStorage) Object.defineProperty(globalThis, 'localStorage', originalStorage);
        else Reflect.deleteProperty(globalThis, 'localStorage');
        if (originalDocument) Object.defineProperty(globalThis, 'document', originalDocument);
        else Reflect.deleteProperty(globalThis, 'document');
        if (originalSessionStorage)
            Object.defineProperty(globalThis, 'sessionStorage', originalSessionStorage);
        else Reflect.deleteProperty(globalThis, 'sessionStorage');
    });
    return sessions;
}

test('setup can load its configuration as soon as startup becomes ready', async (context) => {
    const server = await createStartupServer();
    context.after(() => server.close());
    browserGlobals(context);
    const { api } = await server.ssrLoadModule('\0startup-test-api');
    api.settings.status = async () => ({ required: true });
    const { createState } = await server.ssrLoadModule('/src/state.ts');
    const state = createState();
    let configurationLoads = 0;
    const stop = watch(
        state.ready,
        (ready) => {
            if (ready) state.run(async () => configurationLoads++);
        },
        { flush: 'sync' }
    );
    context.after(stop);
    await state.start();
    assert.equal(state.setup.value, true);
    assert.equal(state.ready.value, true);
    assert.equal(configurationLoads, 1);
    api.settings.status = async () => {
        throw new Error('Server unavailable');
    };
    const failed = createState();
    await failed.start();
    assert.equal(failed.ready.value, false);
    assert.equal(failed.error.value, 'Server unavailable');
});

test('background reads do not dim global controls or release foreground work and ignore old-session failures', async (context) => {
    const server = await createStartupServer();
    context.after(() => server.close());
    browserGlobals(context);
    const { createState } = await server.ssrLoadModule('/src/state.ts');
    const state = createState();
    state.session.value = 'first';
    let finishRead!: () => void;
    const reading = state.run(
        () =>
            new Promise<void>((resolve) => {
                finishRead = resolve;
            }),
        { background: true }
    );
    assert.equal(state.busy.value, false);
    let finishWrite!: () => void;
    const writing = state.run(
        () =>
            new Promise<void>((resolve) => {
                finishWrite = resolve;
            })
    );
    assert.equal(state.busy.value, true);
    finishRead();
    await reading;
    assert.equal(state.busy.value, true);
    finishWrite();
    await writing;
    assert.equal(state.busy.value, false);
    let rejectRead!: (failure: unknown) => void;
    const stale = state.run(
        () =>
            new Promise((_resolve, reject) => {
                rejectRead = reject;
            }),
        { background: true }
    );
    state.session.value = 'second';
    rejectRead({ code: 'UNAUTHORIZED', message: 'Old session' });
    await stale;
    assert.equal(state.session.value, 'second');
    assert.equal(state.error.value, '');
});

test('validated sessions survive reload, clear on logout or expiry, and survive temporary startup failures', async (context) => {
    const server = await createStartupServer();
    context.after(() => server.close());
    const saved = browserGlobals(context);
    const { api } = await server.ssrLoadModule('\0startup-test-api');
    api.settings.status = async () => ({ required: false });
    api.auth.methods = async () => ({ password: true, passkey: true });
    api.profile.registrationEnabled = async () => true;
    api.login.login = async () => ({ session_id: 'live-session' });
    let failure: unknown;
    api.profile.me = async (token: string) => {
        assert.equal(token, 'live-session');
        if (failure) throw failure;
        return { login: 'point', theme: 'dark' };
    };
    const { createState } = await server.ssrLoadModule('/src/state.ts');
    const signedIn = createState();
    await signedIn.login('point', 'password');
    assert.equal(saved.get('jftn-session'), 'live-session');
    const reloaded = createState();
    assert.equal(reloaded.profile.value, null);
    await reloaded.start();
    assert.equal(reloaded.ready.value, true);
    assert.equal(reloaded.session.value, 'live-session');
    assert.equal(reloaded.profile.value.login, 'point');
    assert.equal(reloaded.theme.value, 'dark');
    let loggedOut = false;
    api.login.logout = async (token: string) => {
        assert.equal(token, 'live-session');
        loggedOut = true;
    };
    await reloaded.logout();
    assert.equal(loggedOut, true);
    assert.equal(saved.has('jftn-session'), false);
    assert.equal(reloaded.profile.value, null);

    saved.set('jftn-session', 'live-session');
    failure = new Error('Temporary network failure');
    const retry = createState();
    await retry.start();
    assert.equal(retry.ready.value, false);
    assert.equal(retry.profile.value, null);
    assert.equal(retry.session.value, '');
    assert.equal(saved.get('jftn-session'), 'live-session');
    failure = undefined;
    await retry.start();
    assert.equal(retry.ready.value, true);
    assert.equal(retry.profile.value.login, 'point');
    await retry.run(async () => {
        throw { code: 'SESSION_EXPIRED' };
    });
    assert.equal(saved.has('jftn-session'), false);
    assert.equal(retry.session.value, '');

    saved.set('jftn-session', 'live-session');
    failure = { code: 'UNAUTHORIZED' };
    const expired = createState();
    await expired.start();
    assert.equal(expired.ready.value, true);
    assert.equal(expired.profile.value, null);
    assert.equal(expired.error.value, '');
    assert.equal(saved.has('jftn-session'), false);
});
