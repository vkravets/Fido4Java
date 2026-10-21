// IBM PC display glyphs: https://www.unicode.org/Public/MAPPINGS/VENDORS/MISC/IBMGRAPH.TXT
// TAB, LF and CR retain their text-layout meaning; NUL is not a display glyph.
const glyphs = [
    '',
    '☺',
    '☻',
    '♥',
    '♦',
    '♣',
    '♠',
    '•',
    '◘',
    '',
    '',
    '♂',
    '♀',
    '',
    '♫',
    '☼',
    '►',
    '◄',
    '↕',
    '‼',
    '¶',
    '§',
    '▬',
    '↨',
    '↑',
    '↓',
    '→',
    '←',
    '∟',
    '↔',
    '▲',
    '▼'
];
export const dosGraphicPattern = () => /[\u0001-\u0008\u000b\u000c\u000e-\u001f\u007f]/g;
export function dosGraphic(character: string): string {
    const code = character.charCodeAt(0);
    return code === 0x7f ? '⌂' : glyphs[code] || character;
}
/** Display only. Never use this result to update a draft or its RPC payload. */
export function dosGraphicsText(text: string): string {
    return text.replace(dosGraphicPattern(), dosGraphic);
}
