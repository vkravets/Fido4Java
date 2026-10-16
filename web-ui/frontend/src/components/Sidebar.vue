<script setup lang="ts">
import type { UserProfile } from '@rpc/bindings.mjs';

defineProps<{ profile: UserProfile; page: string }>();
const emit = defineEmits<{ navigate: [page: 'settings' | 'node' | 'accounts' | 'robots'] }>();
</script>

<template>
    <aside class="sidebar">
        <div class="station-label">
            <span class="status-dot"></span>Личный кабинет
            <small>{{ profile.defaultAka || 'JFtn station' }}</small>
        </div>
        <nav aria-label="Основная навигация">
            <span class="nav-label">ПОЧТА</span>
            <button disabled><span>✉</span>Нетмейл<small>Скоро</small></button>
            <button disabled><span>◎</span>Эхоконференции<small>Скоро</small></button>
            <button disabled><span>▤</span>Черновики<small>Скоро</small></button>
            <span class="nav-label">АККАУНТ</span>
            <button
                :class="{ active: page === 'settings' }"
                @click="emit('navigate', 'settings')"
            >
                <span>⚙</span>Мои настройки
            </button>
            <template v-if="profile.role === 'ADMIN'">
                <span class="nav-label">УПРАВЛЕНИЕ НОДОЙ</span>
                <button
                    :class="{ active: page === 'node' }"
                    @click="emit('navigate', 'node')"
                >
                    <span>⌘</span>Станция
                </button>
                <button
                    :class="{ active: page === 'robots' }"
                    @click="emit('navigate', 'robots')"
                >
                    <span>⇄</span>Почтовые роботы
                </button>
                <button
                    :class="{ active: page === 'accounts' }"
                    @click="emit('navigate', 'accounts')"
                >
                    <span>♙</span>Аккаунты и заявки
                </button>
            </template>
        </nav>
        <div class="sidebar-footer">FidoNet · На связи<span>Ваш адрес. Ваш голос.</span></div>
    </aside>
</template>
