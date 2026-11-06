import type { Meta, StoryObj } from '@storybook/vue3-vite';
import EchoConferenceList from '../src/components/EchoConferenceList.vue';

const areas = [
    { name: 'general', messages: 42, readable: true, writeAddresses: ['1:500/100.1'] },
    { name: 'announcements', messages: 12, readable: true, writeAddresses: [] },
    { name: 'post-only', messages: 8, readable: false, writeAddresses: ['1:500/100.1'] },
    {
        name: 'VERY.LONG.CONFERENCE.NAME.THAT.NEEDS.TRUNCATION',
        messages: 12345,
        readable: true,
        writeAddresses: ['1:500/100.1']
    }
];
const meta = {
    title: 'Mail/EchoConferenceList',
    component: EchoConferenceList,
    args: { areas, selected: 'general', busy: false, search: '' }
} satisfies Meta<typeof EchoConferenceList>;
export default meta;
type Story = StoryObj<typeof meta>;
export const Default: Story = {};
export const NoMatches: Story = { args: { search: 'missing' } };
export const Empty: Story = { args: { areas: [] } };
export const Loading: Story = { args: { areas: [], busy: true } };
export const Error: Story = { args: { areas: [], error: 'Could not load conferences.' } };
