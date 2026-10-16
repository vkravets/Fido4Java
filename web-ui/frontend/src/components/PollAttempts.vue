<script setup lang="ts">
import { t } from '../i18n.ts';
import { activePoll, pollState, pollTime } from '../polling.ts';
import type { PollInfo } from '@rpc/bindings.mjs';
defineProps<{ attempts: PollInfo[]; busy: boolean; selectedId?: string }>();
const emit = defineEmits<{
    select: [attempt: PollInfo];
    cancel: [id: string];
    retry: [address: string];
}>();
</script>

<template>
    <div class="attempts-scroll">
        <table>
            <thead>
                <tr>
                    <th>{{ t('Link') }}</th>
                    <th>{{ t('Trigger') }}</th>
                    <th>{{ t('Status') }}</th>
                    <th>{{ t('Queued at') }}</th>
                    <th>{{ t('Actions') }}</th>
                </tr>
            </thead>
            <tbody>
                <tr
                    v-for="attempt in attempts"
                    :key="attempt.id"
                    :class="{ selected: selectedId === attempt.id }"
                >
                    <td class="mono">{{ attempt.address }}</td>
                    <td>{{ attempt.source === 'SCHEDULED' ? t('Scheduled') : t('Manual') }}</td>
                    <td>
                        <span
                            class="badge"
                            :class="{ failed: ['FAILED', 'TIMED_OUT'].includes(attempt.state) }"
                            >{{ pollState(attempt.state) }}</span
                        >
                    </td>
                    <td>{{ pollTime(attempt.created) }}</td>
                    <td>
                        <div class="actions">
                            <button
                                class="secondary"
                                :disabled="busy"
                                @click="emit('select', attempt)"
                            >
                                {{ t('Log') }}
                            </button>
                            <button
                                v-if="activePoll(attempt.state)"
                                class="secondary danger"
                                :disabled="busy"
                                @click="emit('cancel', attempt.id)"
                            >
                                {{ t('Cancel') }}
                            </button>
                            <button
                                v-else
                                class="secondary"
                                :disabled="busy"
                                @click="emit('retry', attempt.address)"
                            >
                                {{ t('Retry') }}
                            </button>
                        </div>
                    </td>
                </tr>
            </tbody>
        </table>
    </div>
</template>

<style scoped>
.attempts-scroll {
    overflow-x: auto;
}
table {
    width: 100%;
    border-collapse: collapse;
    font-size: 13px;
}
th,
td {
    padding: 12px 10px;
    text-align: left;
    border-bottom: 1px solid var(--line);
}
th {
    color: var(--muted);
    font-weight: 500;
}
.selected {
    background: var(--accent-soft);
}
.failed {
    color: var(--danger);
}
.actions {
    flex-wrap: wrap;
}
</style>
