<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { api } from '../api.ts';
import type { PasskeyInfo } from '@rpc/bindings.mjs';
import { credentialJson, publicKeyOptions } from '../webauthn.ts';
import type { AppState } from '../state.ts';
const props = defineProps<{ state: AppState }>();
const current = ref('');
const password = ref('');
const confirmation = ref('');
const keyPassword = ref('');
const label = ref('');
const keys = ref<PasskeyInfo[]>([]);
const supported = window.isSecureContext && !!navigator.credentials;
async function refresh() {
    keys.value = await api.auth.passkeys(props.state.session.value);
}
onMounted(() => props.state.run(refresh));

function changePassword() {
    props.state.run(async () => {
        if (password.value !== confirmation.value) throw new Error('Пароли не совпадают.');
        await api.auth.changePassword(props.state.session.value, current.value, password.value);
        current.value = password.value = confirmation.value = '';
        await props.state.logout();
        props.state.notice.value = 'Пароль изменён. Войдите снова.';
    });
}

function addPasskey() {
    props.state.run(async () => {
        const session = props.state.session.value;
        const request = await api.auth.beginPasskeyRegistration(
            session,
            keyPassword.value,
            label.value
        );
        keyPassword.value = '';
        const credential = await navigator.credentials.create({
            publicKey: publicKeyOptions<PublicKeyCredentialCreationOptions>(request.optionsJson)
        });
        if (!(credential instanceof PublicKeyCredential))
            throw new Error('Добавление passkey отменено.');
        await api.auth.finishPasskeyRegistration(
            session,
            request.requestId,
            credentialJson(credential)
        );
        label.value = '';
        await refresh();
        props.state.notice.value = 'Passkey добавлен.';
    });
}

function removeKey(id: string) {
    props.state.run(async () => {
        if (!keyPassword.value) throw new Error('Подтвердите удаление текущим паролем.');
        await api.auth.removePasskey(props.state.session.value, keyPassword.value, id);
        keyPassword.value = '';
        await refresh();
    });
}
</script>

<template>
    <section class="card">
        <h2>Пароль</h2>
        <p>После изменения все сеансы аккаунта будут завершены.</p>
        <form
            class="narrow-form"
            @submit.prevent="changePassword"
        >
            <label
                >Текущий пароль<input
                    v-model="current"
                    type="password"
                    required
                    autocomplete="current-password"
            /></label>
            <label
                >Новый пароль<input
                    v-model="password"
                    type="password"
                    required
                    minlength="12"
                    maxlength="1024"
                    autocomplete="new-password"
            /></label>
            <label
                >Повторите пароль<input
                    v-model="confirmation"
                    type="password"
                    required
                    minlength="12"
                    autocomplete="new-password"
            /></label>
            <button
                class="primary"
                :disabled="state.busy.value"
            >
                Изменить пароль
            </button>
        </form>
    </section>
    <section class="card">
        <h2>Passkey</h2>
        <p>Вход по отпечатку, распознаванию лица или аппаратному ключу.</p>
        <p
            v-if="!state.methods.passkey || !supported"
            class="hint"
        >
            Добавление passkey доступно при включённом WebAuthn на сервере и в браузере с HTTPS или
            localhost.
        </p>
        <ul class="rows">
            <li
                v-for="key in keys"
                :key="key.credentialId"
            >
                <span>{{ key.label || 'Passkey' }}</span
                ><button
                    class="text-button danger"
                    :disabled="state.busy.value"
                    @click="removeKey(key.credentialId)"
                >
                    Удалить
                </button>
            </li>
            <li
                v-if="!keys.length"
                class="muted"
            >
                Passkey пока нет
            </li>
        </ul>
        <form
            class="narrow-form"
            @submit.prevent="addPasskey"
        >
            <label
                >Текущий пароль для добавления или удаления<input
                    v-model="keyPassword"
                    type="password"
                    required
                    autocomplete="current-password"
            /></label>
            <label
                >Название нового ключа<input
                    v-model="label"
                    required
                    maxlength="100"
                    placeholder="Например, мой ноутбук"
            /></label>
            <button
                class="secondary"
                :disabled="state.busy.value || !state.methods.passkey || !supported"
            >
                Добавить passkey
            </button>
        </form>
    </section>
</template>
