import { test } from 'node:test';
import assert from 'node:assert/strict';
import { inlineKludgeBody, readerTextLines } from '../src/mail-kludges.ts';

test('inline controls stay literal and ordered while ordinary text keeps its quote and signature styles', () => {
    const lines = readerTextLines('Unchanged plain body', [
        { text: '\u0001MSGID: <b>*literal*</b>', kludge: true, known: true },
        { text: '> *quoted*', kludge: false, known: false },
        { text: '\u0001CHRS: UTF-8 4', kludge: true, known: true },
        { text: '-- ', kludge: false, known: false },
        { text: '\u0001CHRS: CP866 2', kludge: true, known: true },
        { text: 'Signature', kludge: false, known: false }
    ]);
    assert.deepEqual(
        lines.map((line) => line.kind),
        ['kludge', 'quote1', 'kludge', 'signature', 'kludge', 'signature']
    );
    assert.deepEqual(lines[0]!.runs, [{ text: '^AMSGID: <b>*literal*</b>', styles: [] }]);
    assert.ok(lines[1]!.runs.some((run) => run.styles.includes('bold')));
    assert.equal(readerTextLines('Plain body')[0]!.runs[0]!.text, 'Plain body');
});

test('quoted inline body preserves control order as printable text', () => {
    assert.equal(
        inlineKludgeBody({
            body: 'Plain body',
            bodyLines: [
                { text: '\u0001MSGID: 1:500/1', kludge: true, known: true },
                { text: 'First line', kludge: false, known: false },
                { text: '\u0001CUSTOM: x', kludge: true, known: false }
            ]
        }),
        '^AMSGID: 1:500/1\nFirst line\n^ACUSTOM: x'
    );
});
