<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue';
import { t } from '../i18n.ts';
const props = withDefaults(defineProps<{ direction?: 'right' | 'below'; ratio?: number }>(), {
    direction: 'right',
    ratio: 38
});
const emit = defineEmits<{ resize: [ratio: number] }>();
const root = ref<HTMLElement>();
const size = ref(props.ratio);
watch(
    () => props.ratio,
    (value) => {
        size.value = value;
    }
);
const clamp = (value: number) => Math.max(20, Math.min(75, value));
const style = computed(() => ({ '--pane-size': `${size.value}%` }));
let pointer: number | null = null;
function move(event: PointerEvent) {
    if (pointer !== event.pointerId || !root.value) return;
    const rect = root.value.getBoundingClientRect();
    const below = props.direction === 'below';
    const dimension = below ? rect.height : rect.width;
    if (dimension <= 0) return;
    size.value = clamp(
        ((below ? event.clientY - rect.top : event.clientX - rect.left) / dimension) * 100
    );
}
function stop(event: PointerEvent) {
    if (pointer !== event.pointerId) return;
    pointer = null;
    cleanup();
    if (event.type === 'pointercancel') {
        size.value = props.ratio;
        return;
    }
    emit('resize', Math.round(size.value));
}
function cleanup() {
    window.removeEventListener('pointermove', move);
    window.removeEventListener('pointerup', stop);
    window.removeEventListener('pointercancel', stop);
}
function start(event: PointerEvent) {
    if (event.button !== 0) return;
    pointer = event.pointerId;
    window.addEventListener('pointermove', move);
    window.addEventListener('pointerup', stop);
    window.addEventListener('pointercancel', stop);
    event.preventDefault();
}
function keyboard(event: KeyboardEvent) {
    const arrows =
        props.direction === 'below' ? ['ArrowUp', 'ArrowDown'] : ['ArrowLeft', 'ArrowRight'];
    const direction = arrows.indexOf(event.key);
    if (direction < 0 && event.key !== 'Home' && event.key !== 'End') return;
    event.preventDefault();
    size.value =
        event.key === 'Home'
            ? 20
            : event.key === 'End'
              ? 75
              : clamp(size.value + (direction === 0 ? -2 : 2));
    emit('resize', size.value);
}
onBeforeUnmount(cleanup);
</script>
<template>
    <div
        ref="root"
        class="split-pane"
        :class="direction"
        :style="style"
    >
        <section class="split-list"><slot name="list" /></section>
        <div
            class="split-handle"
            role="separator"
            tabindex="0"
            :aria-label="t('Resize panes')"
            :aria-orientation="direction === 'below' ? 'horizontal' : 'vertical'"
            :aria-valuenow="Math.round(size)"
            :aria-valuemin="20"
            :aria-valuemax="75"
            @pointerdown="start"
            @keydown="keyboard"
        ></div>
        <section class="split-detail"><slot /></section>
    </div>
</template>
<style scoped>
.split-pane {
    display: grid;
    grid-template-columns: minmax(0, var(--pane-size)) 9px minmax(0, 1fr);
    min-width: 0;
    min-height: 400px;
    height: calc(100dvh - 270px);
}
.split-pane.below {
    grid-template-columns: minmax(0, 1fr);
    grid-template-rows: minmax(0, var(--pane-size)) 9px minmax(0, 1fr);
}
.split-list,
.split-detail {
    min-width: 0;
    min-height: 0;
    overflow: auto;
    overscroll-behavior: contain;
    padding: 4px;
}
.split-handle {
    background: var(--line);
    border-radius: 4px;
    cursor: col-resize;
    touch-action: none;
    margin: 0 3px;
}
.below .split-handle {
    cursor: row-resize;
    margin: 3px 0;
}
.split-handle:hover,
.split-handle:focus-visible {
    background: var(--accent);
    outline: 2px solid var(--accent);
}
@media (max-width: 700px) {
    .split-pane,
    .split-pane.below {
        display: block;
        height: auto;
        min-height: 0;
    }
    .split-handle {
        display: none;
    }
}
</style>
