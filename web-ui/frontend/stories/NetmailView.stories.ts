import { provide, ref } from 'vue';
import type { Meta, StoryObj } from '@storybook/vue3-vite';
import NetmailView from '../src/components/NetmailView.vue';
import { netmailClientKey } from '../src/netmail.ts';
import type { AppState } from '../src/state.ts';
import type { MailDraft } from '@rpc/bindings.mjs';
import { profile } from './fixtures.ts';
import { netmailDraft, netmailMessage } from './netmail-fixture.ts';

const meta = {
    title: 'Mail/NetmailView',
    component: NetmailView,
    args: { state: {} as AppState },
    argTypes: { state: { control: false, table: { disable: true } } },
    render: (args) => ({
        components: { NetmailView },
        setup() {
            const saved: MailDraft[] = [{ ...netmailDraft }];
            const state = {
                profile: ref(profile('POINT')),
                session: ref('storybook-session'),
                busy: ref(false),
                notice: ref(''),
                async run(operation: () => Promise<unknown>) {
                    this.busy.value = true;
                    try {
                        return await operation();
                    } finally {
                        this.busy.value = false;
                    }
                }
            } as AppState;
            provide(netmailClientKey, {
                messages: async () => ({
                    items: [{ ...netmailMessage, toAddress: '1:500/100.1' }],
                    nextId: netmailMessage.id,
                    hasMore: false
                }),
                message: async () => ({ ...netmailMessage, toAddress: '1:500/100.1' }),
                markRead: async () => {},
                drafts: async () => [...saved],
                saveDraft: async (_token, draft) => {
                    saved.push({ ...draft });
                },
                deleteDraft: async (_token, id) => {
                    const index = saved.findIndex((draft) => draft.id === id);
                    if (index >= 0) saved.splice(index, 1);
                },
                send: async () => '1:500/100.1 example-send'
            });
            return { args, state };
        },
        template: '<NetmailView v-bind="args" :state="state" />'
    })
} satisfies Meta<typeof NetmailView>;
export default meta;
type Story = StoryObj<typeof meta>;
export const Inbox: Story = { args: { initialFolder: 'inbox' } };
export const Drafts: Story = { args: { initialFolder: 'drafts' } };
