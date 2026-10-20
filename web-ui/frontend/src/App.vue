<script setup lang="ts">
import { t } from './i18n.ts';
import { onMounted, onBeforeUnmount, ref, watch, provide } from 'vue';
import { api } from './api.ts';
import { createWorkspace, workspaceKey } from './workspace.ts';
import { createMailAppearance, mailAppearanceKey } from './mail-themes.ts';
import { createMailTemplates, mailTemplatesKey } from './mail-templates.ts';
import MailTemplateSettings from './components/MailTemplateSettings.vue';
import { createMailQueue } from './netmail.ts';
import { createState } from './state.ts';
import AuthScreen from './components/AuthScreen.vue';
import SetupWizard from './components/SetupWizard.vue';
import AccountSettings from './components/AccountSettings.vue';
import NodeSettings from './components/NodeSettings.vue';
import Sidebar from './components/Sidebar.vue';
import NodeDirectory from './components/NodeDirectory.vue';
import NotificationBanner from './components/NotificationBanner.vue';
import ApplicationHeader from './components/ApplicationHeader.vue';
import NetmailView from './components/NetmailView.vue';
import EchomailView from './components/EchomailView.vue';
import type { MailDraft } from '@rpc/bindings.mjs';
const state = createState();
const workspace = createWorkspace(api.profile);
provide(workspaceKey, workspace);
const mailAppearance = createMailAppearance(api.profile);
provide(mailAppearanceKey, mailAppearance);
onMounted(() => mailAppearance.files().catch(() => {}));
const queue = createMailQueue(state, api.ui);
const page = ref('settings');
const templatesView = ref<InstanceType<typeof MailTemplateSettings> | null>(null);
const templates = createMailTemplates(api.profile);
provide(mailTemplatesKey, templates);
const preferencesReady = ref(false);
watch([state.session, () => state.profile.value?.login], async ([token, owner]) => {
    if (token && !owner) return;
    preferencesReady.value = false;
    try {
        await Promise.all([
            workspace.load(token),
            mailAppearance.load(token),
            templates.load(token)
        ]);
    } catch {
        if (token === state.session.value)
            state.error.value = t('Could not load mail preferences.');
    } finally {
        if (token === state.session.value) preferencesReady.value = true;
    }
});
const mobileNavigation = ref(false);

const netmail = ref<InstanceType<typeof NetmailView> | null>(null);
const echomail = ref<InstanceType<typeof EchomailView> | null>(null);
const echoDraft = ref<MailDraft>();
const netmailDraft = ref<MailDraft>();
function canLeave() {
    return (
        (!netmail.value || netmail.value.canLeave()) &&
        (!echomail.value || echomail.value.canLeave()) &&
        (!templatesView.value || templatesView.value.canLeave())
    );
}
function navigate(next: string) {
    if (!canLeave()) return;
    echoDraft.value = undefined;
    netmailDraft.value = undefined;
    page.value = next;
    mobileNavigation.value = false;
    refreshQueue();
}
async function refreshQueue() {
    const token = state.session.value;
    try {
        await queue.refresh();
    } catch (failure) {
        if (token !== state.session.value) return;
        state.error.value =
            (failure as { message?: string }).message || t('The request failed. Please try again.');
    }
}
async function sendQueue() {
    await queue.send();
    await netmail.value?.refresh();
}
watch(state.session, refreshQueue);
onMounted(() => window.addEventListener('focus', refreshQueue));
onBeforeUnmount(() => window.removeEventListener('focus', refreshQueue));
onMounted(state.start);
function logout() {
    if (!canLeave()) return;
    page.value = 'settings';
    state.run(state.logout);
}
function openEchoDraft(draft: MailDraft) {
    if (!canLeave()) return;
    echoDraft.value = draft;
    page.value = 'echo';
}
function openForward(draft: MailDraft) {
    if (!canLeave()) return;
    netmailDraft.value = draft;
    page.value = 'netmail';
}
</script>

<template>
    <div
        v-if="state.busy.value"
        class="progress"
        role="status"
        :aria-label="t('Request in progress')"
    ></div>
    <ApplicationHeader
        :profile="state.profile.value"
        :theme="state.theme.value"
        :busy="state.busy.value"
        :menu-open="mobileNavigation"
        @menu="mobileNavigation = !mobileNavigation"
        @theme="state.run(state.toggleTheme)"
        @logout="logout"
    />
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
    <main
        v-else-if="!preferencesReady"
        class="loading"
    >
        <p>{{ t('Connecting to the station\u2026') }}</p>
    </main>
    <div
        v-else
        class="workspace"
        :class="{
            'navigation-collapsed': workspace.value.value.collapsed,
            'navigation-open': mobileNavigation
        }"
    >
        <Sidebar
            :profile="state.profile.value"
            :collapsed="workspace.value.value.collapsed"
            @collapse="
                state.run(() => workspace.update({ collapsed: !workspace.value.value.collapsed }))
            "
            :page="page"
            :pending-count="queue.count.value"
            :busy="state.busy.value"
            @navigate="navigate"
            @send-mail="state.run(sendQueue)"
        />
        <main
            class="content"
            :class="{ 'mail-content': ['netmail', 'echo', 'drafts'].includes(page) }"
        >
            <NetmailView
                v-if="page === 'netmail' || page === 'drafts'"
                :key="page"
                ref="netmail"
                :state="state"
                :initial-folder="page === 'drafts' ? 'drafts' : 'inbox'"
                :initial-draft="netmailDraft"
                @queue-changed="refreshQueue"
                @open-echo-draft="openEchoDraft"
            />
            <EchomailView
                v-if="page === 'echo'"
                ref="echomail"
                :state="state"
                :initial-draft="echoDraft"
                @forward="openForward"
            />
            <MailTemplateSettings
                v-if="page === 'templates'"
                ref="templatesView"
                :state="state"
            />
            <NodeDirectory v-if="page === 'directory'" />
            <AccountSettings
                v-if="page === 'settings'"
                :state="state"
            /><NodeSettings
                v-if="['node', 'links'].includes(page) && state.profile.value.role === 'ADMIN'"
                :key="page"
                :initial-section="page === 'links' ? 'links' : 'general'"
                :state="state"
            />
        </main>
    </div>
</template>
