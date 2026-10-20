import type { Meta, StoryObj } from '@storybook/vue3-vite';
import FidoText from '../src/components/FidoText.vue';
import { provide } from 'vue';
import { createMailAppearance, mailAppearanceKey } from '../src/mail-themes.ts';
import { styledMail } from './mail-text-fixture.ts';
const meta = {
    title: 'Mail/FidoText',
    component: FidoText,
    args: { text: styledMail },
    render: (args, context) => ({
        components: { FidoText },
        setup() {
            const appearance = createMailAppearance();
            appearance.reader.value = context.globals.theme === 'dark' ? 'graphite' : 'paper';
            provide(mailAppearanceKey, appearance);
            return { args };
        },
        template: '<FidoText v-bind="args" />'
    })
} satisfies Meta<typeof FidoText>;
export default meta;
type Story = StoryObj<typeof meta>;
export const Highlighted: Story = {};
export const Source: Story = { args: { source: true } };
