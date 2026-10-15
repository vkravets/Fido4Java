# Mail processing

The Runner uses the database plugin's SQL store for echomail and netmail. Its
current default database is `jdbc:h2:./database/jftn10`. The same plugin context
is shared with other application services; the mail service does not open a
second database. JAM remains an echomail storage implementation and is not the
Runner's netmail store.

## Configuration

```text
Inbound=inbound
Outbound=outbound
Tmp=state
EchoPath=echo
SysOp=Your Name
System=Your Station
Link=2:500/101,2:500/102,session-password,127.0.0.1:24554
PacketPassword=2:500/101,pktpass
EchoArea=TEST,2:500/101=BOTH
EnforceEchoAccess=true
AutoCreateEcho=false
AllowTransit=false
DefaultRoute=2:500/101
TossIntervalSeconds=5
BinkpPort=24554
```

`Link` lists the remote address, local AKAs, session password and optional host.
Local AKAs identify local netmail destinations, including point numbers.
`PacketPassword` overrides the PKT password for that link. Without an override,
the existing link password is used. PKT passwords must fit eight ASCII bytes;
long session passwords require a separate PKT password. `-` selects no password.

`EchoArea` adds or updates SQL subscriptions. It accepts multiple comma-separated
addresses, each optionally followed by `=READ`, `=WRITE` or `=BOTH`; the default
is `BOTH`. READ permits export to that link, WRITE permits import from it.
Existing SQL subscriptions not mentioned in these declarations are retained.
Removing a declaration does not revoke a previously stored subscription.

Unknown areas and unauthorized posts go to quarantine by default. Legacy
automatic area creation can be selected explicitly with `AutoCreateEcho=true`
and `EnforceEchoAccess=false`.

Routes can also be declared in order:

```text
Route=3:600/*,2:500/101
DefaultRoute=2:500/101
AllowTransit=true
```

The first matching `Route` wins. Otherwise an explicitly configured final
destination is used, then `DefaultRoute`. Every next hop must be a configured
link. `DIR`, `CRA` and the Crash attribute require a configured direct link.
Transit requires `AllowTransit=true`; returning a message to its input link or
through a local address already present in its Via trail is refused.
Missing/forbidden routes retain the packet in quarantine rather than discard it.
Hold/HLD packets are offered on inbound sessions, not outbound calls. The
existing Runner polling schedule remains every ten minutes; Crash/CRA does not
introduce an immediate dial scheduler. KillSent deletes a netmail only after
acknowledgement, retaining its duplicate receipt.

## Processing and recovery

The Runner starts `MailService` after loading the transport, scheduler and
database plugins. A process lock in `Tmp` prevents two mail services from using
the same state directory. Existing inbound files and queues are processed at
startup and then periodically. The Groovy `tosspack` command reuses the running
service, or opens a temporary service for a single processing pass.

Incoming files use disk-backed partial storage. Before binkp sends `M_GOT`, a
completed receive file is atomically published beneath `Inbound/peers`, in a
directory for its configured session peer. Packet origins must match that peer.
The filename is derived from transfer identity, preserving the mail extension.
The stream remains readable for existing receive-event consumers. Publication
failure prevents acknowledgement. Equal completed copies can be reused; a
different copy is never overwritten. Processing failures retain the durable
file. Files placed directly in `Inbound` are treated as trusted offline input
and still undergo PKT origin/password/destination checks.

The tosser accepts loose `.pkt` files and ZIP mail bundles with weekday bundle
extensions. Legacy Type-2 packets with zero zone fields are resolved only when
configured addresses identify an unambiguous peer and destination; the receiving
session can disambiguate the peer. Limits are 64 MiB per packet, 256 MiB total unpacked data per bundle
and 4096 archive entries. Non-packet archive entries are rejected; other archive
formats are unsupported. Non-mail files received through binkp remain on disk
and are not interpreted as packets.

Rejected packets are saved as `.bad` in `Tmp`. If an existing name has different
contents, the original source is retained. Correct the cause and move a rejected
packet back to inbound with a `.pkt` extension to retry it. An unhandled message
can quarantine the remaining packet; successfully imported messages are not
lost. SQL errors are propagated rather than reported as successful writes.

Per-packet progress is persisted in `Tmp/toss-journal`. SQL duplicate receipts
are committed with message insertion, scoped by echo area or netmail endpoints.
Messages without MSGID use a stable content fingerprint. Receipts survive
deletion of message contents. Locally submitted messages receive an MSGID from
a persistent 32-bit sequence; imported legacy messages are not assigned fake
MSGIDs. Keep `Tmp`, the SQL database and `Outbound/jftn-mail` together when
backing up or restoring a node. Do not reuse an old progress journal with a
new, empty database. Receipt/journal retention is currently manual.

On opening an existing SQL database, missing netmail delivery records are
created without replacing the original messages. Previously sent mail stays
sent, unsent mail without a route becomes local, and unsent mail with a valid
route is queued. Invalid legacy route strings are retained in a BLOCKED state
for operator repair. The incorrect unique Boolean index on netmail `send` is
removed, and the echo ingress column is added when missing.

Outgoing Type-2+ packets contain next-hop envelope addresses and independent
final message addresses. Netmail exports INTL/FMPT/TOPT and appends a UTC Via
line. Echomail exports independent subscriber copies, preserves existing Origin
and tear lines, updates SEEN-BY/PATH, and omits point addresses from those 2D
lists. Cross-zone copies omit routing entries from other zones rather than
reinterpret their net/node numbers in the receiving zone. Local Origin lines
are generated only for locally submitted messages without an Origin.

An outbound packet and its metadata are persisted before queue publication.
Preparing or writing a packet does not mark it sent. `M_GOT` persists a receipt,
then updates SQL. If the process stops between those actions, the next pass
reconciles SQL from the receipt. Disconnect releases session claims so packets
can be retried. A lost protocol acknowledgement can cause a retransmission;
duplicate checking makes repeated imports safe.

## External JAM editor

SQL remains the authoritative store. Optional editor views are configured with
repeated `JamEcho=AREA,path[,AKA]` and `JamNetmail=path[,AKA]` entries. Paths omit
the `.jhr` suffix. The default AKA is the first configured local address;
`JamCharset=CP866` supplies the decoding fallback when no CHRS is present.

Only explicitly configured existing SQL echoes and netmail addressed to the
view's AKA are exported. Export is incremental, retains `.jlr`, and does not
recreate messages deleted in the editor. Keep `Tmp/jam-bridge` with SQL and JAM
when backing up. A busy editor defers synchronization without blocking transport.

GoldED+ can define these views using, for example:

```text
AREADEF TEST "Test echo" 0 Echo JAM C:\fido\jam\test 2:500/102 (Loc)
AREADEF NETMAIL "Netmail" 0 Net JAM C:\fido\jam\netmail 2:500/102 (Loc Pvt)
```

The service imports local unsent messages before preparing outbound. Sender AKAs
and addresses are validated; unknown echoes, unsupported file services and
missing routes are rejected with a log message. A missing MSGID on a new outgoing
post is durably assigned in JAM before SQL insertion. Existing identifiers are
preserved. Import receipts survive restarts; accepted posts are locked against
editing while queued. SENT is not set merely because SQL accepted a post.
Changing content under a previously submitted MSGID is a conflict, not a resend.

For accepted submissions the bridge records the next hop (netmail) or recipient
set (echomail). It sets JAM SENT and releases its edit lock only after durable
binkp acknowledgement receipts exist for every recorded recipient. Local
netmail to another local AKA completes on SQL acceptance. Missing recipients
defer an echo submission. Delivery means transfer to the configured next hop,
not proof that the final reader received the message. Receipts repair JAM flags
after restart, including KillSent netmail removed from SQL after acknowledgement.
The netmail view includes incoming, queued and retained sent mail for its AKA.

The AKA must be configured locally in both applications. JAM file locks are
implemented, but compatibility with an actual GoldED+ process has not yet been
certified on either Windows or Linux.

### Real HPT interoperability tests

Run `./gradlew :jtoss:hptInteropTest` with a Docker-compatible engine. This is an
explicit integration task; regular `test` and `build` do not start containers.
The first run builds HPT and Husky libraries from the commits in
`jtoss/src/integrationTest/resources/hpt/revisions.txt`. A prebuilt matching image
can be selected with `-Dhpt.image=your-image-tag`.

The host JUnit test controls a container with both HPT and Java 17. Java worker
processes use production JAM, SQL and tosser code. All database operations run
on the container's filesystem, without Windows bind mounts or host/VM locking
assumptions. Each scenario has its own SQL database, JAM areas and outbound.

Scenarios cover SQL-to-JAM export read by HPT and packed to a real PKT, HPT-posted
echo and point netmail imported into SQL across Java process restarts, and
historical HPT archives imported without modifying the source or redistributing
mail. The export test first proves an editor view does not scan as local mail,
then explicitly hands the fixture to HPT by setting LOCAL. HPT alone owns the
outbound in that phase; this does not change the production adapter policy.
A separate test holds the JAM header lock in a Java process, verifies that HPT's
write waits for it, and checks successful posting after release. Archive import
compares every JAM file before and after the operation.

Command output, pinned revisions, configuration, JAM files, SQL and packets are
saved in `jtoss/build/reports/hptInteropTest`; the JUnit HTML report is in
`jtoss/build/reports/tests/hptInteropTest`. CircleCI runs a separate
`hpt-integration` job. Interactive GoldED+ testing remains separate.

On Windows with Podman, use the Docker compatibility API and the WSL host override
described in `binkp/src/integrationTestShared/README.md`. Ryuk remains enabled.

### Historical archive import

Stop the Runner before running the offline command. It acquires the same mail
service lock, opens SQL, reads JAM using read-only handles and never processes
outbound. The target echo must already exist in SQL (or be configured by
`EchoArea`). Paths omit the JAM filename extension.

```powershell
.\gradlew.bat :jftn-bundle:importJamArchive "-PjamSource=C:/archive/ru.test" "-PjamArea=RU.TEST" "-PjamAka=2:500/102"
```

Omit `jamArea` to import netmail. Optional properties: `jftnConfig` (default
`jftn.conf`), `jamDatabase` (default `jdbc:h2:./database/jftn10`), and `jamCharset`
(default `CP866`, used only when CHRS is absent). The fallback AKA supplies
missing legacy addresses; choose it to match the original base.

Archive imports retain MSGID, dates, content and original attributes. Missing
historical MSGIDs remain missing. SQL stores the source path and import time;
archive status and duplicate receipts are committed with each message. Imported
echoes are excluded from export and netmail has ARCHIVE delivery state. A retry
after partial failure skips already committed messages. An existing live message
is retained without changing its delivery policy. Deleted JAM records are skipped.

The importer streams records while holding the JAM read lock. Do not pack or
replace the source while importing. Archive status is durable and survives SQL
reopening; enabling redistribution would require a separate explicit operation.

## Scope and remaining limitations

This implements the described import, local delivery, configured transit and
export pipeline. It is not a claim of complete compliance with every FTSC
document or independent-tosser certification.

- FileAttach, FileRequest and FileUpdateRequest processing is not implemented.
  Outgoing submissions using these attributes fail explicitly; such transit
  packets are quarantined. Local messages can be stored with their attributes,
  but no attachment or FREQ service is executed.
- Return-receipt/audit requests are preserved in the wire attributes; automatic
  receipt generation, PING/TRACE services and AreaFix are not implemented.
- Routing uses configured links and patterns; there is no nodelist-based route
  discovery, DNS node discovery or automatic zonegate selection.
- FTN addresses currently use the project's existing 4D model. Distinct domains
  sharing the same zone/net/node/point cannot be represented independently.
- SQL migrations are exercised against H2. Other database drivers require their
  own migration verification before production use.
- SQL echo export uses independent persistent checkpoints for each configured link
  and JAM view. Checkpoints advance after durable preparation, not acknowledgement;
  pending packets remain in the outbound spool until binkp confirms them. A new
  consumer starts with its own history. Automatic receipt cleanup remains future work.
- Hooks may reject messages, but core durable delivery does not rely on a hook
  to save netmail. Hook publication still uses the existing event bus.

## Processing and storage costs

Netty file writes are drained after each 64 KiB window and at the end of a file.
Outgoing packet streams open on first use, release their descriptors after each
attempt, and reopen and seek for retransmission without buffering the entire file.
Mail bundles are validated into temporary files before
processing starts; existing packet, bundle and entry limits still apply. Temporary
disk space up to the bundle limit is required while processing an archive.

JAM scans stream messages and skip unchanged areas using their modification counter,
file identity, lengths and timestamps. Delivery receipts are still checked while
submitted messages await acknowledgement. An external editor must follow JAM's
locking and modification-counter rules. SQL netmail search keys are backfilled into
`netmail_index` when an existing database is opened; message rows are retained.

JAM bridge updates append checksummed, forced records to `*.properties.log` alongside
legacy `*.properties` receipts. Preserve both kinds of files in backups. A truncated
final record is discarded on retry; a complete record with a bad checksum causes an
error. Keep SQL, outbound packets, acknowledgement receipts and bridge journals
together when backing up or restoring the node. Mail processing uses a separate lock
from session bookkeeping, and repeated receive-triggered scans are coalesced.

## Validation and specifications

Tests cover partial failure/restart, quarantine conflicts, loose PKT intake,
destination/password checks, local netmail, SQL migration, routing and Hold,
acknowledgement recovery, subscriber access, echo fan-out, control-line ordering,
legacy MSGID-free messages, JAM UTF-8 storage, receive publication failure and
real TCP exchange of both message kinds between two production Netty peers.
Existing historical inbound bundle fixtures remain part of the regression suite.
No new interoperability claim is made for an external tosser.

References used for the wire formats and control lines:

- [FTS-0001: packet/message layout and attributes](https://defsol.com/files/ftsc/fts-0001.016)
- [FTS-0004: echomail distribution](https://defsol.com/files/ftsc/fts-0004.001)
- [FTS-0009: MSGID and REPLY](https://defsol.com/files/ftsc/fts-0009.001)
- [FTS-4001: INTL, FMPT and TOPT](https://defsol.com/files/ftsc/fts-4001.001)
- [FTS-4009: Via tracking](https://defsol.com/files/ftsc/fts-4009.001)
- [FSC-0039: Type-2+ header extension](https://defsol.com/files/ftsc/fsc-0039.004)

FSC documents are proposals/reference material, distinct from FTS standards.
