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
let stationTabs: Component;
let pollDetails: Component;
let languageSelector: Component;
let changeLocale: (locale: string) => void;

before(async () => {
    server = await createServer({
        root: fileURLToPath(new URL('..', import.meta.url)),
        server: { middlewareMode: true },
        appType: 'custom'
    });
    sidebar = (await server.ssrLoadModule('/src/components/Sidebar.vue')).default as Component;
    stationTabs = (await server.ssrLoadModule('/src/components/StationSettingsTabs.vue'))
        .default as Component;
    pollDetails = (await server.ssrLoadModule('/src/components/PollDetails.vue'))
        .default as Component;
    languageSelector = (await server.ssrLoadModule('/src/components/LanguageSelector.vue'))
        .default as Component;
    changeLocale = (await server.ssrLoadModule('/src/i18n.ts')).setLocale;
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
    assert.ok(html.includes('Netmail'));
    assert.ok(html.includes('Echomail'));
    assert.ok(html.includes('My settings'));
    assert.ok(!html.includes('Accounts and requests'));
    assert.ok(!html.includes('Mail robots'));
    assert.ok(!html.includes('Polling'));
    assert.ok(!html.includes('Mail links'));
    assert.ok(!html.includes('Logs'));
    assert.ok(html.includes('disabled'));
});

test('administrator navigation groups management under Station with a links shortcut', async () => {
    const html = await renderToString(h(sidebar, { profile: profile('ADMIN'), page: 'node' }));
    assert.ok(html.includes('Settings'));
    assert.ok(html.includes('Station'));
    assert.ok(html.includes('Mail links'));
    for (const label of [
        'Accounts and requests',
        'Mail robots',
        'Polling',
        'Logs',
        'Netmail routing'
    ]) {
        assert.ok(!html.includes(label));
    }
    const station = await renderToString(h(stationTabs));
    for (const label of [
        'Accounts and requests',
        'Mail robots',
        'Polling',
        'Logs',
        'Netmail routing'
    ]) {
        assert.ok(station.includes(label));
    }
});

test('Ukrainian navigation and the language selector expose only English and Ukrainian', async () => {
    changeLocale('uk');
    try {
        const html = await renderToString(h(sidebar, { profile: profile('ADMIN'), page: 'node' }));
        assert.ok(html.includes('Налаштування'));
        assert.ok(html.includes('Лінки'));
        assert.ok(!html.includes('Посилання'));
        assert.ok(!html.includes('NODE MANAGEMENT'));
        const selector = await renderToString(h(languageSelector));
        assert.ok(selector.includes('value="en"'));
        assert.ok(selector.includes('value="uk"'));
        assert.ok(selector.includes('aria-label="Мова"'));
        assert.ok(!selector.includes('value="ru"'));
    } finally {
        changeLocale('en');
    }
});

test('poll details escaped journal text and exposed retry for a finished attempt', async () => {
    const html = await renderToString(
        h(pollDetails, {
            attempt: {
                id: 'attempt-one',
                address: '1:500/101',
                state: 'FAILED',
                source: 'SCHEDULED',
                created: 1,
                detail: 'Closed early'
            },
            lines: ['<script>alert("unsafe")</script>'],
            files: [],
            clipped: false,
            more: false,
            busy: false
        })
    );
    assert.ok(html.includes('Scheduler'));
    assert.ok(html.includes('Retry poll'));
    assert.ok(html.includes('&lt;script&gt;'));
    assert.ok(!html.includes('<script>'));
    assert.ok(!html.includes('Cancel poll'));
});
