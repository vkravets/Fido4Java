# Echomail conference list and workspace — implementation plan

Status: E1–E4 implemented; E5–E6 remain.

E3 adds the optional conference column and actual-work-area width fallbacks.
E4 retains mounted editor/list panes through stage and preview changes, restores
focus and scroll positions, and cancels pending reads when hiding the reader.
Focused browser scenarios cover retained editors, canceled/accepted area changes,
mobile return navigation and shared netmail layout behavior. E5 broadens coverage.

Commit this plan before implementation, then commit each completed item separately.
Use focused functional checks for changed behavior. Create relevant Storybook
states alongside the implementation, but run visual tests and update/review VRT
baselines only in E6, after all functionality is implemented. Do not repeatedly
run the entire test suite at intermediate steps.

## Agreed behavior

Replace the conference dropdown with a flat conference list to the left of the
message list. The navigation chain is conferences -> messages -> reader/composer.
Keep the existing Right, Below and Separate preview preferences, the message
list's Flat/Threads modes, density, search, drafts, permissions and submission
actions. The discussion disclosure below a message remains separate work.

Add a profile-owned **All panels / Last two panels** preference, defaulting to
**All panels**. Keep it independent of preview position. Preserve the preference
when adapting to a narrow screen; resizing must not save another preferred mode.
Use the existing account `workspace` UI document; no RPC contract or database
migration is planned. Missing/invalid new preferences use the stated default.

The conference list contains a search field, a selected-row highlight, conference
name, the existing total-message count and Read only/Post only indications.
Preserve server order; no conference tree, grouping or new unread-count API.
Conference search and message search have independent inputs and state.

## Layouts and navigation

These sketches show the application work area, excluding its main navigation.
Full screen means the available work area, not browser/OS fullscreen.

### Right preview

All panels, with an open message:

```text
| Conferences       | Messages          | Reader              |
| Search conferences| Search messages   | Subject / sender    |
| > RU.LINUX   340  | Message 1         | Message body        |
|   RU.JAVA    120  | > Message 2       | Discussion          |
```

Last two panels, or a workspace too narrow for three columns:

```text
Before opening:  | Conferences | Messages                         |
After opening:  | Messages [Conferences] | Reader                 |
```

The Conferences button returns to the first pair without clearing the open
message/draft. A Back to message/Back to editor action restores the second pair.
When no message or draft is open, use conferences + messages without reserving
an empty third column.

### Below preview

All panels:

```text
| Conferences | Messages                                 |
|             |------------------------------------------|
|             | Reader                                   |
```

Last two panels with a message open:

```text
| Messages [Conferences]                                 |
|-------------------------------------------------------|
| Reader                                                |
```

The existing horizontal message/reader splitter remains available. With no open
message, show conferences + messages side by side.

### Separate preview and composer

Separate preview shows conferences + messages before opening, then the reader
across the work area. Back to messages restores the list view and scroll state.

The composer initially occupies the work area as it does today. Its existing
show-list control restores messages beside it (Right) or above it (Below), with
the conference column hidden. The Conferences button temporarily shows the
conference/message pair; returning to the editor restores the same draft.
Separate preview keeps its existing separate-editor behavior.

### Narrow workspaces

Use the current mobile boundary (viewport at most 700px) for a single-stage flow:
conferences -> messages -> reader/composer, with explicit back actions. Also
adapt to actual work-area width when the application's main sidebar leaves less
space than the viewport suggests. Use a 220px conference column on desktop;
fall back from three columns to the last two below 840px of work-area width,
and to one stage below 640px. In Below mode the conference column beside the
vertical message/reader pair can remain visible at 640px or more. These fallbacks
do not overwrite the profile preference or the existing message/reader ratio.

Every visible list and reader pane scrolls independently. Toggling stages retains
conference selection, message selection, queries, list positions, open content,
draft edits and the existing preview splitter ratio. Changing the actual
conference still uses the existing unsaved-draft confirmation; cancellation
leaves both the conference selection and draft intact. View changes alone do not
trigger discard confirmation or remount the draft to reapply its template.

## Implementation items and model routing

| ID | Deliverable | Level / model |
| --- | --- | --- |
| E1 | Add/normalize/save the independent conference-panel preference in `workspace`; add its English/Ukrainian layout control alongside existing preview controls. Keep new stage state separate from whether a message/draft exists. | Medium / Sol Low |
| E2 | Implement the flat conference-list component with existing counts, rights labels, filtering and selection guards; integrate it into echomail and replace the dropdown. Cover empty/loading/error, no matches, read-only and post-only areas. | Medium / Sol Low |
| E3 | Extend the workspace composition for conference/message/reader panels, Right/Below/Separate positions, All/Last-two choices and the agreed width fallbacks. Keep the netmail workspace functional. | High / Sol Medium |
| E4 | Complete Conferences/Back navigation and composer show-list integration. Preserve mounted editor state, selection, independent scrolling, focus and dirty-draft guards through stage/layout changes, area switches and resize. Keep existing mail actions reachable in every applicable stage. | High / Sol Medium |
| E5 | Complete focused unit/browser coverage for preferences, conference selection, all layout/stage transitions and netmail regression cases; update behavior documentation and TODO. Audit the implementation against the agreed sketches before visual verification. | Medium / Sol Low |
| E6 | Once E1–E5 are complete, build the full Storybook catalogue, generate only affected baselines in pinned Linux Playwright, review them, and run the same VRT selection without snapshot updates. Fix any resulting layout defects within this final item. | Medium / Sol Low |

The scale remains Low = Luna Medium, Medium = Sol Low, High = Sol Medium.
Estimates include verification and are not timing guarantees. Begin E3/E4 with
Sol Medium because the shared workspace and editor lifecycle are the main risks.

## Implementation boundaries and acceptance

- Reuse existing area loading and selection in `EchomailView.vue`; respect its
  exact READ/WRITE grants and existing background-reader request invalidation.
  A post-only area exposes its allowed actions without revealing a reader list.
- Extend `MailWorkspace.vue` through an optional conference-pane integration;
  retain the existing netmail layout. Keep preview position and message-list
  tree/flat mode independent of the new panel preference.
- Never use a single opened flag to represent both retained content and the
  visible navigation stage. Moving to conferences/messages must not clear the
  retained message or editor model. A late request for another area/session
  cannot replace the active conference's content.
- Functional checks cover both panel preferences with Right/Below/Separate,
  reader and editor, conference navigation, canceled/accepted area changes,
  search with no matches, draft resume, reload of saved preferences, resizing,
  keyboard access and focus restoration. Verify counts and permission labels
  continue to use current RPC data, and test the unaffected netmail layouts.
- Final VRT covers English/Ukrainian, light/dark, desktop/mobile, a desktop with
  the main sidebar open, the width fallbacks, long conference names, empty and
  permission-limited areas, both list modes, each preview position, the initial
  full-area editor and its restored message list. Do not run/update VRT before E6.
- E6 records the reviewed screenshots, comparison result and any remaining
  environment limitation. No test modifies the installed node's private state;
  updating the user's test installation remains a separate requested operation.

## Initial defaults

- Conference panels: All panels; preview position retains the existing profile
  value and default.
- On a narrow screen: start at conferences if no area is selected, otherwise at
  messages; open reader/editor actions advance to their own stage.
- Do not persist transient stage/scroll/draft visibility as new browser storage.
  Keep the existing session/account persistence mechanisms and in-memory view
  state. No new dependency is required.
