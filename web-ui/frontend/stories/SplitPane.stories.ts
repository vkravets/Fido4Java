import type { Meta, StoryObj } from '@storybook/vue3-vite';
import SplitPane from '../src/components/SplitPane.vue';
const meta = {
    title: 'Layout/SplitPane',
    component: SplitPane,
    render: (args) => ({
        components: { SplitPane },
        setup: () => ({ args }),
        template:
            '<SplitPane v-bind="args"><template #list><h2>Messages</h2><p>Alice — Welcome to the conference</p><p>Bob — Re: Welcome</p></template><h2>Welcome to the conference</h2><p>Hello everyone!</p></SplitPane>'
    })
} satisfies Meta<typeof SplitPane>;
export default meta;
type Story = StoryObj<typeof meta>;
export const Right: Story = {};
export const Below: Story = { args: { direction: 'below' } };
