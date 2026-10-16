<script setup lang="ts">
import { t } from '../i18n.ts';
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
        if (password.value !== confirmation.value) throw new Error(t('Passwords do not match.'));
        await api.auth.changePassword(props.state.session.value, current.value, password.value);
        current.value = password.value = confirmation.value = '';
        await props.state.logout();
        props.state.notice.value = t('Password changed. Sign in again.');
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
            throw new Error(t('Passkey registration cancelled.'));
        await api.auth.finishPasskeyRegistration(
            session,
            request.requestId,
            credentialJson(credential)
        );
        label.value = '';
        await refresh();
        props.state.notice.value = t('Passkey added.');
    });
}

function removeKey(id: string) {
    props.state.run(async () => {
        if (!keyPassword.value) throw new Error(t('Confirm deletion with your current password.'));
        await api.auth.removePasskey(props.state.session.value, keyPassword.value, id);
        keyPassword.value = '';
        await refresh();
    });
}
</script>

<template>
    <section class="card">
        <h2>{{ t('Password') }}</h2>
        <p>{{ t('Changing the password will end all account sessions.') }}</p>
        <form
            class="narrow-form"
            @submit.prevent="changePassword"
        >
            <label
                >{{ t('Current password')
                }}<input
                    v-model="current"
                    type="password"
                    required
                    autocomplete="current-password"
            /></label>
            <label
                >{{ t('New password')
                }}<input
                    v-model="password"
                    type="password"
                    required
                    minlength="12"
                    maxlength="1024"
                    autocomplete="new-password"
            /></label>
            <label
                >{{ t('Confirm password')
                }}<input
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
                {{ t('Change password') }}
            </button>
        </form>
    </section>
    <section class="card">
        <h2>{{ t('Passkeys') }}</h2>
        <p>{{ t('Sign in with a fingerprint, face recognition or a security key.') }}</p>
        <p
            v-if="!state.methods.passkey || !supported"
            class="hint"
        >
            {{
                t(
                    'Passkeys can be added when WebAuthn is enabled on the server and the browser uses HTTPS or localhost.'
                )
            }}
        </p>
        <ul class="rows">
            <li
                v-for="key in keys"
                :key="key.credentialId"
            >
                <span>{{ key.label || t('Passkey') }}</span
                ><button
                    class="text-button danger"
                    :disabled="state.busy.value"
                    @click="removeKey(key.credentialId)"
                >
                    {{ t('Delete') }}
                </button>
            </li>
            <li
                v-if="!keys.length"
                class="muted"
            >
                {{ t('No passkeys yet') }}
            </li>
        </ul>
        <form
            class="narrow-form"
            @submit.prevent="addPasskey"
        >
            <label
                >{{ t('Current password to add or remove a passkey')
                }}<input
                    v-model="keyPassword"
                    type="password"
                    required
                    autocomplete="current-password"
            /></label>
            <label
                >{{ t('New passkey name')
                }}<input
                    v-model="label"
                    required
                    maxlength="100"
                    :placeholder="t('For example, my laptop')"
            /></label>
            <button
                class="secondary"
                :disabled="state.busy.value || !state.methods.passkey || !supported"
            >
                {{ t('Add passkey') }}
            </button>
        </form>
    </section>
</template>
