export const mailFonts = {
    system: 'var(--font-mono, monospace)',
    consolas: 'Consolas, monospace',
    cascadia: '"Cascadia Mono", monospace',
    dejavu: '"DejaVu Sans Mono", monospace',
    liberation: '"Liberation Mono", monospace',
    courier: '"Courier New", monospace'
} as const;

export const mailFontLabels = {
    system: 'System monospace',
    consolas: 'Consolas',
    cascadia: 'Cascadia Mono',
    dejavu: 'DejaVu Sans Mono',
    liberation: 'Liberation Mono',
    courier: 'Courier New'
} as const;

export interface MailTypography {
    font: keyof typeof mailFonts;
    size: number | null;
    lineHeight: number | null;
}

export const defaultMailTypography = (): MailTypography => ({
    font: 'system',
    size: null,
    lineHeight: null
});

export function mailTypography(value: unknown): MailTypography {
    if (!value || typeof value !== 'object') throw new Error('Invalid mail typography');
    const row = value as MailTypography;
    if (
        !Object.hasOwn(mailFonts, row.font) ||
        !validNumber(row.size, 10, 32) ||
        !validNumber(row.lineHeight, 1, 2)
    ) {
        throw new Error('Invalid mail typography');
    }
    return { font: row.font, size: row.size, lineHeight: row.lineHeight };
}

function validNumber(value: unknown, min: number, max: number) {
    return (
        value === null ||
        (typeof value === 'number' && Number.isFinite(value) && value >= min && value <= max)
    );
}

export function loadMailTypography(value: unknown): MailTypography {
    try {
        return mailTypography(value);
    } catch {
        return defaultMailTypography();
    }
}

export function typographyStyle(value: MailTypography): Record<string, string> {
    const row = mailTypography(value);
    return {
        '--mail-font-family': mailFonts[row.font],
        ...(row.size === null ? {} : { '--mail-font-size': `${row.size}px` }),
        ...(row.lineHeight === null ? {} : { '--mail-line-height': String(row.lineHeight) })
    };
}
