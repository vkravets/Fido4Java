<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { Compartment, EditorState, StateField } from '@codemirror/state';
import type { Range } from '@codemirror/state';
import { Decoration, EditorView, keymap } from '@codemirror/view';
import type { DecorationSet } from '@codemirror/view';
import {
    defaultKeymap,
    history,
    historyKeymap,
    isolateHistory,
    undo,
    redo
} from '@codemirror/commands';
import { fidoStyleRanges, fidoText } from '../fido-text.ts';
import { formatFidoLines } from '../editor-format.ts';
import { themeStyle, useMailAppearance } from '../mail-themes.ts';
import { t, locale } from '../i18n.ts';
import FidoText from './FidoText.vue';
const model = defineModel<string>({ required: true });
const props = defineProps<{
    disabled?: boolean;
    initialMode?: 'visual' | 'source' | 'preview';
    encoding?: string;
}>();
const host = ref<HTMLElement>();
const source = ref(props.initialMode === 'source');
const preview = ref(props.initialMode === 'preview');
const appearance = useMailAppearance();
const colors = computed(() => themeStyle(appearance.find(appearance.editor.value)));
const bytes = computed(() => new TextEncoder().encode(model.value).length);
const decorations = new Compartment();
const editable = new Compartment();
const accessibility = new Compartment();
const attributes = () =>
    EditorView.contentAttributes.of({
        'aria-label': t('Message text'),
        'aria-multiline': 'true',
        role: 'textbox',
        spellcheck: 'true',
        'aria-readonly': String(!!props.disabled),
        'aria-disabled': String(!!props.disabled)
    });
let editor: EditorView | undefined;
function highlight(state: EditorState): DecorationSet {
    const ranges: Range<Decoration>[] = [];
    const text = state.doc.toString();
    const lines = fidoText(text, false);
    for (let number = 1; number <= state.doc.lines; number++) {
        const line = state.doc.line(number);
        ranges.push(Decoration.line({ class: lines[number - 1]!.kind }).range(line.from));
    }
    for (const range of fidoStyleRanges(text)) {
        if (range.markers === false) {
            ranges.push(Decoration.mark({ class: range.style }).range(range.from, range.to));
            continue;
        }
        ranges.push(Decoration.mark({ class: range.style }).range(range.from + 1, range.to - 1));
        if (
            state.selection.ranges.some(
                (selection) => selection.from <= range.to && selection.to >= range.from
            )
        )
            continue;
        ranges.push(Decoration.replace({}).range(range.from, range.from + 1));
        ranges.push(Decoration.replace({}).range(range.to - 1, range.to));
    }
    return Decoration.set(ranges, true);
}
const highlighted = StateField.define<DecorationSet>({
    create: highlight,
    update: (value, transaction) =>
        transaction.docChanged || transaction.selection ? highlight(transaction.state) : value,
    provide: (field) => EditorView.decorations.from(field)
});
onMounted(() => {
    editor = new EditorView({
        parent: host.value,
        state: EditorState.create({
            doc: model.value.replaceAll('\r\n', '\n').replaceAll('\r', '\n'),
            extensions: [
                EditorView.cspNonce.of(
                    document.querySelector<HTMLMetaElement>('meta[name="jftn-style-nonce"]')
                        ?.content || ''
                ),
                history(),
                keymap.of([
                    {
                        key: 'Mod-b',
                        run: () => {
                            wrap('*');
                            return true;
                        }
                    },
                    {
                        key: 'Mod-i',
                        run: () => {
                            wrap('/');
                            return true;
                        }
                    },
                    {
                        key: 'Mod-u',
                        run: () => {
                            wrap('_');
                            return true;
                        }
                    },
                    ...defaultKeymap,
                    ...historyKeymap
                ]),
                EditorView.lineWrapping,
                accessibility.of(attributes()),
                decorations.of(source.value ? [] : highlighted),
                editable.of([
                    EditorState.readOnly.of(!!props.disabled),
                    EditorView.editable.of(!props.disabled)
                ]),
                EditorView.theme({
                    '&': {
                        fontSize: '14px',
                        backgroundColor: 'var(--mail-background)',
                        color: 'var(--mail-text)'
                    },
                    '.cm-content': { fontFamily: 'monospace', minHeight: '300px' },
                    '.cm-cursor': { borderLeftColor: 'var(--mail-caret)' },
                    '&.cm-focused .cm-selectionBackground, .cm-selectionBackground, ::selection': {
                        backgroundColor: 'var(--mail-selection)'
                    }
                }),
                EditorView.updateListener.of((update) => {
                    if (update.docChanged) model.value = update.state.doc.toString();
                })
            ]
        })
    });
});
watch(model, (value) => {
    if (!editor || editor.state.doc.toString() === value) return;
    editor.dispatch({ changes: { from: 0, to: editor.state.doc.length, insert: value } });
});
watch(source, (value) =>
    editor?.dispatch({ effects: decorations.reconfigure(value ? [] : highlighted) })
);
watch(
    () => props.disabled,
    (value) =>
        editor?.dispatch({
            effects: editable.reconfigure([
                EditorState.readOnly.of(!!value),
                EditorView.editable.of(!value)
            ])
        })
);
watch([locale, () => props.disabled], () =>
    editor?.dispatch({ effects: accessibility.reconfigure(attributes()) })
);
onBeforeUnmount(() => editor?.destroy());
function wrap(marker: string) {
    if (!editor || props.disabled) return;
    const { from, to } = editor.state.selection.main;
    const selected = editor.state.sliceDoc(from, to);
    const surrounding =
        from > 0 &&
        editor.state.sliceDoc(from - marker.length, from) === marker &&
        editor.state.sliceDoc(to, to + marker.length) === marker;
    const start = surrounding ? from - marker.length : from;
    const end = surrounding ? to + marker.length : to;
    const insert = surrounding ? selected : formatFidoLines(selected, marker);
    editor.dispatch({
        annotations: isolateHistory.of('full'),
        changes: { from: start, to: end, insert },
        selection: {
            anchor: selected ? start : start + marker.length,
            head: selected ? start + insert.length : start + marker.length
        }
    });
    editor.focus();
}
function quote() {
    if (!editor || props.disabled) return;
    const selection = editor.state.selection.main;
    const from = editor.state.doc.lineAt(selection.from).from;
    const to = editor.state.doc.lineAt(selection.to).to;
    editor.dispatch({
        annotations: isolateHistory.of('full'),
        changes: {
            from,
            to,
            insert: editor.state
                .sliceDoc(from, to)
                .split('\n')
                .map((line) => '> ' + line)
                .join('\n')
        }
    });
    editor.focus();
}
function historyAction(action: typeof undo) {
    if (editor && !props.disabled) {
        action(editor);
        editor.focus();
    }
}
function setSource(value: boolean) {
    source.value = value;
    preview.value = false;
}
defineExpose({
    focusAt(position: number) {
        editor?.dispatch({ selection: { anchor: Math.min(position, editor.state.doc.length) } });
        editor?.focus();
    }
});
</script>
<template>
    <div
        class="fido-editor"
        :style="colors"
    >
        <div
            class="editor-toolbar actions"
            role="toolbar"
            :aria-label="t('Text formatting')"
        >
            <button
                type="button"
                :disabled="disabled || preview"
                :aria-label="t('Bold')"
                @click="wrap('*')"
            >
                <b>B</b>
            </button>
            <button
                type="button"
                :disabled="disabled || preview"
                :aria-label="t('Italic')"
                @click="wrap('/')"
            >
                <i>I</i>
            </button>
            <button
                type="button"
                :disabled="disabled || preview"
                :aria-label="t('Underline')"
                @click="wrap('_')"
            >
                <u>U</u>
            </button>
            <button
                type="button"
                :disabled="disabled || preview"
                :aria-label="t('Reverse')"
                @click="wrap('#')"
            >
                #
            </button>
            <button
                type="button"
                :disabled="disabled || preview"
                @click="quote"
            >
                {{ t('Quote') }}
            </button>
            <button
                type="button"
                :disabled="disabled || preview"
                @click="historyAction(undo)"
            >
                {{ t('Undo') }}
            </button>
            <button
                type="button"
                :disabled="disabled || preview"
                @click="historyAction(redo)"
            >
                {{ t('Redo') }}
            </button>
            <button
                type="button"
                :aria-pressed="!source && !preview"
                @click="setSource(false)"
            >
                {{ t('Visual') }}
            </button>
            <button
                type="button"
                :aria-pressed="source && !preview"
                @click="setSource(true)"
            >
                {{ t('Source text') }}
            </button>
            <button
                type="button"
                :aria-pressed="preview"
                @click="preview = !preview"
            >
                {{ t('Preview') }}
            </button>
        </div>
        <div
            v-show="!preview"
            ref="host"
            class="fido-text editor-host"
        ></div>
        <FidoText
            v-if="preview"
            :theme-id="appearance.editor.value"
            :text="model"
        />
        <p
            v-if="bytes > 60000"
            class="muted"
            role="status"
        >
            {{ t('Message exceeds 60000 UTF-8 bytes.') }}
        </p>
        <small
            >{{ t('Plain text') }} · {{ encoding || 'UTF-8' }} · {{ bytes }} / 60000
            {{ t('UTF-8 bytes') }}</small
        >
    </div>
</template>
<style scoped>
.editor-toolbar {
    flex-wrap: wrap;
    gap: 4px;
    padding: 6px;
    border: 1px solid var(--line);
    border-radius: 6px 6px 0 0;
}
.editor-toolbar button {
    padding: 6px 9px;
    background: var(--surface);
    color: var(--text);
}
.editor-toolbar button[aria-pressed='true'] {
    background: var(--accent-soft);
    border-color: var(--accent);
    color: var(--accent);
}
.editor-host {
    border: 1px solid var(--line);
    max-height: 60dvh;
    overflow: auto;
    padding: 4px;
}
.fido-editor {
    min-width: 0;
}
</style>
