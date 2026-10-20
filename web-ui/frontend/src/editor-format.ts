/** FIDO style markers stay inside quote prefixes and never span paragraph boundaries. */
export function formatFidoLines(text: string, marker: string): string {
    if (!text) return marker + marker;
    return text
        .split('\n')
        .map((line) => {
            const match = line.match(/^(\s*(?:[^\s<>]{0,12}>)+\s*)?(\s*)(.*?)(\s*)$/)!;
            const content = match[3]!;
            if (!content) return line;
            const formatted =
                content.startsWith(marker) &&
                content.endsWith(marker) &&
                content.length > marker.length * 2
                    ? content.slice(marker.length, -marker.length)
                    : marker + content + marker;
            return (match[1] || '') + match[2] + formatted + match[4];
        })
        .join('\n');
}
