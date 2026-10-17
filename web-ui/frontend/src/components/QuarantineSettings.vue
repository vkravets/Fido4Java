<script setup lang="ts">
import { onMounted } from 'vue';
import { api } from '../api.ts';
import { createQuarantine } from '../quarantine.ts';
import { t } from '../i18n.ts';
import type { AppState } from '../state.ts';
import QuarantinePanel from './QuarantinePanel.vue';
import NotificationBanner from './NotificationBanner.vue';
const props = defineProps<{ state: AppState }>();
const editor = createQuarantine(api.ui, () => props.state.session.value);
const { page, selected, action, peer, result } = editor;
onMounted(() =>
    editor.refresh().catch((failure: unknown) => {
        props.state.error.value =
            (failure as Error)?.message || t('The request failed. Please try again.');
    })
);
</script>
<template>
    <NotificationBanner
        v-if="result"
        :kind="result.accepted ? 'info' : 'warning'"
        :message="result.detail"
    />
    <QuarantinePanel
        v-if="page"
        :page="page"
        :selected="selected"
        :action="action"
        v-model:peer="peer"
        :busy="state.busy.value"
        @select="editor.select"
        @cancel="selected = null"
        @confirm="state.run(editor.confirm)"
        @refresh="state.run(() => editor.refresh())"
        @next="state.run(() => editor.refresh(page!.nextCursor))"
    />
    <section
        v-else
        class="card"
    >
        <h2>{{ t('Packet quarantine') }}</h2>
        <button
            type="button"
            class="secondary"
            :disabled="state.busy.value"
            @click="state.run(() => editor.refresh())"
        >
            {{ t('Refresh') }}
        </button>
    </section>
</template>
