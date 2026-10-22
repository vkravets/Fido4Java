<script setup lang="ts">
import { t } from '../i18n.ts';
defineProps<{
    active: boolean;
    remote: boolean;
    pending: boolean;
    failure: boolean;
    hasMore: boolean;
}>();
</script>
<template>
    <p
        v-if="active"
        class="mail-search-status"
        :class="{ message: failure, error: failure }"
        role="status"
        aria-live="polite"
    >
        {{
            failure
                ? t('Search failed. Use Load more to retry.')
                : pending
                  ? t('Searching messages…')
                  : remote
                    ? hasMore
                        ? t('Searching the archive. Load more to continue.')
                        : t('Archive search complete.')
                    : t('Searching loaded messages only.')
        }}
    </p>
</template>
<style scoped>
.mail-search-status {
    margin: 8px 0 0;
    color: var(--muted);
    font-size: 12px;
}
.mail-search-status.error {
    color: var(--danger);
}
</style>
