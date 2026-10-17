<script setup lang="ts">
import type { MailLinkConfiguration } from '@rpc/bindings.mjs';
import { t } from '../i18n.ts';
import MailLinkForm from './MailLinkForm.vue';

defineProps<{
    links: MailLinkConfiguration[];
    existingCount: number;
    busy: boolean;
    initial?: boolean;
}>();
defineEmits<{ add: []; remove: [index: number] }>();
</script>

<template>
    <fieldset
        v-for="(link, index) in links"
        :key="index"
        class="mail-link-settings"
        :disabled="busy"
    >
        <legend>{{ link.address || t('New link') }}</legend>
        <MailLinkForm
            :model="link"
            :existing="index < existingCount"
            :station-akas="initial"
        />
        <button
            v-if="index >= existingCount && (!initial || links.length > 1)"
            type="button"
            class="secondary"
            @click="$emit('remove', index)"
        >
            {{ t('Cancel') }}
        </button>
    </fieldset>
    <button
        type="button"
        class="secondary"
        :disabled="busy || links.length >= 1024"
        @click="$emit('add')"
    >
        {{ t('Add link') }}
    </button>
</template>

<style scoped>
.mail-link-settings {
    min-width: 0;
    border: 1px solid var(--line);
    border-radius: 8px;
    padding: 20px;
    margin: 24px 0;
}
.mail-link-settings legend {
    padding: 0 8px;
    color: var(--text);
}
</style>
