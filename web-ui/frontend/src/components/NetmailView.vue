<script setup lang="ts">
import { computed, inject, onMounted, onBeforeUnmount, ref, shallowRef } from 'vue';
import AppIcon from './AppIcon.vue';
import MailSearchStatus from './MailSearchStatus.vue';
import { createArchiveSearch } from '../archive-search.ts';
import { t } from '../i18n.ts';
import { forwardDraft } from '../mail-drafts.ts';
import { useMailReading } from '../mail-reading.ts';
import { createNetmail, mailId, netmailClientKey } from '../netmail.ts';
import { api, bindings } from '../api.ts';
import type { AppState } from '../state.ts';
import type { MailFolder } from '../netmail.ts';
import type { MailDraft, MailItem } from '@rpc/bindings.mjs';
import NetmailList from './NetmailList.vue';
import NetmailDrafts from './NetmailDrafts.vue';
import NetmailMessage from './NetmailMessage.vue';
import MailComposer from './MailComposer.vue';
import MailWorkspace from './MailWorkspace.vue';
const props = defineProps<{
    state: AppState;
    initialFolder?: MailFolder;
    initialMessage?: string;
    initialDraft?: MailDraft;
}>();
const emit = defineEmits<{ queueChanged: []; openEchoDraft: [draft: MailDraft] }>();
const client = inject(netmailClientKey, api.ui);
const reader = createNetmail(
    props.state,
    client,
    (value) => new bindings.MailDraft(value),
    () => emit('queueChanged')
);
const navigationItems = shallowRef<MailItem[]>([]);
const query = ref('');
const mailSearch = createArchiveSearch(
    props.state.session,
    computed(() =>
        reader.folder.value === 'drafts'
            ? null
            : { mailbox: reader.folder.value === 'outbox' ? (3 as const) : (2 as const), area: '' }
    ),
    query,
    reader.items,
    client,
    (failure) => {
        void props.state.run(
            async () => {
                throw failure;
            },
            { background: true }
        );
    }
);
async function loadMore() {
    if (mailSearch.remote.value) await mailSearch.loadMore();
    else await props.state.run(() => reader.load(true));
}
defineExpose({ canLeave: reader.discard, refresh });
const {
    pending: opening,
    open: openMessage,
    cancel: cancelRead
} = useMailReading(props.state, reader, reader.folder);
const folders = ['inbox', 'outbox', 'drafts'] as const;
const labels = { inbox: 'Inbox', outbox: 'Outbox', drafts: 'Drafts' } as const;
onMounted(() =>
    props.state.run(async () => {
        await reader.navigate(props.initialFolder || 'inbox');
        if (props.initialDraft) {
            reader.edit(props.initialDraft);
            return;
        }
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
    cancelRead();
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
async function refresh() {
    await reader.load();
    if (mailSearch.active.value) await mailSearch.refresh();
}
async function removeMessage() {
    await reader.removeMessage();
    if (mailSearch.remote.value) await mailSearch.refresh();
}
function reply(kind: 'quoted' | 'reply' = 'quoted') {
    reader.edit(undefined, reader.selected.value!);
    if (reader.draft.value) reader.draft.value.composeKind = kind;
}
const nextUnread = computed(() =>
    navigationItems.value.find(
        (item) => !item.read && mailId(item.id) !== mailId(reader.selected.value?.id)
    )
);
const index = computed(() =>
    navigationItems.value.findIndex((item) => mailId(item.id) === mailId(reader.selected.value?.id))
);
</script>
<template>
    <MailWorkspace
        v-model:query="query"
        kind="netmail"
        :selected="reader.selected.value ? mailId(reader.selected.value.id) : undefined"
        :opened="!!reader.selected.value || !!reader.draft.value"
        :composing="!!reader.draft.value"
        :busy="state.busy.value"
        @back="back"
    >
        <template #search-status>
            <MailSearchStatus
                :active="mailSearch.active.value"
                :remote="mailSearch.remote.value"
                :pending="mailSearch.pending.value"
                :failure="mailSearch.failure.value"
                :has-more="mailSearch.hasMore.value"
            />
        </template>
        <template #toolbar>
            <header class="page-heading">
                <div>
                    <p class="eyebrow">{{ t('MAIL') }}</p>
                    <h1>{{ reader.folder.value === 'drafts' ? t('Drafts') : t('Netmail') }}</h1>
                </div>
                <div class="actions">
                    <button
                        class="primary"
                        :disabled="state.busy.value || !state.profile.value?.allowedAkas.length"
                        v-shortcut="'mail.new'"
                        @click="reader.edit()"
                    >
                        <AppIcon name="edit" />{{ t('New netmail') }}
                    </button>
                    <button
                        class="secondary"
                        :aria-label="t('Refresh')"
                        :title="t('Refresh')"
                        :disabled="state.busy.value"
                        v-shortcut="'page.refresh'"
                        @click="state.run(refresh)"
                    >
                        <AppIcon name="refresh" /><span class="desktop-hidden">{{
                            t('Refresh')
                        }}</span>
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
        </template>
        <template #list>
            <NetmailDrafts
                v-if="reader.folder.value === 'drafts'"
                :drafts="reader.drafts.value"
                :busy="state.busy.value"
                @edit="editDraft"
                @remove="(draft) => state.run(() => reader.remove(draft))"
            />

            <NetmailList
                v-else
                :items="mailSearch.items.value"
                :search-results="mailSearch.remote.value"
                :searching="mailSearch.pending.value"
                :search-failed="mailSearch.failure.value"
                :selected="reader.selected.value ? mailId(reader.selected.value.id) : ''"
                :busy="state.busy.value"
                :has-more="
                    mailSearch.remote.value ? mailSearch.hasMore.value : reader.hasMore.value
                "
                :outgoing="reader.folder.value === 'outbox'"
                @navigation="navigationItems = $event"
                @open="openMessage"
                @more="loadMore"
            />
        </template>
        <MailComposer
            v-if="reader.draft.value"
            :key="reader.draft.value.id"
            v-model="reader.draft.value"
            :addresses="state.profile.value?.allowedAkas || []"
            :busy="state.busy.value || opening"
            @initialized="reader.initializeDraft"
            @save="state.run(reader.save)"
            @send="state.run(reader.send)"
            @cancel="cancel"
        />
        <NetmailMessage
            v-else-if="reader.selected.value"
            :message="reader.selected.value"
            :busy="state.busy.value"
            :pending="opening"
            :reply="reader.folder.value === 'inbox'"
            :forwardable="!!state.profile.value?.allowedAkas.length"
            @forward="reader.edit(forwardDraft(reader.selected.value!, state.profile.value!))"
            @reply="reply"
            @compose="reader.edit()"
            :navigation="true"
            :previous-disabled="index <= 0"
            :next-disabled="index < 0 || index >= navigationItems.length - 1"
            :unread-disabled="!nextUnread"
            @previous="openMessage(navigationItems[index - 1]!)"
            @next="openMessage(navigationItems[index + 1]!)"
            @unread="nextUnread && openMessage(nextUnread)"
            @remove="state.run(removeMessage)"
        />
        <section
            v-else
            class="card muted mail-reader-empty"
        >
            <p>{{ t('Select a message to read.') }}</p>
        </section>
    </MailWorkspace>
</template>
