import { watchEffect } from 'vue';
import type { App } from 'vue';
import { t } from './i18n.ts';
import { shortcutActions } from './shortcut-actions.ts';
import type { ShortcutAction } from './shortcut-actions.ts';
import { bindingsFor, eventBinding, shortcutsKey } from './shortcuts.ts';
import type { Shortcuts } from './shortcuts.ts';

function available(element: HTMLElement): boolean {
    if (
        !element.isConnected ||
        element.matches(':disabled, [aria-disabled="true"]') ||
        element.closest('[inert], [data-shortcuts-disabled], [aria-disabled="true"]')
    )
        return false;
    for (let current: HTMLElement | null = element; current; current = current.parentElement) {
        const style = getComputedStyle(current);
        const closedMenu =
            current.hasAttribute('data-shortcut-menu') &&
            current.closest('details')?.open === false;
        if (
            current.hidden ||
            current.getAttribute('aria-hidden') === 'true' ||
            (style.display === 'none' &&
                !(current === element && element.dataset.shortcutHidden === 'layout') &&
                !closedMenu) ||
            style.visibility === 'hidden'
        )
            return false;
    }
    return true;
}
export function installShortcuts(app: App, model: Shortcuts) {
    app.provide(shortcutsKey, model);
    const targets = new Map<HTMLElement, ShortcutAction>();
    const cleanups = new WeakMap<HTMLElement, () => void>();
    const updates = new WeakMap<HTMLElement, () => void>();
    app.directive('shortcut', {
        getSSRProps() {
            return {};
        },
        mounted(element: HTMLElement, binding: { value: ShortcutAction }) {
            if (!shortcutActions.some((action) => action.id === binding.value)) return;
            targets.set(element, binding.value);
            element.dataset.shortcut = binding.value;
            let baseTitle = element.title;
            let lastTitle = '';
            const update = () => {
                const action = shortcutActions.find((action) => action.id === binding.value)!;
                const keys = model.value.value.enabled
                    ? bindingsFor(model.value.value, binding.value)
                    : [];
                if (element.title && element.title !== lastTitle) baseTitle = element.title;
                lastTitle =
                    (baseTitle || t(action.label)) + (keys.length ? ` (${keys.join(' / ')})` : '');
                element.title = lastTitle;
                if (keys.length)
                    element.setAttribute(
                        'aria-keyshortcuts',
                        keys.map((key) => key.replace('Ctrl+', 'Control+')).join(' ')
                    );
                else element.removeAttribute('aria-keyshortcuts');
            };
            updates.set(element, update);
            cleanups.set(element, watchEffect(update));
        },
        updated(element: HTMLElement) {
            updates.get(element)?.();
        },
        unmounted(element: HTMLElement) {
            cleanups.get(element)?.();
            targets.delete(element);
        }
    });
    function keyboard(event: KeyboardEvent) {
        const preferences = model.value.value;
        if (event.defaultPrevented || event.isComposing || event.getModifierState('AltGraph'))
            return;
        const focused = event.target instanceof Element ? event.target : null;
        if (focused?.closest('[data-shortcut-recorder], [data-shortcuts-disabled]')) return;
        if (
            [...document.querySelectorAll<HTMLElement>('dialog[open], [aria-modal="true"]')].some(
                available
            )
        )
            return;
        const editable = !!focused?.closest(
            'input, textarea, select, [contenteditable="true"], [role="textbox"]'
        );
        if (event.key === '?' && !event.ctrlKey && !event.altKey && !event.metaKey && !editable) {
            event.preventDefault();
            event.stopPropagation();
            if (!event.repeat) model.helpOpen.value = true;
            return;
        }
        if (!preferences.enabled) return;
        if (event.key === 'Escape' && focused?.closest('details[open]')) return;
        if (
            event.key === 'Escape' &&
            focused?.closest('select, [role="combobox"][aria-expanded="true"]')
        )
            return;
        if (
            /^(?:Arrow|Home|End|Enter|Space|Tab)/.test(event.code) &&
            ((focused?.closest('[role="tree"]') && !focused.matches('[data-shortcut-row]')) ||
                focused?.closest('[role="tablist"], [role="separator"]'))
        )
            return;
        const key = eventBinding(event);
        if (!key) return;
        const composer = [...document.querySelectorAll<HTMLElement>('.mail-composer')].find(
            available
        );
        const editor = focused?.closest('.fido-editor');
        const navigation = ['mail.previous', 'mail.next', 'mail.unread'].some((id) =>
            bindingsFor(preferences, id as ShortcutAction).includes(key)
        );
        const reader =
            navigation && !composer && !editable
                ? [...document.querySelectorAll<HTMLElement>('.mail-message')].find(available)
                : undefined;
        if (event.repeat) {
            if (reader) {
                event.preventDefault();
                event.stopPropagation();
            }
            return;
        }
        for (const [element, id] of targets) {
            if (!bindingsFor(preferences, id).includes(key)) continue;
            const action = shortcutActions.find((action) => action.id === id)!;
            if (
                (action.scope === 'mail' && (composer || editable)) ||
                (action.scope === 'compose' && !composer) ||
                (action.scope === 'editor' && !editor) ||
                (editable &&
                    !event.ctrlKey &&
                    !event.altKey &&
                    !event.metaKey &&
                    !/^F(?:[1-9]|1[0-2])$/.test(event.code) &&
                    id !== 'compose.cancel')
            )
                continue;
            if (!available(element)) continue;
            if (action.scope === 'editor' && !editor?.contains(element)) continue;
            if (action.scope === 'compose' && !composer?.contains(element)) continue;
            event.preventDefault();
            event.stopPropagation();
            if (element instanceof HTMLInputElement) {
                element.focus();
                element.select();
            } else {
                if (['mail.previous', 'mail.next', 'mail.unread'].includes(id)) {
                    element.closest<HTMLElement>('.mail-message')?.focus({ preventScroll: true });
                }
                element.click();
            }
            return;
        }
        if (composer || editable) return;
        const direction = bindingsFor(preferences, 'mail.next').includes(key)
            ? 1
            : bindingsFor(preferences, 'mail.previous').includes(key)
              ? -1
              : 0;
        // A mounted reader owns navigation even while its actions are pending or at a boundary.
        // Do not fall back to list rows or allow native arrow scrolling in that case.
        if (reader) {
            reader.focus({ preventScroll: true });
            event.preventDefault();
            event.stopPropagation();
            return;
        }
        if (!direction) return;
        const rows = [...document.querySelectorAll<HTMLElement>('[data-shortcut-row]')].filter(
            available
        );
        const index = rows.findIndex((row) => row.hasAttribute('data-shortcut-selected'));
        const next = rows[index < 0 ? (direction > 0 ? 0 : rows.length - 1) : index + direction];
        if (!next) return;
        event.preventDefault();
        event.stopPropagation();
        next.click();
    }
    if (typeof window !== 'undefined') {
        window.addEventListener('keydown', keyboard, true);
        app.onUnmount(() => window.removeEventListener('keydown', keyboard, true));
    }
}
