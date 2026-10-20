<script setup lang="ts">
import { t } from '../i18n.ts';
import { useMedia } from '../media.ts';
import type { MailLayout } from '../workspace.ts';
import AppIcon from './AppIcon.vue';
const query = defineModel<string>({ required: true });
defineProps<{ preferences: MailLayout; busy?: boolean }>();
defineEmits<{ update: [value: Partial<MailLayout>] }>();
const desktop = useMedia('(min-width: 701px)');
const positions = [
    ['right', 'Preview on the right'],
    ['below', 'Preview below'],
    ['separate', 'No preview']
] as const;
</script>
<template>
    <div class="mail-list-tools">
        <slot name="toolbar" />
        <div class="mail-filter-row">
            <slot name="filters" />
            <label class="mail-query">
                <span class="control-label">{{ t('Search loaded messages') }}</span>
                <AppIcon name="search" />
                <input
                    v-model="query"
                    type="search"
                    maxlength="120"
                    :placeholder="t('Search messages')"
                />
            </label>
            <details
                class="mail-view-options"
                :open="!desktop"
                @keydown.esc="($event.currentTarget as HTMLDetailsElement).open = false"
            >
                <summary
                    role="button"
                    :aria-label="t('Display options')"
                    :title="t('Display options')"
                >
                    <AppIcon name="sliders" />
                </summary>
                <div class="mail-options-panel">
                    <label
                        >{{ t('Preview position')
                        }}<select
                            :value="preferences.preview"
                            :disabled="busy"
                            @change="
                                $emit('update', {
                                    preview: ($event.target as HTMLSelectElement)
                                        .value as MailLayout['preview']
                                })
                            "
                        >
                            <option
                                v-for="position in positions"
                                :key="position[0]"
                                :value="position[0]"
                            >
                                {{ t(position[1]) }}
                            </option>
                        </select></label
                    >
                    <label
                        >{{ t('List density')
                        }}<select
                            :value="preferences.density"
                            :disabled="busy"
                            @change="
                                $emit('update', {
                                    density: ($event.target as HTMLSelectElement)
                                        .value as MailLayout['density']
                                })
                            "
                        >
                            <option value="compact">{{ t('Compact') }}</option>
                            <option value="comfortable">{{ t('Comfortable') }}</option>
                        </select></label
                    >
                    <slot name="options" />
                </div>
            </details>
        </div>
    </div>
</template>
