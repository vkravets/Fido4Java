import { provide, ref } from 'vue';
import type { Meta, StoryObj } from '@storybook/vue3-vite';
import MailComposer from '../src/components/MailComposer.vue';
import MailWorkspace from '../src/components/MailWorkspace.vue';
import { createWorkspace, workspaceKey } from '../src/workspace.ts';
import { netmailDraft } from './netmail-fixture.ts';
import { mailWorkspaceFixture } from './mail-workspace-fixture.ts';
const meta = {
    title: 'Mail/MailComposer',
    component: MailComposer,
    args: {
        modelValue: netmailDraft,
        addresses: ['1:500/100', '1:500/100.1'],
        busy: false
    },
    render: (args, context) => ({
        components: { MailComposer },
        setup() {
            mailWorkspaceFixture(context.globals.theme === 'dark');
            return { args, draft: ref({ ...args.modelValue }) };
        },
        template: '<MailComposer v-bind="args" v-model="draft" />'
    })
} satisfies Meta<typeof MailComposer>;
export default meta;
type Story = StoryObj<typeof meta>;
export const Reply: Story = {};
export const CompactReply: Story = {
    decorators: [() => ({ template: '<div style="width:600px;max-width:100%"><story /></div>' })]
};
export const WorkspaceReply: Story = {
    render: (args, context) => ({
        components: { MailComposer, MailWorkspace },
        setup() {
            mailWorkspaceFixture(context.globals.theme === 'dark');
            provide(workspaceKey, createWorkspace());
            return { args, draft: ref({ ...args.modelValue }) };
        },
        template:
            '<MailWorkspace kind="netmail" :opened="true"><MailComposer v-bind="args" v-model="draft" /></MailWorkspace>'
    })
};
export const NewMessage: Story = {
    args: {
        modelValue: {
            ...netmailDraft,
            replyTo: undefined,
            toName: '',
            toAddress: '',
            subject: '',
            body: ''
        }
    }
};
export const Sending: Story = { args: { busy: true } };
