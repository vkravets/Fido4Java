import type { Meta, StoryObj } from '@storybook/vue3-vite';
import QuarantinePanel from '../src/components/QuarantinePanel.vue';
import type { QuarantineItem } from '../../build/frontend-generated/bindings.mjs';
import { ref } from 'vue';

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
    render: (args) => ({
        components: { QuarantinePanel },
        setup() {
            const checked = ref(args.checked || []);
            const peers = ref(args.peers || {});
            const batch = ref(args.batch || []);
            return { args, checked, peers, batch };
        },
        template: `<QuarantinePanel v-bind="args" v-model:checked="checked" v-model:peers="peers"
            :batch="batch" @select-batch="batch = args.page.items.filter(item => checked.includes(item.id))"
            @cancel="batch = []" />`
    }),
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
const mixedItems = [item, { ...item, id: 'c'.repeat(64), name: 'unknown.bad', peer: '' }];
export const BatchRetry: Story = {
    args: {
        page: {
            items: mixedItems,
            nextCursor: '',
            hasMore: true,
            peers: ['1:500/101.0', '1:500/102.0']
        },
        checked: mixedItems.map((entry) => entry.id),
        batch: mixedItems
    }
};
export const BatchReady: Story = {
    args: { ...BatchRetry.args, peers: { ['c'.repeat(64)]: '1:500/102.0' } }
};
export const BatchResults: Story = {
    args: {
        outcomes: [
            { id: item.id, name: item.name, accepted: true, detail: 'Packet accepted.' },
            {
                id: 'c'.repeat(64),
                name: 'unknown.bad',
                accepted: false,
                detail: 'PKT password mismatch'
            }
        ]
    }
};
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
