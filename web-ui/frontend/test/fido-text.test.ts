import assert from 'node:assert/strict';
import { test } from 'node:test';
import { fidoStyleRanges, fidoText } from '../src/fido-text.ts';
test('FIDO quotes and endings are classified without interpreting HTML', () => {
    const lines = fidoText(
        'AE> hello\rAE>> deeper\rAE>>> deepest\r--- JFtn\r * Origin: test\r... tagline\r<script>x</script>'
    );
    assert.deepEqual(
        lines.map((line) => line.kind),
        ['quote1', 'quote2', 'quote3', 'tearline', 'origin', 'tagline', 'text']
    );
    assert.equal(lines.at(-1)!.runs[0]!.text, '<script>x</script>');
});
test('visual marker styles do not confuse URLs, paths or change source mode', () => {
    const text = '*bold* /italic/ _under_ #reverse# https://example.test/a/b /usr/bin/file';
    const lines = fidoText(text);
    assert.equal(lines[0]!.runs.filter((run) => run.styles.length).length, 4);
    assert.equal(lines[0]!.runs.find((run) => run.href)!.href, 'https://example.test/a/b');
    assert.equal(fidoText(text, false)[0]!.runs[0]!.text, text);
    assert.equal(fidoText('*unfinished')[0]!.runs[0]!.text, '*unfinished');
});

test('editor ranges preserve link and FTN address bytes without hiding delimiters', () => {
    const text = 'https://example.test/a/b and 1:500/100.1';
    const ranges = fidoStyleRanges(text);
    assert.deepEqual(
        ranges.map((value) => [value.style, value.markers, text.slice(value.from, value.to)]),
        [
            ['link', false, 'https://example.test/a/b'],
            ['address', false, '1:500/100.1']
        ]
    );
});
