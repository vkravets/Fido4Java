<script setup lang="ts">
import { computed } from 'vue';
import type { TextRun } from '../fido-text.ts';
import { highlightText } from '../mail-search.ts';
const props = defineProps<{ run: TextRun; query: string }>();
const parts = computed(() => highlightText(props.run.text, props.query));
</script>
<template>
    <component
        :is="run.href ? 'a' : 'span'"
        :class="run.styles"
        :href="run.href"
        :target="run.href ? '_blank' : undefined"
        :rel="run.href ? 'noopener noreferrer' : undefined"
        ><template
            v-for="(part, index) in parts"
            :key="index"
            ><mark v-if="part.match">{{ part.text }}</mark
            ><template v-else>{{ part.text }}</template></template
        ></component
    >
</template>
