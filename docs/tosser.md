# Mail processing

The Runner uses the database plugin's SQL store for echomail and netmail. Its
current default database is `jdbc:h2:./database/jftn10`. The same plugin context
is shared with other application services; the mail service does not open a
second database. JAM remains an echomail storage implementation and is not the
Runner's netmail store.

## Configuration

Packed-message decoding and export recognize FTSC `LATIN-1`, `LATIN-2`,
`LATIN-5`, `LATIN-9`, `CP10000` and Ukrainian `CP848` identifiers, in addition
to JDK-supported encodings. CP848 uses the IBM/ICU mapping for Ukrainian letters
and euro, preserving ASCII control bytes for FTN framing. The mapping reference is
[IBM CP848 in ICU](https://github.com/unicode-org/icu-data/blob/main/charset/data/ucm/ibm-848_P100-1999.ucm).
For incoming legacy `CHRS: IBMPC`, a numeric `CODEPAGE` overrides the ambiguous
identifier, as specified by FTS-5003. It does not override an explicit modern
`CHRS` identifier. Unknown encodings retain the historical CP866 fallback.

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

Unknown areas and unauthorized posts go to quarantine by default. Automatic
creation requires an explicit sender allowlist, even when normal echo access
checks are disabled:

```text
EchoSubscriptions=database
AutoCreateEcho=true
EnforceEchoAccess=true
AutoCreateEchoFrom=1:500/101,WRITE
AutoCreateEchoFrom=1:500/103,BOTH
```

Each rule names an exact configured link, including its point number. `WRITE`
allows receiving mail from that sender; `BOTH` additionally allows sending mail
back. `READ`, wildcards, duplicate peers and unknown links are rejected. With no
rules, no sender may create an area. The master switch defaults to false.

After packet authentication and message hooks succeed, the SQL area and its sole
initial subscription are created in one transaction. Other peers receive no
permissions. Existing areas, permissions and export positions are never changed
by these rules; changing or removing a rule only affects future new areas.
If an area already exists without the needed permission, grant it explicitly in
the echo access screen. Packet retry uses the current rules without a restart.

Administrators configure the rule under **Station → Mail links → Create new echoes
from this link** and enable the master switch under **Tosser settings**. Saving
applies live. File-only operation uses repeated `AutoCreateEchoFrom` declarations
in `jftn.conf`. This automation requires database-managed subscriptions; file-mode
subscriptions must be switched deliberately before enabling per-link rules.
Older configurations that used only `AutoCreateEcho=true` with access checks
disabled must now name their permitted senders explicitly.

Routes can also be declared in order:

```text
Route=3:600/*,2:500/101
DefaultRoute=2:500/101
AllowTransit=true
```

The first matching `Route` wins. Otherwise an explicitly configured final
destination is used, then `DefaultRoute`. Every next hop must be a configured
link. `DIR`, `CRA`, `HLD`, `IMM` and the Crash/Hold attributes bypass ordinary
route rules and require a configured direct link. Immediate outranks Direct;
Crash+Hold is Direct, while Hold alone is held for an incoming connection.
Transit requires `AllowTransit=true`; returning a message to its input link or
through a local address already present in its Via trail is refused.
Missing/forbidden routes retain the packet in quarantine rather than discard it.
Hold/HLD packets are offered on inbound sessions, not outbound calls. The
existing Runner polling schedule remains every ten minutes; Crash/CRA does not
introduce an immediate dial scheduler. KillSent deletes a netmail only after
acknowledgement, retaining its duplicate receipt.

### Managed netmail routing

`Tosser.getNetmailRouter()` exposes the same policy used by local submissions and
transit. `explain(message)` returns a typed reason, next hop and matched rule ID;
`explainTransit(message, authenticatedPeer)` also checks relay permissions,
return-to-sender and local Via loops. Previews do not create mail or packets.
`Route` masks support `*` and `?` against full addresses, including explicit points.
All configured masks and active next hops are validated before processing starts.
Local destinations take precedence over forwarding rules. Direct/Crash never
fall back to an uplink. Hold continues to hold delivery to the selected next hop;
this is an explicit local policy, not an implementation of HPT's flavour syntax.

By default rules come from ordered `Route=mask,next-hop` declarations. For durable
administration set `NetmailRoutes=database` and use `saveRule(NetmailRoute)` /
`removeRule(id)` / `rules()`. SQL rules contain a stable ID, numeric priority,
destination mask, next hop and enabled flag. Lower priority values run first;
equal priorities are ordered by ID. Database mode does not merge config rules.
An empty table still permits direct links and `DefaultRoute`. Disabling a rule
retains its settings. CLI/RPC adapters can call this API; RPC methods are separate.

`AllowTransit=true` remains necessary for non-local incoming mail. Optional repeated
`TransitFrom=1:500/101` and `TransitTo=1:500/103` masks restrict authenticated input
links and selected output links respectively. With no lists the existing global
policy applies. Local delivery does not need transit permission.

Updating rules affects new deliveries only: the persisted next hop, prepared PKT,
spool key and acknowledgement history of accepted mail remain unchanged. No
automatic rerouting of an in-flight or already prepared delivery is performed.

The first-match approach was compared with HPT's
[`findSelfRouteForNetmail` / `findRouteForNetmail`](https://github.com/huskyproject/hpt/blob/master/src/scan.c).
Wire addresses and Via remain governed by FTS-4001 and FTS-4009. Route ordering,
SQL storage and operator permissions are application policy. Targets `host`,
`boss`, `hub`, `noroute` and durable `nopack`, priority selection, RouteFile and
optional outgoing directory links are described below. Automatic zonegate policy
and HPT configuration-file syntax as a whole are not imported.

## Processing and recovery

### Managed echo distribution

Echomail is distributed by readable subscriptions rather than netmail `Route`
rules. `Tosser.getEchoRouter().explain(message)` returns the eligibility of each
configured link: permitted, no read access, archived message, input link,
already in SEEN-BY, or local address. A point remains eligible even when its boss
is present in the 2D SEEN-BY list. Previews do not advance delivery positions.

Set `EchoSubscriptions=database` to administer subscriptions with the synchronized
`Tosser.configureEchoSubscription(peer, area, access, history)` and
`removeEchoSubscription(peer, area)` methods. The Runner then stops reapplying
`EchoArea` declarations at startup; existing SQL subscriptions remain authoritative.
With `EchoSubscriptions=config`, the additive `EchoArea` behavior is retained.
Administrators can use **Station -> Echo areas and link access** in the browser to
inspect permissions, create an area by granting link access, change READ/WRITE/BOTH
or revoke a subscription. Database-mode changes apply immediately without a
restart. In configuration mode the browser is read-only. WRITE permits incoming
mail from the selected peer; READ permits outgoing mail to that peer. Granting
access does not automatically retry packets already placed in quarantine.
The packaged mail-script configuration selects `database` mode for AreaFix.
READ, WRITE and BOTH are relative to the remote link. New areas can be created
by subscription administration, but peers must already be configured links.

`EchoHistory.ALL` begins a new consumer at the start of the area;
`EchoHistory.NEW_ONLY` starts after the newest existing SQL message ID. This
choice initializes only a previously unseen consumer. Updating access, removing
and restoring a subscription never rewinds or skips its existing checkpoint.
Mail accumulated while unsubscribed is therefore eligible after resubscription.
Removing a subscription revokes future import/export permission but deliberately
retains prepared outbound packets and receipts. Cancelling those packets requires
a separate explicit queue operation, not subscription editing.

For an explicit historical resend use
`rescanEcho(area, peer, firstId, lastId, requestId)`. The inclusive range uses SQL
message IDs. A readable subscription is required. This operation bypasses the
normal source-link/SEEN-BY suppression for the selected peer, never exports
archived imports, and does not change ordinary delivery checkpoints. Its separate
spool namespace and stable request ID make repeated preparation idempotent even
after acknowledgement. A new request ID explicitly requests another resend.
All subscription edits and rescans through the Tosser are serialized with export.

Optional repeated `EchoAka=TEST,1:500/101,1:500/202` bindings select a local AKA for
an area/peer pair. The AKA must already belong to that link's local AKA list.
It is used for the PKT envelope, newly generated local Origin, SEEN-BY and PATH;
the stored original and received Origin are retained. Duplicates and foreign
aliases fail at startup. The first configured AKA remains the default.

Eligibility and routing-line updates were compared with HPT's
[`forwardMsgToLinks`](https://github.com/huskyproject/hpt/blob/master/src/toss.c):
SEEN-BY suppresses normal duplicate fan-out and excludes point addresses;
PATH records processors. FTS-0004 defines these different roles, while FTS-4001
requires final netmail addresses to stay independent of the packet envelope.
Optional [Groovy mail scripts](mail-scripts.md) provide AreaFix and FileFix.
A nodelist router and RPC endpoints remain separate features.

The Runner starts `MailRuntime` after loading the transport, scheduler and
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
contents, the original source is retained. Correct the cause, then use the
administrator's **Station → Packet quarantine → Release and retry** action.
A legacy packet without receiving-session metadata requires an explicit expected
configured source link. The original session peer of a newly quarantined packet
cannot be substituted. Source/destination, PKT password and echo access checks
remain active. An unhandled message
can quarantine the remaining packet; successfully imported messages are not
lost. SQL errors are propagated rather than reported as successful writes.

Quarantine lists regular `.bad` files under `Tmp`, with bounded pages, reasons,
retry counts and timestamps. Corrupt bundles retained in inbound are not packet
quarantine entries. Files larger than 64 MiB or with damaged metadata require
manual inspection. Listing/action checksums reject stale selections if the packet
changes. **Archive packet** removes an item from active quarantine without
importing it; both archival and retry first preserve the exact original in
`Tmp/.quarantine/archive/<SHA256>.bad`. A failed retry retains the active file.
Recovery archive conflicts fail without removing the active packet. There is no
automatic retry of `.bad` files; retained inbound sources may be scanned again.

Keep `Tmp/.quarantine` metadata/recovery copies together with `Tmp/toss-journal`
in backups. Retry passes the unchanged packet bytes to the existing journal:
messages accepted before the original failure are skipped, including after a
restart. Do not edit a packet to fix its password; correct link configuration
and apply the configuration. UI link/password and basic tosser changes apply live
after successful validation; manually edited supported options require the UI's
**Reload configuration files** action. Database echo permission changes also take
effect immediately. Changes to plugin internals/paths still require a restart.

Per-packet progress is persisted in `Tmp/toss-journal`. SQL duplicate receipts
are committed with message insertion, scoped by echo area or netmail endpoints.
Messages without MSGID use a stable content fingerprint. Receipts survive
deletion of message contents. Locally submitted messages receive an MSGID from
a persistent 32-bit sequence; imported legacy messages are not assigned fake
MSGIDs. Keep `Tmp`, the SQL database and `Outbound/jftn-mail` together when
backing up or restoring a node. Do not reuse an old progress journal with a
new, empty database. Receipt/journal retention is currently manual.

New local MSGIDs use HPT's wire layout: `Z:N/F[.P] xxxxxxxx`, with eight lowercase
hexadecimal digits. Node addresses omit `.0`; points retain their point number.
The persistent sequence advances at least once per allocation and follows UTC
epoch seconds when the clock is ahead. Browser submissions use this same generator;
their request journal reserves the resulting MSGID so retries after restart reuse
it without allocating a new serial. Existing/imported MSGIDs and REPLY targets
are preserved.

Netmail tracking follows HPT's `addViaToMsg` layout:
`^AVia Z:N/F[.P] @YYYYMMDD.HHMMSS.UTC JFtn <version>`.
The address is our AKA for the outgoing link, not the final recipient. The final
fields identify the program/build, not another message ID. Packaged builds read
the `jtoss` JAR's implementation version; source-class execution uses `dev`.
`-SNAPSHOT` is displayed as `-dev`, and the version field is limited to ten
characters as required by FTS-4009. Existing Via records remain in order.

Reference behavior: [HPT tracking](https://github.com/huskyproject/hpt/blob/c2a91d64cee0e6cd357376d30c9285ee3f9c624f/src/scan.c),
[HPT message creation](https://github.com/huskyproject/hpt/blob/c2a91d64cee0e6cd357376d30c9285ee3f9c624f/src/perl.c)
and [Husky's sequence allocator](https://github.com/huskyproject/huskylib/blob/d3c0c1c3b643d7fce45f82d6c8378802e85d9205/src/genmsgid.c).
JFtn keeps its existing locked sequence file; it does not share Husky's `.seq`
directory or reset a counter that is ahead of the clock.

### Duplicate mail and packet-name collisions

Like HPT's move/discard policies, duplicate handling keeps repeated messages out
of regular mailboxes and forwarding. `DupeAction=move` is the default; it writes
separate, reconstructed single-message PKTs under `Tmp/dupes`. `DupePath` overrides
this location. `DupeAction=discard` omits those copies while retaining SQL duplicate
protection. There is deliberately no switch that disables SQL duplicate receipts.
These file-only options require a restart. They can be placed in `conf.d/*.conf`.

Duplicate detection uses MSGID within the echo area, or within netmail endpoints.
Legacy mail without MSGID uses headers and content, excluding mutable routing
lines. Netmail received in a different packet does not invoke local robots again.
Retry of the original delivery after a robot fails remains possible; a committed
SQL message alone does not prove that its processing/journal update completed.

The duplicate archive is not a delivery queue or an automatically exported echo.
It retains different duplicate bodies and peers separately, while an identical
repeated copy reuses its archive entry. Archive PKTs have short hexadecimal names,
an empty packet password and accompanying `.dupe` indexes for provenance/recovery.
Packet encoding normalizes transport fields; these are inspection copies, not
byte-for-byte originals. `PreserveReceivedFiles` separately controls retention of
the unchanged received originals. An archive write failure leaves processing
unfinished so the received packet can be retried after storage is repaired.

Short outbound names are allocated independently of duplicate-message hashes.
Existing packet names and retained owner records are skipped rather than
overwritten. Invalid sequence/owner metadata stops publication/acknowledgement;
it never marks a different delivery sent. Keep the sequence, ownership metadata
and full delivery receipts together when backing up the spool. Do not reset the
sequence or remove owner records as a routine cleanup operation.

The current SQL schema is the baseline. New databases are created directly in
this format. Opening an existing database does not migrate old tables, alter
indexes or columns, or rebuild delivery records and netmail search keys.
Databases from older schemas must be converted separately before use.

Husky route targets can be used in the existing `Route=pattern,target` file
syntax or the RPC rule's `nextHop` field: `host` selects `Z:N/0`, `boss` removes
the point, `hub` selects the start of the destination's 100-node block, and
`noroute` selects the final destination. These derived targets require a configured
remote link, or an explicitly enabled outgoing directory lookup. `nopack` stores mail without producing a PKT. It is
re-evaluated when processing runs after the rule changes; the first real next hop
is then persisted. Transit mail retains its transport source so release cannot
bypass source/target authorization or return-to-sender checks. Once a real next
hop is assigned, later rule edits do not reroute the prepared delivery.

`AllowVirtualOutbound=true` in `conf.d/27-virtual-outbound.conf` enables outgoing-only
lookups through the nodelist plugin. A configured link always wins. Otherwise a
listed node/point with IBN/INA data supplies the connection host/port and empty
session credentials; Down/Hold entries and unknown addresses are not dialled.
BSO mode can hand listed PSTN nodes to the external mailer too. Explicit packet
password declarations still apply, but no password or subscription is inferred
from the catalog. Manual polls use a session-local link, and scheduled processing
checks queued non-held virtual deliveries. No virtual link is inserted into the
trusted configuration, SQL link table, incoming server's link list or robot rules.

Unexpected files received from an unconfigured peer during an outgoing virtual
session are retained under `Tmp/untrusted-inbound/peers/<address>/`, outside the
normal automatic tosser/directory-feed scans. Incoming sessions still require a
configured link. Review retained files and explicitly configure/authorize the
peer before importing them. Disabling directory lookup prevents new virtual
calls; it does not erase queues or retained files.

Outgoing Type-2+ packets contain next-hop envelope addresses and independent
final message addresses. Netmail exports INTL/FMPT/TOPT and appends a UTC Via
line. Echomail exports independent subscriber copies, preserves existing Origin
and tear lines, updates SEEN-BY/PATH, and omits point addresses from those 2D
lists. Cross-zone copies omit routing entries from other zones rather than
reinterpret their net/node numbers in the receiving zone. Local Origin lines
are generated only for locally submitted messages without an Origin.

Packet serialization clears only Husky's local/transit/kill-after-send bits from
the wire attributes and leaves the SQL source unchanged. Packet password checking
is case-insensitive like HPT; binkp/UI passwords keep their own authentication
rules. Received mail clears the sender's Crash/Hold/Sent/Local delivery state
before route selection. Local netmail is private and unread; transit is marked
as forwarded. This prevents a received Hold/Crash bit from overriding our routes.

Reference: [HPT scan.c](https://github.com/huskyproject/hpt/blob/c2a91d64cee0e6cd357376d30c9285ee3f9c624f/src/scan.c)
and [Husky createXMSG](https://github.com/huskyproject/fidoconf/blob/62c963a6f078905660f2c0b3a6929383823190c5/src/afixcmn.c).

An outbound packet and its metadata are persisted before queue publication.
New netmail, echomail and explicit rescan packets use HPT-style `hhhhhhhh.pkt`
names (eight lowercase hex
digits), allocated from the persisted `Outbound/jftn-mail/packet.sequence`.
The packet name is independent of its full delivery identity: internal metadata,
owner records and acknowledgement receipts must be backed up with the queue.
Retries and restarts retain the allocated name and bytes. Existing UUID-named
packets are drained under their original names, without renaming an active queue.
FTS-0001 describes legacy 8.3 transport filenames; it does not require the HPT
sequence algorithm. HPT's BSO `.out`/`.hut`/`.cut` names are local mailer queue
files, not the naming scheme for our binkp-transmitted `.pkt` files.
Each fan-out delivery and explicit rescan gets its own name; acknowledgements
remain scoped to the message/request and recipient, not the short filename.
Preparing or writing a packet does not mark it sent. `M_GOT` persists a receipt,
then updates SQL. If the process stops between those actions, the next pass
reconciles SQL from the receipt. Disconnect releases session claims so packets
can be retried. A lost protocol acknowledgement can cause a retransmission;
duplicate checking makes repeated imports safe.

## External JAM editor

In Full/Headless, SQL remains the authoritative store. Legacy instead uses
[primary JAM mailboxes](transit-profiles.md#primary-jam-and-golded).
Configure the optional SQL-to-JAM plugin in
`conf.d/20-jam-bridge.conf`. Optional editor views are configured with
repeated `JamEcho=AREA,path[,AKA]` and `JamNetmail=path[,AKA]` entries. Paths omit
the `.jhr` suffix. The default AKA is the first configured local address;
`JamCharset=CP866` supplies the decoding fallback when no CHRS is present.

Synchronization is provided by the optional `jam-bridge` plugin module. The
standard bundle includes it at runtime; the mail-processing core depends only on
the `MailExtensionFactory` / `MailProcessingExtension` interfaces. Set
`JamBridgeEnabled=false` to use SQL/RPC without JAM editor synchronization. The
flag is checked before constructing a bridge: disabled bindings and charset
settings are not validated, and no JAM files or bridge journals are opened or
created. Existing files and journals are retained. The default is `true` for
existing configurations; without `JamEcho` / `JamNetmail` entries there are no
views to synchronize. Changing the flag requires restarting the node.

The Runner obtains extension factories from loaded plugins. Standalone
`MailRuntime` / `runOnce` calls discover the same factories through `ServiceLoader`.
Extensions are created per mail service, run under its processing lock before
and after packet export, and are closed before the database plugin is unloaded.
Without the plugin JAR, SQL storage, packet generation and binkp delivery still
work; JAM view settings have no effect. Offline historical JAM archive import is
independent of the synchronization plugin.

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
`jtoss/build/reports/tests/hptInteropTest`. GitHub Actions runs the HPT
`integration` matrix job after a successful main build. It verifies and runs the
compiled runtime and worker JARs from that build's artifact snapshot using
`-PciIntegrationSnapshot`, without recompiling application or test sources.
Interactive GoldED+ testing remains separate.

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

- FileAttach and FileRequest export require the optional external BSO mode.
  This includes peer-scoped transit payloads, RouteFile, KFS/TFS and `.req`
  generation; see [BSO operation](bso.md). Native binkp does not implement these
  operations. FileUpdateRequest remains a wire attribute; serving requests is
  the external mailer/request processor's responsibility.
- Return-receipt/audit requests are preserved in the wire attributes; automatic
  receipt generation and PING/TRACE services are not implemented.
- Routing uses configured rules and optional outgoing directory links. There is
  no automatic zonegate selection or DNS discovery independent of the directory.
- FTN addresses currently use the project's existing 4D model. Distinct domains
  sharing the same zone/net/node/point cannot be represented independently.
- SQL storage is exercised against H2. Other database drivers require their
  own compatibility verification before production use.
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
locking and modification-counter rules. SQL netmail search keys are written to
`netmail_index` together with new messages and retained across database restarts.

JAM bridge updates append checksummed, forced records to `*.properties.log` alongside
legacy `*.properties` receipts. Preserve both kinds of files in backups. A truncated
final record is discarded on retry; a complete record with a bad checksum causes an
error. Keep SQL, outbound packets, acknowledgement receipts and bridge journals
together when backing up or restoring the node. Mail processing uses a separate lock
from session bookkeeping, and repeated receive-triggered scans are coalesced.

## Validation and specifications

Tests cover partial failure/restart, quarantine conflicts, loose PKT intake,
destination/password checks, local netmail, SQL persistence, routing and Hold,
acknowledgement recovery, subscriber access, echo fan-out, control-line ordering,
legacy MSGID-free messages, JAM UTF-8 storage, receive publication failure and
real TCP exchange of both message kinds between two production Netty peers.
Existing historical inbound bundle fixtures remain part of the regression suite.
The explicit HPT tests additionally check the JAM/PKT scenarios described above;
they do not certify every external editor or tosser implementation.

References used for the wire formats and control lines:

- [FTS-0001: packet/message layout and attributes](https://defsol.com/files/ftsc/fts-0001.016)
- [FTS-0004: echomail distribution](https://defsol.com/files/ftsc/fts-0004.001)
- [FTS-0009: MSGID and REPLY](https://defsol.com/files/ftsc/fts-0009.001)
- [FTS-4001: INTL, FMPT and TOPT](https://defsol.com/files/ftsc/fts-4001.001)
- [FTS-4009: Via tracking](https://defsol.com/files/ftsc/fts-4009.001)
- [FSC-0039: Type-2+ header extension](https://defsol.com/files/ftsc/fsc-0039.004)

FSC documents are proposals/reference material, distinct from FTS standards.
