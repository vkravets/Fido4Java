<script setup lang="ts">
import { ref, watch } from 'vue';
import { t } from '../i18n.ts';
import { routingReason } from '../routing.ts';
import AddressInput from './AddressInput.vue';
import type { NetmailRoutePreview } from '@rpc/bindings.mjs';
defineProps<{ peers: string[]; result: NetmailRoutePreview | null; busy: boolean }>();
const emit = defineEmits<{
    preview: [destination: string, source: string, direct: boolean];
    invalidate: [];
}>();
const destination = ref('');
const source = ref('');
const direct = ref(false);
watch([destination, source, direct], () => emit('invalidate'));
</script>
<template>
    <form
        class="card"
        @submit.prevent="$emit('preview', destination, source, direct)"
    >
        <h2>{{ t('Check a route') }}</h2>
        <p>{{ t('Preview only: no message is created or sent.') }}</p>
        <fieldset
            class="routing-fields"
            :disabled="busy"
        >
            <div class="form-grid">
                <label
                    >{{ t('Recipient address')
                    }}<AddressInput
                        v-model="destination"
                        required
                        :include-points="true"
                /></label>
                <label
                    >{{ t('Transit sender')
                    }}<select v-model="source">
                        <option value="">{{ t('Local submission') }}</option>
                        <option
                            v-for="peer in peers"
                            :key="peer"
                            :value="peer"
                        >
                            {{ peer }}
                        </option>
                    </select></label
                >
            </div>
            <label class="check"
                ><input
                    v-model="direct"
                    type="checkbox"
                />{{ t('Require direct delivery') }}</label
            >
            <button
                class="secondary"
                type="submit"
            >
                {{ t('Check route') }}
            </button>
        </fieldset>
        <div
            v-if="result"
            class="routing-result"
            role="status"
        >
            <strong>{{ routingReason(result.reason) }}</strong>
            <p v-if="result.nextHop">{{ t('Next hop') }}: {{ result.nextHop }}</p>
            <p v-if="result.ruleId">{{ t('Rule ID') }}: {{ result.ruleId }}</p>
        </div>
    </form>
</template>
