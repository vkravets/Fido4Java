<script setup lang="ts">
import { inject, onMounted, onBeforeUnmount } from 'vue';
import { t } from '../i18n.ts';
import { createNetmail, mailId, netmailClientKey } from '../netmail.ts';
import { api, bindings } from '../api.ts';
import type { AppState } from '../state.ts';
import type { MailFolder } from '../netmail.ts';
import type { MailDraft } from '@rpc/bindings.mjs';
import NetmailList from './NetmailList.vue';
import NetmailDrafts from './NetmailDrafts.vue';
import NetmailMessage from './NetmailMessage.vue';
import MailComposer from './MailComposer.vue';
import MailWorkspace from './MailWorkspace.vue';
const props = defineProps<{
    state: AppState;
    initialFolder?: MailFolder;
    initialMessage?: string;
}>();
const emit = defineEmits<{ queueChanged: []; openEchoDraft: [draft: MailDraft] }>();
const reader = createNetmail(
    props.state,
    inject(netmailClientKey, api.ui),
    (value) => new bindings.MailDraft(value),
    () => emit('queueChanged')
);
defineExpose({ canLeave: reader.discard, refresh: reader.load });
const folders = ['inbox', 'outbox', 'drafts'] as const;
const labels = { inbox: 'Inbox', outbox: 'Outbox', drafts: 'Drafts' } as const;
onMounted(() =>
    props.state.run(async () => {
        await reader.navigate(props.initialFolder || 'inbox');
        const item = reader.items.value.find((item) => mailId(item.id) === props.initialMessage);
        if (item) await reader.open(item);
    })
);
function beforeUnload(event: BeforeUnloadEvent) {
    if (reader.draft.value) event.preventDefault();
}
onMounted(() => window.addEventListener('beforeunload', beforeUnload));
onBeforeUnmount(() => window.removeEventListener('beforeunload', beforeUnload));
function back() {
    if (!reader.discard()) return;
    reader.draft.value = null;
    reader.selected.value = null;
}
function cancel() {
    if (reader.discard()) reader.draft.value = null;
}
function editDraft(value: MailDraft) {
    if (value.mailbox === 1) emit('openEchoDraft', value);
    else reader.edit(value);
}
</script>
<template>
    <header class="page-heading">
        <div>
            <p class="eyebrow">{{ t('MAIL') }}</p>
            <h1>{{ reader.folder.value === 'drafts' ? t('Drafts') : t('Netmail') }}</h1>
        </div>
        <div class="actions">
            <button
                class="primary"
                :disabled="state.busy.value || !state.profile.value?.allowedAkas.length"
                @click="reader.edit()"
            >
                {{ t('New netmail') }}
            </button>
            <button
                class="secondary"
                :disabled="state.busy.value"
                @click="state.run(() => reader.load())"
            >
                {{ t('Refresh') }}
            </button>
        </div>
    </header>
    <nav
        class="tabs"
        :aria-label="t('Mail folders')"
    >
        <button
            v-for="folder in folders"
            :key="folder"
            :class="{ selected: reader.folder.value === folder }"
            :aria-current="reader.folder.value === folder ? 'page' : undefined"
            :disabled="state.busy.value"
            @click="state.run(() => reader.navigate(folder))"
        >
            {{ t(labels[folder]) }}
        </button>
    </nav>
    <NetmailDrafts
        v-if="reader.folder.value === 'drafts' && !reader.draft.value"
        :drafts="reader.drafts.value"
        :busy="state.busy.value"
        @edit="editDraft"
        @remove="(draft) => state.run(() => reader.remove(draft))"
    />
    <MailWorkspace
        v-else
        kind="netmail"
        :opened="!!reader.selected.value || !!reader.draft.value"
        :busy="state.busy.value"
        @back="back"
    >
        <template #list>
            <NetmailList
                :items="reader.items.value"
                :selected="reader.selected.value ? mailId(reader.selected.value.id) : ''"
                :busy="state.busy.value"
                :has-more="reader.hasMore.value"
                :outgoing="reader.folder.value === 'outbox'"
                @open="(item) => state.run(() => reader.open(item))"
                @more="state.run(() => reader.load(true))"
            />
        </template>
        <MailComposer
            v-if="reader.draft.value"
            v-model="reader.draft.value"
            :addresses="state.profile.value?.allowedAkas || []"
            :busy="state.busy.value"
            @save="state.run(reader.save)"
            @send="state.run(reader.send)"
            @cancel="cancel"
        />
        <NetmailMessage
            v-else-if="reader.selected.value"
            :message="reader.selected.value"
            :busy="state.busy.value"
            :reply="reader.folder.value === 'inbox'"
            @reply="reader.edit(undefined, reader.selected.value!)"
            @remove="state.run(reader.removeMessage)"
        />
        <section
            v-else
            class="card muted"
        >
            <p>{{ t('Select a message to read.') }}</p>
        </section>
    </MailWorkspace>
</template>
