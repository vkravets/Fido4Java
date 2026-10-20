import type { Meta, StoryObj } from '@storybook/vue3-vite';
import { provide, ref } from 'vue';
import MailApplicationFrame from './MailApplicationFrame.vue';
import MailWorkspace from '../src/components/MailWorkspace.vue';
import EchoMessageList from '../src/components/EchoMessageList.vue';
import NetmailList from '../src/components/NetmailList.vue';
import { createWorkspace, workspaceKey, workspacePreferences } from '../src/workspace.ts';
import { mailId } from '../src/netmail.ts';
import { t } from '../src/i18n.ts';
import { echoMessages } from './echo-fixture.ts';
import { netmailMessage } from './netmail-fixture.ts';
import type { MailItem } from '@rpc/bindings.mjs';

const meta = {
    title: 'Layout/MailListWorkspace',
    component: MailWorkspace,
    args: { kind: 'echo', opened: false },
    render: (args, context) => ({
        components: { MailApplicationFrame, MailWorkspace, EchoMessageList, NetmailList },
        setup() {
            const workspace = createWorkspace();
            workspace.value.value = workspacePreferences({
                echo: {
                    ratio: 50,
                    density: context.parameters.compact ? 'compact' : 'comfortable'
                },
                netmail: { ratio: 50 }
            });
            provide(workspaceKey, workspace);
            const netmail = args.kind === 'netmail';
            const items = netmail
                ? [
                      netmailMessage,
                      { ...netmailMessage, id: '2', read: true },
                      { ...netmailMessage, id: '3', fromName: 'Carol Example' }
                  ]
                : echoMessages;
            const selected = ref(mailId(items[1]!.id));
            return {
                args,
                items,
                selected,
                netmail,
                t,
                mode: context.parameters.flat ? 'flat' : 'tree',
                theme: context.globals.theme === 'dark' ? 'dark' : 'light',
                select: (item: MailItem) => {
                    selected.value = mailId(item.id);
                }
            };
        },
        template: `<MailApplicationFrame :theme="theme" :page="netmail ? 'netmail' : 'echo'" :initial-collapsed="true">
            <MailWorkspace v-bind="args">
                <template #toolbar><h1>{{ t(netmail ? 'Netmail' : 'Echomail') }}</h1></template>
                <template #list>
                    <NetmailList v-if="netmail" :items="items" :selected="selected" :busy="false" :has-more="false" @open="select" />
                    <EchoMessageList v-else :items="items" :selected="selected" :initial-mode="mode" :busy="false" :has-more="false" @open="select" />
                </template>
                <section class="card muted mail-reader-empty"><p>{{ t('Select a message to read.') }}</p></section>
            </MailWorkspace>
        </MailApplicationFrame>`
    })
} satisfies Meta<typeof MailWorkspace>;
export default meta;
type Story = StoryObj<typeof meta>;
export const EchoTree: Story = {};
export const EchoFlat: Story = { parameters: { flat: true } };
export const EchoCompact: Story = { parameters: { compact: true } };
export const Netmail: Story = { args: { kind: 'netmail' } };
