import type { NodeConfiguration, PollInfo, UserProfile } from '@rpc/bindings.mjs';

export function profile(role: 'ADMIN' | 'POINT'): UserProfile {
    const address = role === 'ADMIN' ? '1:500/100' : '1:500/100.1';
    return {
        login: role === 'ADMIN' ? 'sysop' : 'point',
        displayName: role === 'ADMIN' ? 'sysop' : 'point',
        role,
        enabled: true,
        theme: 'light',
        defaultAka: address,
        allowedAkas: [address],
        claims: []
    };
}

export function station(): NodeConfiguration {
    return {
        address: '1:500/100',
        aliases: ['1:500/100.1'],
        system: 'Example station',
        sysop: 'Example Sysop',
        location: 'Kyiv',
        storage: './mail',
        nodeListUrl: '',
        login: '',
        password: '',
        charset: 'CP866',
        browserOrigin: 'http://localhost:9091',
        preserveReceivedFiles: true
    };
}

export function attempt(state = 'FAILED'): PollInfo {
    return {
        id: 'example-poll',
        address: '1:500/101',
        state,
        source: 'SCHEDULED',
        created: Date.UTC(2026, 9, 16, 12),
        started: Date.UTC(2026, 9, 16, 12, 0, 1),
        finished: state === 'FAILED' ? Date.UTC(2026, 9, 16, 12, 0, 2) : undefined,
        detail: state === 'FAILED' ? 'Connection refused: localhost:24554' : ''
    };
}

export function uplinks() {
    return [
        {
            address: '1:500/101',
            localAddresses: ['1:500/100', '1:500/100.1'],
            host: 'first.example.org',
            port: 24554,
            sessionPassword: '',
            packetPassword: '',
            packetPasswordMode: 'INHERIT',
            autoCreateEchoAccess: 'NONE'
        },
        {
            address: '1:500/102',
            localAddresses: ['1:500/100', '1:500/100.1'],
            host: 'second.example.org',
            port: 24555,
            sessionPassword: '',
            packetPassword: '',
            packetPasswordMode: 'SEPARATE',
            autoCreateEchoAccess: 'NONE'
        }
    ];
}
