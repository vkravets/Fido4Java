import assert from 'node:assert/strict';
import { test } from 'node:test';
import { createNodeLog } from '../src/node-log.ts';
import type { LogPage } from '../../build/frontend-generated/bindings.mjs';

function page(lines: string[], offset: number, rotated = false): LogPage {
    return { lines, nextOffset: { toString: () => String(offset) }, rotated } as unknown as LogPage;
}
function reader(read: (offset: number) => Promise<LogPage>) {
    return createNodeLog(
        { logs: async (_session, offset) => read(Number(offset.toString())) },
        () => 'admin'
    );
}
test('starts at the latest window and advances byte cursors without duplicating lines', async () => {
    const cursors: number[] = [];
    const log = reader(async (offset) => {
        cursors.push(offset);
        return offset === -1 ? page(['IN PWD [redacted]'], 40) : page(['OUT GOT mail.pkt'], 80);
    });
    await log.read();
    await log.read();
    assert.deepEqual(cursors, [-1, 40]);
    assert.deepEqual(log.lines.value, ['IN PWD [redacted]', 'OUT GOT mail.pkt']);
    await log.latest();
    assert.deepEqual(log.lines.value, ['IN PWD [redacted]']);
});
test('rotation clears old lines and the browser retains at most 1000 lines', async () => {
    let rotated = false;
    const log = reader(async () =>
        page(
            Array.from({ length: 200 }, (_, i) => String(i)),
            200,
            rotated
        )
    );
    for (let i = 0; i < 6; i++) await log.read();
    assert.equal(log.lines.value.length, 1000);
    assert.equal(log.clipped.value, true);
    rotated = true;
    await log.read();
    assert.equal(log.lines.value.length, 200);
    assert.equal(log.clipped.value, false);
    assert.equal(log.rotated.value, true);
});
test('concurrent refreshes share a request and unmounted readers ignore late responses', async () => {
    let finish!: (value: LogPage) => void;
    let calls = 0;
    const log = reader(async () => {
        calls++;
        return new Promise<LogPage>((resolve) => {
            finish = resolve;
        });
    });
    const first = log.read();
    const second = log.read();
    assert.equal(calls, 1);
    log.dispose();
    finish(page(['late'], 40));
    await Promise.all([first, second]);
    assert.deepEqual(log.lines.value, []);
    assert.equal(log.busy.value, false);
});
test('request failures preserve the cursor and allow another refresh', async () => {
    const cursors: number[] = [];
    const log = reader(async (offset) => {
        cursors.push(offset);
        if (cursors.length === 1) throw new Error('Unavailable');
        return page(['reconnected'], 50);
    });
    await assert.rejects(log.read(), /Unavailable/);
    await log.read();
    assert.deepEqual(cursors, [-1, -1]);
    assert.equal(log.busy.value, false);
});
