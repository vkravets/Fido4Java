import { ref } from 'vue';
import type { Meta, StoryObj } from '@storybook/vue3-vite';
import ApplicationHeader from '../src/components/ApplicationHeader.vue';
import { profile } from './fixtures.ts';
const meta = {
    title: 'Navigation/ApplicationHeader',
    component: ApplicationHeader,
    args: { profile: profile('POINT'), theme: 'light', busy: false, menuOpen: false },
    render: (args, context) => ({
        components: { ApplicationHeader },
        setup: () => ({
            args,
            open: ref(args.menuOpen),
            theme: context.globals.theme === 'dark' ? 'dark' : 'light'
        }),
        template:
            '<ApplicationHeader v-bind="args" :theme="theme" :menu-open="open" @menu="open = !open" />'
    })
} satisfies Meta<typeof ApplicationHeader>;
export default meta;
type Story = StoryObj<typeof meta>;
export const SignedIn: Story = {};
export const DisplayName: Story = {
    args: { profile: { ...profile('POINT'), displayName: 'Alex Example' } }
};
