<script setup lang="ts">
import { ref, watch } from 'vue';

const props = withDefaults(
    defineProps<{
        message: string;
        kind?: 'info' | 'warning' | 'error';
    }>(),
    { kind: 'info' }
);
const dismissed = ref(false);
watch(
    () => [props.message, props.kind],
    () => {
        dismissed.value = false;
    }
);
</script>

<template>
    <Teleport
        to="#notifications"
        defer
    >
        <div
            v-if="message && !dismissed"
            :class="['message', kind]"
            :role="kind === 'error' ? 'alert' : 'status'"
        >
            <span class="message-text">{{ message }}</span>
            <button
                v-if="kind !== 'error'"
                type="button"
                class="message-close"
                :aria-label="kind === 'warning' ? 'Закрыть предупреждение' : 'Закрыть уведомление'"
                @click="dismissed = true"
            >
                <span aria-hidden="true">×</span>
            </button>
        </div>
    </Teleport>
</template>
