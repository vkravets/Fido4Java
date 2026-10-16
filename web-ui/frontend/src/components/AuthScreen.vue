<script setup lang="ts">
import { t } from '../i18n.ts';
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
        if (password.value !== confirmation.value) throw new Error(t('Passwords do not match.'));
        await api.profile.register(login.value, password.value, address.value);
        password.value = confirmation.value = '';
        pending.value = true;
    });
}
</script>

<template>
    <main class="auth-layout">
        <section class="intro">
            <span class="eyebrow">{{ t('FIDONET · CONNECTIONS WITHOUT BORDERS') }}</span>
            <h1>{{ t('Your mail.') }}<br />{{ t('Your station.') }}</h1>
            <p>{{ t('One interface for personal mail, echomail and your node.') }}</p>
            <div
                class="network-art"
                aria-hidden="true"
            >
                <i></i><i></i><i></i><i></i><b>JFtn</b>
            </div>
            <small>{{ t('Netmail and echomail are coming in the next stage.') }}</small>
        </section>
        <section class="card auth-card">
            <template v-if="pending">
                <span class="badge">{{ t('Request submitted') }}</span>
                <h2>{{ t('Awaiting confirmation') }}</h2>
                <p>
                    {{
                        t(
                            'The administrator will review your account and address {address}. Sign-in is unavailable until approval.',
                            { address }
                        )
                    }}
                </p>
                <button
                    @click="
                        pending = false;
                        mode = 'login';
                    "
                >
                    {{ t('Back to sign-in') }}
                </button>
            </template>
            <template v-else>
                <span class="eyebrow">{{ t('WELCOME') }}</span>
                <h2>{{ mode === 'login' ? t('Sign in to JFtn') : t('Create an account') }}</h2>
                <p>
                    {{
                        mode === 'login'
                            ? t('Use your account password or a passkey.')
                            : t('Enter a point AKA. An administrator will review your request.')
                    }}
                </p>
                <form @submit.prevent="submit">
                    <label
                        >{{ t('Username')
                        }}<input
                            v-model="login"
                            required
                            maxlength="100"
                            autocomplete="username"
                            pattern="[A-Za-z0-9_.@-]+"
                    /></label>
                    <label
                        >{{ t('Password')
                        }}<input
                            v-model="password"
                            required
                            :minlength="mode === 'register' ? 12 : 1"
                            maxlength="1024"
                            type="password"
                            :autocomplete="mode === 'login' ? 'current-password' : 'new-password'"
                    /></label>
                    <template v-if="mode === 'register'">
                        <label
                            >{{ t('Confirm password')
                            }}<input
                                v-model="confirmation"
                                required
                                type="password"
                                autocomplete="new-password"
                                minlength="12"
                        /></label>
                        <label
                            >{{ t('Point address')
                            }}<input
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
                        {{ mode === 'login' ? t('Sign in') : t('Submit request') }} <span>→</span>
                    </button>
                </form>
                <button
                    v-if="mode === 'login' && state.methods.passkey"
                    class="secondary full"
                    :disabled="state.busy.value"
                    @click="state.run(state.passkeyLogin)"
                >
                    {{ t('Sign in with a passkey') }}
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
                                ? t('No account? Register')
                                : t('Already have an account? Sign in')
                        }}
                    </button>
                </p>
            </template>
        </section>
    </main>
</template>
