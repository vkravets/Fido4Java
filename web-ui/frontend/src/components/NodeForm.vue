<script setup lang="ts">
import { computed } from 'vue';
import type { NodeConfiguration } from '@rpc/bindings.mjs';
const props = defineProps<{ model: NodeConfiguration; initial: boolean }>();
const aliases = computed({
    get: () => props.model.aliases.join(' '),
    set: (value) => {
        props.model.aliases = value.trim().split(/\s+/).filter(Boolean);
    }
});
</script>

<template>
    <div class="form-grid">
        <label
            >Основной АКА<input
                v-model="model.address"
                placeholder="1:500/100"
                required
        /></label>
        <label
            >Дополнительные АКА<input
                v-model="aliases"
                placeholder="Через пробел"
        /></label>
        <div
            v-if="$slots.lookup"
            class="wide"
        >
            <slot name="lookup" />
        </div>
        <label
            >Имя станции<input
                v-model="model.system"
                required
                maxlength="200"
        /></label>
        <label
            >Сисоп<input
                v-model="model.sysop"
                required
                maxlength="200"
        /></label>
        <label class="wide"
            >Местоположение<input
                v-model="model.location"
                maxlength="200"
        /></label>
        <label
            >Аплинк / линк<input
                v-model="model.uplink"
                placeholder="1:500/101"
                required
        /></label>
        <label
            >Сервер binkp<input
                v-model="model.host"
                placeholder="binkp.example.org"
                required
        /></label>
        <label
            >Порт<input
                v-model.number="model.port"
                type="number"
                required
                min="1"
                max="65535"
        /></label>
        <label
            >Пароль линка<input
                v-model="model.linkPassword"
                type="password"
                autocomplete="new-password"
                :placeholder="initial ? 'Или − для открытого линка' : 'Пусто — сохранить текущий'"
        /></label>
        <label class="wide"
            >Каталог почты<input
                v-model="model.storage"
                :readonly="!initial"
                required
            /><small v-if="!initial"
                >Перенос существующего хранилища выполняется отдельно.</small
            ></label
        >
        <label class="wide"
            >Адрес веб-интерфейса (origin)<input
                v-model="model.browserOrigin"
                placeholder="http://localhost:9091"
                required
            /><small>Для удалённого доступа используйте HTTPS через обратный прокси.</small></label
        >
        <label class="wide">
            <span
                ><input
                    v-model="model.preserveReceivedFiles"
                    type="checkbox"
                />
                Сохранять оригиналы полученных файлов</span
            >
            <small
                >Включено по умолчанию. Оригиналы PKT, бандлов, TIC и других принятых файлов
                сохраняются в архиве без автоматической очистки. Изменение применяется после
                перезапуска.</small
            >
        </label>
        <label class="wide"
            >HTTP-источник нодлиста<input
                v-model="model.nodeListUrl"
                type="url"
            /><small
                >Можно указать своё зеркало. Загрузка выполняется по кнопке, не при каждом
                старте.</small
            ></label
        >
        <label
            >Кодировка нодлиста<input
                v-model="model.charset"
                required
                placeholder="CP866"
        /></label>
    </div>
</template>
