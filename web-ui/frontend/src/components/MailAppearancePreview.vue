<script setup lang="ts">
import { computed, provide, ref, watch } from 'vue';
import { mailAppearanceKey, useMailAppearance } from '../mail-themes.ts';
import type { MailEncoding, MailTheme } from '../mail-themes.ts';
import type { MailDraft, MailItem } from '@rpc/bindings.mjs';
import { t } from '../i18n.ts';
import MailComposer from './MailComposer.vue';
import NetmailMessage from './NetmailMessage.vue';

export type AppearancePreviewMode = 'reader' | 'editor' | 'custom';
const mode = defineModel<AppearancePreviewMode>({ required: true });
const props = defineProps<{
    reader: MailTheme;
    editor: MailTheme;
    custom: MailTheme;
    encoding: MailEncoding;
}>();
const appearance = useMailAppearance();
const theme = computed(() => props[mode.value]);
const previewTheme = ref('appearance-preview');
provide(mailAppearanceKey, {
    ...appearance,
    reader: previewTheme,
    editor: previewTheme,
    find: (id) => (id === previewTheme.value ? theme.value : appearance.find(id))
});
const body =
    'Hello Bob,\n\nAE> Welcome to the conference!\nBE>> Thank you.\nCS>>> See you here!\nDE>>>> The fourth level uses the first color.\nEE>>>>> The fifth level uses the second color.\nFE>>>>>> The sixth level uses the third color.\n\nThis is *bold*, /italic/, _underlined_ and #highlighted#.\nhttps://example.test\n\n-- \nAlex\n--- JFtn\n * Origin: Example station (1:500/100.1)';
const message: MailItem = {
    id: '1',
    messageId: '1:500/100.1 12345678',
    fromName: 'Alex Example',
    fromAddress: '1:500/100.1',
    toName: 'Bob Example',
    toAddress: '1:500/101',
    subject: 'Welcome to the conference',
    date: '20 Oct 26  14:27:00',
    read: true,
    body
};
const draft = ref<MailDraft>({
    id: 'appearance-preview',
    mailbox: 3,
    area: '',
    fromName: message.fromName,
    fromAddress: message.fromAddress,
    toName: message.toName,
    toAddress: message.toAddress,
    subject: message.subject,
    body,
    templateInitialized: true
});
watch(
    () => props.encoding,
    (encoding) => {
        draft.value.encoding = encoding;
    },
    { immediate: true }
);
</script>
<template>
    <aside
        class="appearance-preview"
        :aria-label="t('Live mail preview')"
    >
        <h3>{{ t('Live mail preview') }}</h3>
        <p class="muted">{{ t('Preview changes are not saved or sent.') }}</p>
        <nav
            class="tabs"
            :aria-label="t('Preview mode')"
        >
            <button
                v-for="tab in ['reader', 'editor', 'custom'] as const"
                :key="tab"
                :class="{ selected: mode === tab }"
                :aria-pressed="mode === tab"
                @click="mode = tab"
            >
                {{
                    t(
                        tab === 'reader'
                            ? 'Reading theme'
                            : tab === 'editor'
                              ? 'Editor theme'
                              : 'Customize theme'
                    )
                }}
            </button>
        </nav>
        <div class="appearance-preview-window">
            <NetmailMessage
                v-if="mode === 'reader'"
                :message="message"
                :busy="false"
                :reply="true"
                :removable="false"
                :navigation="true"
                @reply="mode = 'editor'"
            />
            <MailComposer
                v-else
                v-model="draft"
                :addresses="[message.fromAddress]"
                :busy="false"
                :preview-only="true"
            />
        </div>
    </aside>
</template>
<style scoped>
.appearance-preview {
    min-width: 0;
    position: sticky;
    top: 16px;
    align-self: start;
}
.appearance-preview h3 {
    margin: 0 0 6px;
}
.appearance-preview > p {
    margin: 0 0 12px;
    font-size: 12px;
}
.appearance-preview .tabs {
    flex-wrap: wrap;
}
.appearance-preview-window {
    overflow: auto;
    max-height: calc(100dvh - 180px);
    border: 1px solid var(--line);
    border-radius: 6px;
}
.appearance-preview-window :deep(.card) {
    margin: 0;
    border: 0;
    border-radius: 0;
    box-shadow: none;
}
.appearance-preview-window :deep(.mail-message) {
    padding: 0;
}
@media (max-width: 1000px) {
    .appearance-preview {
        position: static;
    }
    .appearance-preview-window {
        max-height: 720px;
    }
}
</style>
