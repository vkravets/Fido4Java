import { fidoText } from './fido-text.ts';
import type { TextLine, TextRun } from './fido-text.ts';
import type { MailBodyLine } from '@rpc/bindings.mjs';

export function visibleKludge(kludge: string): string {
    return kludge.replace(/[\u0000-\u001f\u007f-\u009f]/g, (character) => {
        const code = character.charCodeAt(0);
        if (code <= 0x1f) return `^${String.fromCharCode(code + 64)}`;
        if (code === 0x7f) return '^?';
        return `\\x${code.toString(16).toUpperCase().padStart(2, '0')}`;
    });
}

type ReaderLine = TextLine | { kind: 'kludge'; runs: TextRun[] };

export function readerTextLines(
    text: string,
    bodyLines?: MailBodyLine[],
    visual = true
): ReaderLine[] {
    if (!bodyLines) return fidoText(text, visual);
    const plain = fidoText(
        bodyLines
            .filter((line) => !line.kludge)
            .map((line) => line.text)
            .join('\n'),
        visual
    );
    let index = 0;
    return bodyLines.map((line) =>
        line.kludge
            ? { kind: 'kludge', runs: [{ text: visibleKludge(line.text), styles: [] }] }
            : plain[index++]!
    );
}
