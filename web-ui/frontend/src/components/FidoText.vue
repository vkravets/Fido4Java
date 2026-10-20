<script setup lang="ts">
import { computed } from 'vue';
import { fidoText } from '../fido-text.ts';
import { useMailSearch } from '../mail-search.ts';
import FidoRun from './FidoRun.vue';
import { themeStyle, useMailAppearance } from '../mail-themes.ts';
import type { MailTheme } from '../mail-themes.ts';
const props = defineProps<{
    text: string;
    source?: boolean;
    themeId?: string;
    theme?: MailTheme;
}>();
const appearance = useMailAppearance();
const colors = computed(() =>
    themeStyle(props.theme || appearance.find(props.themeId || appearance.reader.value))
);
const query = useMailSearch();
const lines = computed(() => fidoText(props.text, !props.source));
</script>
<template>
    <div
        class="fido-text mail-body"
        :style="colors"
    >
        <div
            v-for="(line, index) in lines"
            :key="index"
            class="fido-line"
            :class="line.kind"
        >
            <FidoRun
                v-for="(run, offset) in line.runs"
                :key="offset"
                :run="run"
                :query="query"
            />
            <br v-if="line.runs.every((run) => !run.text)" />
        </div>
    </div>
</template>
<style>
.fido-text {
    background: var(--mail-background, var(--surface));
    color: var(--mail-text, var(--text));
    font-family: var(--font-mono, monospace);
    white-space: pre-wrap;
    padding: 16px;
    border-radius: 6px;
}
.fido-text .quote1 {
    color: var(--mail-quote1, #298d9b);
}
.fido-text .quote2 {
    color: var(--mail-quote2, #9d72c6);
}
.fido-text .quote3 {
    color: var(--mail-quote3, #ba8140);
}
.fido-text .signature,
.fido-text .tearline,
.fido-text .origin,
.fido-text .tagline {
    color: var(--mail-ending, var(--muted));
}
.fido-text .bold {
    font-weight: 750;
}
.fido-text .italic {
    font-style: italic;
}
.fido-text .underline {
    text-decoration: underline;
}
.fido-text .reverse {
    background: var(--mail-text, var(--text));
    color: var(--mail-background, var(--surface));
}
.fido-text a,
.fido-text .address {
    color: var(--mail-link, var(--accent));
}
</style>

<style>
.fido-text mark {
    background: var(--mail-search, #f4d38c);
    color: #161616;
}
</style>
