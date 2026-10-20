<script setup lang="ts">
import { t } from '../i18n.ts';
import { inject, onMounted } from 'vue';
import { robotAccessClientKey } from '../station-clients.ts';
import { api, bindings } from '../api.ts';
import { createRobotEditor } from '../robot-rules.ts';
import NotificationBanner from './NotificationBanner.vue';
import type { AppState } from '../state.ts';

const props = defineProps<{ state: AppState }>();
const client = inject(robotAccessClientKey, api.ui);
const editor = createRobotEditor(client, () => props.state.session.value, bindings);
const { configuration, editing, pendingDelete, model } = editor;
onMounted(() =>
    props.state.run(async () => {
        await editor.refresh();
        editor.reset();
    })
);

function save() {
    props.state.run(async () => {
        await editor.save();
        props.state.notice.value = t('Rule saved. No restart required.');
    });
}
function remove() {
    props.state.run(async () => {
        await editor.remove();
        props.state.notice.value = t('Rule deleted.');
    });
}
</script>

<template>
    <div class="page-heading">
        <span class="eyebrow">{{ t('NODE MANAGEMENT') }}</span>
        <h1>{{ t('Mail robots') }}</h1>
        <p>{{ t('Link access to AreaFix, FileFix and TIC file exchange.') }}</p>
    </div>
    <button
        class="secondary refresh"
        :disabled="state.busy.value"
        @click="state.run(editor.refresh)"
    >
        {{ t('Refresh') }}
    </button>
    <template v-if="configuration">
        <NotificationBanner
            v-if="configuration.source !== 'database'"
            kind="warning"
            :message="
                t(
                    'Rules come from files and are read-only. To edit them, set RobotAccessSource=database in conf.d/40-mail-scripts.conf and restart the node. Import existing rules explicitly with --import-robot-access.'
                )
            "
        />
        <p
            v-else
            class="muted"
        >
            {{
                t(
                    'Source: database. Changes apply to future requests; previously accepted work is not cancelled.'
                )
            }}
        </p>
        <section class="card">
            <h2>{{ t('Access rules') }}</h2>
            <p
                v-if="!configuration.rules.length"
                class="muted"
            >
                {{ t('No rules yet. Robot access is denied.') }}
            </p>
            <ul class="rows">
                <li
                    v-for="rule in configuration.rules"
                    :key="`${rule.service}:${rule.address}`"
                >
                    <div>
                        <strong>{{ rule.service }} · {{ rule.address }}</strong>
                        <p>
                            {{ rule.masks }} <span v-if="rule.access">· {{ rule.access }}</span> ·
                            {{ rule.enabled ? t('Enabled') : t('Disabled') }}
                        </p>
                    </div>
                    <div
                        v-if="configuration.source === 'database'"
                        class="actions"
                    >
                        <button
                            class="secondary"
                            :disabled="state.busy.value"
                            @click="editor.edit(rule)"
                        >
                            {{ t('Edit') }}
                        </button>
                        <button
                            class="secondary danger"
                            :disabled="state.busy.value"
                            @click="pendingDelete = rule"
                        >
                            {{ t('Delete') }}
                        </button>
                    </div>
                </li>
            </ul>
        </section>
        <section
            v-if="pendingDelete"
            class="card"
            role="alert"
        >
            <p>
                {{
                    t(
                        'Delete the {service} rule for {address}? Future requests will be rejected.',
                        { service: pendingDelete.service, address: pendingDelete.address }
                    )
                }}
            </p>
            <div class="actions">
                <button
                    class="secondary"
                    :disabled="state.busy.value"
                    @click="pendingDelete = null"
                >
                    {{ t('Cancel') }}
                </button>
                <button
                    class="primary danger"
                    :disabled="state.busy.value"
                    @click="remove"
                >
                    {{ t('Delete rule') }}
                </button>
            </div>
        </section>
        <form
            v-if="configuration.source === 'database'"
            class="card"
            @submit.prevent="save"
        >
            <h2>{{ editing ? t('Edit rule') : t('New rule') }}</h2>
            <NotificationBanner
                v-if="!configuration.peers.length"
                :message="t('Configure a station link first.')"
            />
            <fieldset :disabled="state.busy.value || !configuration.peers.length">
                <div class="form-grid">
                    <label
                        >{{ t('Service')
                        }}<select
                            v-model="model.service"
                            :disabled="editing"
                        >
                            <option>AreaFix</option>
                            <option>FileFix</option>
                            <option value="Tic">TIC</option>
                        </select></label
                    >
                    <label
                        >{{ t('Link')
                        }}<select
                            v-model="model.address"
                            :disabled="editing"
                            required
                        >
                            <option
                                v-if="editing && !configuration.peers.includes(model.address)"
                                :value="model.address"
                            >
                                {{ model.address }} {{ t('(removed from configuration)') }}
                            </option>
                            <option
                                v-for="peer in configuration.peers"
                                :key="peer"
                            >
                                {{ peer }}
                            </option>
                        </select></label
                    >
                    <label
                        >{{ t('Area patterns')
                        }}<input
                            v-model="model.masks"
                            required
                            maxlength="4096"
                            placeholder="test.*,local.news"
                        /><small>{{
                            t(
                                '* matches any characters, ? matches one character; separate patterns with commas.'
                            )
                        }}</small></label
                    >
                    <label v-if="model.service === 'AreaFix'"
                        >{{ t('Permissions')
                        }}<select v-model="model.access">
                            <option value="READ">{{ t('READ — read') }}</option>
                            <option value="BOTH">{{ t('BOTH — read and write') }}</option>
                        </select></label
                    >
                    <label
                        >{{ t('Password')
                        }}<input
                            v-model="model.password"
                            type="password"
                            autocomplete="new-password"
                            :required="!editing"
                            maxlength="256"
                        /><small>{{
                            editing
                                ? t('Leave empty to keep the current password.')
                                : t('Set a separate robot password for a new rule.')
                        }}</small></label
                    >
                </div>
                <p
                    v-if="model.service === 'Tic'"
                    class="muted"
                >
                    {{
                        t(
                            'TIC uses a shared file transfer password. It is stored in the database for outgoing TIC files and is never returned to the browser.'
                        )
                    }}
                </p>
                <label
                    ><input
                        v-model="model.enabled"
                        type="checkbox"
                    />
                    {{ t('Rule enabled') }}</label
                >
                <footer class="actions">
                    <button
                        type="button"
                        class="secondary"
                        @click="editor.reset"
                    >
                        {{ editing ? t('Cancel') : t('Clear') }}
                    </button>
                    <button class="primary">{{ t('Save rule') }}</button>
                </footer>
            </fieldset>
        </form>
    </template>
</template>

<style scoped>
fieldset {
    border: 0;
    padding: 0;
    margin: 0;
    min-width: 0;
}
.rows li {
    gap: 16px;
    flex-wrap: wrap;
}
.rows li p {
    overflow-wrap: anywhere;
}
</style>
