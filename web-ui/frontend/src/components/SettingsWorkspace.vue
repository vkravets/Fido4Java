<script setup lang="ts">
import { computed, nextTick, onMounted, provide, ref, watch } from 'vue';
import { settingsWorkspaceKey } from '../settings-workspace.ts';
import SettingsPageHeader from './SettingsPageHeader.vue';
const props = defineProps<{ context: string; title: string }>();
const actions = ref<HTMLElement | null>(null);
const navigation = ref<HTMLElement | null>(null);
provide(settingsWorkspaceKey, { title: computed(() => props.title), actions });

function revealSelectedTab() {
    const tabs = navigation.value?.querySelector<HTMLElement>('.tabs');
    const selected = tabs?.querySelector<HTMLElement>('[aria-selected="true"]');
    if (!tabs || !selected || tabs.scrollWidth <= tabs.clientWidth) return;
    const viewport = tabs.getBoundingClientRect();
    const item = selected.getBoundingClientRect();
    if (item.left < viewport.left) tabs.scrollLeft -= viewport.left - item.left;
    else if (item.right > viewport.right) tabs.scrollLeft += item.right - viewport.right;
}
onMounted(revealSelectedTab);
watch(
    () => props.title,
    () => nextTick(revealSelectedTab),
    { flush: 'post' }
);
</script>
<template>
    <div class="settings-page">
        <SettingsPageHeader
            :context="context"
            :title="title"
        >
            <div
                ref="actions"
                class="settings-action-outlet"
            >
                <slot name="actions" />
            </div>
        </SettingsPageHeader>
        <aside
            ref="navigation"
            class="settings-navigation"
        >
            <slot name="navigation" />
        </aside>
        <div class="settings-body"><slot /></div>
    </div>
</template>
