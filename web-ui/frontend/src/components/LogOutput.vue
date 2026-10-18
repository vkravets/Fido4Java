<script setup lang="ts">
import { nextTick, ref, watch } from 'vue';
import { t } from '../i18n.ts';
const props = defineProps<{
    lines: string[];
    label: string;
    clipped: boolean;
    more: boolean;
    busy: boolean;
}>();
defineEmits<{ more: [] }>();
const follow = ref(true);
const output = ref<HTMLElement | null>(null);
watch(
    () => props.lines.at(-1),
    async () => {
        if (!follow.value) return;
        await nextTick();
        if (output.value) output.value.scrollTop = output.value.scrollHeight;
    }
);
</script>
<template>
    <label class="inline-option"
        ><input
            v-model="follow"
            type="checkbox"
        />{{ t('Follow new events') }}</label
    >
    <p
        v-if="clipped"
        class="muted"
    >
        {{ t('The browser shows the latest 1000 loaded lines.') }}
    </p>
    <pre
        ref="output"
        class="poll-log"
        tabindex="0"
        :aria-label="label"
        >{{ lines.length ? lines.join('\n') : t('No events yet.') }}</pre>
    <button
        v-if="more"
        class="secondary"
        :disabled="busy"
        @click="$emit('more')"
    >
        {{ t('Load more events') }}
    </button>
</template>
<style scoped>
.poll-log {
    background: var(--field);
    color: var(--text);
    border: 1px solid var(--line);
    padding: 16px;
    border-radius: 8px;
    max-height: 28rem;
    overflow: auto;
    white-space: pre-wrap;
    overflow-wrap: anywhere;
    font-size: 12px;
}
.inline-option {
    flex-direction: row;
    align-items: center;
}
</style>
