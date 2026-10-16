import { reactive, ref } from 'vue';
import { api, bindings, loginSession } from './api.ts';
import { credentialJson, publicKeyOptions } from './webauthn.ts';
import type { LoginResponse, UserProfile } from '@rpc/bindings.mjs';

export type Theme = 'light' | 'dark';
export type AppState = ReturnType<typeof createState>;

export function createState() {
    const profile = ref<UserProfile | null>(null);
    const session = ref('');
    const busy = ref(false);
    const error = ref('');
    const notice = ref('');
    const ready = ref(false);
    const setup = ref(false);
    const registration = ref(false);
    const methods = reactive({ password: true, passkey: false });
    const theme = ref<Theme>(localStorage.getItem('jftn-theme') === 'dark' ? 'dark' : 'light');

    function setTheme(value: Theme) {
        theme.value = value;
        document.documentElement.dataset.theme = value;
        localStorage.setItem('jftn-theme', value);
    }
    setTheme(theme.value);

    async function toggleTheme(): Promise<void> {
        const next = theme.value === 'dark' ? 'light' : 'dark';
        if (profile.value) {
            await api.profile.savePreferences(session.value, next, profile.value.defaultAka);
        }
        setTheme(next);
    }

    async function run<T>(operation: () => Promise<T>): Promise<T | undefined> {
        if (busy.value) return;
        busy.value = true;
        error.value = '';
        notice.value = '';
        try {
            return await operation();
        } catch (failure) {
            const reason = failure as { message?: string; code?: string } | null;
            error.value = reason?.message || 'Не удалось выполнить запрос. Попробуйте снова.';
            if (reason?.code === 'UNAUTHORIZED' || reason?.code === 'SESSION_EXPIRED') {
                session.value = '';
                profile.value = null;
            }
        } finally {
            busy.value = false;
        }
    }

    async function refreshProfile() {
        profile.value = await api.profile.me(session.value);
        setTheme(profile.value.theme === 'dark' ? 'dark' : 'light');
    }

    async function enter(response: LoginResponse) {
        session.value = loginSession(response);
        try {
            await refreshProfile();
        } catch (failure) {
            session.value = '';
            throw failure;
        }
    }

    async function start() {
        const started = await run(async () => {
            setup.value = (await api.settings.status()).required;
            if (!setup.value) {
                Object.assign(methods, await api.auth.methods());
                registration.value = await api.profile.registrationEnabled();
            }
            return true;
        });
        if (started) ready.value = true;
    }

    async function logout() {
        const token = session.value;
        session.value = '';
        profile.value = null;
        if (token) await api.login.logout(token);
    }

    async function passkeyLogin() {
        if (!window.isSecureContext || !navigator.credentials) {
            throw new Error('Passkey требует HTTPS или localhost и совместимый браузер.');
        }
        const request = await api.auth.beginPasskeyLogin();
        const credential = await navigator.credentials.get({
            publicKey: publicKeyOptions<PublicKeyCredentialRequestOptions>(request.optionsJson)
        });
        if (!(credential instanceof PublicKeyCredential)) throw new Error('Вход отменён.');
        await enter(
            await api.auth.finishPasskeyLogin(request.requestId, credentialJson(credential))
        );
    }

    return {
        profile,
        session,
        busy,
        error,
        notice,
        ready,
        setup,
        registration,
        methods,
        theme,
        run,
        start,
        logout,
        refreshProfile,
        passkeyLogin,
        setTheme,
        toggleTheme,
        login: enterResponse
    };

    async function enterResponse(login: string, password: string) {
        await enter(await api.login.login(new bindings.Credential({ login, password })));
    }
}
