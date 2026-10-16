<script setup lang="ts">
import { t } from '../i18n.ts';
import { nextTick, ref, watch } from 'vue';
import { activePoll, pollState, pollTime } from '../polling.ts';
import type { PollInfo, QueueItem } from '@rpc/bindings.mjs';
const props = defineProps<{
    attempt: PollInfo;
    lines: string[];
    files: QueueItem[];
    clipped: boolean;
    more: boolean;
    busy: boolean;
}>();
const emit = defineEmits<{ more: []; cancel: [id: string]; retry: [address: string] }>();
const follow = ref(true);
const output = ref<HTMLElement | null>(null);
watch(
    () => props.lines.at(-1),
    async () => {
        if (!follow.value) return;
        await nextTick();
        if (output.value) output.value.scrollTop = output.value.scrollHeight;
    }
);
</script>

<template>
    <section class="card">
        <div class="section-title">
            <h2>{{ t('Poll {address}', { address: attempt.address }) }}</h2>
            <span class="badge">{{ pollState(attempt.state) }}</span>
        </div>
        <dl>
            <dt>{{ t('ID') }}</dt>
            <dd class="mono">{{ attempt.id }}</dd>
            <dt>{{ t('Source') }}</dt>
            <dd>{{ attempt.source === 'SCHEDULED' ? t('Scheduler') : t('Administrator') }}</dd>
            <dt>{{ t('Started') }}</dt>
            <dd>{{ pollTime(attempt.started) }}</dd>
            <dt>{{ t('Finished') }}</dt>
            <dd>{{ pollTime(attempt.finished) }}</dd>
        </dl>
        <p v-if="attempt.detail">{{ attempt.detail }}</p>
        <div class="actions">
            <button
                v-if="activePoll(attempt.state)"
                class="secondary danger"
                :disabled="busy"
                @click="emit('cancel', attempt.id)"
            >
                {{ t('Cancel poll') }}
            </button>
            <button
                v-else
                class="secondary"
                :disabled="busy"
                @click="emit('retry', attempt.address)"
            >
                {{ t('Retry poll') }}
            </button>
        </div>
        <h3>{{ t('Attempt log') }}</h3>
        <label class="inline-option"
            ><input
                v-model="follow"
                type="checkbox"
            />
            {{ t('Follow new events') }}</label
        >
        <p
            v-if="clipped"
            class="muted"
        >
            {{ t('The browser shows the latest 1000 loaded lines.') }}
        </p>
        <pre
            ref="output"
            class="poll-log"
            tabindex="0"
            :aria-label="t('Selected poll log')"
            >{{ lines.length ? lines.join('\n') : t('No events yet.') }}</pre>
        <button
            v-if="more"
            class="secondary"
            :disabled="busy"
            @click="emit('more')"
        >
            {{ t('Load more events') }}
        </button>
        <h3>{{ t('Prepared files for this link') }}</h3>
        <p class="muted">
            {{
                t(
                    'Up to 50 files in the outbound spool. This queue is separate from connection jobs; new messages may still be waiting to be packed.'
                )
            }}
        </p>
        <p
            v-if="!files.length"
            class="muted"
        >
            {{ t('No prepared files.') }}
        </p>
        <ul
            v-else
            class="rows"
        >
            <li
                v-for="file in files"
                :key="`${file.kind}:${file.name}`"
            >
                <span class="mono">{{ file.name }}</span
                ><span>{{
                    t('{bytes} bytes · {kind}', { bytes: String(file.bytes), kind: file.kind })
                }}</span>
            </li>
        </ul>
    </section>
</template>

<style scoped>
.poll-log {
    background: var(--field);
    color: var(--text);
    border: 1px solid var(--line);
    padding: 16px;
    border-radius: 8px;
    max-height: 28rem;
    overflow: auto;
    white-space: pre-wrap;
    overflow-wrap: anywhere;
    font-size: 12px;
}
dd {
    overflow-wrap: anywhere;
}
.inline-option {
    flex-direction: row;
    align-items: center;
}
.rows li {
    gap: 16px;
    flex-wrap: wrap;
    overflow-wrap: anywhere;
}
h3 {
    margin-top: 28px;
}
</style>
