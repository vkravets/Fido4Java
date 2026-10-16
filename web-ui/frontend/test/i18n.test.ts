import { test } from 'node:test';
import type { TestContext } from 'node:test';
import assert from 'node:assert/strict';
import { computed } from 'vue';
import { initializeLocale, locale, setLocale, t, translateMessage } from '../src/i18n.ts';
import { ukrainian } from '../src/locales/uk.ts';
import { pollTime } from '../src/polling.ts';

function browser(context: TestContext, saved: string | null = null) {
    const originals = ['document', 'localStorage'].map(
        (key) => [key, Object.getOwnPropertyDescriptor(globalThis, key)] as const
    );
    const page = { documentElement: { lang: '' }, title: '' };
    const values = new Map<string, string>();
    if (saved !== null) values.set('jftn-language', saved);
    Object.defineProperty(globalThis, 'document', { configurable: true, value: page });
    Object.defineProperty(globalThis, 'localStorage', {
        configurable: true,
        value: {
            getItem: (key: string) => values.get(key) ?? null,
            setItem: (key: string, value: string) => values.set(key, value)
        }
    });
    context.after(() => {
        setLocale('en');
        for (const [key, descriptor] of originals) {
            if (descriptor) Object.defineProperty(globalThis, key, descriptor);
            else Reflect.deleteProperty(globalThis, key);
        }
    });
    return { page, values };
}

test('English is the default and unsupported saved languages fall back to English', (context) => {
    const { page, values } = browser(context, 'ru');
    initializeLocale();
    assert.equal(locale.value, 'en');
    assert.equal(t('Settings'), 'Settings');
    assert.equal(page.documentElement.lang, 'en');
    assert.equal(page.title, 'JFtn · Mail and node');
    assert.equal(values.get('jftn-language'), 'en');
});

test('Ukrainian is restored and switching updates reactive labels, metadata and storage', (context) => {
    const { page, values } = browser(context, 'uk');
    initializeLocale();
    const heading = computed(() => t('Settings'));
    assert.equal(heading.value, 'Налаштування');
    assert.equal(page.documentElement.lang, 'uk');
    assert.equal(page.title, 'JFtn · Пошта й нода');
    assert.equal(translateMessage('Settings saved.'), 'Налаштування збережено.');
    setLocale('en');
    assert.equal(heading.value, 'Settings');
    assert.equal(values.get('jftn-language'), 'en');
    assert.equal(translateMessage('Налаштування збережено.'), 'Settings saved.');
    assert.equal(translateMessage('Remote failure: ECONNRESET'), 'Remote failure: ECONNRESET');
    assert.equal(
        translateMessage('Дані знайдено в NODELIST.289. Перевірте їх перед збереженням.'),
        'Details found in NODELIST.289. Review them before saving.'
    );
});

test('all Ukrainian translations preserve interpolation parameters', (context) => {
    browser(context);
    for (const [key, value] of Object.entries(ukrainian)) {
        assert.ok(value.trim(), key);
        assert.doesNotMatch(key, /[А-Яа-яЁё]/);
        const parameters = (text: string) =>
            [...text.matchAll(/\{\w+\}/g)].map(([name]) => name).sort();
        assert.deepEqual(parameters(value), parameters(key), key);
    }
    setLocale('uk');
    assert.equal(t('Poll {address}', { address: '1:500/100' }), 'Полл 1:500/100');
    const stamp = Date.UTC(2026, 9, 16, 12);
    assert.equal(pollTime(stamp), new Date(stamp).toLocaleString('uk-UA'));
    setLocale('en');
    assert.equal(pollTime(stamp), new Date(stamp).toLocaleString('en-GB'));
});

test('blocked storage does not prevent language switching', (context) => {
    browser(context);
    Object.defineProperty(globalThis, 'localStorage', {
        configurable: true,
        get() {
            throw new Error('Storage blocked');
        }
    });
    initializeLocale();
    assert.equal(locale.value, 'en');
    setLocale('uk');
    assert.equal(t('Language'), 'Мова');
});
