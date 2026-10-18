import assert from 'node:assert/strict';
import { test } from 'node:test';
import { addressToken, createDirectorySearch } from '../src/directory.ts';
import type { DirectoryPage } from '../../build/frontend-generated/bindings.mjs';

const page = (address: string): DirectoryPage =>
    ({ items: [{ address }], nextOffset: 25, hasMore: true }) as DirectoryPage;
test('finds the token around the caret without replacing other AKAs', () => {
    const value = '1:500/100 1:500/10 1:500/102';
    assert.deepEqual(addressToken(value, 15, true), { start: 10, end: 18, query: '1:500/10' });
    assert.deepEqual(addressToken(' 1:500/10 ', 2, false), {
        start: 0,
        end: 10,
        query: '1:500/10'
    });
    assert.equal(addressToken('1:500/100 ', 10, true).query, '');
});
test('older responses cannot replace newer searches and only the requested page is retained', async () => {
    let finish: (value: DirectoryPage) => void = () => {};
    const reader = createDirectorySearch((query) =>
        query === 'old'
            ? new Promise((resolve) => {
                  finish = resolve;
              })
            : Promise.resolve(page('1:500/102'))
    );
    const old = reader.load('old');
    await reader.load('new');
    finish(page('1:500/101'));
    await old;
    assert.equal(reader.items.value[0]?.address, '1:500/102');
    await reader.load('new', 25);
    assert.equal(reader.items.value.length, 1);
    assert.equal(reader.offset.value, 25);
    assert.equal(reader.hasMore.value, true);
});
test('clearing or disposing ignores late replies and failures allow a retry', async () => {
    let finish: (value: DirectoryPage) => void = () => {};
    const reader = createDirectorySearch(
        () =>
            new Promise((resolve) => {
                finish = resolve;
            })
    );
    const pending = reader.load('old');
    reader.clear();
    finish(page('1:500/100'));
    await pending;
    assert.equal(reader.items.value.length, 0);
    const later = reader.load('later');
    reader.dispose();
    finish(page('1:500/101'));
    await later;
    assert.equal(reader.items.value.length, 0);
    let failed = true;
    const retry = createDirectorySearch(async () => {
        if (failed) throw new Error('offline');
        return page('1:500/101');
    });
    await assert.rejects(retry.load('query'), /offline/);
    assert.equal(retry.busy.value, false);
    failed = false;
    await retry.load('query');
    assert.equal(retry.items.value.length, 1);
});
