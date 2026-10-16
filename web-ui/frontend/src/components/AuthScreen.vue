<script setup lang="ts">
import { ref } from 'vue';
import { api } from '../api.ts';
import type { AppState } from '../state.ts';
const props = defineProps<{ state: AppState }>();
const mode = ref('login');
const login = ref('');
const password = ref('');
const confirmation = ref('');
const address = ref('');
const pending = ref(false);

function submit() {
    props.state.run(async () => {
        if (mode.value === 'login') {
            await props.state.login(login.value, password.value);
            password.value = '';
            return;
        }
        if (password.value !== confirmation.value) throw new Error('Пароли не совпадают.');
        await api.profile.register(login.value, password.value, address.value);
        password.value = confirmation.value = '';
        pending.value = true;
    });
}
</script>

<template>
    <main class="auth-layout">
        <section class="intro">
            <span class="eyebrow">FIDONET · СВЯЗЬ БЕЗ ГРАНИЦ</span>
            <h1>Ваша почта.<br />Ваша станция.</h1>
            <p>Один интерфейс для личной почты, эхоконференций и вашей ноды.</p>
            <div
                class="network-art"
                aria-hidden="true"
            >
                <i></i><i></i><i></i><i></i><b>JFtn</b>
            </div>
            <small>Netmail и эхоконференции появятся на следующем этапе.</small>
        </section>
        <section class="card auth-card">
            <template v-if="pending">
                <span class="badge">Заявка отправлена</span>
                <h2>Осталось подтверждение</h2>
                <p>
                    Администратор проверит аккаунт и адрес {{ address }}. До одобрения вход закрыт.
                </p>
                <button
                    @click="
                        pending = false;
                        mode = 'login';
                    "
                >
                    Вернуться ко входу
                </button>
            </template>
            <template v-else>
                <span class="eyebrow">ДОБРО ПОЖАЛОВАТЬ</span>
                <h2>{{ mode === 'login' ? 'Вход в JFtn' : 'Создать аккаунт' }}</h2>
                <p>
                    {{
                        mode === 'login'
                            ? 'Используйте пароль аккаунта или passkey.'
                            : 'Укажите АКА поинта. Заявку подтвердит администратор.'
                    }}
                </p>
                <form @submit.prevent="submit">
                    <label
                        >Логин<input
                            v-model="login"
                            required
                            maxlength="100"
                            autocomplete="username"
                            pattern="[A-Za-z0-9_.@-]+"
                    /></label>
                    <label
                        >Пароль<input
                            v-model="password"
                            required
                            :minlength="mode === 'register' ? 12 : 1"
                            maxlength="1024"
                            type="password"
                            :autocomplete="mode === 'login' ? 'current-password' : 'new-password'"
                    /></label>
                    <template v-if="mode === 'register'">
                        <label
                            >Повторите пароль<input
                                v-model="confirmation"
                                required
                                type="password"
                                autocomplete="new-password"
                                minlength="12"
                        /></label>
                        <label
                            >Адрес поинта<input
                                v-model="address"
                                required
                                placeholder="1:500/100.1"
                                spellcheck="false"
                        /></label>
                    </template>
                    <button
                        class="primary"
                        :disabled="state.busy.value"
                    >
                        {{ mode === 'login' ? 'Войти' : 'Отправить заявку' }} <span>→</span>
                    </button>
                </form>
                <button
                    v-if="mode === 'login' && state.methods.passkey"
                    class="secondary full"
                    :disabled="state.busy.value"
                    @click="state.run(state.passkeyLogin)"
                >
                    Войти с passkey
                </button>
                <p
                    v-if="state.registration.value"
                    class="center"
                >
                    <button
                        class="text-button"
                        @click="mode = mode === 'login' ? 'register' : 'login'"
                    >
                        {{
                            mode === 'login'
                                ? 'Нет аккаунта? Зарегистрироваться'
                                : 'Уже есть аккаунт? Войти'
                        }}
                    </button>
                </p>
            </template>
        </section>
    </main>
</template>
