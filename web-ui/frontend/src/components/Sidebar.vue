<script setup lang="ts">
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
    navigate: [
        page:
            | 'settings'
            | 'node'
            | 'accounts'
            | 'robots'
            | 'connections'
            | 'directory'
            | 'netmail'
            | 'echo'
            | 'drafts'
            | 'routing'
            | 'templates'
    ];
}>();
</script>

<template>
    <aside
        class="sidebar"
        :class="{ collapsed }"
    >
        <button
            class="sidebar-toggle secondary"
            :aria-label="collapsed ? t('Expand menu') : t('Collapse menu')"
            :aria-expanded="!collapsed"
            :disabled="busy"
            @click="emit('collapse')"
        >
            {{ collapsed ? '\u00bb' : '\u00ab'
            }}<span class="nav-text">{{ t('Collapse menu') }}</span>
        </button>
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
                <span aria-hidden="true">✉</span><span class="nav-text">{{ t('Netmail') }}</span>
            </button>
            <button
                :class="{ active: page === 'echo' }"
                :aria-label="t('Echomail')"
                :title="collapsed ? t('Echomail') : undefined"
                @click="emit('navigate', 'echo')"
            >
                <span aria-hidden="true">◎</span><span class="nav-text">{{ t('Echomail') }}</span>
            </button>
            <button
                :class="{ active: page === 'drafts' }"
                :aria-label="t('Drafts')"
                :title="collapsed ? t('Drafts') : undefined"
                @click="emit('navigate', 'drafts')"
            >
                <span aria-hidden="true">▤</span><span class="nav-text">{{ t('Drafts') }}</span>
            </button>
            <button
                :class="{ active: page === 'templates' }"
                :aria-label="t('Mail templates')"
                :title="collapsed ? t('Mail templates') : undefined"
                @click="emit('navigate', 'templates')"
            >
                <span aria-hidden="true">&#9638;</span
                ><span class="nav-text">{{ t('Mail templates') }}</span>
            </button>
            <button
                v-if="collapsed"
                class="secondary"
                :disabled="busy || !pendingCount"
                :aria-label="t('Send queued mail')"
                :title="t('Send queued mail')"
                @click="emit('sendMail')"
            >
                &#8679;<small>{{ pendingCount || 0 }}</small>
            </button>
            <MailQueueControl
                v-else
                :count="pendingCount || 0"
                :busy="busy || false"
                @send="emit('sendMail')"
            />
            <span class="nav-label">{{ t('DIRECTORY') }}</span>
            <button
                :class="{ active: page === 'directory' }"
                :aria-label="t('Node directory')"
                :title="collapsed ? t('Node directory') : undefined"
                @click="emit('navigate', 'directory')"
            >
                <span aria-hidden="true">⌕</span
                ><span class="nav-text">{{ t('Node directory') }}</span>
            </button>
            <span class="nav-label">{{ t('ACCOUNT') }}</span>
            <button
                :class="{ active: page === 'settings' }"
                :aria-label="t('My settings')"
                :title="collapsed ? t('My settings') : undefined"
                @click="emit('navigate', 'settings')"
            >
                <span aria-hidden="true">⚙</span
                ><span class="nav-text">{{ t('My settings') }}</span>
            </button>
            <template v-if="profile.role === 'ADMIN'">
                <span class="nav-label">{{ t('NODE MANAGEMENT') }}</span>
                <button
                    :class="{ active: page === 'routing' }"
                    :aria-label="t('Netmail routing')"
                    :title="collapsed ? t('Netmail routing') : undefined"
                    @click="emit('navigate', 'routing')"
                >
                    <span aria-hidden="true">⇢</span
                    ><span class="nav-text">{{ t('Netmail routing') }}</span>
                </button>
                <button
                    :class="{ active: page === 'node' }"
                    :aria-label="t('Station')"
                    :title="collapsed ? t('Station') : undefined"
                    @click="emit('navigate', 'node')"
                >
                    <span aria-hidden="true">⌘</span
                    ><span class="nav-text">{{ t('Station') }}</span>
                </button>
                <button
                    :class="{ active: page === 'connections' }"
                    :aria-label="t('Connections')"
                    :title="collapsed ? t('Connections') : undefined"
                    @click="emit('navigate', 'connections')"
                >
                    <span aria-hidden="true">↔</span
                    ><span class="nav-text">{{ t('Connections') }}</span>
                </button>
                <button
                    :class="{ active: page === 'robots' }"
                    :aria-label="t('Mail robots')"
                    :title="collapsed ? t('Mail robots') : undefined"
                    @click="emit('navigate', 'robots')"
                >
                    <span aria-hidden="true">⇄</span
                    ><span class="nav-text">{{ t('Mail robots') }}</span>
                </button>
                <button
                    :class="{ active: page === 'accounts' }"
                    :aria-label="t('Accounts and requests')"
                    :title="collapsed ? t('Accounts and requests') : undefined"
                    @click="emit('navigate', 'accounts')"
                >
                    <span aria-hidden="true">♙</span
                    ><span class="nav-text">{{ t('Accounts and requests') }}</span>
                </button>
            </template>
        </nav>
        <div class="sidebar-footer">
            {{ t('FidoNet · Connected') }}<span>{{ t('Your address. Your voice.') }}</span>
        </div>
    </aside>
</template>
