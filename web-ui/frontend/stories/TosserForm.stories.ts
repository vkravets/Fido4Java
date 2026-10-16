import type { Meta, StoryObj } from '@storybook/vue3-vite';
import TosserForm from '../src/components/TosserForm.vue';

const meta = {
    title: 'Settings/TosserForm',
    component: TosserForm,
    args: {
        model: {
            intervalSeconds: 5,
            deleteProcessedFiles: true,
            enforceEchoAccess: true,
            autoCreateEcho: false
        }
    },
    decorators: [() => ({ template: '<section class="card"><story /></section>' })]
} satisfies Meta<typeof TosserForm>;
export default meta;
type Story = StoryObj<typeof meta>;
export const Defaults: Story = {};
