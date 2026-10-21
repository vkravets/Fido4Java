import assert from 'node:assert/strict';
import { test } from 'node:test';
import { dosGraphicsText } from '../src/dos-graphics.ts';
import { fidoText } from '../src/fido-text.ts';

test('IBM PC control positions display as DOS graphics while layout characters remain intact', () => {
    const controls =
        Array.from({ length: 31 }, (_, index) => String.fromCharCode(index + 1)).join('') +
        '\u007f';
    assert.equal(dosGraphicsText(controls), '☺☻♥♦♣♠•◘\t\n♂♀\r♫☼►◄↕‼¶§▬↨↑↓→←∟↔▲▼⌂');
    assert.equal(dosGraphicsText('\0\t\r\n┌─┐░▒▓█ Україна'), '\0\t\r\n┌─┐░▒▓█ Україна');
});

test('display conversion preserves source runs, formatting and raw link targets', () => {
    const original = '> \u0004 *History* https://example.test/\u0004';
    const lines = fidoText(original);
    assert.equal(lines[0]!.kind, 'quote1');
    assert.equal(lines[0]!.runs.find((run) => run.styles.includes('bold'))!.text, 'History');
    assert.equal(lines[0]!.runs.find((run) => run.href)!.href, 'https://example.test/\u0004');
    assert.equal(dosGraphicsText(lines[0]!.runs[0]!.text), '> ♦ ');
    assert.equal(fidoText(original, false)[0]!.runs[0]!.text, original);
});
