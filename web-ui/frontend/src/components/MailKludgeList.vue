<script setup lang="ts">
defineProps<{ kludges: string[]; label: string }>();

function visibleKludge(kludge: string) {
    return kludge.replace(/[\u0000-\u001f\u007f-\u009f]/g, (character) => {
        const code = character.charCodeAt(0);
        if (code <= 0x1f) return `^${String.fromCharCode(code + 64)}`;
        if (code === 0x7f) return '^?';
        return `\\x${code.toString(16).toUpperCase().padStart(2, '0')}`;
    });
}
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
