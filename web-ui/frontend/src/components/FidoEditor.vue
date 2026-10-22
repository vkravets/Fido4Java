<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { Compartment, EditorState, StateField } from '@codemirror/state';
import type { Range } from '@codemirror/state';
import { Decoration, drawSelection, EditorView, keymap, WidgetType } from '@codemirror/view';
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
import { dosGraphic, dosGraphicPattern } from '../dos-graphics.ts';
import { typographyStyle } from '../mail-typography.ts';
import { themeStyle, useMailAppearance } from '../mail-themes.ts';
import { t, locale } from '../i18n.ts';
import FidoText from './FidoText.vue';
import AppIcon from './AppIcon.vue';
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
const colors = computed(() => ({
    ...themeStyle(appearance.find(appearance.editor.value)),
    ...typographyStyle(appearance.typography.value)
}));
watch(
    appearance.typography,
    async () => {
        await nextTick();
        editor?.requestMeasure();
    },
    { deep: true }
);
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
class DosGraphicWidget extends WidgetType {
    constructor(readonly character: string) {
        super();
    }
    eq(other: DosGraphicWidget) {
        return this.character === other.character;
    }
    toDOM(view: EditorView) {
        const span = view.dom.ownerDocument.createElement('span');
        span.className = 'dos-graphic';
        span.textContent = dosGraphic(this.character);
        return span;
    }
    ignoreEvent() {
        return false;
    }
}
function highlight(state: EditorState, visual = true): DecorationSet {
    const ranges: Range<Decoration>[] = [];
    const text = state.doc.toString();
    const lines = fidoText(text, false);
    for (let number = 1; visual && number <= state.doc.lines; number++) {
        const line = state.doc.line(number);
        ranges.push(Decoration.line({ class: lines[number - 1]!.kind }).range(line.from));
    }
    for (const range of visual ? fidoStyleRanges(text) : []) {
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
    for (const match of text.matchAll(dosGraphicPattern())) {
        ranges.push(
            Decoration.replace({ widget: new DosGraphicWidget(match[0]) }).range(
                match.index!,
                match.index! + 1
            )
        );
    }
    return Decoration.set(ranges, true);
}
const highlighted = StateField.define<DecorationSet>({
    create: highlight,
    update: (value, transaction) =>
        transaction.docChanged || transaction.selection ? highlight(transaction.state) : value,
    provide: (field) => EditorView.decorations.from(field)
});
const sourceGraphics = StateField.define<DecorationSet>({
    create: (state) => highlight(state, false),
    update: (value, transaction) =>
        transaction.docChanged ? highlight(transaction.state, false) : value,
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
                drawSelection(),
                keymap.of([...defaultKeymap, ...historyKeymap]),
                EditorView.lineWrapping,
                accessibility.of(attributes()),
                decorations.of(source.value ? sourceGraphics : highlighted),
                editable.of([
                    EditorState.readOnly.of(!!props.disabled),
                    EditorView.editable.of(!props.disabled)
                ]),
                EditorView.theme({
                    '&': {
                        fontSize: 'var(--mail-font-size, 14px)',
                        backgroundColor: 'var(--mail-background)',
                        color: 'var(--mail-text)'
                    },
                    '.cm-scroller': {
                        fontFamily: 'var(--mail-font-family, monospace)',
                        lineHeight: 'var(--mail-line-height, 1.4)',
                        fontVariantLigatures: 'none'
                    },
                    '.cm-content': { minHeight: '300px' },
                    '.dos-graphic': {
                        display: 'inline-block',
                        width: '1ch',
                        textAlign: 'center',
                        fontVariantEmoji: 'text'
                    },
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
    editor?.dispatch({ effects: decorations.reconfigure(value ? sourceGraphics : highlighted) })
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
    focusAt(position: number, focus = true) {
        editor?.dispatch({ selection: { anchor: Math.min(position, editor.state.doc.length) } });
        if (focus) editor?.focus();
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
                :title="t('Bold')"
                v-shortcut="'editor.bold'"
                @click="wrap('*')"
            >
                <AppIcon name="bold" />
            </button>
            <button
                type="button"
                :disabled="disabled || preview"
                :aria-label="t('Italic')"
                :title="t('Italic')"
                v-shortcut="'editor.italic'"
                @click="wrap('/')"
            >
                <AppIcon name="italic" />
            </button>
            <button
                type="button"
                :disabled="disabled || preview"
                :aria-label="t('Underline')"
                :title="t('Underline')"
                v-shortcut="'editor.underline'"
                @click="wrap('_')"
            >
                <AppIcon name="underline" />
            </button>
            <button
                type="button"
                :disabled="disabled || preview"
                :aria-label="t('Reverse')"
                :title="t('Reverse')"
                v-shortcut="'editor.reverse'"
                @click="wrap('#')"
            >
                <AppIcon name="reverse" />
            </button>
            <button
                type="button"
                :disabled="disabled || preview"
                class="icon-action"
                :aria-label="t('Quote')"
                :title="t('Quote')"
                v-shortcut="'editor.quote'"
                @click="quote"
            >
                <AppIcon name="quote" />
            </button>
            <button
                type="button"
                :disabled="disabled || preview"
                class="icon-action"
                :aria-label="t('Undo')"
                :title="t('Undo')"
                v-shortcut="'editor.undo'"
                @click="historyAction(undo)"
            >
                <AppIcon name="undo" />
            </button>
            <button
                type="button"
                :disabled="disabled || preview"
                class="icon-action"
                :aria-label="t('Redo')"
                :title="t('Redo')"
                v-shortcut="'editor.redo'"
                @click="historyAction(redo)"
            >
                <AppIcon name="redo" />
            </button>
            <button
                type="button"
                :aria-pressed="!source && !preview"
                :aria-label="t('Visual')"
                :title="t('Visual')"
                @click="setSource(false)"
            >
                <AppIcon name="edit" />
            </button>
            <button
                type="button"
                :aria-pressed="source && !preview"
                :aria-label="t('Source text')"
                :title="t('Source text')"
                v-shortcut="'editor.source'"
                @click="setSource(true)"
            >
                <AppIcon name="source" />
            </button>
            <button
                type="button"
                :aria-pressed="preview"
                class="icon-action"
                :aria-label="t('Preview')"
                :title="t('Preview')"
                v-shortcut="'editor.preview'"
                @click="preview = !preview"
            >
                <AppIcon name="eye" />
            </button>
            <div
                v-if="$slots.toolbar"
                class="editor-extra"
            >
                <slot name="toolbar" />
            </div>
        </div>
        <div
            v-show="!preview"
            ref="host"
            class="fido-text editor-host"
        ></div>
        <FidoText
            v-if="preview"
            :theme-id="appearance.editor.value"
            :typography="appearance.typography.value"
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
.editor-extra {
    margin-inline-start: auto;
    min-width: 0;
}
.editor-toolbar button {
    width: 32px;
    height: 32px;
    min-height: 32px;
    padding: 6px;
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
@media (min-width: 701px) {
    .editor-toolbar {
        gap: 3px;
        padding: 4px;
    }
    .editor-toolbar button {
        width: 28px;
        height: 28px;
        min-height: 28px;
        padding: 4px;
        font-size: 12px;
        white-space: nowrap;
    }
}
</style>
