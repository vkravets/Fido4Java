<script setup lang="ts">
import { t } from '../i18n.ts';
import AppIcon from './AppIcon.vue';
import FidoText from './FidoText.vue';
import type { MailItem } from '@rpc/bindings.mjs';
withDefaults(
    defineProps<{
        message: MailItem;
        busy: boolean;
        reply?: boolean;
        removable?: boolean;
        forwardable?: boolean;
        navigation?: boolean;
        previousDisabled?: boolean;
        nextDisabled?: boolean;
        unreadDisabled?: boolean;
    }>(),
    { removable: true }
);
defineEmits<{
    reply: [kind?: 'quoted' | 'reply'];
    remove: [];
    forward: [];
    previous: [];
    next: [];
    unread: [];
    compose: [];
}>();
function closeMenu(event: Event) {
    if ((event.target as Element).closest('button'))
        (event.currentTarget as HTMLDetailsElement).open = false;
}
</script>
<template>
    <article class="card mail-message">
        <header class="reader-heading">
            <h2>{{ message.subject }}</h2>
            <div class="reader-meta-actions">
                <dl class="mail-envelope">
                    <dt>{{ t('From') }}</dt>
                    <dd>{{ message.fromName }} &lt;{{ message.fromAddress }}&gt;</dd>
                    <dt>{{ t('To') }}</dt>
                    <dd>{{ message.toName }} &lt;{{ message.toAddress }}&gt;</dd>
                    <dt>{{ t('Date') }}</dt>
                    <dd>{{ message.date }}</dd>
                </dl>
                <div class="actions reader-actions">
                    <button
                        v-if="reply"
                        class="secondary"
                        :disabled="busy"
                        @click="$emit('reply', 'quoted')"
                    >
                        <AppIcon name="reply" />{{ t('Reply') }}
                    </button>
                    <button
                        v-if="navigation"
                        class="secondary"
                        :aria-label="t('Previous message')"
                        :disabled="busy || previousDisabled"
                        @click="$emit('previous')"
                    >
                        <AppIcon name="left" /><span>{{ t('Previous') }}</span>
                    </button>
                    <button
                        v-if="navigation"
                        class="secondary"
                        :aria-label="t('Next message')"
                        :disabled="busy || nextDisabled"
                        @click="$emit('next')"
                    >
                        <span>{{ t('Next') }}</span
                        ><AppIcon name="right" />
                    </button>
                    <div class="reader-direct-actions">
                        <button
                            v-if="forwardable"
                            class="secondary"
                            :disabled="busy"
                            @click="$emit('forward')"
                        >
                            <AppIcon name="forward" />{{ t('Forward') }}
                        </button>
                        <button
                            v-if="removable"
                            class="danger"
                            :disabled="busy"
                            @click="$emit('remove')"
                        >
                            <AppIcon name="trash" />{{ t('Delete message') }}
                        </button>
                    </div>
                    <details
                        v-if="reply || forwardable || removable"
                        class="reader-more"
                        @click="closeMenu"
                        @keydown.esc="($event.currentTarget as HTMLDetailsElement).open = false"
                    >
                        <summary role="button">{{ t('More') }}<AppIcon name="down" /></summary>
                        <div class="reader-more-panel">
                            <button
                                v-if="reply"
                                :disabled="busy"
                                @click="$emit('reply', 'quoted')"
                            >
                                <AppIcon name="file" />{{ t('Quoted reply') }}
                            </button>
                            <button
                                v-if="reply"
                                :disabled="busy"
                                @click="$emit('reply', 'reply')"
                            >
                                <AppIcon name="file" />{{ t('Short reply') }}
                            </button>
                            <button
                                v-if="reply"
                                :disabled="busy"
                                @click="$emit('compose')"
                            >
                                <AppIcon name="edit" />{{ t('New message') }}
                            </button>
                            <button
                                v-if="forwardable"
                                :disabled="busy"
                                @click="$emit('forward')"
                            >
                                <AppIcon name="forward" />{{ t('Forward') }}
                            </button>
                            <button
                                v-if="removable"
                                class="danger"
                                :disabled="busy"
                                @click="$emit('remove')"
                            >
                                <AppIcon name="trash" />{{ t('Delete message') }}
                            </button>
                        </div>
                    </details>
                </div>
            </div>
        </header>
        <div class="reader-correspondents">
            {{ message.fromName }} <AppIcon name="right" /> {{ message.toName }}
        </div>
        <FidoText :text="message.body || ''" />
        <footer class="reader-footer">
            <button
                v-if="reply"
                class="primary"
                :aria-label="t('Reply to message')"
                :disabled="busy"
                @click="$emit('reply', 'quoted')"
            >
                <AppIcon name="reply" />{{ t('Reply') }}
            </button>
            <button
                v-if="navigation"
                class="text-button next-unread"
                :disabled="busy || unreadDisabled"
                @click="$emit('unread')"
            >
                {{ t('Next unread') }}<AppIcon name="right" />
            </button>
        </footer>
    </article>
</template>
