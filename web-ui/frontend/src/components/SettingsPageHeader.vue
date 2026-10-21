<script setup lang="ts">
defineProps<{ context?: string; title: string }>();
</script>
<template>
    <header class="settings-page-header">
        <div class="settings-breadcrumb">
            <span
                v-if="context"
                class="settings-context"
                >{{ context }}</span
            >
            <span
                v-if="context"
                class="settings-divider"
                aria-hidden="true"
                >/</span
            >
            <h1>{{ title }}</h1>
        </div>
        <div class="settings-header-actions"><slot /></div>
    </header>
</template>
