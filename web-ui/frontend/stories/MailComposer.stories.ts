import { provide, ref } from 'vue';
import type { Meta, StoryObj } from '@storybook/vue3-vite';
import MailComposer from '../src/components/MailComposer.vue';
import MailWorkspace from '../src/components/MailWorkspace.vue';
import { createWorkspace, workspaceKey } from '../src/workspace.ts';
import { directorySearchKey } from '../src/directory.ts';
import { searchDirectory } from './directory-fixture.ts';
import { netmailDraft } from './netmail-fixture.ts';
import { mailWorkspaceFixture } from './mail-workspace-fixture.ts';
import { originalKludgeSnapshot } from '../src/mail-drafts.ts';
import type { MailItem } from '@rpc/bindings.mjs';
const meta = {
    title: 'Mail/MailComposer',
    component: MailComposer,
    args: {
        modelValue: netmailDraft,
        addresses: ['1:500/100', '1:500/100.1'],
        busy: false
    },
    render: (args, context) => ({
        components: { MailComposer },
        setup() {
            provide(directorySearchKey, searchDirectory);
            mailWorkspaceFixture(context.globals.theme === 'dark');
            return { args, draft: ref({ ...args.modelValue }) };
        },
        template: '<MailComposer v-bind="args" v-model="draft" />'
    })
} satisfies Meta<typeof MailComposer>;
export default meta;
type Story = StoryObj<typeof meta>;
export const Reply: Story = {};
export const CompactReply: Story = {
    decorators: [() => ({ template: '<div style="width:600px;max-width:100%"><story /></div>' })]
};
export const WorkspaceReply: Story = {
    render: (args, context) => ({
        components: { MailComposer, MailWorkspace },
        setup() {
            provide(directorySearchKey, searchDirectory);
            mailWorkspaceFixture(context.globals.theme === 'dark');
            provide(workspaceKey, createWorkspace());
            return { args, draft: ref({ ...args.modelValue }) };
        },
        template:
            '<MailWorkspace kind="netmail" :opened="true" :composing="true"><MailComposer v-bind="args" v-model="draft" /></MailWorkspace>'
    })
};
export const NewMessage: Story = {
    args: {
        modelValue: {
            ...netmailDraft,
            replyTo: undefined,
            toName: '',
            toAddress: '',
            subject: '',
            body: ''
        }
    }
};
export const Sending: Story = { args: { busy: true } };

export const RecipientSearch: Story = {
    args: { modelValue: { ...netmailDraft, toName: 'Point Owner', toAddress: '' } },
    play: async ({ canvasElement }) => {
        const input = canvasElement.querySelector<HTMLInputElement>('input[role="combobox"]')!;
        input.focus();
    }
};
export const EchoRecipientSearch: Story = {
    ...RecipientSearch,
    args: { modelValue: { ...netmailDraft, mailbox: 1, area: 'TEST', toName: 'Point Owner' } }
};

const goldedDraft = {
    ...netmailDraft,
    templateInitialized: false,
    body: '',
    originalName: 'Alice Example',
    originalAddress: '1:500/101.2',
    originalDate: '18 Oct 26 12:30:00',
    originalBody: 'Welcome to FidoNet!'
};
const goldedReplyDraft = {
    ...goldedDraft,
    originalKludges: ['\u0001MSGID: 1:500/101.2 12345', '\u0001CHRS: UTF-8 4']
};
export const GoldedNew: Story = {
    args: { modelValue: { ...goldedDraft, composeKind: 'new', replyTo: undefined, toName: 'All' } }
};
export const GoldedReply: Story = {
    args: { modelValue: { ...goldedReplyDraft, composeKind: 'reply' } }
};
export const GoldedQuoted: Story = {
    args: { modelValue: { ...goldedReplyDraft, composeKind: 'quoted' } }
};
export const GoldedForward: Story = {
    args: { modelValue: { ...goldedReplyDraft, composeKind: 'forward', replyTo: undefined } }
};

const referenceDraft = {
    ...goldedReplyDraft,
    originalKludges: [
        '\u0001MSGID: 1:500/101.2 12345',
        '\u0001CHRS: UTF-8 4',
        '\u0001CHRS: CP866 2',
        `\u0001UNKNOWN: REFERENCE_ONLY @quote <b>literal text</b> ${'long-value-'.repeat(20)}`
    ]
};
const referenceRender: NonNullable<Story['render']> = (args, context) => ({
    components: { MailComposer },
    setup() {
        provide(directorySearchKey, searchDirectory);
        mailWorkspaceFixture(context.globals.theme === 'dark');
        const draft = ref({ ...args.modelValue });
        return {
            args,
            draft,
            saved: ref(''),
            submitted: ref(''),
            snapshot: () => JSON.stringify(draft.value)
        };
    },
    template: `
        <MailComposer
            v-bind="args"
            v-model="draft"
            @save="saved = snapshot()"
            @send="submitted = snapshot()"
        />
        <textarea hidden data-testid="draft-state" :value="snapshot()" />
        <textarea hidden data-testid="saved-draft" :value="saved" />
        <textarea hidden data-testid="submitted-draft" :value="submitted" />
    `
});
export const ReferenceReply: Story = {
    args: { modelValue: { ...referenceDraft, composeKind: 'reply' } },
    render: referenceRender
};
export const ReferenceQuoted: Story = {
    args: { modelValue: { ...referenceDraft, composeKind: 'quoted' } },
    render: referenceRender
};
export const ReferenceForward: Story = {
    args: { modelValue: { ...referenceDraft, composeKind: 'forward', replyTo: undefined } },
    render: referenceRender
};
export const ReferenceEcho: Story = {
    args: {
        modelValue: { ...referenceDraft, mailbox: 1, area: 'TEST.ECHO', composeKind: 'quoted' }
    },
    render: referenceRender
};
export const TruncatedReference: Story = {
    args: {
        modelValue: {
            ...goldedReplyDraft,
            ...originalKludgeSnapshot({
                kludges: ['\u0001MSGID: 1:500/101.2 12345', '\u0001X: ' + 'Ж'.repeat(30000)]
            } as MailItem),
            composeKind: 'quoted'
        }
    },
    render: referenceRender
};
export const OmittedReference: Story = {
    args: {
        modelValue: {
            ...goldedReplyDraft,
            ...originalKludgeSnapshot({ kludges: ['\u0001X: ' + 'Ж'.repeat(30000)] } as MailItem),
            composeKind: 'quoted'
        }
    },
    render: referenceRender
};

export const SenderDetails: Story = {
    play: async ({ canvasElement }) => {
        canvasElement.querySelector<HTMLButtonElement>('.composer-sender-toggle')!.click();
    }
};
export const EchoCompact: Story = {
    args: { modelValue: { ...netmailDraft, mailbox: 1, area: 'odessa.fileecho' } }
};
export const AddressSearch: Story = {
    args: { modelValue: { ...netmailDraft, toAddress: '1:500/101.1' } },
    play: async ({ canvasElement }) => {
        canvasElement
            .querySelector<HTMLInputElement>('.composer-fields label:nth-child(2) input')!
            .focus();
    }
};
