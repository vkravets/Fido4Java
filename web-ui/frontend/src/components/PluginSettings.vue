<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { api } from '../api.ts';
import { t } from '../i18n.ts';
import PluginSettingsPanel from './PluginSettingsPanel.vue';
import type { AppState } from '../state.ts';
import type { ExtensionSettings } from '@rpc/bindings.mjs';

const props = defineProps<{ state: AppState }>();
const extensions = ref<ExtensionSettings[]>([]);
async function refresh() {
    extensions.value = await api.ui.extensions(props.state.session.value);
}
onMounted(() => props.state.run(refresh));
function save(id: string, section: string, values: Record<string, string>) {
    props.state.run(async () => {
        await api.ui.saveExtension(props.state.session.value, id, section, values);
        await refresh();
        props.state.notice.value = t('Plugin settings applied. No restart required.');
    });
}
</script>
<template>
    <PluginSettingsPanel
        :extensions="extensions"
        :busy="state.busy.value"
        @save="save"
    />
</template>
