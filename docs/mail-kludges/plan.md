# Mail Kludges — implementation plan

Status: S1–S5, U1–U6 and T1–T5 are complete.

K1–K3 and U7 are complete. See the verification record below.

Execution agreement: commit the plan before implementation, then commit each plan
item separately. Include focused checks with the item they verify.

## Summary

Add a separate collapsible kludge block to netmail and echomail readers. In the
reply and forward composer, show the original message's kludges as read-only
reference information before the editable text.

## Complexity and model selection

| Level | Suggested model | Assignment criteria |
| --- | --- | --- |
| Low | Luna Medium | Local, mechanical work with settled interfaces and clear checks. |
| Medium | Sol Low | Changes across existing components using established patterns. |
| High | Sol Medium | Protocol fidelity, trust boundaries, or behavior spanning several layers. |

These are initial routing estimates from static repository inspection, not measured
model performance or time estimates. Complexity includes verifying correctness.
Provide each task with its contract, callers and acceptance cases. Settle shared
interfaces before dependent UI work. A combined task uses its highest complexity;
independent mechanical steps can be assigned separately at a lower level.

Escalate one level if investigation reveals missing contracts, potential data loss,
authorization concerns or repeated failures. Unresolved High work needs further
investigation; Sol Medium is not a guarantee of completion. Use Sol Medium for the
final review of serialization and submission boundaries.

## Follow-up — reader controls

| ID | Planned change | Level / model | Why |
| --- | --- | --- | --- |
| K1 | Add an Appearance profile preference for showing or hiding the full kludge block; default it to hidden. Keep the block's existing label “Kludges” (“Клуджі”). Persist the preference in the existing account `workspace` UI document. | Medium / Sol Low | Reuse the existing preference storage and settings pattern; ensure the setting controls the full block in both netmail and echomail readers. |
| K2 | Repurpose the existing reader toolbar button to toggle inline kludges. Add a kludge-specific icon and an accessible localized name/title; retain the session-wide inline visibility behavior across messages and navigation. | Medium / Sol Low | The session state and toolbar already exist; the icon and accessible control must remain clear in both themes and languages. |
| K3 | Verify both independent settings together: full block hidden/shown by profile, inline kludges toggled by the toolbar. Cover persistence/reload, netmail/echomail, both themes/languages, desktop/mobile Storybook states and pinned Linux VRT comparisons. | Medium / Sol Low | Existing UI test and visual workflows can be extended; verify the default and both control states without changing message data. |

Acceptance: with an unset profile preference, the complete kludge block is
hidden. Changing the Appearance preference persists across reload and applies in
both readers. The toolbar icon toggles only inline kludges and its session choice
continues to survive message/folder navigation. The full block retains the
“Kludges” label when enabled.

| ID | Planned change | Level / model | Status |
| --- | --- | --- | --- |
| U7 | When inline kludges are enabled, include the displayed ordered lines in quoted replies and the template `@quote`; render control characters as printable text. Keep short replies and forwards unchanged. | Medium / Sol Low | Complete |

Verification: helper unit coverage, all 31 kludge browser scenarios, frontend and
Storybook typechecks passed. The tests cover netmail/echomail, both desktop/mobile
paths, saved/reopened drafts, literal control values and submission without Ctrl+A.

### K1–K3 — reader controls completed

- The account `workspace.showKludgeBlock` preference defaults to false and shows
  the complete labelled block in both readers when enabled. The reader toolbar
  icon button independently toggles the existing session-wide inline state.
- Updated component stories, browser workflows and unit coverage for the two
  independent settings and their persistence. Corrected the button's accessibility
  while a message loads and aligned the profile checkbox on mobile.
- Verification: 105 frontend unit tests, both TypeScript checks, Storybook build,
  90/92 focused browser cases on the first run and the two corrected cases on
  rerun. Reviewed desktop/mobile English/Ukrainian screenshots in both themes;
  all 144 affected Linux VRT comparisons passed without snapshot updates.

## Server and RPC

| ID | Planned change | Level / model | Why |
| --- | --- | --- | --- |
| S1 | Add optional `list<string>` fields `MailItem.kludges` and `MailDraft.originalKludges` in `rpc/src/main/thrift/ui.thrift`. Regenerate bindings through the existing build; no compatibility with older RPC clients is required. | Medium / Sol Low | Java/browser consumers and stored Thrift drafts share this contract; preserve data used by the current test installation. |
| S2 | Return kludges only with full messages after existing access checks; omit them from message lists and search results. | Medium / Sol Low | Inspect every shared mapping caller to avoid exposing data or enlarging lightweight responses. |
| S3 | Extract Ctrl+A-prefixed lines from existing `Body` serialization, preserving order, repeated and unknown controls, including PATH/Via. Exclude ordinary text and SEEN-BY. | High / Sol Medium | The kludge map alone loses repeated controls; layout, parsed messages and constructed messages need coverage without changing serialization. |
| S4 | Persist reference kludges in the existing Thrift draft payload; no table migration is expected. Validate a total limit of 60000 UTF-8 bytes on the server. | Medium / Sol Low | Cover save/reopen, optional fields and oversized untrusted input; byte count differs from character count. |
| S5 | Ignore `originalKludges` during submission. Keep server-generated outgoing controls and rejection of Ctrl+A/routing injection in editable body. | High / Sol Medium | Trace submission through packet generation; client-supplied reference data must have no transport effect. |

## Viewer and editor

| ID | Planned change | Level / model | Why |
| --- | --- | --- | --- |
| U1 | Add one shared native `<details>` component, collapsed by default and absent for empty data. Use existing theme styles and native keyboard behavior. | Low / Luna Medium | A small isolated component; no editor transaction filtering is needed. |
| U2 | Place it next to the reader body and before the composer text field, with the label "Original message kludges" in the composer. | Low / Luna Medium | Existing netmail/echomail readers and composers share components. |
| U3 | Show monospace lines with a visible `^A` prefix, escaped text, selection and copying. Handle long lines and control characters. | Medium / Sol Low | Untrusted message text must render as text, without HTML or mail markup interpretation. |
| U4 | Copy the source snapshot into plain replies, quoted replies and forwards; retain it after saving and reopening drafts. | High / Sol Medium | Separate entry points, absent source metadata and persistence must agree; new messages can legitimately have no snapshot. |
| U5 | Keep kludges outside the editable document, quotations, template expansion and submitted body. Add English/Ukrainian labels, both themes and mobile layout. | High / Sol Medium | Isolation must hold across composition paths and editor modes. Labels and styling alone are Low after data flow is settled. |
| U6 | Add an optional GoldED-like reader mode: show recognized kludges inline in original body order with distinct styling, controlled by a reader toggle. Show unknown controls inline by default, with an account setting to reveal them only through the reader toggle; keep the complete collapsed disclosure. | High / Sol Medium | The current `kludges` list does not encode positions relative to body text. Define an ordered display-segment contract from the authoritative `Body` representation and a reliable known-control classification before changing the viewer. |

## GoldED+ comparison and U6 approach

GoldED+ hides recognized kludges by default (other hidden lines have a separate
visibility flag) and offers reader keys to show kludges alone
(`K` / `Alt-K`) or together with hidden lines (`V` / `Alt-V`). Its `VIEWKLUDGE`
setting covers recognized kludges, including configured entries; the manual
describes them as color-distinguished lines. See the [reader key help](https://github.com/golded-plus/golded-plus/blob/master/cfgs/config/goldhelp.cfg)
and [VIEWKLUDGE behavior](https://koapp.narod.ru/tehlit/instruction/golded.htm).

U6 adapts that interaction to the web reader without replacing U1–U3's
separate disclosure:

1. Inspect the message body's authoritative representation and define ordered
   display segments for ordinary text and all controls, with server-owned classification. Preserve repeated
   controls and their positions; do not infer positions from the extracted list.
2. Reuse an existing protocol-level control classifier where available. Document
   its recognized set and provenance in [classification.md](classification.md).
   Always transmit unknown controls and display them by default; support an
   account preference to reveal them only through the reader toggle.
3. Add an accessible reader toggle, off by default. When enabled, render recognized
   controls inline at their original positions with distinct theme-aware styling;
   leave the full disclosure available for all controls.
4. Verify ordering, repeats, unknown-control visibility, escaped text, no duplicate
   presentation, and behavior in both themes/languages on desktop and mobile.

Implemented through `MailItem.bodyLines`, derived from the same reconciled
`Body` content serialization. The full disclosure remains independently
available, including recognized lines also visible in inline mode; no ordinary
text or controls are duplicated within the reading pane itself.

## Tests and documentation

| ID | Planned verification or documentation | Level / model | Why |
| --- | --- | --- | --- |
| T1 | Completed: server tests cover known/unknown/repeated controls, order, unchanged body, access control and draft round trips. No installed-state inspection was needed for this test-only change. | High / Sol Medium | Use representative stored/serialized fixtures; map-only tests would miss duplicate loss and representation differences. |
| T2 | Completed: decoded actual outbound packets after RPC submission with malicious reference metadata; verified rejection of editable-body control injection without publication or request-ID consumption. | High / Sol Medium | Read-only UI does not enforce the server trust boundary; packet assertions must establish isolation. |
| T3 | Completed: browser scenarios cover expansion, read-only presentation, actual reply/forward entry points, saved-draft reopening and absence of controls from quoted/submitted text. | Medium / Sol Low | Several workflows and captured RPC payloads need coverage using existing test patterns. |
| T4 | Completed: pinned Linux VRT covers expanded/collapsed reader blocks, inline controls, long lines, editor references and warnings across both themes/languages and desktop/mobile. Reviewed new baselines, then compared without updates. | Medium / Sol Low | Existing scenarios reduced coding work; reviewed Linux screenshots and ran the final comparison. |
| T5 | Completed: focused Java, RPC, frontend and browser checks passed; formatting and both frontend/Storybook typechecks passed. Behavior documentation is current and the completed work was removed from `TODO`. | Low / Luna Medium | Established commands and documentation were mechanical; Gradle used valid up-to-date results for the unchanged RPC contract test. |

Suggested dependency order: S1, then S3/S2/S4/S5, then U1–U6, with relevant tests
alongside each change and T5 last. Review the complete feature with Sol Medium,
focusing on repeated controls, draft persistence, authorization and outgoing packets.
These are work assignments, not a requirement to use separate agents. Each plan
item is committed separately under the execution agreement above.

## Assumptions and limits

- RPC backward compatibility is not required during development. Preserve existing
  persisted data used by the current test installation in the test folder; inspect
  affected storage before changing its format. Optional kludge fields represent
  legitimately absent metadata, not support for old RPC clients.

- New messages without an original message have no kludge block.
- The UI offers no editing control for the reference snapshot. RPC clients can
  still supply metadata; the server validates its size and never trusts it as
  authoritative outgoing controls.
- Display reflects the stored server representation, not a guaranteed byte-for-byte
  copy of the original PKT.
- Verification uses isolated fixtures and temporary state, preserving installed
  node configuration, databases and mail.
- Complexity was assessed statically; no model performance comparison was run.
  Implementation verification is recorded below as work progresses.

## Implementation record

### S1 — optional RPC fields

- Added optional field 14 `MailItem.kludges` and field 18
  `MailDraft.originalKludges`, both `list<string>`, preserving existing field IDs.
- Regenerated Java and browser bindings using the repository Gradle tasks.
  Generated files remain build outputs and are not committed.
- Added focused Thrift binary round-trip coverage for ordered repeated control
  lines and new drafts without source metadata. Server population,
  persistence validation and UI rendering remain subsequent plan items.
- Verification passed on Windows with the pinned development profile: both
  `MailKludgeContractTest` cases, `spotlessCheck`, `verifyJavaFormatting`,
  `:rpc-node:compileJava`, `:web-ui:typecheckFrontend` and
  `:web-ui:typecheckStorybook`. Java/browser contract generation also passed.
  Browser functional tests and VRT were not run because S1 changes no UI behavior.
- Reviewed S1 under the development RPC policy: retained optional fields for
  legitimately absent metadata, removed assertions fixing wire field IDs, and
  described the absent-source draft test by current behavior rather than legacy
  compatibility. No legacy adapters or fallback paths were introduced.

### S3 — control-line extraction prerequisite for S2

- Added `Body.getKludgeLines()`, selecting Ctrl+A-prefixed lines from existing
  serialization without changing the body, layout or transport serialization.
- Verified parsed duplicates, unknown controls, PATH/Via, exclusion of AREA,
  SEEN-BY and ordinary text, empty controls and edits to constructed messages.
- All five `BodySerializationTest` cases passed on the pinned Windows profile;
  applied the repository formatter to the two affected Java files.
- Completed before S2 so RPC can use the correct shared extraction immediately.

### S2 — full-message RPC metadata

- Full `message` responses now populate `kludges` through `Body.getKludgeLines()`
  after the existing mailbox/area authorization. Body text remains separate.
- Summary mappings for ordinary/recent lists, parent/reply context and archive
  search omit kludges. Documented this boundary in the RPC guide.
- All 30 tests in `EchoReaderRpcTest` and `UiRpcIntegrationTest` passed on the
  pinned Windows profile, including full echo/netmail metadata, TCP/HTTP transport,
  summary/search exclusion and denied or revoked read access.
- Root `spotlessCheck` and `verifyJavaFormatting` passed after both changes.
- UI behavior is unchanged; browser functional tests and VRT were not run.

### S4 — persisted reference snapshots and UTF-8 limit

- Existing Thrift draft payloads retain `originalKludges` without a database
  schema change. Added aggregate 60000-byte UTF-8 validation to the shared draft
  validator; absent/empty lists are valid and null entries are rejected.
- Verified the exact byte boundary, multibyte text, aggregate overflow across
  multiple strings, ordered/repeated controls after storage reopen and owner
  isolation. TCP/HTTP tests confirm oversized updates preserve the saved draft,
  including after restarting RPC.
- All 28 tests in `UserMailStateStoreTest` and `UiRpcIntegrationTest` passed on the
  pinned Windows profile. Tests used temporary databases; the existing test
  installation and its data were not changed.
- Updated the RPC guide. Browser functional tests and VRT were not run because
  this change does not alter UI behavior.
- Root `spotlessCheck` and `verifyJavaFormatting` passed.

### S5 — submission isolation

- Submission now clears `originalKludges` from a copy before fingerprinting and
  message creation. Previously the snapshot was already excluded from message
  text but still affected the idempotency fingerprint. Draft validation and the
  existing body-control rejection remain in force.
- RPC tests verify a retry with reference metadata removed or cleared reuses its
  MSGID after restart; editable-body changes still conflict with the same request
  ID. Ctrl+A, AREA injection with CR/LF and NUL remain rejected in editable text.
- PKT tests cover both netmail and echomail with forged MSGID/REPLY/CHRS,
  addressing, PATH/Via and unknown reference controls. They decode the generated
  packet and verify authoritative controls, addresses and unchanged plain text.
- Updated the RPC guide and decision record. All verification uses isolated test
  state; UI behavior and the installed test station are unchanged.
- Verification passed on the pinned Windows profile: 23 RPC integration cases,
  all 8 `MailEncodingTest` cases, `spotlessCheck` and `verifyJavaFormatting`.
  The first PKT run exposed an echo fixture using a point sender without Origin;
  corrected that fixture to a node sender and reran all encoding/PKT cases.
  Browser functional tests and VRT were not run for this server-only change.

### U1 — shared read-only disclosure

- Added the reusable native `<details>` component with an external summary label;
  it stays closed by default and does not render for an empty list. Native summary
  keyboard and disclosure semantics are retained.
- Added Storybook examples for populated and empty lists. Reader/composer placement,
  localization and visible `^A` formatting remain later UI items.

### U2 — reader and composer placement

- Added the disclosure next to the shared message body, covering both netmail and
  echomail readers. Added the original-message disclosure before the composer
  editor; it remains outside the editable text field.
- Added English and Ukrainian labels and a Storybook example showing both
  placements. Reply and forward drafts receive their snapshots in U4.

### U3 — readable, selectable kludge lines

- Render kludges as escaped text in monospace, converting C0/DEL controls to
  caret notation and C1 controls to visible `\xNN` notation. Long lines wrap
  within the available width; text remains selectable for native copying.
- Added a Storybook/VRT state covering HTML-like input, control characters and
  long unbroken lines. The eight pinned Linux VRT combinations passed after
  baseline review (desktop/mobile, light/dark, English/Ukrainian).

### U4 — original snapshots in reply and forward drafts

- Shared snapshot copying now populates `originalKludges` for netmail and
  echomail replies, including plain and quoted reply actions, and forwards.
  Missing or empty source metadata leaves the reference block hidden.
- Frontend tests use the generated Thrift draft constructor and verify ordered
  repeated/unknown controls, independent copies, save/reopen behavior and
  preservation of editable body text. Server persistence remains covered by S4.
- Review corrected a Storybook fixture that would have added reference kludges
  to a new message, and removed the test mock's assumption that every draft has
  reference metadata. List fixtures continue to omit full-message kludges.
- Verification passed: application/Storybook typechecks, formatting of changed
  files, all 101 frontend unit tests and Storybook build. Reviewed the affected
  Linux baselines and passed 32 VRT comparisons without snapshot updates,
  including unchanged new-message states in both themes/languages and sizes.

### U5 — editor, template and submitted-body isolation

- Confirmed the existing composer keeps the reference disclosure outside the
  editable document and supplies an explicit plain-text context to templates.
  Existing English/Ukrainian labels and theme styles cover both screen sizes.
- Added dedicated Storybook states and browser checks for plain/quoted replies,
  forwards and echomail. They verify keyboard disclosure, literal control/HTML
  text, wrapping, no editable reference controls, source/visual/preview modes,
  quotation actions and template replacement. Captured composer save/send events
  preserve the reference metadata while keeping it out of the editable body.
  Server submission isolation remains covered by S5.
- Updated the UI guide and replaced the stale TODO claim that no viewer exists.
  Remaining inline-view and final audit work stays recorded in this plan.
- Verification passed: eight focused Playwright cases, application/Storybook
  typechecks, changed-file formatting and Storybook build. Generated/reviewed
  16 new pinned Linux baselines for expanded netmail/echomail references, then
  passed all 16 comparisons without updates across themes/languages and sizes.

### Review follow-up — oversized source snapshots

- Fixed replies/forwards becoming unsavable or unsendable when the original
  control lines exceeded the draft's 60000-byte limit. Snapshot creation now
  copies a prefix of complete lines by UTF-8 byte size; the source is unchanged.
- Added optional Thrift field 19 `originalKludgesTruncated`. It persists a visible
  English/Ukrainian notice after reopening, including when no whole line fits.
  Submission clears both reference fields before fingerprinting/message creation.
- Verification passed: exact-limit and multibyte frontend cases (102 unit tests),
  six focused Java cases including a 60000-byte snapshot saved/sent across
  restart and persisted truncation state, 10 browser cases, both typechecks,
  Storybook build and Java/frontend formatting. Reviewed 16 new Linux baselines;
  24 comparisons passed without updates, including ordinary new-message states.

### U6 — optional inline reader

- Added ordered `MailBodyLine` records to full-message RPC responses only. Shared
  `Body` content serialization preserves text/control positions and repetitions;
  existing message/packet serialization tests remain unchanged and pass.
  Summaries, search and reply context omit the new display field.
- The server classifies a documented set of control names for inline display.
  Unknown controls remain in the complete disclosure. Parsed AREA and Origin/
  SEEN-BY trailers stay outside the plain reader content; parsed PATH/Via trailers
  follow it.
- Added an English/Ukrainian reader checkbox, initially off by default and reset
  on message changes (superseded by the session-state follow-up below). Known
  controls remain literal, with visible control notation and a
  theme-aware border/color. Ordinary quote/signature formatting is preserved.
  Editable drafts, templates and submission continue to use plain `body`.
- Verification passed: 49 fido-tools tests, two RPC contract tests, 31 RPC
  integration cases, the frontend unit suite, both typechecks and formatting.
  All 12 focused browser cases passed, including keyboard toggle, duplicate/order
  checks, literal markup, full-list access and navigation reset. Reviewed 16 new
  pinned Linux baselines; 32 comparisons passed without updates, including prior
  ordinary-reader and disclosure states across both themes/languages and sizes.

### U6 follow-up — sourced classification and unknown-control policy

- Replaced the initial short name allowlist with a reusable server classifier
  derived from GoldED's FTS/FSC/XXX/RFC tables, including historical aliases,
  case rules and gateway headers. [Classification sources](classification.md)
  distinguish recognized extensions from adopted standards and record exact
  GoldED and FTSC snapshot revisions. The official FTSC site was unavailable;
  cited document revisions were verified through the documented 2021 mirror.
- `MailBodyLine` now includes required `known` classification. Full messages
  retain all unknown/repeated control display records in stored order. These
  response-only fields do not change persisted draft or installed mail formats.
- Unknown controls appear inline by default. Personal Appearance settings persist
  `workspace.unknownKludges` as `always` or `toggle`. The message checkbox reveals
  all controls and never changes editor/template data. Its initial per-message
  reset was superseded by the session-state follow-up below.
- Verification passed: 51 fido-tools tests, two RPC contract tests, 31 RPC
  integration cases (with the eight echo cases rerun after adding explicit
  known/unknown assertions), 103 frontend unit tests, both TypeScript checks,
  Storybook build, 14 browser cases, root Java formatting and changed frontend
  formatting. The whole frontend Prettier check reports an existing violation in
  unchanged `stories/protocol-fixture.ts`; that file was not reformatted.
- Generated affected Linux baselines only: 24 updated and 16 new screenshots.
  Reviewed default unknown visibility, toggle-only and unknown-only readers,
  inline controls and personal settings. Final comparison passed 56/56 without
  updates across both themes/languages and desktop/mobile, including unchanged
  ordinary reader and full-disclosure baselines.

### U6 follow-up — session-scoped reader switch

- Replaced the message-local checkbox state with one session ref in the shared
  workspace. Moving between messages, folders, netmail/echomail, settings or
  recreating the reader keeps the user's choice. The initial state is off.
- `sessionStorage.jftn-reader-inline-kludges` restores the choice after tab
  reload. New login, logout, account/session change and authorization expiry
  reset it. Blocked storage keeps the in-memory choice usable. The switch is not
  included in the persistent `workspace` account document; the unknown-control
  visibility policy remains an independent account preference.
- Updated behavior/source documentation and recorded browser versus server
  storage ownership in the browser interface guide.
- Verification passed: 104 frontend unit tests, both TypeScript checks,
  Storybook build, changed-file Prettier checks and 15 browser cases, including
  message changes, reader remounts, reload of both switch states and auth-session
  cleanup. Linux VRT comparison passed 56/56 across both themes/languages and
  desktop/mobile without changing any baseline. No Java code or contracts changed.

### T1 — server coverage audit completed

Audited existing tests and added missing cross-layer assertions. This item changes
tests and documentation only; production code, RPC contracts and persisted formats
are unchanged. Installed test-node state did not need inspection or migration;
all verification used isolated temporary configuration, account storage and SQL mail.

| Acceptance case | Evidence |
| --- | --- |
| Known/unknown/repeated controls and stored text order | `BodySerializationTest`, `MessageTailTest`, `FtscCompatibilityTest` and `ReaderKludgesTest` retain parser/serializer and classifier coverage. New `UiRpcIntegrationTest.preservedStoredKludgeOrderAndPlainBodyAcrossTcpAndHttpReads` checks exact control/display lists after SQL storage for both netmail and echomail, including repeated CHRS/CUSTOM and PATH or multiple Via trailers. |
| Unchanged body and originals | The new stored-reader case checks exact plain text, literal markup, repeated full reads on TCP and HTTP, and unchanged source/stored serialization after reading. AREA and parsed Origin/SEEN-BY remain outside the display records. |
| Lightweight response boundaries | Netmail/echo summaries, recent echo pages, reply context and archive search omit both `kludges` and `bodyLines`; existing assertions were completed where only one field was checked. |
| Authorization | Existing foreign-netmail and exact-area tests remain in place. New `EchoReaderRpcTest.enforcedLiveReadRightsForFullKludgeRecordsOnHttpAndTcp` denies full records before READ permission and after changing it to WRITE, using the same live sessions on both transports. |
| Draft fidelity and owner isolation | New `UiRpcIntegrationTest.isolatedKludgeDraftSnapshotsByOwnerAcrossTransportsAndRpcRestart` checks echo/netmail drafts with the same ID under different accounts, identical duplicate controls, Unicode, empty truncated snapshots, deletion isolation and RPC/account-store restart. Revoking/regranting echo rights hides/restores the unchanged snapshot. |
| Contract presence and limits | `MailKludgeContractTest` now round-trips unknown display flags and identical repeated records, and distinguishes an empty truncated snapshot from absent metadata. Existing integration cases cover the exact 60000-byte boundary, UTF-8 and aggregate oversize rejection without replacing the saved draft. |

Final verification succeeded: 51 fido-tools tests, three RPC contract tests and
34 focused RPC integration cases (nine echo-reader and 25 UI cases), plus root
`spotlessCheck verifyJavaFormatting`. Gradle reused unchanged fido-tools/contract
results in the final matrix; the affected RPC integration suites passed together
after correcting the new fixture's configured point link and valid PKT password.
No browser or container checks were needed for this server-test-only item.
At T1 completion, T2–T5 remained pending, including the separate outgoing-packet audit.

### T2 — actual outgoing-packet audit completed

- Retained the existing `MailEncodingTest` direct PacketWriter tests and added
  eight end-to-end cases to `UiRpcIntegrationTest`: netmail/echomail through each
  of TCP and HTTP, for both reference isolation and editable-body rejection.
- Reference-isolation cases save/reopen the draft, submit it through RPC, run
  the real mail runtime/tosser and decode the generated outbound spool PKT.
  Forged MSGID/REPLY/CHRS, INTL/FMPT/TOPT, FLAGS/PID, PATH/Via, repeated unknown
  controls, AREA/SEEN-BY/Origin text and embedded CR/LF/NUL in reference strings
  do not reach the wire. The real MSGID, REPLY, UTF-8 declaration, message text,
  envelope addresses and server-generated Origin/PATH or INTL/Via remain intact.
- Retrying with both reference fields unset returns the same MSGID, preserves
  exactly one packet and leaves its bytes unchanged. The input draft is unchanged
  and the saved draft is removed only after successful submission.
- Rejection cases attempt ten editable-body variants per mailbox/transport:
  SOH at the start, inside text or after CR/LF/CRLF; AREA at the start or after
  those line boundaries; and NUL. Every send returns INVALID_ARGUMENT while
  retaining the saved draft. Processing/releasing mail afterwards produces no
  packet or stored outgoing message. A corrected body succeeds with the same
  request ID, proving rejection did not reserve an incompatible fingerprint.
- Verification passed: 33 UI RPC cases (including the eight new cases), eight
  encoding/packet cases and three PacketWriter cases, plus root `spotlessCheck`
  and `verifyJavaFormatting`. All 44 cases passed in one final invocation.
  No production code, contracts, installed node state or UI/VRT baselines changed.
  At T2 completion, T3–T5 remained pending.

### T3 — browser workflow audit completed

- Added `KludgeWorkflow.stories.ts`, which mounts the actual NetmailView and
  EchomailView components with a shared typed service fixture. Summary responses
  omit body/control fields; full messages contain interleaved known and repeated
  unknown controls. Save/list/reopen operations use detached JSON snapshots,
  and the fixture captures the draft passed to the service's send method.
  This is component/browser integration with a mocked service, not a live node;
  T1/T2 separately verify backend persistence and actual outbound PKT behavior.
- Added ten complete entry-point workflows: plain/quoted replies and forwarding
  on desktop for netmail/echomail, plus quoted replies/forwarding through the
  visible mobile controls. Desktop uses English/light and mobile Ukrainian/dark.
  Echo forwarding switches into the real netmail view. The short-reply reader
  action is desktop-menu-only in the existing UI; shared plain-reply composer
  isolation remains tested at both sizes by the earlier U5 cases.
- Each workflow opens the native disclosure by keyboard, checks literal and
  read-only controls, enables inline viewing, starts composition through reader
  actions, edits text, saves, navigates to the real draft list and reopens it.
  Captured save/send data retains duplicate controls and original plain body,
  contains no reference controls in the editable body, and does not expand the
  reference's `@quote`. Original text occurs once for quoting/forwarding and is
  absent from a plain reply. Reopening preserves typed text without reapplying
  the template, and source message data remains unchanged.
- Added absent/truncated/fully-omitted snapshot workflows through save/reopen/send,
  verifying the persistent omission notice and absent disclosure where appropriate.
  A new-message case verifies no source disclosure or omission notice appears.
- Verification passed: 29 focused browser cases (14 new and 15 existing), both
  application/Storybook TypeScript checks, Storybook build, changed-file Prettier
  and `git diff --check`. Test corrections use the existing mobile direct buttons
  and Thrift's null representation of absent fields. No production UI or server
  behavior changed; VRT baseline review remains the separate T4 audit.

### T4 — visual audit completed

- Added two reader VRT states using the existing Storybook kludge story: the full
  reference block expanded with inline viewing off and with inline viewing on.
  The fixture includes repeated CHRS records, an unknown control, literal markup
  and a long control line. Existing states cover the collapsed reader, unknown
  visibility setting, read-only composer references and truncation/omission notices.
- Generated 16 new baselines in the pinned Linux/amd64 Playwright 1.64.1 container,
  covering desktop/mobile, light/dark and English/Ukrainian. Reviewed representative
  screenshots for all combinations of viewport, theme, language and inline state,
  plus existing mobile long-line and composer-reference baselines. Long lines wrap
  without clipping or horizontal overflow; control text remains literal.
- The final comparison ran without snapshot updates: all 104 feature-relevant
  visual cases passed, including the 16 new cases. Storybook build, Storybook
  typecheck and changed-file Prettier check passed. No production behavior or
  unrelated baseline changed.

### T5 — final feature audit completed

- Ran 22 focused fido-tools tests covering body serialization, message tails,
  FTS compatibility and reader-control classification. The three RPC contract
  cases were confirmed up to date by Gradle; their successful run is recorded
  under T1.
- Ran `UiRpcIntegrationTest` and `EchoReaderRpcTest`; all selected integration
  cases passed, including stored ordering, authorization, draft ownership and
  restart, outbound packet isolation, and editable-control rejection.
- Ran all 104 frontend unit tests and all 29 kludge browser workflows. Frontend
  and Storybook typechecks, Storybook build, root `spotlessCheck`, and
  `verifyJavaFormatting` passed. T4 records the 104-case pinned Linux visual
  comparison without snapshot updates.
- Confirmed [web UI behavior documentation](../web-ui.md) and the RPC guide
  describe the current reader, settings, session storage, draft and submission
  behavior. Removed this completed feature from the root `TODO` list.
- No implementation files changed in T5. All planned kludge work is complete.
