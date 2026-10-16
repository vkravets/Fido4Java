<script setup lang="ts">
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
            <h2>Полл {{ attempt.address }}</h2>
            <span class="badge">{{ pollState(attempt.state) }}</span>
        </div>
        <dl>
            <dt>Идентификатор</dt>
            <dd class="mono">{{ attempt.id }}</dd>
            <dt>Источник</dt>
            <dd>{{ attempt.source === 'SCHEDULED' ? 'Планировщик' : 'Администратор' }}</dd>
            <dt>Начало</dt>
            <dd>{{ pollTime(attempt.started) }}</dd>
            <dt>Завершение</dt>
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
                Отменить полл
            </button>
            <button
                v-else
                class="secondary"
                :disabled="busy"
                @click="emit('retry', attempt.address)"
            >
                Повторить полл
            </button>
        </div>
        <h3>Журнал попытки</h3>
        <label class="inline-option"
            ><input
                v-model="follow"
                type="checkbox"
            />
            Прокручивать к новым событиям</label
        >
        <p
            v-if="clipped"
            class="muted"
        >
            В браузере показаны последние 1000 загруженных строк.
        </p>
        <pre
            ref="output"
            class="poll-log"
            tabindex="0"
            aria-label="Журнал выбранного полла"
            >{{ lines.length ? lines.join('\n') : 'Событий пока нет.' }}</pre>
        <button
            v-if="more"
            class="secondary"
            :disabled="busy"
            @click="emit('more')"
        >
            Загрузить следующие события
        </button>
        <h3>Подготовленные файлы для линка</h3>
        <p class="muted">
            До 50 файлов в исходящем спуле. Это отдельная очередь от заданий подключения; новые
            письма могут ещё ожидать упаковки.
        </p>
        <p
            v-if="!files.length"
            class="muted"
        >
            Подготовленных файлов нет.
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
                ><span>{{ file.bytes }} байт · {{ file.kind }}</span>
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
