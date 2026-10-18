import type { Meta, StoryObj } from '@storybook/vue3-vite';
import NetmailRoutesPanel from '../src/components/NetmailRoutesPanel.vue';
const meta = {
    title: 'Routing/NetmailRoutesPanel',
    component: NetmailRoutesPanel,
    args: {
        rules: [
            {
                id: 'regional',
                priority: 10,
                pattern: '1:500/*',
                nextHop: '1:500/101.0',
                enabled: true
            }
        ],
        peers: ['1:500/101.0', '1:500/102.0'],
        writable: true,
        busy: false
    }
} satisfies Meta<typeof NetmailRoutesPanel>;
export default meta;
type Story = StoryObj<typeof meta>;
export const Database: Story = {};
export const FileManaged: Story = { args: { writable: false } };
export const Empty: Story = { args: { rules: [] } };
