export type TextStyle = 'bold' | 'italic' | 'underline' | 'reverse' | 'address' | 'link';
export interface TextRun {
    text: string;
    styles: TextStyle[];
    href?: string;
}
export interface TextLine {
    kind: 'text' | 'quote1' | 'quote2' | 'quote3' | 'signature' | 'tearline' | 'origin' | 'tagline';
    runs: TextRun[];
}
const styles: Record<string, TextStyle> = {
    '*': 'bold',
    '/': 'italic',
    _: 'underline',
    '#': 'reverse'
};
export interface FidoStyleRange {
    from: number;
    to: number;
    style: TextStyle;
    markers?: boolean;
}
function runs(
    text: string,
    inherited: TextStyle[] = [],
    depth = 0,
    base = 0,
    ranges?: FidoStyleRange[]
): TextRun[] {
    if (depth > 4) return [{ text, styles: inherited }];
    const pattern =
        /https?:\/\/[^\s<>]+|\b[0-9]{1,5}:[0-9]{1,5}\/[0-9]{1,5}(?:\.[0-9]{1,10})?(?:@[a-zA-Z0-9.-]+)?\b|([*\/_#])(\S(?:.*?\S)?)\1/g;
    const result: TextRun[] = [];
    let position = 0;
    for (const match of text.matchAll(pattern)) {
        const start = match.index!;
        const end = start + match[0].length;
        const link = match[0].startsWith('http');
        const address = !link && !match[1];
        if (
            !link &&
            !address &&
            ((start && !/[\s([{]/.test(text[start - 1]!)) ||
                (end < text.length && !/[\s.,!?;:)\]}]/.test(text[end]!)))
        )
            continue;
        if (start > position) result.push({ text: text.slice(position, start), styles: inherited });
        if (link) {
            result.push({ text: match[0], styles: inherited, href: match[0] });
            ranges?.push({ from: base + start, to: base + end, style: 'link', markers: false });
        } else if (address) {
            result.push({ text: match[0], styles: [...inherited, 'address'] });
            ranges?.push({ from: base + start, to: base + end, style: 'address', markers: false });
        } else {
            ranges?.push({ from: base + start, to: base + end, style: styles[match[1]!]! });
            result.push(
                ...runs(
                    match[2]!,
                    [...inherited, styles[match[1]!]!],
                    depth + 1,
                    base + start + 1,
                    ranges
                )
            );
        }
        position = end;
    }
    if (position < text.length || !result.length)
        result.push({ text: text.slice(position), styles: inherited });
    return result;
}
export function fidoText(text: string, visual = true): TextLine[] {
    let signature = false;
    return text
        .replaceAll('\r\n', '\n')
        .replaceAll('\r', '\n')
        .split('\n')
        .map((line) => {
            const quote = line.match(/^\s*(?:[^\s<>]{0,12}>)+/);
            let kind: TextLine['kind'] = 'text';
            if (quote) {
                const depth = quote[0].match(/>/g)!.length;
                kind = `quote${((depth - 1) % 3) + 1}` as TextLine['kind'];
            } else if (/^\s*\* Origin:/.test(line)) kind = 'origin';
            else if (/^---(?: |$)/.test(line)) kind = 'tearline';
            else if (/^\.\.\.(?: |$)/.test(line)) kind = 'tagline';
            else if (line === '-- ') {
                signature = true;
                kind = 'signature';
            } else if (signature) kind = 'signature';
            return { kind, runs: visual ? runs(line) : [{ text: line, styles: [] }] };
        });
}

export function fidoStyleRanges(text: string): FidoStyleRange[] {
    const ranges: FidoStyleRange[] = [];
    runs(text, [], 0, 0, ranges);
    return ranges;
}
