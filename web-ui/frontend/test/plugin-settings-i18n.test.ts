import { test } from 'node:test';
import assert from 'node:assert/strict';
import { locale } from '../src/i18n.ts';
import { pluginSettingsLabel, pluginSettingsChoice } from '../src/plugin-settings-i18n.ts';

test('plugin metadata uses browser locale and preserves unknown labels', () => {
    try {
        locale.value = 'uk';
        assert.equal(pluginSettingsLabel('nodelist', 'CRC checks'), 'Перевірка CRC');
        assert.equal(pluginSettingsLabel('fileecho', 'File echoes'), 'Файлові конференції');
        assert.equal(
            pluginSettingsLabel('nodelist', 'Legacy CRC exceptions'),
            'Старі винятки перевірки CRC'
        );
        assert.equal(pluginSettingsLabel('external', 'File echoes'), 'File echoes');
        assert.equal(pluginSettingsLabel('nodelist', 'Unknown field'), 'Unknown field');
        locale.value = 'en';
        assert.equal(pluginSettingsLabel('nodelist', 'CRC checks'), 'CRC checks');
        assert.equal(pluginSettingsChoice('fileecho', 'access', 'READ'), 'Receives from us');
    } finally {
        locale.value = 'en';
    }
});

test('only known enum fields are translated; user-defined choices remain unchanged', () => {
    try {
        locale.value = 'uk';
        assert.equal(pluginSettingsChoice('fileecho', 'enabled', 'true'), 'Так');
        assert.equal(pluginSettingsChoice('fileecho', 'access', 'WRITE'), 'Надсилає нам');
        assert.equal(pluginSettingsChoice('fileecho', 'name', 'READ'), 'READ');
        assert.equal(pluginSettingsChoice('nodelist', 'source', 'Source'), 'Source');
        assert.equal(pluginSettingsChoice('nodelist', 'mode', 'ARC-CRC'), 'ARC-CRC');
        assert.equal(pluginSettingsChoice('nodelist', 'requireChecksum', 'true'), 'Так');
        assert.equal(pluginSettingsChoice('external', 'enabled', 'true'), 'true');
    } finally {
        locale.value = 'en';
    }
});
