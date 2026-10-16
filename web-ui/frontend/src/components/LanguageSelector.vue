<script setup lang="ts">
import { locale, setLocale, t } from '../i18n.ts';

function change(event: Event) {
    setLocale((event.target as HTMLSelectElement).value);
}
</script>

<template>
    <select
        class="language-selector"
        :value="locale"
        :aria-label="t('Language')"
        @change="change"
    >
        <option
            value="en"
            lang="en"
        >
            English
        </option>
        <option
            value="uk"
            lang="uk"
        >
            Українська
        </option>
    </select>
</template>
