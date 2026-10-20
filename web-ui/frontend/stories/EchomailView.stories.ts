import { provide, ref } from 'vue';
import { createWorkspace, workspaceKey, workspacePreferences } from '../src/workspace.ts';
import type { Meta, StoryObj } from '@storybook/vue3-vite';
import EchomailView from '../src/components/EchomailView.vue';
import { echoClientKey } from '../src/echomail.ts';
import type { AppState } from '../src/state.ts';
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
            const workspace = createWorkspace();
            workspace.value.value = workspacePreferences(context.parameters.workspace || {});
            provide(workspaceKey, workspace);
            const state = {
                session: ref('storybook-session'),
                profile: ref(profile('POINT')),
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
            provide(echoClientKey, echoClient(context.parameters.echo || {}));
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
