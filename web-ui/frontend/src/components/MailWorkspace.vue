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
}>();
defineEmits<{ back: [] }>();
const workspace = useWorkspace();
const query = defineModel<string>('query', { default: '' });
provide(mailSearchKey, query);
const preferences = computed(() => workspace.value.value[props.kind]);
const desktop = useMedia('(min-width: 701px)');
const listHidden = ref(true);
const canToggleList = computed(
    () => !!props.composing && desktop.value && preferences.value.preview !== 'separate'
);
provide(composerListKey, {
    available: canToggleList,
    hidden: listHidden,
    toggle() {
        if (canToggleList.value) listHidden.value = !listHidden.value;
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
const conferenceStage = ref(false);
const singleStage = computed(
    () => hasConferences.value && (!desktop.value || (workWidth.value > 0 && workWidth.value < 640))
);
const showConferences = computed(() => {
    if (!hasConferences.value) return false;
    if (conferenceStage.value) return true;
    if (singleStage.value) return !props.conference && !props.opened;
    if (props.composing) return false;
    if (!props.opened) return true;
    return (
        preferences.value.preview !== 'separate' &&
        workspace.value.value.conferencePanels === 'all' &&
        (preferences.value.preview === 'below' || workWidth.value >= 840)
    );
});
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
        conferenceStage.value = false;
    }
);
watch(
    () => props.opened,
    (opened) => {
        if (opened) conferenceStage.value = false;
    }
);
let listTop = 0;
let windowTop = 0;
watch(
    () => props.selected,
    () => {
        const list = layoutRoot.value?.querySelector<HTMLElement>('.split-list');
        const row = list?.querySelector<HTMLElement>('[data-shortcut-selected]');
        if (!list || !row || !list.getClientRects().length || !list.clientHeight) return;

        // Scroll only the list. scrollIntoView would also move the page and reader.
        const bounds = list.getBoundingClientRect();
        const tools = list.querySelector<HTMLElement>('.mail-list-tools');
        const top = Math.max(bounds.top, tools?.getBoundingClientRect().bottom || bounds.top);
        const bottom = bounds.top + list.clientTop + list.clientHeight;
        const selected = row.getBoundingClientRect();
        if (selected.top < top) list.scrollTop -= top - selected.top;
        else if (selected.bottom > bottom) list.scrollTop += selected.bottom - bottom;
    },
    { flush: 'post' }
);
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
        ref="layoutRoot"
        class="mail-layout"
        :class="[
            preferences.density,
            {
                'detail-open': opened,
                'with-conferences': showConferences && !singleStage,
                'single-stage': singleStage,
                'conference-stage': conferenceStage,
                'echo-no-detail': hasConferences && (!opened || conferenceStage),
                separate: preferences.preview === 'separate',
                'composer-list-hidden': canToggleList && listHidden && !conferenceStage
            }
        ]"
    >
        <aside
            v-if="hasConferences"
            v-show="showConferences"
            class="conference-pane"
        >
            <button
                v-if="conferenceStage || singleStage"
                class="secondary"
                @click="conferenceStage = false"
            >
                <AppIcon name="right" />{{ t('Back to messages') }}
            </button>
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
                    @click="conferenceStage = true"
                >
                    <AppIcon name="echo" />{{ t('Conferences') }}
                </button>
                <button
                    v-if="conferenceStage && opened"
                    class="secondary"
                    @click="conferenceStage = false"
                >
                    <AppIcon name="right" />{{
                        composing ? t('Back to editor') : t('Back to message')
                    }}
                </button>
            </div>
            <SplitPane
                v-if="preferences.preview !== 'separate'"
                :direction="preferences.preview"
                :ratio="preferences.ratio"
                @resize="(ratio) => update({ ratio })"
            >
                <template #list
                    ><MailListTools
                        v-model="query"
                        :preferences="preferences"
                        :kind="kind"
                        :busy="busy || saving"
                        @update="update"
                    >
                        <template #toolbar><slot name="toolbar" /></template>
                        <template #filters><slot name="filters" /></template>
                        <template #options><slot name="options" /></template>
                        <template #search-status
                            ><slot name="search-status"
                        /></template> </MailListTools
                    ><slot name="list"
                /></template>
                <button
                    v-if="opened"
                    class="secondary mail-back"
                    :disabled="busy"
                    v-shortcut="'mail.back'"
                    data-shortcut-hidden="layout"
                    @click="$emit('back')"
                >
                    <AppIcon name="left" />{{ t('Back to messages') }}
                </button>
                <slot />
            </SplitPane>
            <template v-else>
                <section
                    v-show="!opened || conferenceStage"
                    class="separate-list"
                >
                    <MailListTools
                        v-model="query"
                        :preferences="preferences"
                        :kind="kind"
                        :busy="busy || saving"
                        @update="update"
                    >
                        <template #toolbar><slot name="toolbar" /></template>
                        <template #filters><slot name="filters" /></template>
                        <template #options><slot name="options" /></template>
                        <template #search-status><slot name="search-status" /></template>
                    </MailListTools>
                    <slot name="list" />
                </section>
                <section
                    v-if="opened"
                    v-show="!conferenceStage"
                    class="separate-detail"
                >
                    <button
                        class="secondary mail-back"
                        :disabled="busy"
                        v-shortcut="'mail.back'"
                        data-shortcut-hidden="layout"
                        @click="$emit('back')"
                    >
                        <AppIcon name="left" />{{ t('Back to messages') }}</button
                    ><slot />
                </section>
            </template>
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
    gap: 8px;
}
.mail-workspace-content {
    min-width: 0;
}
.conference-pane {
    min-width: 0;
    overflow: auto;
    overscroll-behavior: contain;
    height: calc(100dvh - 270px);
    min-height: 400px;
}
.conference-pane :deep(.echo-conference-list) {
    min-width: 0;
    margin: 0;
}
.conference-pane :deep(.echo-conference-rows) {
    max-height: none;
    overflow: visible;
}
.conference-navigation:empty {
    display: none;
}
.mail-layout:has(.conference-pane) .separate-detail {
    max-width: none;
}
.mail-layout.echo-no-detail :deep(.split-pane) {
    grid-template-columns: minmax(0, 1fr);
    grid-template-rows: minmax(0, 1fr);
}
.mail-layout.echo-no-detail :deep(.split-detail),
.mail-layout.echo-no-detail :deep(.split-handle) {
    display: none;
}
.mail-layout.single-stage :deep(.split-pane) {
    display: block;
}
.mail-layout.single-stage :deep(.split-handle),
.mail-layout.single-stage.detail-open:not(.conference-stage) :deep(.split-list),
.mail-layout.single-stage:not(.detail-open) :deep(.split-detail) {
    display: none;
}
.mail-layout.composer-list-hidden :deep(.split-pane.right),
.mail-layout.composer-list-hidden :deep(.split-pane.below) {
    grid-template-columns: minmax(0, 1fr);
    grid-template-rows: minmax(0, 1fr);
}
.mail-layout.composer-list-hidden :deep(.split-list),
.mail-layout.composer-list-hidden :deep(.split-handle) {
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
.separate-detail {
    max-width: 1100px;
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
