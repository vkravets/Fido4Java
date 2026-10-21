<script setup lang="ts">
import { t } from '../i18n.ts';
import { onMounted, reactive, ref } from 'vue';
import { api, bindings } from '../api.ts';
import NodeForm from './NodeForm.vue';
import StationSettingsHeader from './StationSettingsHeader.vue';
import StationSettingsTabs from './StationSettingsTabs.vue';
import MailSettings from './MailSettings.vue';
import EchoSettings from './EchoSettings.vue';
import QuarantineSettings from './QuarantineSettings.vue';
import NotificationBanner from './NotificationBanner.vue';
import PluginSettings from './PluginSettings.vue';
import NetmailRoutingView from './NetmailRoutingView.vue';
import Connections from './Connections.vue';
import RobotAccess from './RobotAccess.vue';
import AccountModeration from './AccountModeration.vue';
import type { StationSection } from '../station-navigation.ts';
import type { NodeConfiguration } from '@rpc/bindings.mjs';
import type { AppState } from '../state.ts';
const props = defineProps<{ state: AppState; initialSection?: 'general' | 'links' }>();
const section = ref<StationSection>(props.initialSection || 'general');
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
        saved.value = true;
    });
    if (saved.value) mailVersion.value++;
}
async function reload() {
    await props.state.run(async () => {
        saved.value = false;
        await api.settings.reload(props.state.session.value);
        Object.assign(model, await api.settings.current(props.state.session.value));
        saved.value = true;
        mailVersion.value++;
    });
}
</script>

<template>
    <StationSettingsHeader
        :busy="state.busy.value"
        @reload="reload"
    />
    <NotificationBanner
        v-if="saved"
        kind="info"
        :message="
            t(
                'Configuration applied. New operations use the new settings; existing connections keep their session settings.'
            )
        "
    />
    <StationSettingsTabs
        v-model="section"
        v-slot="{ group }"
        :busy="state.busy.value"
    >
        <NetmailRoutingView
            v-if="group === 'routing'"
            :state="state"
        />
        <Connections
            v-if="group === 'logs'"
            initial-tab="log"
            :state="state"
        />
        <RobotAccess
            v-if="group === 'robots'"
            :state="state"
        />
        <AccountModeration
            v-if="group === 'accounts'"
            :state="state"
        />
        <PluginSettings
            v-if="group === 'plugins'"
            :state="state"
        />
        <form
            v-if="model.aliases"
            v-show="group === 'general'"
            class="card"
            @submit.prevent="save"
        >
            <NodeForm
                :model="model"
                :initial="false"
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
            :group="group === 'links' || group === 'tosser' ? group : 'hidden'"
            :key="mailVersion"
            :state="state"
            :local-addresses="[model.address, ...model.aliases]"
            @saved="mailSaved"
            @saving="saved = false"
        />
        <div v-show="group === 'echoes'">
            <EchoSettings
                v-if="ready"
                :key="'echo-' + mailVersion"
                :state="state"
            />
        </div>
        <div v-show="group === 'quarantine'">
            <QuarantineSettings
                v-if="ready"
                :state="state"
            />
        </div>
    </StationSettingsTabs>
</template>
