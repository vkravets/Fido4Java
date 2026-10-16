<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue';
import { api } from '../api.ts';
import { createPolling, startPollRefresh } from '../polling.ts';
import PollAttempts from './PollAttempts.vue';
import PollDetails from './PollDetails.vue';
import NotificationBanner from './NotificationBanner.vue';
import type { AppState } from '../state.ts';
import type { PollInfo } from '@rpc/bindings.mjs';

const props = defineProps<{ state: AppState }>();
const model = createPolling(api.ui, () => props.state.session.value);
const {
    queue,
    history,
    links,
    selected,
    files,
    lines,
    loaded,
    refreshing,
    hasOlder,
    before,
    hasMoreLogs,
    clipped
} = model;
const peer = ref('');
const automatic = ref(true);
const refreshError = ref('');
let stop = () => {};

onMounted(() => {
    stop = startPollRefresh(
        async () => {
            if ((!automatic.value && loaded.value) || document.hidden || props.state.busy.value)
                return;
            await refresh();
        },
        (failure) => {
            const reason = failure as { message?: string; code?: string };
            refreshError.value = reason?.message || 'Не удалось обновить соединения.';
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
    model.dispose();
});

async function refresh() {
    await model.refresh();
    refreshError.value = '';
    if (!peer.value && links.value.length) peer.value = links.value[0]!.address;
}
function start(address: string) {
    props.state.run(async () => {
        await model.start(address);
        props.state.notice.value = 'Полл запрошен.';
    });
}
function cancel(id: string) {
    props.state.run(async () => {
        await model.cancel(id);
        props.state.notice.value = 'Отмена запрошена. Исходящие файлы сохраняются.';
    });
}
function select(attempt: PollInfo) {
    props.state.run(() => model.select(attempt));
}
</script>

<template>
    <div class="page-heading">
        <span class="eyebrow">УПРАВЛЕНИЕ НОДОЙ</span>
        <h1>Соединения</h1>
        <p>Исходящие поллы: общая очередь планировщика и ручных запусков.</p>
    </div>
    <div class="actions refresh-controls">
        <button
            class="secondary"
            :disabled="state.busy.value || refreshing"
            @click="state.run(refresh)"
        >
            Обновить
        </button>
        <label class="inline-option"
            ><input
                v-model="automatic"
                type="checkbox"
            />
            Автообновление каждые 2 секунды</label
        >
    </div>
    <NotificationBanner
        :message="refreshError"
        kind="error"
    />
    <p
        v-if="!loaded"
        class="muted"
    >
        Загружаем очередь соединений…
    </p>
    <form
        class="card"
        @submit.prevent="start(peer)"
    >
        <h2>Ручной полл</h2>
        <label
            >Настроенный линк<select
                v-model="peer"
                required
                :disabled="state.busy.value || !links.length"
            >
                <option
                    v-for="link in links"
                    :key="link.address"
                    :value="link.address"
                >
                    {{ link.address }} · {{ link.host }}:{{ link.port || 24554 }}
                </option>
            </select></label
        >
        <p
            v-if="loaded && !links.length"
            class="muted"
        >
            Сначала настройте линк станции.
        </p>
        <button
            class="primary"
            :disabled="state.busy.value || !peer"
        >
            Полл сейчас
        </button>
        <p class="muted">Для линка с активной попыткой будет выбрано существующее задание.</p>
    </form>
    <section class="card">
        <h2>Активные и ожидающие · {{ queue.length }}</h2>
        <p
            v-if="loaded && !queue.length"
            class="muted"
        >
            Очередь пуста.
        </p>
        <PollAttempts
            v-if="queue.length"
            :attempts="queue"
            :busy="state.busy.value"
            :selected-id="selected?.id"
            @select="select"
            @cancel="cancel"
            @retry="start"
        />
    </section>
    <PollDetails
        v-if="selected"
        :attempt="selected"
        :lines="lines"
        :files="files"
        :clipped="clipped"
        :more="hasMoreLogs"
        :busy="state.busy.value"
        @cancel="cancel"
        @retry="start"
        @more="state.run(model.readSelected)"
    />
    <section class="card">
        <h2>История попыток</h2>
        <p
            v-if="loaded && !history.length"
            class="muted"
        >
            Попыток на этой странице нет.
        </p>
        <PollAttempts
            v-if="history.length"
            :attempts="history"
            :busy="state.busy.value"
            :selected-id="selected?.id"
            @select="select"
            @cancel="cancel"
            @retry="start"
        />
        <footer class="actions">
            <button
                v-if="before"
                class="secondary"
                :disabled="state.busy.value"
                @click="state.run(model.newest)"
            >
                К новым попыткам
            </button>
            <button
                v-if="hasOlder"
                class="secondary"
                :disabled="state.busy.value"
                @click="state.run(model.older)"
            >
                Более ранние
            </button>
        </footer>
    </section>
</template>

<style scoped>
.inline-option {
    flex-direction: row;
    align-items: center;
    margin: 0;
}
.refresh-controls {
    margin-bottom: 24px;
    flex-wrap: wrap;
}
footer {
    margin-top: 20px;
}
</style>
