import type { MailDraft, MailItem } from '@rpc/bindings.mjs';
export const netmailMessage: MailItem = {
    id: '9007199254740993',
    fromName: 'Alice Example',
    toName: 'Test Sysop',
    fromAddress: '1:500/101.2',
    toAddress: '1:500/100',
    subject: 'Welcome to FidoNet',
    date: '18 Oct 26  12:30:00',
    messageId: '1:500/101.2 12345678',
    read: false,
    body: 'Hello!\n\nYour station is ready to exchange netmail.\nThis is plain text: <script>alert("example")</script>\n\nBest regards,\nAlice'
};
export const netmailDraft: MailDraft = {
    id: 'example-draft',
    mailbox: 3,
    area: '',
    fromName: 'Test Sysop',
    fromAddress: '1:500/100',
    toName: 'Alice Example',
    toAddress: '1:500/101.2',
    subject: 'Re: Welcome to FidoNet',
    body: 'Hello Alice,\n\nThanks for your message!\n\n> Your station is ready to exchange netmail.',
    replyTo: '1:500/101.2 12345678'
};
