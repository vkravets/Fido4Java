<script setup lang="ts">
import { t } from '../i18n.ts';
import type { UserProfile } from '@rpc/bindings.mjs';

defineProps<{ profile: UserProfile; page: string }>();
const emit = defineEmits<{
    navigate: [
        page:
            | 'settings'
            | 'node'
            | 'accounts'
            | 'robots'
            | 'connections'
            | 'directory'
            | 'netmail'
            | 'drafts'
    ];
}>();
</script>

<template>
    <aside class="sidebar">
        <div class="station-label">
            <span class="status-dot"></span>{{ t('My account') }}
            <small>{{ profile.defaultAka || 'JFtn station' }}</small>
        </div>
        <nav :aria-label="t('Main navigation')">
            <span class="nav-label">{{ t('MAIL') }}</span>
            <button
                :class="{ active: page === 'netmail' }"
                @click="emit('navigate', 'netmail')"
            >
                <span>✉</span>{{ t('Netmail') }}
            </button>
            <button disabled>
                <span>◎</span>{{ t('Echomail') }}<small>{{ t('Coming soon') }}</small>
            </button>
            <button
                :class="{ active: page === 'drafts' }"
                @click="emit('navigate', 'drafts')"
            >
                <span>▤</span>{{ t('Drafts') }}
            </button>
            <span class="nav-label">{{ t('DIRECTORY') }}</span>
            <button
                :class="{ active: page === 'directory' }"
                @click="emit('navigate', 'directory')"
            >
                <span>⌕</span>{{ t('Node directory') }}
            </button>
            <span class="nav-label">{{ t('ACCOUNT') }}</span>
            <button
                :class="{ active: page === 'settings' }"
                @click="emit('navigate', 'settings')"
            >
                <span>⚙</span>{{ t('My settings') }}
            </button>
            <template v-if="profile.role === 'ADMIN'">
                <span class="nav-label">{{ t('NODE MANAGEMENT') }}</span>
                <button
                    :class="{ active: page === 'node' }"
                    @click="emit('navigate', 'node')"
                >
                    <span>⌘</span>{{ t('Station') }}
                </button>
                <button
                    :class="{ active: page === 'connections' }"
                    @click="emit('navigate', 'connections')"
                >
                    <span>↔</span>{{ t('Connections') }}
                </button>
                <button
                    :class="{ active: page === 'robots' }"
                    @click="emit('navigate', 'robots')"
                >
                    <span>⇄</span>{{ t('Mail robots') }}
                </button>
                <button
                    :class="{ active: page === 'accounts' }"
                    @click="emit('navigate', 'accounts')"
                >
                    <span>♙</span>{{ t('Accounts and requests') }}
                </button>
            </template>
        </nav>
        <div class="sidebar-footer">
            {{ t('FidoNet · Connected') }}<span>{{ t('Your address. Your voice.') }}</span>
        </div>
    </aside>
</template>
