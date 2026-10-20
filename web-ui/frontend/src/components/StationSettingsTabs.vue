<script setup lang="ts">
import { t } from '../i18n.ts';
import { useMedia } from '../media.ts';
import { stationSections as groups } from '../station-navigation.ts';
import type { StationSection } from '../station-navigation.ts';
const narrow = useMedia('(max-width: 700px)');

const active = defineModel<StationSection>({ default: 'general' });
defineProps<{ busy?: boolean }>();
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
    <div class="settings-workspace">
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
        <div
            id="station-settings-panel"
            role="tabpanel"
            :aria-labelledby="`station-tab-${active}`"
            tabindex="0"
        >
            <slot :group="active" />
        </div>
    </div>
</template>
<style scoped>
.settings-workspace {
    display: grid;
    grid-template-columns: 200px minmax(0, 1fr);
    gap: 20px;
    align-items: start;
}
.station-tabs {
    flex-direction: column;
    gap: 4px;
    border: 0;
    position: sticky;
    top: 12px;
}
.station-tabs button {
    width: 100%;
    text-align: left;
    white-space: normal;
    border: 0;
    padding: 12px;
    border-radius: 5px;
}
.station-tabs button.selected {
    background: var(--accent-soft);
    box-shadow: inset 3px 0 var(--accent);
}
@media (max-width: 700px) {
    .settings-workspace {
        grid-template-columns: minmax(0, 1fr);
    }
    .station-tabs {
        position: static;
        flex-direction: row;
        flex-wrap: wrap;
    }
    .station-tabs button {
        width: auto;
    }
}
</style>
