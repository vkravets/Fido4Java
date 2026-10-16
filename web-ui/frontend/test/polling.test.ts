import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createPolling, activePoll, pollState, startPollRefresh } from '../src/polling.ts';
import type { PollInfo } from '../../build/frontend-generated/bindings.mjs';

function attempt(id = 'one', state = 'QUEUED'): PollInfo {
    return {
        id,
        address: '1:500/101.0',
        state,
        created: 1000,
        detail: '',
        source: 'MANUAL'
    } as PollInfo;
}

function fixture() {
    const requests: string[] = [];
    let current = attempt();
    const client = {
        async poll(session: string, address: string) {
            assert.equal(session, 'admin');
            assert.equal(address, current.address);
            requests.push('poll');
            return current;
        },
        async cancelPoll(_session: string, id: string) {
            assert.equal(id, current.id);
            current = attempt(id, 'CANCELLED');
            requests.push('cancel');
        },
        async pollQueue() {
            return activePoll(current.state) ? [current] : [];
        },
        async pollDetails(_session: string, id: string) {
            return { ...current, id };
        },
        async pollHistory(_session: string, before: string) {
            requests.push(`history:${before}`);
            return { items: [current], nextCursor: 'older-cursor', hasMore: !before };
        },
        async pollLog(_session: string, id: string, offset: unknown) {
            const cursor = Number(offset);
            return { lines: [`${id}:${cursor}`], nextOffset: cursor + 1, hasMore: true };
        },
        async links() {
            return [{ address: current.address }];
        },
        async outbound(_session: string, address: string, limit: number) {
            assert.equal(address, current.address);
            assert.equal(limit, 50);
            return [];
        }
    };
    const model = createPolling(
        client as unknown as Parameters<typeof createPolling>[0],
        () => 'admin'
    );
    return { client, model, requests };
}

test('shared refreshes manual polls cancellation and older history used the intended RPC methods', async () => {
    const { model, requests } = fixture();
    const first = model.refresh();
    assert.equal(model.refresh(), first);
    await first;
    assert.equal(model.queue.value.length, 1);
    await model.start('1:500/101.0');
    assert.equal(model.selected.value?.id, 'one');
    assert.ok(requests.includes('poll'));
    await model.cancel('one');
    assert.equal(model.queue.value.length, 0);
    assert.equal(model.selected.value?.state, 'CANCELLED');
    await model.older();
    await model.refresh();
    assert.equal(model.before.value, 'older-cursor');
    assert.equal(model.hasOlder.value, false);
    await model.newest();
    assert.equal(model.before.value, '');
});

test('late responses from another attempt were discarded and concurrent log reads did not duplicate lines', async () => {
    const { model, client } = fixture();
    let release: (value: {
        lines: string[];
        nextOffset: number;
        hasMore: boolean;
    }) => void = () => {};
    const original = client.pollLog;
    client.pollLog = async (session, id, offset) => {
        if (id === 'old')
            return new Promise((resolve) => {
                release = resolve;
            });
        return original(session, id, offset);
    };
    const stale = model.select(attempt('old'));
    await model.select(attempt('new'));
    release({ lines: ['wrong attempt'], nextOffset: 1, hasMore: false });
    await stale;
    assert.deepEqual(model.lines.value, ['new:0']);
    await Promise.all([model.readSelected(), model.readSelected()]);
    assert.deepEqual(model.lines.value, ['new:0', 'new:1']);
});

test('log memory was bounded and disposal prevented future refreshes', async () => {
    const { model, client, requests } = fixture();
    client.pollLog = async () => ({
        lines: Array.from({ length: 100 }, (_, index) => `line ${index}`),
        nextOffset: 0,
        hasMore: true
    });
    await model.select(attempt());
    for (let index = 0; index < 12; index++) await model.readSelected();
    assert.equal(model.lines.value.length, 1000);
    assert.equal(model.clipped.value, true);
    model.dispose();
    await model.refresh();
    assert.equal(requests.length, 0);
});

test('auto refresh never overlapped requests and stopped without scheduling another timer', async () => {
    const tasks: Array<() => void> = [];
    let finish = () => {};
    let count = 0;
    const stop = startPollRefresh(
        () => {
            count++;
            return new Promise<void>((resolve) => {
                finish = resolve;
            });
        },
        (error) => {
            throw error;
        },
        {
            schedule: (task) => {
                tasks.push(task);
                return task;
            },
            clear: (handle) => {
                const index = tasks.indexOf(handle as () => void);
                if (index >= 0) tasks.splice(index, 1);
            }
        }
    );
    assert.equal(count, 1);
    assert.equal(tasks.length, 0);
    finish();
    await new Promise((resolve) => setImmediate(resolve));
    assert.equal(tasks.length, 1);
    tasks.shift()!();
    assert.equal(count, 2);
    stop();
    finish();
    await new Promise((resolve) => setImmediate(resolve));
    assert.equal(tasks.length, 0);
});

test('all outgoing lifecycle states had readable UI labels', () => {
    for (const state of [
        'QUEUED',
        'CONNECTING',
        'AUTHENTICATING',
        'TRANSFERRING',
        'SUCCEEDED',
        'BUSY',
        'TIMED_OUT',
        'FAILED',
        'CANCELLED',
        'INTERRUPTED'
    ]) {
        assert.notEqual(pollState(state), state);
    }
});
