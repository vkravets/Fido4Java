import { test } from 'node:test';
import assert from 'node:assert/strict';
import { Thrift, Int64 } from '../../build/frontend-generated/runtime.mjs';
import { createBindings } from '../../build/frontend-generated/bindings.mjs';
import { createRpcClient } from '../../build/frontend-generated/client.mjs';

test('browser Thrift runtime preserves full 64-bit values', async () => {
    const bindings = createBindings(Thrift, Int64);
    const original = globalThis.fetch;
    try {
        globalThis.fetch = async () =>
            new Response(
                '[1,"passkeys",2,0,{"0":{"lst":["rec",1,{"1":{"str":"id"},"2":{"str":"key"},"3":{"i64":9223372036854775807}}]}}]'
            );
        const client = createRpcClient(
            Thrift,
            bindings.AuthServiceClient,
            'http://localhost/rpc/auth'
        );
        const keys = await client.passkeys('token');
        assert.equal(keys[0].created, '9223372036854775807');
    } finally {
        globalThis.fetch = original;
    }
});

test('browser writes returned decimal i64 IDs exactly, including signed boundaries', async () => {
    const bindings = createBindings(Thrift, Int64);
    const original = globalThis.fetch;
    const requests: string[] = [];
    try {
        globalThis.fetch = async (_url, options) => {
            requests.push(String(options!.body));
            return new Response('[1,"markRead",2,0,{}]');
        };
        const client = createRpcClient(Thrift, bindings.UiServiceClient, 'http://localhost/rpc/ui');
        for (const id of ['9007199254740993', '9223372036854775807', '-9223372036854775808']) {
            await client.markRead('token', 1, 'test', id, true);
            assert.ok(requests.at(-1)!.includes(`"4":{"i64":${id}}`));
        }
        const before = requests.length;
        for (const id of ['9223372036854775808', '-9223372036854775809', '1.5', 'NaN']) {
            await assert.rejects(client.markRead('token', 1, 'test', id, true), RangeError);
        }
        await assert.rejects(
            client.markRead('token', 1, 'test', Number.MAX_SAFE_INTEGER + 1, true),
            RangeError
        );
        assert.equal(requests.length, before);
    } finally {
        globalThis.fetch = original;
    }
});
