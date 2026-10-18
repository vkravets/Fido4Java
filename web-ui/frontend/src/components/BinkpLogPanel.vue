<script setup lang="ts">
import { t } from '../i18n.ts';
import LogOutput from './LogOutput.vue';
defineProps<{
    lines: string[];
    busy: boolean;
    clipped: boolean;
    more: boolean;
    rotated: boolean;
}>();
defineEmits<{ refresh: []; more: [] }>();
</script>
<template>
    <section class="card">
        <h2>{{ t('Binkp log') }}</h2>
        <p>
            {{
                t(
                    'Incoming and outgoing connections: commands, file transfers and acknowledgements. Passwords and file contents are not shown.'
                )
            }}
        </p>
        <button
            class="secondary"
            :disabled="busy"
            @click="$emit('refresh')"
        >
            {{ t('Show latest events') }}
        </button>
        <p
            v-if="rotated"
            class="muted"
        >
            {{ t('The log was rotated. Showing the new file.') }}
        </p>
        <LogOutput
            :lines="lines"
            :busy="busy"
            :clipped="clipped"
            :more="more"
            :label="t('Binkp log')"
            @more="$emit('more')"
        />
    </section>
</template>
