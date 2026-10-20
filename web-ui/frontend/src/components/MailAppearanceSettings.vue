<script setup lang="ts">
import { computed, ref } from 'vue';
import { t } from '../i18n.ts';
import { themeLabel } from '../mail-theme-labels.ts';
import { mailColors, mailEncodings, mailTheme, useMailAppearance } from '../mail-themes.ts';
import type { MailTheme } from '../mail-themes.ts';
import type { AppState } from '../state.ts';
import MailTypographyFields from './MailTypographyFields.vue';
import MailAppearancePreview from './MailAppearancePreview.vue';
import type { AppearancePreviewMode } from './MailAppearancePreview.vue';
const props = defineProps<{ state: AppState }>();
const appearance = useMailAppearance();
const reader = ref(appearance.find(appearance.reader.value).id);
const editor = ref(appearance.find(appearance.editor.value).id);
const encoding = ref(appearance.encoding.value);
const readerTypography = ref({ ...appearance.readerTypography.value });
const editorTypography = ref({ ...appearance.editorTypography.value });
const selected = ref(editor.value);
const draft = ref<MailTheme>(copy(appearance.find(selected.value)));
const previewMode = ref<AppearancePreviewMode>('reader');
const custom = computed(() => appearance.custom.value.some((theme) => theme.id === selected.value));
function copy(theme: MailTheme): MailTheme {
    return { ...theme, colors: { ...theme.colors } };
}
function choose() {
    draft.value = copy(appearance.find(selected.value));
    previewMode.value = 'custom';
}
async function save() {
    await appearance.save(
        reader.value,
        editor.value,
        appearance.custom.value,
        encoding.value,
        readerTypography.value,
        editorTypography.value
    );
    props.state.notice.value = t('Settings saved.');
}
async function saveTheme() {
    const id = custom.value ? selected.value : 'custom-' + crypto.randomUUID().slice(0, 12);
    const theme = mailTheme({ ...draft.value, id });
    const themes = [...appearance.custom.value.filter((value) => value.id !== id), theme];
    await appearance.save(
        reader.value,
        id,
        themes,
        encoding.value,
        readerTypography.value,
        editorTypography.value
    );
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
        previewMode.value = 'custom';
    } finally {
        input.value = '';
    }
}
async function remove() {
    if (!custom.value || !window.confirm(t('Delete this theme?'))) return;
    const next = appearance.custom.value.filter((theme) => theme.id !== selected.value);
    reader.value = reader.value === selected.value ? 'paper' : reader.value;
    editor.value = editor.value === selected.value ? 'paper' : editor.value;
    await appearance.save(
        reader.value,
        editor.value,
        next,
        encoding.value,
        readerTypography.value,
        editorTypography.value
    );
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
        <div class="appearance-layout">
            <fieldset :disabled="state.busy.value">
                <div class="form-grid">
                    <label
                        >{{ t('Reading theme')
                        }}<select
                            v-model="reader"
                            @change="previewMode = 'reader'"
                        >
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
                        }}<select
                            v-model="editor"
                            @change="previewMode = 'editor'"
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
                </div>
                <MailTypographyFields
                    v-model="readerTypography"
                    :label="t('Reading font')"
                    @update:model-value="previewMode = 'reader'"
                />
                <MailTypographyFields
                    v-model="editorTypography"
                    :label="t('Editor font')"
                    @update:model-value="previewMode = 'editor'"
                />
                <p class="muted">
                    {{
                        t(
                            'For continuous box drawing, use a monospace font and line height 1.0. Fonts must be installed on your device.'
                        )
                    }}
                </p>
                <label
                    >{{ t('Default outgoing encoding') }}
                    <select
                        v-model="encoding"
                        @change="previewMode = 'editor'"
                    >
                        <option
                            v-for="value in mailEncodings"
                            :key="value"
                            :value="value"
                        >
                            {{ value }}
                        </option>
                    </select>
                </label>
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
                    {{
                        t('Built-in and file themes are read-only. Changes create a personal copy.')
                    }}
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
                            @input="previewMode = 'custom'"
                    /></label>
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
            <MailAppearancePreview
                v-model="previewMode"
                :reader="appearance.find(reader)"
                :editor="appearance.find(editor)"
                :custom="draft"
                :encoding="encoding"
                :reader-typography="readerTypography"
                :editor-typography="editorTypography"
            />
        </div>
    </section>
</template>
<style scoped>
.appearance-layout {
    display: grid;
    grid-template-columns: minmax(260px, 340px) minmax(0, 1fr);
    gap: 24px;
    align-items: start;
}
.appearance-layout .form-grid {
    grid-template-columns: minmax(0, 1fr);
}
.appearance-layout label {
    font-size: 13px;
}
.appearance-layout button {
    min-height: 30px;
    padding: 5px 9px;
    font-size: 13px;
}
.appearance-layout input,
.appearance-layout select {
    min-height: 32px;
    padding: 5px 8px;
}
.theme-color-grid {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(120px, 1fr));
    gap: 12px;
}
.theme-color-grid input {
    height: 40px;
    padding: 4px;
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
@media (max-width: 1000px) {
    .appearance-layout {
        grid-template-columns: minmax(0, 1fr);
    }
}
</style>
