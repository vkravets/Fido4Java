import type { Meta, StoryObj } from '@storybook/vue3-vite';
import PollDetails from '../src/components/PollDetails.vue';
import { attempt } from './fixtures.ts';

const meta = {
    title: 'Connections/PollDetails',
    component: PollDetails,
    args: {
        attempt: attempt(),
        lines: [
            'CONNECT localhost:24554',
            'ConnectException: Connection refused',
            '<script>This is plain journal text</script>'
        ],
        files: [{ peer: '1:500/101', name: 'example.pkt', bytes: 2048, kind: 'PKT' }],
        clipped: false,
        more: false,
        busy: false
    }
} satisfies Meta<typeof PollDetails>;
export default meta;
type Story = StoryObj<typeof meta>;
export const Failed: Story = {};
export const Transferring: Story = { args: { attempt: attempt('TRANSFERRING') } };
export const Empty: Story = { args: { lines: [], files: [] } };
export const Truncated: Story = { args: { clipped: true, more: true } };
