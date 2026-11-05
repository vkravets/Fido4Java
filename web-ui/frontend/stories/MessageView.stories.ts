import type { Meta, StoryObj } from '@storybook/vue3-vite';
import { nextTick, ref, provide } from 'vue';
import { createWorkspace, workspaceKey } from '../src/workspace.ts';
import MessageView from '../src/components/MessageView.vue';
import { netmailMessage } from './netmail-fixture.ts';
const meta = {
    title: 'Mail/MessageView',
    component: MessageView,
    args: { message: netmailMessage, reply: true, busy: false }
} satisfies Meta<typeof MessageView>;
export default meta;
type Story = StoryObj<typeof meta>;
export const Reading: Story = {};
const inlineMessage = {
    ...netmailMessage,
    body: 'First paragraph\nAE> Quoted text\n-- \nSignature',
    kludges: [
        '\u0001MSGID: 1:500/101.2 first',
        '\u0001CUSTOM: visible by default',
        '\u0001CHRS: UTF-8 4',
        '\u0001CHRS: CP866 2',
        '\u0001PID: <b>*literal*</b> @quote ' + 'long-value-'.repeat(16)
    ],
    bodyLines: [
        { text: '\u0001MSGID: 1:500/101.2 first', kludge: true, known: true },
        { text: 'First paragraph', kludge: false, known: false },
        { text: '\u0001CUSTOM: visible by default', kludge: true, known: false },
        { text: '\u0001CHRS: UTF-8 4', kludge: true, known: true },
        { text: 'AE> Quoted text', kludge: false, known: false },
        { text: '\u0001CHRS: CP866 2', kludge: true, known: true },
        {
            text: '\u0001PID: <b>*literal*</b> @quote ' + 'long-value-'.repeat(16),
            kludge: true,
            known: true
        },
        { text: '-- ', kludge: false, known: false },
        { text: 'Signature', kludge: false, known: false }
    ]
};
export const KludgeReading: Story = {
    args: { message: inlineMessage, navigation: true },
    render: (args, context) => ({
        components: { MessageView },
        setup() {
            const workspace = createWorkspace();
            void workspace.load('storybook');
            if (context.parameters.unknownKludges === 'toggle')
                workspace.value.value.unknownKludges = 'toggle';
            if (context.parameters.showKludgeBlock) workspace.value.value.showKludgeBlock = true;
            provide(workspaceKey, workspace);
            const message = ref(args.message);
            const visible = ref(true);
            const navigation = ref(0);
            const next = () => {
                navigation.value++;
                message.value =
                    navigation.value === 1
                        ? {
                              ...args.message,
                              id: '9007199254740994',
                              body: 'Second body',
                              kludges: ['\u0001CHRS: CP866 2'],
                              bodyLines: [
                                  { text: '\u0001CHRS: CP866 2', kludge: true, known: true },
                                  { text: 'Second body', kludge: false, known: false }
                              ]
                          }
                        : {
                              ...args.message,
                              id: '9007199254740995',
                              body: 'Body without kludges',
                              kludges: [],
                              bodyLines: [
                                  { text: 'Body without kludges', kludge: false, known: false }
                              ]
                          };
            };
            return {
                args,
                message,
                next,
                visible,
                sessionNavigation: context.parameters.sessionNavigation
            };
        },
        template:
            '<button v-if="sessionNavigation" data-testid="remount-reader" @click="visible = !visible">Toggle reader</button><MessageView v-if="visible" v-bind="args" :message="message" @next="next" />'
    })
};
export const InlineKludges: Story = {
    ...KludgeReading,
    play: async ({ canvasElement }) => {
        canvasElement.querySelector<HTMLButtonElement>('.reader-kludge-button')!.click();
    }
};
export const SessionNavigation: Story = {
    ...KludgeReading,
    parameters: { sessionNavigation: true }
};
export const FullKludgeBlock: Story = {
    ...KludgeReading,
    parameters: { showKludgeBlock: true }
};
export const InlineAndFullKludges: Story = {
    ...FullKludgeBlock,
    play: InlineKludges.play
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

export const UnknownKludgesOnToggle: Story = {
    ...KludgeReading,
    parameters: { unknownKludges: 'toggle' }
};
export const UnknownKludgesOnly: Story = {
    ...KludgeReading,
    args: {
        message: {
            ...inlineMessage,
            bodyLines: inlineMessage.bodyLines.filter((line) => !line.known)
        }
    }
};
