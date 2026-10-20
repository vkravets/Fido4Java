<script setup lang="ts">
import AddressInput from './AddressInput.vue';
import { t } from '../i18n.ts';
import { computed, ref } from 'vue';
import { api } from '../api.ts';
import SecuritySettings from './SecuritySettings.vue';
import MailAppearanceSettings from './MailAppearanceSettings.vue';
import MailTemplateSettings from './MailTemplateSettings.vue';
import type { AppState } from '../state.ts';
import type { Theme } from '../state.ts';
const tabs = [
    { id: 'profile', label: 'Profile' },
    { id: 'aka', label: 'My AKAs' },
    { id: 'security', label: 'Security' },
    { id: 'theme', label: 'Appearance' },
    { id: 'mail-appearance', label: 'Mail appearance' },
    { id: 'templates', label: 'Mail templates' }
] as const;
type PersonalTab = (typeof tabs)[number]['id'];
const props = defineProps<{ state: AppState; initialTab?: PersonalTab }>();
const tab = ref<PersonalTab>(props.initialTab || 'profile');
const templateSettings = ref<InstanceType<typeof MailTemplateSettings> | null>(null);
function canLeave() {
    return !templateSettings.value || templateSettings.value.canLeave();
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
        <div class="page-heading">
            <span class="eyebrow">{{ t('MY ACCOUNT') }}</span>
            <h1>{{ t('Settings') }}</h1>
            <p>{{ t('Your account, addresses and security.') }}</p>
        </div>
        <nav
            class="tabs"
            :aria-label="t('Personal settings')"
        >
            <button
                v-for="item in tabs"
                :key="item.id"
                :class="{ selected: tab === item.id }"
                @click="selectTab(item.id)"
            >
                {{ t(item.label) }}
            </button>
        </nav>
        <MailTemplateSettings
            v-if="tab === 'templates'"
            ref="templateSettings"
            :state="state"
        />
        <MailAppearanceSettings
            v-if="tab === 'mail-appearance'"
            :state="state"
        />
        <section
            v-if="tab === 'profile'"
            class="card"
        >
            <h2>{{ t('Profile') }}</h2>
            <dl>
                <dt>{{ t('Username') }}</dt>
                <dd>{{ state.profile.value.login }}</dd>
                <dt>{{ t('Access') }}</dt>
                <dd>
                    {{ state.profile.value.role === 'ADMIN' ? t('Administrator') : t('Point') }}
                </dd>
                <dt>{{ t('Default AKA') }}</dt>
                <dd>{{ state.profile.value.defaultAka || t('Not selected') }}</dd>
            </dl>
            <p class="hint">{{ t('Each point address requires administrator approval.') }}</p>
        </section>
        <section
            v-if="tab === 'aka'"
            class="card"
        >
            <h2>{{ t('My addresses') }}</h2>
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
            <h2>{{ t('Appearance') }}</h2>
            <p>{{ t('Your selected theme is saved in your profile.') }}</p>
            <div class="theme-options">
                <button
                    v-for="theme in themes"
                    :key="theme"
                    :class="['theme-preview', theme, { chosen: state.theme.value === theme }]"
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
                    >{{ theme === 'light' ? t('Light · Day station') : t('Dark · Night shift') }}
                </button>
            </div>
        </section>
    </template>
</template>
