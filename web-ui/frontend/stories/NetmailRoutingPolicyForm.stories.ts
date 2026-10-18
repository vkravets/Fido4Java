import { ref } from 'vue';
import type { Meta, StoryObj } from '@storybook/vue3-vite';
import NetmailRoutingPolicyForm from '../src/components/NetmailRoutingPolicyForm.vue';
const meta = {
    title: 'Routing/NetmailRoutingPolicyForm',
    component: NetmailRoutingPolicyForm,
    args: {
        modelValue: {
            mode: 'database',
            defaultHop: '1:500/101.0',
            allowTransit: false,
            transitFrom: [],
            transitTo: []
        },
        peers: ['1:500/101.0', '1:500/102.0'],
        busy: false
    },
    render: (args) => ({
        components: { NetmailRoutingPolicyForm },
        setup() {
            return { args, policy: ref({ ...args.modelValue }) };
        },
        template: '<NetmailRoutingPolicyForm v-bind="args" v-model="policy" />'
    })
} satisfies Meta<typeof NetmailRoutingPolicyForm>;
export default meta;
type Story = StoryObj<typeof meta>;
export const Database: Story = {};
export const Transit: Story = {
    args: {
        modelValue: {
            mode: 'database',
            defaultHop: '',
            allowTransit: true,
            transitFrom: ['1:500/101'],
            transitTo: ['1:500/102']
        }
    }
};
export const Busy: Story = { args: { busy: true } };
