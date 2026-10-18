import assert from 'node:assert/strict';
import { test } from 'node:test';
import { routingMasks, routingReason } from '../src/routing.ts';
import { setLocale } from '../src/i18n.ts';

test('routing masks retain wildcard syntax and separate individual lines', () => {
    assert.deepEqual(routingMasks(' 1:500/*\r\n\r\n2:*\n'), ['1:500/*', '2:*']);
    assert.deepEqual(routingMasks(' \n '), []);
});

test('route decisions have translated explanations and retain unknown diagnostics', () => {
    setLocale('en');
    assert.equal(routingReason('NO_ROUTE'), 'No route');
    setLocale('uk');
    assert.equal(routingReason('NO_ROUTE'), 'Немає маршруту');
    assert.equal(routingReason('NEW_REASON'), 'NEW_REASON');
    setLocale('en');
});
