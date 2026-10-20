<script setup lang="ts">
import { t } from '../i18n.ts';
import { computed } from 'vue';
import { matchesMail, useMailSearch } from '../mail-search.ts';
import { mailId } from '../netmail.ts';
import type { MailItem } from '@rpc/bindings.mjs';
const props = defineProps<{
    items: MailItem[];
    selected: string;
    busy: boolean;
    hasMore: boolean;
    outgoing?: boolean;
}>();
const query = useMailSearch();
const visibleItems = computed(() => props.items.filter((item) => matchesMail(item, query.value)));
defineEmits<{ open: [item: MailItem]; more: [] }>();
</script>
<template>
    <section
        class="card mail-list"
        :aria-label="t('Messages')"
        :aria-busy="busy"
    >
        <p v-if="!visibleItems.length">
            {{ busy ? t('Loading messages…') : t('No messages in this folder.') }}
        </p>
        <button
            v-for="item in visibleItems"
            :key="mailId(item.id)"
            class="mail-row"
            :class="{ active: selected === mailId(item.id), unread: !item.read }"
            :disabled="busy"
            @click="$emit('open', item)"
        >
            <span class="mail-row-title">{{ item.subject || t('(No subject)') }}</span>
            <span
                class="mail-excerpt"
                v-if="item.excerpt || item.body"
                >{{ item.excerpt || item.body?.slice(0, 160) }}</span
            >
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
