import { t } from './i18n.ts';

export function routingReason(reason: string): string {
    const labels: Record<string, string> = {
        LOCAL: t('Local mailbox'),
        RULE: t('Matching rule'),
        DIRECT: t('Direct link'),
        DEFAULT: t('Default route'),
        NO_ROUTE: t('No route'),
        DIRECT_UNAVAILABLE: t('Direct delivery unavailable'),
        INVALID_DESTINATION: t('Invalid destination'),
        TRANSIT_DISABLED: t('Transit is disabled'),
        SOURCE_DENIED: t('Transit sender denied'),
        TARGET_DENIED: t('Transit next hop denied'),
        RETURN_TO_SOURCE: t('Would return to sender'),
        VIA_LOOP: t('VIA loop detected')
    };
    return labels[reason] || reason;
}

export function routingMasks(value: string): string[] {
    return value
        .split(/\r?\n/)
        .map((line) => line.trim())
        .filter(Boolean);
}
