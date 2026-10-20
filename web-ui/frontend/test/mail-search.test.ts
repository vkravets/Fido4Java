import assert from 'node:assert/strict';
import { test } from 'node:test';
import { highlightText } from '../src/mail-search.ts';
test('search highlighting treats punctuation as literal text and preserves the complete body', () => {
    const text = 'Hello [Bob]. hello [bob].';
    const parts = highlightText(text, '[Bob]');
    assert.equal(parts.filter((part) => part.match).length, 2);
    assert.equal(parts.map((part) => part.text).join(''), text);
    assert.deepEqual(highlightText('anything', '.*'), [{ text: 'anything', match: false }]);
});
