import { provide, ref } from 'vue';
import type { Meta, StoryObj } from '@storybook/vue3-vite';
import NetmailView from '../src/components/NetmailView.vue';
import EchomailView from '../src/components/EchomailView.vue';
import { netmailClientKey } from '../src/netmail.ts';
import { echoClientKey } from '../src/echomail.ts';
import type { EchoClient } from '../src/echomail.ts';
import { createWorkspace, workspaceKey } from '../src/workspace.ts';
import { directorySearchKey } from '../src/directory.ts';
import { mailWorkspaceFixture } from './mail-workspace-fixture.ts';
import { netmailMessage } from './netmail-fixture.ts';
import type { AppState } from '../src/state.ts';
import type { MailDraft, MailItem } from '@rpc/bindings.mjs';

const meta = {
    title: 'Mail/KludgeWorkflow',
    component: NetmailView,
    args: { state: {} as AppState },
    argTypes: { state: { control: false } },
    render: (_args, context) => ({
        components: { NetmailView, EchomailView },
        setup() {
            const { state } = mailWorkspaceFixture(context.globals.theme === 'dark');
            provide(workspaceKey, createWorkspace());
            provide(directorySearchKey, async () => ({ items: [], nextOffset: 0, hasMore: false }));
            const source: MailItem = {
                ...netmailMessage,
                toAddress: state.profile.value!.defaultAka,
                subject: 'Kludge workflow source',
                body: 'ORIGINAL_BODY_ONLY\nSecond source line',
                kludges: [
                    '\u0001MSGID: 1:500/101.2 source',
                    '\u0001CHRS: UTF-8 4',
                    '\u0001CUSTOM: REFERENCE_ONLY @quote <b>literal</b>',
                    '\u0001CUSTOM: REFERENCE_ONLY @quote <b>literal</b>'
                ],
                bodyLines: [
                    { text: '\u0001MSGID: 1:500/101.2 source', kludge: true, known: true },
                    { text: 'ORIGINAL_BODY_ONLY', kludge: false, known: false },
                    { text: '\u0001CHRS: UTF-8 4', kludge: true, known: true },
                    {
                        text: '\u0001CUSTOM: REFERENCE_ONLY @quote <b>literal</b>',
                        kludge: true,
                        known: false
                    },
                    { text: 'Second source line', kludge: false, known: false },
                    {
                        text: '\u0001CUSTOM: REFERENCE_ONLY @quote <b>literal</b>',
                        kludge: true,
                        known: false
                    }
                ]
            };
            if (context.parameters.snapshot === 'absent') {
                delete source.kludges;
                delete source.bodyLines;
            } else if (context.parameters.snapshot) {
                const huge = '\u0001CUSTOM: ' + 'Ж'.repeat(30000);
                source.kludges =
                    context.parameters.snapshot === 'truncated'
                        ? [source.kludges![0]!, huge]
                        : [huge];
                delete source.bodyLines;
            }
            const { body: _body, kludges: _kludges, bodyLines: _lines, ...summary } = source;
            const saved = ref('');
            const submitted = ref('');
            const store = new Map<string, string>();
            const draftReads = ref(0);
            type Client = EchoClient &
                Pick<typeof import('../src/api.ts').api.ui, 'messages' | 'deleteMessage'>;
            const client: Client = {
                messages: async () => ({
                    items: [{ ...summary }],
                    nextId: source.id,
                    hasMore: false
                }),
                echoMessages: async () => ({
                    items: [{ ...summary }],
                    nextId: source.id,
                    hasMore: false
                }),
                areas: async () => [
                    {
                        name: 'general',
                        messages: 1,
                        readable: true,
                        writeAddresses: state.profile.value!.allowedAkas
                    }
                ],
                message: async () => JSON.parse(JSON.stringify(source)),
                echoContext: async () => ({
                    missingParent: false,
                    replies: { items: [], nextId: 0, hasMore: false }
                }),
                markRead: async () => {},
                deleteMessage: async () => {},
                saveDraft: async (_token, draft) => {
                    saved.value = JSON.stringify(draft);
                    store.set(draft.id, saved.value);
                },
                drafts: async () => {
                    draftReads.value++;
                    return [...store.values()].map((document) => JSON.parse(document));
                },
                deleteDraft: async (_token, id) => {
                    store.delete(id);
                },
                send: async (_token, draft) => {
                    submitted.value = JSON.stringify(draft);
                    store.delete(draft.id);
                    return '1:500/100.1 submitted';
                }
            };
            provide(netmailClientKey, client);
            provide(echoClientKey, client);
            const echo = ref(context.parameters.mailKind === 'echo');
            const forwarded = ref<MailDraft>();
            function forward(draft: MailDraft) {
                forwarded.value = draft;
                echo.value = false;
            }
            return { state, echo, forwarded, forward, source, saved, submitted, draftReads };
        },
        template: `
            <EchomailView v-if="echo" :state="state" :initial-message="String(source.id)" @forward="forward" />
            <NetmailView v-else :state="state" :initial-message="String(source.id)" :initial-draft="forwarded" />
            <textarea hidden data-testid="saved-draft" :value="saved" />
            <textarea hidden data-testid="submitted-draft" :value="submitted" />
            <textarea hidden data-testid="source-state" :value="JSON.stringify(source)" />
            <input hidden data-testid="draft-reads" :value="draftReads" />
        `
    })
} satisfies Meta<typeof NetmailView>;
export default meta;
type Story = StoryObj<typeof meta>;
export const Netmail: Story = {};
export const Echomail: Story = { parameters: { mailKind: 'echo' } };
export const WithoutControls: Story = { parameters: { snapshot: 'absent' } };
export const Truncated: Story = { parameters: { snapshot: 'truncated' } };
export const Omitted: Story = { parameters: { snapshot: 'omitted' } };
