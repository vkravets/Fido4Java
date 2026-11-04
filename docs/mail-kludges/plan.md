# Mail Kludges — implementation plan

Status: implementation in progress. S1–S3 are implemented; S4–S5, U1–U5 and T1–T5
remain pending.

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

## Tests and documentation

| ID | Planned verification or documentation | Level / model | Why |
| --- | --- | --- | --- |
| T1 | Add server tests for known/unknown/repeated controls, order, unchanged body, access control and draft round trips; check affected persisted data from the current test installation when needed. | High / Sol Medium | Use representative stored/serialized fixtures; map-only tests would miss duplicate loss and representation differences. |
| T2 | Check actual outgoing packet behavior with malicious reference metadata; verify body control injection is still rejected. | High / Sol Medium | Read-only UI does not enforce the server trust boundary; packet assertions must establish isolation. |
| T3 | Add browser tests for expansion, read-only presentation, reply/forward, draft reopening and absence of controls from quotes/submitted text. | Medium / Sol Low | Several workflows and captured RPC payloads need coverage using existing test patterns. |
| T4 | Add Storybook and pinned Linux VRT cases for expanded/collapsed blocks, long lines, both themes/languages and desktop/mobile. Review affected baselines, then compare without updates. | Medium / Sol Low | Existing scenarios reduce coding work, but visual review and Linux execution remain required. Report blocked verification explicitly. |
| T5 | Run formatting checks, focused Java/browser tests and frontend/Storybook typechecks. Update behavior docs and the relevant `TODO` entry after implementation. | Low / Luna Medium | Established commands and documentation are mechanical; diagnose failures at the complexity of their cause. |

Suggested dependency order: S1, then S3/S2/S4/S5, then U1–U5, with relevant tests
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
