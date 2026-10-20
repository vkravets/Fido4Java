<script setup lang="ts">
import { computed, ref } from 'vue';
import { t } from '../i18n.ts';
import { themeLabel } from '../mail-theme-labels.ts';
import { mailColors, mailTheme, themeStyle, useMailAppearance } from '../mail-themes.ts';
import type { MailTheme } from '../mail-themes.ts';
import type { AppState } from '../state.ts';
import FidoText from './FidoText.vue';
const props = defineProps<{ state: AppState }>();
const appearance = useMailAppearance();
const reader = ref(appearance.find(appearance.reader.value).id);
const editor = ref(appearance.find(appearance.editor.value).id);
const selected = ref(editor.value);
const draft = ref<MailTheme>(copy(appearance.find(selected.value)));
const preview =
    'Hello Bob,\n\nAE> Welcome to the conference!\nBE>> Thank you.\n\nThis is *bold*, /italic/, _underlined_ and #highlighted#.\nhttps://example.test\n\n-- \nAlex\n--- JFtn\n * Origin: Example station (1:500/100.1)';
const custom = computed(() => appearance.custom.value.some((theme) => theme.id === selected.value));
function copy(theme: MailTheme): MailTheme {
    return { ...theme, colors: { ...theme.colors } };
}
function choose() {
    draft.value = copy(appearance.find(selected.value));
}
async function save() {
    await appearance.save(reader.value, editor.value, appearance.custom.value);
    props.state.notice.value = t('Settings saved.');
}
async function saveTheme() {
    const id = custom.value ? selected.value : 'custom-' + crypto.randomUUID().slice(0, 12);
    const theme = mailTheme({ ...draft.value, id });
    const themes = [...appearance.custom.value.filter((value) => value.id !== id), theme];
    await appearance.save(reader.value, id, themes);
    editor.value = id;
    selected.value = id;
    choose();
    props.state.notice.value = t('Settings saved.');
}
function exportTheme() {
    const url = URL.createObjectURL(
        new Blob([JSON.stringify(draft.value, null, 2)], { type: 'application/json' })
    );
    const link = document.createElement('a');
    link.href = url;
    link.download = draft.value.id + '.json';
    link.click();
    URL.revokeObjectURL(url);
}
async function importTheme(event: Event) {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    try {
        if (!file) return;
        if (file.size > 32768) throw new Error(t('Theme file is too large.'));
        draft.value = mailTheme(JSON.parse(await file.text()));
        selected.value = '';
    } finally {
        input.value = '';
    }
}
async function remove() {
    if (!custom.value || !window.confirm(t('Delete this theme?'))) return;
    const next = appearance.custom.value.filter((theme) => theme.id !== selected.value);
    reader.value = reader.value === selected.value ? 'paper' : reader.value;
    editor.value = editor.value === selected.value ? 'paper' : editor.value;
    await appearance.save(reader.value, editor.value, next);
    selected.value = editor.value;
    choose();
}
const labels = {
    background: 'Background',
    text: 'Text',
    quote1: 'Quote level 1',
    quote2: 'Quote level 2',
    quote3: 'Quote level 3',
    ending: 'Signature and origin',
    link: 'Links',
    selection: 'Selection',
    search: 'Search matches',
    caret: 'Caret'
} as const;
</script>
<template>
    <section class="card mail-appearance">
        <h2>{{ t('Mail appearance') }}</h2>
        <p>{{ t('Mail themes are independent of the application theme.') }}</p>
        <fieldset :disabled="state.busy.value">
            <div class="form-grid">
                <label
                    >{{ t('Reading theme')
                    }}<select v-model="reader">
                        <option
                            v-for="theme in appearance.themes.value"
                            :key="theme.id"
                            :value="theme.id"
                        >
                            {{ themeLabel(theme) }}
                        </option>
                    </select></label
                ><label
                    >{{ t('Editor theme')
                    }}<select v-model="editor">
                        <option
                            v-for="theme in appearance.themes.value"
                            :key="theme.id"
                            :value="theme.id"
                        >
                            {{ themeLabel(theme) }}
                        </option>
                    </select></label
                >
            </div>
            <button
                class="primary"
                @click="state.run(save)"
            >
                {{ t('Save preferences') }}
            </button>
            <hr />
            <label
                >{{ t('Customize theme')
                }}<select
                    v-model="selected"
                    @change="choose"
                >
                    <option
                        v-for="theme in appearance.themes.value"
                        :key="theme.id"
                        :value="theme.id"
                    >
                        {{ themeLabel(theme) }}
                    </option>
                </select></label
            >
            <p
                v-if="!custom"
                class="muted"
            >
                {{ t('Built-in and file themes are read-only. Changes create a personal copy.') }}
            </p>
            <label
                >{{ t('Theme name')
                }}<input
                    v-model="draft.name"
                    maxlength="80"
            /></label>
            <div class="theme-color-grid">
                <label
                    v-for="key in mailColors"
                    :key="key"
                    >{{ t(labels[key])
                    }}<input
                        v-model="draft.colors[key]"
                        type="color"
                /></label>
            </div>
            <div
                :style="themeStyle(draft)"
                class="mail-palette-preview"
            >
                <FidoText
                    :text="preview"
                    :theme="draft"
                />
            </div>
            <div class="actions">
                <button
                    class="primary"
                    @click="state.run(saveTheme)"
                >
                    {{ t('Save personal theme') }}</button
                ><button
                    class="secondary"
                    @click="exportTheme"
                >
                    {{ t('Export theme') }}</button
                ><button
                    v-if="custom"
                    class="danger"
                    @click="state.run(remove)"
                >
                    {{ t('Delete theme') }}
                </button>
            </div>
            <label
                >{{ t('Import theme')
                }}<input
                    type="file"
                    accept="application/json,.json"
                    @change="state.run(() => importTheme($event))"
            /></label>
        </fieldset>
    </section>
</template>
<style scoped>
.theme-color-grid {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(120px, 1fr));
    gap: 12px;
}
.theme-color-grid input {
    height: 40px;
    padding: 4px;
}
.mail-palette-preview {
    margin: 20px 0;
}
.mail-palette-preview :deep(.fido-text) {
    background: var(--mail-background);
    color: var(--mail-text);
}
hr {
    border: 0;
    border-top: 1px solid var(--line);
    margin: 20px 0;
}
fieldset {
    border: 0;
    padding: 0;
    min-width: 0;
}
</style>
