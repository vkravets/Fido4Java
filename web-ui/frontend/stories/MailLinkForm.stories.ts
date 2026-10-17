import type { Meta, StoryObj } from '@storybook/vue3-vite';
import MailLinkForm from '../src/components/MailLinkForm.vue';

const meta = {
    title: 'Settings/MailLinkForm',
    component: MailLinkForm,
    args: {
        existing: true,
        model: {
            address: '1:500/101',
            localAddresses: ['1:500/100'],
            host: 'binkp.example.org',
            port: 24554,
            sessionPassword: '',
            packetPassword: '',
            packetPasswordMode: 'INHERIT',
            autoCreateEchoAccess: 'NONE'
        }
    },
    decorators: [() => ({ template: '<section class="card"><story /></section>' })]
} satisfies Meta<typeof MailLinkForm>;
export default meta;
type Story = StoryObj<typeof meta>;
export const Existing: Story = {};
export const EchoCreation: Story = {
    args: { model: { ...meta.args.model, autoCreateEchoAccess: 'BOTH' } }
};
export const SeparatePacketPassword: Story = {
    args: { model: { ...meta.args.model, packetPasswordMode: 'SEPARATE' } }
};
