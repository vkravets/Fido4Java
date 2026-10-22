export type ShortcutScope = 'global' | 'mail' | 'compose' | 'editor';
export const shortcutGroups = [
    { id: 'global', label: 'Navigation and page' },
    { id: 'mail', label: 'Reading mail' },
    { id: 'compose', label: 'Writing mail' },
    { id: 'editor', label: 'Text editor' }
] as const;
export const shortcutActions = [
    { id: 'nav.netmail', label: 'Netmail', scope: 'global', keys: ['Alt+1'] },
    { id: 'nav.echo', label: 'Echomail', scope: 'global', keys: ['Alt+2'] },
    { id: 'nav.drafts', label: 'Drafts', scope: 'global', keys: ['Alt+3'] },
    { id: 'nav.directory', label: 'Node directory', scope: 'global', keys: ['Alt+4'] },
    { id: 'nav.settings', label: 'My settings', scope: 'global', keys: ['Alt+5'] },
    { id: 'nav.station', label: 'Station', scope: 'global', keys: ['Alt+6'] },
    { id: 'nav.links', label: 'Links', scope: 'global', keys: ['Alt+7'] },
    { id: 'nav.polling', label: 'Polling', scope: 'global', keys: ['Alt+8'] },
    { id: 'nav.collapse', label: 'Collapse menu', scope: 'global', keys: ['Alt+Shift+M'] },
    { id: 'nav.sendQueue', label: 'Send queued mail', scope: 'global', keys: ['Ctrl+Alt+Enter'] },
    {
        id: 'mail.new',
        label: 'New message',
        scope: 'mail',
        keys: ['C'],
        golded: ['E', 'Insert', 'Alt+E']
    },
    {
        id: 'mail.reply',
        label: 'Quoted reply',
        scope: 'mail',
        keys: ['R'],
        golded: ['Q', 'F4', 'Alt+Q']
    },
    {
        id: 'mail.shortReply',
        label: 'Short reply',
        scope: 'mail',
        keys: ['Shift+R'],
        golded: ['R', 'F3', 'Alt+R']
    },
    {
        id: 'mail.previous',
        label: 'Previous message',
        scope: 'mail',
        keys: ['K', 'ArrowLeft'],
        golded: ['ArrowLeft']
    },
    {
        id: 'mail.next',
        label: 'Next message',
        scope: 'mail',
        keys: ['J', 'ArrowRight'],
        golded: ['ArrowRight']
    },
    {
        id: 'mail.unread',
        label: 'Next unread',
        scope: 'mail',
        keys: ['U'],
        golded: ['Alt+ArrowRight', 'Alt+U']
    },
    {
        id: 'mail.search',
        label: 'Search loaded messages',
        scope: 'mail',
        keys: ['/'],
        golded: ['F', 'Alt+F']
    },
    {
        id: 'mail.back',
        label: 'Back to messages',
        scope: 'mail',
        keys: ['Escape'],
        golded: ['L', 'F9', 'Alt+L', 'Escape']
    },
    {
        id: 'mail.forward',
        label: 'Forward',
        scope: 'mail',
        keys: ['Shift+F'],
        golded: ['M', 'Alt+M']
    },
    {
        id: 'mail.delete',
        label: 'Delete message',
        scope: 'mail',
        keys: ['Delete'],
        golded: ['D', 'Delete', 'Alt+D']
    },
    { id: 'page.refresh', label: 'Refresh', scope: 'global', keys: ['Alt+Shift+R'] },
    {
        id: 'compose.send',
        label: 'Submit message',
        scope: 'compose',
        keys: ['Ctrl+Enter'],
        golded: ['F2', 'Alt+S']
    },
    { id: 'compose.save', label: 'Save draft', scope: 'compose', keys: ['Ctrl+S'] },
    { id: 'compose.cancel', label: 'Cancel', scope: 'compose', keys: ['Escape'] },
    { id: 'compose.template', label: 'Apply template', scope: 'compose', keys: ['Ctrl+Alt+T'] },
    { id: 'editor.bold', label: 'Bold', scope: 'editor', keys: ['Ctrl+B', 'Meta+B'] },
    { id: 'editor.italic', label: 'Italic', scope: 'editor', keys: ['Ctrl+I', 'Meta+I'] },
    { id: 'editor.underline', label: 'Underline', scope: 'editor', keys: ['Ctrl+U', 'Meta+U'] },
    { id: 'editor.quote', label: 'Quote', scope: 'editor', keys: ['Ctrl+Alt+Q'] },
    { id: 'editor.reverse', label: 'Reverse', scope: 'editor', keys: ['Ctrl+Shift+H'] },
    { id: 'editor.undo', label: 'Undo', scope: 'editor', keys: ['Ctrl+Z'] },
    { id: 'editor.redo', label: 'Redo', scope: 'editor', keys: ['Ctrl+Shift+Z'] },
    { id: 'editor.source', label: 'Source text', scope: 'editor', keys: ['Ctrl+Shift+X'] },
    { id: 'editor.preview', label: 'Preview', scope: 'editor', keys: ['Alt+P'] }
] as const satisfies ReadonlyArray<{
    id: string;
    label: string;
    scope: ShortcutScope;
    keys: readonly string[];
    golded?: readonly string[];
}>;
export type ShortcutAction = (typeof shortcutActions)[number]['id'];
export type ShortcutProfile = 'jftn' | 'golded';
export function defaultBindings(action: ShortcutAction, profile: ShortcutProfile): string[] {
    const row = shortcutActions.find((value) => value.id === action)!;
    return [...(profile === 'golded' && 'golded' in row ? row.golded : row.keys)];
}
