<script setup lang="ts">
import { t } from '../i18n.ts';
import { inject, onMounted, ref } from 'vue';
import { accountModerationClientKey } from '../station-clients.ts';
import { api } from '../api.ts';
import type { UserProfile } from '@rpc/bindings.mjs';
import type { AppState } from '../state.ts';
const props = defineProps<{ state: AppState }>();
const client = inject(accountModerationClientKey, api.profile);
const accounts = ref<UserProfile[]>([]);
async function refresh() {
    accounts.value = await client.accounts(props.state.session.value);
}
onMounted(() => props.state.run(refresh));
function decide(owner: string, address: string, approve: boolean) {
    props.state.run(async () => {
        await client.decideAka(props.state.session.value, owner, address, approve);
        await refresh();
        props.state.notice.value = approve
            ? t('Address approved and account activated.')
            : t('Request rejected.');
    });
}
</script>

<template>
    <div class="page-heading">
        <span class="eyebrow">{{ t('NODE MANAGEMENT') }}</span>
        <h1>{{ t('Accounts and requests') }}</h1>
        <p>{{ t('Verify that the user owns the address before approving it.') }}</p>
    </div>
    <button
        class="secondary refresh"
        :disabled="state.busy.value"
        @click="state.run(refresh)"
    >
        {{ t('Refresh') }}
    </button>
    <section
        v-for="account in accounts"
        :key="account.login"
        class="card account-row"
    >
        <div class="section-title">
            <h2>{{ account.login }}</h2>
            <span class="badge">{{
                account.role === 'ADMIN'
                    ? t('Administrator')
                    : account.enabled
                      ? t('Active')
                      : t('Awaiting approval')
            }}</span>
        </div>
        <ul class="rows">
            <li
                v-for="claim in account.claims"
                :key="claim.address"
            >
                <span class="mono">{{ claim.address }}</span>
                <div
                    v-if="claim.state === 'PENDING'"
                    class="actions"
                >
                    <button
                        class="secondary danger"
                        :disabled="state.busy.value"
                        @click="decide(account.login, claim.address, false)"
                    >
                        {{ t('Reject') }}</button
                    ><button
                        class="primary"
                        :disabled="state.busy.value"
                        @click="decide(account.login, claim.address, true)"
                    >
                        {{ t('Approve') }}
                    </button>
                </div>
                <span
                    v-else
                    class="badge"
                    >{{ claim.state === 'APPROVED' ? t('Approved') : t('Rejected') }}</span
                >
            </li>
        </ul>
        <p
            v-if="!account.claims.length"
            class="muted"
        >
            {{ t('No address requests') }}
        </p>
    </section>
</template>
