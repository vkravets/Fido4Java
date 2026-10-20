import type { Meta, StoryObj } from '@storybook/vue3-vite';
import MailTemplateSettings from '../src/components/MailTemplateSettings.vue';
import NotificationBanner from '../src/components/NotificationBanner.vue';
import type { AppState } from '../src/state.ts';
import { mailWorkspaceFixture } from './mail-workspace-fixture.ts';
const meta = {
    title: 'Mail/MailTemplateSettings',
    component: MailTemplateSettings,
    args: { state: {} as AppState },
    argTypes: { state: { control: false } },
    render: (args, context) => ({
        components: { MailTemplateSettings, NotificationBanner },
        setup() {
            const { state } = mailWorkspaceFixture(context.globals.theme === 'dark');
            return { args, state };
        },
        template:
            '<NotificationBanner :message="state.error.value" kind="error" /><MailTemplateSettings v-bind="args" :state="state" />'
    })
} satisfies Meta<typeof MailTemplateSettings>;
export default meta;
type Story = StoryObj<typeof meta>;
export const Templates: Story = {};

export const InvalidToken: Story = { args: { initialTemplate: { id: 'invalid', name: 'Unsupported directive', kind: 'new', body: '@include /private' } } };
