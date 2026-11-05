<script setup lang="ts">
import { computed, inject, onBeforeUnmount, onMounted, ref, shallowRef } from 'vue';
import AppIcon from './AppIcon.vue';
import NotificationBanner from './NotificationBanner.vue';
import { createEchoDiscussion } from '../echo-discussion.ts';
import MailSearchStatus from './MailSearchStatus.vue';
import { createArchiveSearch } from '../archive-search.ts';
import { t } from '../i18n.ts';
import { forwardDraft } from '../mail-drafts.ts';
import { inlineKludgeBody } from '../mail-kludges.ts';
import { useMailReading } from '../mail-reading.ts';
import { useWorkspace } from '../workspace.ts';
import { api, bindings } from '../api.ts';
import { createEchomail, echoClientKey } from '../echomail.ts';
import { mailId } from '../netmail.ts';
import type { AppState } from '../state.ts';
import type { MailDraft, MailItem } from '@rpc/bindings.mjs';
import EchoMessageList from './EchoMessageList.vue';
import MessageView from './MessageView.vue';
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
const workspace = useWorkspace();
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
    const selected = reader.selected.value!;
    reader.edit(
        undefined,
        kind === 'quoted'
            ? {
                  ...selected,
                  body: inlineKludgeBody(
                      selected,
                      workspace.inlineKludges.value,
                      workspace.value.value.unknownKludges === 'always'
                  )
              }
            : selected
    );
    if (reader.draft.value) reader.draft.value.composeKind = kind;
}
const nextUnread = computed(() =>
    navigationItems.value.find(
        (item) => !item.read && mailId(item.id) !== mailId(reader.selected.value?.id)
    )
);
const discussionView = computed(() => workspace.value.value.discussionView);
const discussionSaving = ref(false);
const discussionError = ref('');
async function saveDiscussion(patch: {
    discussionView?: 'current' | 'whole';
    discussionSelection?: 'center' | 'retain';
}) {
    if (discussionSaving.value) return;
    discussionSaving.value = true;
    discussionError.value = '';
    try {
        await workspace.update(patch);
    } catch {
        discussionError.value = t('Could not save workspace preferences.');
    } finally {
        discussionSaving.value = false;
    }
}
const discussion = createEchoDiscussion(
    props.state.session,
    reader.area,
    reader.selected,
    reader.context,
    discussionView,
    (...args) => client.echoContext(...args)
);
const discussionRows = discussion.rows;
function discussionKey(event: KeyboardEvent) {
    const target = event.target as HTMLElement;
    if (target !== event.currentTarget || props.state.busy.value || opening.value) return;
    const rows = [...target.parentElement!.querySelectorAll<HTMLElement>('[data-discussion-row]')];
    const index = rows.indexOf(target);
    if (event.key === 'Enter' || event.key === ' ') {
        event.preventDefault();
        const item = discussionRows.value[index]?.item;
        if (item) openDiscussionRow(item);
        return;
    }
    let next: HTMLElement | undefined;
    if (event.key === 'ArrowDown') next = rows[index + 1];
    else if (event.key === 'ArrowUp') next = rows[index - 1];
    else if (event.key === 'Home') next = rows[0];
    else if (event.key === 'End') next = rows.at(-1);
    else if (event.key === 'ArrowRight') {
        const row = discussionRows.value[index]!;
        if (!discussion.expanded.value.has(mailId(row.item.id))) void discussion.toggle(row.item);
        else next = rows[index + 1];
    } else if (event.key === 'ArrowLeft') {
        const row = discussionRows.value[index]!;
        if (discussion.expanded.value.has(mailId(row.item.id))) void discussion.toggle(row.item);
        else next = rows.find((entry) => entry.dataset.discussionRow === row.parent);
    } else return;
    event.preventDefault();
    next?.focus();
}
function openDiscussionRow(item: MailItem) {
    void openMessage(item).then(() => {
        if (
            mailId(reader.selected.value?.id) === mailId(item.id) &&
            discussionView.value === 'whole' &&
            workspace.value.value.discussionSelection === 'center'
        ) {
            void saveDiscussion({ discussionView: 'current' });
        }
    });
}
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
                <div class="actions mail-header-actions">
                    <button
                        class="primary"
                        :aria-label="t('New echomail')"
                        :title="t('New echomail')"
                        :disabled="state.busy.value || !reader.authors.value.length"
                        v-shortcut="'mail.new'"
                        @click="reader.edit()"
                    >
                        <AppIcon name="edit" /><span class="mail-action-label">{{
                            t('New echomail')
                        }}</span>
                    </button>
                    <button
                        class="secondary"
                        :disabled="state.busy.value"
                        :aria-label="t('Echo drafts')"
                        :title="t('Echo drafts')"
                        @click="state.run(reader.loadDrafts)"
                    >
                        <AppIcon name="file" />
                    </button>
                    <button
                        class="secondary"
                        :disabled="state.busy.value"
                        :aria-label="t('Refresh')"
                        :title="t('Refresh')"
                        v-shortcut="'page.refresh'"
                        @click="state.run(refresh)"
                    >
                        <AppIcon name="refresh" />
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
            <MessageView
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
                <div class="echo-conversation-content">
                    <NotificationBanner
                        kind="error"
                        :message="discussionError"
                    />
                    <div
                        class="actions discussion-view-controls"
                        role="group"
                        :aria-label="t('Discussion view')"
                    >
                        <button
                            class="secondary icon-button"
                            :aria-pressed="discussionView === 'current'"
                            :disabled="discussionSaving"
                            :aria-label="t('Current message')"
                            :title="t('Current message')"
                            @click="saveDiscussion({ discussionView: 'current' })"
                        >
                            <AppIcon name="mail" />
                        </button>
                        <button
                            class="secondary icon-button"
                            :aria-pressed="discussionView === 'whole'"
                            :disabled="discussionSaving"
                            :aria-label="t('Whole discussion')"
                            :title="t('Whole discussion')"
                            @click="saveDiscussion({ discussionView: 'whole' })"
                        >
                            <AppIcon name="logs" />
                        </button>
                        <label v-if="discussionView === 'whole'"
                            >{{ t('When opening a discussion message') }}
                            <select
                                :value="workspace.value.value.discussionSelection"
                                :disabled="discussionSaving"
                                @change="
                                    saveDiscussion({
                                        discussionSelection: ($event.target as HTMLSelectElement)
                                            .value as 'center' | 'retain'
                                    })
                                "
                            >
                                <option value="center">{{ t('Center on opened message') }}</option>
                                <option value="retain">{{ t('Keep discussion root') }}</option>
                            </select>
                        </label>
                    </div>
                    <p
                        v-if="
                            discussionView === 'whole' &&
                            discussionRows.some(
                                (row) =>
                                    discussion.links.value.get(mailId(row.item.id))?.missingParent
                            )
                        "
                        class="muted"
                    >
                        {{ t('The parent message is not available in this conference.') }}
                    </p>
                    <p
                        v-if="!reader.context.value.replies.items.length"
                        class="muted"
                    >
                        {{ t('No replies yet.') }}
                    </p>
                    <ul
                        role="tree"
                        :aria-label="t('Conversation')"
                        class="discussion-rows"
                    >
                        <li
                            v-for="row in discussionRows"
                            :key="mailId(row.item.id)"
                            role="treeitem"
                            :aria-level="row.depth + 1"
                            :aria-expanded="discussion.expanded.value.has(mailId(row.item.id))"
                            :aria-selected="
                                mailId(row.item.id) === mailId(reader.selected.value.id)
                            "
                            :data-discussion-row="mailId(row.item.id)"
                            :style="{ '--thread-indent': `${Math.min(row.depth, 8) * 12}px` }"
                            tabindex="0"
                            @keydown="discussionKey"
                        >
                            <button
                                class="text-button icon-button discussion-toggle"
                                :disabled="
                                    state.busy.value ||
                                    discussion.pending.value.has(mailId(row.item.id))
                                "
                                :aria-label="
                                    discussion.expanded.value.has(mailId(row.item.id))
                                        ? t('Collapse conversation')
                                        : t('Expand conversation')
                                "
                                @click="discussion.toggle(row.item)"
                            >
                                <AppIcon
                                    :name="
                                        discussion.expanded.value.has(mailId(row.item.id))
                                            ? 'down'
                                            : 'right'
                                    "
                                />
                            </button>
                            <button
                                class="text-button discussion-open icon-button"
                                :disabled="state.busy.value"
                                :aria-label="`${row.item.fromName} — ${row.item.subject}`"
                                :title="`${row.item.fromName} — ${row.item.subject}`"
                                :aria-disabled="opening || undefined"
                                @click="!opening && openDiscussionRow(row.item)"
                            >
                                <AppIcon name="mail" />
                            </button>
                            <time class="conversation-date">{{ row.item.date }}</time>
                            <span
                                v-if="discussion.pending.value.has(mailId(row.item.id))"
                                role="status"
                                >{{ t('Loading messages…') }}</span
                            >
                            <button
                                v-if="discussion.failures.value.has(mailId(row.item.id))"
                                class="secondary"
                                @click="discussion.retry(row.item)"
                            >
                                {{ t('Could not load replies. Retry') }}
                            </button>
                            <button
                                v-if="
                                    discussion.expanded.value.has(mailId(row.item.id)) &&
                                    discussion.links.value.get(mailId(row.item.id))?.replies.hasMore
                                "
                                class="secondary"
                                :disabled="discussion.pending.value.has(mailId(row.item.id))"
                                @click="discussion.load(row.item, true)"
                            >
                                {{ t('More replies') }}
                            </button>
                        </li>
                    </ul>
                </div>
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
.discussion-view-controls {
    margin-block: 12px;
}
.discussion-rows {
    list-style: none;
    padding-inline-start: 0;
}
.discussion-rows li {
    padding: 6px 8px 6px calc(8px + var(--thread-indent));
    border-inline-start: 1px solid var(--line);
}
.discussion-rows li[aria-selected='true'] {
    background: var(--accent-soft);
    border-inline-start: 3px solid var(--accent);
}
.echo-conversation-content {
    padding: 8px 12px;
}
.conversation-date {
    display: inline-block;
    margin-inline-start: 12px;
    color: var(--muted);
    font-size: 12px;
    font-weight: normal;
}
</style>
