import type { MailDraft, MailItem, UserProfile } from '@rpc/bindings.mjs';
export function originalKludgeSnapshot(item: MailItem): Pick<MailDraft, 'originalKludges'> {
    return item.kludges?.length ? { originalKludges: [...item.kludges] } : {};
}

/** Forwarding copies readable text into new netmail; it never reuses MSGID/REPLY. */
export function forwardDraft(item: MailItem, profile: UserProfile): MailDraft {
    return {
        id: crypto.randomUUID(),
        mailbox: 3,
        area: '',
        fromName: profile.displayName || profile.login,
        fromAddress: profile.allowedAkas.includes(profile.defaultAka)
            ? profile.defaultAka
            : profile.allowedAkas[0] || '',
        toName: '',
        toAddress: '',
        subject: 'Fwd: ' + item.subject,
        body: item.body || '',
        originalBody: item.body || '',
        originalDate: item.date,
        originalName: item.fromName,
        originalAddress: item.fromAddress,
        ...originalKludgeSnapshot(item),
        composeKind: 'forward',
        templateInitialized: false
    };
}
