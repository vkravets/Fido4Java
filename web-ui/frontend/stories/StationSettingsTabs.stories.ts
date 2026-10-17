import type { Meta, StoryObj } from '@storybook/vue3-vite';
import { reactive } from 'vue';
import StationSettingsTabs from '../src/components/StationSettingsTabs.vue';
import StationSettingsHeader from '../src/components/StationSettingsHeader.vue';
import NodeForm from '../src/components/NodeForm.vue';
import MailLinksEditor from '../src/components/MailLinksEditor.vue';
import TosserForm from '../src/components/TosserForm.vue';
import EchoAccessPanel from '../src/components/EchoAccessPanel.vue';
import QuarantinePanel from '../src/components/QuarantinePanel.vue';
import { station, uplinks } from './fixtures.ts';
import { t } from '../src/i18n.ts';

const meta = {
    title: 'Settings/StationSettingsTabs',
    component: StationSettingsTabs,
    args: { modelValue: 'general', busy: false },
    render: (args) => ({
        components: {
            StationSettingsTabs,
            StationSettingsHeader,
            NodeForm,
            MailLinksEditor,
            TosserForm,
            EchoAccessPanel,
            QuarantinePanel
        },
        setup() {
            return {
                args,
                t,
                model: reactive(station()),
                links: reactive(uplinks()),
                tosser: reactive({
                    intervalSeconds: 5,
                    deleteProcessedFiles: true,
                    enforceEchoAccess: true,
                    autoCreateEcho: false
                })
            };
        },
        template: `<StationSettingsHeader :busy="args.busy" />
            <StationSettingsTabs v-bind="args" v-slot="{ group }">
                <form v-show="group === 'general'" class="card" @submit.prevent>
                    <NodeForm :model="model" :initial="false" />
                    <button class="primary" :disabled="args.busy">{{ t('Save configuration') }}</button>
                </form>
                <section v-show="group === 'links'" class="card"><h2>{{ t('Mail links') }}</h2>
                    <MailLinksEditor :links="links" :existing-count="1" :busy="args.busy" />
                </section>
                <section v-show="group === 'tosser'" class="card"><h2>{{ t('Tosser settings') }}</h2>
                    <TosserForm :model="tosser" />
                </section>
                <div v-show="group === 'echoes'">
                    <EchoAccessPanel :configuration="{ source: 'database', peers: [], areas: [], subscriptions: [] }"
                        :model="{ peer: '', area: '', access: 'WRITE' }" :editing="false" :pending-delete="null" :busy="args.busy" />
                </div>
                <div v-show="group === 'quarantine'">
                    <QuarantinePanel :page="{ items: [], peers: [], hasMore: false, nextCursor: '' }"
                        :selected="null" action="retry" peer="" :busy="args.busy" />
                </div>
            </StationSettingsTabs>`
    })
} satisfies Meta<typeof StationSettingsTabs>;
export default meta;
type Story = StoryObj<typeof meta>;
export const General: Story = {};
export const Links: Story = { args: { modelValue: 'links' } };
export const Tosser: Story = { args: { modelValue: 'tosser' } };
export const Echoes: Story = { args: { modelValue: 'echoes' } };
export const Quarantine: Story = { args: { modelValue: 'quarantine' } };
export const Busy: Story = { args: { busy: true } };
