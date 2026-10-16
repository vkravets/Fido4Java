<script setup lang="ts">
import { t } from './i18n.ts';
import { onMounted, ref } from 'vue';
import { createState } from './state.ts';
import AuthScreen from './components/AuthScreen.vue';
import SetupWizard from './components/SetupWizard.vue';
import AccountSettings from './components/AccountSettings.vue';
import AccountModeration from './components/AccountModeration.vue';
import NodeSettings from './components/NodeSettings.vue';
import Sidebar from './components/Sidebar.vue';
import RobotAccess from './components/RobotAccess.vue';
import Connections from './components/Connections.vue';
import NotificationBanner from './components/NotificationBanner.vue';
import LanguageSelector from './components/LanguageSelector.vue';
const state = createState();
const page = ref('settings');
onMounted(state.start);
function logout() {
    page.value = 'settings';
    state.run(state.logout);
}
</script>

<template>
    <div
        v-if="state.busy.value"
        class="progress"
        role="status"
        :aria-label="t('Request in progress')"
    ></div>
    <header class="topbar">
        <a
            href="/"
            class="brand"
            :aria-label="t('JFtn, home')"
            ><img
                class="brand-mark"
                src="/jftn.svg"
                alt=""
            />JFtn<span class="version">NODE / WEB</span></a
        >
        <div class="topbar-actions">
            <LanguageSelector />
            <span
                v-if="state.profile.value"
                class="user-name"
                >{{ state.profile.value.login }}</span
            ><button
                class="icon-button"
                :aria-label="state.theme.value === 'dark' ? t('Light theme') : t('Dark theme')"
                :disabled="state.busy.value"
                @click="state.run(state.toggleTheme)"
            >
                {{ state.theme.value === 'dark' ? '☀' : '☾' }}</button
            ><button
                v-if="state.profile.value"
                class="text-button"
                :disabled="state.busy.value"
                @click="logout"
            >
                {{ t('Sign out') }}
            </button>
        </div>
    </header>
    <Teleport to="body">
        <section
            id="notifications"
            class="messages"
            :aria-label="t('Notifications')"
        ></section>
    </Teleport>
    <NotificationBanner
        :message="state.error.value"
        kind="error"
    />
    <NotificationBanner :message="state.notice.value" />
    <main
        v-if="!state.ready.value"
        class="loading"
    >
        <h1>JFtn</h1>
        <p>{{ t('Connecting to the station…') }}</p>
        <button
            v-if="state.error.value"
            class="secondary"
            @click="state.start"
        >
            {{ t('Retry') }}
        </button>
    </main>
    <SetupWizard
        v-else-if="state.setup.value"
        :state="state"
    />
    <AuthScreen
        v-else-if="!state.profile.value"
        :state="state"
    />
    <div
        v-else
        class="workspace"
    >
        <Sidebar
            :profile="state.profile.value"
            :page="page"
            @navigate="page = $event"
        />
        <main class="content">
            <AccountSettings
                v-if="page === 'settings'"
                :state="state"
            /><NodeSettings
                v-if="page === 'node' && state.profile.value.role === 'ADMIN'"
                :state="state"
            /><Connections
                v-if="page === 'connections' && state.profile.value.role === 'ADMIN'"
                :state="state"
            /><RobotAccess
                v-if="page === 'robots' && state.profile.value.role === 'ADMIN'"
                :state="state"
            /><AccountModeration
                v-if="page === 'accounts' && state.profile.value.role === 'ADMIN'"
                :state="state"
            />
        </main>
    </div>
</template>
