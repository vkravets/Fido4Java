<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { api, bindings } from '../api.ts';
import { t } from '../i18n.ts';
import type { AppState } from '../state.ts';
import type {
    NetmailRouteInfo,
    NetmailRoutePreview,
    NetmailRoutesConfiguration
} from '@rpc/bindings.mjs';
import NetmailRoutingPolicyForm from './NetmailRoutingPolicyForm.vue';
import NetmailRoutesPanel from './NetmailRoutesPanel.vue';
import NetmailRoutePreviewForm from './NetmailRoutePreviewForm.vue';
const props = defineProps<{ state: AppState }>();
const configuration = ref<NetmailRoutesConfiguration | null>(null);
const result = ref<NetmailRoutePreview | null>(null);
const applied = ref('');
const dirty = computed(
    () => configuration.value && JSON.stringify(configuration.value.policy) !== applied.value
);
let source = '';
async function load() {
    configuration.value = await api.ui.netmailRoutes(props.state.session.value);
    source = configuration.value.policy.mode;
    applied.value = JSON.stringify(configuration.value.policy);
    result.value = null;
}
async function savePolicy() {
    if (!configuration.value) return;
    if (
        source !== configuration.value.policy.mode &&
        !window.confirm(t('Changing the rule source does not copy rules. Continue?'))
    )
        return;
    await api.settings.saveNetmailRouting(
        props.state.session.value,
        new bindings.NetmailRoutingPolicy(configuration.value.policy)
    );
    await load();
    props.state.notice.value = t('Routing policy applied.');
}
async function saveRule(rule: NetmailRouteInfo) {
    await api.ui.saveNetmailRoute(props.state.session.value, new bindings.NetmailRouteInfo(rule));
    await load();
    props.state.notice.value = t('Routing rule saved.');
}
async function remove(id: string) {
    if (!window.confirm(t('Delete this routing rule?'))) return;
    await api.ui.deleteNetmailRoute(props.state.session.value, id);
    await load();
}
async function preview(destination: string, sender: string, direct: boolean) {
    result.value = null;
    result.value = await api.ui.previewNetmailRoute(
        props.state.session.value,
        destination,
        sender,
        direct
    );
}
onMounted(() => props.state.run(load));
</script>
<template>
    <header class="page-heading">
        <p class="eyebrow">{{ t('NODE MANAGEMENT') }}</p>
        <h1>{{ t('Netmail routing') }}</h1>
    </header>
    <template v-if="configuration">
        <NetmailRoutingPolicyForm
            v-model="configuration.policy"
            :peers="configuration.peers"
            :busy="state.busy.value"
            @save="state.run(savePolicy)"
        />
        <p
            v-if="dirty"
            class="muted"
        >
            {{ t('Save policy changes before editing rules or checking routes.') }}
        </p>
        <NetmailRoutesPanel
            :rules="configuration.rules"
            :peers="configuration.peers"
            :writable="source === 'database'"
            :busy="state.busy.value || !!dirty"
            @save="(rule) => state.run(() => saveRule(rule))"
            @remove="(id) => state.run(() => remove(id))"
        />
        <NetmailRoutePreviewForm
            :peers="configuration.peers"
            :result="result"
            :busy="state.busy.value || !!dirty"
            @invalidate="result = null"
            @preview="
                (destination, sender, direct) =>
                    state.run(() => preview(destination, sender, direct))
            "
        />
    </template>
</template>
