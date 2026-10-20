<script setup lang="ts">
import { computed, ref, provide, watch, nextTick } from 'vue';
import { mailSearchKey } from '../mail-search.ts';
import { t } from '../i18n.ts';
import { useWorkspace } from '../workspace.ts';
import type { MailKind, MailLayout } from '../workspace.ts';
import SplitPane from './SplitPane.vue';
import MailListTools from './MailListTools.vue';
import AppIcon from './AppIcon.vue';
import NotificationBanner from './NotificationBanner.vue';
const props = defineProps<{ kind: MailKind; opened: boolean; busy?: boolean }>();
defineEmits<{ back: [] }>();
const workspace = useWorkspace();
const query = ref('');
provide(mailSearchKey, query);
const preferences = computed(() => workspace.value.value[props.kind]);
const saving = ref(false);
const error = ref('');
const layoutRoot = ref<HTMLElement>();
let listTop = 0;
let windowTop = 0;
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
            { 'detail-open': opened, separate: preferences.preview === 'separate' }
        ]"
    >
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
                    :busy="busy || saving"
                    @update="update"
                >
                    <template #toolbar><slot name="toolbar" /></template>
                    <template #filters><slot name="filters" /></template>
                    <template #options><slot name="options" /></template> </MailListTools
                ><slot name="list"
            /></template>
            <button
                v-if="opened"
                class="secondary mail-back"
                :disabled="busy"
                @click="$emit('back')"
            >
                <AppIcon name="left" />{{ t('Back to messages') }}
            </button>
            <slot />
        </SplitPane>
        <template v-else>
            <section
                v-show="!opened"
                class="separate-list"
            >
                <MailListTools
                    v-model="query"
                    :preferences="preferences"
                    :busy="busy || saving"
                    @update="update"
                >
                    <template #toolbar><slot name="toolbar" /></template>
                    <template #filters><slot name="filters" /></template>
                    <template #options><slot name="options" /></template>
                </MailListTools>
                <slot name="list" />
            </section>
            <section
                v-if="opened"
                class="separate-detail"
            >
                <button
                    class="secondary mail-back"
                    :disabled="busy"
                    @click="$emit('back')"
                >
                    <AppIcon name="left" />{{ t('Back to messages') }}</button
                ><slot />
            </section>
        </template>
    </div>
</template>
<style scoped>
.mail-back {
    margin-bottom: 10px;
}
.mail-layout {
    min-width: 0;
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
