import type { Meta, StoryObj } from '@storybook/vue3-vite';
import { provide } from 'vue';
import Connections from '../src/components/Connections.vue';
import { pollClientKey } from '../src/polling.ts';
import type { AppState } from '../src/state.ts';
import { mailWorkspaceFixture } from './mail-workspace-fixture.ts';
import { attempt, uplinks } from './fixtures.ts';

const meta = {
    title: 'Connections/Connections',
    component: Connections,
    args: { state: {} as AppState },
    argTypes: { state: { control: false } },
    render: (args, context) => ({
        components: { Connections },
        setup() {
            const { state } = mailWorkspaceFixture(context.globals.theme === 'dark');
            const empty = context.parameters.empty;
            provide(pollClientKey, {
                pollQueue: async () => (empty ? [] : [attempt('QUEUED')]),
                pollHistory: async () => ({
                    items: empty ? [] : [attempt()],
                    nextCursor: '',
                    hasMore: false
                }),
                links: async () => uplinks().map((link) => ({ ...link, subscriptions: [] })),
                pollDetails: async () => attempt(),
                pollLog: async () => ({ lines: [], nextOffset: 0, hasMore: false }),
                outbound: async () => [],
                poll: async () => attempt('QUEUED'),
                cancelPoll: async () => {}
            });
            return { args, state };
        },
        template: '<Connections v-bind="args" :state="state" />'
    })
} satisfies Meta<typeof Connections>;
export default meta;
type Story = StoryObj<typeof meta>;
export const Queue: Story = {};
export const Empty: Story = { parameters: { empty: true } };
