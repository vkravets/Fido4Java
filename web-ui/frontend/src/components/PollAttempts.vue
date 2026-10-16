<script setup lang="ts">
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
                    <th>Линк</th>
                    <th>Запуск</th>
                    <th>Состояние</th>
                    <th>Поставлен в очередь</th>
                    <th>Действия</th>
                </tr>
            </thead>
            <tbody>
                <tr
                    v-for="attempt in attempts"
                    :key="attempt.id"
                    :class="{ selected: selectedId === attempt.id }"
                >
                    <td class="mono">{{ attempt.address }}</td>
                    <td>{{ attempt.source === 'SCHEDULED' ? 'Плановый' : 'Ручной' }}</td>
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
                                Журнал
                            </button>
                            <button
                                v-if="activePoll(attempt.state)"
                                class="secondary danger"
                                :disabled="busy"
                                @click="emit('cancel', attempt.id)"
                            >
                                Отменить
                            </button>
                            <button
                                v-else
                                class="secondary"
                                :disabled="busy"
                                @click="emit('retry', attempt.address)"
                            >
                                Повторить
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
    background: var(--bg);
}
.failed {
    color: var(--danger);
}
.actions {
    flex-wrap: wrap;
}
</style>
