# Echomail crossposting: analysis and implementation proposal

Status: **not implemented**. Recorded on 2026-10-22 against JFtn revision
`e888f751`. This document preserves the research, design rationale and proposed
work; it does not describe an available UI control, RPC method or delivery guarantee.
The user requested that the proposal be retained for later implementation.
Field/method names and optional behavior below remain proposals.

## Scope and terminology

The requested feature is author-initiated publication of one composed message in
several explicitly selected echo areas. It includes the primary area and selected
additional destinations, submitted as one operation with a result per area.

This differs from netmail carbon copies to multiple people, forwarding an existing
message, transport fan-out to multiple links of one area, and automatic copying
of received/outgoing messages according to tosser rules. Those mechanisms must
not be conflated with this feature.

## Research and references

The official FTSC site did not respond during this investigation. The standards
were read from [the defsol FTSC mirror](https://defsol.com/f_ftsc), whose index says
it was copied from ftsc.org and last updated on 2025-01-22. Completeness against a
later FTSC catalogue was not established. The linked documents are the standards
texts, rather than secondary descriptions of them.

| Reference | Relevant behavior | Consequence for this proposal |
| --- | --- | --- |
| [FTS-0004.001](https://defsol.com/files/ftsc/fts-0004.001) | Echo area selection, Origin, SEEN-BY and PATH | Each destination is a separate area publication; distribution history belongs to that publication. |
| [FTS-0009.001](https://defsol.com/files/ftsc/fts-0009.001) | Unique message identifiers and exact REPLY linkage; intermediate systems preserve them | New author-created area publications receive separate MSGIDs. Retries and transport copies retain the already assigned MSGID. |
| [FTS-5003.001](https://defsol.com/files/ftsc/fts-5003.001) | CHRS describes message encoding, including header/control text | Validate the final outgoing content and keep the declaration consistent with its encoding. |
| [FTS-4000.001](https://defsol.com/files/ftsc/fts-4000.001) | Control paragraph syntax and preservation of unknown controls | Do not invent a required XC control paragraph or reinterpret incoming message text as an instruction. |
| [FTS-4001.001](https://defsol.com/files/ftsc/fts-4001.001) | Addressing controls normally used for netmail | Reuse the existing echomail creation/export path instead of adding netmail routing controls. |

`XC:`/`XP:` as described below are editor commands in GoldED+, not a replacement
for the standard AREA/MSGID/REPLY message representation. The standards do not
dictate our selector layout, batch RPC shape or failure-recovery interface.

### GoldED+

Inspected revision: `1a251c6375081453a410bb8d647be120b8902de3`.
The official remote HEAD matched that revision during this investigation.

- [gecarb.cpp](https://github.com/golded-plus/golded-plus/blob/1a251c6375081453a410bb8d647be120b8902de3/golded3/gecarb.cpp):
  `DoCrosspost` recognizes XC/XP outside classified kludge/tear/Origin lines;
  `AddXCAreas` expands area masks, avoids duplicate targets and handles the
  current area separately. The implementation supports `@file` lists and `#`
  entries omitted from the visible destination list.
- [gepost.cpp](https://github.com/golded-plus/golded-plus/blob/1a251c6375081453a410bb8d647be120b8902de3/golded3/gepost.cpp):
  the crosspost loop switches areas, uses the area's AKA/export charset and
  rebuilds the outgoing tear/Origin and control lines.
- [gectrl.cpp](https://github.com/golded-plus/golded-plus/blob/1a251c6375081453a410bb8d647be120b8902de3/golded3/gectrl.cpp):
  `DoKludges` generates the outgoing MSGID and retains the reply reference when
  present. This is author-side creation, not rewriting an in-transit message.
- [goldhelp.cfg](https://github.com/golded-plus/golded-plus/blob/1a251c6375081453a410bb8d647be120b8902de3/cfgs/config/goldhelp.cfg):
  describes processing/ignoring XC instructions and keeping, expanding in
  columns, compacting into lines or removing the visible list.

The useful model for JFtn is one authoring operation producing separate area
publications. Full emulation of every GoldED+ command is not required for the
first version.

### Husky HPT

Inspected revision: `c2a91d64cee0e6cd357376d30c9285ee3f9c624f`.
The official remote HEAD matched that revision during this investigation.

- [carbon.c](https://github.com/huskyproject/hpt/blob/c2a91d64cee0e6cd357376d30c9285ee3f9c624f/src/carbon.c):
  `carbonCopy` selects copies from configuration rules and avoids repeated
  copies to the same destination. `processCarbonCopy` carries the original
  control lines into a copy, records the source area and handles local storage
  versus re-export separately. This is not the same MSGID lifecycle as creating
  new crossposts in an editor.
- [hpt.texi](https://github.com/huskyproject/hpt/blob/c2a91d64cee0e6cd357376d30c9285ee3f9c624f/doc/hpt.texi):
  CarbonCopy/CarbonFromArea/CarbonOut describe automatic copying policy.
  CarbonKeepSb documents removal of SEEN-BY/PATH by default and the option to
  retain them. DupeBaseType describes duplicate keys, including area identity
  for the common duplicate base.

Do not implement user crossposting by adding automatic carbon rules. If automatic
copying is requested later, its policy belongs in the existing external script/
hook architecture, with reusable parsing and validation in Java.

## Current JFtn implementation

The current composer and `MailDraft` carry one echo area. `UiService.send` returns
one MSGID. There is no additional-area selector, batch submission result, durable
per-area progress or XC/XP command interpreter.

Existing building blocks:

- [ui.thrift](../rpc/src/main/thrift/ui.thrift): MailDraft, AreaInfo with writable
  sender AKAs, and the single-message send contract.
- [MailboxAccess](../rpc-node/src/main/java/org/fidonet/jftn/ui/MailboxAccess.java):
  account AKA ownership and exact-area posting authorization.
- [MailSubmissionService](../rpc-node/src/main/java/org/fidonet/jftn/ui/MailSubmissionService.java):
  validation, payload fingerprinting and persistent single-message ID reservation.
- [H2UserMailRepository](../account-storage-h2/src/main/java/org/fidonet/jftn/ui/H2UserMailRepository.java):
  owner-scoped drafts and submission reservations. One reservation currently
  contains one message ID, rather than a batch manifest and per-area progress.
- [Tosser](../jtoss/src/main/java/org/fidonet/jftn/tosser/Tosser.java) and
  [EchoExporter](../jtoss/src/main/java/org/fidonet/jftn/tosser/EchoExporter.java):
  local echo submission and existing per-link distribution.
- [MailIdentity](../fido-tools/src/main/java/org/fidonet/packet/MailIdentity.java)
  and SQL message receipts already distinguish echo identity by area and MSGID.
  They provide a useful basis for retry protection, not a completed batch API.

## Proposed implementation

### 1. Compact composer control

Add an echomail-only Crossposting button beside the primary conference. After
selection it shows the number of additional destinations. Its popup contains
search and checkboxes; the primary area is already included and additional areas
can be removed. Show the final destination count beside the publication action.
Keep recipient-name search, editor state, undo history and list visibility intact.

Use one explicitly selected sender AKA for the first version. Offer destinations
that allow that AKA to post; changing it revalidates the selection. Do not silently
switch identity for individual copies. Per-area sender selection is a possible
later feature, not part of the initial proposal.

### 2. Structured draft and RPC data

Extend the draft with additional echo destinations (provisional name:
`crossPostAreas`). Persist them with the draft. Normalize names and deduplicate
the complete set, including the primary area. Bound the list and input sizes.

Extend the submission result to include the operation identity and a result per
area: area name, reserved MSGID, state and error where applicable. A browser loop
of independent send requests would lose the relationship and recovery state;
the server should own the whole operation. Exact contract names remain to be
settled; edit Thrift sources rather than generated bindings.

Selector behavior, template expansion and optional XC/XP text conversion belong
in the UI. Posting authorization, durable submission state and publication belong
on the server. This work need not first reorganize all existing RPC modules.

### 3. Validate all targets before the first publication

Check each area exists, the selected AKA belongs to the account and has current
posting rights, the final message is valid and existing export-target validation
passes. If initial validation fails anywhere, publish nothing and identify the
affected destination. The send operation must repeat authorization even if UI
selection or an earlier preview succeeded.

Do not create areas, subscriptions, trusted links or routes as a side effect.
On recovery, revoked rights must block still-pending destinations rather than
being bypassed by a previously accepted preview.

### 4. Persist the batch before publishing

Store an immutable operation snapshot: owner, request identity, normalized target
set, content/options, creation time and one reserved MSGID per destination. Track
acceptance/progress durably for each destination. Persist enough information to
resume after a browser reload or node restart; an in-memory browser UUID alone
is insufficient.

Retried submissions use the same snapshot and IDs. Reusing an operation identity
with different content or targets must fail. Recovery must handle a crash after
an area accepted its message but before progress was updated, using storage
receipts/idempotent insertion to avoid another publication.

Do not delete the draft until all destinations have been accepted. Retain partial
progress and allow retry of unfinished destinations. An already started snapshot
must not silently change when the editor is modified.

This is not atomic network delivery. Report accepted-by-this-node, pending and
failed destinations accurately, for example "Accepted 2 of 3; retry remaining".
Already released copies cannot be rolled back. Continue to use normal echo export
and transport delivery rather than introducing a second sender.

### 5. Construct independent outgoing publications

Create a message for each target using the existing mail path. Give it the target
AREA, its reserved MSGID and correct CHRS/Origin. Keep the common author, recipient
name, subject and authored text. Do not transplant another area's SEEN-BY/PATH.
Keep a stable creation time across retries.

For a reply, retain the actual original MSGID in REPLY. Another destination may
not contain that parent; the reader should show its existing missing-parent
state rather than joining messages by subject or inventing a different parent.

Proposed default visible note, shown before sending:

```text
* Originally in ECHO.ONE
* Crossposted in ECHO.TWO, ECHO.THREE
```

Generate the note once for the frozen destination set. Saving/resuming a draft or
retrying publication must not duplicate it. Validate final text size and encoding
after adding it. Whether to expose compact/multiline/no-note choices in the first
version remains an implementation decision. UI translations stay in the UI;
the transmitted note's format must not change accidentally with interface locale.

### 6. Optional GoldED-style input compatibility

Recognize unquoted XC/XP instructions only in the beginning of an author's
outgoing composition and offer to convert them to the structured selector.
Require explicit confirmation of the resolved destinations. Exact area names
are sufficient initially. Never interpret quoted examples, incoming messages or
arbitrary stored body text as an instruction to distribute mail.

RPC clients use structured destinations; the tosser does not execute XC/XP text.
If masks are added later, show the expanded, authorized destination list before
submission and freeze it for retries. File-list expansion (`@file`), hidden
destinations (`#`), netmail carbon copies and automatic HPT-style carbon rules
are deferred.

## Verification and implementation order

1. Define the batch contract and durable state/recovery behavior, then implement
   backend validation and publication using existing storage capabilities.
2. Persist/resume structured destinations and operation identity through RPC and
   drafts; update clients for the baseline API change.
3. Add the composer selector, destination count, preview and partial-result/retry
   interface. Add XC/XP conversion after the structured path works.
4. Test unauthorized/missing targets, changed AKA/rights, duplicate destinations,
   ID uniqueness and stability, request fingerprint conflicts, double submission,
   draft resumption, encoding/size failures, replies and distribution controls.
5. Inject failures before/after each acceptance/progress boundary and restart the
   node. Verify no duplicate publications and no loss of pending destinations.
6. Use isolated HPT interoperability fixtures to verify acceptance into each area
   without lost copies or false duplicate suppression. This has not been run for
   the proposed feature.
7. Add functional browser scenarios and Storybook/VRT coverage for both themes,
   English/Ukrainian, desktop/mobile, including partial failure and changed rights.

No crossposting code, schema migration or feature-specific tests have been
implemented by recording this document. Existing single-area behavior remains
authoritative. Track the implementation in [TODO](../TODO).
