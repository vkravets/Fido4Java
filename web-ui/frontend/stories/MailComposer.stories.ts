import { ref } from 'vue';
import type { Meta, StoryObj } from '@storybook/vue3-vite';
import MailComposer from '../src/components/MailComposer.vue';
import { netmailDraft } from './netmail-fixture.ts';
const meta = {
    title: 'Mail/MailComposer',
    component: MailComposer,
    args: { modelValue: netmailDraft, addresses: ['1:500/100', '1:500/100.1'], busy: false },
    render: (args) => ({
        components: { MailComposer },
        setup() {
            return { args, draft: ref({ ...args.modelValue }) };
        },
        template: '<MailComposer v-bind="args" v-model="draft" />'
    })
} satisfies Meta<typeof MailComposer>;
export default meta;
type Story = StoryObj<typeof meta>;
export const Reply: Story = {};
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
