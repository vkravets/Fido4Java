<script setup lang="ts">
import { t } from '../i18n.ts';
import type { TosserConfiguration } from '@rpc/bindings.mjs';
defineProps<{ model: TosserConfiguration }>();
</script>

<template>
    <div class="form-grid">
        <label
            >{{ t('Processing interval (seconds)')
            }}<input
                v-model.number="model.intervalSeconds"
                type="number"
                min="1"
                max="86400"
                required
        /></label>
        <label class="wide"
            ><span
                ><input
                    v-model="model.deleteProcessedFiles"
                    type="checkbox"
                />
                {{ t('Remove successfully processed files from inbound') }}</span
            ><small>{{
                t(
                    'Original received files are retained separately when archiving is enabled in station settings. Rejected files are not accepted by this option.'
                )
            }}</small></label
        >
        <label class="wide"
            ><span
                ><input
                    v-model="model.enforceEchoAccess"
                    type="checkbox"
                />
                {{ t('Check echomail permissions for each link') }}</span
            ></label
        >
        <label class="wide"
            ><span
                ><input
                    v-model="model.autoCreateEcho"
                    type="checkbox"
                />
                {{ t('Create unknown echo areas automatically') }}</span
            ><small>{{
                t(
                    'Echo access checks still apply. Disable automatic creation unless you trust the configured peers.'
                )
            }}</small></label
        >
    </div>
</template>
