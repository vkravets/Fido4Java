import type { Meta, StoryObj } from '@storybook/vue3-vite';
import LanguageSelector from '../src/components/LanguageSelector.vue';

const meta = { title: 'Navigation/LanguageSelector', component: LanguageSelector } satisfies Meta<
    typeof LanguageSelector
>;
export default meta;
type Story = StoryObj<typeof meta>;
export const Default: Story = {};
