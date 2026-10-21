<script setup lang="ts">
import SettingsSectionHeader from './SettingsSectionHeader.vue';
import SettingsRefreshButton from './SettingsRefreshButton.vue';
import { computed, ref } from 'vue';
import { t } from '../i18n.ts';
import NotificationBanner from './NotificationBanner.vue';
import type { EchoAccessConfiguration, EchoSubscriptionInfo } from '@rpc/bindings.mjs';

const props = defineProps<{
    configuration: EchoAccessConfiguration;
    model: { peer: string; area: string; access: string };
    editing: boolean;
    pendingDelete: EchoSubscriptionInfo | null;
    busy: boolean;
}>();
defineEmits<{
    save: [];
    refresh: [];
    reset: [];
    edit: [subscription: EchoSubscriptionInfo];
    requestDelete: [subscription: EchoSubscriptionInfo | null];
    remove: [];
}>();
const search = ref('');
const rows = computed(() =>
    props.configuration.subscriptions.filter((row) =>
        `${row.area} ${row.peer}`.toLowerCase().includes(search.value.trim().toLowerCase())
    )
);
</script>

<template>
    <section
        id="echo-access"
        class="card"
    >
        <SettingsSectionHeader :title="t('Echo areas and link access')">
            <template #actions
                ><SettingsRefreshButton
                    :busy="busy"
                    :context="t('Echo areas and link access')"
                    @refresh="$emit('refresh')"
            /></template>
        </SettingsSectionHeader>
        <p>
            {{
                t(
                    'WRITE receives mail from the link. READ sends mail to the link. BOTH allows both directions.'
                )
            }}
        </p>
        <p class="muted">
            {{
                t(
                    'Permission changes apply immediately. Quarantined packets are not retried automatically.'
                )
            }}
        </p>
        <NotificationBanner
            v-if="configuration.source !== 'database'"
            kind="warning"
            :message="
                t(
                    'Echo permissions are read-only. Set EchoSubscriptions=database in conf.d/41-areafix.conf and restart to manage them here.'
                )
            "
        />

        <details v-if="configuration.areas.length">
            <summary>{{ t('Known echo areas') }} ({{ configuration.areas.length }})</summary>
            <ul>
                <li
                    v-for="area in configuration.areas"
                    :key="area"
                >
                    {{ area }}
                </li>
            </ul>
        </details>
        <label
            >{{ t('Filter echo areas or links')
            }}<input
                v-model="search"
                type="search"
        /></label>
        <p
            v-if="!configuration.subscriptions.length"
            class="muted"
        >
            {{ t('No echo permissions yet. Incoming echomail is denied by default.') }}
        </p>
        <p
            v-else-if="!rows.length"
            class="muted"
        >
            {{ t('No matching echo permissions.') }}
        </p>
        <ul class="rows">
            <li
                v-for="row in rows"
                :key="`${row.peer}:${row.area}`"
            >
                <div>
                    <strong>{{ row.area }}</strong>
                    <p>{{ row.peer }} · {{ row.access }}</p>
                </div>
                <div
                    v-if="configuration.source === 'database'"
                    class="actions"
                >
                    <button
                        type="button"
                        class="secondary"
                        :disabled="busy"
                        @click="$emit('edit', row)"
                    >
                        {{ t('Edit') }}
                    </button>
                    <button
                        type="button"
                        class="secondary danger"
                        :disabled="busy"
                        @click="$emit('requestDelete', row)"
                    >
                        {{ t('Revoke access') }}
                    </button>
                </div>
            </li>
        </ul>
        <div
            v-if="pendingDelete"
            class="echo-confirmation"
            role="alert"
        >
            <p>
                {{
                    t(
                        'Revoke access to {area} for {peer}? Stored messages and prepared outbound packets are retained.',
                        { area: pendingDelete.area, peer: pendingDelete.peer }
                    )
                }}
            </p>
            <div class="actions">
                <button
                    type="button"
                    class="secondary"
                    :disabled="busy"
                    @click="$emit('requestDelete', null)"
                >
                    {{ t('Cancel') }}
                </button>
                <button
                    type="button"
                    class="primary danger"
                    :disabled="busy"
                    @click="$emit('remove')"
                >
                    {{ t('Confirm revocation') }}
                </button>
            </div>
        </div>
        <form
            v-if="configuration.source === 'database'"
            @submit.prevent="$emit('save')"
        >
            <h3>{{ editing ? t('Edit echo access') : t('Grant echo access') }}</h3>
            <p v-if="!configuration.peers.length">{{ t('Configure a station link first.') }}</p>
            <fieldset :disabled="busy || !configuration.peers.length">
                <div class="form-grid">
                    <label
                        >{{ t('Link')
                        }}<select
                            v-model="model.peer"
                            required
                            :disabled="editing"
                        >
                            <option
                                v-for="peer in configuration.peers"
                                :key="peer"
                                :value="peer"
                            >
                                {{ peer }}
                            </option>
                        </select></label
                    >
                    <label
                        >{{ t('Echo area')
                        }}<input
                            v-model="model.area"
                            list="echo-area-names"
                            required
                            :readonly="editing"
                            maxlength="100"
                    /></label>
                    <datalist id="echo-area-names">
                        <option
                            v-for="area in configuration.areas"
                            :key="area"
                            :value="area"
                        />
                    </datalist>
                    <label
                        >{{ t('Echo access rights')
                        }}<select
                            v-model="model.access"
                            required
                        >
                            <option value="WRITE">{{ t('WRITE — receive from link') }}</option>
                            <option value="READ">{{ t('READ — send to link') }}</option>
                            <option value="BOTH">{{ t('BOTH — receive and send') }}</option>
                        </select></label
                    >
                </div>
                <p class="muted">
                    {{
                        t(
                            'A new area is created when access is granted. New subscriptions send only new messages; existing delivery positions are preserved.'
                        )
                    }}
                </p>
                <footer class="actions">
                    <button class="primary">{{ t('Save echo access') }}</button>
                    <button
                        v-if="editing"
                        type="button"
                        class="secondary"
                        @click="$emit('reset')"
                    >
                        {{ t('Cancel') }}
                    </button>
                </footer>
            </fieldset>
        </form>
    </section>
</template>

<style scoped>
details {
    margin: 20px 0;
}
.echo-confirmation {
    border: 1px solid var(--line);
    padding: 16px;
    margin: 20px 0;
}
</style>
