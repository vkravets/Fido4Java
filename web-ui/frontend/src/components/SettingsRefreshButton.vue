<script setup lang="ts">
import { t } from '../i18n.ts';
import AppIcon from './AppIcon.vue';
defineProps<{ busy?: boolean; context?: string }>();
defineEmits<{ refresh: [] }>();
</script>
<template>
    <button
        type="button"
        class="secondary settings-icon-action"
        :disabled="busy"
        :title="context ? `${context}: ${t('Refresh')}` : t('Refresh')"
        :aria-label="t('Refresh')"
        @click="$emit('refresh')"
    >
        <AppIcon name="refresh" />
    </button>
</template>
