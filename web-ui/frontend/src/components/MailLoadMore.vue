<script setup lang="ts">
import { t } from '../i18n.ts';
import AppIcon from './AppIcon.vue';

defineProps<{ disabled?: boolean }>();
defineEmits<{ more: [] }>();
</script>
<template>
    <button
        type="button"
        class="text-button mail-load-more"
        :disabled="disabled"
        @click="$emit('more')"
    >
        <span class="mail-load-more-label">{{ t('Load more') }}</span>
        <span
            class="mail-load-more-icon"
            aria-hidden="true"
            ><AppIcon name="expand"
        /></span>
    </button>
</template>
<style scoped>
.mail-load-more {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    min-height: 32px;
    margin: 8px 12px;
    padding: 4px 0;
    border: 0;
    border-radius: 4px;
    color: var(--accent);
}
.mail-load-more-icon {
    display: grid;
    place-items: center;
    width: 24px;
    height: 24px;
    border: 1px solid currentColor;
    border-radius: 4px;
}
.mail-load-more-icon .app-icon {
    transform: rotate(90deg);
}
.mail-load-more:not(:disabled):hover {
    background: none;
}
.mail-load-more:not(:disabled):hover .mail-load-more-label {
    text-decoration: underline;
    text-underline-offset: 3px;
}
.mail-load-more:not(:disabled):hover .mail-load-more-icon {
    background: var(--accent-soft);
}
@media (max-width: 700px) {
    .mail-load-more {
        margin-inline: 0;
        min-height: 40px;
    }
}
</style>
