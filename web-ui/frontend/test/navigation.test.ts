import { after, before, test } from 'node:test';
import assert from 'node:assert/strict';
import { fileURLToPath } from 'node:url';
import { createServer } from 'vite';
import type { ViteDevServer } from 'vite';
import { h } from 'vue';
import type { Component } from 'vue';
import { renderToString } from 'vue/server-renderer';
import type { UserProfile } from '../../build/frontend-generated/bindings.mjs';

let server: ViteDevServer;
let sidebar: Component;

before(async () => {
    server = await createServer({
        root: fileURLToPath(new URL('..', import.meta.url)),
        server: { middlewareMode: true },
        appType: 'custom'
    });
    sidebar = (await server.ssrLoadModule('/src/components/Sidebar.vue')).default as Component;
});

after(async () => {
    await server?.close();
});

function profile(role: string): UserProfile {
    return {
        login: role.toLowerCase(),
        role,
        enabled: true,
        theme: 'light',
        defaultAka: '1:500/100.1',
        allowedAkas: ['1:500/100.1'],
        claims: []
    } as UserProfile;
}

test('point navigation contains mail placeholders and personal settings without node controls', async () => {
    const html = await renderToString(h(sidebar, { profile: profile('POINT'), page: 'settings' }));
    assert.ok(html.includes('Нетмейл'));
    assert.ok(html.includes('Эхоконференции'));
    assert.ok(html.includes('Мои настройки'));
    assert.ok(!html.includes('УПРАВЛЕНИЕ НОДОЙ'));
    assert.ok(!html.includes('Аккаунты и заявки'));
    assert.ok(html.includes('disabled'));
});

test('administrator navigation exposes station configuration and account moderation', async () => {
    const html = await renderToString(h(sidebar, { profile: profile('ADMIN'), page: 'node' }));
    assert.ok(html.includes('УПРАВЛЕНИЕ НОДОЙ'));
    assert.ok(html.includes('Аккаунты и заявки'));
    assert.ok(html.includes('Станция'));
});
