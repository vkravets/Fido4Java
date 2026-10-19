# Proposed runtime profiles and SQL-free transit

Status: design proposal, not implemented. Current headless operation supports
`RpcEnabled=false`, but mail processing still requires SQL. These profiles must
not be interpreted as available configuration switches yet.

## Profiles

- **Full:** current SQL mailboxes, UI/RPC, optional plugins and JAM bridge.
- **Headless:** SQL mail handling with file-managed settings and no UI/RPC
  listeners. A smaller distribution can later exclude browser/RPC dependencies.
- **Transit:** no SQL mailboxes, UI/RPC or JAM. File-managed links, routes and
  subscriptions; durable filesystem delivery state and duplicate history remain.

The profile describes storage and services. Delivery transport is independent:
native binkp or an external BSO mailer. Selecting transit must not silently enable
unconfigured incoming links or turn on directory-based trust.

## Receive, toss, assemble and deliver

1. Authenticate the configured peer and complete receipt of the file. Persist
   original name, source and receipt before scheduling processing. Preserve the
   received original according to the node's retention policy.
2. Unpack bundles and validate packet destination/password, message structure and
   encoding using the existing parsers. Record individual message checkpoints so
   a crash does not require successful messages to be forwarded again.
3. Check duplicates using durable MSGID/content identities. Netmail applies the
   same routing and transit authorization; echomail selects authorized readable
   subscriptions and applies the existing SEEN-BY/PATH rules.
4. Publish a per-delivery manifest and immutable outgoing message data in the
   spool. No reader mailbox or local echo archive is created. Persist selected
   next hops and recipient state; configuration edits must not rewrite in-flight
   deliveries. `nopack` stays pending for explicit route resolution.
5. Reuse the existing PKT writer, short-name allocation and Via generation to
   assemble packets. Optional bundle packing follows a separate explicit policy;
   raw PKTs remain valid. Attachments wait for receipts from their actual source.
6. Offer packets/files to native binkp or BSO. Mark each recipient complete only
   after transport acknowledgement. Retain identity/receipt history after payload
   cleanup so restart and retransmission do not create new deliveries.

TIC validation, subscribers, Replaces and scripts remain in the fileecho plugin.
Its file-managed provider can be used in transit; scripts and authorization need
interfaces independent of `MailDatabaseFacade`. A pure SQL-free profile must
also disable SQL-backed directory/auth plugins or provide separate file-backed
implementations; disabling only the mail database is insufficient.

## Implementation boundaries

First separate mailbox storage from routing/subscriptions, duplicate tracking,
delivery queues and processing checkpoints. Introduce focused interfaces and
adapt the current SQL implementation before adding a filesystem implementation.
The runner, mail runtime, extension factories and script API currently reference
the concrete database facade; those dependencies must become capabilities rather
than dummy SQL objects or a disposable in-memory database.

File journals need atomic publication, writer locking, identity validation and
restart recovery. Shared parsers, PKT/TIC writers and routing decisions should
remain common to all profiles. Profile validation must reject database-only
settings/plugins when their backing service is absent.

## Decisions to settle before implementation

- Netmail addressed to the transit node itself needs an explicit destination:
  service/robot processing, a retained local spool, or quarantine. It must never be
  silently discarded. Reader mailboxes would require the full/headless profile.
- Decide whether the first transit distribution includes AreaFix/FileFix and the
  fileecho plugin, or adds them after basic echo/netmail relay is verified.
- Choose bounded duplicate-history retention and a safe cleanup command. Existing
  queue data cannot be discarded merely when switching runtime profiles.

Verification should use the real BSO/HTick suites, native binkp, crash/restart
tests and mixed-recipient failures before declaring transit suitable for use.
