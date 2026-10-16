<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { api, bindings } from '../api.ts';
import NodeForm from './NodeForm.vue';
import type { AppState } from '../state.ts';
const props = defineProps<{ state: AppState }>();
const step = ref(0);
const done = ref(false);
const confirmation = ref('');
const model = reactive(new bindings.NodeConfiguration());
const token = new URLSearchParams(location.hash.slice(1)).get('setup') || '';
const steps = ['Администратор', 'Станция', 'Нодлист', 'Подтверждение'];
onMounted(() =>
    props.state.run(async () => Object.assign(model, await api.settings.current(token)))
);

function next() {
    props.state.run(async () => {
        if (
            step.value === 0 &&
            (model.password !== confirmation.value || model.password.length < 12)
        ) {
            throw new Error('Пароли должны совпадать и содержать не менее 12 символов.');
        }
        step.value++;
    });
}

function lookup(download: boolean) {
    props.state.run(async () => {
        const record = await api.settings.lookup(
            token,
            model.address,
            download ? model.nodeListUrl : '',
            model.charset
        );
        if (!record.found) throw new Error('Адрес не найден. Можно заполнить данные вручную.');
        if (record.boss) {
            props.state.notice.value =
                'Найден босс поинта. Заполните свои данные вручную: запись босса не подтверждает личность поинта.';
            return;
        }
        for (const key of ['system', 'sysop', 'location'] as const)
            if (record[key]) model[key] = record[key];
        props.state.notice.value = `Данные найдены: ${record.source}. Проверьте их перед сохранением.`;
    });
}

function save() {
    props.state.run(async () => {
        await api.settings.save(token, new bindings.NodeConfiguration(model));
        model.password = confirmation.value = model.linkPassword = '';
        history.replaceState(null, '', location.pathname);
        done.value = true;
    });
}
</script>

<template>
    <main class="setup-layout">
        <div class="page-heading">
            <span class="eyebrow">ПЕРВЫЙ ЗАПУСК</span>
            <h1>Настроим вашу станцию</h1>
            <p>Почтовые службы запустятся после завершения настройки.</p>
        </div>
        <section
            v-if="done"
            class="card"
        >
            <h2>Всё готово</h2>
            <p>
                Настройки сохранены. Сервер запускает почтовые службы. Откройте настроенный адрес
                интерфейса и войдите.
            </p>
            <a :href="model.browserOrigin">Открыть JFtn →</a>
        </section>
        <template v-else-if="model.aliases">
            <ol class="steps">
                <li
                    v-for="(label, index) in steps"
                    :key="label"
                    :class="{ active: index === step, completed: index < step }"
                >
                    <span>{{ index + 1 }}</span
                    >{{ label }}
                </li>
            </ol>
            <form
                class="card wizard"
                @submit.prevent="step < 3 ? next() : save()"
            >
                <template v-if="step === 0">
                    <h2>Ваш аккаунт администратора</h2>
                    <p>Этот пароль относится к интерфейсу JFtn и отличается от пароля binkp.</p>
                    <label
                        >Логин<input
                            v-model="model.login"
                            required
                            pattern="[A-Za-z0-9_.@-]+"
                            maxlength="100"
                            autocomplete="username"
                    /></label>
                    <label
                        >Пароль<input
                            v-model="model.password"
                            required
                            minlength="12"
                            maxlength="1024"
                            type="password"
                            autocomplete="new-password"
                    /></label>
                    <label
                        >Повторите пароль<input
                            v-model="confirmation"
                            required
                            minlength="12"
                            type="password"
                            autocomplete="new-password"
                    /></label>
                </template>
                <template v-if="step === 1"
                    ><h2>Станция и связь</h2>
                    <NodeForm
                        :model="model"
                        initial
                    >
                        <template #lookup>
                            <p>Введите основной АКА, чтобы заполнить данные станции по нодлисту.</p>
                            <div class="actions">
                                <button
                                    type="button"
                                    class="secondary"
                                    :disabled="state.busy.value || !model.address"
                                    @click="lookup(false)"
                                >
                                    Найти в каталоге
                                </button>
                                <button
                                    type="button"
                                    class="secondary"
                                    :disabled="
                                        state.busy.value || !model.address || !model.nodeListUrl
                                    "
                                    @click="lookup(true)"
                                >
                                    Скачать и заполнить
                                </button>
                            </div>
                        </template>
                    </NodeForm>
                </template>
                <template v-if="step === 2">
                    <h2>Проверим данные по нодлисту</h2>
                    <p>
                        Справочник помогает заполнить станцию, но не подтверждает право владения
                        адресом. Этот шаг можно пропустить.
                    </p>
                    <label
                        >Адрес<input
                            v-model="model.address"
                            required
                    /></label>
                    <label
                        >HTTP-источник<input
                            v-model="model.nodeListUrl"
                            type="url"
                    /></label>
                    <label
                        >Кодировка<input
                            v-model="model.charset"
                            required
                    /></label>
                    <div class="actions">
                        <button
                            type="button"
                            class="secondary"
                            :disabled="state.busy.value"
                            @click="lookup(false)"
                        >
                            Найти в каталоге</button
                        ><button
                            type="button"
                            class="secondary"
                            :disabled="state.busy.value || !model.nodeListUrl"
                            @click="lookup(true)"
                        >
                            Скачать и проверить
                        </button>
                    </div>
                </template>
                <template v-if="step === 3">
                    <h2>Готово к запуску</h2>
                    <dl>
                        <dt>Станция</dt>
                        <dd>{{ model.system }} · {{ model.address }}</dd>
                        <dt>Сисоп</dt>
                        <dd>{{ model.sysop }}</dd>
                        <dt>Линк</dt>
                        <dd>{{ model.uplink }} · {{ model.host }}:{{ model.port }}</dd>
                        <dt>Почта</dt>
                        <dd>{{ model.storage }}</dd>
                        <dt>Администратор</dt>
                        <dd>{{ model.login }}</dd>
                    </dl>
                    <p>
                        Регистрация поинтов будет открыта. Каждую заявку должен одобрить
                        администратор.
                    </p>
                </template>
                <footer class="actions">
                    <button
                        v-if="step > 0"
                        class="secondary"
                        type="button"
                        :disabled="state.busy.value"
                        @click="step--"
                    >
                        Назад</button
                    ><button
                        class="primary"
                        :disabled="state.busy.value"
                    >
                        {{ step === 3 ? 'Сохранить и запустить' : 'Продолжить →' }}
                    </button>
                </footer>
            </form>
        </template>
        <p v-else>Откройте полную ссылку настройки из консоли сервера.</p>
    </main>
</template>
