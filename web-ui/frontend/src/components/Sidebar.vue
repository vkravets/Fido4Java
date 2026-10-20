<script setup lang="ts">
import AppIcon from './AppIcon.vue';
import { t } from '../i18n.ts';
import type { UserProfile } from '@rpc/bindings.mjs';
import MailQueueControl from './MailQueueControl.vue';

defineProps<{
    profile: UserProfile;
    page: string;
    pendingCount?: number;
    busy?: boolean;
    collapsed?: boolean;
}>();
const emit = defineEmits<{
    sendMail: [];
    collapse: [];
    navigate: [page: 'settings' | 'node' | 'directory' | 'netmail' | 'echo' | 'drafts' | 'links'];
}>();
</script>

<template>
    <aside
        class="sidebar"
        :class="{ collapsed }"
    >
        <div class="station-label">
            <span class="status-dot"></span>{{ t('My account') }}
            <small>{{ profile.defaultAka || 'JFtn station' }}</small>
        </div>
        <nav :aria-label="t('Main navigation')">
            <span class="nav-label">{{ t('MAIL') }}</span>
            <button
                :class="{ active: page === 'netmail' }"
                :aria-label="t('Netmail')"
                :title="collapsed ? t('Netmail') : undefined"
                @click="emit('navigate', 'netmail')"
            >
                <AppIcon name="mail" /><span class="nav-text">{{ t('Netmail') }}</span>
            </button>
            <button
                :class="{ active: page === 'echo' }"
                :aria-label="t('Echomail')"
                :title="collapsed ? t('Echomail') : undefined"
                @click="emit('navigate', 'echo')"
            >
                <AppIcon name="echo" /><span class="nav-text">{{ t('Echomail') }}</span>
            </button>
            <button
                :class="{ active: page === 'drafts' }"
                :aria-label="t('Drafts')"
                :title="collapsed ? t('Drafts') : undefined"
                @click="emit('navigate', 'drafts')"
            >
                <AppIcon name="file" /><span class="nav-text">{{ t('Drafts') }}</span>
            </button>
            <button
                v-if="collapsed"
                class="secondary"
                :disabled="busy || !pendingCount"
                :aria-label="t('Send queued mail')"
                :title="t('Send queued mail')"
                @click="emit('sendMail')"
            >
                <AppIcon name="send" /><small>{{ pendingCount || 0 }}</small>
            </button>
            <MailQueueControl
                v-else
                :count="pendingCount || 0"
                :busy="busy || false"
                @send="emit('sendMail')"
            />
            <span class="nav-label">{{ t('Settings') }}</span>
            <button
                :class="{ active: page === 'directory' }"
                :aria-label="t('Node directory')"
                :title="collapsed ? t('Node directory') : undefined"
                @click="emit('navigate', 'directory')"
            >
                <AppIcon name="directory" />
                <span class="nav-text">{{ t('Node directory') }}</span>
            </button>

            <button
                :class="{ active: page === 'settings' }"
                :aria-label="t('My settings')"
                :title="collapsed ? t('My settings') : undefined"
                @click="emit('navigate', 'settings')"
            >
                <AppIcon name="settings" />
                <span class="nav-text">{{ t('My settings') }}</span>
            </button>
            <template v-if="profile.role === 'ADMIN'">
                <button
                    :class="{ active: page === 'node' }"
                    :aria-label="t('Station')"
                    :title="collapsed ? t('Station') : undefined"
                    @click="emit('navigate', 'node')"
                >
                    <AppIcon name="settings" />
                    <span class="nav-text">{{ t('Station') }}</span>
                </button>
                <button
                    :class="{ active: page === 'links' }"
                    :aria-label="t('Mail links')"
                    :title="collapsed ? t('Mail links') : undefined"
                    @click="emit('navigate', 'links')"
                >
                    <AppIcon name="link" /><span class="nav-text">{{ t('Links') }}</span>
                </button>
            </template>
        </nav>
        <div class="sidebar-footer">
            <AppIcon name="user" /><span
                >{{ profile.login }}<small>{{ profile.defaultAka }}</small></span
            >
        </div>
        <button
            class="sidebar-toggle secondary"
            :aria-label="collapsed ? t('Expand menu') : t('Collapse menu')"
            :aria-expanded="!collapsed"
            :disabled="busy"
            @click="emit('collapse')"
        >
            <AppIcon :name="collapsed ? 'expand' : 'collapse'" /><span class="nav-text">{{
                t('Collapse menu')
            }}</span>
        </button>
    </aside>
</template>
