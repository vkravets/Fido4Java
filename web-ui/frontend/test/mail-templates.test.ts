import assert from 'node:assert/strict';
import { test } from 'node:test';
import { builtInTemplates, createMailTemplates, expandTemplate } from '../src/mail-templates.ts';
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
    const result = expandTemplate({ id: 'quoted', name: 'Quoted', kind: 'quoted', body: 'Hello @oname.\n\n@quote\n\n@position\n\n@cname' }, context);
    assert.ok(result.body.includes('> @cname\n> Original'));
    assert.equal(result.body.slice(result.cursor), '\n\nAlex Example');
    const template = {
        ...{ id: 'new', name: 'New', kind: 'new' },
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
