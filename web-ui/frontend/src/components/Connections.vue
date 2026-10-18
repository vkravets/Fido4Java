<script setup lang="ts">
import { t } from '../i18n.ts';
import { onMounted, onUnmounted, ref, watch } from 'vue';
import { api } from '../api.ts';
import { createPolling, startPollRefresh } from '../polling.ts';
import PollAttempts from './PollAttempts.vue';
import PollDetails from './PollDetails.vue';
import NotificationBanner from './NotificationBanner.vue';
import BinkpLogSettings from './BinkpLogSettings.vue';
import ConnectionTabs from './ConnectionTabs.vue';
import type { AppState } from '../state.ts';
import type { PollInfo } from '@rpc/bindings.mjs';

const props = defineProps<{ state: AppState }>();
const tab = ref<'polls' | 'log'>('polls');
const logOpened = ref(false);
watch(tab, (value) => {
    if (value === 'log') logOpened.value = true;
});
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
            if (
                tab.value !== 'polls' ||
                (!automatic.value && loaded.value) ||
                document.hidden ||
                props.state.busy.value
            )
                return;
            await refresh();
        },
        (failure) => {
            const reason = failure as { message?: string; code?: string };
            refreshError.value = reason?.message || t('Could not refresh connections.');
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
        props.state.notice.value = t('Poll requested.');
    });
}
function cancel(id: string) {
    props.state.run(async () => {
        await model.cancel(id);
        props.state.notice.value = t('Cancellation requested. Outbound files are retained.');
    });
}
function select(attempt: PollInfo) {
    props.state.run(() => model.select(attempt));
}
</script>

<template>
    <div class="page-heading">
        <span class="eyebrow">{{ t('NODE MANAGEMENT') }}</span>
        <h1>{{ t('Connections') }}</h1>
        <p>{{ t('Outgoing polls: one queue for scheduled and manual attempts.') }}</p>
    </div>
    <ConnectionTabs v-model="tab">
        <template #log>
            <BinkpLogSettings
                v-if="logOpened"
                :state="state"
                :active="tab === 'log'"
            />
        </template>
        <template #polls>
            <div class="actions refresh-controls">
                <button
                    class="secondary"
                    :disabled="state.busy.value || refreshing"
                    @click="state.run(refresh)"
                >
                    {{ t('Refresh') }}
                </button>
                <label class="inline-option"
                    ><input
                        v-model="automatic"
                        type="checkbox"
                    />
                    {{ t('Refresh automatically every 2 seconds') }}</label
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
                {{ t('Loading the connection queue…') }}
            </p>
            <form
                class="card"
                @submit.prevent="start(peer)"
            >
                <h2>{{ t('Manual poll') }}</h2>
                <label
                    >{{ t('Configured link')
                    }}<select
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
                    {{ t('Configure a station link first.') }}
                </p>
                <button
                    class="primary"
                    :disabled="state.busy.value || !peer"
                >
                    {{ t('Poll now') }}
                </button>
                <p class="muted">
                    {{
                        t(
                            'If the link already has an active attempt, the existing job will be selected.'
                        )
                    }}
                </p>
            </form>
            <section class="card">
                <h2>{{ t('Active and queued · {count}', { count: queue.length }) }}</h2>
                <p
                    v-if="loaded && !queue.length"
                    class="muted"
                >
                    {{ t('The queue is empty.') }}
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
                <h2>{{ t('Attempt history') }}</h2>
                <p
                    v-if="loaded && !history.length"
                    class="muted"
                >
                    {{ t('No attempts on this page.') }}
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
                        {{ t('Latest attempts') }}
                    </button>
                    <button
                        v-if="hasOlder"
                        class="secondary"
                        :disabled="state.busy.value"
                        @click="state.run(model.older)"
                    >
                        {{ t('Older attempts') }}
                    </button>
                </footer>
            </section>
        </template>
    </ConnectionTabs>
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
