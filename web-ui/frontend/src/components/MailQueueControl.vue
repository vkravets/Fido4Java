<script setup lang="ts">
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
            :disabled="busy || count === 0"
            @click="$emit('send')"
        >
            {{ t('Send queued mail') }}
        </button>
    </section>
</template>
