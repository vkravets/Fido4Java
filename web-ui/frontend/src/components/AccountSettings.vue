<script setup lang="ts">
import SettingsWorkspace from './SettingsWorkspace.vue';
import SettingsSectionHeader from './SettingsSectionHeader.vue';
import KeyboardSettings from './KeyboardSettings.vue';
import ProfileSettings from './ProfileSettings.vue';
import { useMedia } from '../media.ts';
import AddressInput from './AddressInput.vue';
import { t } from '../i18n.ts';
import { computed, ref } from 'vue';
import { api } from '../api.ts';
import SecuritySettings from './SecuritySettings.vue';
import MailAppearanceSettings from './MailAppearanceSettings.vue';
import MailTemplateSettings from './MailTemplateSettings.vue';
import type { AppState } from '../state.ts';
import type { Theme } from '../state.ts';
import { startupPages, useWorkspace } from '../workspace.ts';
import type { StartupPage } from '../workspace.ts';
const tabs = [
    { id: 'profile', label: 'Profile' },
    { id: 'aka', label: 'My AKAs' },
    { id: 'security', label: 'Security' },
    { id: 'theme', label: 'Appearance' },
    { id: 'mail-appearance', label: 'Mail appearance' },
    { id: 'templates', label: 'Mail templates' },
    { id: 'keyboard', label: 'Keyboard shortcuts' }
] as const;
type PersonalTab = (typeof tabs)[number]['id'];
const props = defineProps<{ state: AppState; initialTab?: PersonalTab }>();
const workspace = useWorkspace();
function saveStartupPage(event: Event) {
    const select = event.target as HTMLSelectElement;
    const startupPage = select.value as StartupPage;
    props.state.run(async () => {
        try {
            await workspace.update({ startupPage });
            props.state.notice.value = t('Settings saved.');
        } finally {
            select.value = workspace.value.value.startupPage;
        }
    });
}
const tab = ref<PersonalTab>(props.initialTab || 'profile');
const narrow = useMedia('(max-width: 700px)');
const title = computed(() => t(tabs.find((item) => item.id === tab.value)!.label));
function navigate(event: KeyboardEvent) {
    const index = tabs.findIndex((item) => item.id === tab.value);
    const delta = ['ArrowRight', 'ArrowDown'].includes(event.key)
        ? 1
        : ['ArrowLeft', 'ArrowUp'].includes(event.key)
          ? -1
          : 0;
    if (!delta && !['Home', 'End'].includes(event.key)) return;
    event.preventDefault();
    const next =
        event.key === 'Home'
            ? 0
            : event.key === 'End'
              ? tabs.length - 1
              : (index + delta + tabs.length) % tabs.length;
    selectTab(tabs[next]!.id);
    if (tab.value === tabs[next]!.id)
        (event.currentTarget as HTMLElement)
            .querySelectorAll<HTMLButtonElement>('[role=tab]')
            [next]?.focus();
}
const templateSettings = ref<InstanceType<typeof MailTemplateSettings> | null>(null);
const keyboardSettings = ref<InstanceType<typeof KeyboardSettings> | null>(null);
const profileSettings = ref<InstanceType<typeof ProfileSettings> | null>(null);
function canLeave() {
    return (
        (!profileSettings.value || profileSettings.value.canLeave()) &&
        (!templateSettings.value || templateSettings.value.canLeave()) &&
        (!keyboardSettings.value || keyboardSettings.value.canLeave())
    );
}
function selectTab(next: PersonalTab) {
    if (next === tab.value || !canLeave()) return;
    tab.value = next;
}
defineExpose({ canLeave });
const requested = ref('');
function preferences(theme: Theme, aka: string) {
    props.state.run(async () => {
        await api.profile.savePreferences(props.state.session.value, theme, aka);
        await props.state.refreshProfile();
        props.state.notice.value = t('Settings saved.');
    });
}
function requestAka() {
    props.state.run(async () => {
        await api.profile.requestAka(props.state.session.value, requested.value);
        requested.value = '';
        await props.state.refreshProfile();
        props.state.notice.value = t('AKA request submitted.');
    });
}
const labels = computed(() => ({
    PENDING: t('Pending review'),
    APPROVED: t('Approved'),
    REJECTED: t('Rejected')
}));
const themes: Theme[] = ['light', 'dark'];
function claimLabel(state: string): string {
    return labels.value[state as keyof typeof labels.value] ?? state;
}
</script>

<template>
    <template v-if="state.profile.value">
        <SettingsWorkspace
            :context="t('Personal settings')"
            :title="title"
        >
            <template #navigation>
                <nav
                    class="tabs personal-tabs"
                    role="tablist"
                    :aria-orientation="narrow ? 'horizontal' : 'vertical'"
                    :aria-label="t('Personal settings')"
                    @keydown="navigate"
                >
                    <button
                        v-for="item in tabs"
                        :key="item.id"
                        :id="`personal-tab-${item.id}`"
                        role="tab"
                        :aria-selected="tab === item.id"
                        :tabindex="tab === item.id ? 0 : -1"
                        aria-controls="personal-settings-panel"
                        :class="{ selected: tab === item.id }"
                        @click="selectTab(item.id)"
                    >
                        {{ t(item.label) }}
                    </button>
                </nav>
            </template>
            <div
                id="personal-settings-panel"
                role="tabpanel"
                :aria-labelledby="`personal-tab-${tab}`"
                tabindex="0"
            >
                <KeyboardSettings
                    v-if="tab === 'keyboard'"
                    ref="keyboardSettings"
                    :state="state"
                />
                <MailTemplateSettings
                    v-if="tab === 'templates'"
                    ref="templateSettings"
                    :state="state"
                />
                <MailAppearanceSettings
                    v-if="tab === 'mail-appearance'"
                    :state="state"
                />
                <ProfileSettings
                    v-if="tab === 'profile'"
                    ref="profileSettings"
                    :state="state"
                />
                <section
                    v-if="tab === 'aka'"
                    class="card"
                >
                    <SettingsSectionHeader :title="t('My addresses')" />
                    <p>
                        {{ t('Choose an approved default AKA for the future mail reader.') }}
                    </p>
                    <div class="aka-list">
                        <label
                            v-for="aka in state.profile.value.allowedAkas"
                            :key="aka"
                            class="choice"
                            ><input
                                type="radio"
                                name="aka"
                                :checked="state.profile.value.defaultAka === aka"
                                :disabled="state.busy.value"
                                @change="preferences(state.theme.value, aka)"
                            /><span>{{ aka }}</span
                            ><span class="badge">{{ t('Available') }}</span></label
                        >
                    </div>
                    <ul class="rows">
                        <li
                            v-for="claim in state.profile.value.claims"
                            :key="claim.address"
                        >
                            <span>{{ claim.address }}</span
                            ><span
                                class="badge"
                                :class="claim.state.toLowerCase()"
                                >{{ claimLabel(claim.state) }}</span
                            >
                        </li>
                    </ul>
                    <form
                        class="inline-form"
                        @submit.prevent="requestAka"
                    >
                        <label
                            >{{ t('Add a point AKA')
                            }}<AddressInput
                                v-model="requested"
                                :aria-label="t('Add a point AKA')"
                                required
                                placeholder="1:500/100.1" /></label
                        ><button
                            class="secondary"
                            :disabled="state.busy.value"
                        >
                            {{ t('Submit request') }}
                        </button>
                    </form>
                </section>
                <SecuritySettings
                    v-if="tab === 'security'"
                    :state="state"
                />
                <section
                    v-if="tab === 'theme'"
                    class="card"
                >
                    <SettingsSectionHeader :title="t('Appearance')" />
                    <label class="startup-page">
                        {{ t('Page after sign-in') }}
                        <select
                            :value="workspace.value.value.startupPage"
                            :disabled="state.busy.value"
                            @change="saveStartupPage"
                        >
                            <option
                                v-for="item in startupPages"
                                :key="item.id"
                                :value="item.id"
                            >
                                {{ t(item.label) }}
                            </option>
                        </select>
                        <small>{{
                            t('Also used when reloading the page. Saved automatically.')
                        }}</small>
                    </label>
                    <p>{{ t('Your selected theme is saved in your profile.') }}</p>
                    <div class="theme-options">
                        <button
                            v-for="theme in themes"
                            :key="theme"
                            :class="[
                                'theme-preview',
                                theme,
                                { chosen: state.theme.value === theme }
                            ]"
                            :aria-pressed="state.theme.value === theme"
                            :disabled="state.busy.value"
                            @click="preferences(theme, state.profile.value.defaultAka)"
                        >
                            <span
                                class="preview-window"
                                aria-hidden="true"
                            >
                                <span class="preview-header"></span>
                                <span class="preview-sidebar"></span>
                                <span class="preview-sections"><i></i><i></i></span> </span
                            >{{
                                theme === 'light'
                                    ? t('Light · Day station')
                                    : t('Dark · Night shift')
                            }}
                        </button>
                    </div>
                </section>
            </div>
        </SettingsWorkspace>
    </template>
</template>

<style scoped>
.startup-page {
    display: grid;
    gap: 0.5rem;
    max-width: 26rem;
    margin-bottom: 1.25rem;
}
</style>
