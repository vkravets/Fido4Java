import { t } from './i18n.ts';
import type { MailTemplate } from './mail-templates.ts';
export function templateLabel(template: MailTemplate) {
    const labels: Record<string, string> = { new: t('New message'), reply: t('Short reply'), quoted: t('Quoted reply'), forward: t('Forward') };
    return labels[template.id] || template.name;
}
export function templateError(failure: unknown) {
    const message = failure instanceof Error ? failure.message : String(failure);
    const token = message.match(/^Unsupported template token: (.*)$/);
    return token ? t('Unsupported template token: {token}', { token: token[1]! }) : message;
}
