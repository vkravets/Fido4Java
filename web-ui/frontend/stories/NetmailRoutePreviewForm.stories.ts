import type { Meta, StoryObj } from '@storybook/vue3-vite';
import NetmailRoutePreviewForm from '../src/components/NetmailRoutePreviewForm.vue';
const meta = {
    title: 'Routing/NetmailRoutePreviewForm',
    component: NetmailRoutePreviewForm,
    args: {
        peers: ['1:500/101.0'],
        result: { reason: 'RULE', nextHop: '1:500/101.0', ruleId: 'regional', permitted: true },
        busy: false
    }
} satisfies Meta<typeof NetmailRoutePreviewForm>;
export default meta;
type Story = StoryObj<typeof meta>;
export const MatchingRule: Story = {};
export const NoRoute: Story = {
    args: { result: { reason: 'NO_ROUTE', nextHop: '', ruleId: '', permitted: false } }
};
