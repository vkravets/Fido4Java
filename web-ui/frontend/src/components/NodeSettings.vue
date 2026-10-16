<script setup lang="ts">
import { t } from '../i18n.ts';
import { onMounted, reactive, ref } from 'vue';
import { api, bindings } from '../api.ts';
import NodeForm from './NodeForm.vue';
import MailSettings from './MailSettings.vue';
import NotificationBanner from './NotificationBanner.vue';
import type { NodeConfiguration } from '@rpc/bindings.mjs';
import type { AppState } from '../state.ts';
const props = defineProps<{ state: AppState }>();
const model = reactive(new bindings.NodeConfiguration());
const saved = ref(false);
const mailVersion = ref(0);
function mailSaved(configuration?: NodeConfiguration) {
    if (configuration) Object.assign(model, configuration);
    saved.value = true;
}
const ready = ref(false);
onMounted(async () => {
    await props.state.run(async () => {
        Object.assign(model, await api.settings.current(props.state.session.value));
    });
    ready.value = Boolean(model.aliases);
});
async function save() {
    await props.state.run(async () => {
        saved.value = false;
        await api.settings.save(props.state.session.value, new bindings.NodeConfiguration(model));
        model.linkPassword = '';
        saved.value = true;
    });
    if (saved.value) mailVersion.value++;
}
</script>

<template>
    <div class="page-heading">
        <span class="eyebrow">{{ t('NODE MANAGEMENT') }}</span>
        <h1>{{ t('Station settings') }}</h1>
        <p>{{ t('Station identity, mail links and tosser processing settings.') }}</p>
    </div>
    <NotificationBanner
        v-if="saved"
        kind="warning"
        :message="
            t(
                'Configuration saved. Restart the node to apply it. Current connections still use the previous configuration.'
            )
        "
    />
    <form
        v-if="model.aliases"
        class="card"
        @submit.prevent="save"
    >
        <NodeForm
            :model="model"
            :initial="false"
            :show-link="false"
        />
        <footer class="actions">
            <button
                class="primary"
                :disabled="state.busy.value"
            >
                {{ t('Save configuration') }}
            </button>
        </footer>
    </form>
    <MailSettings
        v-if="ready"
        :key="mailVersion"
        :state="state"
        :local-addresses="[model.address, ...model.aliases]"
        @saved="mailSaved"
    />
</template>
