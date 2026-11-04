<script setup lang="ts">
import { visibleKludge } from '../mail-kludges.ts';
defineProps<{ kludges: string[]; label: string }>();
</script>

<template>
    <details
        v-if="kludges.length"
        class="mail-kludge-list"
    >
        <summary>{{ label }}</summary>
        <ul>
            <li
                v-for="(kludge, index) in kludges"
                :key="index"
            >
                <code>{{ visibleKludge(kludge) }}</code>
            </li>
        </ul>
    </details>
</template>

<style scoped>
.mail-kludge-list ul {
    padding-inline-start: 2rem;
}
.mail-kludge-list li {
    overflow-wrap: anywhere;
}
.mail-kludge-list code {
    font-family: var(--font-mono, monospace);
    white-space: pre-wrap;
    overflow-wrap: anywhere;
    user-select: text;
}
</style>
