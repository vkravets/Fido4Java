import type { Meta, StoryObj } from '@storybook/vue3-vite';
import EchoAccessPanel from '../src/components/EchoAccessPanel.vue';

const meta = {
    title: 'Settings/EchoAccessPanel',
    component: EchoAccessPanel,
    args: {
        configuration: {
            source: 'database',
            peers: ['1:500/101.0'],
            areas: ['test.echo'],
            subscriptions: [{ peer: '1:500/101.0', area: 'test.echo', access: 'WRITE' }]
        },
        model: { peer: '1:500/101.0', area: '', access: 'WRITE' },
        editing: false,
        pendingDelete: null,
        busy: false
    }
} satisfies Meta<typeof EchoAccessPanel>;
export default meta;
type Story = StoryObj<typeof meta>;
export const Database: Story = {};
export const FileManaged: Story = {
    args: {
        configuration: {
            source: 'config',
            peers: ['1:500/101.0'],
            areas: ['test.echo'],
            subscriptions: []
        }
    }
};
export const Empty: Story = {
    args: { configuration: { source: 'database', peers: [], areas: [], subscriptions: [] } }
};
export const Revocation: Story = {
    args: { pendingDelete: { peer: '1:500/101.0', area: 'test.echo', access: 'WRITE' } }
};
