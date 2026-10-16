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
import MailLinkForm from './MailLinkForm.vue';
import TosserForm from './TosserForm.vue';

const props = defineProps<{ state: AppState; localAddresses: string[] }>();
const emit = defineEmits<{ saved: [configuration?: NodeConfiguration] }>();
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
            packetPasswordMode: 'INHERIT'
        })
    );
}
function saveLinks() {
    props.state.run(async () => {
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
        <section class="card">
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
                <fieldset
                    v-for="(link, index) in links"
                    :key="index"
                    class="mail-link-settings"
                >
                    <legend>{{ link.address || t('New link') }}</legend>
                    <MailLinkForm
                        :model="link"
                        :existing="index < existingCount"
                    />
                    <button
                        v-if="index >= existingCount"
                        type="button"
                        class="secondary"
                        @click="links.splice(index, 1)"
                    >
                        {{ t('Cancel') }}
                    </button>
                </fieldset>
                <footer class="actions">
                    <button
                        type="button"
                        class="secondary"
                        :disabled="state.busy.value"
                        @click="addLink"
                    >
                        {{ t('Add link') }}
                    </button>
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

<style scoped>
.mail-link-settings {
    min-width: 0;
    border: 1px solid var(--line);
    border-radius: 8px;
    padding: 20px;
    margin: 24px 0;
}
.mail-link-settings legend {
    padding: 0 8px;
    color: var(--text);
}
</style>
