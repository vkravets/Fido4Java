<script setup lang="ts">
import AppIcon from './AppIcon.vue';
import { t } from '../i18n.ts';
defineProps<{ count: number; busy: boolean }>();
defineEmits<{ send: [] }>();
</script>
<template>
    <section
        class="mail-queue-control"
        :aria-label="t('Outgoing mail queue')"
    >
        <p role="status">{{ t('Awaiting send: {count}', { count }) }}</p>
        <button
            v-shortcut="'nav.sendQueue'"
            class="queue-send-button"
            :aria-label="t('Send queued mail')"
            :disabled="busy || count === 0"
            :title="t('Send queued mail')"
            @click="$emit('send')"
        >
            <AppIcon name="send" />{{ t('Send queued mail') }}
        </button>
    </section>
</template>
