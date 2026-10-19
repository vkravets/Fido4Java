# Project decisions and working agreements

Last reconciled: 2026-10-19. This is a decision record, not a complete chat transcript.
The implementation and tests are authoritative when this document becomes stale.
Update the relevant entry when a decision changes; record the replacement and reason.
For current unfinished work, read [TODO](../TODO) and inspect the current Git state.

## Product and module boundaries

The node/point directory browser is shared by ADMIN and POINT users. Its local
catalog search API is deliberately public, also serving pre-login registration
suggestions. Only imported directory metadata is exposed: no import/download,
configured-peer credentials, routing permissions or ownership grants. Keep
administrative settings and the exact lookup/download operation protected.
Cross-source records remain distinct and labelled. Free-form address inputs use
bounded debounced suggestions; trusted-peer selectors retain configured-only choices.

Echo autocreation requires both the master switch and an exact configured sender
in `AutoCreateEchoFrom=address,WRITE|BOTH`. Full uses database-managed
subscriptions; file-managed runtime profiles persist the same explicit grants
through their selected mail store. After packet authentication and hooks, SQL creates the absent area and
its sole initial grant transactionally. Existing areas/grants/cursors stay intact;
other peers gain no rights. Disabling normal echo access checks does not bypass
the creation allowlist. Rules are live configuration, editable per link through
admin-only settings. Every submitted link must specify its creation mode; older
RPC clients are not supported while the protocol is under active development.

Initial station setup supports multiple uplinks in one validated configuration
publication. `NodeConfiguration.initialLinks` is a write-only field required on
first setup. Station settings no longer carry single-link connection/password
fields or a legacy setup fallback. Subsequent edits use the
existing `links`/`saveLinks` API and preserve the primary link and existing peers.
Per-link binkp and PKT passwords remain distinct; adding a peer grants no echo
permissions or routes. Setup uses station-wide AKAs; later link settings can
customize the AKAs for each peer.

Visible UI work includes Storybook states and VRT coverage in the same change.
Baseline updates use the pinned Linux Playwright container, require visual review
and a subsequent comparison without updates, and cover both themes/languages and
desktop/mobile. Functional tests do not replace VRT. If the environment prevents
verification, report that gap explicitly instead of treating it as completed.

Supported UI configuration changes are applied live after validation/staging.
An explicit administrator RPC/UI reload rereads main-file and fragment edits;
there is no file watcher. The coordinator serializes with mail processing,
poll enqueue and session-template copies. New sessions use a coherent station/
link snapshot; existing/queued sessions retain theirs. SQL link updates remain
rollbackable through file publication; runtime publication follows SQL commit.
File rollback is attempted if commit fails after publication. This is not a
cross-resource crash-atomic transaction. Unknown/runtime-owned settings (listeners,
storage, routing declarations and plugins) fail closed instead of pretending to
apply. Existing login sessions remain valid. Basic flags do not grant echo rights.
See [live configuration reload](web-ui.md#live-configuration-reload).

JFtn is a full FTN node, mail transport, tosser and administration application.
The maintained build is a Gradle multi-project repository; module membership is
defined in [settings.gradle](../settings.gradle). Do not recover deleted modules
from old cache files merely because their artifacts are still present locally.

| Concern | Current owner |
| --- | --- |
| Packet/message formats, addresses, links | `fido-fts` |
| Main configuration and ordered fragments | `fido-config` |
| SQL mail, link/subscription/routing state | `fido-database` |
| Mail import/export, routing and durable outbound preparation | `jtoss` |
| Optional JAM editor synchronization | `jam-bridge`, using `jambase` |
| Directory formats, validation, catalog and list/diff updates | `nodelist` |
| Shared binkp state machine and file transfer | `binkp/binkp-common` |
| Default network implementation | `binkp/binkp-netty` |
| Retained alternative network implementation | `binkp/binkp-mina2` |
| Thrift contracts, processors and account/authentication API | `rpc` |
| Embedded HTTP UI routes, setup and settings persistence | `web-ui` |
| Vue/TypeScript application, stories and browser tests | `web-ui/frontend` |
| Node lifecycle, mail workers, polling, script runtime, packaging | `jftn-bundle` |

## 1. SQL is authoritative; JAM is an optional external-editor bridge

Mail lives in the SQL-backed database. JAM is a separately configurable plugin
for external editors, not a mandatory intermediate step before making PKT mail.
RPC/UI operation must remain possible without the JAM bridge. Retained JAM views
can also import submissions written by an external editor under the bridge's
submission/identity policy. Historical JAM archive import is a separate operation.

Interoperability was exercised against HPT. A real interactive GoldED automation
test was considered and intentionally deferred; HPT bidirectional compatibility
is useful evidence, not a blanket guarantee about every GoldED feature.
See [tosser/JAM guide](tosser.md) for lifecycle, locking and supported behavior.

## 2. Current database structure is the baseline

Legacy schema migration code was removed by agreement. Do not add an implicit
conversion from an obsolete database structure without a new migration design.
Treat existing data and journals as operational state; tests use temporary data.
Database/account stores are not Gradle caches and cannot be reset as a build fix.

## 3. Netty is the default; MINA2 remains optional

The runner uses Netty by default. MINA2 remains supported and has independent
tests, including the shared binkd interoperability scenarios. MINA3 was removed;
its history remains in Git. Old MINA3 JARs/logs in `.gradle-user-home` do not mean
the module should be put back into the build.

## 4. Binkp protocol behavior belongs in the common state machine

Client/server transports share protocol semantics. Version 1.1 supports multiple
batches and preserves session authentication/security state between batches.
Version fallback and extension negotiation must be verified against the protocol
references and real-peer tests, not inferred from transport class names.

NR policy is directional: allowing NR locally, requesting NR for incoming files,
and honoring a remote NR request for outgoing files are distinct operations.
Do not replace this with an unconditional requirement that both peers advertise
the same flag. Explicitly disabling NR also affects the advertised protocol
version because 1.1 requires NR support. Both roles need regression coverage.
See [binkp references](binkp/README.md), the main README and the interoperability tests.
There is no claim that every optional FTSC extension has been implemented.

## 5. Routing, queue preparation and acknowledgement are separate stages

Netmail routing distinguishes the final recipient from the next hop. Echomail
uses its subscription/access and path/seen-by policy. Prepared outbound files
remain queued until the transport acknowledges them; reconnects must not silently
drop them or fabricate successful delivery. UI operations share mail-service
synchronization. Never delete delivery receipts, `.bad` packets or journals as
part of unrelated refactoring or development-environment cleanup.

## 6. Keep original inbound files while development is active

`PreserveReceivedFiles` defaults to true and is available through station settings.
Original received PKTs, bundles, TICs and other payloads are retained in the
received archive without automatic cleanup. `Deletetossed` separately controls
processed files in inbound. Disabling processed-file retention must not erase
the archived originals or override quarantine handling.

Packet quarantine is recovered explicitly through the administrator UI/RPC.
Retry preserves exact packet bytes and existing toss journals; it does not bypass
source/destination, PKT password or echo access validation. New quarantine captures
bind metadata to the receiving session peer and content checksum. Legacy `.bad`
files without this metadata require an explicit expected configured peer. Retry
and archive first retain a verified content-addressed copy under `Tmp/.quarantine`;
archive removes only the active entry without importing it. Recovery metadata and
archives are operational state, like toss journals, not build caches. Oversized or
damaged entries require manual inspection. This does not automatically retry
quarantine files or suppress independent rescans of retained inbound sources.

`*.received` files store original transport filenames. They are metadata, not
ZIP bundles. Bundle recognition must match the complete filename; a suffix such
as `.MO0.received` must not be treated as a `.MO0` archive.

## 7. Connection, packet, robot and UI credentials have different purposes

Binkp session passwords authenticate a connection. PKT passwords authenticate
packets and have an eight-ASCII-byte format limit. `PacketPassword` can override
the session password for a peer; `-` explicitly means an empty PKT password.
A wrong protected packet must not be accepted just because binkp connected.

AreaFix/FileFix/TIC credentials are separate robot-access rules. UI passwords
belong to the account store. Do not silently substitute one kind for another.
Settings passwords are write-only: an empty submitted edit preserves the current
secret, and read responses contain empty secret fields.

## 8. Groovy owns automation policy; reusable parsers stay in Java modules

The bundled external scripts implement AreaFix, FileFix/TIC handling and
directory-feed orchestration. Shared robot authorization/state and delivery APIs
remain in the application/database layer. Directory parsers, checksum validation,
streaming ZIP handling and atomic list/diff publication remain in `nodelist`.
This lets operators customize policy without duplicating core file-format code.

Scripts are supplied as editable external files under `scripts/mail-scripts/`.
The default configuration loads these paths, not `classpath:` resources.
Classpath loading remains useful for isolated tests and explicit compatibility.
Existing operator scripts/configurations are preserved during startup; upgrading
JARs alone does not overwrite an old customized external script.

## 9. One directory owner with explicit delivery adapters

`DirectoryService` owns the catalog and every `DirectorySource` definition.
The automation runtime passes its `DirectoryImportService` API to scripts.
`30-nodelist.conf` contains definitions; feeds in `43-directory-feed.conf` refer
to IDs without repeating formats, base paths, charsets or checksum settings.
Local refresh selects explicit `DirectoryLocalSource` IDs. Duplicate IDs/base
paths and competing local/delivery owners are rejected. `NodeDirectoryEnabled`
controls all automatic adapters; local refresh has a separate opt-in switch.

`DirectoryFeed` imports after the fileecho plugin validates and stores a TIC delivery.
The direct mode (`DirectoryInboundEnabled`, `DirectoryInboundFeed`) imports
completed binkp inbox files from an explicitly authorized configured peer,
without requiring a TIC. A bare file in the inbound root is not implicitly trusted.
Direct mode is opt-in and processes both existing completed deliveries and new ones.

`DirectoryFeedFolder` attaches an accepted-file folder to an existing TIC feed.
It scans only direct regular files, recognizes historical receipt-prefixed names,
and ignores duplicate subdirectories. The retained base must be outside the source
folder. This explicit local source can catch up completed TICs without replaying
mail or bypassing checksum/date validation.

Publication dates include the year; filename day suffixes alone are insufficient
for ordering a backlog. Older full publications must not replace a newer retained
base. Persistent processing receipts survive restarts; diffs require the proper
base and retain failure/recovery state. Headerless/custom-date feeds need explicit
configuration and have weaker automatic ordering guarantees.

CRC/parser errors are not silently ignored. `requireChecksum=false` permits an
absent checksum, not a checksum mismatch. Imported directory records never become
trusted connection links, passwords, subscriptions or routing rules.
See [directory import](nodelist.md) and [script configuration](mail-scripts.md).

## 10. Main configuration is UI-managed; plugin internals use `conf.d`

UI edits atomically replace `jftn.conf` and detect conflicting external changes.
Technical plugin settings live in ordered `conf.d/*.conf` beside that main file.
Later fragments replace earlier values for a key, including the entire list of
a repeated option. Keep all `MailScript` entries together in registration order.

UI-managed station keys cannot be silently overridden in fragments. Mail settings
that already have a manual fragment override are read effectively, but their UI
save is refused until the conflict is resolved. The UI never rewrites operator
fragments. File-only operation remains supported without UI/RPC.
See [configuration ownership](plugin-configuration.md).

## 11. Database-backed robot configuration is the full-package default

The full package includes UI/RPC and uses live database robot rules by default.
File mode is explicit, with offline validation/import/export commands. Export is
available when moving to file-only administration. Do not introduce a silent file
fallback when a database rule is absent: that would create two conflicting sources
of authority. A lightweight package is a future option, not the existing no-JRE ZIP.
See [robot access](robot-access.md).

## 12. UI is Vue + TypeScript, served by the Java application

Node/npm are development tools for compiling assets and running tests; production
does not need a separate Node UI server. GraalVM JavaScript embedding is not needed
for this architecture. Handwritten frontend application/test code uses TypeScript;
generated Thrift browser JavaScript and vendor runtime are build outputs.

The UI supports English by default and Ukrainian, with light/dark themes. Russian
UI translations were intentionally removed. Both themes must receive consistent
controls, validation, layouts and tests. Netmail reading/composition and approved
mailbox scopes are implemented; echomail reading remains planned. Reserved
navigation alone must not be presented as an implemented feature.

Notifications are fixed to the viewport, not a scrollable page/card. Information
and warning notifications can be dismissed; error notifications cannot. A close
button must not change a notification's height. Keep the connected-three-node SVG
identity instead of replacing it with a generic letter icon.

## 13. UI accounts and point identity require server-side authorization

Users can self-register. Point AKA claims need administrator approval; claims
are not immediately usable send identities. Administrators can manage the node;
ordinary points do not see node-management navigation. The backend must enforce
this restriction even if a client calls RPC directly.

Initial setup uses a private one-time URL/token printed by the server and creates
the administrator and station configuration. General administrative mail-settings
methods require an authenticated administrator, not the setup token.

Passkeys are an additional login method alongside passwords. Private keys remain
with authenticators; server-side WebAuthn verification stores credential metadata
and public keys, checks RP/origin/challenges and maintains session authorization.
Authenticator selection/storage UI can be controlled by Chrome/Windows rather
than the application. See [RPC authentication](rpc-ui.md).

## 14. Keep both Thrift transports and generated contracts

The existing framed TCP RPC remains available. HTTP/JSON Thrift serves the browser
client; contracts are generated from `rpc/src/main/thrift`, including browser types.
Do not hand-edit generated Java/JS/declarations. Transport factories and lifecycle
management are separate from service handlers so new transports do not grow a
single monolithic runner class. Authorization is transport-independent.

## 15. Distribution is an ordinary classpath layout, not a fat JAR

Packages separate `lib/`, `lib/groovy/`, `plugins/` and editable `scripts/`.
Root launchers support command prompt, PowerShell and Unix shells. Runtime paths
are relative to the installation working directory, and missing working folders
are created. A no-JRE ZIP and platform JRE packages are distinct distributions.
The JRE lock is for the packaged runtime, not the development JDK.
See [distribution guide](distributions.md) and `gradle/distributions.gradle`.

## 16. Keep generated software and caches out of Git

Gradle wrapper JARs are bootstrapped externally from fixed URLs with hashes.
Gradle itself is pinned by wrapper properties. Development downloads now have
`tools/development-tools.lock.json`; they live under ignored `.dev-tools/`.
Library binaries, downloaded compilers/runtimes, temporary probes, reports and
cache trees do not belong in commits. Intentional binary test fixtures and
committed VRT PNG baselines are exceptions, not unwanted application binaries.

## 17. CI uses GitHub Actions; releases are tag-driven

The current pipeline is `.github/workflows/ci.yml`, not old CircleCI cache probes.
Build precedes binkd/HPT integrations; browser UI tests follow integrations;
VRT follows UI tests. Tagged-release packaging/publishing depends on the checks.
The build seals compiled artifacts with a revision and checksums so downstream
jobs test the same outputs instead of recompiling an unrelated checkout.

Normal branch pushes/PRs validate the project. Release publishing is driven by
matching version tags and uses GitHub Actions token permissions; no CircleCI
release PAT/context is needed. Do not publish or push without an explicit request.
Container interoperability suites are explicit tasks, not part of ordinary unit tests.

## 18. Test behavior, recovery and interoperability

Use JUnit Jupiter directly, with no JUnit 4/Vintage compatibility layer. Use
temporary directories/databases, shared fixtures and readable helper methods.
Priorities include meaningful branches, failure/restart boundaries, actual TCP
role-to-role exchange and real binkd/HPT interoperability. Coverage percentages
are measurements, not a substitute for behavior assertions.

Storybook is the component catalogue. Playwright covers components and real HTTP
UI workflows. VRT compares committed screenshots in a pinned Linux Playwright
container; native Windows screenshots must not replace Linux baselines. Reports
attach baseline images even for successful VRT comparisons. Open downloaded
Storybook over HTTP, not `file://`. See [UI testing](web-ui.md).

## 19. Preserve the agreed source layout

Java formatting uses Spotless/Eclipse with project-specific expression alignment:
four spaces, a 100-column target, explicit imports and no wildcard imports even
in temporary helper source. Import groups: `org.fidonet`, other libraries, Java
platform, static imports. Keep `@Test` and other annotations on separate lines.
Keep original multiline `Created by` metadata comments.

Break long declarations at parameters: first stays beside the method name;
remaining parameters align underneath it. Break long call chains by selectors,
aligning dots. For nested string concatenation, keep the first operand near its
opening parenthesis and align continuation `+` and nested selector dots. Use the
shared formatter rather than trying to reproduce this with a generic Google sorter.
Where the alignment cannot meet the limit, the configured formatter has a fallback.

One statement per line. Reduce nested `try`/loop/conditional blocks through
focused methods, guard clauses and reusable fixtures. Do not mechanically split
every expression into a method if that makes the execution order harder to follow.
Prefer imports to fully qualified names when there is no naming collision.

## 20. Collaboration and commits

Give progress updates during sustained work, including what is being verified.
When a task requests multiple independent steps/commits, finish them in order and
report each result. Commit messages are concise English descriptions in past tense
(for example, "Added ..." or "Fixed ..."). Stage coherent files/hunks rather than
folding unrelated accumulated changes together. Browser and backend work can be
separate commits when their boundaries are useful or explicitly requested.

Formatting-only work should not launch application/container suites. Behavioral
changes need relevant tests; expand testing when failures or unresolved concerns
justify it. Preserve user changes and inspect Git status before staging. Never
commit production credentials or publish a change merely to make a local task end.

## 21. File echoes are optional and plugin settings are declarative

The `fileecho` plugin owns TIC parsing, validation, areas, permissions and delivery.
FileFix remains an external command script and registers only when that service
is active. Disabling/removing the plugin preserves inbound files, receipts and
pending file pairs; the host does not load those pairs without the provider.

File areas use SQL by default, with an explicit file-managed mode. Automatic
creation requires a permitted sender, valid TIC authentication and a validated
payload, and grants rights only to that sender. Existing rights are not expanded.

Plugin settings use the transport-independent `SettingsExtension` contract.
Providers own fields, translations, validation and writes; the shared admin-only
RPC and Vue renderer have no dependency on fileecho implementation classes.
No executable plugin JavaScript is loaded by the browser. See
[file echoes](fileecho.md) and [plugin settings](plugin-settings.md).

## 22. Binkp streams reuse the protocol engine

TCP and process-stream sessions share handshake and command dispatch in
`BinkpSessionEngine`. Stream framing handles short reads and switches CRYPT at
frame boundaries. Outgoing writes have a bounded queue and explicit flush/failure
completion. External stderr is drained separately from binary stdout.

Outgoing SSH uses a managed OpenSSH process and remote `binkd -i`; no source was
copied from jnode. The usual link AKA and binkp credentials remain authoritative.
File-only `BinkpSsh` mappings in `conf.d` select SSH per link, with TCP as the default.
OpenSSH owns accounts, keys and host trust. Noninteractive authentication and strict
host-key checking are mandatory; arbitrary browser-supplied commands are not exposed.
The poll service uses `ClientConnector` and waits for transport completion before
recording success. See [SSH operation and integration checks](binkp/README.md#outgoing-ssh-connections).

## 23. File echo storage and script policy

Validated fileecho payloads retain original names. Case-insensitive collisions
preserve the existing copy and place incoming copies under `dupes/<receipt>/`.
A placement journal preserves retry identity. Collisions do not forward or trigger
directory imports. Historical prefixed files require explicit operator migration.
Java owns storage guarantees. An external Groovy script owns description indexes
and local netmail reports. Literal templates are files beneath the script folder;
received descriptions cannot execute code and reports expose no TIC passwords.

## 24. Runtime, adapters and repositories have explicit roles

`MailRuntime` owns lifecycle, the processing lock and extension ownership.
`MailProcessingCoordinator` runs processing cycles, `BinkpMailTransferAdapter`
adapts durable queues to transport callbacks, and `MailDatabaseConfiguration`
seeds configured links/subscriptions. `MailAutomationRuntime` exposes the script
API; `ExternalMailScripts` owns Groovy engines and registration directories.

`NodeUiServiceHandler` is the RPC adapter. `MailDraftValidator` owns pure draft
validation and `MailSubmissionService` owns idempotent submission under the mail
runtime lock. Authorization remains at the existing `AuthenticatedUi` boundary.
`UserMailStateStore` explicitly names persistent drafts/read/submission data.
`NodeConfigurationService` coordinates validation, publication and live application;
`MailDatabaseFacade` composes mail storage/repositories, not just JDBC lifecycle.

`SessionTelemetry` owns diagnostic listeners and formatting. Authentication guards,
batch transitions and their synchronization remain together in `SessionContext`.
`FileMetadata` is immutable; mutable progress is separate in `FileTransferState`,
composed by `FileTransferInfo`. Only metadata participates in transfer identity.
`FileEchoAreaService` owns domain operations and policy; `FileEchoRepository`
owns SQL access and transactions. No database schema or wire protocol changed.

These internal class/API names replace the earlier ambiguous names directly;
external Java/Groovy integrations must rebuild/update imports. No binary alias
layer is provided. Existing runtime databases and operational state are retained.

## 25. Offline fileecho index recovery keeps formatting in scripts

Optional plugin `OfflineCommand` providers run before node startup and must not
start listeners or mail processing. The fileecho provider takes the existing mail
runtime file lock, reads retained TICs with the shared bounded parser, and matches
metadata against current payload CRC/size. It does not replay deliveries, change
mail journals or grant sender permissions. Conflicting metadata fails before
publication. The operator selects the area tag and directory explicitly.

The external fileecho tools script owns index formatting/templates for both live
events and offline recovery. Offline rebuild replaces all rows atomically and
preserves an existing index as a hidden backup. Unmatched files remain visible
without descriptions; missing originals are never fabricated.

Index rows expose size and date as literal template values, with optional bounded
column padding. Size is validated payload bytes (TIC Size must agree when present).
Date is the first TIC Path timestamp in UTC, not a guessed payload mtime. Rebuilds
choose the earliest matching copy's first-hop time. Unknown/unrepresentable dates
stay visibly unknown. Host locale/timezone cannot alter the default columns.

## 26. Directory header CRC exceptions are opt-in and filename-scoped

`DirectoryIgnoreCRC=source-id|filename-mask` in the directory plugin fragment is
an explicit operator exception for full publications with known broken header
CRCs. No global disable flag exists. Matching uses original delivery names, not
hashed receipt paths or temporary filenames; each use emits a WARN. Glob rules
must retain a literal filename component and cannot escape their source ID.

Only presence/mismatch of the directory header checksum is exempted. Structural
parsing, address bounds, SQL uniqueness, publication ordering, ZIP integrity and
TIC authentication/checksums stay enforced. Diffs always require checked bases
and outputs. Originals are not rewritten. Rule definitions participate in script
receipt identity, and relaxed/strict parsing have different catalog definitions,
preventing a previous relaxed import from bypassing a later strict reimport.

## 27. Web netmail uses approved account identities

Node administration and echomail access remain administrator-only. The full node's
mail backend resolves approved author addresses on every request and applies them
to SQL pagination, detail reads, read marks and draft submission. A guessed ID in
another mailbox returns NOT_FOUND. Drafts and send receipts remain owner-scoped;
the sender address is checked on the server.

While the full RPC runtime is active, approved account AKAs are local netmail
mailboxes in the tosser. Disabling an account denies login but retains incoming
mail. The mailbox provider is detached before the authentication database closes.
Headless operation continues to use configured station AKAs; directory imports
never create local mailboxes or grant account permissions.

## 28. Netmail route administration shares runtime routing and publication

Administrators manage SQL routing rules and preview decisions through UiService.
Previews reuse the active tosser/router, including approved local mailboxes, and
never submit messages or start transports. Policy saves use SettingsService and
the station's existing atomic configuration/live-application transaction. Invalid
next hops or masks preserve the active policy and file. Source switches require
explicit UI confirmation and never silently migrate rules. File rules stay
read-only. Queued deliveries retain their previously selected next hop.

## 29. Packet names and duplicate mail have separate identities

New outgoing netmail, echo and rescan packets use eight hexadecimal digits and
`.pkt`, following HPT's transferred-packet convention. A durable 32-bit sequence
allocates names; full delivery keys, recipient-scoped acknowledgement receipts
and explicit owner records remain independent. Old queued UUID filenames are
drained unchanged. Filename collisions never overwrite or acknowledge another
delivery. Sequence and ownership files are operational state, not caches.

SQL duplicate history remains authoritative and survives deletion of message
contents. Repeated incoming mail defaults to `DupeAction=move`: reconstructed
single-message PKTs are retained outside mailboxes/outbound in `Tmp/dupes` (or
`DupePath`). Explicit `discard` keeps SQL duplicate protection but omits those
inspection copies. Exact repeated copies reuse an archive entry; distinct bodies
or peers are retained separately. Original received bytes are governed separately
by PreserveReceivedFiles. These file-only policy settings require a restart.

Netmail duplicate checks distinguish a different delivery from retry of the
original delivery key. A robot failure after SQL insertion must remain retryable;
a newly delivered duplicate must not invoke robots again. Failed duplicate
archiving must not complete the packet progress journal. Duplicate archives have
no automatic re-export or release action.

## 30. Mail robots use Husky-compatible envelopes with explicit capability limits

AreaFix/FileFix policy remains in external Groovy scripts; Java owns reusable
validation, durable pause state, queueing and exact-file resends. Requests are
authenticated direct-peer netmail with a separate robot password in the subject.
Local command support and deliberate differences from HPT/HTick are documented
in [mail robots](mail-robots.md); unsupported operations never report success.

Per-peer AreaFix/FileFix pauses are independent, preserve subscriptions/backlog
and apply when queues are loaded. They do not recall files already offered by an
active transport. File resends require an enabled subscribed area, FileFix policy
and outgoing TIC access; they create a fresh local TIC and durable file/TIC job.

Trusted automation and an offline locked CLI can submit commands to configured
uplink robots through ordinary private netmail. Remote passwords are explicit
inputs and are never borrowed from inbound password verifiers. Stable request
IDs bind content to one delivery; remote replies remain untrusted mail, not an
automatic configuration/catalog import. The existing netmail composer supports
manual requests without a new browser form.

## 31. Browser netmail waits for explicit release and deletion is mailbox-scoped

UI submission stages netmail as WAITING. Background processing, polling and JAM
export cannot release it. An explicit authenticated action releases only messages
authored by that account's currently approved AKAs, then prepares packets under
the mail runtime lock. Local mail becomes visible to its recipient at release.
Existing automatic robot/transit/script submissions retain automatic delivery.
Queued next hops remain fixed; changing routing does not rewrite staged mail.

The MAIL navigation group displays the waiting count and send action. This count
excludes already released transport deliveries; release prepares packets without
forcing a network connection. Normal polling remains responsible for transfer.

Deletion records durable per-account/per-folder tombstones in UI state. Deleting
waiting outgoing mail cancels that delivery, but does not erase SQL duplicate
history, another recipient's copy or prepared packets. Released delivery records
and ACKs remain valid. Keep mail and UI-state databases together in backups.

## 32. Local MSGIDs and Via use the Husky wire layout with durable JFtn state

Local message IDs use an address plus eight hexadecimal digits, matching HPT's
wire representation. Browser request IDs remain internal idempotency keys;
the UI submission journal preserves the allocated MSGID across retries/restarts.
Existing IDs are immutable. Via identifies the actual JFtn build and outgoing
link AKA in UTC; it does not introduce a second message ID. See
[tosser tracking and MSGIDs](tosser.md) for the reference revisions and examples.

## 33. Husky wire compatibility retains explicit trust and durable delivery ownership

The PKT/netmail reference is HPT `c2a91d64cee0e6cd357376d30c9285ee3f9c624f`,
with fidoconf `62c963a6f078905660f2c0b3a6929383823190c5`; TIC processing follows
HTick `b146b48b8ff4e1e86179bc68dccc8fc19b37c464`. Wire attribute normalization,
priority order, special route targets, RouteFile and direct FRQ follow those
implementations. These features do not import the whole Husky configuration
language or claim implementation of every FTSC document.

For Full/Headless, SQL and the internal spool remain authoritative. Optional external BSO publishes
standard FLO/REQ controls and isolated copies with durable ownership/consumption
records; native polling is disabled in this mode. Raw packets retain eight-hex
names and use FLO rather than shared `.out` accumulation. Directory links are
explicitly opt-in and outgoing-only; they do not establish inbound trust or add
configured credentials. See [BSO](bso.md) and [routing](tosser.md).

FILE attaches resolve beneath an explicit local root or from the authenticated
transit source's completed receipts. SQL stores that source separately from the
outgoing route. Missing/ambiguous payloads defer export. KFS/TFS run only after
handoff consumption, retain durable originals and never remove a changed source.
External mailers/request processors own actual FRQ service. TIC Replaces removes
active matches only after authentication/CRC validation and retains replaced
originals. This preserves the project's default retention policy.

Evidence: focused restart, source-isolation, route, busy-lock, batch-ACK and
replacement tests cover these paths. Real pinned HPT successfully tossed a JFtn
packet with point addressing, mixed-case packet password, MSGID/REPLY/Via and a
repeated toss without duplicate mail. Windows Testcontainers could not discover
the Podman Docker API; that case was verified through ordinary Podman CLI
container operations instead. The real BSO/HTick suites now verify binkd FLO/REQ
consumption, network transfers, post-ACK actions, HTick toss/hatch and retained
Replaces across worker restarts. See [the integration runbook](husky-integration.md)
for the exact fixture boundary and local/CI commands.

## 34. BSO and HTick integration exercises real processes from a compiled snapshot

The new suites use ordinary Docker/Podman CLI commands, isolated networks and
unprivileged Linux container processes. Java and the consuming binkd share Linux
paths inside a sender container; a second binkd is the remote peer. No host engine
socket or installed-node state is mounted. Existing HPT/JAM and transport suites
retain their Testcontainers setup.

Fixture helpers own lifecycle and diagnostics; short-lived workers invoke the
production SQL/spool and fileecho code. Receiver binkd receipt metadata is adapted
by a test fixture before entering the production plugin. The SRIF responder is a
minimal test service, not a production request processor. FRQ delivery success is
distinct from receiving a matching file; both positive and no-match exchanges
are tested, including a subsequent connection without repeating the request.

GitHub Actions has separate BSO and HTick matrix jobs after build. These use the
same sealed/revision-checked integration runtime as HPT, without compiling the
application again. Reports retain command output and per-peer filesystem state.
Local evidence uses Podman Linux/amd64; actual execution of the updated GHA
workflow remains a remote CI result, not something implied by a local test.

The subsequent runtime profiles implementation is described in decision 35 and
[the profile runbook](transit-profiles.md).

## 35. Storage profiles select capabilities and separate installation artifacts

Full/Headless retain authoritative SQL storage; Full alone includes UI/RPC.
Legacy uses primary JAM echoes and selectable JAM/FTS1 MSG netmail with a separate
durable delivery journal. JAM netmail remains the default; the selected storage
format/path/encoding is pinned in operational state, with no implicit conversion.
PKT remains a delivery format rather than an editor mailbox. Transit keeps
file queues, identities and a retained local service-mail spool without reader
mailboxes. All profiles reuse the existing parsers, routers, PKT/TIC and delivery
code. SQL-free profiles reject SQL policy sources and directory-based virtual
links rather than silently creating a database or granting directory trust.

Legacy scans local unsent editor messages, publishes a stable queue entry and
then sets the mailbox Snt flag. A durable receipt covers restart before that flag is written.
Editor deletions/read state survive scans and restart. Generated AREADEF and
Husky area catalogs are separate from personal GoldED configuration. Existing
SQL-to-JAM bridge semantics remain available only to SQL profiles.

The build seals one compiled superset and its per-profile library manifests.
Packaging derives eight public ZIPs from that same verified input: four profiles,
each with/without runtimes. With-JRE ZIPs contain both pinned Windows x64 and
Linux x64 JREs. Reduced archives exclude UI/RPC; Legacy/Transit additionally omit
SQL/ORM/database and directory-catalog JARs. No storage migration or pruning is
implied by changing a profile marker; journals and mail are operational state.

## Changing these decisions

Before replacing a behavior, check the implementation, relevant tests and the
linked guide. Write the new decision and its consequences, adjust tests, and
update the relevant service guide and TODO. Ask about genuine product/protocol
ambiguity; routine implementation choices do not need another approval round. Keep future proposals
visibly separate from accepted and shipped behavior.
