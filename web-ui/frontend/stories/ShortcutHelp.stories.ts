import type { Meta, StoryObj } from '@storybook/vue3-vite';
import { onMounted, ref } from 'vue';
import ShortcutHelp from '../src/components/ShortcutHelp.vue';
import { shortcutPreferences, useShortcuts } from '../src/shortcuts.ts';

const meta = {
    title: 'Navigation/ShortcutHelp',
    component: ShortcutHelp,
    args: { administrator: true },
    render: (args, context) => ({
        components: { ShortcutHelp },
        setup() {
            const model = useShortcuts();
            model.value.value = shortcutPreferences({
                profile: context.parameters.shortcutsProfile,
                enabled: context.parameters.enabled,
                overrides: context.parameters.overrides
            });
            const actions = ref(0);
            onMounted(() => {
                model.helpOpen.value = context.parameters.open !== false;
            });
            return { args, model, actions };
        },
        template: `<div style="padding: 2rem">
            <button class="primary" @click="model.helpOpen.value = true">Open help</button>
            <button v-shortcut="'mail.new'" @click="actions++">New message</button>
            <p>Actions: {{ actions }}</p>
            <label>Message text<textarea aria-label="Message text"></textarea></label>
            <div contenteditable="true" role="textbox" aria-label="Rich text">Editable text</div>
            <ShortcutHelp v-bind="args" />
        </div>`
    })
} satisfies Meta<typeof ShortcutHelp>;
export default meta;
type Story = StoryObj<typeof meta>;
export const Default: Story = {};
export const Golded: Story = { parameters: { shortcutsProfile: 'golded' } };
export const Closed: Story = { parameters: { open: false } };
export const Disabled: Story = { parameters: { open: false, enabled: false } };
export const Personal: Story = {
    args: { administrator: false },
    parameters: { overrides: { 'mail.reply': ['Alt+Shift+Q'], 'mail.delete': [] } }
};
