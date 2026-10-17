<script setup lang="ts">
import { computed } from 'vue';
import { t } from '../i18n.ts';
import type { MailLinkConfiguration } from '@rpc/bindings.mjs';

const props = defineProps<{
    model: MailLinkConfiguration;
    existing: boolean;
    stationAkas?: boolean;
}>();
const addresses = computed({
    get: () => props.model.localAddresses.join(' '),
    set: (value: string) => {
        props.model.localAddresses = value.trim().split(/\s+/).filter(Boolean);
    }
});
</script>

<template>
    <div class="form-grid">
        <label
            >{{ t('Link address')
            }}<input
                v-model="model.address"
                :readonly="existing"
                required
                placeholder="1:500/101"
        /></label>
        <label
            >{{ t('Local AKAs for this link')
            }}<input
                v-model="addresses"
                :readonly="stationAkas"
                required
                placeholder="1:500/100 1:500/100.1"
        /></label>
        <label
            >{{ t('binkp server')
            }}<input
                v-model="model.host"
                :aria-label="t('binkp server')"
                placeholder="binkp.example.org"
            /><small>{{ t('Leave empty for incoming-only links.') }}</small></label
        >
        <label
            >{{ t('Port')
            }}<input
                v-model.number="model.port"
                type="number"
                min="1"
                max="65535"
                required
        /></label>
        <label
            >{{ t('binkp session password')
            }}<input
                v-model="model.sessionPassword"
                :aria-label="t('binkp session password')"
                type="password"
                autocomplete="new-password"
                :placeholder="
                    existing
                        ? t('Leave empty to keep the current password')
                        : t('Or − for an unprotected link')
                "
            /><small>{{ t('Use - for an unprotected binkp session.') }}</small></label
        >
        <label
            >{{ t('PKT password mode')
            }}<select
                v-model="model.packetPasswordMode"
                :aria-label="t('PKT password mode')"
            >
                <option value="INHERIT">{{ t('Use binkp password') }}</option>
                <option value="SEPARATE">{{ t('Separate PKT password') }}</option>
                <option value="UNPROTECTED">{{ t('Unprotected packets') }}</option>
            </select></label
        >
        <label
            v-if="model.packetPasswordMode === 'SEPARATE'"
            class="wide"
            >{{ t('Separate PKT password')
            }}<input
                v-model="model.packetPassword"
                :aria-label="t('Separate PKT password')"
                type="password"
                autocomplete="new-password"
                maxlength="8"
                :required="!existing"
                :placeholder="existing ? t('Leave empty to keep the current password') : ''"
            /><small>{{
                t(
                    'At most 8 ASCII characters. Must match the packet password configured by your peer.'
                )
            }}</small></label
        >
        <p
            v-if="model.packetPasswordMode === 'UNPROTECTED'"
            class="wide"
        >
            {{
                t(
                    'Incoming packets for this link must have an empty PKT password. Use only by agreement with your peer.'
                )
            }}
        </p>

    </div>
</template>
