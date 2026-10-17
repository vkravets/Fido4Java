import type { Meta, StoryObj } from '@storybook/vue3-vite';
import SetupStationForm from '../src/components/SetupStationForm.vue';
import { station, uplinks } from './fixtures.ts';
const meta = {
    title: 'Settings/SetupStationForm',
    component: SetupStationForm,
    args: { model: station(), links: uplinks(), busy: false },
    decorators: [() => ({ template: '<section class="card"><story /></section>' })]
} satisfies Meta<typeof SetupStationForm>;
export default meta;
type Story = StoryObj<typeof meta>;
export const MultipleUplinks: Story = {};
