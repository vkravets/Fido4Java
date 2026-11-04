import type { Meta, StoryObj } from '@storybook/vue3-vite';
import MailKludgeList from '../src/components/MailKludgeList.vue';

const meta = {
    title: 'Mail/MailKludgeList',
    component: MailKludgeList,
    args: {
        label: 'Kludges',
        kludges: ['\u0001MSGID: 1:500/100 abc', '\u0001CHRS: UTF-8 4', '\u0001CHRS: CP866 2']
    }
} satisfies Meta<typeof MailKludgeList>;
export default meta;
type Story = StoryObj<typeof meta>;

export const WithLines: Story = {};
export const Empty: Story = { args: { kludges: [] } };

export const ReaderAndComposerPlacement: Story = {
    render: () => ({
        components: { MailKludgeList },
        setup() {
            return {
                kludges: meta.args.kludges,
                body: 'Hello from the message body.',
                editor: 'Editable reply text'
            };
        },
        template: `
            <article class="card mail-message">
                <MailKludgeList :kludges="kludges" label="Kludges" />
                <p>{{ body }}</p>
            </article>
            <section class="card mail-composer">
                <MailKludgeList
                    :kludges="kludges"
                    label="Original message kludges"
                />
                <textarea :value="editor" aria-label="Message editor" />
            </section>
        `
    })
};
