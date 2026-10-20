# UI RPC API

## Netmail staging, release and deletion

`UiService.send` validates and stores netmail as `WAITING`; it does not prepare a
PKT or expose a local recipient's incoming copy yet. `pendingNetmail` counts only
waiting mail authored by the current account's approved AKAs. `sendPendingNetmail`
releases that scope and prepares outgoing packets under the normal mail lock;
local messages become deliverable without a PKT. It returns the number released.
The timer/manual processing and JAM export do not release waiting UI messages.
Robot replies, transit and existing automatic submission APIs retain their normal
automatic queue behavior. Existing prepared packets are not rebuilt or recalled.

`deleteMessage(session, INBOX|OUTBOX, id)` removes a message from that account's
selected mailbox through durable UI-state tombstones. Other accounts/folders,
SQL duplicate history, delivery records and already prepared packets remain.
Deleting from OUTBOX also cancels a matching `WAITING` delivery before hiding it;
deleting released mail does not recall it. Deletion can be retried safely. Detail
reads and pagination exclude deleted entries; guessed foreign IDs return NOT_FOUND.
All three methods support POINT accounts with server-side AKA scoping and both
TCP and HTTP/JSON. Keep the UI-state database with mail backups so deleted/read
state remains consistent. Removing a letter is not secure erasure of retained
SQL contents or received-original archives.

The optional `rpc-node` plugin starts a sysop RPC service alongside MailRuntime.
Configure it in `conf.d/90-rpc.conf`:

```ini
RpcEnabled=true
RpcBind=127.0.0.1
RpcPort=9090
RpcUser=sysop
RpcPassword=replace-with-a-private-password
RpcSessionSeconds=3600
# Optional separate authentication DB; default is Tmp/rpc-auth.
# RpcAuthPath=./database/rpc-auth
# Optional: an existing UTF-8 node log written by your logging configuration.
RpcLogFile=./logs/binkp.log
```

RPC defaults to disabled when the setting is absent. A new full installation
uses the [browser setup wizard](web-ui.md#initial-configuration) to create its initial
administrator and enable HTTP access. For manual provisioning without the wizard,
set RpcUser/RpcPassword explicitly. These credentials create an account only when
the authentication database is empty; restarts never reset its password. Remove
RpcPassword after successful provisioning.

The RPC plugin owns listeners and authentication sessions; the runner coordinates
its lifecycle through the common service-plugin API. The node-owned poll service
also works without RPC. These services stop before MailRuntime/database shutdown;
startup failure releases resources. The standalone default RPC handler rejects
unconfigured authentication.

Web UI is a separate optional plugin depending on RPC. Remove its JAR or set
`WebUiEnabled=false` in `conf.d/95-web-ui.conf` to retain RPC without browser routes
or the setup wizard. Remove both plugin JARs to run mail processing without them.
Profiles select their default package composition rather than directly launching
services. RPC requires SQL storage; Full/Headless can host it, while Legacy/Transit
cannot use this plugin with their file/JAM stores. See [plugin installation](distributions.md).

## Transport and authentication

`SettingsService.searchDirectory(query, offset, limit)` is an explicitly public,
read-only local-directory search. It supports pre-login registration suggestions
as well as the administrator/point browser. It accepts no session token and
does not grant access to configuration or trigger downloads/imports. Queries are
literal and case-insensitive (at most 160 characters), offset is 0..1000000 and
limit is 1..100. `DirectoryPage` returns records, `nextOffset` and `hasMore`.
Rows include address, list source, kind, station/sysop/location/phone, flags and
binkp endpoints; duplicate source records are not merged. See [directory browsing](nodelist.md).

`SettingsService.save`, `saveLinks` and `saveTosser` apply supported settings live
after candidate validation. `SettingsService.reload(authorization)` explicitly
reads the main file and ordered fragments; it requires an administrator session
and rejects setup tokens and point accounts. It preserves the RPC identity,
listeners and existing login sessions. See [live reload](web-ui.md#live-configuration-reload)
for supported options and transaction/failure boundaries. A true save response
means the supported runtime update was published, not merely written for restart.
Unsupported listener/storage/plugin changes return `INVALID_ARGUMENT` with the
option name before publication. Invalid syntax returns `INVALID_CONFIGURATION`;
other I/O/SQL failures are logged with their exception and return `SETTINGS_FAILED`.
Manually edited invalid files stay on disk, but the last valid runtime remains
active. There is no filesystem watcher; headless clients can invoke the same RPC.

The authoritative schema is `rpc/src/main/thrift/main.thrift`, including
`ui.thrift`. Generate clients using Apache Thrift 0.25.0. The transport is
**framed TCP**, binary protocol, multiplexed services. Connect a `login` client
and call `login(Credential)`; inspect `respose_status` (the legacy spelling is
retained) before using `session_id`. Call the new `ui` service with that token
as the first argument of every method. `logout` invalidates it.

Tokens contain 256 random bits, are held in memory, expire after the configured
fixed lifetime (60..86400 seconds), and are lost on restart. At most 64 sessions
can exist. Accounts and public passkey credentials persist in the authentication
database. ADMIN accounts can use node administration methods; POINT accounts
use the scoped profile/authentication APIs described below.
Disabling an account rejects both login methods and its existing sessions.
Unauthenticated UI calls fail with a typed UiError; credentials and transport
passwords are never included in link/status DTOs.

The listener defaults to loopback and has no built-in TLS. Keep it behind a
local client, SSH tunnel or a protected gateway when used remotely. A browser
cannot directly open this TCP protocol. The optional HTTP/JSON listener described
below exposes the same handlers and sessions to browsers. The optional `web-ui`
module supplies the embedded Vue/TypeScript interface in the full distribution.
Legacy `api` ping/version remain public; the old unauthenticated `messages`
service is disabled in the application runtime. `statistics` remains unavailable;
use `ui.status` instead. Worker threads handle calls outside the selector loop,
with a 2 MiB aggregate incoming frame buffer limit.

### Browser HTTP/JSON transport

The node runtime separates listener lifecycle from application services:

- `NodeRpc` coordinates startup and shutdown.
- `RpcIdentity` owns the account database, sessions and passkey authentication.
- `NodeRpcHandlers` binds authenticated application handlers to Thrift.
- `RpcTransports` owns listeners behind the shared `RpcTransport` interface.
- TCP and HTTP factories parse only their own transport configuration.

To add a transport, implement `RpcTransport` and `RpcTransportFactory`, then add
the factory to the default list in `RpcTransports.open`. `NodeRpc` and its
authentication/service wiring do not need changes. The overload accepting a
factory list allows alternative compositions and tests without real sockets.
Disabled factories return null. A created listener is registered before start,
so a failed start closes that listener and all previously acquired listeners.
Shutdown runs in reverse acquisition order and continues after cleanup failures;
startup errors retain cleanup errors as suppressed exceptions. Existing
`getPort()`/`getHttpPort()` accessors remain available, with `getPort(name)` for
other transports.

Enable HTTP alongside TCP (requires `RpcEnabled=true`):

```ini
RpcHttpEnabled=true
RpcHttpBind=127.0.0.1
RpcHttpPort=9091
RpcHttpOrigin=http://localhost:5173
# Repeat for every permitted browser origin, including the production UI.
# RpcHttpOrigin=https://node.example
```

The POST endpoints are `/rpc/login`, `/rpc/auth`, `/rpc/ui`, `/rpc/api` and the
other configured services. These are separate service processors, without a
multiplexed service prefix or TCP frame header. The wire format is Thrift
`TJSONProtocol`, not ordinary DTO JSON. The listener uses the Java 11 JDK HTTP
server, with no additional servlet container or Java version upgrade.

Browser origins must match an explicit `RpcHttpOrigin` exactly, with no wildcard
or trailing slash. Unlisted origins (including `null`) are rejected. OPTIONS
preflights allow POST and Content-Type. Clients without an Origin header can
connect, but still need authentication for protected methods. RPC tokens remain
explicit method arguments; no cookie authentication is added. Requests are
limited to 2 MiB, worker threads and the pending execution queue are bounded.
Keep the listener on loopback behind an HTTPS reverse proxy for remote access;
configure proxy request/body timeouts as well as the frontend request timeout.
`RpcWebAuthnOrigin` remains a separate check for the WebAuthn ceremony itself.

Generate the browser clients with the same native compiler used for Java:

```shell
./gradlew :rpc:browserRpcZip -PthriftExecutable=/path/to/thrift
```

`assemble` also runs this task. The archive in `rpc/build/distributions` contains:

- `generated/`: native ES6/Promise clients and the compiler's raw `.d.ts` output.
- `bindings.mjs`: an ES module factory enclosing generated globals for Vue/Vite.
- `bindings.d.mts`: combined module declarations for that factory, used by the
  Vue/TypeScript build, including the supported i64 input types.
- `client.mjs`: asynchronous fetch adapter with timeout, HTTP error propagation,
  independent buffers per call, and completion handling for oneway logout.

The browser runtime is supplied by the UI build, not downloaded at node startup.
Use the matching Apache Thrift 0.25.0 browser runtime (`lib/js/src/thrift.js` in
the Apache distribution). Configure its `JSONInt64.parse` using Thrift's
`json_parse` helper, and provide `Int64Util` and `node-int64`, before creating
clients. These helpers preserve signed i64 values beyond JavaScript's safe
integer range. Use `Int64Util.fromDecimalString` for large outgoing IDs and
`Int64Util.toDecimalString` for display; never round-trip SQL IDs through Number.
The raw declarations under `generated/` retain upstream global protocol references.
For module imports, use `bindings.d.mts` alongside `bindings.mjs`; the build
combines the declarations and supplies their Thrift/Int64 types. The `.mjs` entry
point also works for JavaScript callers. Runtime initialization remains necessary
regardless of whether the caller uses TypeScript.

Example after the UI has initialized the runtime and imported the generated
archive files:

```javascript
import { createBindings } from './rpc/bindings.mjs';
import { createRpcClient } from './rpc/client.mjs';

const bindings = createBindings(Thrift, Int64);
const login = createRpcClient(Thrift, bindings.LoginServiceClient, '/rpc/login');
const ui = createRpcClient(Thrift, bindings.UiServiceClient, '/rpc/ui');

const response = await login.login(new bindings.Credential({
    login: username,
    password
}));
if (response.respose_status !== bindings.Response.OK) {
    throw new Error('Authentication failed');
}
const areas = await ui.areas(response.session_id);
```

Serve the UI and proxy `/rpc/` from one origin, or use the configured CORS origin
during development. Account/password/passkey storage and validation are shared
with the TCP service. The Vue interface implements WebAuthn option/response
conversion; the transport carries `optionsJson` and `responseJson` strings.

Browser adapter regression tests use the actual generated clients and the
official browser runtime. `:rpc:check` (and therefore `build`) runs
`:rpc:browserTest`; Node.js 22 or newer is required for this verification only.
No npm dependencies or running browser are needed. Configure the runtime path:

```shell
THRIFT_BROWSER_RUNTIME=/path/to/thrift/lib/js/src/thrift.js ./gradlew :rpc:browserTest
```

Alternatively pass `-PthriftBrowserRuntime=/path/to/thrift.js`. A portable Node
executable can be selected with `-PnodeExecutable=/path/to/node`. Missing runtime
configuration or a failed JS test fails the task; tests are not silently skipped.
The JUnit report is written to `rpc/build/test-results/browserTest/results.xml`.
To invoke Node directly after generating bindings:

```shell
THRIFT_BROWSER_RUNTIME=/path/to/thrift/lib/js/src/thrift.js node --test rpc/src/test/browser/client.test.mjs
```

On PowerShell, set `$env:THRIFT_BROWSER_RUNTIME` before `node --test`. These tests
cover concurrent calls, typed errors, HTTP/network errors, request timeout and
oneway logout completion. They do not require a running JFtn node or a browser.

GitHub Actions sets up Java 17 and Node.js 24. A version/platform cache stores
both the Thrift compiler and `thrift.js` from the same official Apache archive.
A missing browser runtime does not cause a cached compiler to be rebuilt.
Browser results are saved by the main build job, so failed browser tests prevent
the dependent binkd/HPT jobs from starting.

## Mail

### Account profiles and point registration

The `profile` service supports public point registration, personal preferences,
AKA requests and administrator approval. Set `RpcRegistrationEnabled=false` to
disable signup. New registrations are POINT accounts, disabled until an
administrator approves an address request. Existing accounts retain ADMIN rights.
Passwords and binkp secrets are independent; signup does not grant an address
merely because it appears in a nodelist. Point ownership is reserved with a unique
database key as part of the same transaction that approves the request and
activates the account.

`me`, `savePreferences` and `requestAka` operate on the session's owner only.
`accounts` and `decideAka` require ADMIN. Light/dark theme and the default approved
posting AKA persist in the account database. The `ui` service keeps node
administration ADMIN-only on TCP and HTTP. Mail operations are scoped to the
account's current approved AKAs. Echo reading uses READ/BOTH subscriptions for
those exact AKAs; posting uses WRITE/BOTH for the selected sender AKA. Administrators
can read all local echoes and post with station AKAs. Node administration remains
administrator-only. Personal settings and passkeys remain
available to approved points through `profile` and `auth`.

| Methods | Behavior |
| --- | --- |
| `areas` | Accessible echo names, message counts, readability and exact writable sender AKAs; write-only grants do not disclose counts |
| `echoMessages` | Newest received echo messages first, using an exclusive decreasing row-ID cursor; separate from the existing ascending `messages` cursor |
| `echoContext` | Area-scoped parent by exact REPLY/MSGID and a bounded, cursor-paged list of direct replies; missing parents remain explicit |
| `messages` | Cursor page of summaries, without bodies |
| `message` | Plain message body, attributes and separate MSGID/REPLY identifiers |
| `markRead` | Persistent per-account reader position for an individual message |
| `drafts`, `saveDraft`, `deleteDraft` | Persistent draft editing, including incomplete drafts |
| `send` | Validated local submission through the running Tosser |

Echo bodies returned to readers contain plain message text, not transport control
lines. `MailItem.replyTo` carries the trimmed FTN REPLY identifier separately.
No subject-based heuristic merges discussions. The parent lookup uses the existing
MSGID index; reply pages query serialized REPLY lines in the selected area without
materializing the entire archive. Very large areas can still require a text scan
for replies; a persistent derived reply index is a future performance improvement.

Mailboxes are ECHO (specify its area), INBOX (local netmail) and OUTBOX (locally
originated netmail, queued and sent). Netmail uses an empty area string. The
OUTBOX message's sent attribute reflects transport acknowledgement. Transit
netmail is not exposed as incoming local mail.

Netmail `MailItem.body` is human-readable message text without FTN routing/control
lines. `messageId` remains available separately for REPLY linkage. Clients quote
this text instead of replaying incoming CHRS/MSGID/INTL or other kludges.

For `messages` and direct replies, start pagination with afterId=0, limit=1..200.
For `echoMessages`, start with beforeId=0. Pass nextId to continue while
hasMore is true. IDs are SQL row identities within a mailbox, not FTN MSGIDs;
the full FTN identifier is returned separately. Echo names are case-insensitive.
Unknown messages return NOT_FOUND, including an ID from another mailbox/area.
Reading a message does not mark it read automatically. Read marks and drafts
live in the separate H2 `Tmp/ui-state` database and survive RPC restarts.

A draft specifies mailbox, area, names, addresses, subject, body and optional
replyTo (FTN MSGID). All fields must be present, but saveDraft allows unfinished
addresses/names/subject. Send requires a station AKA or the account's currently
approved hosted AKA and a valid recipient. Echo submission additionally requires
WRITE/BOTH permission for that exact sender and area (administrators use station
AKAs). Names fit 35 UTF-8 bytes and subjects 71; body fits 60000 bytes.
Clients send plain text; the server adds CHRS, MSGID and optional REPLY. Embedded
NUL/kludge controls and AREA headers are rejected. Request/draft IDs contain
1..80 letters, digits, `_` or `-`. Up to 200 drafts can be stored.

`MailDraft.encoding` selects `UTF-8`, `CP866`, `KOI8-R`, `CP1251` or `CP848`;
omitting it selects UTF-8. It is preserved in drafts and used for both `CHRS` and
packet encoding. Submission rejects unrepresentable names, subject or body.
The account's `mail-themes` document stores the editor default in `encoding`;
external editors can read that preference and put it on new drafts explicitly.

Use a new stable requestId for each logical send and retain it across retries.
The same ID/content yields the same MSGID and durable delivery identity; changing
content with that ID fails. Submission intents persist before mail storage;
the mail store's identity receipts prevent duplicates after interruption.
A successful submission deletes its draft. Send stores mail for normal periodic
export; call processMail to request an immediate processing pass.

## Node operations

| Methods | Behavior |
| --- | --- |
| `status` | API version 2.0, RPC-service uptime, local AKAs, heap usage, active outgoing polls (manual and scheduled; excludes waiting jobs) |
| `links` | Configured peer addresses, hosts, ports, local AKAs and echo subscriptions |
| `echoAccess` | Effective subscription source (`database` or `config`), configured peers, known areas and exact READ/WRITE/BOTH rights; administrator-only, no credentials |
| `subscribe`, `unsubscribe` | Managed SQL echo subscriptions; new subscriptions use NEW_ONLY |
| `rescan` | Explicit repeat-safe rescan of 1..10000 recent messages with a stable requestId |
| `processMail` | Run the existing inbound/scripts/export processing cycle |
| `poll`, `polls`, `cancelPoll` | Queue, inspect and cancel manual and scheduled outgoing connections |
| `pollQueue`, `pollDetails` | Active/waiting attempts and one retained attempt, including source/start/end timestamps |
| `pollHistory` | Newest-first history pages (1..200) using an opaque `nextCursor` |
| `pollLog` | One attempt's safe journal, byte cursor and bounded line count (1..200) |
| `outbound` | Up to 200 prepared mail packets and the next file/TIC pair for a configured peer |
| `logs` | Bounded reads from the configured log file; no client-selected filesystem paths |

Echo administration requires `EchoSubscriptions=database`. Access is READ,
WRITE or BOTH. All mutations use MailRuntime's processing lock, so UI operations
cannot race scripts or inbound/outbound scans.

Polls own independent SessionContexts and use the same Netty transport/mail
callbacks. States are QUEUED, CONNECTING, AUTHENTICATING, TRANSFERRING, SUCCEEDED,
BUSY, TIMED_OUT, FAILED, CANCELLED and INTERRUPTED. A closed socket is successful
only when the protocol exchange actually completed. Repeated requests for a queued/active peer return the
existing job. Manual and scheduled polls share one node-owned queue that works
without RPC. One outgoing poll runs at a time, up to 64 wait. History is persisted
under `Tmp/polls` with source and timestamps; unfinished records become INTERRUPTED
after restart and are not silently redialled. `PollHistoryLimit` retains the last
256 completed attempts by default (1..10000). Cancellation interrupts the job and
closes its client; the peer stays reserved until cleanup completes. Connection
timeout is 30 seconds; `PollTimeoutSeconds` defaults to 120 (1..86400) after connection.
Only configured links can be dialed. Scheduler polls use this same service and
are visible through RPC when enabled. Incoming connections are outside this queue.

Each attempt has a dedicated `Tmp/polls/<id>.log` journal of protocol states,
`<<<` (incoming) / `>>>` (outgoing) binkp commands and file-transfer events with
byte counts, elapsed time and CPS. Netty callbacks only enqueue bounded events;
the poll worker flushes them.
PWD values (including CRAM responses), binary file contents and unknown command
payloads are omitted. `PollLogLimitBytes` defaults to 262144 per attempt (4096..16777216);
when full, a marker is written and subsequent events are omitted. Pruning history
also removes its journals. Readers use bounded UTF-8 pages and byte cursors.
All poll methods require an administrator session. `pollHistory` uses a stable
timestamp/ID cursor, so insertion of newer jobs does not shift older pages. Start
with an empty cursor; reset to it to refresh recent attempts. `pollLog` starts at
offset zero, and its `nextOffset` is scoped to the selected attempt ID. Logs remain
available after restart until history pruning removes the attempt. No RPC method
accepts a log path supplied by the browser.

Failed attempts retain the exception type/message and cause chain in their detail.
The attempt journal and application logger include a bounded stack trace with
causes and suppressed exceptions, correlated by attempt ID and peer. Asynchronous
Netty/sender exceptions and remote ERR/BSY explanations are retained too. Known
link passwords, session keys and authentication tokens are redacted before these
diagnostics are stored or exposed through RPC. Known command arguments are bounded
and escaped; authentication command arguments are always redacted.

The outbound method is a snapshot of prepared disk files, including held mail;
it does not enumerate all unexported database deliveries or every queued file
job. Request processMail before reading it when you need freshly exported mail.
It cannot delete files currently owned by a transport session.

For `logs`, use offset=-1 to read the latest limit lines in the final 64 KiB,
or offset=0 to read from the beginning; limit is 1..200. nextOffset is a byte cursor; rotated
is true if the file shrank below the cursor. Reads cover at most 64 KiB per call.
Unfinished final lines are deferred, oversized lines are skipped. Same-size or
larger file replacement cannot be detected by this cursor alone: reset offset
when switching a rotated log. The default RpcLogFile is `logs/binkp.log`, written
by the bundled Log4j2 configuration for incoming and outgoing binkp sessions.
Before the first connection, a missing file returns an empty page. An explicit
RpcLogFile selects another log; an empty value disables this endpoint. This
setting does not configure logging, so custom Log4j2 configurations must also
provide the intended file appender. Known secrets are redacted before RPC output.

Common errors include UNAUTHORIZED (missing/expired session), FORBIDDEN (role or
setup authorization), INVALID_ARGUMENT, NOT_FOUND, UNSUPPORTED and OPERATION_FAILED.
Robot editing in file mode returns READ_ONLY; settings failures can return
SETTINGS_FAILED. This is not a closed enumeration; clients should display a safe
fallback for unknown codes. Detailed backend failures remain in the node log.

The browser UI implements setup, account approval, personal settings, station
settings (including links and basic tosser options), robot access rules and outgoing
poll management. Its netmail reader supports approved account AKAs, inbox/outbox,
replies and persistent drafts. Echo reader navigation remains reserved. See
[browser interface](web-ui.md).

`UiService.netmailRoutes`, `saveNetmailRoute`, `deleteNetmailRoute` and
`previewNetmailRoute` are administrator-only on both transports. Database rule
mutations share the mail processing lock. File rules are read-only. The preview
uses the same router as submission/transit and includes approved local mailboxes.
`SettingsService.saveNetmailRouting` publishes rule-source/default-hop/transit
policy with the existing atomic validation and hot-reload machinery. Configured
next hops are validated; source changes do not migrate rules or rewrite queues.

Remaining API/UI work includes tracking or terminating incoming sessions,
full-text mail search, trash, message editing and safe link removal/renaming,
file subscription administration and durable automatic poll
retry scheduling. Station settings support adding and editing all configured links,
including separate PKT passwords. AreaFix/FileFix can be addressed through ordinary netmail sends;
their authorization rules can be managed through the administrative UI/API.

`SettingsService.links/saveLinks` manage the link list, preserving its primary-link
order. Link session and packet passwords are write-only; returned fields are empty.
An empty submitted password retains its previous value. Packet password modes are
`INHERIT`, `SEPARATE` and `UNPROTECTED`; the last writes an explicit empty-password
override rather than disabling PKT validation. Existing links cannot be removed
or renamed through this API until their dependent configuration is reviewed.
`SettingsService.tosser/saveTosser` manage the processing interval, processed-file
cleanup, echo access checks and automatic area creation. All four methods require
an administrator session; the setup token is not accepted. Saves apply supported
settings live through the station configuration's atomic, external-edit-aware writer.

## Database accounts and optional passkeys

`rpc_users` stores login, salted password hash, enabled flag and a stable random
WebAuthn user handle. `rpc_passkeys` stores credential ID, owner, RP ID, public
COSE key, signature counter, backup flags, label and creation time. Private keys
and biometric data are never sent to the node or stored here. The tables belong
to the separate RPC authentication H2 database, not the FTN link configuration.
The mail database remains separate. The account repository has a createUser API
for trusted provisioning. Public point registration and ADMIN approval use the
`profile` service; they do not grant administrative access to new point accounts.

Passwords use PBKDF2-HMAC-SHA256, a random 16-byte salt, 600000 iterations and a
256-bit derived key. Hash format/iterations are stored with each hash. This
matches the [OWASP PBKDF2 guidance](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html).
`auth.changePassword(session, currentPassword, newPassword)` requires password
confirmation and a 12..1024-character new password, and invalidates all sessions
for that account. The next login uses the database password, including after a
restart; bootstrap settings cannot restore an old password.

Passkey is an **alternative login method**, not a second factor required after
password login. Password login remains available if a device/key is unavailable.
Enable passkeys only after choosing the browser UI's stable hostname:

```ini
RpcPasskeysEnabled=true
RpcWebAuthnRpId=node.example
RpcWebAuthnOrigin=https://node.example
# Repeat RpcWebAuthnOrigin for additional exact authorized origins.
```

RP ID is the hostname, without scheme/port/path; origins include scheme and any
port, without path/trailing slash. Origins must be HTTPS and belong to the RP
ID's hostname/subdomains. `localhost` with `http://localhost:<port>` is allowed
for local development. Origin matching is exact; arbitrary ports/subdomains
are not implicitly trusted. RP ID must remain stable for existing keys to work.
It is the **UI origin**, not the RPC socket's address or an address supplied by
the HTTP proxy/client. The server takes it only from operator configuration.

The `auth` multiplexed Thrift service supplies:

| Method | Purpose |
| --- | --- |
| `methods()` | Discover whether password/passkey login is available |
| `beginPasskeyRegistration(session,password,label)` | Start enrollment for the authenticated account, with password confirmation |
| `finishPasskeyRegistration(session,requestId,responseJson)` | Verify and save a newly created public credential |
| `passkeys(session)` | List the account's passkeys for the configured RP |
| `removePasskey(session,password,credentialId)` | Revoke an owned key with password confirmation |
| `beginPasskeyLogin()` | Start username-less discoverable-credential login |
| `finishPasskeyLogin(requestId,responseJson)` | Verify the assertion and return the ordinary LoginResponse/session token |
| `changePassword(session,currentPassword,newPassword)` | Change the fallback password and invalidate sessions |

In a browser, the registration sequence is:

```javascript
const request = await auth.beginPasskeyRegistration(session, password, "My device");
const options = JSON.parse(request.optionsJson);
const publicKey = PublicKeyCredential.parseCreationOptionsFromJSON(options.publicKey);
const credential = await navigator.credentials.create({ publicKey });
await auth.finishPasskeyRegistration(session, request.requestId,
    JSON.stringify(credential.toJSON()));
```

Login uses `beginPasskeyLogin()`, `parseRequestOptionsFromJSON`,
`navigator.credentials.get({publicKey})` and `finishPasskeyLogin`. The gateway
relays server-provided options and browser responses without inventing its own
challenges. These snippets assume a Promise-based gateway adapter and browser
WebAuthn JSON conversion support; older browsers need base64url/ArrayBuffer
conversion. The bundled TypeScript WebAuthn adapter handles this conversion
for the Vue UI; the HTTP transport passes the signed response to server validation.

Registration/login requests are held server-side, expire after 120 seconds,
are single-use even after a failed response, and are capped at 128 outstanding
ceremonies. Enrollment is bound to the initiating session. Up to 10 keys per
account/RP can be stored. Discoverable credentials and authenticator user
verification (PIN/biometrics) are required. The server checks origin, RP ID,
challenge, signature, ownership, presence/verification flags and counters.
Zero counters used by synced passkeys are supported; nonzero counter rollback
is rejected and updates use compare-and-set storage. Application sessions use
the same expiry/logout behavior for both login methods. Key revocation prevents
future assertions; already issued sessions expire normally or can be logged out.

Verification uses [Yubico java-webauthn-server](https://github.com/Yubico/java-webauthn-server),
following the [WebAuthn relying-party operations](https://www.w3.org/TR/webauthn-2/#rp-operations).
Local algorithms are ES256 and RS256, available on Java 11. Device attestation
trust lists are not required: this is account-bound passkey enrollment, not a
policy that restricts particular authenticator manufacturers.

## Packet quarantine operations

`UiService.quarantine(session, after, limit)` lists active `.bad` files under
`Tmp`. Use an empty cursor for the first page, `nextCursor` only while `hasMore`
is true, and a limit of 1–200 (the browser uses 25). The response includes
configured source peers and packet IDs, display names, SHA-256 checksums, sizes,
modification times, recorded session peers, controlled rejection reasons and
retry counts/times. No packet bodies, filesystem paths or passwords are returned.

`retryQuarantine(session, id, checksum, peer)` processes the exact retained bytes
using the existing toss journal. The original receiving peer cannot be changed;
legacy files need an explicitly selected configured expected peer. `accepted=false`
means policy/validation still rejected the packet; its controlled `detail` and
metadata are updated. Parsing/storage exceptions return `OPERATION_FAILED` and
leave the active packet intact; consult the server exception log.

`archiveQuarantine(session, id, checksum)` preserves a verified recovery copy and
removes only the selected active file, without processing messages. Retry also
preserves the original before any import. IDs are opaque and cannot be paths;
the expected content checksum prevents acting on changed files. Invalid cursors,
stale snapshots and invalid source selection return `INVALID_ARGUMENT`.
Uninspectable/oversized rows have an empty checksum and require manual handling.
These operations are serialized with normal mail processing and administrator-only
on both transports. Recovery copies/metadata and toss journals are operational
state; do not clean them as caches.

## Personal workspace documents and draft context

`ProfileService.uiDocument(session, key)` and `saveUiDocument(session, key, document)`
read/write only the authenticated account's `workspace`, `mail-themes` and
`mail-templates` documents. Unknown keys and invalid sessions are rejected. A
missing document returns `{}`. JSON must be an object, use unique keys, be at
most 256 KiB UTF-8, depth 16 and string values at most 60,000 characters. These
preferences never grant permissions. Account ownership is selected server-side;
the request accepts no arbitrary owner. Both transports share these checks.

### Personal mail templates for external editors

Template management belongs to the authenticated user, not the station or an FTN
AKA. Administrators and approved points use the same calls:

```text
ProfileService.uiDocument(session, "mail-templates")
ProfileService.saveUiDocument(session, "mail-templates", json)
```

An external RPC editor can read and replace the same document as the browser UI:

```json
{
  "version": 1,
  "custom": [
    {"id": "personal-new", "name": "My greeting", "kind": "new",
     "body": "Hello @tname.\n\n@position\n\n@cname"}
  ],
  "rules": [
    {"kind": "new", "aka": "", "area": "", "template": "personal-new"}
  ]
}
```

`custom` contains up to 20 templates: unique IDs (1–64 letters, digits, `_`, `-`),
names up to 80 characters, bodies up to 6000 characters, and kind `new`, `reply`,
`quoted` or `forward`. `rules` contains up to 64 defaults; empty AKA/area means
any, otherwise it selects a particular sender or conference. Built-in IDs are
`new`, `reply`, `quoted` and `forward`; custom IDs must not replace them. Clients
must preserve other personal templates/rules when editing this document.

Built-in template text, token expansion and preview currently run in the editor.
RPC persists the document with JSON/size checks; editors must apply template
validation and the documented expansion rules. Templates never affect tosser
processing or grant posting rights. They survive restart and are isolated by the
session owner, including when multiple users share a node or AKA.

`MailItem.excerpt` is an optional bounded summary (240 characters); list calls
still omit full bodies and routing kludges. `MailDraft` optionally stores original
body/date/name/address, composition kind and template-initialized state. This
context survives draft persistence for client-side template expansion; it does
not override server submission authorization, MSGID generation or REPLY handling.
Original bodies are bounded to 60,000 UTF-8 bytes. See [Mail workspace](mail-workspace.md)
for the supported template tokens and preference limits.
