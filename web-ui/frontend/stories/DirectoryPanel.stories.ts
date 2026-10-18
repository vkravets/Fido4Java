import type { Meta, StoryObj } from '@storybook/vue3-vite';
import DirectoryPanel from '../src/components/DirectoryPanel.vue';
import { directoryNodes } from './directory-fixture.ts';
const meta = {
    title: 'Directory/DirectoryPanel',
    component: DirectoryPanel,
    args: { modelValue: '', items: directoryNodes, busy: false, hasMore: true, offset: 0 }
} satisfies Meta<typeof DirectoryPanel>;
export default meta;
type Story = StoryObj<typeof meta>;
export const NodesAndPoints: Story = {};
export const Empty: Story = { args: { items: [], hasMore: false } };
export const Loading: Story = { args: { items: [], busy: true, hasMore: false } };
