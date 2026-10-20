import assert from 'node:assert/strict';
import { test } from 'node:test';
import { builtInTemplates, createMailTemplates, expandTemplate } from '../src/mail-templates.ts';

test('changing accounts clears templates and ignores an earlier account response', async () => {
    let completeFirst: (document: string) => void = () => {};
    const first = new Promise<string>((resolve) => {
        completeFirst = resolve;
    });
    const store = createMailTemplates({
        uiDocument: async (token) => (token === 'first' ? first : '{}'),
        saveUiDocument: async () => {}
    });
    const loading = store.load('first');
    await store.load('second');
    completeFirst(
        JSON.stringify({
            version: 1,
            custom: [
                { id: 'private-first', name: 'First only', kind: 'new', body: 'Private greeting' }
            ],
            rules: []
        })
    );
    await loading;
    assert.deepEqual(store.custom.value, []);
    assert.deepEqual(store.rules.value, []);
    await store.load('');
    assert.deepEqual(store.custom.value, []);
});
test('templates expand GoldED-style data, conditions and quotes once with a precise cursor', () => {
    const context = {
        kind: 'quoted' as const,
        echo: true,
        fromName: 'Alex Example',
        fromAddress: '1:500/100.1',
        toName: 'Bob',
        toAddress: '1:500/101',
        subject: 'Test',
        area: 'test.echo',
        originalBody: '@cname\nOriginal',
        originalName: 'Bob'
    };
    const result = expandTemplate(builtInTemplates[2]!, context);
    assert.ok(result.body.includes('> @cname\n> Original'));
    assert.equal(result.body.slice(result.cursor), '\n\nAlex Example');
    const template = {
        ...builtInTemplates[0]!,
        body: '@newNew\n@echoEcho\n@netNet\n@@cname\n@caddr @position!'
    };
    const value = expandTemplate(template, context);
    assert.equal(value.body, 'Echo\n@cname\n1:500/100.1 !');
    assert.equal(value.body[value.cursor], '!');
    assert.throws(
        () => expandTemplate({ ...template, body: '@include /private' }, context),
        /Unsupported/
    );
});
test('specific defaults override generic rules without changing stored identities', async () => {
    const store = createMailTemplates();
    await store.save(
        [],
        [
            { kind: 'quoted', aka: '', area: '', template: 'quoted' },
            { kind: 'quoted', aka: '1:500/100.1', area: 'TEST.ECHO', template: 'reply' }
        ]
    );
    assert.equal(store.choose('quoted', '1:500/100.1', 'test.echo').id, 'reply');
    assert.equal(store.choose('quoted', '1:500/100.2', 'test.echo').id, 'quoted');
});

test('escaped cursor tokens remain literal and values never expand recursively', () => {
    const context = {
        kind: 'new' as const,
        echo: false,
        fromName: '@quote',
        fromAddress: '1:500/100',
        toName: 'Bob',
        toAddress: '1:500/101',
        subject: '',
        area: ''
    };
    const result = expandTemplate(
        { ...builtInTemplates[0]!, body: '; ignored\n@@position @cname @position!\n@position' },
        context
    );
    assert.equal(result.body, '@position @quote !\n');
    assert.equal(result.cursor, '@position @quote '.length);
    assert.throws(
        () => expandTemplate({ ...builtInTemplates[0]!, body: '@constructor' }, context),
        /Unsupported/
    );
});
