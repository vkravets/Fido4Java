<script setup lang="ts">
import { inject } from 'vue';
import { settingsWorkspaceKey } from '../settings-workspace.ts';
const workspace = inject(settingsWorkspaceKey, undefined);
withDefaults(defineProps<{ active?: boolean }>(), { active: true });
</script>
<template>
    <Teleport
        v-if="active && workspace?.actions.value"
        :to="workspace.actions.value"
        ><slot
    /></Teleport>
    <div
        v-else-if="!workspace || !active"
        class="settings-local-actions"
    >
        <slot />
    </div>
</template>
