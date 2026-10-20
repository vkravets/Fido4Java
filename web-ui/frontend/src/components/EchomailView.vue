<script setup lang="ts">
import { computed, inject, onBeforeUnmount, onMounted, ref } from 'vue';
import { t } from '../i18n.ts';
import { forwardDraft } from '../mail-drafts.ts';
import { api, bindings } from '../api.ts';
import { createEchomail, echoClientKey } from '../echomail.ts';
import { mailId } from '../netmail.ts';
import type { AppState } from '../state.ts';
import type { MailDraft } from '@rpc/bindings.mjs';
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
const reader = createEchomail(
    props.state,
    inject(echoClientKey, api.ui),
    (value) => new bindings.MailDraft(value)
);
const search = ref('');
const visibleAreas = computed(() =>
    reader.areas.value.filter((area) =>
        area.name.toLowerCase().includes(search.value.trim().toLowerCase())
    )
);
const index = computed(() =>
    reader.items.value.findIndex((item) => mailId(item.id) === mailId(reader.selected.value?.id))
);
defineExpose({ canLeave: reader.discard });
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
}
</script>
<template>
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
                @click="reader.edit()"
            >
                {{ t('New echomail') }}
            </button>
            <button
                class="secondary"
                :disabled="state.busy.value"
                @click="state.run(reader.loadDrafts)"
            >
                {{ t('Echo drafts') }}
            </button>
            <button
                class="secondary"
                :disabled="state.busy.value"
                @click="state.run(refresh)"
            >
                {{ t('Refresh') }}
            </button>
        </div>
    </header>
    <MailWorkspace
        kind="echo"
        :opened="!!reader.selected.value || !!reader.draft.value || reader.showDrafts.value"
        :busy="state.busy.value"
        @back="back"
    >
        <template #list>
            <div class="card conference-selector">
                <label
                    >{{ t('Find a conference')
                    }}<input
                        v-model="search"
                        type="search"
                /></label>
                <label
                    >{{ t('Select a conference')
                    }}<select
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
            <EchoMessageList
                v-if="reader.currentArea.value?.readable"
                :key="reader.area.value"
                :items="reader.items.value"
                :selected="reader.selected.value ? mailId(reader.selected.value.id) : ''"
                :busy="state.busy.value"
                :has-more="reader.hasMore.value"
                :initial-mode="initialMode"
                @open="(item) => state.run(() => reader.open(item))"
                @more="state.run(() => reader.load(true))"
            />
        </template>
        <MailComposer
            v-if="reader.draft.value"
            :key="reader.draft.value.id"
            v-model="reader.draft.value"
            :addresses="reader.authors.value"
            :busy="state.busy.value"
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
            <nav
                class="actions echo-message-navigation"
                :aria-label="t('Message navigation')"
            >
                <button
                    class="secondary"
                    :disabled="state.busy.value || index <= 0"
                    @click="state.run(() => reader.open(reader.items.value[index - 1]!))"
                >
                    {{ t('Previous message') }}
                </button>
                <button
                    class="secondary"
                    :disabled="
                        state.busy.value || index < 0 || index >= reader.items.value.length - 1
                    "
                    @click="state.run(() => reader.open(reader.items.value[index + 1]!))"
                >
                    {{ t('Next message') }}
                </button>
            </nav>
            <NetmailMessage
                :message="reader.selected.value"
                :busy="state.busy.value"
                :reply="!!reader.authors.value.length"
                :removable="false"
                :forwardable="!!state.profile.value?.allowedAkas.length"
                @forward="
                    emit('forward', forwardDraft(reader.selected.value!, state.profile.value!))
                "
                @reply="reader.edit(undefined, reader.selected.value!)"
            />
            <section
                v-if="reader.context.value"
                class="card echo-conversation"
                :aria-label="t('Conversation')"
            >
                <h3>{{ t('Conversation') }}</h3>
                <button
                    v-if="reader.context.value.parent"
                    class="secondary"
                    :disabled="state.busy.value"
                    @click="state.run(() => reader.open(reader.context.value!.parent!))"
                >
                    {{ t('In reply to') }}: {{ reader.context.value.parent.fromName }} —
                    {{ reader.context.value.parent.subject }}
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
                            @click="state.run(() => reader.open(reply))"
                        >
                            {{ reply.fromName }} — {{ reply.subject }}
                        </button>
                    </li>
                </ul>
                <button
                    v-if="reader.context.value.replies.hasMore"
                    class="secondary"
                    :disabled="state.busy.value"
                    @click="state.run(reader.moreReplies)"
                >
                    {{ t('More replies') }}
                </button>
            </section>
        </div>
        <section
            v-else
            class="card muted"
        >
            <p>{{ t('Select a message to read.') }}</p>
        </section>
    </MailWorkspace>
</template>
