<script setup lang="ts">
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
                {{ kludge }}
            </li>
        </ul>
    </details>
</template>
