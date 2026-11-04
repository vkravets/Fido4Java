import type { MailDraft, MailItem, UserProfile } from '@rpc/bindings.mjs';
export function originalKludgeSnapshot(
    item: MailItem
): Pick<MailDraft, 'originalKludges' | 'originalKludgesTruncated'> {
    if (!item.kludges?.length) return {};
    const encoder = new TextEncoder();
    const lines: string[] = [];
    let bytes = 0;
    for (const line of item.kludges) {
        const size = encoder.encode(line).length;
        if (bytes + size > 60000) return { originalKludges: lines, originalKludgesTruncated: true };
        lines.push(line);
        bytes += size;
    }
    return { originalKludges: lines };
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
