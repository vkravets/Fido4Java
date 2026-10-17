<script setup lang="ts">
import { t } from '../i18n.ts';

type Group = 'general' | 'links' | 'tosser' | 'echoes' | 'quarantine';
const active = defineModel<Group>({ default: 'general' });
defineProps<{ busy?: boolean }>();
const groups = [
    { id: 'general', label: 'General' },
    { id: 'links', label: 'Mail links' },
    { id: 'tosser', label: 'Tosser settings' },
    { id: 'echoes', label: 'Echo areas and link access' },
    { id: 'quarantine', label: 'Packet quarantine' }
] as const;
function navigate(event: KeyboardEvent) {
    const index = groups.findIndex((group) => group.id === active.value);
    const targets: Record<string, number> = {
        ArrowRight: (index + 1) % groups.length,
        ArrowLeft: (index + groups.length - 1) % groups.length,
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
    <nav
        class="tabs station-tabs"
        role="tablist"
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
    <div
        id="station-settings-panel"
        role="tabpanel"
        :aria-labelledby="`station-tab-${active}`"
        tabindex="0"
    >
        <slot :group="active" />
    </div>
</template>
<style scoped>
.station-tabs {
    flex-wrap: wrap;
}
</style>
