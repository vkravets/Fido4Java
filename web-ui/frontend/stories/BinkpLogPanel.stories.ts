import type { Meta, StoryObj } from '@storybook/vue3-vite';
import BinkpLogPanel from '../src/components/BinkpLogPanel.vue';
import ConnectionTabs from '../src/components/ConnectionTabs.vue';
import { protocolLines } from './protocol-fixture.ts';
import { t } from '../src/i18n.ts';
const meta = {
    title: 'Connections/BinkpLogPanel',
    component: BinkpLogPanel,
    args: { lines: protocolLines, busy: false, clipped: false, more: true, rotated: false },
    render: (args) => ({
        components: { BinkpLogPanel, ConnectionTabs },
        setup: () => ({ args, t }),
        template: `<ConnectionTabs model-value="log">
            <template #log><BinkpLogPanel v-bind="args" /></template>
            <template #polls><section class="card">{{ t('Outgoing polls') }}</section></template>
        </ConnectionTabs>`
    })
} satisfies Meta<typeof BinkpLogPanel>;
export default meta;
type Story = StoryObj<typeof meta>;
export const Connections: Story = {};
export const Empty: Story = { args: { lines: [], more: false } };
export const Rotated: Story = { args: { rotated: true, clipped: true } };
export const Busy: Story = { args: { busy: true } };
