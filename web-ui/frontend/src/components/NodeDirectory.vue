<script setup lang="ts">
import { inject, onMounted, onUnmounted, ref, watch } from 'vue';
import { api } from '../api.ts';
import { createDirectorySearch, directorySearchKey } from '../directory.ts';
import { t } from '../i18n.ts';
import DirectoryPanel from './DirectoryPanel.vue';
import CompactPage from './CompactPage.vue';
import SettingsRefreshButton from './SettingsRefreshButton.vue';
import NotificationBanner from './NotificationBanner.vue';
const query = ref('');
const error = ref('');
const waiting = ref(false);
const search = inject(directorySearchKey, (query, offset, limit, includePoints) =>
    api.settings.searchDirectory(query, offset, limit, includePoints)
);
const reader = createDirectorySearch(search);
const { items, busy, hasMore, offset, nextOffset } = reader;
let timer: ReturnType<typeof setTimeout> | undefined;
async function load(start = 0) {
    clearTimeout(timer);
    waiting.value = false;
    error.value = '';
    try {
        await reader.load(query.value, start);
    } catch {
        error.value = t('Directory search is unavailable. Please try again.');
    }
}
watch(query, () => {
    clearTimeout(timer);
    waiting.value = true;
    error.value = '';
    reader.clear();
    timer = setTimeout(() => void load(), 300);
});
onMounted(() => void load());
onUnmounted(() => {
    clearTimeout(timer);
    reader.dispose();
});
</script>
<template>
    <CompactPage :title="t('Node directory')">
        <template #actions
            ><SettingsRefreshButton
                :busy="busy || waiting"
                :context="t('Node directory')"
                @refresh="load()"
        /></template>
        <NotificationBanner
            :message="error"
            kind="error"
        />
        <DirectoryPanel
            v-model="query"
            :items="items"
            :busy="busy || waiting"
            :has-more="hasMore"
            :offset="offset"
            @search="load()"
            @next="load(nextOffset)"
            @previous="load(Math.max(0, offset - 25))"
        />
    </CompactPage>
</template>
