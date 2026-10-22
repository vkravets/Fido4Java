import type { Meta, StoryObj } from '@storybook/vue3-vite';
import { provide, ref } from 'vue';
import { passkeyListKey } from '../src/security-client.ts';
import AccountSettings from '../src/components/AccountSettings.vue';
import NotificationBanner from '../src/components/NotificationBanner.vue';
import type { AppState } from '../src/state.ts';
import { mailWorkspaceFixture } from './mail-workspace-fixture.ts';
import { profile } from './fixtures.ts';
import { directorySearchKey } from '../src/directory.ts';
import { saveDisplayNameKey } from '../src/profile-name.ts';

const meta = {
    title: 'Settings/AccountSettings',
    component: AccountSettings,
    args: { state: {} as AppState, initialTab: 'templates' },
    argTypes: { state: { control: false } },
    render: (args, context) => ({
        components: { AccountSettings, NotificationBanner },
        setup() {
            provide(passkeyListKey, async () => []);
            const { state } = mailWorkspaceFixture(context.globals.theme === 'dark');
            state.methods = { password: true, passkey: false };
            state.theme = ref(context.globals.theme === 'dark' ? 'dark' : 'light');
            state.profile.value = profile(context.parameters.role === 'ADMIN' ? 'ADMIN' : 'POINT');
            state.profile.value.displayName =
                context.parameters.displayName || state.profile.value.login;
            let savedName = state.profile.value.displayName;
            provide(saveDisplayNameKey, async (_session, name) => {
                savedName = name.trim() || state.profile.value!.login;
            });
            state.refreshProfile = async () => {
                state.profile.value = { ...state.profile.value!, displayName: savedName };
            };
            provide(directorySearchKey, async () => ({
                items: context.parameters.directoryNames
                    ? [
                          {
                              address: '1:500/100.1',
                              sysop: 'Alex Example',
                              source: 'points',
                              kind: 'POINT',
                              system: 'Example station',
                              location: 'Kyiv',
                              flags: [],
                              phone: '',
                              endpoints: []
                          },
                          {
                              address: '1:500/100.1',
                              sysop: 'Oleksii Example',
                              source: 'points-r46',
                              kind: 'POINT',
                              system: 'Example station',
                              location: 'Kyiv',
                              flags: [],
                              phone: '',
                              endpoints: []
                          },
                          {
                              address: '1:500/100.10',
                              sysop: 'Another Point',
                              source: 'points',
                              kind: 'POINT',
                              system: 'Other station',
                              location: 'Kyiv',
                              flags: [],
                              phone: '',
                              endpoints: []
                          }
                      ]
                    : [],
                nextOffset: 0,
                hasMore: false
            }));
            return { args, state };
        },
        template:
            '<NotificationBanner :message="state.error.value" kind="error" /><AccountSettings v-bind="args" :state="state" />'
    })
} satisfies Meta<typeof AccountSettings>;
export default meta;
type Story = StoryObj<typeof meta>;
export const PointTemplates: Story = {};
export const AdministratorTemplates: Story = { parameters: { role: 'ADMIN' } };
export const Profile: Story = { args: { initialTab: 'profile' } };
export const ProfileDirectory: Story = {
    ...Profile,
    parameters: { directoryNames: true, displayName: 'Alex Example' }
};
export const Addresses: Story = { args: { initialTab: 'aka' } };
export const Security: Story = { args: { initialTab: 'security' } };
export const Appearance: Story = { args: { initialTab: 'theme' } };
export const MailAppearance: Story = { args: { initialTab: 'mail-appearance' } };
export const Keyboard: Story = { args: { initialTab: 'keyboard' } };
export const GoldedKeyboard: Story = {
    args: { initialTab: 'keyboard' },
    parameters: { shortcutsProfile: 'golded' }
};
