<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { api, bindings } from '../api.ts';
import NodeForm from './NodeForm.vue';
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
        await api.settings.save(props.state.session.value, new bindings.NodeConfiguration(model));
        model.linkPassword = '';
        saved.value = true;
        props.state.notice.value =
            'Конфигурация сохранена. Перезапустите ноду, чтобы применить изменения.';
    });
}
</script>

<template>
    <div class="page-heading">
        <span class="eyebrow">УПРАВЛЕНИЕ НОДОЙ</span>
        <h1>Настройки станции</h1>
        <p>Основной линк и адреса станции. Другие линки сохраняются.</p>
    </div>
    <p
        v-if="saved"
        class="notice"
    >
        Ожидается перезапуск ноды. Текущие соединения продолжают использовать прежнюю конфигурацию.
    </p>
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
                Сохранить конфигурацию
            </button>
        </footer>
    </form>
</template>
