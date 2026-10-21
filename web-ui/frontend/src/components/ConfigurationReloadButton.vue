<script setup lang="ts">
import { t } from '../i18n.ts';
import AppIcon from './AppIcon.vue';
defineProps<{ busy?: boolean }>();
defineEmits<{ reload: [] }>();
</script>
<template>
    <button
        type="button"
        class="secondary settings-icon-action"
        :disabled="busy"
        :title="t('Reload configuration files')"
        :aria-label="t('Reload configuration files')"
        @click="$emit('reload')"
    >
        <AppIcon name="reloadConfig" />
    </button>
</template>
