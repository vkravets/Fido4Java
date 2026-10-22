import { ref } from 'vue';
import type { Meta, StoryObj } from '@storybook/vue3-vite';
import FidoEditor from '../src/components/FidoEditor.vue';
import { mailWorkspaceFixture, styledMail } from './mail-workspace-fixture.ts';
import { dosMail } from './dos-mail-fixture.ts';
const meta = {
    title: 'Mail/FidoEditor',
    component: FidoEditor,
    args: { modelValue: styledMail },
    render: (args, context) => ({
        components: { FidoEditor },
        setup() {
            mailWorkspaceFixture(context.globals.theme === 'dark');
            return { args, value: ref(args.modelValue) };
        },
        template: '<FidoEditor v-bind="args" v-model="value" />'
    })
} satisfies Meta<typeof FidoEditor>;
export default meta;
type Story = StoryObj<typeof meta>;
export const Writing: Story = {};
export const Focused: Story = {
    args: { modelValue: 'Hello All.\n\n\nVova Kravets' },
    play: async ({ canvasElement }) => {
        canvasElement.querySelector<HTMLElement>('.cm-content')!.focus();
    }
};
export const ReadOnly: Story = { args: { disabled: true } };

export const Source: Story = { args: { initialMode: 'source' } };
export const Preview: Story = { args: { initialMode: 'preview' } };
export const DosGraphics: Story = {
    args: { modelValue: dosMail },
    render: (args, context) => ({
        components: { FidoEditor },
        setup() {
            mailWorkspaceFixture(context.globals.theme === 'dark');
            return { args, value: ref(args.modelValue) };
        },
        template:
            '<FidoEditor v-bind="args" v-model="value" /><textarea hidden data-testid="dos-original" :value="value" />'
    })
};
