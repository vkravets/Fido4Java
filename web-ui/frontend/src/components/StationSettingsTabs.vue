<script setup lang="ts">
import { computed } from 'vue';
import SettingsWorkspace from './SettingsWorkspace.vue';
import ConfigurationReloadButton from './ConfigurationReloadButton.vue';
import { t } from '../i18n.ts';
import { useMedia } from '../media.ts';
import { stationSections as groups } from '../station-navigation.ts';
import type { StationSection } from '../station-navigation.ts';
const narrow = useMedia('(max-width: 700px)');

const active = defineModel<StationSection>({ default: 'general' });
defineProps<{ busy?: boolean }>();
defineEmits<{ reload: [] }>();
const title = computed(() => t(groups.find((group) => group.id === active.value)!.label));
function navigate(event: KeyboardEvent) {
    const index = groups.findIndex((group) => group.id === active.value);
    const targets: Record<string, number> = {
        ArrowRight: (index + 1) % groups.length,
        ArrowDown: (index + 1) % groups.length,
        ArrowLeft: (index + groups.length - 1) % groups.length,
        ArrowUp: (index + groups.length - 1) % groups.length,
        Home: 0,
        End: groups.length - 1
    };
    const next = targets[event.key];
    if (next === undefined) return;
    event.preventDefault();
    active.value = groups[next]!.id;
    const buttons = (event.currentTarget as HTMLElement).querySelectorAll<HTMLButtonElement>(
        '[role="tab"]'
    );
    buttons[next]?.focus();
}
</script>
<template>
    <SettingsWorkspace
        :context="t('Station')"
        :title="title"
    >
        <template #actions
            ><ConfigurationReloadButton
                :busy="busy"
                @reload="$emit('reload')"
        /></template>
        <template #navigation>
            <nav
                class="tabs station-tabs"
                role="tablist"
                :aria-orientation="narrow ? 'horizontal' : 'vertical'"
                :aria-label="t('Station settings')"
                @keydown="navigate"
            >
                <button
                    v-for="group in groups"
                    :id="`station-tab-${group.id}`"
                    :key="group.id"
                    type="button"
                    role="tab"
                    :class="{ selected: active === group.id }"
                    :aria-selected="active === group.id"
                    aria-controls="station-settings-panel"
                    :tabindex="active === group.id ? 0 : -1"
                    :disabled="busy"
                    @click="active = group.id"
                >
                    {{ t(group.label) }}
                </button>
            </nav>
        </template>
        <div
            id="station-settings-panel"
            role="tabpanel"
            :aria-labelledby="`station-tab-${active}`"
            tabindex="0"
        >
            <slot :group="active" />
        </div>
    </SettingsWorkspace>
</template>
