import type { Meta, StoryObj } from '@storybook/vue3-vite';
import { nextTick, ref } from 'vue';
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
const inlineMessage = {
    ...netmailMessage,
    body: 'First paragraph\nAE> Quoted text\n-- \nSignature',
    kludges: [
        '\u0001MSGID: 1:500/101.2 first',
        '\u0001X-UNKNOWN: hidden only in the full list',
        '\u0001CHRS: UTF-8 4',
        '\u0001CHRS: CP866 2',
        '\u0001PID: <b>*literal*</b> @quote ' + 'long-value-'.repeat(16)
    ],
    bodyLines: [
        { text: '\u0001MSGID: 1:500/101.2 first', kludge: true },
        { text: 'First paragraph', kludge: false },
        { text: '\u0001CHRS: UTF-8 4', kludge: true },
        { text: 'AE> Quoted text', kludge: false },
        { text: '\u0001CHRS: CP866 2', kludge: true },
        { text: '\u0001PID: <b>*literal*</b> @quote ' + 'long-value-'.repeat(16), kludge: true },
        { text: '-- ', kludge: false },
        { text: 'Signature', kludge: false }
    ]
};
export const KludgeReading: Story = {
    args: { message: inlineMessage, navigation: true },
    render: (args) => ({
        components: { NetmailMessage },
        setup() {
            const message = ref(args.message);
            const next = () => {
                message.value = {
                    ...args.message,
                    id: '9007199254740994',
                    body: 'Second body',
                    kludges: ['\u0001CHRS: CP866 2'],
                    bodyLines: [
                        { text: '\u0001CHRS: CP866 2', kludge: true },
                        { text: 'Second body', kludge: false }
                    ]
                };
            };
            return { args, message, next };
        },
        template: '<NetmailMessage v-bind="args" :message="message" @next="next" />'
    })
};
export const InlineKludges: Story = {
    ...KludgeReading,
    play: async ({ canvasElement }) => {
        canvasElement.querySelector<HTMLInputElement>('.reader-kludge-toggle input')!.click();
    }
};
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
