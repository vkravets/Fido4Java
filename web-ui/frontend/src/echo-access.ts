import { reactive, ref } from 'vue';
import { t } from './i18n.ts';
import type {
    EchoAccessConfiguration,
    EchoSubscriptionInfo,
    UiServiceClient
} from '@rpc/bindings.mjs';

export function createEchoEditor(
    client: Pick<UiServiceClient, 'echoAccess' | 'subscribe' | 'unsubscribe'>,
    session: () => string
) {
    const configuration = ref<EchoAccessConfiguration | null>(null);
    const editing = ref(false);
    const pendingDelete = ref<EchoSubscriptionInfo | null>(null);
    const model = reactive({ peer: '', area: '', access: 'WRITE' });

    function reset() {
        Object.assign(model, {
            peer: configuration.value?.peers[0] || '',
            area: '',
            access: 'WRITE'
        });
        editing.value = false;
        pendingDelete.value = null;
    }
    async function refresh() {
        configuration.value = await client.echoAccess(session());
    }
    function edit(subscription: EchoSubscriptionInfo) {
        Object.assign(model, subscription);
        editing.value = true;
        pendingDelete.value = null;
    }
    function writable() {
        if (configuration.value?.source !== 'database') {
            throw new Error(t('Echo permissions are managed through configuration files.'));
        }
    }
    async function save() {
        writable();
        const area = model.area.trim();
        if (!model.peer || !area || !['READ', 'WRITE', 'BOTH'].includes(model.access)) {
            throw new Error(t('Select a link, an echo area and valid access rights.'));
        }
        await client.subscribe(session(), model.peer, area, model.access);
        await refresh();
        reset();
    }
    async function remove() {
        writable();
        const subscription = pendingDelete.value;
        if (!subscription) return;
        await client.unsubscribe(session(), subscription.peer, subscription.area);
        await refresh();
        reset();
    }
    return { configuration, editing, pendingDelete, model, reset, refresh, edit, save, remove };
}
