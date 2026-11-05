# Compact echomail discussion panel — implementation plan

Status: C1–C4 implemented and verified. Each implementation step was committed
individually.

## Goal

Keep the Conversation disclosure header fixed in place and make its controls and
message tree compact like the compact mail list. Keep the existing discussion
data, account preferences, navigation, and accessibility behavior.

## Implementation steps

| ID | Change | Level / model | Commit |
| --- | --- | --- | --- |
| C1 | Stabilize disclosure spacing when closed/open and across message navigation. Finish the in-progress icon-button edits, including correct accessible names and titles. | Low / Luna Medium | Separate |
| C2 | Restore each message subject in the tree row; show sender and date as secondary compact text. Use compact mail-list row spacing, retaining expand controls, current-message highlight, and keyboard navigation. | Medium / Sol Low | Separate |
| C3 | Replace the two mode buttons with one icon button: pressed means Current message (yellow), unpressed means Whole discussion (dark). Show the existing selection-behavior dropdown only in Whole discussion mode, without a visible title, inline with the mode button. Reuse compact editor control spacing and responsive wrapping. | Medium / Sol Low | Separate |
| C4 | Add focused behavior checks and Storybook states. Update only affected pinned Linux VRT baselines for English/Ukrainian, light/dark, desktop/mobile; inspect them and compare without snapshot updates. | Medium / Sol Low | Separate |

## Acceptance checks

- Opening/closing the disclosure and changing messages does not move its header.
- Tree rows show subject, sender, and date compactly; the selected row remains clear.
- The single mode button uses the established yellow pressed and dark unpressed styles.
- The compact untitled selection dropdown appears only in Whole discussion mode and retains its account preference.
- Focused functional tests pass and all affected visual comparisons pass.

## Assumptions

- The existing uncommitted icon changes belong to this work and are incomplete;
  finish them in C1 rather than committing them as finished work.
- No RPC, database, or preference migration is required.
- Do not update the test installation until implementation and visual review are
  complete, unless requested separately.
