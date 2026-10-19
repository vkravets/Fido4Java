# Runtime profiles

JFtn provides four installation profiles. Select the matching ZIP; its
`runtime-profile` marker supplies the default and rejects a conflicting
`RuntimeProfile` in `jftn.conf`. A source checkout without a marker defaults to
`full`, or uses the explicit profile in its configuration.

| Profile | Mail storage | Administration | Optional JAM bridge |
| --- | --- | --- | --- |
| Full | SQL | UI/RPC, files and robots | Yes |
| Headless | SQL | Files and robots; no UI/RPC libraries/listeners | Yes |
| Legacy | Primary JAM mailboxes and filesystem delivery state | Files, robots and an external editor | Not needed |
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

## Primary JAM and GoldED+

Legacy uses `JamMailStore`, built on the common `JamArea` format/locking code.
Full and Headless continue to use the optional `JamBridge` with SQL authoritative.
Do not enable that bridge in Legacy or mirror JAM into itself.

```ini
JamRoot=./mail/jam
JamCharset=CP866
EditorConfigPath=./editor
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

JFtn generates `editor/areas.lst` using GoldED's `AREADEF` syntax and
`editor/fidoconfig.areas` using Husky area declarations. The filename `areas.lst`
is not a separate standard. Include the generated AREADEF file in a personal
`golded.cfg`, or use `AREAFILE Fidoconfig` with the generated Husky area file.
`golded.cfg.example` is created once; personal editor configuration is never
rewritten. The Husky file is an editor catalog, not a complete HPT configuration.
Area catalogs are regenerated during processing, including newly created echoes.

The scanner accepts local, unsent, unlocked messages from configured local AKAs.
It persists a missing MSGID in JAM before submission, publishes the delivery
queue, then sets JAM `Snt`. `Snt` prevents the next scan from queuing the message
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
3. Check durable identities. Legacy also appends accepted mail to JAM. Transit
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
source peers, routes, delivery receipts and Legacy JAM publication/submission
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

Back up configuration, JAM, fileecho, received originals, queues and all of Tmp
as one stopped-node snapshot. SQL profiles also need their databases. Do not
remove journals to force a resend, overlay a different profile onto a live node,
or assume changing a marker migrates storage. A state directory claimed by
another profile is rejected. SQL/JAM imports and outgoing queues need a deliberate
migration plan before changing an existing installation's storage profile.
Identity/submission history has no automatic expiration in this implementation;
retain it with the mail state. Automatic history pruning and profile migration
are not provided by changing `RuntimeProfile`.

Tests cover exclusive ownership, restart, fixed destinations, independent echo
handoffs, local transit retention, JAM `Snt` recovery, editor deletion and generated
catalogs. Fresh JVM tests unpack reduced ZIPs and process real PKTs/TICs without
SQL/UI JARs, then start binkp on an ephemeral port. Existing HPT/JAM, BSO and HTick
suites remain the external wire/format checks. GoldED configuration syntax was
checked against its parser and example configuration; the interactive editor is
not exercised by these tests.
