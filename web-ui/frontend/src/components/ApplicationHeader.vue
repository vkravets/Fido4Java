<script setup lang="ts">
import { t } from '../i18n.ts';
import type { UserProfile } from '@rpc/bindings.mjs';
import LanguageSelector from './LanguageSelector.vue';
defineProps<{
    profile: UserProfile | null;
    theme: 'light' | 'dark';
    busy: boolean;
    menuOpen: boolean;
}>();
defineEmits<{ menu: []; theme: []; logout: [] }>();
</script>
<template>
    <header class="topbar">
        <button
            v-if="profile"
            class="mobile-menu-toggle secondary"
            :aria-label="t('Open menu')"
            :aria-expanded="menuOpen"
            @click="$emit('menu')"
        >
            &#9776;
        </button>
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
                v-if="profile"
                class="user-name"
                >{{ profile.login }}</span
            ><button
                class="icon-button"
                :aria-label="theme === 'dark' ? t('Light theme') : t('Dark theme')"
                :disabled="busy"
                @click="$emit('theme')"
            >
                {{ theme === 'dark' ? '☀' : '☾' }}</button
            ><button
                v-if="profile"
                class="text-button"
                :aria-label="t('Sign out')"
                :disabled="busy"
                @click="$emit('logout')"
            >
                {{ t('Sign out') }}
            </button>
        </div>
    </header>
</template>
