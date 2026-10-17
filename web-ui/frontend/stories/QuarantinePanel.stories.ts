import type { Meta, StoryObj } from '@storybook/vue3-vite';
import QuarantinePanel from '../src/components/QuarantinePanel.vue';
import type { QuarantineItem } from '../../build/frontend-generated/bindings.mjs';

const item = {
    id: 'a'.repeat(64),
    name: 'rejected.bad',
    bytes: 256,
    modified: 0,
    checksum: 'b'.repeat(64),
    peer: '1:500/101.0',
    reason: 'WRITE access denied for echo area: test.echo',
    attempts: 1,
    lastAttempt: 0
} as unknown as QuarantineItem;
const meta = {
    title: 'Settings/QuarantinePanel',
    component: QuarantinePanel,
    args: {
        page: { items: [item], nextCursor: '', hasMore: false, peers: ['1:500/101.0'] },
        selected: null,
        action: 'retry',
        peer: '',
        busy: false
    }
} satisfies Meta<typeof QuarantinePanel>;
export default meta;
type Story = StoryObj<typeof meta>;
export const Packets: Story = {};
export const LongFilename: Story = {
    args: {
        page: {
            items: [{ ...item, name: 'a'.repeat(96) + '.bad' }],
            nextCursor: '',
            hasMore: false,
            peers: ['1:500/101.0']
        }
    }
};
export const LegacyRetry: Story = {
    args: {
        selected: { ...item, peer: '' },
        page: {
            items: [{ ...item, peer: '' }],
            nextCursor: '',
            hasMore: false,
            peers: ['1:500/101.0']
        }
    }
};
export const Archive: Story = { args: { selected: item, action: 'archive' } };
export const Empty: Story = {
    args: { page: { items: [], nextCursor: '', hasMore: false, peers: [] } }
};
export const Unavailable: Story = {
    args: {
        page: {
            items: [
                {
                    ...item,
                    checksum: '',
                    peer: '',
                    reason: 'Packet metadata is unavailable; inspect the node log.'
                }
            ],
            nextCursor: '',
            hasMore: false,
            peers: ['1:500/101.0']
        }
    }
};
