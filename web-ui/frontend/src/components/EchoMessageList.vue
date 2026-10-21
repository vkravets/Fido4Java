<script setup lang="ts">
import AppIcon from './AppIcon.vue';
import { shortMailDate } from '../mail-date.ts';
import { computed, ref } from 'vue';
import { t } from '../i18n.ts';
import NotificationBanner from './NotificationBanner.vue';
import { useWorkspace } from '../workspace.ts';
import { matchesMail, useMailSearch } from '../mail-search.ts';
import { mailId } from '../netmail.ts';
import { echoThreads } from '../echo-threads.ts';
import type { EchoThreadRow } from '../echo-threads.ts';
import type { MailItem } from '@rpc/bindings.mjs';

const props = defineProps<{
    items: MailItem[];
    selected: string;
    busy: boolean;
    hasMore: boolean;
    initialMode?: 'flat' | 'tree';
}>();
const emit = defineEmits<{ open: [item: MailItem]; more: [] }>();
const workspace = useWorkspace();
const selectedMode = ref<'flat' | 'tree'>();
const savingMode = ref(false);
const modeError = ref('');
const mode = computed({
    get: () => selectedMode.value || props.initialMode || workspace.value.value.echo.mode,
    set: (value) => {
        if (savingMode.value) return;
        savingMode.value = true;
        modeError.value = '';
        void workspace
            .updateLayout('echo', { mode: value })
            .then(() => {
                selectedMode.value = value;
            })
            .catch(() => {
                modeError.value = t('Could not save workspace preferences.');
            })
            .finally(() => {
                savingMode.value = false;
            });
    }
});
const query = useMailSearch();
const visibleItems = computed(() => props.items.filter((item) => matchesMail(item, query.value)));
const collapsed = ref(new Set<string>());
const rows = computed<EchoThreadRow[]>(() =>
    mode.value === 'tree'
        ? echoThreads(visibleItems.value, collapsed.value)
        : visibleItems.value.map((item) => ({ item, depth: 0, children: false, parent: '' }))
);
function toggle(id: string) {
    const value = new Set(collapsed.value);
    if (value.has(id)) value.delete(id);
    else value.add(id);
    collapsed.value = value;
}
function keyboard(event: KeyboardEvent, row: EchoThreadRow) {
    if (props.busy || event.target !== event.currentTarget) return;
    const node = event.currentTarget as HTMLElement;
    const nodes = [...node.parentElement!.querySelectorAll<HTMLElement>('[data-mail-row]')];
    const index = nodes.indexOf(node);
    const id = mailId(row.item.id);
    if (event.key === 'Enter' || event.key === ' ') emit('open', row.item);
    else if (event.key === 'ArrowDown') nodes[index + 1]?.focus();
    else if (event.key === 'ArrowUp') nodes[index - 1]?.focus();
    else if (event.key === 'Home') nodes[0]?.focus();
    else if (event.key === 'End') nodes.at(-1)?.focus();
    else if (mode.value === 'tree' && event.key === 'ArrowRight') {
        if (row.children && collapsed.value.has(id)) toggle(id);
        else if (row.children) nodes[index + 1]?.focus();
    } else if (mode.value === 'tree' && event.key === 'ArrowLeft') {
        if (row.children && !collapsed.value.has(id)) toggle(id);
        else nodes.find((entry) => entry.dataset.mailRow === row.parent)?.focus();
    } else return;
    event.preventDefault();
}
</script>
<template>
    <NotificationBanner
        kind="error"
        :message="modeError"
    />
    <section
        class="card echo-message-list"
        :aria-label="t('Conference messages')"
    >
        <div class="actions echo-list-controls">
            <label class="list-mode-select"
                >{{ t('Message list')
                }}<select
                    v-model="mode"
                    :disabled="busy || savingMode"
                >
                    <option value="flat">{{ t('Flat list') }}</option>
                    <option value="tree">{{ t('Conversation tree') }}</option>
                </select></label
            >
            <div
                class="list-mode-buttons"
                role="group"
                :aria-label="t('Message list')"
            >
                <button
                    :aria-label="t('Flat list')"
                    :aria-pressed="mode === 'flat'"
                    :disabled="busy || savingMode"
                    @click="mode = 'flat'"
                >
                    {{ t('Flat') }}
                </button>
                <button
                    :aria-label="t('Conversation tree')"
                    :aria-pressed="mode === 'tree'"
                    :disabled="busy || savingMode"
                    @click="mode = 'tree'"
                >
                    {{ t('Threads') }}
                </button>
            </div>
            <span class="muted">{{ t('Loaded messages: {count}', { count: items.length }) }}</span>
        </div>
        <p
            v-if="!visibleItems.length"
            class="muted"
        >
            {{ query ? t('No matching loaded messages.') : t('No messages in this conference.') }}
        </p>
        <p
            v-if="mode === 'tree' && hasMore"
            class="muted"
        >
            {{ t('The tree includes loaded messages. Load more to see earlier or later replies.') }}
        </p>
        <div
            class="mail-table-head"
            aria-hidden="true"
        >
            <span>{{ t('From') }}</span
            ><span>{{ t('Subject') }}</span
            ><span>{{ t('Date') }}</span>
        </div>
        <ul
            class="echo-message-rows"
            :role="mode === 'tree' ? 'tree' : 'list'"
            :aria-label="t('Conference messages')"
        >
            <li
                v-for="row in rows"
                :key="mailId(row.item.id)"
                :role="mode === 'tree' ? 'treeitem' : 'listitem'"
                :aria-level="mode === 'tree' ? row.depth + 1 : undefined"
                :aria-expanded="
                    mode === 'tree' && row.children
                        ? !collapsed.has(mailId(row.item.id))
                        : undefined
                "
                :aria-selected="mode === 'tree' ? selected === mailId(row.item.id) : undefined"
                :data-mail-row="mailId(row.item.id)"
                tabindex="0"
                :class="{ selected: selected === mailId(row.item.id), unread: !row.item.read }"
                :style="{ '--thread-indent': `${Math.min(row.depth, 8) * 12}px` }"
                @keydown="keyboard($event, row)"
            >
                <button
                    v-if="mode === 'tree' && row.children"
                    class="text-button echo-branch"
                    :aria-label="
                        collapsed.has(mailId(row.item.id))
                            ? t('Expand conversation')
                            : t('Collapse conversation')
                    "
                    :disabled="busy"
                    @click.stop="toggle(mailId(row.item.id))"
                >
                    <AppIcon :name="collapsed.has(mailId(row.item.id)) ? 'right' : 'down'" />
                </button>
                <button
                    class="echo-message-open"
                    data-shortcut-row
                    :data-shortcut-selected="selected === mailId(row.item.id) ? '' : undefined"
                    :disabled="busy"
                    @click="emit('open', row.item)"
                >
                    <strong class="mail-row-title">{{ row.item.subject }}</strong>
                    <span class="mail-sender"
                        ><i
                            class="mail-unread-dot"
                            aria-hidden="true"
                        ></i
                        >{{ row.item.fromName
                        }}<span class="mail-recipient"> → {{ row.item.toName }}</span></span
                    >
                    <span
                        class="mail-excerpt"
                        v-if="row.item.excerpt || row.item.body"
                        >{{ row.item.excerpt || row.item.body?.slice(0, 160) }}</span
                    >
                    <time
                        class="mail-date"
                        :title="row.item.date"
                        >{{ shortMailDate(row.item.date) }}</time
                    >
                    <small class="mobile-mail-date"
                        >{{ row.item.date }}
                        <span v-if="!row.item.read"> · {{ t('Unread') }}</span></small
                    >
                </button>
            </li>
        </ul>
        <button
            v-if="hasMore"
            class="secondary"
            :disabled="busy"
            @click="emit('more')"
        >
            {{ t('Load older messages') }}
        </button>
    </section>
</template>
