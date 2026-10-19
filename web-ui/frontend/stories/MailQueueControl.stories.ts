import { ref } from 'vue';
import type { Meta, StoryObj } from '@storybook/vue3-vite';
import MailQueueControl from '../src/components/MailQueueControl.vue';
const meta = {
    title: 'Mail/MailQueueControl',
    component: MailQueueControl,
    args: { count: 7, busy: false },
    render: (args) => ({
        components: { MailQueueControl },
        setup() {
            const count = ref(args.count);
            return { args, count };
        },
        template: '<MailQueueControl v-bind="args" :count="count" @send="count = 0" />'
    })
} satisfies Meta<typeof MailQueueControl>;
export default meta;
type Story = StoryObj<typeof meta>;
export const Waiting: Story = {};
export const Empty: Story = { args: { count: 0 } };
export const Busy: Story = { args: { busy: true } };
