<script setup lang="ts">
import { computed, ref } from 'vue';
import { t } from '../i18n.ts';
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
const mode = ref<'flat' | 'tree'>(props.initialMode || 'tree');
const collapsed = ref(new Set<string>());
const rows = computed<EchoThreadRow[]>(() =>
    mode.value === 'tree'
        ? echoThreads(props.items, collapsed.value)
        : props.items.map((item) => ({ item, depth: 0, children: false, parent: '' }))
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
    <section
        class="card echo-message-list"
        :aria-label="t('Conference messages')"
    >
        <div class="actions echo-list-controls">
            <label
                >{{ t('Message list')
                }}<select
                    v-model="mode"
                    :disabled="busy"
                >
                    <option value="flat">{{ t('Flat list') }}</option>
                    <option value="tree">{{ t('Conversation tree') }}</option>
                </select></label
            >
            <span class="muted">{{ t('Loaded messages: {count}', { count: items.length }) }}</span>
        </div>
        <p
            v-if="!items.length"
            class="muted"
        >
            {{ t('No messages in this conference.') }}
        </p>
        <p
            v-if="mode === 'tree' && hasMore"
            class="muted"
        >
            {{ t('The tree includes loaded messages. Load more to see earlier or later replies.') }}
        </p>
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
                :style="{ paddingInlineStart: `${Math.min(row.depth, 12) * 1.1 + 0.6}rem` }"
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
                    {{ collapsed.has(mailId(row.item.id)) ? '▸' : '▾' }}
                </button>
                <button
                    class="echo-message-open"
                    :disabled="busy"
                    @click="emit('open', row.item)"
                >
                    <strong>{{ row.item.subject }}</strong>
                    <span>{{ row.item.fromName }} → {{ row.item.toName }}</span>
                    <small
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
