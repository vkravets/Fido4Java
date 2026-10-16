import { test } from 'node:test';
import assert from 'node:assert/strict';
import { decode, encode, publicKeyOptions, credentialJson } from '../src/webauthn.ts';

test('binary WebAuthn values round trip without changing bytes', () => {
    const input = Uint8Array.from([0, 255, 254, 127, 0]);
    assert.deepEqual(decode(encode(input)), input);
    assert.equal(encode(null), null);
});

test('registration and authentication options decode all binary identifiers', () => {
    const options = publicKeyOptions<PublicKeyCredentialCreationOptions>(
        JSON.stringify({
            publicKey: {
                challenge: 'AP8',
                user: { id: 'AQI' },
                excludeCredentials: [{ id: 'AwQ', type: 'public-key' }]
            }
        })
    );
    assert.deepEqual([...new Uint8Array(options.challenge as ArrayBuffer)], [0, 255]);
    assert.deepEqual([...new Uint8Array(options.user.id as ArrayBuffer)], [1, 2]);
    assert.deepEqual([...new Uint8Array(options.excludeCredentials![0].id as ArrayBuffer)], [3, 4]);
});

test('assertion serializes signatures and nullable user handles for server verification', () => {
    const credential = {
        id: 'AP8',
        rawId: Uint8Array.of(0, 255),
        type: 'public-key',
        response: {
            clientDataJSON: Uint8Array.of(1),
            authenticatorData: Uint8Array.of(3),
            signature: Uint8Array.of(2),
            userHandle: null
        },
        getClientExtensionResults: () => ({})
    };
    const result = JSON.parse(credentialJson(credential as unknown as PublicKeyCredential));
    assert.equal(result.rawId, 'AP8');
    assert.equal(result.response.signature, 'Ag');
    assert.equal(result.response.userHandle, null);
});
