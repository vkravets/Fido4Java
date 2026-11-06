<script setup lang="ts">
import { computed } from 'vue';
import { t } from '../i18n.ts';
import { mailId } from '../netmail.ts';
import NotificationBanner from './NotificationBanner.vue';
import type { AreaInfo } from '@rpc/bindings.mjs';

const props = defineProps<{
    areas: AreaInfo[];
    selected: string;
    busy: boolean;
    error?: string;
}>();
const search = defineModel<string>('search', { required: true });
const emit = defineEmits<{ select: [name: string] }>();
const visibleAreas = computed(() =>
    props.areas.filter((area) =>
        area.name.toLowerCase().includes(search.value.trim().toLowerCase())
    )
);
function select(name: string) {
    if (!props.busy && name !== props.selected) emit('select', name);
}
</script>
<template>
    <section
        class="card echo-conference-list"
        :aria-label="t('Conferences')"
    >
        <NotificationBanner
            kind="error"
            :message="error || ''"
        />
        <label class="echo-conference-search">
            {{ t('Find a conference') }}
            <input v-model="search" type="search" :placeholder="t('Find a conference')" />
        </label>
        <p v-if="!areas.length && busy" class="muted" role="status">
            {{ t('Loading conferences…') }}
        </p>
        <p v-else-if="!areas.length && !error" class="muted">
            {{ t('No accessible conferences. Ask the administrator to grant your AKA access.') }}
        </p>
        <p v-else-if="areas.length && !visibleAreas.length" class="muted">
            {{ t('No matching conferences.') }}
        </p>
        <ul
            v-if="visibleAreas.length"
            class="echo-conference-rows"
        >
            <li
                v-for="area in visibleAreas"
                :key="area.name"
            >
                <button
                    type="button"
                    class="echo-conference-row"
                    :class="{ selected: selected === area.name }"
                    :aria-current="selected === area.name ? 'true' : undefined"
                    :disabled="busy"
                    @click="select(area.name)"
                >
                    <span class="echo-conference-name">{{ area.name }}</span>
                    <span class="echo-conference-count">{{ mailId(area.messages) }}</span>
                    <small v-if="!area.readable">{{ t('Post only') }}</small>
                    <small v-else-if="!area.writeAddresses?.length">{{ t('Read only') }}</small>
                </button>
            </li>
        </ul>
    </section>
</template>
<style scoped>
.echo-conference-list {
    min-width: 220px;
    padding: 10px;
}
.echo-conference-search {
    display: block;
}
.echo-conference-search input {
    width: 100%;
}
.echo-conference-rows {
    list-style: none;
    margin: 8px 0 0;
    padding: 0;
    max-height: 16rem;
    overflow: auto;
}
.echo-conference-row {
    display: grid;
    grid-template-columns: minmax(0, 1fr) auto;
    gap: 2px 8px;
    width: 100%;
    text-align: left;
}
.echo-conference-row.selected {
    border-color: var(--accent);
    background: var(--accent-soft);
}
.echo-conference-name {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
}
.echo-conference-row small {
    grid-column: 1 / -1;
}
</style>
