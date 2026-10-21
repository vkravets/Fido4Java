import assert from 'node:assert/strict';
import { test } from 'node:test';
import { highlightText, matchesMail } from '../src/mail-search.ts';
import type { MailItem } from '@rpc/bindings.mjs';
test('search highlighting treats punctuation as literal text and preserves the complete body', () => {
    const text = 'Hello [Bob]. hello [bob].';
    const parts = highlightText(text, '[Bob]');
    assert.equal(parts.filter((part) => part.match).length, 2);
    assert.equal(parts.map((part) => part.text).join(''), text);
    assert.deepEqual(highlightText('anything', '.*'), [{ text: 'anything', match: false }]);
});

test('loaded mail can be found by its visible DOS glyph without changing the original body', () => {
    const item = {
        subject: 'History',
        fromName: '',
        toName: '',
        fromAddress: '',
        toAddress: '',
        body: '\u0004 Sessions History'
    } as MailItem;
    assert.equal(matchesMail(item, '♦ Sessions'), true);
    assert.equal(matchesMail(item, '\u0004 Sessions'), true);
    assert.equal(matchesMail(item, '♣ Sessions'), false);
    assert.equal(item.body, '\u0004 Sessions History');
});
