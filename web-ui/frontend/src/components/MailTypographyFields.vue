<script setup lang="ts">
import { t } from '../i18n.ts';
import { mailFontLabels } from '../mail-typography.ts';
import type { MailTypography } from '../mail-typography.ts';
const model = defineModel<MailTypography>({ required: true });
defineProps<{ label: string }>();
function update<K extends keyof MailTypography>(key: K, value: MailTypography[K]) {
    model.value = { ...model.value, [key]: value };
}
</script>
<template>
    <fieldset class="mail-typography">
        <legend>{{ label }}</legend>
        <label
            >{{ t('Font family') }}
            <select
                :value="model.font"
                @change="
                    update(
                        'font',
                        ($event.target as HTMLSelectElement).value as MailTypography['font']
                    )
                "
            >
                <option
                    v-for="(name, id) in mailFontLabels"
                    :key="id"
                    :value="id"
                >
                    {{ id === 'system' ? t('System monospace') : name }}
                </option>
            </select>
        </label>
        <div class="font-metrics">
            <label
                >{{ t('Font size (px)') }}
                <select
                    :value="model.size ?? ''"
                    @change="
                        update(
                            'size',
                            ($event.target as HTMLSelectElement).value === ''
                                ? null
                                : Number(($event.target as HTMLSelectElement).value)
                        )
                    "
                >
                    <option value="">{{ t('Automatic') }}</option>
                    <option
                        v-for="size in [10, 11, 12, 13, 14, 15, 16, 18, 20, 24, 28, 32]"
                        :key="size"
                        :value="size"
                    >
                        {{ size }}
                    </option>
                </select>
            </label>
            <label
                >{{ t('Line height') }}
                <select
                    :value="model.lineHeight ?? ''"
                    @change="
                        update(
                            'lineHeight',
                            ($event.target as HTMLSelectElement).value === ''
                                ? null
                                : Number(($event.target as HTMLSelectElement).value)
                        )
                    "
                >
                    <option value="">{{ t('Automatic') }}</option>
                    <option
                        v-for="height in [1, 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.65, 1.8, 2]"
                        :key="height"
                        :value="height"
                    >
                        {{ height.toFixed(2).replace(/0$/, '') }}
                    </option>
                </select>
            </label>
        </div>
    </fieldset>
</template>
<style scoped>
.mail-typography {
    border: 1px solid var(--line);
    border-radius: 6px;
    padding: 10px;
    margin: 12px 0;
    min-width: 0;
}
legend {
    font-weight: 600;
    padding: 0 4px;
}
.font-metrics {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 10px;
}
label {
    font-size: 13px;
}
select {
    min-height: 32px;
    padding: 5px 8px;
}
</style>
