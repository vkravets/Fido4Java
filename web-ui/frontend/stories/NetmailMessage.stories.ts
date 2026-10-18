import type { Meta, StoryObj } from '@storybook/vue3-vite';
import NetmailMessage from '../src/components/NetmailMessage.vue';
import { netmailMessage } from './netmail-fixture.ts';
const meta = {
    title: 'Mail/NetmailMessage',
    component: NetmailMessage,
    args: { message: netmailMessage, reply: true, busy: false }
} satisfies Meta<typeof NetmailMessage>;
export default meta;
type Story = StoryObj<typeof meta>;
export const Reading: Story = {};
export const LongText: Story = {
    args: {
        message: {
            ...netmailMessage,
            body: 'A'.repeat(240) + '\n' + 'A long message line. '.repeat(180)
        }
    }
};
