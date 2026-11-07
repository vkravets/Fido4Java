import type { Page, TestInfo } from '@playwright/test';
import type { Screen } from './screens.ts';
import { inlineReader } from './inline-reader.ts';
import { expandReaderKludges } from './expand-reader-kludges.ts';
import { mailSearch } from './mail-search.ts';
import { hideComposerList } from './hide-composer-list.ts';
import { showComposerList } from './show-composer-list.ts';
import { profileDirectory } from './profile-directory.ts';
import { scrollSelection } from './scroll-selection.ts';
import { treeNavigation } from './tree-navigation.ts';
import { switching } from './switching.ts';
import { disclosure } from './disclosure.ts';
import { suggestions } from './suggestions.ts';
import { conversation } from './conversation.ts';
import { expandKludges } from './expand-kludges.ts';
import { referenceKludges } from './reference-kludges.ts';
import { unavailable } from './unavailable.ts';

export async function prepare(page: Page, screen: Screen, info: TestInfo) {
    for (const check of [
        inlineReader,
        expandReaderKludges,
        mailSearch,
        hideComposerList,
        showComposerList,
        profileDirectory,
        scrollSelection,
        treeNavigation,
        switching,
        disclosure,
        suggestions,
        conversation,
        expandKludges,
        referenceKludges,
        unavailable
    ])
        await check(page, screen, info);
}
