<script setup lang="ts">
import { t } from '../i18n.ts';
const active = defineModel<'polls' | 'log'>({ default: 'polls' });
function navigate(event: KeyboardEvent) {
    if (!['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return;
    event.preventDefault();
    active.value =
        event.key === 'Home'
            ? 'polls'
            : event.key === 'End'
              ? 'log'
              : active.value === 'polls'
                ? 'log'
                : 'polls';
    const tabs = (event.currentTarget as HTMLElement).querySelectorAll<HTMLButtonElement>(
        '[role="tab"]'
    );
    tabs[active.value === 'polls' ? 0 : 1]?.focus();
}
</script>
<template>
    <nav
        class="tabs"
        role="tablist"
        :aria-label="t('Connections')"
        @keydown="navigate"
    >
        <button
            id="connection-polls-tab"
            type="button"
            role="tab"
            :aria-selected="active === 'polls'"
            aria-controls="connection-polls-panel"
            :tabindex="active === 'polls' ? 0 : -1"
            :class="{ selected: active === 'polls' }"
            @click="active = 'polls'"
        >
            {{ t('Outgoing polls') }}
        </button>
        <button
            id="connection-log-tab"
            type="button"
            role="tab"
            :aria-selected="active === 'log'"
            aria-controls="connection-log-panel"
            :tabindex="active === 'log' ? 0 : -1"
            :class="{ selected: active === 'log' }"
            @click="active = 'log'"
        >
            {{ t('Binkp log') }}
        </button>
    </nav>
    <div
        v-show="active === 'polls'"
        id="connection-polls-panel"
        role="tabpanel"
        aria-labelledby="connection-polls-tab"
    >
        <slot name="polls" />
    </div>
    <div
        v-show="active === 'log'"
        id="connection-log-panel"
        role="tabpanel"
        aria-labelledby="connection-log-tab"
    >
        <slot name="log" />
    </div>
</template>
