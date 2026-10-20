import type { Meta, StoryObj } from '@storybook/vue3-vite';
import MailAppearanceSettings from '../src/components/MailAppearanceSettings.vue';
import NotificationBanner from '../src/components/NotificationBanner.vue';
import type { AppState } from '../src/state.ts';
import { mailWorkspaceFixture } from './mail-workspace-fixture.ts';
const meta = {
    title: 'Mail/MailAppearanceSettings',
    component: MailAppearanceSettings,
    args: { state: {} as AppState },
    argTypes: { state: { control: false } },
    render: (args, context) => ({
        components: { MailAppearanceSettings, NotificationBanner },
        setup() {
            const { state, appearance } = mailWorkspaceFixture(context.globals.theme === 'dark');
            state.busy.value = context.parameters.busy || false;
            if (context.parameters.file) appearance.editor.value = 'operator-green';
            if (context.parameters.error) state.error.value = 'Invalid mail theme color';
            return { args, state };
        },
        template:
            '<NotificationBanner :message="state.error.value" kind="error" /><MailAppearanceSettings v-bind="args" :state="state" />'
    })
} satisfies Meta<typeof MailAppearanceSettings>;
export default meta;
type Story = StoryObj<typeof meta>;
export const Themes: Story = {};
export const Busy: Story = { parameters: { busy: true } };

export const FileTheme: Story = { parameters: { file: true } };
export const InvalidImport: Story = { parameters: { error: true } };
