import type { Meta, StoryObj } from '@storybook/vue3-vite';
import NetmailList from '../src/components/NetmailList.vue';
import { netmailMessage } from './netmail-fixture.ts';
const meta = {
    title: 'Mail/NetmailList',
    component: NetmailList,
    args: {
        items: [
            netmailMessage,
            { ...netmailMessage, id: '2', subject: 'Your subscription', read: true }
        ],
        selected: '',
        busy: false,
        hasMore: true
    }
} satisfies Meta<typeof NetmailList>;
export default meta;
type Story = StoryObj<typeof meta>;
export const Inbox: Story = {};
export const Outbox: Story = {
    args: {
        outgoing: true,
        items: [
            { ...netmailMessage, attributes: 8 },
            { ...netmailMessage, id: '3', attributes: 1, subject: 'Pending delivery' }
        ]
    }
};
export const Empty: Story = { args: { items: [], hasMore: false } };
export const Loading: Story = { args: { items: [], busy: true, hasMore: false } };
