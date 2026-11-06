<script setup lang="ts">
import AppIcon from './AppIcon.vue';

defineProps<{ label: string; shortcut?: string; maxlength?: number }>();
const model = defineModel<string>({ required: true });
</script>
<template>
    <label class="search-field">
        <span class="control-label">{{ label }}</span>
        <AppIcon name="search" />
        <input
            v-model="model"
            type="search"
            :maxlength="maxlength"
            :placeholder="label"
            v-shortcut="shortcut"
        />
    </label>
</template>
<style scoped>
.search-field {
    display: block;
    position: relative;
}
.control-label {
    position: absolute;
    width: 1px;
    height: 1px;
    overflow: hidden;
    clip-path: inset(50%);
}
.search-field > .app-icon {
    display: block;
    position: absolute;
    top: 8px;
    left: 8px;
    width: 16px;
    height: 16px;
    color: var(--muted);
    pointer-events: none;
}
input {
    width: 100%;
    height: 32px;
    padding: 5px 8px 5px 30px;
    border-radius: 4px;
    font-size: 12px;
}
</style>
