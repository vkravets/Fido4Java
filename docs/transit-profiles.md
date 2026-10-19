# Runtime profiles

JFtn provides four installation profiles. Select the matching ZIP; its
`runtime-profile` marker supplies the default and rejects a conflicting
`RuntimeProfile` in `jftn.conf`. A source checkout without a marker defaults to
`full`, or uses the explicit profile in its configuration.

| Profile | Mail storage | Administration | Optional JAM bridge |
| --- | --- | --- | --- |
| Full | SQL | UI/RPC, files and robots | Yes |
| Headless | SQL | Files and robots; no UI/RPC libraries/listeners | Yes |
| Legacy | JAM echoes, JAM or MSG netmail, filesystem delivery state | Files, robots and an external editor | Not needed |
| Transit | Filesystem delivery state; no reader mailboxes | Files and robots | No |

Transport is independent of the profile: native Netty binkp, SSH binkp, or an
external BSO mailer. Selecting a profile never grants incoming trust or enables
netmail transit implicitly. Configure trusted `Link` entries, packet passwords,
subscriptions, `AllowTransit`, transit masks and routes explicitly.

## Installation

There are eight public ZIPs: `jftn-VERSION-PROFILE-no-jre.zip` and
`jftn-VERSION-PROFILE-with-jre.zip` for each profile. A runtime package includes
both locked Linux x64 and Windows x64 JREs. Launchers select the matching runtime;
other supported hosts can use the no-JRE package with installed Java 17+.
Libraries, plugins, Groovy and editable scripts remain separate.

Full retains the first-run browser wizard. For other profiles, copy
`jftn.conf.example` to `jftn.conf`, replace the example identities/links, and start
the launcher. Missing working directories and profile-appropriate plugin files
are created without replacing existing configuration. Reduced profiles do not
start a setup HTTP server when configuration is absent.

SQL-free packages physically omit the database plugin, H2, ORMLite, directory SQL
catalog, SQL-to-JAM bridge and UI/RPC JARs. They retain fileecho and external mail
scripts. File-managed policy is selected explicitly:

```ini
RuntimeProfile=legacy
RpcEnabled=false
EchoSubscriptions=config
NetmailRoutes=config
RobotAccessSource=files
FileEchoSource=files
NodeDirectoryEnabled=false
JamBridgeEnabled=false
AllowVirtualOutbound=false
```

Use `RuntimeProfile=transit` for the spool-only profile. These profiles reject
SQL-only policy sources, SQL directory imports and directory-based virtual links;
they do not substitute an in-memory database. Fileecho still supports TIC,
FileFix, retained duplicates and scripts. AreaFix administers persisted file
subscriptions. An explicit `EchoArea` declaration reapplies its grants on startup;
robot-created subscriptions and automatically created areas remain in the catalog.
File changes require restart; Full's existing UI reload path remains available.

## Primary mailboxes and GoldED+

Legacy uses `LegacyMailStore`, with common `JamArea` code for JAM and a separate
`MsgNetmailMailbox` for classic numbered `*.msg` netmail files.
Full and Headless continue to use the optional `JamBridge` with SQL authoritative.
Do not enable that bridge in Legacy or mirror JAM into itself.

```ini
JamRoot=./mail/jam
JamCharset=CP866
EditorConfigPath=./editor
LegacyNetmailFormat=jam
# Optional explicit paths and per-area local AKA:
# JamEcho=test.echo,./mail/jam/custom-test,1:500/100
# JamNetmail=./mail/jam/netmail,1:500/100
```

Without explicit bindings, netmail uses `JamRoot/netmail` and each echo uses
`JamRoot/echo-TAG`. `JamRoot` defaults to `EchoPath`. AKA bindings must be local,
and different areas cannot share a JAM base. Existing paths/encoding are recorded
in the state directory; incompatible edits require an explicit data migration.
The bases are primary mailboxes: editor read/deleted flags and last-read files
are preserved, and restarting does not resurrect a deleted message.

### Classic MSG netmail

To use an individual file per netmail message while keeping echoes in JAM, choose
the following **before the first start of a new Legacy installation**:

```ini
LegacyNetmailFormat=msg
MsgNetmailPath=./mail/netmail
MsgCharset=CP866
```

`MsgNetmailPath` defaults to `JamRoot/netmail-msg`; `MsgCharset` defaults to CP866
and is used when an editor message has no CHRS/CHARSET/CODEPAGE declaration.
Explicit message encoding overrides that fallback. `JamNetmail` cannot be combined
with MSG mode. Existing installations reject format/path/encoding changes rather
than hiding old mail or migrating it implicitly. JAM remains the default.

Each `1.msg`, `2.msg`, etc. has the 190-byte little-endian FTSC stored-message
header and NUL-terminated text. These files are **mailbox messages**, whereas
outbound `*.pkt` are transport packets containing one or more messages.
JFtn writes INTL/FMPT/TOPT as appropriate and preserves existing message numbers,
read counters, reply links and editor `lastread` files when marking a message sent.
Deleting a message in the editor does not recreate it during journal recovery.
JFtn publishes new files with a no-replacement hard link: the mailbox filesystem
must support hard links (for example NTFS or a normal Linux local filesystem).

The implemented header variant is **FTS1**, not the OPUS timestamp variant.
The generated GoldED AREADEF catalog selects `FTS1` and sets `FIDOMSGTYPE FTS1`;
the Husky catalog uses `NetmailArea ... -b Msg`. If using only `AREAFILE Fidoconfig`,
also set `FIDOMSGTYPE FTS1` in your personal GoldED configuration.
JFtn locks individual messages while scanning and writing metadata; a busy
message defers the processing cycle with an error. Honor editor locks and avoid
editing/packing/renumbering the mailbox during a scan. Unsent scans inspect only
the header of already-sent/received messages instead of loading their bodies.

The reference behavior was checked against
[HPT scan.c](https://github.com/huskyproject/hpt/blob/c2a91d64cee0e6cd357376d30c9285ee3f9c624f/src/scan.c)
and GoldED+
[FidoHdr](https://github.com/golded-plus/golded-plus/blob/1a251c6375081453a410bb8d647be120b8902de3/goldlib/gmb3/gmofido.h),
[message reading](https://github.com/golded-plus/golded-plus/blob/1a251c6375081453a410bb8d647be120b8902de3/goldlib/gmb3/gmofido3.cpp)
and [Husky configuration parsing](https://github.com/golded-plus/golded-plus/blob/1a251c6375081453a410bb8d647be120b8902de3/goldlib/gcfg/gxhpt.cpp).

JFtn generates `editor/areas.lst` using GoldED's `AREADEF` syntax and
`editor/fidoconfig.areas` using Husky area declarations. The filename `areas.lst`
is not a separate standard. Include the generated AREADEF file in a personal
`golded.cfg`, or use `AREAFILE Fidoconfig` with the generated Husky area file.
`golded.cfg.example` is created once; personal editor configuration is never
rewritten. The Husky file is an editor catalog, not a complete HPT configuration.
Area catalogs are regenerated during processing, including newly created echoes.

The scanner accepts local, unsent, unlocked messages from configured local AKAs.
It persists a missing MSGID in the selected mailbox before submission, publishes
the delivery queue, then sets `Snt` (JAM bit 0x10; MSG bit 0x0008).
`Snt` prevents the next scan from queuing the message
again; transport acknowledgement remains a separate delivery state. A persistent
submission receipt covers a crash between queue publication and `Snt`. Editing
an accepted submission or clearing `Snt` does not create a new delivery: make a
new message/MSGID to resend intentionally. FILE/FRQ submissions use the existing
external BSO requirements instead of bypassing attachment validation.

## Receive, toss and deliver

All profiles reuse the same authentication, parsers, PKT/TIC writers, routes,
SEEN-BY/PATH and duplicate archive policy.

1. Receive a complete file with authenticated peer/name metadata; retain its
   original according to the configured retention policy.
2. Validate packets and messages, recording per-message processing checkpoints.
3. Check durable identities. Legacy also appends accepted mail to its primary mailbox. Transit
   creates no reader mailbox; locally addressed netmail is retained in the local
   records of `MailStatePath/netmail` and can be handled by service/robot scripts.
   It is never silently discarded.
4. Freeze echo fan-out in the durable outbound spool before accepting the echo.
   Netmail records retain their selected next hop across restart; `nopack` stays
   pending until a real route is available. Later subscription/route changes do
   not rewrite already prepared deliveries.
5. Generate ordinary short-name PKTs, Via and attachment batches using shared
   delivery code. Native binkp or the external BSO mailer owns transfer.
6. Complete each recipient only after acknowledgement. Compact handed-off echo
   payloads and acknowledged netmail snapshots, retaining identity/receipt
   records for duplicate suppression. Received originals follow their separate
   retention policy.

`MailStatePath` defaults to `Tmp/mail-state`. Its exclusive writer lock covers
catalog and queue recovery. Atomic versioned records retain message identities,
source peers, routes, delivery receipts and Legacy mailbox publication/submission
checkpoints. Queue state is operational data, not a rebuildable cache.

For an offline Legacy/Transit processing cycle, stop the running station and use:

```sh
./jftn.sh --process-mail
```

The command uses the same locks, scripts and delivery code as the server. It does
not start RPC, UI, polling or a binkp listener. In native mode it prepares outgoing
packets; the next server session transfers them. In BSO mode it also prepares
external-mailer handoffs.

## Recovery, switching and verification

Back up configuration, JAM/MSG mailboxes, fileecho, received originals, queues and all of Tmp
as one stopped-node snapshot. SQL profiles also need their databases. Do not
remove journals to force a resend, overlay a different profile onto a live node,
or assume changing a marker migrates storage. A state directory claimed by
another profile is rejected. SQL/JAM imports and outgoing queues need a deliberate
migration plan before changing an existing installation's storage profile.
Identity/submission history has no automatic expiration in this implementation;
retain it with the mail state. Automatic history pruning and profile migration
are not provided by changing `RuntimeProfile`.

Tests cover exclusive ownership, restart, fixed destinations, independent echo
handoffs, local transit retention, JAM/MSG `Snt` recovery, editor deletion and generated
catalogs. Editor simulation tests write JAM echoes and JAM/MSG netmail, scan and
parse the actual outgoing PKTs, then restart and rescan without duplicate packets.
Independent MSG-header fixtures cover CP866, KOI8-R, UTF-8, lock contention and
damaged files. Fresh JVM tests unpack reduced ZIPs (including Legacy in MSG mode)
and process real PKTs/TICs without
SQL/UI JARs, then start binkp on an ephemeral port. Existing HPT/JAM, BSO and HTick
suites remain the external wire/format checks. GoldED configuration syntax was
checked against its parser and example configuration; the interactive editor is
not exercised by these tests.
