<script setup lang="ts">
import { t } from '../i18n.ts';
import { computed, ref, watch } from 'vue';
import { pollTime } from '../polling.ts';
import { quarantineReason } from '../quarantine.ts';
import type { QuarantineItem, QuarantinePage } from '@rpc/bindings.mjs';
const props = withDefaults(
    defineProps<{
        page: QuarantinePage;
        selected: QuarantineItem | null;
        action: 'retry' | 'archive';
        peer: string;
        busy: boolean;
        checked?: string[];
        peers?: Record<string, string>;
        batch?: QuarantineItem[];
        outcomes?: { id: string; name: string; accepted: boolean; detail: string }[];
    }>(),
    { checked: () => [], peers: () => ({}), batch: () => [], outcomes: () => [] }
);
const eligible = computed(() => props.page.items.filter((item) => item.checksum));
const pending = computed(() =>
    props.batch.length ? props.batch : props.selected ? [props.selected] : []
);
const valid = computed(() =>
    pending.value.every((item) =>
        props.page.peers.includes(
            item.peer || props.peers[item.id] || (props.batch.length ? '' : props.peer)
        )
    )
);
const confirmation = ref<HTMLFormElement | null>(null);
watch(
    () => pending.value.map((item) => item.id).join(','),
    () => {
        confirmation.value?.scrollIntoView({ block: 'center' });
    },
    { flush: 'post' }
);
const emit = defineEmits<{
    select: [item: QuarantineItem, action: 'retry' | 'archive'];
    confirm: [];
    cancel: [];
    refresh: [];
    next: [];
    'update:peer': [value: string];
    'update:checked': [value: string[]];
    'update:peers': [value: Record<string, string>];
    selectBatch: [];
}>();
function toggle(id: string, enabled: boolean) {
    emit(
        'update:checked',
        enabled ? [...props.checked, id] : props.checked.filter((value) => value !== id)
    );
}
function setPeer(id: string, value: string) {
    emit('update:peers', { ...props.peers, [id]: value });
}
</script>
<template>
    <section class="card">
        <h2>{{ t('Packet quarantine') }}</h2>
        <p>
            {{
                t(
                    'Fix the rejection reason before retrying. Accepted messages are not imported again. Originals are retained.'
                )
            }}
        </p>
        <div class="actions">
            <button
                type="button"
                class="primary"
                :disabled="busy || !checked.length"
                @click="$emit('selectBatch')"
            >
                {{ t('Restore selected') }} ({{ checked.length }})
            </button>
            <button
                type="button"
                class="secondary"
                :disabled="busy"
                @click="$emit('refresh')"
            >
                {{ t('Refresh') }}
            </button>
            <button
                v-if="page.hasMore"
                type="button"
                class="secondary"
                :disabled="busy"
                @click="$emit('next')"
            >
                {{ t('Next page') }}
            </button>
        </div>
        <p v-if="!page.items.length">{{ t('No quarantined packets.') }}</p>
        <p
            v-if="page.items.length"
            class="muted"
        >
            {{ t('Selection applies to this page only.') }}
        </p>
        <div
            v-if="page.items.length"
            class="quarantine-table"
            tabindex="0"
            :aria-label="t('Packet quarantine')"
        >
            <table>
                <thead>
                    <tr>
                        <th>
                            <input
                                type="checkbox"
                                :aria-label="t('Select all on this page')"
                                :checked="
                                    !!eligible.length &&
                                    eligible.every((item) => checked.includes(item.id))
                                "
                                :indeterminate="
                                    checked.length > 0 && checked.length < eligible.length
                                "
                                :disabled="busy || !!pending.length || !eligible.length"
                                @change="
                                    emit(
                                        'update:checked',
                                        ($event.target as HTMLInputElement).checked
                                            ? eligible.map((item) => item.id)
                                            : []
                                    )
                                "
                            />
                        </th>
                        <th>{{ t('Packet') }}</th>
                        <th>{{ t('Expected source link') }}</th>
                        <th>{{ t('Reason') }}</th>
                        <th>{{ t('Actions') }}</th>
                    </tr>
                </thead>
                <tbody>
                    <tr
                        v-for="item in page.items"
                        :key="item.id"
                    >
                        <td>
                            <input
                                type="checkbox"
                                :aria-label="t('Select packet {name}', { name: item.name })"
                                :checked="checked.includes(item.id)"
                                :disabled="busy || !!pending.length || !item.checksum"
                                @change="
                                    toggle(item.id, ($event.target as HTMLInputElement).checked)
                                "
                            />
                        </td>
                        <td class="packet-name">
                            <strong>{{ item.name }}</strong>
                            <p>{{ String(item.bytes) }} {{ t('bytes') }}</p>
                            <p>{{ t('Modified') }}: {{ pollTime(item.modified) }}</p>
                            <p>{{ t('Retry attempts') }}: {{ item.attempts }}</p>
                            <p v-if="item.attempts">
                                {{ t('Last retry') }}: {{ pollTime(item.lastAttempt) }}
                            </p>
                        </td>
                        <td>
                            <span v-if="item.peer">{{ item.peer }}</span>
                            <label v-else
                                >{{ t('Expected source link') }}
                                <select
                                    :value="peers[item.id] || ''"
                                    :disabled="busy || !item.checksum"
                                    @change="
                                        setPeer(item.id, ($event.target as HTMLSelectElement).value)
                                    "
                                >
                                    <option value="">
                                        {{ t('Select an expected source link.') }}
                                    </option>
                                    <option
                                        v-for="address in page.peers"
                                        :key="address"
                                        :value="address"
                                    >
                                        {{ address }}
                                    </option>
                                </select>
                            </label>
                        </td>
                        <td>{{ quarantineReason(item.reason) }}</td>
                        <td>
                            <div class="actions">
                                <button
                                    type="button"
                                    class="secondary"
                                    :disabled="busy || !!pending.length || !item.checksum"
                                    @click="$emit('select', item, 'retry')"
                                >
                                    {{ t('Release and retry') }}
                                </button>
                                <button
                                    type="button"
                                    class="secondary"
                                    :disabled="busy || !!pending.length || !item.checksum"
                                    @click="$emit('select', item, 'archive')"
                                >
                                    {{ t('Archive packet') }}
                                </button>
                            </div>
                        </td>
                    </tr>
                </tbody>
            </table>
        </div>
        <details
            v-if="outcomes.length"
            open
        >
            <summary>{{ t('Batch results') }}</summary>
            <ul>
                <li
                    v-for="outcome in outcomes"
                    :key="outcome.id"
                >
                    <strong>{{ outcome.name }}</strong
                    >: {{ outcome.accepted ? t('Accepted') : t('Failed') }} —
                    {{ quarantineReason(outcome.detail) }}
                </li>
            </ul>
        </details>
        <form
            v-if="pending.length"
            ref="confirmation"
            class="card"
            @submit.prevent="$emit('confirm')"
        >
            <h3>
                {{ action === 'retry' ? t('Confirm packet retry') : t('Confirm packet archival') }}
            </h3>
            <p v-if="batch.length">{{ t('Selected packets: {count}', { count: batch.length }) }}</p>
            <p v-else>{{ selected?.name }}</p>
            <p>
                {{
                    t(
                        'The original is preserved. Archival does not import messages. Retained inbound files may be queued again.'
                    )
                }}
            </p>
            <label
                v-for="item in action === 'retry' ? pending.filter((entry) => !entry.peer) : []"
                :key="item.id"
            >
                {{ item.name }} — {{ t('Expected source link') }}
                <select
                    :aria-label="t('Expected source link')"
                    :value="peers[item.id] || (batch.length ? '' : peer)"
                    required
                    :disabled="busy"
                    @change="setPeer(item.id, ($event.target as HTMLSelectElement).value)"
                >
                    <option value="">{{ t('Select an expected source link.') }}</option>
                    <option
                        v-for="address in page.peers"
                        :key="address"
                        :value="address"
                    >
                        {{ address }}
                    </option>
                </select>
            </label>
            <p v-if="action === 'retry' && selected?.peer">
                {{ t('Expected source link') }}: {{ selected.peer }}
            </p>
            <p v-if="action === 'retry' && !valid">
                {{ t('Select an expected source link for every packet before confirming.') }}
            </p>
            <div class="actions">
                <button
                    class="primary"
                    :disabled="busy || (action === 'retry' && !valid)"
                >
                    {{ t('Confirm') }}
                </button>
                <button
                    type="button"
                    class="secondary"
                    :disabled="busy"
                    @click="$emit('cancel')"
                >
                    {{ t('Cancel') }}
                </button>
            </div>
        </form>
    </section>
</template>

<style scoped>
section {
    overflow-wrap: anywhere;
}
.quarantine-table {
    overflow-x: auto;
    margin-block: 1rem;
}
table {
    width: 100%;
    border-collapse: collapse;
    min-width: 760px;
}
th,
td {
    text-align: left;
    vertical-align: top;
    padding: 0.75rem;
    border-bottom: 1px solid var(--line);
}
.packet-name {
    min-width: 180px;
    max-width: 270px;
}
td p {
    margin: 0.35rem 0;
}
td select {
    min-width: 180px;
}
input[type='checkbox'] {
    width: 1.15rem;
    height: 1.15rem;
}
</style>
