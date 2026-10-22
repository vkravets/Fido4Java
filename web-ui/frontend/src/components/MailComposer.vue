<script setup lang="ts">
import AppIcon from './AppIcon.vue';
import { t } from '../i18n.ts';
import { templateLabel, templateError } from '../mail-template-labels.ts';
import { computed, nextTick, onMounted, ref, watch } from 'vue';
import FidoEditor from './FidoEditor.vue';
import NotificationBanner from './NotificationBanner.vue';
import { expandTemplate, useMailTemplates } from '../mail-templates.ts';
import type { ComposeKind, MailTemplate } from '../mail-templates.ts';
import AddressInput from './AddressInput.vue';
import type { MailDraft } from '@rpc/bindings.mjs';
import { useMailAppearance } from '../mail-themes.ts';
const draft = defineModel<MailDraft>({ required: true });
const props = defineProps<{ addresses: string[]; busy: boolean; previewOnly?: boolean }>();
const emit = defineEmits<{ save: []; send: []; cancel: []; initialized: [] }>();
const editor = ref<InstanceType<typeof FidoEditor>>();
const templates = useMailTemplates();
const appearance = useMailAppearance();
const error = ref('');
const selectedTemplate = ref('');
const canSubmit = computed(
    () =>
        props.addresses.length > 0 &&
        draft.value.body.trim().length > 0 &&
        new TextEncoder().encode(draft.value.body).length <= 60000
);
const kind = computed<ComposeKind>(() =>
    ['new', 'reply', 'quoted', 'forward'].includes(draft.value.composeKind || '')
        ? (draft.value.composeKind as ComposeKind)
        : draft.value.replyTo
          ? 'quoted'
          : 'new'
);
let automaticBody: string | null = null;
function context() {
    return {
        kind: kind.value,
        echo: draft.value.mailbox === 1,
        fromName: draft.value.fromName,
        fromAddress: draft.value.fromAddress,
        toName: draft.value.toName || 'there',
        toAddress: draft.value.toAddress,
        subject: draft.value.subject,
        area: draft.value.area,
        originalName: draft.value.originalName || draft.value.toName,
        originalAddress: draft.value.originalAddress || draft.value.toAddress,
        originalDate: draft.value.originalDate,
        originalBody: draft.value.originalBody || ''
    };
}
function apply(template: MailTemplate, automatic = false) {
    if (
        !automatic &&
        draft.value.body.trim() &&
        !window.confirm(t('Replace the message body with this template?'))
    )
        return;
    try {
        const result = expandTemplate(template, { ...context(), kind: template.kind });
        draft.value.body = result.body;
        draft.value.templateInitialized = true;
        automaticBody = automatic ? result.body : null;
        selectedTemplate.value = template.id;
        error.value = '';
        void nextTick(() => editor.value?.focusAt(result.cursor));
    } catch (failure) {
        error.value = templateError(failure);
    }
}
onMounted(() => {
    if (!draft.value.encoding) draft.value.encoding = appearance.encoding.value;
    if (draft.value.composeKind && !draft.value.templateInitialized)
        apply(templates.choose(kind.value, draft.value.fromAddress, draft.value.area), true);
    emit('initialized');
});
watch(
    () => [
        draft.value.fromAddress,
        draft.value.fromName,
        draft.value.toName,
        draft.value.subject,
        draft.value.area
    ],
    () => {
        if (automaticBody === null || draft.value.body !== automaticBody) return;
        apply(templates.choose(kind.value, draft.value.fromAddress, draft.value.area), true);
    }
);
function chooseTemplate() {
    const template = templates.all.value.find((value) => value.id === selectedTemplate.value);
    if (template) apply(template);
}
</script>
<template>
    <form
        class="card mail-composer"
        :data-shortcuts-disabled="previewOnly ? '' : undefined"
        @submit.prevent="!previewOnly && canSubmit && $emit('send')"
    >
        <h2>
            {{
                draft.replyTo
                    ? t('Reply')
                    : draft.mailbox === 1
                      ? t('New echomail')
                      : t('New netmail')
            }}
        </h2>
        <p
            v-if="draft.mailbox === 1"
            class="muted"
        >
            {{ t('Conference') }}: {{ draft.area }}
        </p>
        <p
            v-if="draft.replyTo"
            class="muted"
        >
            {{ t('Reply to message') }}: {{ draft.replyTo }}
        </p>
        <fieldset :disabled="busy">
            <div class="form-grid">
                <label
                    >{{ t('Sender name')
                    }}<input
                        v-model="draft.fromName"
                        required
                        maxlength="35"
                /></label>
                <label
                    >{{ t('Sender AKA')
                    }}<select
                        v-model="draft.fromAddress"
                        required
                    >
                        <option
                            v-for="address in addresses"
                            :key="address"
                            :value="address"
                        >
                            {{ address }}
                        </option>
                    </select></label
                >
                <label
                    >{{ t('Recipient name')
                    }}<AddressInput
                        v-model="draft.toName"
                        :aria-label="t('Recipient name')"
                        value-kind="name"
                        @select="
                            (node) => {
                                if (draft.mailbox !== 1) draft.toAddress = node.address;
                            }
                        "
                        required
                        maxlength="35"
                /></label>
                <label v-if="draft.mailbox !== 1"
                    >{{ t('Recipient address')
                    }}<AddressInput
                        v-model="draft.toAddress"
                        :aria-label="t('Recipient address')"
                        required
                        :include-points="true"
                /></label>
            </div>
            <label
                >{{ t('Subject')
                }}<input
                    v-model="draft.subject"
                    required
                    maxlength="71"
            /></label>
            <NotificationBanner
                kind="error"
                :message="error"
            />
            <div class="composer-template actions">
                <label
                    >{{ t('Template')
                    }}<select v-model="selectedTemplate">
                        <option value="">{{ t('Template') }}</option>
                        <option
                            v-for="template in templates.all.value"
                            :key="template.id"
                            :value="template.id"
                        >
                            {{ templateLabel(template) }}
                        </option>
                    </select></label
                ><button
                    type="button"
                    class="secondary"
                    :disabled="!selectedTemplate"
                    v-shortcut="'compose.template'"
                    @click="chooseTemplate"
                >
                    <AppIcon name="templates" />{{ t('Apply template') }}
                </button>
            </div>
            <FidoEditor
                :key="draft.id"
                ref="editor"
                v-model="draft.body"
                :disabled="busy"
                :encoding="draft.encoding"
            />
            <div class="actions composer-actions">
                <button
                    class="primary"
                    type="submit"
                    v-shortcut="'compose.send'"
                    :disabled="!canSubmit || previewOnly"
                >
                    <AppIcon name="send" />{{
                        draft.mailbox === 1 ? t('Post message') : t('Add to send queue')
                    }}
                </button>
                <button
                    class="secondary"
                    type="button"
                    v-shortcut="'compose.save'"
                    @click="$emit('save')"
                    :disabled="previewOnly"
                >
                    <AppIcon name="save" />{{ t('Save draft') }}
                </button>
                <button
                    class="text-button"
                    type="button"
                    v-shortcut="'compose.cancel'"
                    @click="$emit('cancel')"
                    :disabled="previewOnly"
                >
                    <AppIcon name="close" />{{ t('Cancel') }}
                </button>
            </div>
        </fieldset>
    </form>
</template>
<style scoped>
.composer-actions {
    padding-block: 8px 16px;
}
.composer-template {
    align-items: flex-end;
    flex-wrap: wrap;
    gap: 8px;
    margin-bottom: 8px;
}
.composer-template label {
    flex: 0 1 240px;
    min-width: 0;
    margin: 0;
}
.composer-template button {
    flex-shrink: 0;
}
@media (min-width: 701px) {
    .composer-template label {
        flex-direction: row;
        align-items: center;
        flex-basis: 280px;
        gap: 8px;
        white-space: nowrap;
    }
    .composer-template button {
        height: 28px;
        min-height: 28px;
        padding: 4px 7px;
        font-size: 12px;
    }
}
</style>
