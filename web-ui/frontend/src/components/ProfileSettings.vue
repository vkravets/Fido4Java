<script setup lang="ts">
import { computed, inject, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import type { DirectoryNode } from '@rpc/bindings.mjs';
import type { AppState } from '../state.ts';
import { api } from '../api.ts';
import { createDirectorySearch, directorySearchKey } from '../directory.ts';
import { saveDisplayNameKey } from '../profile-name.ts';
import { t } from '../i18n.ts';
import SettingsSectionHeader from './SettingsSectionHeader.vue';
import AppIcon from './AppIcon.vue';

const props = defineProps<{ state: AppState }>();
const saveName = inject(saveDisplayNameKey, (session, name) =>
    api.profile.saveDisplayName(session, name)
);
const search = inject(directorySearchKey, (query, offset, limit, points) =>
    api.settings.searchDirectory(query, offset, limit, points)
);
const profile = computed(() => props.state.profile.value!);
const saved = computed(() => profile.value.displayName || profile.value.login);
const name = ref(saved.value);
const address = ref(
    profile.value.allowedAkas.includes(profile.value.defaultAka)
        ? profile.value.defaultAka
        : profile.value.allowedAkas[0] || ''
);
const dirty = computed(() => name.value !== saved.value);
const directory = createDirectorySearch(search, 100);
const searched = ref(false);
const matches = computed(() =>
    directory.items.value.filter((node) => node.address === address.value && node.sysop.trim())
);
watch(saved, (value) => {
    name.value = value;
});
watch(address, () => {
    directory.clear();
    searched.value = false;
});
function canLeave() {
    return !dirty.value || window.confirm(t('Unsaved profile changes. Discard changes?'));
}
defineExpose({ canLeave });
function beforeUnload(event: BeforeUnloadEvent) {
    if (dirty.value) event.preventDefault();
}
onMounted(() => window.addEventListener('beforeunload', beforeUnload));
onBeforeUnmount(() => {
    directory.dispose();
    window.removeEventListener('beforeunload', beforeUnload);
});
async function lookup() {
    searched.value = false;
    await props.state.run(async () => {
        await directory.load(address.value);
        searched.value = true;
    });
}
function choose(node: DirectoryNode) {
    name.value = node.sysop.trim();
}
async function save() {
    await props.state.run(async () => {
        await saveName(props.state.session.value, name.value);
        await props.state.refreshProfile();
        name.value = saved.value;
        props.state.notice.value = t('Settings saved.');
    });
}
</script>
<template>
    <section class="card profile-settings">
        <SettingsSectionHeader :title="t('Profile')">
            <template #actions>
                <button
                    type="submit"
                    form="profile-name-form"
                    class="primary settings-icon-action"
                    :disabled="state.busy.value || !dirty"
                    :title="t('Save preferences')"
                    :aria-label="t('Save preferences')"
                >
                    <AppIcon name="save" />
                </button>
            </template>
        </SettingsSectionHeader>
        <dl>
            <dt>{{ t('Username') }}</dt>
            <dd>{{ profile.login }}</dd>
            <dt>{{ t('Access') }}</dt>
            <dd>{{ profile.role === 'ADMIN' ? t('Administrator') : t('Point') }}</dd>
            <dt>{{ t('Default AKA') }}</dt>
            <dd>{{ profile.defaultAka || t('Not selected') }}</dd>
        </dl>
        <form
            id="profile-name-form"
            @submit.prevent="save"
        >
            <label
                >{{ t('Display name')
                }}<input
                    v-model="name"
                    maxlength="80"
                    :placeholder="profile.login"
                    :disabled="state.busy.value"
            /></label>
            <p class="muted">
                {{
                    t(
                        'Shown throughout the interface and used as the sender name and @cname in new messages.'
                    )
                }}
            </p>
        </form>
        <div class="profile-directory">
            <label
                >{{ t('AKA for name lookup')
                }}<select
                    v-model="address"
                    :disabled="state.busy.value || !profile.allowedAkas.length"
                >
                    <option
                        v-for="aka in profile.allowedAkas"
                        :key="aka"
                        :value="aka"
                    >
                        {{ aka }}
                    </option>
                </select></label
            >
            <button
                type="button"
                class="secondary"
                :disabled="state.busy.value || !address"
                @click="lookup"
            >
                {{ t('Find name in nodelist / pointlist') }}
            </button>
        </div>
        <ul
            v-if="matches.length"
            class="rows profile-name-matches"
        >
            <li
                v-for="node in matches"
                :key="`${node.source}:${node.address}`"
            >
                <button
                    type="button"
                    class="secondary"
                    :disabled="state.busy.value"
                    @click="choose(node)"
                >
                    {{ node.sysop }} — {{ node.source }}
                </button>
            </li>
        </ul>
        <p
            v-else-if="searched"
            class="muted"
        >
            {{ t('No name found for this AKA. Enter a display name manually.') }}
        </p>
        <p class="hint">{{ t('Each point address requires administrator approval.') }}</p>
    </section>
</template>
<style scoped>
.profile-settings form {
    max-width: 560px;
}
.profile-directory {
    display: flex;
    align-items: end;
    flex-wrap: wrap;
    gap: 12px;
    margin-top: 16px;
}
.profile-directory label {
    margin: 0;
    min-width: 180px;
}
.profile-name-matches button {
    text-align: left;
}
</style>
