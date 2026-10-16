import type { Meta, StoryObj } from '@storybook/vue3-vite';
import Sidebar from '../src/components/Sidebar.vue';
import { profile } from './fixtures.ts';

const meta = {
    title: 'Navigation/Sidebar',
    component: Sidebar,
    args: { profile: profile('ADMIN'), page: 'settings' },
    decorators: [() => ({ template: '<div class="storybook-sidebar"><story /></div>' })]
} satisfies Meta<typeof Sidebar>;
export default meta;
type Story = StoryObj<typeof meta>;
export const Administrator: Story = {};
export const Point: Story = { args: { profile: profile('POINT') } };
