<script setup lang="ts">
import type { MailLinkConfiguration, NodeConfiguration } from '@rpc/bindings.mjs';
import NodeForm from './NodeForm.vue';
import MailLinksEditor from './MailLinksEditor.vue';
import { t } from '../i18n.ts';
defineProps<{ model: NodeConfiguration; links: MailLinkConfiguration[]; busy: boolean }>();
defineEmits<{ add: []; remove: [index: number] }>();
</script>

<template>
    <NodeForm
        :model="model"
        initial
        :show-link="false"
    >
        <template
            v-if="$slots.lookup"
            #lookup
            ><slot name="lookup"
        /></template>
    </NodeForm>
    <h3>{{ t('Uplinks') }}</h3>
    <p>
        {{
            t(
                'Add one or more uplinks. Each link has its own connection and packet passwords. Local AKAs follow the station addresses during setup.'
            )
        }}
    </p>
    <MailLinksEditor
        :links="links"
        :existing-count="0"
        :busy="busy"
        initial
        @add="$emit('add')"
        @remove="$emit('remove', $event)"
    />
</template>
