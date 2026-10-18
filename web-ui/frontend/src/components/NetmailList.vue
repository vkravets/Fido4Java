<script setup lang="ts">
import { t } from '../i18n.ts';
import { mailId } from '../netmail.ts';
import type { MailItem } from '@rpc/bindings.mjs';
defineProps<{
    items: MailItem[];
    selected: string;
    busy: boolean;
    hasMore: boolean;
    outgoing?: boolean;
}>();
defineEmits<{ open: [item: MailItem]; more: [] }>();
</script>
<template>
    <section
        class="card mail-list"
        :aria-label="t('Messages')"
        :aria-busy="busy"
    >
        <p v-if="!items.length">
            {{ busy ? t('Loading messages…') : t('No messages in this folder.') }}
        </p>
        <button
            v-for="item in items"
            :key="mailId(item.id)"
            class="mail-row"
            :class="{ active: selected === mailId(item.id), unread: !item.read }"
            :disabled="busy"
            @click="$emit('open', item)"
        >
            <span class="mail-row-title">{{ item.subject || t('(No subject)') }}</span>
            <span>{{ outgoing ? item.toName : item.fromName }}</span>
            <small>{{ outgoing ? item.toAddress : item.fromAddress }} · {{ item.date }}</small>
            <small v-if="outgoing">{{
                (item.attributes || 0) & 8 ? t('Sent') : t('Queued')
            }}</small>
            <small v-else-if="!item.read">{{ t('Unread') }}</small>
        </button>
        <button
            v-if="hasMore"
            class="secondary"
            :disabled="busy"
            @click="$emit('more')"
        >
            {{ t('Load more') }}
        </button>
    </section>
</template>
