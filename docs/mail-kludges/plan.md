# Mail Kludges — implementation plan

Status: proposed; implementation has not started.

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
| S1 | Add optional `list<string>` fields `MailItem.kludges` and `MailDraft.originalKludges` in `rpc/src/main/thrift/ui.thrift`. Use additive field IDs and regenerate bindings through the existing build. | Medium / Sol Low | Java/browser consumers and stored Thrift drafts share this contract; old drafts must still load. |
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
| U4 | Copy the source snapshot into plain replies, quoted replies and forwards; retain it after saving and reopening drafts. | High / Sol Medium | Separate entry points, optional RPC data and persistence must agree; old drafts have no snapshot. |
| U5 | Keep kludges outside the editable document, quotations, template expansion and submitted body. Add English/Ukrainian labels, both themes and mobile layout. | High / Sol Medium | Isolation must hold across composition paths and editor modes. Labels and styling alone are Low after data flow is settled. |

## Tests and documentation

| ID | Planned verification or documentation | Level / model | Why |
| --- | --- | --- | --- |
| T1 | Add server tests for known/unknown/repeated controls, order, unchanged body, access control, draft round trips and old drafts. | High / Sol Medium | Use representative stored/serialized fixtures; map-only tests would miss duplicate loss and representation differences. |
| T2 | Check actual outgoing packet behavior with malicious reference metadata; verify body control injection is still rejected. | High / Sol Medium | Read-only UI does not enforce the server trust boundary; packet assertions must establish isolation. |
| T3 | Add browser tests for expansion, read-only presentation, reply/forward, draft reopening and absence of controls from quotes/submitted text. | Medium / Sol Low | Several workflows and captured RPC payloads need coverage using existing test patterns. |
| T4 | Add Storybook and pinned Linux VRT cases for expanded/collapsed blocks, long lines, both themes/languages and desktop/mobile. Review affected baselines, then compare without updates. | Medium / Sol Low | Existing scenarios reduce coding work, but visual review and Linux execution remain required. Report blocked verification explicitly. |
| T5 | Run formatting checks, focused Java/browser tests and frontend/Storybook typechecks. Update behavior docs and the relevant `TODO` entry after implementation. | Low / Luna Medium | Established commands and documentation are mechanical; diagnose failures at the complexity of their cause. |

Suggested dependency order: S1, then S3/S2/S4/S5, then U1–U5, with relevant tests
alongside each change and T5 last. Review the complete feature with Sol Medium,
focusing on repeated controls, draft persistence, authorization and outgoing packets.
These are work assignments, not a requirement to use separate agents or commits.

## Assumptions and limits

- New messages without an original message have no kludge block.
- The UI offers no editing control for the reference snapshot. RPC clients can
  still supply metadata; the server validates its size and never trusts it as
  authoritative outgoing controls.
- Display reflects the stored server representation, not a guaranteed byte-for-byte
  copy of the original PKT.
- Verification uses isolated fixtures and temporary state, preserving installed
  node configuration, databases and mail.
- Complexity was assessed statically. No implementation, model comparison, build,
  browser test or visual verification was performed for this assessment.
