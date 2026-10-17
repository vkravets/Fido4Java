import type { Meta, StoryObj } from '@storybook/vue3-vite';
import MailLinksEditor from '../src/components/MailLinksEditor.vue';
import { uplinks } from './fixtures.ts';
const meta = {
    title: 'Settings/MailLinksEditor',
    component: MailLinksEditor,
    args: { links: uplinks(), existingCount: 1, busy: false },
    decorators: [() => ({ template: '<section class="card"><story /></section>' })]
} satisfies Meta<typeof MailLinksEditor>;
export default meta;
type Story = StoryObj<typeof meta>;
export const AddingUplink: Story = {};
export const Saving: Story = { args: { busy: true } };
