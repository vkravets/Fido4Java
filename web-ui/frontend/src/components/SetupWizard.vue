<script setup lang="ts">
import { t } from '../i18n.ts';
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { api, bindings } from '../api.ts';
import SetupStationForm from './SetupStationForm.vue';
import type { MailLinkConfiguration } from '@rpc/bindings.mjs';
import type { AppState } from '../state.ts';
const props = defineProps<{ state: AppState }>();
const step = ref(0);
const done = ref(false);
const confirmation = ref('');
const model = reactive(new bindings.NodeConfiguration());
const links = ref<MailLinkConfiguration[]>([]);
const localAddresses = computed(() => [model.address, ...(model.aliases || [])].filter(Boolean));
watch(localAddresses, (addresses) => {
    for (const link of links.value) link.localAddresses = [...addresses];
});
function addLink() {
    links.value.push(
        new bindings.MailLinkConfiguration({
            address: '',
            localAddresses: [...localAddresses.value],
            host: '',
            port: 24554,
            sessionPassword: '',
            packetPassword: '',
            packetPasswordMode: 'INHERIT'
        })
    );
}
const token = new URLSearchParams(location.hash.slice(1)).get('setup') || '';
const steps = computed(() => [t('Administrator'), t('Station'), t('Nodelist'), t('Confirmation')]);
onMounted(() =>
    props.state.run(async () => {
        Object.assign(model, await api.settings.current(token));
        addLink();
    })
);

function next() {
    props.state.run(async () => {
        if (
            step.value === 0 &&
            (model.password !== confirmation.value || model.password.length < 12)
        ) {
            throw new Error(t('Passwords must match and contain at least 12 characters.'));
        }
        step.value++;
    });
}

function lookup(download: boolean) {
    props.state.run(async () => {
        const record = await api.settings.lookup(
            token,
            model.address,
            download ? model.nodeListUrl : '',
            model.charset
        );
        if (!record.found)
            throw new Error(t('Address not found. You can enter the details manually.'));
        if (record.boss) {
            props.state.notice.value = t(
                'Point boss found. Enter your details manually: the boss record does not verify the point identity.'
            );
            return;
        }
        for (const key of ['system', 'sysop', 'location'] as const)
            if (record[key]) model[key] = record[key];
        props.state.notice.value = t('Details found in {source}. Review them before saving.', {
            source: record.source
        });
    });
}

function save() {
    props.state.run(async () => {
        await api.settings.save(
            token,
            new bindings.NodeConfiguration({
                ...model,
                initialLinks: links.value.map(
                    (link) =>
                        new bindings.MailLinkConfiguration({
                            ...link,
                            localAddresses: [...localAddresses.value]
                        })
                )
            })
        );
        model.password = confirmation.value = model.linkPassword = '';
        for (const link of links.value) link.sessionPassword = link.packetPassword = '';
        history.replaceState(null, '', location.pathname);
        done.value = true;
    });
}
</script>

<template>
    <main class="setup-layout">
        <div class="page-heading">
            <span class="eyebrow">{{ t('FIRST START') }}</span>
            <h1>{{ t('Set up your station') }}</h1>
            <p>{{ t('Mail services will start after setup is complete.') }}</p>
        </div>
        <section
            v-if="done"
            class="card"
        >
            <h2>{{ t('All set') }}</h2>
            <p>
                {{
                    t(
                        'Settings saved. The server is starting mail services. Open the configured interface address and sign in.'
                    )
                }}
            </p>
            <a :href="model.browserOrigin">{{ t('Open JFtn →') }}</a>
        </section>
        <template v-else-if="model.aliases">
            <ol class="steps">
                <li
                    v-for="(label, index) in steps"
                    :key="label"
                    :class="{ active: index === step, completed: index < step }"
                >
                    <span>{{ index + 1 }}</span
                    >{{ label }}
                </li>
            </ol>
            <form
                class="card wizard"
                @submit.prevent="step < 3 ? next() : save()"
            >
                <template v-if="step === 0">
                    <h2>{{ t('Your administrator account') }}</h2>
                    <p>
                        {{
                            t(
                                'This password is for the JFtn interface and is separate from the binkp password.'
                            )
                        }}
                    </p>
                    <label
                        >{{ t('Username')
                        }}<input
                            v-model="model.login"
                            required
                            pattern="[A-Za-z0-9_.@-]+"
                            maxlength="100"
                            autocomplete="username"
                    /></label>
                    <label
                        >{{ t('Password')
                        }}<input
                            v-model="model.password"
                            required
                            minlength="12"
                            maxlength="1024"
                            type="password"
                            autocomplete="new-password"
                    /></label>
                    <label
                        >{{ t('Confirm password')
                        }}<input
                            v-model="confirmation"
                            required
                            minlength="12"
                            type="password"
                            autocomplete="new-password"
                    /></label>
                </template>
                <template v-if="step === 1"
                    ><h2>{{ t('Station and connection') }}</h2>
                    <SetupStationForm
                        :model="model"
                        :links="links"
                        :busy="state.busy.value"
                        @add="addLink"
                        @remove="links.splice($event, 1)"
                    >
                        <template #lookup>
                            <p>
                                {{
                                    t(
                                        'Enter the default AKA to fill station details from the nodelist.'
                                    )
                                }}
                            </p>
                            <div class="actions">
                                <button
                                    type="button"
                                    class="secondary"
                                    :disabled="state.busy.value || !model.address"
                                    @click="lookup(false)"
                                >
                                    {{ t('Look up in directory') }}
                                </button>
                                <button
                                    type="button"
                                    class="secondary"
                                    :disabled="
                                        state.busy.value || !model.address || !model.nodeListUrl
                                    "
                                    @click="lookup(true)"
                                >
                                    {{ t('Download and fill') }}
                                </button>
                            </div>
                        </template>
                    </SetupStationForm>
                </template>
                <template v-if="step === 2">
                    <h2>{{ t('Check details against the nodelist') }}</h2>
                    <p>
                        {{
                            t(
                                'The directory helps fill station details but does not verify address ownership. You can skip this step.'
                            )
                        }}
                    </p>
                    <label
                        >{{ t('Address')
                        }}<input
                            v-model="model.address"
                            required
                    /></label>
                    <label
                        >{{ t('HTTP source')
                        }}<input
                            v-model="model.nodeListUrl"
                            type="url"
                    /></label>
                    <label
                        >{{ t('Encoding')
                        }}<input
                            v-model="model.charset"
                            required
                    /></label>
                    <div class="actions">
                        <button
                            type="button"
                            class="secondary"
                            :disabled="state.busy.value"
                            @click="lookup(false)"
                        >
                            {{ t('Look up in directory') }}</button
                        ><button
                            type="button"
                            class="secondary"
                            :disabled="state.busy.value || !model.nodeListUrl"
                            @click="lookup(true)"
                        >
                            {{ t('Download and check') }}
                        </button>
                    </div>
                </template>
                <template v-if="step === 3">
                    <h2>{{ t('Ready to start') }}</h2>
                    <dl>
                        <dt>{{ t('Station') }}</dt>
                        <dd>{{ model.system }} · {{ model.address }}</dd>
                        <dt>{{ t('Sysop') }}</dt>
                        <dd>{{ model.sysop }}</dd>
                        <dt>{{ t('Uplinks') }}</dt>
                        <dd
                            v-for="(link, index) in links"
                            :key="index"
                        >
                            {{ link.address }} ·
                            {{ link.host ? `${link.host}:${link.port}` : t('Incoming only') }}
                        </dd>
                        <dt>{{ t('Mail') }}</dt>
                        <dd>{{ model.storage }}</dd>
                        <dt>{{ t('Administrator') }}</dt>
                        <dd>{{ model.login }}</dd>
                    </dl>
                    <p>
                        {{
                            t(
                                'Point registration will be open. Each request requires administrator approval.'
                            )
                        }}
                    </p>
                </template>
                <footer class="actions">
                    <button
                        v-if="step > 0"
                        class="secondary"
                        type="button"
                        :disabled="state.busy.value"
                        @click="step--"
                    >
                        {{ t('Back') }}</button
                    ><button
                        class="primary"
                        :disabled="state.busy.value"
                    >
                        {{ step === 3 ? t('Save and start') : t('Continue →') }}
                    </button>
                </footer>
            </form>
        </template>
        <p v-else>{{ t('Open the complete setup link from the server console.') }}</p>
    </main>
</template>
