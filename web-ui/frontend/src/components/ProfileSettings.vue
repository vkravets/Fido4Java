<script setup lang="ts">
import { computed, inject, nextTick, onBeforeUnmount, onMounted, ref, useId, watch } from 'vue';
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
const searchOpen = ref(false);
const picker = ref<HTMLElement>();
const searchButton = ref<HTMLButtonElement>();
const nameInput = ref<HTMLInputElement>();
const akaSelect = ref<HTMLSelectElement>();
const id = useId();
let lookupVersion = 0;
const matches = computed(() =>
    directory.items.value.filter((node) => node.address === address.value && node.sysop.trim())
);
watch(saved, (value) => {
    name.value = value;
});
watch(address, () => {
    directory.clear();
    searched.value = false;
    if (searchOpen.value) void lookup();
});
function closeSearch() {
    searchOpen.value = false;
    lookupVersion++;
    directory.clear();
    searched.value = false;
}
function outsidePointer(event: PointerEvent) {
    if (!searchOpen.value) return;
    if (event.target instanceof Node && !picker.value?.contains(event.target)) closeSearch();
}
function escapeSearch(event: KeyboardEvent) {
    if (!searchOpen.value) return;
    event.preventDefault();
    event.stopPropagation();
    closeSearch();
    searchButton.value?.focus({ preventScroll: true });
}
async function toggleSearch() {
    if (searchOpen.value) {
        closeSearch();
        return;
    }
    searchOpen.value = true;
    void lookup();
    await nextTick();
    akaSelect.value?.focus({ preventScroll: true });
}
function canLeave() {
    return !dirty.value || window.confirm(t('Unsaved profile changes. Discard changes?'));
}
defineExpose({ canLeave });
function beforeUnload(event: BeforeUnloadEvent) {
    if (dirty.value) event.preventDefault();
}
onMounted(() => {
    window.addEventListener('beforeunload', beforeUnload);
    document.addEventListener('pointerdown', outsidePointer);
});
onBeforeUnmount(() => {
    closeSearch();
    directory.dispose();
    window.removeEventListener('beforeunload', beforeUnload);
    document.removeEventListener('pointerdown', outsidePointer);
});
async function lookup() {
    const version = ++lookupVersion;
    searched.value = false;
    await props.state.run(
        async () => {
            await directory.load(address.value);
            if (version === lookupVersion && searchOpen.value) searched.value = true;
        },
        { background: true }
    );
}
function choose(node: DirectoryNode) {
    name.value = node.sysop.trim();
    closeSearch();
    void nextTick(() => nameInput.value?.focus({ preventScroll: true }));
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
            <div
                ref="picker"
                class="profile-name-picker"
                @keydown.esc="escapeSearch"
            >
                <label :for="`${id}-name`">{{ t('Display name') }}</label>
                <div class="profile-name-row">
                    <input
                        :id="`${id}-name`"
                        ref="nameInput"
                        v-model="name"
                        maxlength="80"
                        :placeholder="profile.login"
                        :disabled="state.busy.value"
                        :aria-describedby="`${id}-hint`"
                    />
                    <button
                        ref="searchButton"
                        type="button"
                        class="profile-name-search settings-icon-action"
                        :disabled="state.busy.value || !address"
                        :title="t('Find name in nodelist / pointlist')"
                        :aria-label="t('Find name in nodelist / pointlist')"
                        :aria-expanded="searchOpen"
                        :aria-controls="`${id}-directory`"
                        @click="toggleSearch"
                    >
                        <AppIcon name="fileSearch" />
                    </button>
                </div>
                <section
                    v-if="searchOpen"
                    :id="`${id}-directory`"
                    class="profile-directory"
                    role="region"
                    :aria-label="t('Name from lists')"
                    data-shortcuts-disabled
                >
                    <div class="profile-directory-heading">
                        <strong>{{ t('Name from lists') }}</strong>
                        <button
                            type="button"
                            class="secondary settings-icon-action"
                            :title="t('Close name lookup')"
                            :aria-label="t('Close name lookup')"
                            @click="
                                closeSearch();
                                searchButton?.focus({ preventScroll: true });
                            "
                        >
                            <AppIcon name="close" />
                        </button>
                    </div>
                    <label
                        >{{ t('AKA for name lookup')
                        }}<select
                            ref="akaSelect"
                            v-model="address"
                            :disabled="
                                state.busy.value ||
                                directory.busy.value ||
                                !profile.allowedAkas.length
                            "
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
                    <p
                        v-if="directory.busy.value"
                        role="status"
                        class="muted"
                    >
                        {{ t('Searching directory…') }}
                    </p>
                    <ul
                        v-else-if="matches.length"
                        class="profile-name-matches"
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
                                <span>{{ node.sysop }}</span
                                ><small>{{ node.address }} · {{ node.source }}</small>
                            </button>
                        </li>
                    </ul>
                    <p
                        v-else-if="searched"
                        class="muted"
                        role="status"
                    >
                        {{ t('No name found for this AKA. Enter a display name manually.') }}
                    </p>
                </section>
            </div>
            <p
                :id="`${id}-hint`"
                class="muted profile-name-hint"
            >
                {{ t('Name for the interface and @cname') }}
            </p>
        </form>
        <p class="hint">{{ t('Each point address requires administrator approval.') }}</p>
    </section>
</template>
<style scoped>
.profile-name-picker {
    position: relative;
    width: min(100%, 364px);
}
.profile-name-picker > label {
    margin-bottom: 6px;
}
.profile-name-row {
    display: flex;
    align-items: center;
    gap: 8px;
}
.profile-name-row input {
    flex: 1;
    min-width: 0;
}
.profile-name-search {
    flex-shrink: 0;
    background: light-dark(#ed9b2a, #f0a443);
    border-color: light-dark(#c27a18, #d58c28);
    color: #302109;
}
.profile-name-search:not(:disabled):hover {
    background: light-dark(#f1ac48, #f6b557);
}
.profile-name-hint {
    margin-top: 7px;
}
.profile-directory {
    position: absolute;
    top: calc(100% + 6px);
    inset-inline: 0;
    width: 100%;
    box-sizing: border-box;
    z-index: 15;
    padding: 12px;
    border: 1px solid var(--line);
    border-radius: 6px;
    background: var(--surface-raised);
    box-shadow: var(--shadow);
}
.profile-directory-heading {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
    margin-bottom: 10px;
}
.profile-directory label {
    margin-bottom: 10px;
}
.profile-name-matches {
    list-style: none;
    padding: 0;
    margin: 0;
    display: grid;
    gap: 6px;
}
.profile-name-matches button {
    text-align: left;
    width: 100%;
    display: flex;
    flex-direction: column;
    align-items: start;
    gap: 4px;
    overflow-wrap: anywhere;
}
.profile-name-matches small {
    color: var(--muted);
}
.profile-directory p {
    margin-bottom: 0;
}
</style>
