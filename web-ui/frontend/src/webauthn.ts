export function decode(value: string): Uint8Array<ArrayBuffer> {
    const binary = atob(value.replace(/-/g, '+').replace(/_/g, '/'));
    return Uint8Array.from(binary, (character) => character.charCodeAt(0));
}

export function encode(value: ArrayBuffer | ArrayBufferView): string;
export function encode(value: null): null;
export function encode(value: ArrayBuffer | ArrayBufferView | null): string | null;
export function encode(value: ArrayBuffer | ArrayBufferView | null): string | null {
    if (value == null) return null;
    const bytes =
        value instanceof ArrayBuffer
            ? new Uint8Array(value)
            : new Uint8Array(value.buffer, value.byteOffset, value.byteLength);
    let binary = '';
    for (const byte of bytes) binary += String.fromCharCode(byte);
    return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

export function publicKeyOptions<
    T extends PublicKeyCredentialCreationOptions | PublicKeyCredentialRequestOptions
>(json: string): T {
    const parsed = JSON.parse(json);
    const options = parsed.publicKey ?? parsed;
    options.challenge = decode(options.challenge);
    if (options.user) options.user.id = decode(options.user.id);
    for (const list of [options.allowCredentials, options.excludeCredentials]) {
        for (const credential of list ?? []) credential.id = decode(credential.id);
    }
    return options;
}

export function credentialJson(credential: PublicKeyCredential): string {
    const response: Record<string, string | string[] | null> = {
        clientDataJSON: encode(credential.response.clientDataJSON)
    };
    if ('attestationObject' in credential.response) {
        const registration = credential.response as AuthenticatorAttestationResponse;
        response.attestationObject = encode(registration.attestationObject);
        if (registration.getTransports) response.transports = registration.getTransports();
    } else {
        const assertion = credential.response as AuthenticatorAssertionResponse;
        response.authenticatorData = encode(assertion.authenticatorData);
        response.signature = encode(assertion.signature);
        response.userHandle = encode(assertion.userHandle);
    }
    return JSON.stringify({
        id: credential.id,
        rawId: encode(credential.rawId),
        type: credential.type,
        response,
        clientExtensionResults: credential.getClientExtensionResults(),
        authenticatorAttachment: credential.authenticatorAttachment
    });
}
