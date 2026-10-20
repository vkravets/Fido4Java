import { t } from './i18n.ts';
import type { MailTheme } from './mail-themes.ts';
export function themeLabel(theme: MailTheme) {
    const labels: Record<string, string> = {
        paper: t('Paper'),
        graphite: t('Graphite'),
        terminal: t('Terminal')
    };
    return labels[theme.id] || theme.name;
}
