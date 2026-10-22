<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue';
import { t } from '../i18n.ts';
import { shortcutActions, shortcutGroups } from '../shortcut-actions.ts';
import { bindingsFor, useShortcuts } from '../shortcuts.ts';
import AppIcon from './AppIcon.vue';

const props = defineProps<{ administrator?: boolean }>();
const model = useShortcuts();
const dialog = ref<HTMLDialogElement | null>(null);
const closeButton = ref<HTMLButtonElement | null>(null);
const content = ref<HTMLElement | null>(null);
const groups = computed(() =>
    shortcutGroups.map((group) => ({
        ...group,
        actions: shortcutActions
            .filter((action) => action.scope === group.id)
            .filter(
                (action) =>
                    props.administrator ||
                    !['nav.station', 'nav.links', 'nav.polling'].includes(action.id)
            )
            .map((action) => ({ ...action, keys: bindingsFor(model.value.value, action.id) }))
    }))
);
watch(
    [model.helpOpen, dialog],
    ([open, element]) => {
        if (!element) return;
        if (open && !element.open) element.showModal();
        if (!open && element.open) element.close();
    },
    { flush: 'post' }
);
onBeforeUnmount(() => {
    dialog.value?.close();
    model.helpOpen.value = false;
});
function close() {
    model.helpOpen.value = false;
}
function keyboard(event: KeyboardEvent) {
    if (event.key !== 'Tab') return;
    if (event.shiftKey && document.activeElement === closeButton.value) {
        event.preventDefault();
        content.value?.focus();
    } else if (!event.shiftKey && document.activeElement === content.value) {
        event.preventDefault();
        closeButton.value?.focus();
    }
}
</script>

<template>
    <Teleport to="body">
        <dialog
            ref="dialog"
            class="shortcut-help"
            aria-labelledby="shortcut-help-title"
            @close="close"
            @cancel.prevent="close"
            @keydown="keyboard"
        >
            <header class="help-heading">
                <div>
                    <h2 id="shortcut-help-title">{{ t('Keyboard shortcuts') }}</h2>
                    <p>
                        {{
                            model.value.value.profile === 'golded'
                                ? t('GoldED+ (web)')
                                : t('JFtn default')
                        }}
                    </p>
                </div>
                <button
                    ref="closeButton"
                    type="button"
                    class="secondary icon-button"
                    :aria-label="t('Close shortcut help')"
                    autofocus
                    @click="close"
                >
                    <AppIcon name="close" />
                </button>
            </header>
            <div
                ref="content"
                class="help-content"
                tabindex="0"
            >
                <p
                    v-if="!model.value.value.enabled"
                    class="help-note"
                >
                    {{
                        t(
                            'Keyboard shortcuts are disabled. You can enable them in personal settings.'
                        )
                    }}
                </p>
                <div class="help-groups">
                    <section
                        v-for="group in groups"
                        :key="group.id"
                    >
                        <h3>{{ t(group.label) }}</h3>
                        <dl>
                            <div
                                v-for="action in group.actions"
                                :key="action.id"
                                :data-help-action="action.id"
                            >
                                <dt>{{ t(action.label) }}</dt>
                                <dd>
                                    <template v-if="action.keys.length"
                                        ><kbd
                                            v-for="key in action.keys"
                                            :key="key"
                                            >{{ key }}</kbd
                                        ></template
                                    ><span v-else>{{ t('Not assigned') }}</span>
                                </dd>
                            </div>
                        </dl>
                    </section>
                </div>
            </div>
            <footer>
                {{
                    t(
                        'Keys depend on the current screen. Change them in My settings → Keyboard shortcuts.'
                    )
                }}<span
                    ><kbd>?</kbd> {{ t('Keyboard shortcuts') }} · <kbd>Esc</kbd>
                    {{ t('Close shortcut help') }}</span
                >
            </footer>
        </dialog>
    </Teleport>
</template>

<style scoped>
.shortcut-help {
    position: fixed;
    inset: 0;
    margin: auto;
    padding: 0;
    width: min(54rem, calc(100vw - 2rem));
    max-width: none;
    max-height: calc(100dvh - 2rem);
    border: 1px solid var(--line);
    border-radius: 12px;
    background: var(--surface);
    color: var(--text);
    box-shadow: var(--shadow);
}
.shortcut-help[open] {
    display: flex;
    flex-direction: column;
}
.shortcut-help::backdrop {
    background: #0009;
}
.help-heading {
    display: flex;
    justify-content: space-between;
    align-items: start;
    gap: 1rem;
    padding: 1rem 1.25rem;
    border-bottom: 1px solid var(--line);
}
.help-heading h2,
.help-heading p,
h3,
dl,
dd {
    margin: 0;
}
.help-heading h2 {
    font-size: 1.25rem;
}
.help-heading p {
    color: var(--muted);
    margin-top: 0.25rem;
}
.help-content {
    overflow: auto;
    overscroll-behavior: contain;
    min-height: 0;
    padding: 1rem 1.25rem;
}
.help-groups {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 1.5rem 2rem;
}
h3 {
    font-size: 1rem;
    color: var(--accent);
    margin-bottom: 0.5rem;
}
dl > div {
    display: flex;
    align-items: baseline;
    justify-content: space-between;
    gap: 0.75rem;
    padding: 0.4rem 0;
    border-bottom: 1px solid var(--line);
}
dl {
    display: block;
    padding: 0;
    font-size: 0.875rem;
}
dt {
    color: var(--text);
    overflow-wrap: anywhere;
}
dd {
    display: flex;
    flex-wrap: wrap;
    justify-content: end;
    gap: 0.25rem;
    text-align: right;
    color: var(--muted);
}
kbd {
    display: inline-block;
    padding: 0.15rem 0.35rem;
    border: 1px solid var(--line);
    border-radius: 4px;
    background: var(--surface-raised);
    color: var(--text);
    white-space: nowrap;
    font-size: 0.8rem;
}
footer {
    padding: 0.75rem 1.25rem;
    border-top: 1px solid var(--line);
    color: var(--muted);
    font-size: 0.8rem;
}
footer span {
    display: block;
    margin-top: 0.4rem;
}
@media (max-width: 700px) {
    .help-groups {
        grid-template-columns: minmax(0, 1fr);
    }
}
</style>
