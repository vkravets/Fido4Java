import type { Meta, StoryObj } from '@storybook/vue3-vite';
import { ref } from 'vue';
import PluginSettingsPanel from '../src/components/PluginSettingsPanel.vue';
import { fileEchoExtensions } from './plugin-fixtures.ts';

const meta = {
    title: 'Plugins/PluginSettingsPanel',
    component: PluginSettingsPanel,
    args: { extensions: fileEchoExtensions(), busy: false },
    render: (args) => ({
        components: { PluginSettingsPanel },
        setup() {
            const lastSave = ref('');
            function saved(id: string, section: string, values: Record<string, string>) {
                const safe = { ...values };
                if ('password' in safe) safe.password = safe.password ? '[provided]' : '';
                lastSave.value = JSON.stringify({ id, section, values: safe });
            }
            return { args, lastSave, saved };
        },
        template:
            '<PluginSettingsPanel v-bind="args" @save="saved" /><output aria-label="Last saved entry">{{ lastSave }}</output>'
    })
} satisfies Meta<typeof PluginSettingsPanel>;
export default meta;
type Story = StoryObj<typeof meta>;
export const Areas: Story = {};
export const Access: Story = { args: { initialSection: 'access' } };
export const Creation: Story = { args: { initialSection: 'creation' } };
export const Robots: Story = { args: { initialSection: 'robots' } };
const files = fileEchoExtensions();
files[0]!.source = 'files';
files[0]!.sections.forEach((section) => {
    section.writable = false;
});
export const FileManaged: Story = { args: { extensions: files } };
export const Empty: Story = { args: { extensions: [] } };
