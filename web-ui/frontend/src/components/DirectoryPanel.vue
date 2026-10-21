<script setup lang="ts">
import { t } from '../i18n.ts';
import SettingsSectionHeader from './SettingsSectionHeader.vue';
import AppIcon from './AppIcon.vue';
import type { DirectoryNode } from '@rpc/bindings.mjs';
defineProps<{ items: DirectoryNode[]; busy: boolean; hasMore: boolean; offset: number }>();
const query = defineModel<string>({ default: '' });
defineEmits<{ search: []; next: []; previous: [] }>();
</script>
<template>
    <section
        class="card directory-panel"
        :aria-busy="busy"
    >
        <SettingsSectionHeader
            :title="t('Node and point directory')"
            page
        />
        <p>
            {{
                t(
                    'Imported nodelists and pointlists. Directory records do not grant address ownership or connection permissions.'
                )
            }}
        </p>
        <form
            class="directory-search"
            @submit.prevent="$emit('search')"
        >
            <label
                >{{ t('Search the directory')
                }}<input
                    v-model="query"
                    type="search"
                    maxlength="160"
                    :placeholder="t('AKA, sysop, station, location or IP / hostname')"
            /></label>
            <button
                class="secondary settings-icon-action"
                :title="t('Search')"
                :aria-label="t('Search')"
                :disabled="busy"
            >
                <AppIcon name="search" />
            </button>
        </form>
        <p
            v-if="busy"
            role="status"
        >
            {{ t('Searching directory…') }}
        </p>
        <p
            v-else-if="!items.length"
            class="muted"
        >
            {{ t('No matching nodes. Import a nodelist or pointlist, or change your search.') }}
        </p>
        <div
            v-if="items.length"
            class="directory-table"
            tabindex="0"
            :aria-label="t('Directory results')"
        >
            <table>
                <thead>
                    <tr>
                        <th>{{ t('Address') }}</th>
                        <th>{{ t('Station and sysop') }}</th>
                        <th>{{ t('Location') }}</th>
                        <th>{{ t('Connection details') }}</th>
                        <th>{{ t('List source') }}</th>
                    </tr>
                </thead>
                <tbody>
                    <tr
                        v-for="node in items"
                        :key="`${node.source}:${node.address}`"
                    >
                        <td :data-label="t('Address')">
                            <strong class="mono">{{ node.address }}</strong
                            ><small>{{ node.kind }}</small>
                        </td>
                        <td :data-label="t('Station and sysop')">
                            <strong>{{ node.system }}</strong
                            ><span>{{ node.sysop }}</span>
                        </td>
                        <td :data-label="t('Location')">{{ node.location }}</td>
                        <td :data-label="t('Connection details')">
                            <span
                                v-for="endpoint in node.endpoints"
                                :key="endpoint"
                                class="mono"
                                >{{ endpoint }}</span
                            ><small>{{ node.phone }}</small
                            ><small>{{ node.flags.join(', ') }}</small>
                        </td>
                        <td :data-label="t('List source')">{{ node.source }}</td>
                    </tr>
                </tbody>
            </table>
        </div>
        <div class="actions">
            <button
                class="secondary"
                :disabled="busy || offset === 0"
                @click="$emit('previous')"
            >
                {{ t('Previous page') }}
            </button>
            <span>{{ t('Showing {count} entries', { count: items.length }) }}</span>
            <button
                class="secondary"
                :disabled="busy || !hasMore"
                @click="$emit('next')"
            >
                {{ t('Next page') }}
            </button>
        </div>
    </section>
</template>
<style scoped>
.directory-search {
    display: flex;
    align-items: end;
    gap: 12px;
    flex-wrap: wrap;
}
.directory-search label {
    flex: 1;
    min-width: min(100%, 16rem);
    margin-bottom: 0;
}
.directory-table {
    overflow-x: auto;
    margin: 20px 0;
}
table {
    min-width: 44rem;
    width: 100%;
    border-collapse: collapse;
}
th,
td {
    padding: 12px;
    border-bottom: 1px solid var(--line);
    text-align: left;
}
td:first-child {
    white-space: nowrap;
}
td {
    vertical-align: top;
    overflow-wrap: anywhere;
    max-width: 20rem;
}
td span,
td small {
    display: block;
}
td small {
    color: var(--muted);
}
@media (max-width: 640px) {
    .directory-table {
        overflow: visible;
    }
    table,
    tbody,
    tr,
    td {
        display: block;
        min-width: 0;
        max-width: none;
        width: 100%;
    }
    thead {
        position: absolute;
        width: 1px;
        height: 1px;
        overflow: hidden;
        clip-path: inset(50%);
    }
    tr {
        padding: 16px 0;
        border-bottom: 1px solid var(--line);
    }
    td {
        padding: 0 0 12px;
        border: 0;
    }
    td::before {
        content: attr(data-label);
        display: block;
        color: var(--muted);
        font-size: 11px;
        margin-bottom: 4px;
    }
}
</style>
