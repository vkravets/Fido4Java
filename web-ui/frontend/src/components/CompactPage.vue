<script setup lang="ts">
import { inject } from 'vue';
import { settingsWorkspaceKey } from '../settings-workspace.ts';
import SettingsWorkspace from './SettingsWorkspace.vue';
import SettingsPageActions from './SettingsPageActions.vue';
defineProps<{ title: string }>();
const workspace = inject(settingsWorkspaceKey, undefined);
</script>
<template>
    <template v-if="workspace">
        <SettingsPageActions v-if="$slots.actions"><slot name="actions" /></SettingsPageActions>
        <slot />
    </template>
    <SettingsWorkspace
        v-else
        :title="title"
    >
        <template #actions><slot name="actions" /></template>
        <slot />
    </SettingsWorkspace>
</template>
