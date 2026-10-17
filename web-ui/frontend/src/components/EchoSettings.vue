<script setup lang="ts">
import { onMounted } from 'vue';
import { api } from '../api.ts';
import { createEchoEditor } from '../echo-access.ts';
import { t } from '../i18n.ts';
import EchoAccessPanel from './EchoAccessPanel.vue';
import type { AppState } from '../state.ts';

const props = defineProps<{ state: AppState }>();
const editor = createEchoEditor(api.ui, () => props.state.session.value);
const { configuration, editing, pendingDelete, model } = editor;
async function load() {
    await editor.refresh();
    editor.reset();
}
// Initial reads can run alongside the station's other settings cards. state.run
// deliberately rejects concurrent actions, so using it here can skip this load.
onMounted(() =>
    load().catch((failure: unknown) => {
        const reason = failure as { message?: string } | null;
        props.state.error.value = reason?.message || t('The request failed. Please try again.');
    })
);
function save() {
    props.state.run(async () => {
        await editor.save();
        props.state.notice.value = t('Echo access saved. No restart required.');
    });
}
function remove() {
    props.state.run(async () => {
        await editor.remove();
        props.state.notice.value = t('Echo access revoked. Stored messages were retained.');
    });
}
</script>

<template>
    <section
        v-if="!configuration"
        class="card"
    >
        <h2>{{ t('Echo areas and link access') }}</h2>
        <button
            type="button"
            class="secondary"
            :disabled="state.busy.value"
            @click="state.run(load)"
        >
            {{ t('Refresh') }}
        </button>
    </section>
    <EchoAccessPanel
        v-if="configuration"
        :configuration="configuration"
        :model="model"
        :editing="editing"
        :pending-delete="pendingDelete"
        :busy="state.busy.value"
        @refresh="state.run(editor.refresh)"
        @save="save"
        @remove="remove"
        @reset="editor.reset"
        @edit="editor.edit"
        @request-delete="pendingDelete = $event"
    />
</template>
