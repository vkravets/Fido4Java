<script setup lang="ts">
import { computed, reactive, ref, watch, useId } from 'vue';
import { locale, t } from '../i18n.ts';
import type { ExtensionSettings } from '@rpc/bindings.mjs';

const props = defineProps<{
    extensions: ExtensionSettings[];
    busy: boolean;
    initialSection?: string;
}>();
const emit = defineEmits<{ save: [id: string, section: string, values: Record<string, string>] }>();
const selected = ref('');
const group = ref(props.initialSection || '');
const prefix = useId();
const model = reactive<Record<string, string>>({});
const extension = computed(
    () => props.extensions.find((item) => item.id === selected.value) || props.extensions[0]
);
const section = computed(
    () =>
        extension.value?.sections.find((item) => item.id === group.value) ||
        extension.value?.sections[0]
);
const columns = computed(
    () => section.value?.fields.filter((field) => field.type !== 'password') || []
);
watch(
    extension,
    (value) => {
        if (value) selected.value = value.id;
    },
    { immediate: true }
);

function label(value: string): string {
    return extension.value?.translations[locale.value]?.[value] || value;
}
function reset() {
    for (const key of Object.keys(model)) delete model[key];
    for (const field of section.value?.fields || []) model[field.name] = field.choices[0] || '';
}
watch(section, reset, { immediate: true });
function edit(row: Record<string, string>) {
    reset();
    for (const field of section.value?.fields || []) {
        model[field.name] = field.type === 'password' ? '' : row[field.name] || '';
    }
}
function save() {
    if (extension.value && section.value?.writable) {
        emit('save', extension.value.id, section.value.id, { ...model });
    }
}
</script>

<template>
    <section class="card plugin-settings">
        <h2>{{ t('Plugin settings') }}</h2>
        <p
            v-if="!extensions.length"
            role="status"
        >
            {{ t('No active settings extensions.') }}
        </p>
        <template v-else-if="extension && section">
            <label :for="`${prefix}-plugin`">{{ t('Plugin') }}</label>
            <select
                :id="`${prefix}-plugin`"
                v-model="selected"
                :disabled="busy"
            >
                <option
                    v-for="item in extensions"
                    :key="item.id"
                    :value="item.id"
                >
                    {{ item.translations[locale]?.[item.title] || item.title }}
                </option>
            </select>
            <p class="muted">{{ t('Configuration source') }}: {{ extension.source }}</p>
            <nav
                class="tabs plugin-tabs"
                :aria-label="t('Plugin settings')"
            >
                <button
                    v-for="item in extension.sections"
                    :key="item.id"
                    type="button"
                    :class="{ selected: item.id === section.id }"
                    :aria-pressed="item.id === section.id"
                    :disabled="busy"
                    @click="group = item.id"
                >
                    {{ label(item.title) }}
                </button>
            </nav>
            <h3>{{ label(section.title) }}</h3>
            <p
                v-if="!section.writable"
                role="status"
            >
                {{ t('This section is managed through configuration files.') }}
            </p>
            <p v-if="!section.rows.length">{{ t('No entries yet.') }}</p>
            <div
                v-else
                class="plugin-table"
            >
                <table>
                    <thead>
                        <tr>
                            <th
                                v-for="field in columns"
                                :key="field.name"
                                scope="col"
                            >
                                {{ label(field.label) }}
                            </th>
                            <th
                                v-if="section.writable"
                                scope="col"
                            >
                                {{ t('Actions') }}
                            </th>
                        </tr>
                    </thead>
                    <tbody>
                        <tr
                            v-for="(row, index) in section.rows"
                            :key="index"
                        >
                            <td
                                v-for="field in columns"
                                :key="field.name"
                                :data-label="label(field.label)"
                            >
                                {{
                                    field.type === 'select'
                                        ? label(row[field.name] || '')
                                        : row[field.name] || ''
                                }}
                            </td>
                            <td v-if="section.writable">
                                <button
                                    type="button"
                                    class="secondary"
                                    :disabled="busy"
                                    @click="edit(row)"
                                >
                                    {{ t('Edit') }}
                                </button>
                            </td>
                        </tr>
                    </tbody>
                </table>
            </div>
            <form
                v-if="section.writable"
                class="plugin-form"
                @submit.prevent="save"
            >
                <h3>{{ t('Add or update an entry') }}</h3>
                <div class="form-grid">
                    <label
                        v-for="field in section.fields"
                        :key="field.name"
                        :for="`${prefix}-${field.name}`"
                    >
                        {{ label(field.label) }}
                        <select
                            v-if="field.type === 'select'"
                            :id="`${prefix}-${field.name}`"
                            v-model="model[field.name]"
                            :disabled="busy"
                        >
                            <option
                                v-for="value in field.choices"
                                :key="value"
                                :value="value"
                            >
                                {{ label(value) }}
                            </option>
                        </select>
                        <input
                            v-else
                            :id="`${prefix}-${field.name}`"
                            v-model="model[field.name]"
                            :type="field.type === 'password' ? 'password' : 'text'"
                            :disabled="busy"
                            maxlength="4096"
                            :autocomplete="field.type === 'password' ? 'new-password' : 'off'"
                        />
                    </label>
                </div>
                <p
                    v-if="section.fields.some((field) => field.type === 'password')"
                    class="muted"
                >
                    {{ t('Leave the password empty to keep the existing secret.') }}
                </p>
                <footer class="actions">
                    <button
                        type="submit"
                        class="primary"
                        :disabled="busy"
                    >
                        {{ t('Save') }}
                    </button>
                    <button
                        type="button"
                        class="secondary"
                        :disabled="busy"
                        @click="reset"
                    >
                        {{ t('Clear form') }}
                    </button>
                </footer>
            </form>
        </template>
    </section>
</template>

<style scoped>
.plugin-tabs {
    flex-wrap: wrap;
    margin: 20px 0;
}
.plugin-table {
    overflow-x: auto;
    margin: 20px 0;
}
table {
    width: 100%;
    border-collapse: collapse;
}
th,
td {
    padding: 12px;
    border-bottom: 1px solid var(--line);
    text-align: left;
    vertical-align: top;
    overflow-wrap: anywhere;
    max-width: 22rem;
}
.plugin-form {
    border-top: 1px solid var(--line);
    padding-top: 20px;
}
@media (max-width: 640px) {
    .plugin-table {
        overflow: visible;
    }
    table,
    tbody,
    tr,
    td {
        display: block;
        width: 100%;
        max-width: none;
    }
    thead {
        position: absolute;
        width: 1px;
        height: 1px;
        overflow: hidden;
        clip-path: inset(50%);
    }
    tr {
        padding: 12px 0;
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
        font-size: 12px;
    }
}
</style>
