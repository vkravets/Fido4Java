<script setup lang="ts">
import { t } from '../i18n.ts';
import FidoText from './FidoText.vue';
import type { MailItem } from '@rpc/bindings.mjs';
withDefaults(
    defineProps<{
        message: MailItem;
        busy: boolean;
        reply?: boolean;
        removable?: boolean;
        forwardable?: boolean;
    }>(),
    { removable: true }
);
defineEmits<{ reply: []; remove: []; forward: [] }>();
</script>
<template>
    <article class="card mail-message">
        <header class="section-heading">
            <h2>{{ message.subject }}</h2>
            <div class="actions">
                <button
                    v-if="reply"
                    class="secondary"
                    :disabled="busy"
                    @click="$emit('reply')"
                >
                    {{ t('Reply') }}
                </button>
                <button
                    v-if="forwardable"
                    class="secondary"
                    :disabled="busy"
                    @click="$emit('forward')"
                >
                    {{ t('Forward') }}
                </button>
                <button
                    v-if="removable"
                    class="danger"
                    :disabled="busy"
                    @click="$emit('remove')"
                >
                    {{ t('Delete message') }}
                </button>
            </div>
        </header>
        <dl class="mail-envelope">
            <dt>{{ t('From') }}</dt>
            <dd>{{ message.fromName }} &lt;{{ message.fromAddress }}&gt;</dd>
            <dt>{{ t('To') }}</dt>
            <dd>{{ message.toName }} &lt;{{ message.toAddress }}&gt;</dd>
            <dt>{{ t('Date') }}</dt>
            <dd>{{ message.date }}</dd>
        </dl>
        <FidoText :text="message.body || ''" />
    </article>
</template>
