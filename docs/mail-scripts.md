# Scripted mail services

Configure the script list in `conf.d/40-mail-scripts.conf`, file areas in
`42-filefix.conf`, and directory feeds in `43-directory-feed.conf`. Shared catalog
options belong in `30-nodelist.conf`. Manage AreaFix/FileFix/TIC authorization in
the **Mail robots** UI by default. For file-only authorization, select
`RobotAccessSource=files` and put AreaFix peers in `41-areafix.conf` and
FileFix/TIC peers in `42-filefix.conf`.
See [plugin configuration](plugin-configuration.md) for startup and upgrade rules.
Keep every `MailScript` line from the examples below in the one script-list file;
place the remaining lines in the corresponding service file.

`MailScript` is repeatable. All three stock handlers are enabled by default with
external paths under `./scripts/mail-scripts/`, relative to the node working
directory. AreaFix uses database subscriptions by default. Peer passwords,
file areas and directory feeds must be configured separately; an empty feed
list leaves directory import inactive without opening a catalog.
A value can also use a `classpath:` resource for compatibility. Scripts are trusted
operator code and have the JVM's permissions.

MailRuntime loads scripts before its first inbound scan. Each script supplies
`register(api)` and optionally `unload(api)`. Startup fails if registration
fails. Scripts unload in reverse order after processing stops. Automatic hot
reload is deliberately not enabled while messages are being processed.

Authorization can use `RobotAccessSource=database` (default) or `files`, regardless
of whether UI/RPC is enabled. See [access sources and offline administration](robot-access.md).
Scripts use `api.robotAccess` for credential checks; inactive file rules are not
parsed or used as a fallback in database mode. Technical areas/feed settings
remain file-based. Existing file-only configurations remain compatible.

The API exposes `config`, `database`, `tosser`, and `state`. Register synchronous
callbacks with `onNetmail { message, packetPeer, deliveryKey -> boolean }` and
`onScan { ... }`. Netmail callbacks run only for local destinations, after
packet checks and durable storage. The first `true` consumes the request for
subsequent script handlers; the original remains in local mail. Throwing an
exception retains the packet without advancing its processing journal.
Never use `message.setValid(false)` to indicate successful robot handling.

`packetPeer` identifies the validated packet origin. It does not by itself
prove that a routed message's author is that peer. Robots must authorize the
message origin and their own password before making changes.

`state.get(key)` and `state.put(key, text)` store small durable values below
`Tmp/scripts`, using hashed filenames, forced writes and atomic replacement.
Multi-value updates are not a transaction. Changes must be retry-safe; outgoing
mail should use stable delivery keys. Missing atomic move support is an error.
Scans execute under the same processing lock before outbound mail export.
Failed scans are logged and retried on subsequent scans; they must preserve
their source inputs until completion.

## AreaFix

The `*Peer` examples below apply to explicit file mode. In the default database
mode, create equivalent access rules in the UI or perform a deliberate one-time
file import; merely adding a peer line to a fragment does not grant access.

```ini
EchoSubscriptions=database
MailScript=./scripts/mail-scripts/areafix.groovy
AreaFixPeer=1:500/101|separate-robot-password|test.*,local.news|READ
```

The last field is READ or BOTH and controls access granted by new subscriptions.
Requests to AreaFix or AreaMgr are case-insensitive. Their destination must be
a local AKA and their author must equal the validated packet peer. Forwarded
requests are not supported. A robot password is mandatory and distinct from
the packet/session password. Unauthorized requests receive no automatic reply.

Supported commands: area or +area, -area, wildcard masks with `*`/`?`, %HELP,
%LIST [mask], %QUERY/%LINKED [mask], %UNLINKED [mask], %RESCAN area [count].
Rescan defaults to 100 recent messages, capped at 10000, and requires a readable
subscription. Latest messages are selected by SQL ID in descending order; gaps
in IDs do not affect the limit. New subscriptions start with NEW_ONLY; request
a rescan explicitly to fetch history. Unsubscribe removes the subscription,
including write access. Existing readable subscriptions are preserved.
Other commands receive an unsupported-command response. A tearline ends input.

The persistent reply cache uses origin plus MSGID (or packet delivery identity
when MSGID is absent); successful replays resend the cached response with the
same outgoing delivery key. A crash between a subscription change and response
storage can retry that change; subscribe/unsubscribe are idempotent and rescans
use stable spool keys.

Command syntax was checked against
[HPT AreaFix help](https://github.com/huskyproject/hpt/blob/master/misc/areafix.hlp).
This is the supported subset, not every HPT extension (pause, remote password
changes, day-based rescans and forwarded uplink requests are not implemented).

## FileFix and file echoes

```ini
MailScript=./scripts/mail-scripts/filefix.groovy
FileArea=lists|./fileecho/lists|Node and point lists
FileFixPeer=1:500/102|filefix-password|lists
TicPeer=1:500/101|incoming-password|lists
TicPeer=1:500/102|outgoing-password|lists
TicCharset=IBM437
```

FileFix/FileMgr accepts the same basic list and subscribe/unsubscribe commands
as AreaFix, without rescan. It requires the optional fileecho plugin; without
that provider the script does not register a robot. Subscriptions persist in
SQL by default, or script state with `FileEchoSource=files`, and affect new
accepted files only. `TicPeer` explicitly allows a configured transport
peer to exchange files in the listed area masks. It uses one shared password
per link, case-insensitive for TIC interoperability, separate from FileFix.
Areas can be managed in the plugin UI or auto-created from explicitly permitted
links. The third FileArea field is descriptive. Database area permissions remain
separate from TIC/FileFix authentication. See [file echo settings](fileecho.md).

The optional Java fileecho plugin processes TICs from completed authenticated peer inboxes.
Transport metadata preserves original filenames even when the mailer renames
physical files. Safe original basenames may contain spaces, DOS punctuation such
as `$`, and Unicode, with a limit of 255 UTF-8 bytes. Separators, control
characters, Windows device names and other nonportable path syntax cannot be used
as automation filenames. TIC payload names retain their DOS 8.3 restriction,
including legal DOS punctuation; spaces and Unicode are not added to that format.
Area, File, From, Origin, Crc, Path and Seenby are required;
an optional Size is checked. A configured TIC password is mandatory. From must
match the peer; To, when present, must be local. Path loops are rejected.
DOS 8.3 File names are supported; Lfile/Fullname are preserved but not used as
filesystem paths. FTN domain decorations are retained when forwarded, but the
address policy uses numeric addresses in this installation's single namespace.
Cross-domain routing is not implemented.

Payloads are streamed through CRC-32 and SHA-256, limited to 128 MiB. TICs are
limited to 64 KiB and lines to 1024 characters. Missing payloads and invalid
TICs remain in inbound for later delivery, inspection or retry. Accepted files
are stored under their original names in their area directory. Case-insensitive
name collisions go into `dupes/<receipt>/<name>` without replacing the existing
file. Collisions do not forward or trigger directory imports. A placement journal
makes callback retries reuse their own stored copy.
They are forwarded only to authorized subscribers, excluding the sender,
local addresses and Seenby entries. Unknown TIC lines are preserved; outgoing
From, To, Pw, Created, Path and Seenby are rebuilt as appropriate. Replaces is
preserved but does not delete local files. No automatic hatching or FREQ service
is provided.

The transport queue stores a private file/TIC pair and publishes it atomically.
Payload precedes TIC; each is removed only after its own M_GOT acknowledgement.
An interrupted pair is retried; completed receipts prevent re-queuing the same
job. One pending pair is loaded per outbound batch, alongside ordinary mail.
A binkp 1.1 session can load subsequent pairs at later batch boundaries; a 1.0
session needs another connection for the next pair. File queues do not trigger
an immediate poll automatically: existing polling/session scheduling
must connect to the recipient.

`onFile { area, storedPath, originalName, peer, receipt -> ... }` callbacks run
after validation and storage. Their failure leaves the incoming TIC retryable.
Receipts are marked complete after forwarding and callbacks succeed. The plugin
registers before external scripts, and all scripts register before the first
scan. Inputs, stored files and receipts have no automatic retention
cleanup in this version; configure operational retention separately.

The TIC format was checked against
[FTS-5006.001](https://defsol.com/files/ftsc/fts-5006.001) and command conventions
against [Husky htick](https://github.com/huskyproject/htick).

## Automatic node/point directory import

Add the bundled import script after FileFix:

```ini
MailScript=./scripts/mail-scripts/directory-import.groovy
NodeDirectoryPath=./database/nodelist/catalog
DirectoryFeed=nodes|NODELIST|lists|NODELIST.*|NODEDIFF.*|./lists/current-nodes.lst|1:500/101|1|500
DirectoryFeed=points|BOSS|lists|POINTS.*|POINTDIF.*|./lists/current-points.lst|1:500/101
```

The repeatable definition is
`id|format|file-area|full-mask|diff-mask|retained-base-file|peer[|zone|net|charset|requireChecksum]`.
Defaults are zone/net 0, US-ASCII and requireChecksum=true. A blank diff mask
disables diffs. IDs and retained paths must be unique. Full/diff masks must not
overlap for an incoming name. Match the actual distribution names, including
ZIP names such as NODELIST.Z?? and NODEDIFF.Z?? when applicable.

This script opens the existing directory catalog directly. It does not require
`NodeDirectoryEnabled=true` or DirectorySource settings; leave automatic file
polling disabled when feeds own the imports. This also allows startup before
the first list arrives. The nodelist module must remain in the distribution.
After validated TIC delivery, only matching area/name/sender combinations are
imported. A full list or a single-member ZIP initializes the retained plain
base. The same mechanisms support NODELIST, BOSS and other configured parser
formats. Stored SQL entries never become trusted links or routing rules.

Diffs use the A/C/D editing format from
[FTS-5000.005, section 6](https://defsol.com/files/ftsc/fts-5000.005).
The first diff line must match the old base header exactly; counts are
1..32767 and must consume the complete old file. Output uses CRLF and terminal
DOS EOF. Both the old base and the result must pass header CRC validation for
diff updates, even when requireChecksum=false permits full headerless lists.
Pointlist diffs are supported when they use this same editing format. Missing
editions, nonmatching bases and other diff formats are deferred, not guessed.
ARC/ARJ/LZH/RAR distributions must be unpacked separately.

Before publishing a base, the importer validates parsing, CRC and duplicate
addresses through a rolled-back SQL import with bounded batches. The retained
base is atomically replaced, then imported into SQL. These are two resources,
not one transaction: a database failure can leave the verified new base beside
the previous catalog. A forced recovery journal allows redelivery of the same
diff to finish SQL import without applying it twice. Do not delete the adjacent
`.update` journal or replace the base manually during recovery. A per-base file
lock excludes concurrent updates using this API. Invalid data leaves the old
base and catalog intact. Input files remain available for inspection/retry.

The script does not download lists or infer which full edition is newer from
filenames/day numbers. Feed authorized publications in edition order; a full
list is an explicit replacement. When enabling this script after files were
already accepted by FileFix, explicitly import those archived files rather
than expecting completed TIC receipts to run again.

### Direct import from binkp inbound

`directory-import.groovy` can also import completed transport deliveries without
a TIC or FileFix area/password. Enable it explicitly in
`conf.d/43-directory-feed.conf`; the bundled script must remain enabled in
`40-mail-scripts.conf`. The following example matches NODELIST, REGION46 and
PNT46REG publications received from a configured `2:467/70` link:

```ini
DirectoryInboundEnabled=true
DirectoryInboundRequireDate=true
DirectoryInboundRetrySeconds=300
DirectoryInboundFeed=nodes|NODELIST|NODELIST.*|NODEDIFF.*|./lists/nodes.lst|2:467/70|0|0|US-ASCII|true
DirectoryInboundFeed=region46|NODELIST|REGION46.*||./lists/region46.lst|2:467/70|2|0|US-ASCII|true
DirectoryInboundFeed=points46|BOSS|PNT46REG.*||./lists/points46.lst|2:467/70|0|0|US-ASCII|true
```

The repeatable format is
`id|format|full-mask|diff-mask|base-file|peer[|zone|net|charset|requireChecksum]`.
An empty diff mask disables diffs for that feed. Parser defaults match
`DirectoryFeed` (`0|0|US-ASCII|true`). Use NODELIST for complete lists and regional
or net segments, with an explicit enclosing zone/net where necessary; use BOSS
for Boss-style pointlists. Other supported formats can also be configured.
The peer must already be a configured transport link. Automatic direct import
is disabled by default; with no matching rules, received lists remain untouched.

Only files in that peer's completed inbox with `.received` original-name metadata
are considered. Bare files copied into the inbound root, partial transfers,
unmatched names and publications from other peers are ignored. This is a separate
trust decision from TIC delivery: authorization is the explicit peer/feed rule,
not a TIC password. Do not configure both a TIC and a direct feed for the same
source; source IDs and retained base paths must be unique across both kinds.
Disable scheduled `DirectorySource` refresh for bases owned by these feeds.

The existing mail-processing trigger schedules import when a receive is committed;
the startup/periodic scan also processes a backlog. Inbound originals and metadata
are retained. Durable completion receipts under `Tmp/scripts` prevent unchanged
deliveries from being reapplied after scans or restarts. These receipts identify
the feed definition, path, original name, peer, size and last-modified timestamp;
they assume completed inbox files are immutable. Manually replacing a file while
preserving its size and timestamp is not a supported redelivery mechanism.

For dated publications, the newest full list in a scan is tried first. Ordering
uses the English publication date and matching day-of-year in the first header,
including its year, rather than the ambiguous `.Zxx` filename suffix. Full lists
older than the retained base are marked superseded without replacing it. Equal
editions may be retried to finish an interrupted SQL import; publication dates
are ordering hints, not a substitute for CRC or full parser validation.
`requireChecksum=false` permits an absent checksum; it never bypasses a checksum
that is present but does not match the received content. Such a file needs a
correct publication from its provider, rather than a looser feed setting.
Diffs are attempted in base-edition order and must match the retained base header
exactly. Missing bases, bad CRCs, unsupported archives and invalid input are
deferred with a warning, retaining the last valid catalog and allowing other
feeds to proceed. Failed files retry after `DirectoryInboundRetrySeconds`
(1..86400 seconds; restart also clears this in-memory backoff).

`DirectoryInboundRequireDate=true` is the default. Headerless/custom-date fragments
require explicit `false`; without a readable date, full files use local receive
modification order and automatic rollback protection is unavailable. Feed those
publications in order. Plain text and single-member ZIP work; ARC/ARJ/LZH/RAR still
require external unpacking. Parsing remains bounded and streamed by the plugin.
The switch requires restart and does not disable TIC `DirectoryFeed` imports,
manually configured `DirectorySource` imports or explicit UI downloads.
