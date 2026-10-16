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
                        return 'export const api = { settings: {} }; export const bindings = {}; export const loginSession = () => "";';
                }
            }
        ]
    });
}

function browserGlobals(context: TestContext) {
    const originalStorage = Object.getOwnPropertyDescriptor(globalThis, 'localStorage');
    const originalDocument = Object.getOwnPropertyDescriptor(globalThis, 'document');
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
    });
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
