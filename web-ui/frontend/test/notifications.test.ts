import { before, test } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { stripTypeScriptTypes } from 'node:module';
import { createRenderer, h, nextTick, Teleport } from 'vue';
import type { Component } from 'vue';
import { compileScript, parse } from 'vue/compiler-sfc';
import { setLocale, t } from '../src/i18n.ts';

interface Element {
    tag: string;
    text: string;
    props: Record<string, unknown>;
    children: Element[];
    parent: Element | null;
}

let banner: Component;

before(async () => {
    const source = await readFile(
        new URL('../src/components/NotificationBanner.vue', import.meta.url),
        'utf8'
    );
    const { descriptor } = parse(source);
    const compiled = compileScript(descriptor, { id: 'notifications', inlineTemplate: true });
    const script = stripTypeScriptTypes(compiled.content)
        .replace(/from ['"]vue['"]/g, `from ${JSON.stringify(import.meta.resolve('vue'))}`)
        .replace(
            /from ['"]\.\.\/i18n\.ts['"]/g,
            `from ${JSON.stringify(new URL('../src/i18n.ts', import.meta.url).href)}`
        );
    banner = (await import(`data:text/javascript;base64,${Buffer.from(script).toString('base64')}`))
        .default as Component;
});

function element(tag: string, text = ''): Element {
    return { tag, text, props: {}, children: [], parent: null };
}

function find(root: Element, predicate: (item: Element) => boolean): Element | undefined {
    if (predicate(root)) return root;
    for (const child of root.children) {
        const match = find(child, predicate);
        if (match) return match;
    }
}

function fixture() {
    const body = element('body');
    const root = element('app');
    function remove(item: Element) {
        if (!item.parent) return;
        const siblings = item.parent.children;
        siblings.splice(siblings.indexOf(item), 1);
        item.parent = null;
    }
    const { render } = createRenderer<Element, Element>({
        createElement: element,
        createText: (text) => element('text', text),
        createComment: (text) => element('comment', text),
        setText: (item, text) => {
            item.text = text;
        },
        setElementText: (item, text) => {
            item.text = text;
            item.children = [];
        },
        parentNode: (item) => item.parent,
        nextSibling: (item) => {
            const siblings = item.parent?.children || [];
            return siblings[siblings.indexOf(item) + 1] || null;
        },
        patchProp: (item, name, previous, value) => {
            item.props[name] = value;
        },
        insert: (item, parent, anchor) => {
            remove(item);
            const index = anchor ? parent.children.indexOf(anchor) : parent.children.length;
            parent.children.splice(index, 0, item);
            item.parent = parent;
        },
        remove,
        querySelector: (selector) =>
            selector === 'body'
                ? body
                : find(body, (item) => `#${item.props.id}` === selector) || null
    });
    function show(messages: { message: string; kind: 'info' | 'warning' | 'error' }[]) {
        render(
            h('div', [
                h(Teleport, { to: 'body' }, [h('section', { id: 'notifications' })]),
                h(
                    'main',
                    messages.map((props, index) => h(banner, { ...props, key: index }))
                )
            ]),
            root
        );
        return nextTick();
    }
    return { body, root, show, unmount: () => render(null, root) };
}

test('page notifications share a body-level target and disappear when their page unmounts', async () => {
    const ui = fixture();
    await ui.show([
        { message: 'Saved', kind: 'info' },
        { message: 'Restart required', kind: 'warning' },
        { message: 'Connection failed', kind: 'error' }
    ]);
    const host = find(ui.body, (item) => item.props.id === 'notifications')!;
    assert.equal(host.parent, ui.body);
    assert.equal(host.children.filter((item) => item.tag === 'div').length, 3);
    assert.equal(
        find(ui.root, (item) => item.props.role === 'alert'),
        undefined
    );
    assert.ok(find(host, (item) => item.props.role === 'alert'));
    ui.unmount();
    assert.equal(
        find(ui.body, (item) => item.props.id === 'notifications'),
        undefined
    );
});

for (const kind of ['info', 'warning'] as const) {
    test(`${kind} can be dismissed and a changed message appears again`, async () => {
        const ui = fixture();
        await ui.show([{ message: 'First message', kind }]);
        const button = find(ui.body, (item) => item.tag === 'button')!;
        assert.ok(button.props['aria-label']);
        (button.props.onClick as () => void)();
        await nextTick();
        assert.equal(
            find(ui.body, (item) => item.props.role === 'status'),
            undefined
        );
        await ui.show([{ message: 'New message', kind }]);
        assert.ok(find(ui.body, (item) => item.text === 'New message'));
        ui.unmount();
    });
}

test('errors have no dismiss action and clear when the source error is resolved', async () => {
    const ui = fixture();
    await ui.show([{ message: 'Connection failed', kind: 'error' }]);
    assert.ok(find(ui.body, (item) => item.props.role === 'alert'));
    assert.equal(
        find(ui.body, (item) => item.tag === 'button'),
        undefined
    );
    await ui.show([{ message: '', kind: 'error' }]);
    assert.equal(
        find(ui.body, (item) => item.props.role === 'alert'),
        undefined
    );
    ui.unmount();
});

test('existing notifications change language without restoring dismissed notifications', async () => {
    const ui = fixture();
    setLocale('uk');
    await ui.show([{ message: t('Settings saved.'), kind: 'info' }]);
    assert.ok(find(ui.body, (item) => item.text === 'Налаштування збережено.'));
    setLocale('en');
    await nextTick();
    assert.ok(find(ui.body, (item) => item.text === 'Settings saved.'));
    const button = find(ui.body, (item) => item.tag === 'button')!;
    (button.props.onClick as () => void)();
    await nextTick();
    setLocale('uk');
    await nextTick();
    assert.equal(
        find(ui.body, (item) => item.props.role === 'status'),
        undefined
    );
    ui.unmount();
    setLocale('en');
});
