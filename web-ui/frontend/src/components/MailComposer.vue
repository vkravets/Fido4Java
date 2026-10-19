<script setup lang="ts">
import { t } from '../i18n.ts';
import AddressInput from './AddressInput.vue';
import type { MailDraft } from '@rpc/bindings.mjs';
const draft = defineModel<MailDraft>({ required: true });
defineProps<{ addresses: string[]; busy: boolean }>();
defineEmits<{ save: []; send: []; cancel: [] }>();
</script>
<template>
    <form
        class="card mail-composer"
        @submit.prevent="$emit('send')"
    >
        <h2>{{ draft.replyTo ? t('Reply') : t('New netmail') }}</h2>
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
                    }}<input
                        v-model="draft.toName"
                        required
                        maxlength="35"
                /></label>
                <label
                    >{{ t('Recipient address')
                    }}<AddressInput
                        v-model="draft.toAddress"
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
            <label
                >{{ t('Message text')
                }}<textarea
                    v-model="draft.body"
                    rows="14"
                    required
                ></textarea>
            </label>
            <div class="actions">
                <button
                    class="primary"
                    type="submit"
                    :disabled="!addresses.length"
                >
                    {{ t('Add to send queue') }}
                </button>
                <button
                    class="secondary"
                    type="button"
                    @click="$emit('save')"
                >
                    {{ t('Save draft') }}
                </button>
                <button
                    class="text-button"
                    type="button"
                    @click="$emit('cancel')"
                >
                    {{ t('Cancel') }}
                </button>
            </div>
        </fieldset>
    </form>
</template>
