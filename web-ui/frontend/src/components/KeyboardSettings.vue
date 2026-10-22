<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { t } from '../i18n.ts';
import {
    bindingsFor,
    eventBinding,
    reservedBinding,
    shortcutConflict,
    shortcutPreferences,
    useShortcuts
} from '../shortcuts.ts';
import { shortcutActions } from '../shortcut-actions.ts';
import type { ShortcutAction, ShortcutProfile, ShortcutScope } from '../shortcut-actions.ts';
import type { AppState } from '../state.ts';
import SettingsSectionHeader from './SettingsSectionHeader.vue';
import AppIcon from './AppIcon.vue';
import NotificationBanner from './NotificationBanner.vue';
const props = defineProps<{ state: AppState }>();
const model = useShortcuts();
const draft = ref(shortcutPreferences(model.value.value));
const recording = ref<ShortcutAction>();
const error = ref('');
const dirty = computed(() => JSON.stringify(draft.value) !== JSON.stringify(model.value.value));
const conflict = computed(() => shortcutConflict(draft.value));
const groups: {
    id: ShortcutScope;
    label: 'Navigation and page' | 'Reading mail' | 'Writing mail' | 'Text editor';
}[] = [
    { id: 'global', label: 'Navigation and page' },
    { id: 'mail', label: 'Reading mail' },
    { id: 'compose', label: 'Writing mail' },
    { id: 'editor', label: 'Text editor' }
];
watch(model.value, () => {
    draft.value = shortcutPreferences(model.value.value);
});
function canLeave() {
    return !dirty.value || window.confirm(t('Unsaved keyboard shortcuts. Discard changes?'));
}
defineExpose({ canLeave });
function beforeUnload(event: BeforeUnloadEvent) {
    if (dirty.value) event.preventDefault();
}
onMounted(() => window.addEventListener('beforeunload', beforeUnload));
onBeforeUnmount(() => window.removeEventListener('beforeunload', beforeUnload));
function preset(profile: ShortcutProfile) {
    draft.value = shortcutPreferences({ profile, enabled: draft.value.enabled });
    recording.value = undefined;
    error.value = '';
}
function capture(event: KeyboardEvent, action: ShortcutAction) {
    if (recording.value !== action) return;
    event.preventDefault();
    event.stopPropagation();
    if (event.key === 'Escape') {
        recording.value = undefined;
        return;
    }
    if (event.isComposing || event.getModifierState('AltGraph')) return;
    const key = eventBinding(event);
    if (!key) {
        error.value = t('Unsupported key combination.');
        return;
    }
    if (reservedBinding(key)) {
        error.value = t('Shortcut is reserved or conflicts with another action: {key}', { key });
        return;
    }
    draft.value.overrides[action] = [key];
    recording.value = undefined;
    error.value = '';
}
async function save() {
    if (conflict.value || props.state.busy.value) return;
    await props.state.run(async () => {
        await model.save(draft.value);
        props.state.notice.value = t('Settings saved.');
    });
}
</script>
<template>
    <section class="card keyboard-settings">
        <SettingsSectionHeader :title="t('Keyboard shortcuts')">
            <template #actions
                ><button
                    type="button"
                    class="primary settings-icon-action"
                    :disabled="state.busy.value || !dirty || !!conflict"
                    :title="t('Save preferences')"
                    :aria-label="t('Save preferences')"
                    @click="save"
                >
                    <AppIcon name="save" /></button
            ></template>
        </SettingsSectionHeader>
        <form @submit.prevent="save">
            <fieldset :disabled="state.busy.value">
                <div class="keyboard-presets">
                    <label for="shortcut-preset">{{ t('Shortcut preset') }}</label>
                    <select
                        id="shortcut-preset"
                        :value="draft.profile"
                        @change="
                            preset(($event.target as HTMLSelectElement).value as ShortcutProfile)
                        "
                    >
                        <option value="jftn">{{ t('JFtn default') }}</option>
                        <option value="golded">{{ t('GoldED+ (web)') }}</option>
                    </select>
                    <label class="keyboard-enabled"
                        ><input
                            v-model="draft.enabled"
                            type="checkbox"
                        />{{ t('Enable keyboard shortcuts') }}</label
                    >
                </div>
                <p>{{ t('Changing the preset resets your unsaved key assignments.') }}</p>
                <p>
                    {{
                        t(
                            'Click a shortcut field and press the new combination. Escape cancels recording; Clear disables an action.'
                        )
                    }}
                </p>
                <p>
                    {{
                        t(
                            'Browser and system shortcuts cannot all be intercepted. GoldED+ uses the supported equivalents; Ctrl+L, Ctrl+R and Ctrl+Z keep their browser or editing meaning.'
                        )
                    }}
                </p>
                <NotificationBanner
                    :message="
                        error ||
                        (conflict
                            ? t('Shortcut is reserved or conflicts with another action: {key}', {
                                  key: conflict
                              })
                            : '')
                    "
                    kind="error"
                />
                <section
                    v-for="group in groups"
                    :key="group.id"
                    class="keyboard-group"
                >
                    <h3>{{ t(group.label) }}</h3>
                    <div
                        v-for="action in shortcutActions.filter(
                            (value) => value.scope === group.id
                        )"
                        :key="action.id"
                        class="keyboard-row"
                    >
                        <label :for="`shortcut-${action.id}`">{{ t(action.label) }}</label>
                        <div class="keyboard-binding">
                            <input
                                :id="`shortcut-${action.id}`"
                                readonly
                                data-shortcut-recorder
                                :value="
                                    recording === action.id
                                        ? ''
                                        : bindingsFor(draft, action.id).join(' / ')
                                "
                                :placeholder="
                                    recording === action.id
                                        ? t('Press a key combination')
                                        : t('Not assigned')
                                "
                                :aria-label="t(action.label)"
                                @focus="recording = action.id"
                                @click="recording = action.id"
                                @blur="recording = undefined"
                                @keydown="capture($event, action.id)"
                            />
                            <button
                                type="button"
                                class="secondary settings-icon-action"
                                :title="t('Clear shortcut')"
                                :aria-label="`${t('Clear shortcut')}: ${t(action.label)}`"
                                @click="draft.overrides[action.id] = []"
                            >
                                <AppIcon name="close" />
                            </button>
                            <button
                                type="button"
                                class="secondary settings-icon-action"
                                :title="t('Restore shortcut')"
                                :aria-label="`${t('Restore shortcut')}: ${t(action.label)}`"
                                @click="delete draft.overrides[action.id]"
                            >
                                <AppIcon name="refresh" />
                            </button>
                        </div>
                    </div>
                </section>
                <footer class="actions">
                    <button
                        type="button"
                        class="secondary"
                        @click="preset(draft.profile)"
                    >
                        {{ t('Restore preset') }}
                    </button>
                    <button
                        type="button"
                        class="text-button"
                        :disabled="!dirty"
                        @click="
                            draft = shortcutPreferences(model.value.value);
                            error = '';
                        "
                    >
                        {{ t('Discard changes') }}
                    </button>
                </footer>
            </fieldset>
        </form>
    </section>
</template>
<style scoped>
.keyboard-settings {
    --keyboard-columns: minmax(140px, 1fr) minmax(240px, 1.5fr);
}
.keyboard-presets {
    display: grid;
    grid-template-columns: var(--keyboard-columns);
    gap: 6px 12px;
    align-items: center;
    margin-bottom: 12px;
}
.keyboard-presets > label {
    margin: 0;
}
.keyboard-presets select {
    grid-column: 1;
    grid-row: 2;
}
.keyboard-presets .keyboard-enabled {
    grid-column: 2;
    grid-row: 2;
}
.keyboard-settings fieldset {
    border: 0;
    padding: 0;
    margin: 0;
    min-width: 0;
}
.keyboard-enabled {
    flex-direction: row;
    align-items: center;
}
.keyboard-enabled input {
    width: auto;
}
.keyboard-row {
    display: grid;
    grid-template-columns: var(--keyboard-columns);
    align-items: center;
    gap: 12px;
    padding: 7px 0;
    border-bottom: 1px solid var(--line);
}
.keyboard-row label {
    margin: 0;
}
.keyboard-binding {
    display: flex;
    align-items: center;
    gap: 6px;
    min-width: 0;
}
.keyboard-binding input {
    min-width: 0;
}
.keyboard-group {
    margin-bottom: 20px;
}
@media (max-width: 640px) {
    .keyboard-settings {
        --keyboard-columns: minmax(0, 1fr);
    }
    .keyboard-presets .keyboard-enabled {
        grid-column: 1;
        grid-row: 3;
    }
    .keyboard-row {
        grid-template-columns: minmax(0, 1fr);
        gap: 4px;
    }
}
</style>
