<script setup lang="ts">
import { t } from '../i18n.ts';
import LogOutput from './LogOutput.vue';
import { activePoll, pollState, pollTime } from '../polling.ts';
import type { PollInfo, QueueItem } from '@rpc/bindings.mjs';
defineProps<{
    attempt: PollInfo;
    lines: string[];
    files: QueueItem[];
    clipped: boolean;
    more: boolean;
    busy: boolean;
}>();
const emit = defineEmits<{ more: []; cancel: [id: string]; retry: [address: string] }>();
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
        <p class="muted">
            {{
                t(
                    'Commands and file transfer events are recorded below; passwords and file contents are omitted.'
                )
            }}
        </p>
        <LogOutput
            :lines="lines"
            :label="t('Selected poll log')"
            :clipped="clipped"
            :more="more"
            :busy="busy"
            @more="emit('more')"
        />
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
dd {
    overflow-wrap: anywhere;
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
