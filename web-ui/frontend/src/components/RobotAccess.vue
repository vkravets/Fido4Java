<script setup lang="ts">
import { onMounted } from 'vue';
import { api, bindings } from '../api.ts';
import { createRobotEditor } from '../robot-rules.ts';
import NotificationBanner from './NotificationBanner.vue';
import type { AppState } from '../state.ts';

const props = defineProps<{ state: AppState }>();
const editor = createRobotEditor(api.ui, () => props.state.session.value, bindings);
const { configuration, editing, pendingDelete, model } = editor;
onMounted(() =>
    props.state.run(async () => {
        await editor.refresh();
        editor.reset();
    })
);

function save() {
    props.state.run(async () => {
        await editor.save();
        props.state.notice.value = 'Правило сохранено. Перезапуск не требуется.';
    });
}
function remove() {
    props.state.run(async () => {
        await editor.remove();
        props.state.notice.value = 'Правило удалено.';
    });
}
</script>

<template>
    <div class="page-heading">
        <span class="eyebrow">УПРАВЛЕНИЕ НОДОЙ</span>
        <h1>Почтовые роботы</h1>
        <p>Доступ линков к AreaFix, FileFix и обмену файлами TIC.</p>
    </div>
    <button
        class="secondary refresh"
        :disabled="state.busy.value"
        @click="state.run(editor.refresh)"
    >
        Обновить
    </button>
    <template v-if="configuration">
        <NotificationBanner
            v-if="configuration.source !== 'database'"
            kind="warning"
            message="Источник правил — файлы, доступен только просмотр. Для редактирования выберите RobotAccessSource=database в conf.d/40-mail-scripts.conf и перезапустите ноду. Перенос существующих правил выполняется явно через --import-robot-access."
        />
        <p
            v-else
            class="muted"
        >
            Источник — БД. Изменения действуют для следующих запросов; принятая ранее работа не
            отменяется.
        </p>
        <section class="card">
            <h2>Правила доступа</h2>
            <p
                v-if="!configuration.rules.length"
                class="muted"
            >
                Правил пока нет. Доступ к роботам закрыт.
            </p>
            <ul class="rows">
                <li
                    v-for="rule in configuration.rules"
                    :key="`${rule.service}:${rule.address}`"
                >
                    <div>
                        <strong>{{ rule.service }} · {{ rule.address }}</strong>
                        <p>
                            {{ rule.masks }} <span v-if="rule.access">· {{ rule.access }}</span> ·
                            {{ rule.enabled ? 'Включено' : 'Отключено' }}
                        </p>
                    </div>
                    <div
                        v-if="configuration.source === 'database'"
                        class="actions"
                    >
                        <button
                            class="secondary"
                            :disabled="state.busy.value"
                            @click="editor.edit(rule)"
                        >
                            Изменить
                        </button>
                        <button
                            class="secondary danger"
                            :disabled="state.busy.value"
                            @click="pendingDelete = rule"
                        >
                            Удалить
                        </button>
                    </div>
                </li>
            </ul>
        </section>
        <section
            v-if="pendingDelete"
            class="card"
            role="alert"
        >
            <p>
                Удалить правило {{ pendingDelete.service }} для {{ pendingDelete.address }}?
                Следующие запросы будут отклонены.
            </p>
            <div class="actions">
                <button
                    class="secondary"
                    :disabled="state.busy.value"
                    @click="pendingDelete = null"
                >
                    Отмена
                </button>
                <button
                    class="primary danger"
                    :disabled="state.busy.value"
                    @click="remove"
                >
                    Удалить правило
                </button>
            </div>
        </section>
        <form
            v-if="configuration.source === 'database'"
            class="card"
            @submit.prevent="save"
        >
            <h2>{{ editing ? 'Изменить правило' : 'Новое правило' }}</h2>
            <NotificationBanner
                v-if="!configuration.peers.length"
                message="Сначала настройте линк станции."
            />
            <fieldset :disabled="state.busy.value || !configuration.peers.length">
                <div class="form-grid">
                    <label
                        >Сервис<select
                            v-model="model.service"
                            :disabled="editing"
                        >
                            <option>AreaFix</option>
                            <option>FileFix</option>
                            <option value="Tic">TIC</option>
                        </select></label
                    >
                    <label
                        >Линк<select
                            v-model="model.address"
                            :disabled="editing"
                            required
                        >
                            <option
                                v-if="editing && !configuration.peers.includes(model.address)"
                                :value="model.address"
                            >
                                {{ model.address }} (удалён из конфигурации)
                            </option>
                            <option
                                v-for="peer in configuration.peers"
                                :key="peer"
                            >
                                {{ peer }}
                            </option>
                        </select></label
                    >
                    <label
                        >Маски областей<input
                            v-model="model.masks"
                            required
                            maxlength="4096"
                            placeholder="test.*,local.news"
                        /><small
                            >* — любые символы, ? — один символ; несколько масок через
                            запятую.</small
                        ></label
                    >
                    <label v-if="model.service === 'AreaFix'"
                        >Права<select v-model="model.access">
                            <option value="READ">READ — чтение</option>
                            <option value="BOTH">BOTH — чтение и запись</option>
                        </select></label
                    >
                    <label
                        >Пароль<input
                            v-model="model.password"
                            type="password"
                            autocomplete="new-password"
                            :required="!editing"
                            maxlength="256"
                        /><small>{{
                            editing
                                ? 'Оставьте пустым, чтобы сохранить текущий пароль.'
                                : 'Для нового правила задайте отдельный пароль робота.'
                        }}</small></label
                    >
                </div>
                <p
                    v-if="model.service === 'Tic'"
                    class="muted"
                >
                    TIC использует общий пароль при передаче файлов. Пароль хранится в БД для
                    исходящих TIC, но не возвращается в браузер.
                </p>
                <label
                    ><input
                        v-model="model.enabled"
                        type="checkbox"
                    />
                    Правило включено</label
                >
                <footer class="actions">
                    <button
                        type="button"
                        class="secondary"
                        @click="editor.reset"
                    >
                        {{ editing ? 'Отмена' : 'Очистить' }}
                    </button>
                    <button class="primary">Сохранить правило</button>
                </footer>
            </fieldset>
        </form>
    </template>
</template>

<style scoped>
fieldset {
    border: 0;
    padding: 0;
    margin: 0;
    min-width: 0;
}
.rows li {
    gap: 16px;
    flex-wrap: wrap;
}
.rows li p {
    overflow-wrap: anywhere;
}
</style>
