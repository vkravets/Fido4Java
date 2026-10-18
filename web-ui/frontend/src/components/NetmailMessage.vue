<script setup lang="ts">
import { t } from '../i18n.ts';
import type { MailItem } from '@rpc/bindings.mjs';
defineProps<{ message: MailItem; busy: boolean; reply?: boolean }>();
defineEmits<{ reply: [] }>();
</script>
<template>
    <article class="card mail-message">
        <header class="section-heading">
            <h2>{{ message.subject }}</h2>
            <button
                v-if="reply"
                class="secondary"
                :disabled="busy"
                @click="$emit('reply')"
            >
                {{ t('Reply') }}
            </button>
        </header>
        <dl class="mail-envelope">
            <dt>{{ t('From') }}</dt>
            <dd>{{ message.fromName }} &lt;{{ message.fromAddress }}&gt;</dd>
            <dt>{{ t('To') }}</dt>
            <dd>{{ message.toName }} &lt;{{ message.toAddress }}&gt;</dd>
            <dt>{{ t('Date') }}</dt>
            <dd>{{ message.date }}</dd>
        </dl>
        <pre class="mail-body">{{
            (message.body || '').replaceAll('\r\n', '\n').replaceAll('\r', '\n')
        }}</pre>
    </article>
</template>
