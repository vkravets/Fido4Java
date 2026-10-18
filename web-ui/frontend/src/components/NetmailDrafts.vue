<script setup lang="ts">
import { t } from '../i18n.ts';
import type { MailDraft } from '@rpc/bindings.mjs';
defineProps<{ drafts: MailDraft[]; busy: boolean }>();
defineEmits<{ edit: [draft: MailDraft]; remove: [draft: MailDraft] }>();
</script>
<template>
    <section
        class="card"
        :aria-label="t('Drafts')"
    >
        <p v-if="!drafts.length">{{ t('No saved drafts.') }}</p>
        <div
            v-for="draft in drafts"
            :key="draft.id"
            class="mail-draft-row"
        >
            <button
                class="text-button"
                :disabled="busy"
                @click="$emit('edit', draft)"
            >
                {{ draft.subject || t('(No subject)') }} · {{ draft.toAddress }}
            </button>
            <button
                class="secondary"
                :disabled="busy"
                @click="$emit('remove', draft)"
            >
                {{ t('Delete draft') }}
            </button>
        </div>
    </section>
</template>
