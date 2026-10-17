<script setup lang="ts">
import { t } from '../i18n.ts';
import { computed } from 'vue';
import type { NodeConfiguration } from '@rpc/bindings.mjs';
const props = defineProps<{ model: NodeConfiguration; initial: boolean }>();
const aliases = computed({
    get: () => props.model.aliases.join(' '),
    set: (value) => {
        props.model.aliases = value.trim().split(/\s+/).filter(Boolean);
    }
});
</script>

<template>
    <div class="form-grid">
        <label
            >{{ t('Default AKA')
            }}<input
                v-model="model.address"
                placeholder="1:500/100"
                required
        /></label>
        <label
            >{{ t('Additional AKAs')
            }}<input
                v-model="aliases"
                :placeholder="t('Space-separated')"
        /></label>
        <div
            v-if="$slots.lookup"
            class="wide"
        >
            <slot name="lookup" />
        </div>
        <label
            >{{ t('Station name')
            }}<input
                v-model="model.system"
                required
                maxlength="200"
        /></label>
        <label
            >{{ t('Sysop')
            }}<input
                v-model="model.sysop"
                required
                maxlength="200"
        /></label>
        <label class="wide"
            >{{ t('Location')
            }}<input
                v-model="model.location"
                maxlength="200"
        /></label>
        <label class="wide"
            >{{ t('Mail directory')
            }}<input
                v-model="model.storage"
                :readonly="!initial"
                required
            /><small v-if="!initial">{{
                t('Existing storage must be migrated separately.')
            }}</small></label
        >
        <label class="wide"
            >{{ t('Web interface address (origin)')
            }}<input
                v-model="model.browserOrigin"
                placeholder="http://localhost:9091"
                required
            /><small>{{ t('Use HTTPS through a reverse proxy for remote access.') }}</small></label
        >
        <label class="wide">
            <span
                ><input
                    v-model="model.preserveReceivedFiles"
                    type="checkbox"
                />
                {{ t('Keep original received files') }}</span
            >
            <small>{{
                t(
                    'Enabled by default. Original PKT files, bundles, TIC files and other received files are archived without automatic cleanup. Changes apply immediately after saving.'
                )
            }}</small>
        </label>
        <label class="wide"
            >{{ t('Nodelist HTTP source')
            }}<input
                v-model="model.nodeListUrl"
                type="url"
            /><small>{{
                t('You can use your own mirror. Downloads start on request, not at every startup.')
            }}</small></label
        >
        <label
            >{{ t('Nodelist encoding')
            }}<input
                v-model="model.charset"
                required
                placeholder="CP866"
        /></label>
    </div>
</template>
