import { initializeLocale, t } from './i18n.ts';
import { reactive, ref } from 'vue';
import { api, bindings, loginSession } from './api.ts';
import { credentialJson, publicKeyOptions } from './webauthn.ts';
import type { LoginResponse, UserProfile } from '@rpc/bindings.mjs';

export type Theme = 'light' | 'dark';
export type AppState = ReturnType<typeof createState>;
export interface RunOptions {
    background?: boolean;
}

const sessionKey = 'jftn-session';
function storedSession(): string {
    try {
        return sessionStorage.getItem(sessionKey) || '';
    } catch {
        return '';
    }
}
function rememberSession(token: string) {
    try {
        if (token) sessionStorage.setItem(sessionKey, token);
        else sessionStorage.removeItem(sessionKey);
    } catch {
        // Storage can be disabled; the current in-memory session still works.
    }
}
function authenticationFailure(failure: unknown): boolean {
    const code = (failure as { code?: string } | null)?.code;
    return code === 'UNAUTHORIZED' || code === 'SESSION_EXPIRED';
}

export function createState() {
    initializeLocale();
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

    function clearSession() {
        session.value = '';
        profile.value = null;
        rememberSession('');
    }

    async function toggleTheme(): Promise<void> {
        const next = theme.value === 'dark' ? 'light' : 'dark';
        if (profile.value) {
            await api.profile.savePreferences(session.value, next, profile.value.defaultAka);
        }
        setTheme(next);
    }

    async function run<T>(
        operation: () => Promise<T>,
        options: RunOptions = {}
    ): Promise<T | undefined> {
        if (busy.value) return;
        const token = session.value;
        if (!options.background) busy.value = true;
        error.value = '';
        if (!options.background) notice.value = '';
        try {
            return await operation();
        } catch (failure) {
            if (options.background && token !== session.value) return;
            const reason = failure as { message?: string; code?: string } | null;
            error.value = reason?.message || t('The request failed. Please try again.');
            if (authenticationFailure(failure)) clearSession();
        } finally {
            if (!options.background) busy.value = false;
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
            rememberSession(session.value);
        } catch (failure) {
            clearSession();
            throw failure;
        }
    }

    async function restoreSession() {
        const token = storedSession();
        if (!token) return;
        try {
            const restored = await api.profile.me(token);
            profile.value = restored;
            setTheme(restored.theme === 'dark' ? 'dark' : 'light');
            session.value = token;
        } catch (failure) {
            if (!authenticationFailure(failure)) throw failure;
            clearSession();
        }
    }

    async function start() {
        const started = await run(async () => {
            setup.value = (await api.settings.status()).required;
            if (!setup.value) {
                Object.assign(methods, await api.auth.methods());
                registration.value = await api.profile.registrationEnabled();
                await restoreSession();
            } else clearSession();
            return true;
        });
        if (started) ready.value = true;
    }

    async function logout() {
        const token = session.value;
        clearSession();
        if (token) await api.login.logout(token);
    }

    async function passkeyLogin() {
        if (!window.isSecureContext || !navigator.credentials) {
            throw new Error(t('Passkeys require HTTPS or localhost and a compatible browser.'));
        }
        const request = await api.auth.beginPasskeyLogin();
        const credential = await navigator.credentials.get({
            publicKey: publicKeyOptions<PublicKeyCredentialRequestOptions>(request.optionsJson)
        });
        if (!(credential instanceof PublicKeyCredential)) throw new Error(t('Sign-in cancelled.'));
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
