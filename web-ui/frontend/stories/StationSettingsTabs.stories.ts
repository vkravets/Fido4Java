import type { Meta, StoryObj } from '@storybook/vue3-vite';
import { provide, reactive, ref } from 'vue';
import { robotAccessClientKey, accountModerationClientKey } from '../src/station-clients.ts';
import type { AppState } from '../src/state.ts';
import NetmailRoutesPanel from '../src/components/NetmailRoutesPanel.vue';
import NetmailRoutePreviewForm from '../src/components/NetmailRoutePreviewForm.vue';
import NetmailRoutingPolicyForm from '../src/components/NetmailRoutingPolicyForm.vue';
import BinkpLogPanel from '../src/components/BinkpLogPanel.vue';
import RobotAccess from '../src/components/RobotAccess.vue';
import AccountModeration from '../src/components/AccountModeration.vue';
import { protocolLines } from './protocol-fixture.ts';
import StationSettingsTabs from '../src/components/StationSettingsTabs.vue';
import Sidebar from '../src/components/Sidebar.vue';
import ApplicationHeader from '../src/components/ApplicationHeader.vue';
import NodeForm from '../src/components/NodeForm.vue';
import MailLinksEditor from '../src/components/MailLinksEditor.vue';
import TosserForm from '../src/components/TosserForm.vue';
import EchoAccessPanel from '../src/components/EchoAccessPanel.vue';
import QuarantinePanel from '../src/components/QuarantinePanel.vue';
import PluginSettingsPanel from '../src/components/PluginSettingsPanel.vue';
import { fileEchoExtensions } from './plugin-fixtures.ts';
import { station, uplinks, profile } from './fixtures.ts';
import { t } from '../src/i18n.ts';

const meta = {
    title: 'Settings/StationSettingsTabs',
    component: StationSettingsTabs,
    args: { modelValue: 'general', busy: false },
    render: (args) => ({
        components: {
            StationSettingsTabs,
            NodeForm,
            MailLinksEditor,
            TosserForm,
            EchoAccessPanel,
            QuarantinePanel,
            PluginSettingsPanel,
            NetmailRoutingPolicyForm,
            NetmailRoutesPanel,
            NetmailRoutePreviewForm,
            BinkpLogPanel,
            RobotAccess,
            AccountModeration
        },
        setup() {
            provide(robotAccessClientKey, {
                robotAccess: async () => ({
                    source: 'database',
                    peers: ['1:500/101.0'],
                    rules: []
                }),
                saveRobotRule: async () => {},
                deleteRobotRule: async () => {}
            });
            provide(accountModerationClientKey, {
                accounts: async () => [profile('POINT')],
                decideAka: async () => {}
            });
            const state = {
                session: ref('storybook-session'),
                busy: ref(false),
                notice: ref(''),
                async run(operation: () => Promise<unknown>) {
                    return await operation();
                }
            } as AppState;
            return {
                args,
                t,
                state,
                protocolLines,
                policy: reactive({
                    mode: 'database',
                    defaultHop: '1:500/101.0',
                    allowTransit: false,
                    transitFrom: [],
                    transitTo: []
                }),
                extensions: fileEchoExtensions(),
                routes: [
                    {
                        id: 'regional',
                        priority: 10,
                        pattern: '1:500/*',
                        nextHop: '1:500/101.0',
                        enabled: true
                    }
                ],
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
        template: `<StationSettingsTabs v-bind="args" v-slot="{ group }">
                <template v-if="group === 'routing'">
                    <NetmailRoutingPolicyForm v-model="policy" :peers="['1:500/101.0']" :busy="args.busy" />
                    <NetmailRoutesPanel :rules="routes" :peers="['1:500/101.0']" :writable="true" :busy="args.busy" />
                    <NetmailRoutePreviewForm :peers="['1:500/101.0']" :busy="args.busy" />
                </template>
                <BinkpLogPanel v-if="group === 'logs'" :lines="protocolLines" :busy="args.busy" :more="false" :clipped="false" :rotated="false" />
                <RobotAccess v-if="group === 'robots'" :state="state" />
                <AccountModeration v-if="group === 'accounts'" :state="state" />
                <PluginSettingsPanel v-if="group === 'plugins'" :extensions="extensions" :busy="args.busy" />
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
export const Plugins: Story = { args: { modelValue: 'plugins' } };
export const Routing: Story = { args: { modelValue: 'routing' } };
export const Logs: Story = { args: { modelValue: 'logs' } };
export const Robots: Story = { args: { modelValue: 'robots' } };
export const Accounts: Story = { args: { modelValue: 'accounts' } };

export const WorkspaceLogs: Story = {
    args: { modelValue: 'logs' },
    decorators: [
        (story, context) => ({
            components: { story, Sidebar, ApplicationHeader },
            setup() {
                const collapsed = ref(!!context.parameters.collapsed);
                const menuOpen = ref(false);
                return {
                    collapsed,
                    menuOpen,
                    longPage: !!context.parameters.longPage,
                    profile: profile('ADMIN'),
                    theme: context.globals.theme === 'dark' ? 'dark' : 'light'
                };
            },
            template: `<ApplicationHeader :profile="profile" :theme="theme" :busy="false" :menu-open="menuOpen" @menu="menuOpen = !menuOpen" />
                <div class="workspace" :class="{ 'navigation-collapsed': collapsed, 'navigation-open': menuOpen }">
                    <Sidebar :profile="profile" page="node" :collapsed="collapsed" @collapse="collapsed = !collapsed" />
                    <main class="content" :style="longPage ? { minHeight: '1800px' } : undefined"><story /></main>
                </div>`
        })
    ]
};

export const CollapsedWorkspaceLogs: Story = {
    ...WorkspaceLogs,
    parameters: { collapsed: true }
};

export const LongWorkspaceLogs: Story = {
    ...WorkspaceLogs,
    parameters: { longPage: true }
};

export const LongCollapsedWorkspaceLogs: Story = {
    ...WorkspaceLogs,
    parameters: { longPage: true, collapsed: true }
};
