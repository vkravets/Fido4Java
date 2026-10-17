<script setup lang="ts">
import { t } from '../i18n.ts';
import { ref, watch } from 'vue';
import { pollTime } from '../polling.ts';
import { quarantineReason } from '../quarantine.ts';
import type { QuarantineItem, QuarantinePage } from '@rpc/bindings.mjs';
const props = defineProps<{
    page: QuarantinePage;
    selected: QuarantineItem | null;
    action: 'retry' | 'archive';
    peer: string;
    busy: boolean;
}>();
const confirmation = ref<HTMLFormElement | null>(null);
watch(
    () => props.selected?.id,
    () => {
        confirmation.value?.scrollIntoView({ block: 'center' });
    },
    { flush: 'post' }
);
defineEmits<{
    select: [item: QuarantineItem, action: 'retry' | 'archive'];
    confirm: [];
    cancel: [];
    refresh: [];
    next: [];
    'update:peer': [value: string];
}>();
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
        <article
            v-for="item in page.items"
            :key="item.id"
            class="card"
        >
            <h3>{{ item.name }}</h3>
            <p>
                {{ item.peer || t('Unknown original session') }} · {{ String(item.bytes) }}
                {{ t('bytes') }}
            </p>
            <p>{{ quarantineReason(item.reason) }}</p>
            <p>{{ t('Modified') }}: {{ pollTime(item.modified) }}</p>
            <p>{{ t('Retry attempts') }}: {{ item.attempts }}</p>
            <p v-if="item.attempts">{{ t('Last retry') }}: {{ pollTime(item.lastAttempt) }}</p>
            <div class="actions">
                <button
                    type="button"
                    class="secondary"
                    :disabled="busy || !item.checksum"
                    @click="$emit('select', item, 'retry')"
                >
                    {{ t('Release and retry') }}
                </button>
                <button
                    type="button"
                    class="secondary"
                    :disabled="busy || !item.checksum"
                    @click="$emit('select', item, 'archive')"
                >
                    {{ t('Archive packet') }}
                </button>
            </div>
        </article>
        <form
            v-if="selected"
            ref="confirmation"
            class="card"
            @submit.prevent="$emit('confirm')"
        >
            <h3>
                {{ action === 'retry' ? t('Confirm packet retry') : t('Confirm packet archival') }}
            </h3>
            <p>{{ selected.name }}</p>
            <p>
                {{
                    t(
                        'The original is preserved. Archival does not import messages. Retained inbound files may be queued again.'
                    )
                }}
            </p>
            <label v-if="action === 'retry' && !selected.peer">
                {{ t('Expected source link') }}
                <select
                    :value="peer"
                    required
                    :disabled="busy"
                    @change="$emit('update:peer', ($event.target as HTMLSelectElement).value)"
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
            <p v-if="action === 'retry' && selected.peer">
                {{ t('Expected source link') }}: {{ selected.peer }}
            </p>
            <div class="actions">
                <button
                    class="primary"
                    :disabled="
                        busy || (action === 'retry' && !page.peers.includes(selected.peer || peer))
                    "
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
</style>
