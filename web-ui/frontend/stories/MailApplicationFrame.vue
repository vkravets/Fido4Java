<script setup lang="ts">
import { ref } from 'vue';
import ApplicationHeader from '../src/components/ApplicationHeader.vue';
import Sidebar from '../src/components/Sidebar.vue';
import { profile } from './fixtures.ts';
const props = defineProps<{ theme: 'light' | 'dark'; page: string; initialCollapsed?: boolean }>();
const collapsed = ref(!!props.initialCollapsed);
const open = ref(false);
const account = profile('POINT');
</script>
<template>
    <ApplicationHeader
        :profile="account"
        :theme="theme"
        :busy="false"
        :menu-open="open"
        @menu="open = !open"
    />
    <div
        class="workspace"
        :class="{ 'navigation-collapsed': collapsed, 'navigation-open': open }"
    >
        <Sidebar
            :profile="account"
            :page="page"
            :collapsed="collapsed"
            @collapse="collapsed = !collapsed"
        />
        <main class="content mail-content"><slot /></main>
    </div>
</template>
