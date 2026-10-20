import type { Meta, StoryObj } from '@storybook/vue3-vite';
import { nextTick } from 'vue';
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
export const Deleting: Story = { args: { busy: true } };
export const LongText: Story = {
    args: {
        message: {
            ...netmailMessage,
            body: 'A'.repeat(240) + '\n' + 'A long message line. '.repeat(180)
        }
    }
};

export const ReaderActions: Story = {
    args: { navigation: true, forwardable: true },
    parameters: {
        docs: {
            description: {
                story: 'The message header uses compact icon actions with translated tooltips. Additional actions retain labels inside the More menu.'
            }
        }
    }
};
export const MoreActions: Story = {
    ...ReaderActions,
    play: async ({ canvasElement }) => {
        await nextTick();
        canvasElement.querySelector<HTMLDetailsElement>('.reader-more')!.open = true;
    }
};
