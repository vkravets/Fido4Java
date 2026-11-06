<script setup lang="ts">
import { t } from '../i18n.ts';
import { useMedia } from '../media.ts';
import { ref } from 'vue';
import { useWorkspace } from '../workspace.ts';
import type { MailLayout } from '../workspace.ts';
import AppIcon from './AppIcon.vue';
import NotificationBanner from './NotificationBanner.vue';
const query = defineModel<string>({ required: true });
defineProps<{ preferences: MailLayout; kind: 'netmail' | 'echo'; busy?: boolean }>();
defineEmits<{ update: [value: Partial<MailLayout>] }>();
const desktop = useMedia('(min-width: 701px)');
const workspace = useWorkspace();
const panelSaving = ref(false);
const panelError = ref('');
async function setPanels(value: 'all' | 'lastTwo') {
    if (panelSaving.value) return;
    panelSaving.value = true;
    panelError.value = '';
    try {
        await workspace.update({ conferencePanels: value });
    } catch {
        panelError.value = t('Could not save workspace preferences.');
    } finally {
        panelSaving.value = false;
    }
}
function closeOptions(event: KeyboardEvent) {
    if (!desktop.value) return;
    const details = event.currentTarget as HTMLDetailsElement;
    details.open = false;
    details.querySelector('summary')?.focus();
}
const positions = [
    ['right', 'Preview on the right'],
    ['below', 'Preview below'],
    ['separate', 'No preview']
] as const;
</script>
<template>
    <div class="mail-list-tools">
        <NotificationBanner kind="error" :message="panelError" />
        <slot name="toolbar" />
        <div class="mail-filter-row">
            <slot name="filters" />
            <label class="mail-query">
                <span class="control-label">{{ t('Search messages') }}</span>
                <AppIcon name="search" />
                <input
                    v-shortcut="'mail.search'"
                    v-model="query"
                    type="search"
                    maxlength="160"
                    :placeholder="t('Search messages')"
                />
            </label>
            <details
                class="mail-view-options"
                :open="!desktop"
                @keydown.esc="closeOptions"
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
                    <label v-if="kind === 'echo'">{{ t('Conference panels') }}<select :value="workspace.value.value.conferencePanels" :disabled="busy || panelSaving" @change="setPanels(($event.target as HTMLSelectElement).value as 'all' | 'lastTwo')"><option value="all">{{ t('All panels') }}</option><option value="lastTwo">{{ t('Last two panels') }}</option></select></label>
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
        <slot name="search-status" />
    </div>
</template>
