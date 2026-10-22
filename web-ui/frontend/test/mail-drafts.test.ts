import assert from 'node:assert/strict';
import { test } from 'node:test';
import { forwardDraft } from '../src/mail-drafts.ts';
import type { MailItem, UserProfile } from '../../build/frontend-generated/bindings.mjs';
test('forwarding creates new netmail from a permitted identity and leaves the original unchanged', () => {
    const item = {
        id: '42',
        fromName: 'Bob',
        fromAddress: '1:500/101',
        subject: 'Hello',
        body: 'Original',
        date: '20 Oct 26',
        messageId: 'original',
        replyTo: 'parent'
    } as MailItem;
    const draft = forwardDraft(item, {
        login: 'Point',
        displayName: 'Alex Example',
        defaultAka: '1:500/999',
        allowedAkas: ['1:500/100.1']
    } as UserProfile);
    assert.equal(draft.fromAddress, '1:500/100.1');
    assert.equal(draft.fromName, 'Alex Example');
    assert.equal(draft.mailbox, 3);
    assert.equal(draft.replyTo, undefined);
    assert.equal(draft.originalName, 'Bob');
    assert.equal(item.messageId, 'original');
    assert.equal(item.body, 'Original');
});
