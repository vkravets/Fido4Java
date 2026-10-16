import type { Meta, StoryObj } from '@storybook/vue3-vite';
import NodeForm from '../src/components/NodeForm.vue';
import { station } from './fixtures.ts';

const meta = {
    title: 'Settings/NodeForm',
    component: NodeForm,
    args: { model: station(), initial: false },
    decorators: [() => ({ template: '<section class="card"><story /></section>' })]
} satisfies Meta<typeof NodeForm>;
export default meta;
type Story = StoryObj<typeof meta>;
export const Settings: Story = {};
export const InitialSetup: Story = { args: { model: station(), initial: true } };
