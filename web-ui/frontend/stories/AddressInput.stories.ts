import { provide, ref } from 'vue';
import type { Meta, StoryObj } from '@storybook/vue3-vite';
import AddressInput from '../src/components/AddressInput.vue';
import { directorySearchKey } from '../src/directory.ts';
import { searchDirectory } from './directory-fixture.ts';
import { t } from '../src/i18n.ts';
const meta = {
    title: 'Directory/AddressInput',
    component: AddressInput,
    args: { modelValue: '1:500/10' },
    render: (args, context) => ({
        components: { AddressInput },
        setup() {
            const value = ref(args.modelValue);
            provide(
                directorySearchKey,
                context.parameters.offline
                    ? async () => {
                          throw new Error('offline');
                      }
                    : searchDirectory
            );
            return { args, value, t };
        },
        template: `<section class="card" style="min-height: 360px"><label>{{ t('Address') }}<AddressInput v-bind="args" v-model="value" :aria-label="t('Address')" /></label></section>`
    }),
    play: async ({ canvasElement }) => {
        const input = canvasElement.querySelector('input')!;
        input.setSelectionRange(input.value.length, input.value.length);
        input.focus();
    }
} satisfies Meta<typeof AddressInput>;
export default meta;
type Story = StoryObj<typeof meta>;
export const Matches: Story = {};
export const NodesOnly: Story = { args: { includePoints: false } };
export const MultipleAkas: Story = { args: { modelValue: '1:500/100 1:500/10', multiple: true } };
export const Unavailable: Story = { parameters: { offline: true } };
