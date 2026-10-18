import type { Meta, StoryObj } from '@storybook/vue3-vite';
import NetmailDrafts from '../src/components/NetmailDrafts.vue';
import { netmailDraft } from './netmail-fixture.ts';
const meta = {
    title: 'Mail/NetmailDrafts',
    component: NetmailDrafts,
    args: { drafts: [netmailDraft], busy: false }
} satisfies Meta<typeof NetmailDrafts>;
export default meta;
type Story = StoryObj<typeof meta>;
export const Saved: Story = {};
export const Empty: Story = { args: { drafts: [] } };
