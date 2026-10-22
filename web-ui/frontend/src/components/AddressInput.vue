<script setup lang="ts">
import { computed, inject, nextTick, onUnmounted, ref, useAttrs, useId, watch } from 'vue';
import { api } from '../api.ts';
import { addressToken, createDirectorySearch, directorySearchKey } from '../directory.ts';
import { t } from '../i18n.ts';
import type { DirectoryNode } from '@rpc/bindings.mjs';
defineOptions({ inheritAttrs: false });
const model = defineModel<string>({ required: true });
const props = withDefaults(
    defineProps<{ multiple?: boolean; includePoints?: boolean; valueKind?: 'address' | 'name' }>(),
    { multiple: false, includePoints: true, valueKind: 'address' }
);
const emit = defineEmits<{ select: [node: DirectoryNode] }>();
const attrs = useAttrs();
const search = inject(directorySearchKey, (query, offset, limit, includePoints) =>
    api.settings.searchDirectory(query, offset, limit, includePoints)
);
const reader = createDirectorySearch(search, 8, () => props.includePoints);
const { items, busy } = reader;
const input = ref<HTMLInputElement | null>(null);
const open = ref(false);
const active = ref(-1);
const failed = ref(false);
const id = useId();
let timer: ReturnType<typeof setTimeout> | undefined;
let caret = 0;
const expanded = computed(
    () => open.value && (busy.value || failed.value || items.value.length > 0)
);
function close() {
    clearTimeout(timer);
    open.value = false;
    active.value = -1;
    reader.clear();
}
function request(value = input.value?.value ?? model.value) {
    clearTimeout(timer);
    if (input.value?.readOnly || input.value?.disabled) return;
    caret = input.value?.selectionStart ?? model.value.length;
    const token = addressToken(value, caret, props.multiple);
    reader.clear();
    active.value = -1;
    failed.value = false;
    open.value = token.query.length >= 2 && token.query.length <= 160;
    if (!open.value) return;
    timer = setTimeout(async () => {
        try {
            await reader.load(token.query);
        } catch {
            failed.value = true;
        }
    }, 250);
}
function changed(event: Event) {
    model.value = (event.target as HTMLInputElement).value;
    request();
}
async function choose(node: DirectoryNode) {
    const token = addressToken(model.value, caret, props.multiple);
    const suffix = model.value.slice(token.end);
    const selected =
        (props.valueKind === 'name' ? node.sysop : node.address) +
        (props.multiple && !suffix ? ' ' : '');
    model.value = model.value.slice(0, token.start) + selected + suffix;
    close();
    emit('select', node);
    await nextTick();
    input.value?.focus();
    input.value?.setSelectionRange(token.start + selected.length, token.start + selected.length);
    close();
}
function keydown(event: KeyboardEvent) {
    if (event.key === 'Escape') {
        close();
        return;
    }
    if (!expanded.value || !items.value.length) return;
    if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
        event.preventDefault();
        active.value =
            active.value < 0
                ? event.key === 'ArrowDown'
                    ? 0
                    : items.value.length - 1
                : (active.value + (event.key === 'ArrowDown' ? 1 : -1) + items.value.length) %
                  items.value.length;
        document.getElementById(`${id}-${active.value}`)?.scrollIntoView({ block: 'nearest' });
    } else if (event.key === 'Enter' && active.value >= 0) {
        event.preventDefault();
        void choose(items.value[active.value]!);
    }
}
onUnmounted(() => {
    clearTimeout(timer);
    reader.dispose();
});
watch(
    () => props.includePoints,
    () => {
        close();
        if (document.activeElement === input.value) request();
    }
);
</script>
<template>
    <span class="address-input">
        <input
            ref="input"
            v-bind="attrs"
            :value="model"
            role="combobox"
            autocomplete="off"
            aria-autocomplete="list"
            :aria-expanded="expanded"
            :aria-controls="`${id}-list`"
            :aria-activedescendant="expanded && active >= 0 ? `${id}-${active}` : undefined"
            @input="changed"
            @focus="request()"
            @click="request()"
            @blur="close"
            @keydown="keydown"
        />
        <span
            v-if="expanded"
            class="address-options"
        >
            <span
                v-if="busy"
                role="status"
                >{{ t('Searching directory…') }}</span
            >
            <span
                v-if="failed"
                role="status"
                >{{
                    props.valueKind === 'name'
                        ? t('Directory search is unavailable. You can enter a name manually.')
                        : t('Directory search is unavailable. You can enter an address manually.')
                }}</span
            >
            <span
                :id="`${id}-list`"
                role="listbox"
                :aria-label="t('Matching nodes')"
            >
                <span
                    v-for="(node, index) in items"
                    :id="`${id}-${index}`"
                    :key="`${node.source}:${node.address}`"
                    role="option"
                    :aria-selected="active === index"
                    :class="{ active: active === index }"
                    @pointerdown.prevent
                    @click.prevent.stop="choose(node)"
                >
                    <strong class="mono">{{ node.address }}</strong>
                    <span>{{ node.system }} · {{ node.sysop }}</span>
                    <small>{{ node.source }} · {{ node.kind }}</small>
                </span>
            </span>
        </span>
    </span>
</template>
<style scoped>
.address-input {
    display: block;
    position: relative;
    min-width: 0;
}
.address-input input {
    width: 100%;
    display: block;
}
.address-options {
    position: absolute;
    top: 100%;
    left: 0;
    right: 0;
    z-index: 20;
    max-height: 19rem;
    overflow: auto;
    background: var(--surface);
    color: var(--text);
    border: 1px solid var(--line);
    border-radius: 8px;
    box-shadow: 0 8px 24px #0003;
}
[role='option'] {
    display: flex;
    flex-direction: column;
    padding: 10px 12px;
    cursor: pointer;
    overflow-wrap: anywhere;
    border-bottom: 1px solid var(--line);
}
[role='option']:hover,
[role='option'].active {
    background: var(--field);
}
[role='status'] {
    display: block;
    padding: 12px;
}
</style>
