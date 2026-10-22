import { mailId } from '../src/netmail.ts';
import type { EchoClient } from '../src/echomail.ts';
import type { MailItem } from '@rpc/bindings.mjs';
import { echoMessages } from './echo-fixture.ts';

export type SearchFixtureMode = 'enabled' | 'disabled' | 'pending' | 'error' | 'continuation';
export function archiveSearchFixture(mode: SearchFixtureMode, sample: MailItem = echoMessages[0]!) {
    const archive = {
        ...sample,
        id: '9007199254741999',
        messageId: '1:500/101 archive',
        subject: 'Archived discussion',
        excerpt: 'Context from a long body',
        body:
            'Opening paragraph.\n' +
            'More archive content. '.repeat(30) +
            '\nWelcome from the archive.'
    };
    const searchMessages: EchoClient['searchMessages'] = async (
        _token,
        _box,
        _area,
        query,
        after
    ) => {
        if (mode === 'disabled') throw { code: 'FEATURE_DISABLED' };
        if (mode === 'pending') return new Promise(() => {});
        if (mode === 'error') throw { code: 'FORBIDDEN' };
        if (mode === 'continuation' && mailId(after) === '0') {
            return { items: [], nextId: '9007199254742000', hasMore: true, scanned: 1000 };
        }
        return {
            items: query.toLowerCase().includes('welcome') ? [{ ...archive }] : [],
            nextId: archive.id,
            hasMore: false,
            scanned: 20
        };
    };
    return { archive, searchMessages };
}
