<script setup lang="ts">
import { computed, provide, ref } from 'vue';
import { settingsWorkspaceKey } from '../settings-workspace.ts';
import SettingsPageHeader from './SettingsPageHeader.vue';
const props = defineProps<{ context: string; title: string }>();
const actions = ref<HTMLElement | null>(null);
provide(settingsWorkspaceKey, { title: computed(() => props.title), actions });
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
        <aside class="settings-navigation"><slot name="navigation" /></aside>
        <div class="settings-body"><slot /></div>
    </div>
</template>
