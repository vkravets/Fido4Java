<script setup lang="ts">
import { t } from '../i18n.ts';
import type { MessageKey } from '../i18n.ts';
import SettingsPageHeader from './SettingsPageHeader.vue';
import ConfigurationReloadButton from './ConfigurationReloadButton.vue';
withDefaults(defineProps<{ busy: boolean; section?: MessageKey }>(), { section: 'General' });
defineEmits<{ reload: [] }>();
</script>
<template>
    <SettingsPageHeader
        :context="t('Station')"
        :title="t(section)"
    >
        <ConfigurationReloadButton
            :busy="busy"
            @reload="$emit('reload')"
        />
    </SettingsPageHeader>
</template>
