<script setup lang="ts">
import { computed, ref, provide, watch, nextTick, onMounted, onBeforeUnmount, useSlots } from 'vue';
import { mailSearchKey } from '../mail-search.ts';
import { t } from '../i18n.ts';
import { composerListKey, useWorkspace } from '../workspace.ts';
import { useMedia } from '../media.ts';
import type { MailKind, MailLayout } from '../workspace.ts';
import SplitPane from './SplitPane.vue';
import MailListTools from './MailListTools.vue';
import AppIcon from './AppIcon.vue';
import NotificationBanner from './NotificationBanner.vue';
const props = defineProps<{
    kind: MailKind;
    opened: boolean;
    composing?: boolean;
    busy?: boolean;
    selected?: string;
    conference?: string;
    content?: unknown;
}>();
const emit = defineEmits<{ back: []; hide: [] }>();
const workspace = useWorkspace();
const query = defineModel<string>('query', { default: '' });
provide(mailSearchKey, query);
const preferences = computed(() => workspace.value.value[props.kind]);
const desktop = useMedia('(min-width: 701px)');
const listHidden = ref(true);
const canToggleList = computed(
    () =>
        !!props.composing &&
        desktop.value &&
        !singleStage.value &&
        preferences.value.preview !== 'separate'
);
provide(composerListKey, {
    available: canToggleList,
    hidden: listHidden,
    toggle() {
        if (canToggleList.value) {
            listHidden.value = !listHidden.value;
            if (hasConferences.value) changeStage('detail');
        }
    }
});
watch(
    () => props.composing,
    () => {
        listHidden.value = true;
    }
);
const saving = ref(false);
const error = ref('');
const layoutRoot = ref<HTMLElement>();
const slots = useSlots();
const hasConferences = computed(() => props.kind === 'echo' && !!slots.conferences);
const workWidth = ref(0);
type Stage = 'conferences' | 'messages' | 'detail';
const stage = ref<Stage>(props.opened ? 'detail' : props.conference ? 'messages' : 'conferences');
const conferenceStage = computed(() => stage.value === 'conferences');
const singleStage = computed(
    () => hasConferences.value && (!desktop.value || (workWidth.value > 0 && workWidth.value < 640))
);
const showConferences = computed(() => {
    if (!hasConferences.value) return false;
    if (singleStage.value) return conferenceStage.value;
    if (stage.value !== 'detail') return true;
    if (props.composing) return false;
    if (!props.opened) return true;
    return (
        preferences.value.preview !== 'separate' &&
        workspace.value.value.conferencePanels === 'all' &&
        (preferences.value.preview === 'below' || workWidth.value >= 840)
    );
});
const listVisible = computed(() => {
    if (hasConferences.value) {
        if (singleStage.value) return stage.value === 'messages';
        if (stage.value !== 'detail') return true;
    }
    if (!desktop.value || preferences.value.preview === 'separate') return !props.opened;
    return !(canToggleList.value && listHidden.value);
});
const detailVisible = computed(() =>
    hasConferences.value
        ? props.opened && stage.value === 'detail'
        : !desktop.value || preferences.value.preview === 'separate'
          ? props.opened
          : true
);
const paneScroll = new Map<string, number>();
const stageFocus = new Map<Stage, HTMLElement>();
function rememberPanes() {
    for (const selector of ['.conference-pane', '.split-list', '.split-detail']) {
        const pane = layoutRoot.value?.querySelector<HTMLElement>(selector);
        if (pane?.getClientRects().length) paneScroll.set(selector, pane.scrollTop);
    }
}
function restorePanes() {
    for (const [selector, top] of paneScroll) {
        const pane = layoutRoot.value?.querySelector<HTMLElement>(selector);
        if (pane?.getClientRects().length) pane.scrollTop = top;
    }
}
async function changeStage(next: Stage) {
    if (!hasConferences.value) return;
    const active = document.activeElement;
    const currentPane =
        stage.value === 'conferences'
            ? '.conference-pane'
            : stage.value === 'messages'
              ? '.split-list'
              : '.split-detail';
    if (active instanceof HTMLElement && active.closest(currentPane))
        stageFocus.set(stage.value, active);
    rememberPanes();
    if (next !== 'detail') emit('hide');
    stage.value = next;
    await nextTick();
    if (stage.value !== next) return;
    restorePanes();
    const previous = stageFocus.get(next);
    const selector =
        next === 'conferences'
            ? '.conference-pane input'
            : next === 'messages'
              ? '.split-list [data-shortcut-selected], .split-list [data-shortcut-row], .split-list input'
              : props.composing
                ? '.split-detail .cm-content'
                : '.split-detail .mail-message';
    const target =
        previous?.isConnected && previous.getClientRects().length
            ? previous
            : layoutRoot.value?.querySelector<HTMLElement>(selector);
    target?.focus({ preventScroll: true });
}
function back() {
    if (hasConferences.value) void changeStage('messages');
    else emit('back');
}
defineExpose({
    showMessages: () => changeStage('messages'),
    showDetail: () => changeStage('detail')
});
watch(
    [listVisible, detailVisible, showConferences, () => preferences.value.preview],
    async () => {
        rememberPanes();
        await nextTick();
        restorePanes();
    },
    { flush: 'pre' }
);
let observer: ResizeObserver | undefined;
onMounted(() => {
    if (!layoutRoot.value) return;
    workWidth.value = layoutRoot.value.clientWidth;
    observer = new ResizeObserver(([entry]) => {
        if (entry) workWidth.value = entry.contentRect.width;
    });
    observer.observe(layoutRoot.value);
});
onBeforeUnmount(() => observer?.disconnect());
watch(
    () => props.conference,
    () => {
        paneScroll.delete('.split-list');
        paneScroll.delete('.split-detail');
        stageFocus.delete('messages');
        stageFocus.delete('detail');
        stage.value = props.conference ? 'messages' : 'conferences';
    }
);
watch(
    () => [props.opened, props.content, props.composing] as const,
    ([opened]) => {
        if (!hasConferences.value) return;
        if (opened && stage.value !== 'detail') void changeStage('detail');
        else if (!opened) stage.value = props.conference ? 'messages' : 'conferences';
    }
);
let listTop = 0;
let windowTop = 0;
function keepSelectedRowVisible() {
    const list = layoutRoot.value?.querySelector<HTMLElement>('.split-list');
    const row = list?.querySelector<HTMLElement>('[data-shortcut-selected]');
    const scrollContainer = row?.closest<HTMLElement>('.echo-message-scroll') || list;
    if (
        !scrollContainer ||
        !row ||
        !scrollContainer.getClientRects().length ||
        !scrollContainer.clientHeight
    )
        return;

    // Scroll only the message rows. scrollIntoView would also move the page and reader.
    const bounds = scrollContainer.getBoundingClientRect();
    const tools =
        scrollContainer === list ? list.querySelector<HTMLElement>('.mail-list-tools') : null;
    const top = Math.max(bounds.top, tools?.getBoundingClientRect().bottom || bounds.top);
    const bottom = bounds.top + scrollContainer.clientTop + scrollContainer.clientHeight;
    const selected = row.getBoundingClientRect();
    if (selected.top < top) scrollContainer.scrollTop -= top - selected.top;
    else if (selected.bottom > bottom) scrollContainer.scrollTop += selected.bottom - bottom;
}
watch(() => props.selected, keepSelectedRowVisible, { flush: 'post' });
watch(
    () => props.opened,
    (opened, previous) => {
        if (opened === previous) return;
        const list = layoutRoot.value?.querySelector<HTMLElement>('.split-list, .separate-list');
        if (opened) {
            listTop = list?.scrollTop || 0;
            windowTop = window.scrollY;
        }
        void nextTick(() => {
            const separate =
                window.matchMedia('(max-width: 700px)').matches ||
                preferences.value.preview === 'separate';
            if (!opened) {
                const restored = layoutRoot.value?.querySelector<HTMLElement>(
                    '.split-list, .separate-list'
                );
                if (restored) restored.scrollTop = listTop;
            }
            if (separate) window.scrollTo({ top: opened ? 0 : windowTop, behavior: 'instant' });
        });
    }
);
async function update(patch: Partial<MailLayout>) {
    if (saving.value) return;
    saving.value = true;
    error.value = '';
    try {
        await workspace.updateLayout(props.kind, patch);
    } catch {
        error.value = t('Could not save workspace preferences.');
    } finally {
        saving.value = false;
    }
}
</script>
<template>
    <NotificationBanner
        kind="error"
        :message="error"
    />
    <div
        v-if="hasConferences"
        class="mail-workspace-toolbar"
    >
        <slot name="toolbar" />
    </div>
    <div
        ref="layoutRoot"
        class="mail-layout"
        :class="[
            preferences.density,
            {
                'detail-open': detailVisible,
                'with-conferences': showConferences && !singleStage,
                'single-stage': singleStage,
                'conference-stage': conferenceStage,
                separate: preferences.preview === 'separate'
            }
        ]"
    >
        <aside
            v-if="hasConferences"
            v-show="showConferences"
            class="conference-pane"
        >
            <div
                v-if="singleStage"
                class="actions conference-navigation"
            >
                <button
                    class="secondary"
                    @click="changeStage('messages')"
                >
                    <AppIcon name="right" />{{ t('Back to messages') }}
                </button>
                <button
                    v-if="opened"
                    class="secondary"
                    @click="changeStage('detail')"
                >
                    <AppIcon name="right" />{{
                        composing ? t('Back to editor') : t('Back to message')
                    }}
                </button>
            </div>
            <slot name="conferences" />
        </aside>
        <div
            class="mail-workspace-content"
            v-show="!singleStage || !showConferences"
        >
            <div
                v-if="hasConferences"
                class="actions conference-navigation"
            >
                <button
                    v-if="!showConferences"
                    class="secondary"
                    @click="changeStage('conferences')"
                >
                    <AppIcon name="echo" />{{ t('Conferences') }}
                </button>
                <button
                    v-if="stage === 'detail' && opened && !listVisible"
                    class="secondary"
                    :disabled="busy"
                    v-shortcut="'mail.back'"
                    data-shortcut-hidden="layout"
                    @click="back"
                >
                    <AppIcon name="left" />{{ t('Back to messages') }}
                </button>
                <button
                    v-if="stage !== 'detail' && opened"
                    class="secondary"
                    @click="changeStage('detail')"
                >
                    <AppIcon name="right" />{{
                        composing ? t('Back to editor') : t('Back to message')
                    }}
                </button>
            </div>
            <SplitPane
                :direction="preferences.preview"
                :ratio="preferences.ratio"
                :list-visible="listVisible"
                :detail-visible="detailVisible"
                @resize="(ratio) => update({ ratio })"
            >
                <template #list>
                    <MailListTools
                        v-model="query"
                        :preferences="preferences"
                        :kind="kind"
                        :busy="busy || saving"
                        @update="update"
                    >
                        <template #toolbar
                            ><slot
                                v-if="!hasConferences"
                                name="toolbar"
                        /></template>
                        <template #filters><slot name="filters" /></template>
                        <template #options><slot name="options" /></template>
                        <template #search-status><slot name="search-status" /></template>
                    </MailListTools>
                    <slot name="list" />
                </template>
                <button
                    v-if="opened && !hasConferences"
                    class="secondary mail-back"
                    :disabled="busy"
                    v-shortcut="'mail.back'"
                    data-shortcut-hidden="layout"
                    @click="back"
                >
                    <AppIcon name="left" />{{ t('Back to messages') }}
                </button>
                <slot />
            </SplitPane>
        </div>
    </div>
</template>
<style scoped>
.mail-back {
    margin-bottom: 10px;
}
.mail-layout {
    min-width: 0;
}
.mail-layout.with-conferences {
    display: grid;
    grid-template-columns: 220px minmax(0, 1fr);
    gap: 0;
}
.mail-layout.with-conferences .conference-pane {
    border-right: 1px solid var(--line);
}
.mail-workspace-content {
    min-width: 0;
}
.conference-pane {
    min-width: 0;
    padding-inline: 12px;
    overflow: auto;
    overscroll-behavior: contain;
    height: calc(100dvh - 270px);
    min-height: 400px;
}
.mail-layout .conference-navigation {
    display: flex;
    flex-wrap: nowrap;
    align-items: center;
    gap: 8px;
    margin: 0 0 8px;
    padding-left: 16px;
}
.conference-navigation button {
    min-height: 32px;
    padding: 5px 8px;
    font-size: 13px;
    font-weight: 500;
    white-space: nowrap;
}
.conference-pane :deep(.echo-conference-list) {
    min-width: 0;
    margin: 0;
}
@media (min-width: 701px) {
    .mail-layout .conference-pane :deep(.echo-conference-list) {
        padding-top: 10px;
    }
}
.conference-pane :deep(.echo-conference-rows) {
    max-height: none;
    overflow: visible;
}
.conference-navigation:empty {
    display: none;
}
.mail-layout:has(.conference-pane) :deep(.separate-detail) {
    max-width: none;
}
.mail-layout.single-stage :deep(.split-pane) {
    display: block;
}
.mail-layout.single-stage :deep(.split-handle) {
    display: none;
}
.mail-layout :deep(.split-list:has(> .mail-list-empty)) {
    display: flex;
    flex-direction: column;
}
.mail-layout :deep(.mail-list-tools) {
    flex-shrink: 0;
}
.mail-layout :deep(.mail-list-empty) {
    flex: 1;
    display: grid;
    place-items: center;
    text-align: center;
}
.mail-layout :deep(.mail-list-empty p) {
    margin: 0;
    padding: 16px;
}
.mail-layout :deep(.mail-reader-empty) {
    display: grid;
    place-items: center;
    min-height: 100%;
    box-sizing: border-box;
    margin: 0;
    text-align: center;
}
.mail-layout :deep(.mail-reader-empty p) {
    margin: 0;
    padding: 16px;
}
.mail-layout :deep(.separate-detail) {
    max-width: 1100px;
    width: 100%;
    box-sizing: border-box;
    margin: auto;
}
@media (max-width: 700px) {
    .mail-layout :deep(.card) {
        box-shadow: none;
        border-radius: 6px;
        padding: 16px;
    }
    .mail-layout.compact :deep(.mail-row) {
        margin-bottom: 0;
        padding: 8px;
        gap: 2px;
        border-width: 0 0 1px;
        border-radius: 0;
    }
    .mail-layout.compact :deep(.echo-message-rows > li) {
        padding-top: 3px;
        padding-bottom: 3px;
    }
    .mail-layout.compact :deep(.mail-row small),
    .mail-layout.compact :deep(.echo-message-open small) {
        display: none;
    }

    .mail-layout.detail-open :deep(.split-list),
    .mail-layout:not(.detail-open) :deep(.split-detail) {
        display: none;
    }
}
</style>
