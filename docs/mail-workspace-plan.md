# Mail workspace implementation

Accepted on 2026-10-20. Implementation record; verification evidence is recorded below.

## Interaction contract

- One application menu, collapsible to labelled icons, plus a contextual mail list
  and a reading/composition pane. Conference selection belongs to the list pane.
- Reading pane positions: right, below, or a separate screen. Resizable boundaries
  support pointer and keyboard input. Persist netmail/echo preferences independently.
- Echo flat/threaded presentation and compact/comfortable list density are independent.
- Mobile uses list, message and compose screens with Back, retained scroll position
  and protection against discarding unsaved drafts.
- Mail rendering and editing share FTN text parsing. Visual styles remain plain-text
  markers: bold, italic, underline, reverse; distinguish quote depths and endings.
  Never render incoming mail as HTML or allow templates to grant posting identities.
- Mail themes are independent of application light/dark mode. Built-ins, read-only
  operator files under themes/mail, and personal UI overrides have separate ownership.
- The editor supports visual/source modes, undo/redo, quoting and outgoing preview.
  Keep MSGID/REPLY and transport kludges under server control.
- Templates cover new, reply, quoted reply and forward, with recipient/sender/date/
  area variables, quote insertion and cursor placement inspired by GoldED+.
  Templates are data, not executable Groovy/JavaScript; no arbitrary file includes.
- Preserve backend subscription/AKA authorization, explicit netmail release and
  normal echomail export. Node administration remains administrator-only.
- EN/UK and light/dark remain supported. No installed-node changes or push implicitly.

## Ordered commits

1. [x] Record design and implementation sequence.
2. [x] Persist personal workspace preferences with isolation and validation.
3. [x] Collapsible application navigation and workspace shell.
4. [x] Accessible reusable resizable split pane.
5. [x] Netmail right/bottom/separate preview layouts.
6. [x] Shared echomail layout with conference selection in the list pane.
7. [x] Independent list density and flat/threaded presentation.
8. [x] Mobile screens, Back and retained list position.
9. [x] Adapt administration/settings to the shell.
10. [x] Shared FIDO text parser with behavior tests.
11. [x] Highlighted message reader.
12. [x] Built-in and operator-file mail themes.
13. [x] Personal theme UI, preview, import/export.
14. [x] Visual/source editor foundation and plain-text round trips.
15. [x] Editor styles, quoting, history and preview.
16. [x] Netmail editor integration.
17. [x] Echomail editor integration.
18. [x] Persistent template model and safe expansion.
19. [x] Built-in new/reply/quoted/forward templates.
20. [x] Template management UI and preview.
21. [x] Composer selection and template defaults by kind/AKA/area.
22. [x] End-to-end checks, affected Linux VRT, documentation and removal of replaced code.

Each functional step carries focused tests. Visible states need Storybook and
affected desktop/mobile EN/UK light/dark VRT. Compare reviewed Linux references
without update flags. Preserve unrelated local edits. Record evidence and any
remaining limitations here at stage boundaries.

## Reference

[GoldED+ reference](https://github.com/golded-plus/golded-plus/blob/master/manuals/gold_ref.txt):
STYLECODES and The Message Template. In particular, @position is cursor placement,
@caddr is the author's address, and @quote inserts the original quote.

## Verification on 2026-10-20

- Root Spotless and Java layout verification passed.
- RPC/account, RPC-node and Web UI Java suites passed. Focused restart tests also
  verified owner-isolated original reply context and metadata size/kind rejection.
- Frontend unit tests: 72 passed; application and Storybook type checks passed.
- Component/workspace browser checks: 57 passed, including all catalogue smoke
  batches, 1280/820/375px layouts, both desktop composer panes, dirty-draft guards,
  scroll restoration, new-draft template reset, formatting history and forwarding.
- Actual HTTP application and setup workflows passed (12 application scenarios
  and one setup scenario), with the generated Thrift browser client.
- Reviewed affected Linux references: 232 updated and 168 added PNGs. A separate
  comparison without snapshot updates passed all 400 affected scenarios across
  EN/UK, light/dark and desktop/mobile. Unrelated references were not refreshed.

Native browser workers are bounded to two, matching CI: four simultaneous cold
catalogue loads exceeded the local story-ready assertion deadline. The same
scenarios and final full component run passed with two, without increasing test
timeouts or adding retries. Mobile checks emulate viewports; they are not physical
phone certification. See [Mail workspace](mail-workspace.md) for behavior, palette
files, supported template tokens and the loaded-search/GoldED-subset limits.

## Compact desktop follow-up on 2026-10-20

Commit `1fe7968c` aligned the desktop implementation with the approved reference:
flush panes, a 44px header, 216/56px navigation, shared outline SVGs, compact
controls, sender/subject/date rows and a grouped reader action menu. Display
options retain preview position and density; short replies preserve REPLY without
the quote. Links and Logs shortcuts open existing admin panels directly. Mobile
layout refinement remains a separate follow-up in TODO.

Frontend unit tests (72), component/workspace checks (59), real HTTP application
scenarios (13) and first-run setup (1) passed, as did application and Storybook
type checks. New stories cover the populated desktop frame, reader actions,
expanded More menu and display options. Representative Linux references were
reviewed in both themes, EN/UK and desktop/mobile: 296 existing PNGs changed and 32 were added;
24 other selected references remained unchanged.

The first comparison caught a new popup story captured before Vue's media-query
update. Its fixture now waits for Vue's queued updates, and VRT requires the
expanded desktop panel to be visible before capture. Only that incorrect new
reference was regenerated. No comparison threshold or timeout was relaxed.
Commit `7384d8ab` also preserves mobile display controls after Escape and restores
desktop focus to the opener; two additional focused browser checks passed on
the refreshed production/Storybook build, along with both type checks.

The final independent comparison without snapshot updates passed all 352
selected Linux scenarios on the refreshed build, with zero pixel differences.
The complete catalogue now contains 976 PNG references; unrelated images were
not refreshed. The main comparison report is
`web-ui/build/reports/playwright/visual/index.html`.
