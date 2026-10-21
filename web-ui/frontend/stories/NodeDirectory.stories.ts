import type { Meta, StoryObj } from '@storybook/vue3-vite';
import { provide } from 'vue';
import NodeDirectory from '../src/components/NodeDirectory.vue';
import { directorySearchKey } from '../src/directory.ts';
import { searchDirectory } from './directory-fixture.ts';

const meta = {
    title: 'Directory/NodeDirectory',
    component: NodeDirectory,
    render: (_args, context) => ({
        components: { NodeDirectory },
        setup() {
            provide(
                directorySearchKey,
                context.parameters.empty
                    ? async () => ({ items: [], nextOffset: 0, hasMore: false })
                    : searchDirectory
            );
        },
        template: '<NodeDirectory />'
    })
} satisfies Meta<typeof NodeDirectory>;
export default meta;
type Story = StoryObj<typeof meta>;
export const Results: Story = {};
export const Empty: Story = { parameters: { empty: true } };
