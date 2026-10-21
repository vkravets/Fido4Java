<script setup lang="ts">
import SettingsSectionHeader from './SettingsSectionHeader.vue';
import { computed, ref } from 'vue';
import { t } from '../i18n.ts';
import { templateLabel, templateError } from '../mail-template-labels.ts';
import {
    builtInTemplates,
    expandTemplate,
    mailTemplate,
    useMailTemplates
} from '../mail-templates.ts';
import type { ComposeKind, MailTemplate, TemplateRule } from '../mail-templates.ts';
import type { AppState } from '../state.ts';
import FidoText from './FidoText.vue';
const props = defineProps<{ state: AppState; initialTemplate?: MailTemplate }>();
const store = useMailTemplates();
const selected = ref('new');
const draft = ref<MailTemplate>({ ...(props.initialTemplate || builtInTemplates[0]!) });
let original = JSON.stringify(draft.value);
const bodyInput = ref<HTMLTextAreaElement>();
const kinds: ComposeKind[] = ['new', 'reply', 'quoted', 'forward'];
const kindLabels = {
    new: 'New message',
    reply: 'Short reply',
    quoted: 'Quoted reply',
    forward: 'Forward'
} as const;
const rule = ref<TemplateRule>({ kind: 'new', aka: '', area: '', template: 'new' });
const tokens = [
    '@cname',
    '@caddr',
    '@tname',
    '@taddr',
    '@oname',
    '@oaddr',
    '@odate',
    '@cdate',
    '@ctime',
    '@subject',
    '@cecho',
    '@quote',
    '@message',
    '@position'
];
const custom = computed(() => store.custom.value.some((value) => value.id === selected.value));
function canLeave() {
    return (
        JSON.stringify(draft.value) === original || window.confirm(t('Discard unsaved changes?'))
    );
}
defineExpose({ canLeave });
function select(event: Event) {
    const input = event.target as HTMLSelectElement;
    if (!canLeave()) {
        input.value = selected.value;
        return;
    }
    selected.value = input.value;
    draft.value = { ...store.all.value.find((value) => value.id === selected.value)! };
    original = JSON.stringify(draft.value);
}
async function save() {
    const id = custom.value ? selected.value : 'custom-' + crypto.randomUUID().slice(0, 12);
    const next = mailTemplate({ ...draft.value, id });
    await store.save(
        [...store.custom.value.filter((value) => value.id !== id), next],
        store.rules.value
    );
    selected.value = id;
    draft.value = { ...next };
    original = JSON.stringify(next);
    props.state.notice.value = t('Settings saved.');
}
async function remove() {
    if (!custom.value || !window.confirm(t('Delete this template?'))) return;
    await store.save(
        store.custom.value.filter((value) => value.id !== selected.value),
        store.rules.value.filter((value) => value.template !== selected.value)
    );
    selected.value = 'new';
    draft.value = { ...builtInTemplates[0]! };
    original = JSON.stringify(draft.value);
}
function insert(token: string) {
    const input = bodyInput.value;
    if (!input) return;
    const start = input.selectionStart;
    draft.value.body =
        draft.value.body.slice(0, start) + token + draft.value.body.slice(input.selectionEnd);
    input.focus();
    requestAnimationFrame(() =>
        input.setSelectionRange(start + token.length, start + token.length)
    );
}
const preview = computed(() => {
    try {
        return {
            body: expandTemplate(draft.value, {
                kind: draft.value.kind,
                echo: true,
                fromName: props.state.profile.value?.login || 'Alex',
                fromAddress: props.state.profile.value?.defaultAka || '1:500/100.1',
                toName: 'Bob',
                toAddress: '1:500/101',
                subject: 'Example discussion',
                area: 'test.echo',
                originalName: 'Bob',
                originalAddress: '1:500/101',
                originalDate: '20 Oct 26  10:00:00',
                originalBody: 'Welcome to the conference!'
            }).body,
            error: ''
        };
    } catch (failure) {
        return { body: '', error: templateError(failure) };
    }
});
async function saveRule() {
    const value = { ...rule.value, area: rule.value.area.trim().toLowerCase() };
    const others = store.rules.value.filter(
        (row) => !(row.kind === value.kind && row.aka === value.aka && row.area === value.area)
    );
    await store.save(store.custom.value, [...others, value]);
    props.state.notice.value = t('Settings saved.');
}
async function removeRule(index: number) {
    await store.save(
        store.custom.value,
        store.rules.value.filter((_value, offset) => index !== offset)
    );
}
</script>
<template>
    <SettingsSectionHeader
        :title="t('Mail templates')"
        :description="t('Templates change message text, never posting permissions.')"
        page
    />
    <div class="template-workspace">
        <section class="card">
            <label
                >{{ t('Template')
                }}<select
                    :value="selected"
                    @change="select"
                >
                    <option
                        v-for="value in store.all.value"
                        :key="value.id"
                        :value="value.id"
                    >
                        {{ templateLabel(value) }}
                    </option>
                </select></label
            >
            <fieldset :disabled="state.busy.value">
                <p
                    v-if="!custom"
                    class="muted"
                >
                    {{ t('Editing a built-in template creates a personal copy.') }}
                </p>
                <label
                    >{{ t('Template name')
                    }}<input
                        v-model="draft.name"
                        maxlength="80"
                /></label>
                <label
                    >{{ t('Message kind')
                    }}<select v-model="draft.kind">
                        <option
                            v-for="kind in kinds"
                            :key="kind"
                            :value="kind"
                        >
                            {{ t(kindLabels[kind]) }}
                        </option>
                    </select></label
                >
                <label
                    >{{ t('Template body')
                    }}<textarea
                        ref="bodyInput"
                        v-model="draft.body"
                        rows="14"
                        maxlength="6000"
                    ></textarea>
                </label>
                <div class="actions token-buttons">
                    <button
                        v-for="token in tokens"
                        :key="token"
                        type="button"
                        class="secondary"
                        @click="insert(token)"
                    >
                        {{ token }}
                    </button>
                </div>
                <p class="muted">
                    {{
                        t(
                            'Position inserts the cursor; quote inserts the original message with FIDO prefixes.'
                        )
                    }}
                </p>
                <div class="actions">
                    <button
                        class="primary"
                        :disabled="!!preview.error"
                        @click="state.run(save)"
                    >
                        {{ t('Save template') }}</button
                    ><button
                        v-if="custom"
                        class="danger"
                        @click="state.run(remove)"
                    >
                        {{ t('Delete template') }}
                    </button>
                </div>
            </fieldset>
        </section>
        <section class="card">
            <h2>{{ t('Preview') }}</h2>
            <p
                v-if="preview.error"
                role="status"
            >
                {{ preview.error }}
            </p>
            <FidoText
                v-else
                :text="preview.body"
            />
        </section>
    </div>
    <section class="card template-defaults">
        <h2>{{ t('Automatic templates') }}</h2>
        <p>{{ t('An exact AKA or conference rule overrides the general default.') }}</p>
        <fieldset :disabled="state.busy.value">
            <div class="form-grid">
                <label
                    >{{ t('Message kind')
                    }}<select v-model="rule.kind">
                        <option
                            v-for="kind in kinds"
                            :key="kind"
                            :value="kind"
                        >
                            {{ t(kindLabels[kind]) }}
                        </option>
                    </select></label
                ><label
                    >{{ t('Sender AKA')
                    }}<select v-model="rule.aka">
                        <option value="">{{ t('All') }}</option>
                        <option
                            v-for="aka in state.profile.value?.allowedAkas || []"
                            :key="aka"
                            :value="aka"
                        >
                            {{ aka }}
                        </option>
                    </select></label
                ><label
                    >{{ t('Conference')
                    }}<input
                        v-model="rule.area"
                        maxlength="255" /></label
                ><label
                    >{{ t('Template')
                    }}<select v-model="rule.template">
                        <option
                            v-for="value in store.all.value"
                            :key="value.id"
                            :value="value.id"
                        >
                            {{ templateLabel(value) }}
                        </option>
                    </select></label
                >
            </div>
            <button
                class="primary"
                @click="state.run(saveRule)"
            >
                {{ t('Save default') }}
            </button>
            <ul>
                <li
                    v-for="(value, index) in store.rules.value"
                    :key="index"
                >
                    {{ t(kindLabels[value.kind]) }} · {{ value.aka || t('All') }} ·
                    {{ value.area || t('All') }} →
                    {{ store.all.value.find((template) => template.id === value.template)?.name }}
                    <button
                        class="text-button"
                        @click="state.run(() => removeRule(index))"
                    >
                        {{ t('Remove') }}
                    </button>
                </li>
            </ul>
        </fieldset>
    </section>
</template>
<style scoped>
.template-workspace {
    display: grid;
    grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
    gap: 16px;
}
.token-buttons {
    flex-wrap: wrap;
    gap: 4px;
    margin: 12px 0;
}
.token-buttons button {
    font-family: monospace;
    padding: 4px 6px;
}
.template-defaults {
    margin-top: 16px;
}
fieldset {
    border: 0;
    padding: 0;
    min-width: 0;
}
textarea {
    font-family: monospace;
}
@media (max-width: 850px) {
    .template-workspace {
        grid-template-columns: minmax(0, 1fr);
    }
}
</style>
