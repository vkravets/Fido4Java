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
