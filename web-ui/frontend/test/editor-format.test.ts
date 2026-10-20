import assert from 'node:assert/strict';
import { test } from 'node:test';
import { formatFidoLines } from '../src/editor-format.ts';
test('style actions preserve quote prefixes, whitespace and paragraph boundaries', () => {
    const text = 'AE> Hello\n\n  Second line  ';
    const result = formatFidoLines(text, '*');
    assert.equal(result, 'AE> *Hello*\n\n  *Second line*  ');
    assert.equal(formatFidoLines(result, '*'), text);
    assert.equal(formatFidoLines('', '_'), '__');
});
