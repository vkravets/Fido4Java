<script setup lang="ts">
import { visibleKludge } from '../mail-kludges.ts';
defineProps<{ kludges: string[]; label: string; listOnly?: boolean }>();
</script>

<template>
    <details
        v-if="kludges.length && !listOnly"
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
    <section
        v-else-if="kludges.length && listOnly"
        class="mail-kludge-list mail-kludge-list-expanded"
        :aria-label="label"
    >
        <ul>
            <li
                v-for="(kludge, index) in kludges"
                :key="index"
            >
                <code>{{ visibleKludge(kludge) }}</code>
            </li>
        </ul>
    </section>
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
