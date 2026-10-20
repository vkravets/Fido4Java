import type { Meta, StoryObj } from '@storybook/vue3-vite';
import { ref } from 'vue';
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
export const SwitchingPages: Story = {
    render: (args) => ({
        components: { Sidebar },
        setup() {
            const page = ref(args.page);
            return { args, page };
        },
        template: '<Sidebar v-bind="args" :page="page" @navigate="page = $event" />'
    })
};
export const Point: Story = { args: { profile: profile('POINT') } };
export const AwaitingSend: Story = { args: { pendingCount: 12, page: 'netmail' } };
export const PreparingMail: Story = { args: { pendingCount: 12, busy: true, page: 'netmail' } };

export const Collapsed: Story = { args: { collapsed: true } };
export const CollapsedPoint: Story = { args: { collapsed: true, profile: profile('POINT') } };
export const Station: Story = { args: { page: 'node' } };
export const CollapsedStation: Story = { args: { page: 'node', collapsed: true } };
