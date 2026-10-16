<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { api } from '../api.ts';
import type { UserProfile } from '@rpc/bindings.mjs';
import type { AppState } from '../state.ts';
const props = defineProps<{ state: AppState }>();
const accounts = ref<UserProfile[]>([]);
async function refresh() {
    accounts.value = await api.profile.accounts(props.state.session.value);
}
onMounted(() => props.state.run(refresh));
function decide(owner: string, address: string, approve: boolean) {
    props.state.run(async () => {
        await api.profile.decideAka(props.state.session.value, owner, address, approve);
        await refresh();
        props.state.notice.value = approve
            ? 'Адрес подтверждён, аккаунт активирован.'
            : 'Заявка отклонена.';
    });
}
</script>

<template>
    <div class="page-heading">
        <span class="eyebrow">УПРАВЛЕНИЕ НОДОЙ</span>
        <h1>Аккаунты и заявки</h1>
        <p>Проверьте право пользователя на адрес перед подтверждением.</p>
    </div>
    <button
        class="secondary refresh"
        :disabled="state.busy.value"
        @click="state.run(refresh)"
    >
        Обновить
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
                    ? 'Администратор'
                    : account.enabled
                      ? 'Активен'
                      : 'Ожидает одобрения'
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
                        Отклонить</button
                    ><button
                        class="primary"
                        :disabled="state.busy.value"
                        @click="decide(account.login, claim.address, true)"
                    >
                        Подтвердить
                    </button>
                </div>
                <span
                    v-else
                    class="badge"
                    >{{ claim.state === 'APPROVED' ? 'Подтверждён' : 'Отклонён' }}</span
                >
            </li>
        </ul>
        <p
            v-if="!account.claims.length"
            class="muted"
        >
            Нет заявок на адреса
        </p>
    </section>
</template>
