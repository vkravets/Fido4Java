<script setup lang="ts">
import { computed, inject } from 'vue';
import { settingsWorkspaceKey } from '../settings-workspace.ts';
import SettingsPageActions from './SettingsPageActions.vue';
const props = defineProps<{ title: string; description?: string; page?: boolean }>();
const workspace = inject(settingsWorkspaceKey, undefined);
const showTitle = computed(
    () => !workspace || (!props.page && props.title !== workspace.title.value)
);
</script>
<template>
    <div
        v-if="showTitle || description || $slots.actions"
        class="settings-section-header"
    >
        <div>
            <component
                :is="page && !workspace ? 'h1' : 'h2'"
                v-if="showTitle"
                >{{ title }}</component
            >
            <p v-if="description">{{ description }}</p>
        </div>
        <SettingsPageActions
            v-if="$slots.actions"
            :active="!!workspace && title === workspace.title.value"
            ><slot name="actions"
        /></SettingsPageActions>
    </div>
</template>
