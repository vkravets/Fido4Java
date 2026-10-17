import type { Meta, StoryObj } from '@storybook/vue3-vite';
import StationSettingsHeader from '../src/components/StationSettingsHeader.vue';

const meta = {
    title: 'Settings/StationSettingsHeader',
    component: StationSettingsHeader,
    args: { busy: false }
} satisfies Meta<typeof StationSettingsHeader>;
export default meta;
type Story = StoryObj<typeof meta>;
export const Ready: Story = {};
export const Busy: Story = { args: { busy: true } };
