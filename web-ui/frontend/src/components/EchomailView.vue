<script setup lang="ts">
import { computed, inject, onBeforeUnmount, onMounted, ref, shallowRef } from 'vue';
import AppIcon from './AppIcon.vue';
import MailSearchStatus from './MailSearchStatus.vue';
import { createArchiveSearch } from '../archive-search.ts';
import { t } from '../i18n.ts';
import { forwardDraft } from '../mail-drafts.ts';
import { useMailReading } from '../mail-reading.ts';
import { api, bindings } from '../api.ts';
import { createEchomail, echoClientKey } from '../echomail.ts';
import { mailId } from '../netmail.ts';
import type { AppState } from '../state.ts';
import type { MailDraft, MailItem } from '@rpc/bindings.mjs';
import EchoMessageList from './EchoMessageList.vue';
import NetmailMessage from './NetmailMessage.vue';
import NetmailDrafts from './NetmailDrafts.vue';
import MailComposer from './MailComposer.vue';
import MailWorkspace from './MailWorkspace.vue';

const props = defineProps<{
    state: AppState;
    initialDraft?: MailDraft;
    initialArea?: string;
    initialMessage?: string;
    initialMode?: 'flat' | 'tree';
}>();
const emit = defineEmits<{ forward: [draft: MailDraft] }>();
const client = inject(echoClientKey, api.ui);
const reader = createEchomail(props.state, client, (value) => new bindings.MailDraft(value));
const query = ref('');
const mailSearch = createArchiveSearch(
    props.state.session,
    computed(() =>
        reader.currentArea.value?.readable ? { mailbox: 1 as const, area: reader.area.value } : null
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
const navigationItems = shallowRef<MailItem[]>([]);
const search = ref('');
const visibleAreas = computed(() =>
    reader.areas.value.filter((area) =>
        area.name.toLowerCase().includes(search.value.trim().toLowerCase())
    )
);
const index = computed(() =>
    navigationItems.value.findIndex((item) => mailId(item.id) === mailId(reader.selected.value?.id))
);
defineExpose({ canLeave: reader.discard });
const {
    pending: opening,
    open: openMessage,
    cancel: cancelRead,
    run: read
} = useMailReading(props.state, reader, reader.area);
onMounted(() =>
    props.state.run(async () => {
        await reader.start(props.initialDraft);
        if (props.initialArea && !props.initialDraft) await reader.selectArea(props.initialArea);
        const initial = reader.items.value.find((item) => mailId(item.id) === props.initialMessage);
        if (initial) await reader.open(initial);
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
    reader.context.value = null;
    reader.showDrafts.value = false;
}
function cancel() {
    if (reader.discard()) reader.draft.value = null;
}
async function refresh() {
    if (!reader.discard()) return;
    await reader.refreshAreas();
    await reader.selectArea(reader.area.value);
    if (mailSearch.active.value) await mailSearch.refresh();
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
</script>
<template>
    <MailWorkspace
        v-model:query="query"
        kind="echo"
        :selected="reader.selected.value ? mailId(reader.selected.value.id) : undefined"
        :opened="!!reader.selected.value || !!reader.draft.value || reader.showDrafts.value"
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
                    <h1>{{ t('Echomail') }}</h1>
                    <p class="muted">{{ t('Read and discuss in your subscribed conferences.') }}</p>
                </div>
                <div class="actions">
                    <button
                        class="primary"
                        :disabled="state.busy.value || !reader.authors.value.length"
                        v-shortcut="'mail.new'"
                        @click="reader.edit()"
                    >
                        <AppIcon name="edit" />{{ t('New echomail') }}
                    </button>
                    <button
                        class="secondary"
                        :disabled="state.busy.value"
                        :aria-label="t('Echo drafts')"
                        :title="t('Echo drafts')"
                        @click="state.run(reader.loadDrafts)"
                    >
                        <AppIcon name="file" /><span class="desktop-hidden">{{
                            t('Echo drafts')
                        }}</span>
                    </button>
                    <button
                        class="secondary"
                        :disabled="state.busy.value"
                        :aria-label="t('Refresh')"
                        :title="t('Refresh')"
                        v-shortcut="'page.refresh'"
                        @click="state.run(refresh)"
                    >
                        <AppIcon name="refresh" /><span class="desktop-hidden">{{
                            t('Refresh')
                        }}</span>
                    </button>
                </div>
            </header>
        </template>
        <template #filters>
            <div class="card conference-selector">
                <label
                    ><span class="control-label">{{ t('Select a conference') }}</span
                    ><select
                        :value="reader.area.value"
                        :disabled="state.busy.value"
                        @change="
                            state.run(() =>
                                reader.selectArea(($event.target as HTMLSelectElement).value)
                            )
                        "
                    >
                        <option
                            v-for="area in visibleAreas"
                            :key="area.name"
                            :value="area.name"
                        >
                            {{ area.name }} / {{ mailId(area.messages)
                            }}{{
                                !area.readable
                                    ? ' / ' + t('Post only')
                                    : !area.writeAddresses?.length
                                      ? ' / ' + t('Read only')
                                      : ''
                            }}
                        </option>
                    </select></label
                >
                <p
                    v-if="!reader.areas.value.length"
                    class="muted"
                >
                    {{
                        t(
                            'No accessible conferences. Ask the administrator to grant your AKA access.'
                        )
                    }}
                </p>
                <p
                    v-else-if="!visibleAreas.length"
                    class="muted"
                >
                    {{ t('No matching conferences.') }}
                </p>
            </div>
        </template>
        <template #options>
            <label
                >{{ t('Find a conference')
                }}<input
                    v-model="search"
                    type="search"
            /></label>
        </template>
        <template #list>
            <EchoMessageList
                v-if="reader.currentArea.value?.readable"
                :key="reader.area.value"
                :items="mailSearch.items.value"
                :search-results="mailSearch.remote.value"
                :searching="mailSearch.pending.value"
                :search-failed="mailSearch.failure.value"
                :selected="reader.selected.value ? mailId(reader.selected.value.id) : ''"
                :busy="state.busy.value"
                :has-more="
                    mailSearch.remote.value ? mailSearch.hasMore.value : reader.hasMore.value
                "
                :initial-mode="initialMode"
                @navigation="navigationItems = $event"
                @open="openMessage"
                @more="loadMore"
            />
        </template>
        <MailComposer
            v-if="reader.draft.value"
            :key="reader.draft.value.id"
            v-model="reader.draft.value"
            :addresses="reader.authors.value"
            :busy="state.busy.value || opening"
            @initialized="reader.initializeDraft"
            @save="state.run(reader.save)"
            @send="state.run(reader.send)"
            @cancel="cancel"
        />
        <NetmailDrafts
            v-else-if="reader.showDrafts.value"
            :drafts="reader.drafts.value"
            :busy="state.busy.value"
            @edit="(value) => state.run(() => reader.editDraft(value))"
            @remove="(value) => state.run(() => reader.removeDraft(value))"
        />
        <p
            v-else-if="reader.currentArea.value && !reader.currentArea.value.readable"
            class="card muted"
        >
            {{ t('You can post here, but your AKA has no reading permission.') }}
        </p>
        <div
            v-else-if="reader.selected.value"
            class="echo-reading"
        >
            <NetmailMessage
                :message="reader.selected.value"
                :busy="state.busy.value"
                :pending="opening"
                :reply="!!reader.authors.value.length"
                :removable="false"
                :forwardable="!!state.profile.value?.allowedAkas.length"
                @forward="
                    emit('forward', forwardDraft(reader.selected.value!, state.profile.value!))
                "
                @reply="reply"
                @compose="reader.edit()"
                :navigation="true"
                :previous-disabled="index <= 0"
                :next-disabled="index < 0 || index >= navigationItems.length - 1"
                :unread-disabled="!nextUnread"
                @previous="openMessage(navigationItems[index - 1]!)"
                @next="openMessage(navigationItems[index + 1]!)"
                @unread="nextUnread && openMessage(nextUnread)"
            />
            <details
                role="region"
                v-if="reader.context.value"
                class="card echo-conversation"
                :aria-label="t('Conversation')"
            >
                <summary>
                    <AppIcon name="logs" />{{ t('Conversation') }}<AppIcon name="down" />
                </summary>
                <button
                    v-if="reader.context.value.parent"
                    class="secondary"
                    :disabled="state.busy.value"
                    :aria-disabled="opening || undefined"
                    @click="!opening && openMessage(reader.context.value!.parent!)"
                >
                    {{ t('In reply to') }}: {{ reader.context.value.parent.fromName }} —
                    {{ reader.context.value.parent.subject }}
                    <time class="conversation-date">{{ reader.context.value.parent.date }}</time>
                </button>
                <p
                    v-else-if="reader.context.value.missingParent"
                    class="muted"
                >
                    {{ t('The parent message is not available in this conference.') }}
                </p>
                <p
                    v-else
                    class="muted"
                >
                    {{ t('Start of conversation') }}
                </p>
                <h4>{{ t('Replies') }}</h4>
                <p
                    v-if="!reader.context.value.replies.items.length"
                    class="muted"
                >
                    {{ t('No replies yet.') }}
                </p>
                <ul>
                    <li
                        v-for="reply in reader.context.value.replies.items"
                        :key="mailId(reply.id)"
                    >
                        <button
                            class="text-button"
                            :disabled="state.busy.value"
                            :aria-disabled="opening || undefined"
                            @click="!opening && openMessage(reply)"
                        >
                            {{ reply.fromName }} — {{ reply.subject }}
                        </button>
                        <time class="conversation-date">{{ reply.date }}</time>
                    </li>
                </ul>
                <button
                    v-if="reader.context.value.replies.hasMore"
                    class="secondary"
                    :disabled="state.busy.value"
                    :aria-disabled="opening || undefined"
                    @click="!opening && read(reader.moreReplies)"
                >
                    {{ t('More replies') }}
                </button>
            </details>
        </div>
        <section
            v-else
            class="card muted mail-reader-empty"
        >
            <p>{{ t('Select a message to read.') }}</p>
        </section>
    </MailWorkspace>
</template>
<style scoped>
.echo-conversation li {
    padding-block: 4px;
}
.conversation-date {
    display: inline-block;
    margin-inline-start: 12px;
    color: var(--muted);
    font-size: 12px;
    font-weight: normal;
}
</style>
