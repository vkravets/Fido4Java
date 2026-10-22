<script setup lang="ts">
import AppIcon from './AppIcon.vue';
import { t } from '../i18n.ts';
import { templateLabel, templateError } from '../mail-template-labels.ts';
import { computed, nextTick, onMounted, ref, useId, watch } from 'vue';
import FidoEditor from './FidoEditor.vue';
import NotificationBanner from './NotificationBanner.vue';
import { expandTemplate, useMailTemplates } from '../mail-templates.ts';
import type { ComposeKind, MailTemplate } from '../mail-templates.ts';
import AddressInput from './AddressInput.vue';
import type { DirectoryNode, MailDraft } from '@rpc/bindings.mjs';
import { useMailAppearance } from '../mail-themes.ts';
const draft = defineModel<MailDraft>({ required: true });
const props = defineProps<{ addresses: string[]; busy: boolean; previewOnly?: boolean }>();
const emit = defineEmits<{ save: []; send: []; cancel: []; initialized: [] }>();
const editor = ref<InstanceType<typeof FidoEditor>>();
const templates = useMailTemplates();
const appearance = useMailAppearance();
const error = ref('');
const selectedTemplate = ref('');
const senderId = useId();
const senderOpen = ref(!draft.value.fromName || !props.addresses.includes(draft.value.fromAddress));
function recipientSelected(node: DirectoryNode) {
    draft.value.toName = node.sysop;
    if (draft.value.mailbox !== 1) draft.value.toAddress = node.address;
}
function invalidField(event: Event) {
    const target = event.target as HTMLInputElement;
    if (senderOpen.value || !target.closest('.composer-sender-fields')) return;
    event.preventDefault();
    senderOpen.value = true;
    void nextTick(() => {
        target.focus();
        target.reportValidity();
    });
}
watch(
    () => draft.value.id,
    () => {
        senderOpen.value =
            !draft.value.fromName || !props.addresses.includes(draft.value.fromAddress);
    }
);
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
        toName: draft.value.toName,
        toAddress: draft.value.toAddress,
        subject: draft.value.subject,
        area: draft.value.area,
        originalName: draft.value.originalName || draft.value.toName,
        originalAddress: draft.value.originalAddress || draft.value.toAddress,
        originalDate: draft.value.originalDate,
        originalBody: draft.value.originalBody || ''
    };
}
function apply(template: MailTemplate, automatic = false, focus = true) {
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
        void nextTick(() => editor.value?.focusAt(result.cursor, focus));
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
        draft.value.toAddress,
        draft.value.subject,
        draft.value.area
    ],
    () => {
        if (automaticBody === null || draft.value.body !== automaticBody) return;
        apply(templates.choose(kind.value, draft.value.fromAddress, draft.value.area), true, false);
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
        <div class="composer-heading">
            <div class="composer-title">
                <h2>
                    {{
                        draft.replyTo
                            ? t('Reply')
                            : draft.mailbox === 1
                              ? t('New echomail')
                              : t('New netmail')
                    }}
                </h2>
                <span
                    v-if="draft.mailbox === 1"
                    class="composer-area"
                    >{{ draft.area }}</span
                >
            </div>
            <button
                type="button"
                class="secondary composer-sender-toggle"
                :disabled="busy"
                :aria-label="t('Edit sender')"
                :title="t('Edit sender')"
                :aria-expanded="senderOpen"
                :aria-controls="senderId"
                @click="senderOpen = !senderOpen"
            >
                <span
                    >{{ draft.fromName }} <span class="muted">· {{ draft.fromAddress }}</span></span
                >
                <AppIcon name="edit" />
            </button>
        </div>
        <fieldset
            :disabled="busy"
            @invalid.capture="invalidField"
        >
            <div
                :id="senderId"
                v-show="senderOpen"
                class="composer-sender-fields"
            >
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
            </div>
            <div
                class="composer-fields"
                :class="{ 'composer-echo-fields': draft.mailbox === 1 }"
            >
                <label
                    ><span class="field-label">{{ t('To') }}</span
                    ><AddressInput
                        v-model="draft.toName"
                        :aria-label="t('Recipient name')"
                        value-kind="name"
                        @select="recipientSelected"
                        required
                        maxlength="35"
                /></label>
                <label v-if="draft.mailbox !== 1"
                    ><span class="field-label">{{ t('Address') }}</span
                    ><AddressInput
                        v-model="draft.toAddress"
                        :aria-label="t('Recipient address')"
                        @select="recipientSelected"
                        required
                        :include-points="true"
                /></label>
                <label class="composer-subject"
                    ><span class="field-label">{{ t('Subject') }}</span
                    ><input
                        v-model="draft.subject"
                        :aria-label="t('Subject')"
                        required
                        maxlength="71"
                /></label>
            </div>
            <p
                v-if="draft.replyTo"
                class="composer-reply-reference"
            >
                <AppIcon name="reply" />{{ t('Reply to message') }}: {{ draft.replyTo }}
            </p>
            <NotificationBanner
                kind="error"
                :message="error"
            />
            <FidoEditor
                :key="draft.id"
                ref="editor"
                v-model="draft.body"
                :disabled="busy"
                :encoding="draft.encoding"
            >
                <template #toolbar>
                    <div class="composer-template">
                        <select
                            v-model="selectedTemplate"
                            :aria-label="t('Template')"
                        >
                            <option value="">{{ t('Template') }}</option>
                            <option
                                v-for="template in templates.all.value"
                                :key="template.id"
                                :value="template.id"
                            >
                                {{ templateLabel(template) }}
                            </option>
                        </select>
                        <button
                            type="button"
                            class="secondary"
                            :disabled="!selectedTemplate"
                            :aria-label="t('Apply template')"
                            :title="t('Apply template')"
                            v-shortcut="'compose.template'"
                            @click="chooseTemplate"
                        >
                            <AppIcon name="templates" />
                        </button>
                    </div>
                </template>
            </FidoEditor>
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
.mail-composer {
    container-type: inline-size;
}
.mail-composer .composer-heading {
    display: flex;
    align-items: center;
    justify-content: space-between;
    flex-wrap: wrap;
    gap: 6px 12px;
    margin-bottom: 8px;
}
.composer-title {
    display: flex;
    align-items: baseline;
    flex-wrap: wrap;
    gap: 6px 10px;
    min-width: 0;
}
.mail-composer .composer-title h2 {
    margin: 0;
    padding: 0;
    font-size: 18px;
}
.composer-area {
    color: var(--muted);
    font-size: 12px;
    overflow-wrap: anywhere;
}
.mail-composer .composer-sender-toggle {
    display: flex;
    align-items: center;
    gap: 8px;
    min-width: 0;
    max-width: 100%;
    padding: 5px 7px;
    font-size: 12px;
    background: transparent;
}
.composer-sender-toggle > span {
    overflow-wrap: anywhere;
    text-align: start;
}
.composer-sender-toggle .app-icon {
    flex-shrink: 0;
}
.composer-sender-fields {
    display: grid;
    grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
    gap: 8px 12px;
    padding: 8px;
    border: 1px solid var(--line);
    background: var(--surface);
    border-radius: 6px;
    margin-bottom: 8px;
}
.mail-composer .composer-sender-fields label {
    margin: 0;
    font-size: 12px;
}
.composer-fields {
    display: grid;
    grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
    gap: 6px 12px;
    margin-bottom: 6px;
}
.mail-composer .composer-fields label {
    display: flex;
    flex-direction: row;
    align-items: center;
    gap: 8px;
    margin: 0;
    min-width: 0;
    font-size: 12px;
}
.composer-fields .field-label {
    flex: 0 0 48px;
    color: var(--muted);
}
.composer-fields .address-input,
.composer-fields input {
    flex: 1;
    min-width: 0;
    width: 100%;
}
.composer-subject {
    grid-column: 1 / -1;
}
.composer-echo-fields {
    grid-template-columns: minmax(0, 0.85fr) minmax(0, 1.4fr);
}
.composer-echo-fields .composer-subject {
    grid-column: auto;
}
.mail-composer .composer-reply-reference {
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 12px;
    line-height: 1.4;
    color: var(--muted);
    margin: 0 0 6px;
    overflow-wrap: anywhere;
}
.composer-reply-reference .app-icon {
    width: 12px;
    height: 12px;
    flex-shrink: 0;
}
.composer-actions {
    padding-block: 8px 16px;
}
.composer-template {
    display: flex;
    align-items: center;
    gap: 4px;
    min-width: 0;
}
.mail-composer .composer-template select {
    width: 180px;
    max-width: 100%;
    min-width: 0;
    margin: 0;
    font-size: 12px;
}
.mail-composer .composer-template button {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 32px;
    height: 32px;
    min-height: 32px;
    flex-shrink: 0;
    padding: 5px;
}
@media (min-width: 701px) {
    .mail-composer .composer-heading {
        padding: 8px 12px 0;
    }
    .mail-composer .composer-template button {
        width: 28px;
        height: 28px;
        min-height: 28px;
        padding: 4px;
    }
}
@container (max-width: 520px) {
    .composer-fields,
    .composer-echo-fields,
    .composer-sender-fields {
        grid-template-columns: minmax(0, 1fr);
    }
    .composer-echo-fields .composer-subject {
        grid-column: 1 / -1;
    }
}
</style>
