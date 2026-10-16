import type { Meta, StoryObj } from '@storybook/vue3-vite';
import PollAttempts from '../src/components/PollAttempts.vue';
import { attempt } from './fixtures.ts';

const meta = {
    title: 'Connections/PollAttempts',
    component: PollAttempts,
    args: {
        attempts: [attempt(), { ...attempt('QUEUED'), id: 'queued', source: 'MANUAL' }],
        busy: false
    },
    decorators: [() => ({ template: '<section class="card"><story /></section>' })]
} satisfies Meta<typeof PollAttempts>;
export default meta;
type Story = StoryObj<typeof meta>;
export const MixedStates: Story = {};
export const Busy: Story = { args: { busy: true } };
export const Empty: Story = { args: { attempts: [] } };
