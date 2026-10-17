<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { api, bindings } from '../api.ts';
import { t } from '../i18n.ts';
import type {
    MailLinkConfiguration,
    TosserConfiguration,
    NodeConfiguration
} from '@rpc/bindings.mjs';
import type { AppState } from '../state.ts';
import MailLinksEditor from './MailLinksEditor.vue';
import TosserForm from './TosserForm.vue';

const props = defineProps<{
    state: AppState;
    localAddresses: string[];
    group: 'links' | 'tosser' | 'hidden';
}>();
const emit = defineEmits<{ saved: [configuration?: NodeConfiguration]; saving: [] }>();
const links = ref<MailLinkConfiguration[]>([]);
const tosser = ref<TosserConfiguration>();
const existingCount = ref(0);
const ready = ref(false);

async function refresh() {
    links.value = await api.settings.links(props.state.session.value);
    existingCount.value = links.value.length;
    tosser.value = await api.settings.tosser(props.state.session.value);
    ready.value = true;
}
onMounted(() => props.state.run(refresh));

function addLink() {
    links.value.push(
        new bindings.MailLinkConfiguration({
            address: '',
            localAddresses: [...props.localAddresses],
            host: '',
            port: 24554,
            sessionPassword: '',
            packetPassword: '',
            packetPasswordMode: 'INHERIT',
            autoCreateEchoAccess: 'NONE'
        })
    );
}
function saveLinks() {
    props.state.run(async () => {
        emit('saving');
        await api.settings.saveLinks(
            props.state.session.value,
            links.value.map((value) => new bindings.MailLinkConfiguration(value))
        );
        await refresh();
        const station = await api.settings.current(props.state.session.value);
        emit('saved', station);
    });
}
function saveTosser() {
    props.state.run(async () => {
        emit('saving');
        if (!tosser.value) return;
        await api.settings.saveTosser(
            props.state.session.value,
            new bindings.TosserConfiguration(tosser.value)
        );
        emit('saved');
    });
}
</script>

<template>
    <template v-if="ready">
        <section
            v-show="group === 'links'"
            class="card"
        >
            <h2>{{ t('Mail links') }}</h2>
            <p>
                {{
                    t(
                        'binkp authenticates the connection; PKT authenticates mail packets. These passwords may differ.'
                    )
                }}
            </p>
            <p>
                {{
                    t(
                        'Existing link addresses cannot be renamed or removed here: routes and subscriptions must be reviewed first.'
                    )
                }}
            </p>
            <form @submit.prevent="saveLinks">
                <MailLinksEditor
                    :links="links"
                    :existing-count="existingCount"
                    :busy="state.busy.value"
                    @add="addLink"
                    @remove="links.splice($event, 1)"
                />
                <footer class="actions">
                    <button
                        class="primary"
                        :disabled="state.busy.value"
                    >
                        {{ t('Save links') }}
                    </button>
                </footer>
            </form>
        </section>
        <section
            v-if="tosser"
            v-show="group === 'tosser'"
            class="card"
        >
            <h2>{{ t('Tosser settings') }}</h2>
            <form @submit.prevent="saveTosser">
                <TosserForm :model="tosser" />
                <footer class="actions">
                    <button
                        class="primary"
                        :disabled="state.busy.value"
                    >
                        {{ t('Save tosser settings') }}
                    </button>
                </footer>
            </form>
        </section>
    </template>
</template>
