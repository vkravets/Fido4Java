<script setup lang="ts">
import { t } from '../i18n.ts';
import { onMounted, reactive, ref } from 'vue';
import { api, bindings } from '../api.ts';
import NodeForm from './NodeForm.vue';
import NotificationBanner from './NotificationBanner.vue';
import type { AppState } from '../state.ts';
const props = defineProps<{ state: AppState }>();
const model = reactive(new bindings.NodeConfiguration());
const saved = ref(false);
onMounted(() =>
    props.state.run(async () =>
        Object.assign(model, await api.settings.current(props.state.session.value))
    )
);
function save() {
    props.state.run(async () => {
        saved.value = false;
        await api.settings.save(props.state.session.value, new bindings.NodeConfiguration(model));
        model.linkPassword = '';
        saved.value = true;
    });
}
</script>

<template>
    <div class="page-heading">
        <span class="eyebrow">{{ t('NODE MANAGEMENT') }}</span>
        <h1>{{ t('Station settings') }}</h1>
        <p>{{ t('Main link and station addresses. Other links are retained.') }}</p>
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
</template>
