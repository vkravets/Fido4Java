# Mail Kludges — implementation plan

Status: implementation in progress. S1–S5 and U1–U5 are implemented; U6 and
T1–T5 remain pending.

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
| U6 | Add an optional GoldED-like reader mode: show recognized kludges inline in original body order with distinct styling, controlled by a reader toggle. Keep the complete kludge disclosure for unknown controls and the default collapsed view. | High / Sol Medium | The current `kludges` list does not encode positions relative to body text. Define an ordered display-segment contract from the authoritative `Body` representation and a reliable known-control classification before changing the viewer. |

## GoldED+ comparison and U6 approach

GoldED+ hides kludges by default and offers reader keys to show kludges alone
(`K` / `Alt-K`) or together with hidden lines (`V` / `Alt-V`). Its `VIEWKLUDGE`
setting covers recognized kludges, including configured entries; the manual
describes them as color-distinguished lines. See the [reader key help](https://github.com/golded-plus/golded-plus/blob/master/cfgs/config/goldhelp.cfg)
and [VIEWKLUDGE behavior](https://koapp.narod.ru/tehlit/instruction/golded.htm).

U6 will adapt that interaction to the web reader without replacing U1–U3's
separate disclosure:

1. Inspect the message body's authoritative representation and define ordered
   display segments for ordinary text and recognized controls. Preserve repeated
   controls and their positions; do not infer positions from the extracted list.
2. Reuse an existing protocol-level control classifier where available. Document
   its recognized set and keep unknown controls in the complete disclosure rather
   than silently dropping them.
3. Add an accessible reader toggle, off by default. When enabled, render recognized
   controls inline at their original positions with distinct theme-aware styling;
   leave the full disclosure available for all controls.
4. Verify ordering, repeats, unknown-control visibility, escaped text, no duplicate
   presentation, and behavior in both themes/languages on desktop and mobile.

If the current `Body` abstraction cannot provide ordered segments without lossy
re-parsing, U6 must first extend the server/RPC contract; keep the existing
disclosure usable until that contract is established.

## Tests and documentation

| ID | Planned verification or documentation | Level / model | Why |
| --- | --- | --- | --- |
| T1 | Add server tests for known/unknown/repeated controls, order, unchanged body, access control and draft round trips; check affected persisted data from the current test installation when needed. | High / Sol Medium | Use representative stored/serialized fixtures; map-only tests would miss duplicate loss and representation differences. |
| T2 | Check actual outgoing packet behavior with malicious reference metadata; verify body control injection is still rejected. | High / Sol Medium | Read-only UI does not enforce the server trust boundary; packet assertions must establish isolation. |
| T3 | Add browser tests for expansion, read-only presentation, reply/forward, draft reopening and absence of controls from quotes/submitted text. | Medium / Sol Low | Several workflows and captured RPC payloads need coverage using existing test patterns. |
| T4 | Add Storybook and pinned Linux VRT cases for expanded/collapsed blocks, long lines, both themes/languages and desktop/mobile. Review affected baselines, then compare without updates. | Medium / Sol Low | Existing scenarios reduce coding work, but visual review and Linux execution remain required. Report blocked verification explicitly. |
| T5 | Run formatting checks, focused Java/browser tests and frontend/Storybook typechecks. Update behavior docs and the relevant `TODO` entry after implementation. | Low / Luna Medium | Established commands and documentation are mechanical; diagnose failures at the complexity of their cause. |

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
