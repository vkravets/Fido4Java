<script setup lang="ts">
import { t } from '../i18n.ts';
defineProps<{ busy: boolean }>();
defineEmits<{ reload: [] }>();
</script>

<template>
    <div class="page-heading">
        <span class="eyebrow">{{ t('NODE MANAGEMENT') }}</span>
        <h1>{{ t('Station settings') }}</h1>
        <p>{{ t('Station identity, mail links and tosser processing settings.') }}</p>
        <button
            type="button"
            class="secondary"
            :disabled="busy"
            @click="$emit('reload')"
        >
            {{ t('Reload configuration files') }}
        </button>
    </div>
</template>
