import type { Meta, StoryObj } from '@storybook/vue3-vite';
import MailLoadMore from '../src/components/MailLoadMore.vue';

const meta = {
    title: 'Mail/MailLoadMore',
    component: MailLoadMore
} satisfies Meta<typeof MailLoadMore>;
export default meta;
type Story = StoryObj<typeof meta>;
export const Ready: Story = {};
export const Disabled: Story = { args: { disabled: true } };
