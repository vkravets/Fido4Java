<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { createState } from './state.ts';
import AuthScreen from './components/AuthScreen.vue';
import SetupWizard from './components/SetupWizard.vue';
import AccountSettings from './components/AccountSettings.vue';
import AccountModeration from './components/AccountModeration.vue';
import NodeSettings from './components/NodeSettings.vue';
import Sidebar from './components/Sidebar.vue';
import RobotAccess from './components/RobotAccess.vue';
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
        aria-label="Выполняется запрос"
    ></div>
    <header class="topbar">
        <a
            href="/"
            class="brand"
            aria-label="JFtn, главная"
            ><img
                class="brand-mark"
                src="/jftn.svg"
                alt=""
            />JFtn<span class="version">NODE / WEB</span></a
        >
        <div class="topbar-actions">
            <span
                v-if="state.profile.value"
                class="user-name"
                >{{ state.profile.value.login }}</span
            ><button
                class="icon-button"
                :aria-label="state.theme.value === 'dark' ? 'Светлая тема' : 'Тёмная тема'"
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
                Выйти
            </button>
        </div>
    </header>
    <div
        class="messages"
        aria-live="polite"
    >
        <p
            v-if="state.error.value"
            role="alert"
            class="error"
        >
            {{ state.error.value }}
        </p>
        <p
            v-if="state.notice.value"
            class="notice"
        >
            {{ state.notice.value }}
        </p>
    </div>
    <main
        v-if="!state.ready.value"
        class="loading"
    >
        <h1>JFtn</h1>
        <p>Подключаемся к станции…</p>
        <button
            v-if="state.error.value"
            class="secondary"
            @click="state.start"
        >
            Повторить
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
