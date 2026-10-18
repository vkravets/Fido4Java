<script setup lang="ts">
import { ref } from 'vue';
import { t } from '../i18n.ts';
import type { NetmailRouteInfo } from '@rpc/bindings.mjs';
const props = defineProps<{
    rules: NetmailRouteInfo[];
    peers: string[];
    writable: boolean;
    busy: boolean;
}>();
defineEmits<{ save: [rule: NetmailRouteInfo]; remove: [id: string] }>();
function empty(): NetmailRouteInfo {
    return { id: '', priority: 100, pattern: '', nextHop: props.peers[0] || '', enabled: true };
}
const rule = ref(empty());
const editing = ref(false);
function edit(value?: NetmailRouteInfo) {
    rule.value = value ? { ...value } : empty();
    editing.value = !!value;
}
</script>
<template>
    <section class="card">
        <h2>{{ t('Routing rules') }}</h2>
        <p>{{ t('Lower priority numbers are evaluated first; rule ID breaks ties.') }}</p>
        <p
            v-if="!writable"
            class="muted"
        >
            {{ t('Rules are managed through configuration files.') }}
        </p>
        <div
            class="quarantine-table"
            tabindex="0"
            :aria-label="t('Routing rules')"
        >
            <table>
                <thead>
                    <tr>
                        <th>{{ t('Rule ID') }}</th>
                        <th>{{ t('Priority') }}</th>
                        <th>{{ t('Destination mask') }}</th>
                        <th>{{ t('Next hop') }}</th>
                        <th>{{ t('Enabled') }}</th>
                        <th>{{ t('Actions') }}</th>
                    </tr>
                </thead>
                <tbody>
                    <tr
                        v-for="item in rules"
                        :key="item.id"
                    >
                        <td>{{ item.id }}</td>
                        <td>{{ item.priority }}</td>
                        <td>{{ item.pattern }}</td>
                        <td>{{ item.nextHop }}</td>
                        <td>{{ item.enabled ? t('Yes') : t('No') }}</td>
                        <td>
                            <div
                                v-if="writable"
                                class="actions"
                            >
                                <button
                                    class="secondary"
                                    :disabled="busy"
                                    @click="edit(item)"
                                >
                                    {{ t('Edit rule') }}</button
                                ><button
                                    class="text-button"
                                    :disabled="busy"
                                    @click="$emit('remove', item.id)"
                                >
                                    {{ t('Delete rule') }}
                                </button>
                            </div>
                        </td>
                    </tr>
                </tbody>
            </table>
        </div>
        <p v-if="!rules.length">{{ t('No routing rules.') }}</p>
        <form
            v-if="writable"
            @submit.prevent="$emit('save', { ...rule })"
        >
            <h3>{{ editing ? t('Edit rule') : t('Add routing rule') }}</h3>
            <fieldset
                class="routing-fields"
                :disabled="busy"
            >
                <div class="form-grid">
                    <label
                        >{{ t('Rule ID')
                        }}<input
                            v-model="rule.id"
                            required
                            maxlength="80"
                            pattern="[A-Za-z0-9_-]+"
                            :readonly="editing"
                    /></label>
                    <label
                        >{{ t('Priority')
                        }}<input
                            v-model.number="rule.priority"
                            required
                            type="number"
                            min="-2147483648"
                            max="2147483647"
                            step="1"
                    /></label>
                    <label
                        >{{ t('Destination mask')
                        }}<input
                            v-model="rule.pattern"
                            required
                            maxlength="80"
                            placeholder="1:500/*"
                    /></label>
                    <label
                        >{{ t('Next hop')
                        }}<select
                            v-model="rule.nextHop"
                            required
                        >
                            <option
                                v-for="peer in peers"
                                :key="peer"
                                :value="peer"
                            >
                                {{ peer }}
                            </option>
                        </select></label
                    >
                </div>
                <label class="check"
                    ><input
                        v-model="rule.enabled"
                        type="checkbox"
                    />{{ t('Enabled') }}</label
                >
                <div class="actions">
                    <button
                        class="primary"
                        type="submit"
                    >
                        {{ t('Save rule') }}</button
                    ><button
                        class="secondary"
                        type="button"
                        @click="edit()"
                    >
                        {{ t('Clear form') }}
                    </button>
                </div>
            </fieldset>
        </form>
    </section>
</template>
