<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue';
import { api } from '../api.ts';
import { createNodeLog } from '../node-log.ts';
import { startPollRefresh } from '../polling.ts';
import { t } from '../i18n.ts';
import type { AppState } from '../state.ts';
import BinkpLogPanel from './BinkpLogPanel.vue';
import NotificationBanner from './NotificationBanner.vue';
const props = defineProps<{ state: AppState; active: boolean }>();
const reader = createNodeLog(api.ui, () => props.state.session.value);
const { lines, busy, clipped, more, rotated } = reader;
const error = ref('');
let stop = () => {};
async function read() {
    await reader.read();
    error.value = '';
}
onMounted(() => {
    stop = startPollRefresh(
        async () => {
            if (props.active && !document.hidden && !props.state.busy.value) await read();
        },
        (failure) => {
            const reason = failure as { message?: string; code?: string };
            error.value = reason?.message || t('Could not refresh connections.');
            if (['UNAUTHORIZED', 'SESSION_EXPIRED'].includes(reason?.code || '')) {
                void props.state.run(async () => {
                    throw failure;
                });
            }
        }
    );
});
onUnmounted(() => {
    stop();
    reader.dispose();
});
</script>
<template>
    <NotificationBanner
        :message="error"
        kind="error"
    />
    <BinkpLogPanel
        :lines="lines"
        :busy="busy || state.busy.value"
        :clipped="clipped"
        :more="more"
        :rotated="rotated"
        @refresh="
            state.run(async () => {
                await reader.latest();
                error = '';
            })
        "
        @more="state.run(read)"
    />
</template>
