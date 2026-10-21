import { locale } from './i18n.ts';
import { pluginSettingsTranslations } from './locales/plugin-settings.ts';

export function pluginSettingsLabel(plugin: string, value: string): string {
    return pluginSettingsTranslations[plugin]?.[locale.value]?.[value] || value;
}

export function pluginSettingsChoice(plugin: string, field: string, value: string): string {
    // Area names and directory source IDs are user data, even when they match a UI key.
    if (plugin !== 'fileecho' || !['enabled', 'access'].includes(field)) return value;
    return pluginSettingsLabel(plugin, value);
}
