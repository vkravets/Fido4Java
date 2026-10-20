import { provide, ref } from 'vue';
import { mailWorkspaceFixture } from './mail-workspace-fixture.ts';
import { createWorkspace, workspaceKey, workspacePreferences } from '../src/workspace.ts';
import type { Meta, StoryObj } from '@storybook/vue3-vite';
import NetmailView from '../src/components/NetmailView.vue';
import { netmailClientKey, mailId } from '../src/netmail.ts';
import type { AppState } from '../src/state.ts';
import type { MailDraft } from '@rpc/bindings.mjs';
import { profile } from './fixtures.ts';
import { netmailDraft, netmailMessage } from './netmail-fixture.ts';

const meta = {
    title: 'Mail/NetmailView',
    component: NetmailView,
    args: { state: {} as AppState },
    argTypes: { state: { control: false, table: { disable: true } } },
    render: (args, context) => ({
        components: { NetmailView },
        setup() {
            mailWorkspaceFixture(context.globals.theme === 'dark');
            const workspace = createWorkspace();
            workspace.value.value = workspacePreferences(context.parameters.workspace || {});
            provide(workspaceKey, workspace);
            const saved: MailDraft[] = [{ ...netmailDraft }];
            let deleted = false;
            const messages = Array.from({ length: context.parameters.rows || 1 }, (_, index) => ({
                ...netmailMessage,
                id: (BigInt(mailId(netmailMessage.id)) + BigInt(index)).toString(),
                subject: index ? 'Discussion ' + index : netmailMessage.subject,
                toAddress: '1:500/100.1'
            }));
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
                    items: deleted ? [] : messages,
                    nextId: netmailMessage.id,
                    hasMore: false
                }),
                message: async (_token, _box, _area, id) => ({
                    ...messages.find((item) => mailId(item.id) === mailId(id))!
                }),
                markRead: async () => {},
                deleteMessage: async () => {
                    deleted = true;
                },
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

export const Reading: Story = { args: { initialMessage: String(netmailMessage.id) } };
export const Below: Story = {
    parameters: { workspace: { netmail: { preview: 'below' } } },
    args: { initialMessage: String(netmailMessage.id) }
};
export const Separate: Story = {
    parameters: { workspace: { netmail: { preview: 'separate' } } },
    args: { initialMessage: String(netmailMessage.id) }
};

export const LongList: Story = { parameters: { rows: 100 } };
