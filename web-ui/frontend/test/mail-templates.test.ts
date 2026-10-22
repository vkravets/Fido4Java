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
    assert.equal(result.body.slice(result.cursor), '\n> @cname\n> Original\n\nAlex');
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

test('GoldED defaults greet by first name, sign with the sender first name and place replies above quotes', () => {
    const context = {
        echo: false,
        fromName: 'Alex Example',
        fromAddress: '1:500/100.1',
        toName: 'Bob Example',
        toAddress: '1:500/101',
        subject: 'Test',
        area: '',
        originalName: 'Bob Example',
        originalAddress: '1:500/101',
        originalDate: '20 Oct 26 10:00:00',
        originalBody: 'Original message'
    };
    const expected = [
        'Hello Bob!\n\n<cursor>\n\nAlex',
        'Hello Bob!\n\nReplying to a msg dated 20 Oct 26 10:00:00, from Bob Example.\n\n<cursor>\n\nAlex',
        'Hello Bob!\n\n20 Oct 26 10:00:00, Bob Example wrote:\n\n<cursor>\n> Original message\n\nAlex',
        '* Forwarded by Alex Example (1:500/100.1).\n* Originally by: Bob Example (1:500/101), 20 Oct 26 10:00:00.\n\nOriginal message\n\nHello Bob!\n\n<cursor>\n\nAlex'
    ];
    builtInTemplates.forEach((template, index) => {
        const { body, cursor } = expandTemplate(template, { ...context, kind: template.kind });
        assert.equal(body.slice(0, cursor) + '<cursor>' + body.slice(cursor), expected[index]);
    });
    for (const toName of ['All', 'ALL', '']) {
        assert.ok(
            expandTemplate(builtInTemplates[0]!, {
                ...context,
                kind: 'new',
                toName
            }).body.startsWith('Hello everybody!')
        );
    }
});

test('updated built-ins preserve saved personal templates and their default rules', async () => {
    const personal = {
        id: 'personal',
        name: 'My greeting',
        kind: 'new' as const,
        body: 'Hi @tname.\n@position\n@cname'
    };
    const rules = [{ kind: 'new' as const, aka: '', area: '', template: 'personal' }];
    const store = createMailTemplates({
        uiDocument: async () => JSON.stringify({ version: 1, custom: [personal], rules }),
        saveUiDocument: async () => {
            throw new Error('Loading defaults must not write preferences');
        }
    });
    await store.load('session');
    assert.deepEqual(store.choose('new', '1:500/100', ''), personal);
    assert.deepEqual(store.rules.value, rules);
});
