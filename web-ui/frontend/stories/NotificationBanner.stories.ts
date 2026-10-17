import type { Meta, StoryObj } from '@storybook/vue3-vite';
import NotificationBanner from '../src/components/NotificationBanner.vue';

const meta = {
    title: 'Feedback/NotificationBanner',
    component: NotificationBanner,
    args: { message: 'Settings saved.', kind: 'info' },
    render: (args) => ({
        components: { NotificationBanner },
        setup: () => ({ args }),
        template: `<main class="storybook-scroll">
            <h1>Notification viewport check</h1>
            <p>Scroll this page. The notification stays at the top of the window.</p>
            <NotificationBanner v-bind="args" />
        </main>`
    })
} satisfies Meta<typeof NotificationBanner>;
export default meta;
type Story = StoryObj<typeof meta>;
export const Information: Story = {};
export const ConfigurationApplied: Story = {
    args: {
        message:
            'Configuration applied. New operations use the new settings; existing connections keep their session settings.'
    }
};
export const Warning: Story = {
    args: {
        kind: 'warning',
        message:
            'Configuration saved. Restart the node to apply it. Current connections still use the previous configuration.'
    }
};
export const Error: Story = { args: { kind: 'error', message: 'Could not refresh connections.' } };
export const DuplicateUplink: Story = {
    args: { kind: 'error', message: 'Duplicate link address' }
};
export const LongError: Story = {
    args: {
        kind: 'error',
        message: 'Connection failed:\n' + 'A long remote diagnostic. '.repeat(90)
    }
};
