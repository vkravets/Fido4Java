import type { Meta, StoryObj } from '@storybook/vue3-vite';
import { ref } from 'vue';
import AccountSettings from '../src/components/AccountSettings.vue';
import NotificationBanner from '../src/components/NotificationBanner.vue';
import type { AppState } from '../src/state.ts';
import { mailWorkspaceFixture } from './mail-workspace-fixture.ts';
import { profile } from './fixtures.ts';

const meta = {
    title: 'Settings/AccountSettings',
    component: AccountSettings,
    args: { state: {} as AppState, initialTab: 'templates' },
    argTypes: { state: { control: false } },
    render: (args, context) => ({
        components: { AccountSettings, NotificationBanner },
        setup() {
            const { state } = mailWorkspaceFixture(context.globals.theme === 'dark');
            state.theme = ref(context.globals.theme === 'dark' ? 'dark' : 'light');
            state.profile.value = profile(context.parameters.role === 'ADMIN' ? 'ADMIN' : 'POINT');
            return { args, state };
        },
        template:
            '<NotificationBanner :message="state.error.value" kind="error" /><AccountSettings v-bind="args" :state="state" />'
    })
} satisfies Meta<typeof AccountSettings>;
export default meta;
type Story = StoryObj<typeof meta>;
export const PointTemplates: Story = {};
export const AdministratorTemplates: Story = { parameters: { role: 'ADMIN' } };
