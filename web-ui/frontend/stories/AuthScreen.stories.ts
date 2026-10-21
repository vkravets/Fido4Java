import type { Meta, StoryObj } from '@storybook/vue3-vite';
import { ref } from 'vue';
import { userEvent, within } from 'storybook/test';
import AuthScreen from '../src/components/AuthScreen.vue';
import ApplicationHeader from '../src/components/ApplicationHeader.vue';
import type { AppState } from '../src/state.ts';
import { mailWorkspaceFixture } from './mail-workspace-fixture.ts';

const meta = {
    title: 'Authentication/AuthScreen',
    component: AuthScreen,
    args: { state: {} as AppState },
    argTypes: { state: { control: false } },
    render: (args, context) => ({
        components: { AuthScreen, ApplicationHeader },
        setup() {
            const dark = context.globals.theme === 'dark';
            const theme: 'dark' | 'light' = dark ? 'dark' : 'light';
            const { state } = mailWorkspaceFixture(dark);
            state.profile.value = null;
            state.registration = ref(true);
            state.methods = { password: true, passkey: !context.parameters.passwordOnly };
            state.busy.value = !!context.parameters.busy;
            return { args, state, theme };
        },
        template:
            '<div class="auth-preview"><ApplicationHeader :profile="null" :theme="theme" :busy="state.busy.value" :menu-open="false" /><AuthScreen v-bind="args" :state="state" /></div>'
    })
} satisfies Meta<typeof AuthScreen>;
export default meta;
type Story = StoryObj<typeof meta>;
export const Login: Story = {};
export const PasswordOnly: Story = { parameters: { passwordOnly: true } };
export const Busy: Story = { parameters: { busy: true } };
export const Registration: Story = {
    play: async ({ canvasElement }) => {
        const canvas = within(canvasElement);
        await userEvent.click(canvas.getByRole('button', { name: /No account|Немає облікового/ }));
    }
};
