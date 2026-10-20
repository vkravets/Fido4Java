<script setup lang="ts">
import { computed, ref, provide, watch, nextTick } from 'vue';
import { mailSearchKey } from '../mail-search.ts';
import { t } from '../i18n.ts';
import { useWorkspace } from '../workspace.ts';
import type { MailKind, MailLayout } from '../workspace.ts';
import SplitPane from './SplitPane.vue';
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
const positions = [
    ['right', 'Preview on the right'],
    ['below', 'Preview below'],
    ['separate', 'No preview']
] as const;
</script>
<template>
    <NotificationBanner
        kind="error"
        :message="error"
    />
    <div
        class="mail-display-controls actions"
        :class="{ 'detail-open': opened }"
    >
        <label
            >{{ t('Search loaded messages')
            }}<input
                v-model="query"
                type="search"
                maxlength="120"
        /></label>
        <label
            >{{ t('Preview position')
            }}<select
                :value="preferences.preview"
                :disabled="busy || saving"
                @change="
                    update({
                        preview: ($event.target as HTMLSelectElement).value as MailLayout['preview']
                    })
                "
            >
                <option
                    v-for="position in positions"
                    :key="position[0]"
                    :value="position[0]"
                >
                    {{ t(position[1]) }}
                </option>
            </select></label
        >
        <label
            >{{ t('List density')
            }}<select
                :value="preferences.density"
                :disabled="busy || saving"
                @change="
                    update({
                        density: ($event.target as HTMLSelectElement).value as MailLayout['density']
                    })
                "
            >
                <option value="compact">{{ t('Compact') }}</option>
                <option value="comfortable">{{ t('Comfortable') }}</option>
            </select></label
        >
    </div>
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
            <template #list><slot name="list" /></template>
            <button
                v-if="opened"
                class="secondary mail-back"
                :disabled="busy"
                @click="$emit('back')"
            >
                ← {{ t('Back to messages') }}
            </button>
            <slot />
        </SplitPane>
        <template v-else>
            <section
                v-show="!opened"
                class="separate-list"
            >
                <slot name="list" />
            </section>
            <section
                v-if="opened"
                class="separate-detail"
            >
                <button
                    class="secondary"
                    :disabled="busy"
                    @click="$emit('back')"
                >
                    ← {{ t('Back to messages') }}</button
                ><slot />
            </section>
        </template>
    </div>
</template>
<style scoped>
.mail-display-controls {
    margin: 12px 0;
    flex-wrap: wrap;
    align-items: end;
}
.mail-display-controls label {
    max-width: 240px;
}
.mail-back {
    margin-bottom: 10px;
}
.mail-layout {
    min-width: 0;
}
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
.separate-detail {
    max-width: 1100px;
    margin: auto;
}
@media (max-width: 700px) {
    .mail-layout.detail-open :deep(.split-list),
    .mail-layout:not(.detail-open) :deep(.split-detail) {
        display: none;
    }
    .mail-display-controls.detail-open {
        display: none;
    }
    .mail-display-controls label {
        flex: 1 1 140px;
    }
}
</style>
