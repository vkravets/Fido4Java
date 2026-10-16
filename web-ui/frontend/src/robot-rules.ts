import { reactive, ref } from 'vue';
import type {
    createBindings,
    RobotAccessConfiguration,
    RobotRuleInfo,
    UiServiceClient
} from '@rpc/bindings.mjs';

export function createRobotEditor(
    client: Pick<UiServiceClient, 'robotAccess' | 'saveRobotRule' | 'deleteRobotRule'>,
    session: () => string,
    bindings: Pick<ReturnType<typeof createBindings>, 'RobotRuleInfo' | 'RobotRuleChange'>
) {
    const configuration = ref<RobotAccessConfiguration | null>(null);
    const editing = ref(false);
    const pendingDelete = ref<RobotRuleInfo | null>(null);
    const model = reactive({
        service: 'AreaFix',
        address: '',
        masks: '',
        access: 'READ',
        enabled: true,
        password: ''
    });

    function reset() {
        Object.assign(model, {
            service: 'AreaFix',
            address: configuration.value?.peers[0] || '',
            masks: '',
            access: 'READ',
            enabled: true,
            password: ''
        });
        editing.value = false;
        pendingDelete.value = null;
    }

    async function refresh() {
        configuration.value = await client.robotAccess(session());
    }

    function edit(rule: RobotRuleInfo) {
        Object.assign(model, rule, { password: '' });
        editing.value = true;
        pendingDelete.value = null;
    }

    function writable() {
        if (configuration.value?.source !== 'database') {
            throw new Error('Правила управляются через файлы конфигурации.');
        }
    }

    async function save() {
        writable();
        const rule = new bindings.RobotRuleInfo({
            service: model.service,
            address: model.address,
            masks: model.masks.trim(),
            access: model.service === 'AreaFix' ? model.access : '',
            enabled: model.enabled
        });
        const change = new bindings.RobotRuleChange({ rule });
        if (model.password) change.password = model.password;
        try {
            await client.saveRobotRule(session(), change);
        } finally {
            model.password = '';
        }
        reset();
        await refresh();
    }

    async function remove() {
        writable();
        const rule = pendingDelete.value;
        if (!rule) return;
        await client.deleteRobotRule(session(), rule.service, rule.address);
        reset();
        await refresh();
    }

    return { configuration, editing, pendingDelete, model, reset, refresh, edit, save, remove };
}
