<script setup lang="ts">
import { ref } from 'vue';
import { api } from '../api.ts';
import SecuritySettings from './SecuritySettings.vue';
import type { AppState } from '../state.ts';
import type { Theme } from '../state.ts';
const props = defineProps<{ state: AppState }>();
const tab = ref('profile');
const requested = ref('');
function preferences(theme: Theme, aka: string) {
    props.state.run(async () => {
        await api.profile.savePreferences(props.state.session.value, theme, aka);
        await props.state.refreshProfile();
        props.state.notice.value = 'Настройки сохранены.';
    });
}
function requestAka() {
    props.state.run(async () => {
        await api.profile.requestAka(props.state.session.value, requested.value);
        requested.value = '';
        await props.state.refreshProfile();
        props.state.notice.value = 'Заявка на АКА отправлена.';
    });
}
const labels = { PENDING: 'На проверке', APPROVED: 'Подтверждён', REJECTED: 'Отклонён' };
const themes: Theme[] = ['light', 'dark'];
function claimLabel(state: string): string {
    return labels[state as keyof typeof labels] ?? state;
}
</script>

<template>
    <template v-if="state.profile.value">
        <div class="page-heading">
            <span class="eyebrow">ЛИЧНЫЙ КАБИНЕТ</span>
            <h1>Настройки</h1>
            <p>Ваш аккаунт, адреса и безопасность.</p>
        </div>
        <nav
            class="tabs"
            aria-label="Личные настройки"
        >
            <button
                v-for="item in [
                    ['profile', 'Профиль'],
                    ['aka', 'Мои АКА'],
                    ['security', 'Безопасность'],
                    ['theme', 'Внешний вид']
                ]"
                :key="item[0]"
                :class="{ selected: tab === item[0] }"
                @click="tab = item[0]"
            >
                {{ item[1] }}
            </button>
        </nav>
        <section
            v-if="tab === 'profile'"
            class="card"
        >
            <h2>Профиль</h2>
            <dl>
                <dt>Логин</dt>
                <dd>{{ state.profile.value.login }}</dd>
                <dt>Доступ</dt>
                <dd>{{ state.profile.value.role === 'ADMIN' ? 'Администратор' : 'Поинт' }}</dd>
                <dt>Основной АКА</dt>
                <dd>{{ state.profile.value.defaultAka || 'Не выбран' }}</dd>
            </dl>
            <p class="hint">Для каждого адреса поинта требуется одобрение администратора.</p>
        </section>
        <section
            v-if="tab === 'aka'"
            class="card"
        >
            <h2>Мои адреса</h2>
            <p>
                Выберите подтверждённый АКА по умолчанию. Выбор будет использоваться будущим
                почтовым ридером.
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
                    ><span class="badge">Доступен</span></label
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
                    >Добавить АКА поинта<input
                        v-model="requested"
                        required
                        placeholder="1:500/100.1" /></label
                ><button
                    class="secondary"
                    :disabled="state.busy.value"
                >
                    Отправить заявку
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
            <h2>Внешний вид</h2>
            <p>Выбранная тема сохраняется в вашем профиле.</p>
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
                    >{{ theme === 'light' ? 'Светлая · Дневная станция' : 'Тёмная · Ночная смена' }}
                </button>
            </div>
        </section>
    </template>
</template>
