import MailApplicationFrame from './MailApplicationFrame.vue';
import { provide, ref } from 'vue';
import { mailWorkspaceFixture } from './mail-workspace-fixture.ts';
import { createWorkspace, workspaceKey, workspacePreferences } from '../src/workspace.ts';
import type { Meta, StoryObj } from '@storybook/vue3-vite';
import EchomailView from '../src/components/EchomailView.vue';
import { echoClientKey } from '../src/echomail.ts';
import type { AppState, RunOptions } from '../src/state.ts';
import { profile } from './fixtures.ts';
import { echoClient, echoDraft } from './echo-fixture.ts';

const meta = {
    title: 'Mail/EchomailView',
    component: EchomailView,
    args: { state: {} as AppState },
    argTypes: { state: { control: false, table: { disable: true } } },
    render: (args, context) => ({
        components: { EchomailView },
        setup() {
            mailWorkspaceFixture(context.globals.theme === 'dark');
            const workspace = createWorkspace();
            workspace.value.value = workspacePreferences(context.parameters.workspace || {});
            provide(workspaceKey, workspace);
            const state = {
                session: ref('storybook-session'),
                profile: ref(profile('POINT')),
                busy: ref(false),
                notice: ref(''),
                async run(operation: () => Promise<unknown>, options: RunOptions = {}) {
                    if (!options.background) this.busy.value = true;
                    try {
                        return await operation();
                    } finally {
                        if (!options.background) this.busy.value = false;
                    }
                }
            } as AppState;
            const client = echoClient(context.parameters.echo || {});
            const message = client.message;
            let messageRequests = 0;
            client.message = async (token, mailbox, area, id) => {
                if (context.parameters.pauseNavigation && messageRequests++ > 0)
                    await new Promise(() => {});
                return message(token, mailbox, area, id);
            };
            provide(echoClientKey, client);
            return { args, state };
        },
        template: '<EchomailView v-bind="args" :state="state" />'
    })
} satisfies Meta<typeof EchomailView>;
export default meta;
type Story = StoryObj<typeof meta>;
export const Tree: Story = {};
export const Flat: Story = { args: { initialMode: 'flat' } };
export const Reading: Story = { args: { initialMessage: '9007199254740994' } };
export const SwitchingMessage: Story = {
    ...Reading,
    parameters: { pauseNavigation: true }
};
export const Conversation: Story = {
    ...Reading,
    parameters: { workspace: { echo: { preview: 'separate' } } }
};
export const MissingParent: Story = { args: { initialMessage: '9007199254740996' } };
export const Compose: Story = { args: { initialDraft: echoDraft } };
export const Reply: Story = {
    args: {
        initialDraft: {
            ...echoDraft,
            toName: 'Bob Example',
            toAddress: '1:500/102',
            subject: 'Re: Welcome to the conference',
            replyTo: '1:500/102 reply',
            body: '> Thanks Alice, glad to be here.\n\nWelcome Bob!'
        }
    }
};
export const ReadOnly: Story = {
    parameters: { echo: { readOnly: true } },
    args: { initialMessage: '9007199254740993' }
};
export const Empty: Story = { parameters: { echo: { empty: true } } };
export const NoAccess: Story = { parameters: { echo: { noAreas: true } } };

export const Below: Story = {
    parameters: { workspace: { echo: { preview: 'below' } } },
    args: { initialMessage: '9007199254740994' }
};
export const Separate: Story = {
    parameters: { workspace: { echo: { preview: 'separate' } } },
    args: { initialMessage: '9007199254740994' }
};
export const Compact: Story = { parameters: { workspace: { echo: { density: 'compact' } } } };

export const ApplicationWorkspace: Story = {
    args: { initialMessage: '9007199254740994' },
    decorators: [
        (_story, context) => ({
            components: { MailApplicationFrame },
            setup: () => ({ theme: context.globals.theme === 'dark' ? 'dark' : 'light' }),
            template:
                '<MailApplicationFrame :theme="theme" page="echo"><story /></MailApplicationFrame>'
        })
    ]
};

export const FocusedDesktop: Story = {
    ...ApplicationWorkspace,
    parameters: { echo: { dense: true } }
};
