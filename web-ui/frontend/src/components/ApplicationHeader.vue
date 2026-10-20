<script setup lang="ts">
import AppIcon from './AppIcon.vue';
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
            ><svg
                class="desktop-brand-mark"
                viewBox="0 0 32 32"
                aria-hidden="true"
            >
                <path
                    d="M7 25L18 6L25 25"
                    fill="none"
                    stroke="currentColor"
                    stroke-width="1.5"
                />
                <circle
                    cx="7"
                    cy="25"
                    r="4"
                    fill="currentColor"
                />
                <circle
                    cx="18"
                    cy="6"
                    r="4"
                    fill="currentColor"
                />
                <circle
                    cx="25"
                    cy="25"
                    r="4"
                    fill="currentColor"
                /></svg
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
                <AppIcon :name="theme === 'dark' ? 'sun' : 'moon'" /></button
            ><button
                v-if="profile"
                class="text-button"
                :aria-label="t('Sign out')"
                :disabled="busy"
                @click="$emit('logout')"
            >
                <AppIcon name="logout" /><span>{{ t('Sign out') }}</span>
            </button>
        </div>
    </header>
</template>
