<script setup lang="ts">
import { t } from '../i18n.ts';
import { routingMasks } from '../routing.ts';
import type { NetmailRoutingPolicy } from '@rpc/bindings.mjs';
const policy = defineModel<NetmailRoutingPolicy>({ required: true });
defineProps<{ peers: string[]; busy: boolean }>();
defineEmits<{ save: [] }>();
function masks(event: Event): string[] {
    return routingMasks((event.target as HTMLTextAreaElement).value);
}
</script>
<template>
    <form
        class="card"
        @submit.prevent="$emit('save')"
    >
        <h2>{{ t('Routing policy') }}</h2>
        <fieldset
            class="routing-fields"
            :disabled="busy"
        >
            <div class="form-grid">
                <label
                    >{{ t('Rule source')
                    }}<select v-model="policy.mode">
                        <option value="config">{{ t('Configuration file') }}</option>
                        <option value="database">{{ t('Database') }}</option>
                    </select></label
                >
                <label
                    >{{ t('Default route')
                    }}<select v-model="policy.defaultHop">
                        <option value="">{{ t('No default route') }}</option>
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
            <p class="muted">{{ t('Changing the rule source does not copy rules.') }}</p>
            <label class="check"
                ><input
                    v-model="policy.allowTransit"
                    type="checkbox"
                />{{ t('Allow netmail transit') }}</label
            >
            <p class="muted">
                {{ t('Empty transit masks allow every configured link when transit is enabled.') }}
            </p>
            <div class="form-grid">
                <label
                    >{{ t('Transit sender masks (one per line)')
                    }}<textarea
                        rows="3"
                        :value="policy.transitFrom.join('\n')"
                        @change="policy.transitFrom = masks($event)"
                    ></textarea>
                </label>
                <label
                    >{{ t('Transit next-hop masks (one per line)')
                    }}<textarea
                        rows="3"
                        :value="policy.transitTo.join('\n')"
                        @change="policy.transitTo = masks($event)"
                    ></textarea>
                </label>
            </div>
            <button
                class="primary"
                type="submit"
            >
                {{ t('Save routing policy') }}
            </button>
        </fieldset>
    </form>
</template>
