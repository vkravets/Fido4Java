# UI RPC API

The application can start a real sysop RPC service alongside MailService:

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
RpcLogFile=./logs/jftn.log
```

RPC is disabled by default. The first enabled startup requires explicit
RpcUser/RpcPassword bootstrap credentials. They create the initial account only
when the authentication database is empty; later restarts never reset its
password from configuration. Remove RpcPassword after successful provisioning.
The
listener, authentication sessions and UI poll worker are owned by Runner and
stop before MailService/database shutdown. Startup failure releases resources.
The standalone default RPC handler no longer authenticates arbitrary users.

## Transport and authentication

The authoritative schema is `rpc/src/main/thrift/main.thrift`, including
`ui.thrift`. Generate clients using Apache Thrift 0.25.0. The transport is
**framed TCP**, binary protocol, multiplexed services. Connect a `login` client
and call `login(Credential)`; inspect `respose_status` (the legacy spelling is
retained) before using `session_id`. Call the new `ui` service with that token
as the first argument of every method. `logout` invalidates it.

Tokens contain 256 random bits, are held in memory, expire after the configured
fixed lifetime (60..86400 seconds), and are lost on restart. At most 64 sessions
can exist. Accounts and public passkey credentials persist in the authentication
database; every provisioned account currently has full sysop privileges.
Disabling an account rejects both login methods and its existing sessions.
Unauthenticated UI calls fail with a typed UiError; credentials and transport
passwords are never included in link/status DTOs.

The listener defaults to loopback and has no built-in TLS. Keep it behind a
local client, SSH tunnel or a protected gateway when used remotely. A browser
cannot directly open this TCP protocol. The optional HTTP/JSON listener described
below exposes the same handlers and sessions to browsers. No frontend is included.
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
- `client.mjs`: asynchronous fetch adapter with timeout, HTTP error propagation,
  independent buffers per call, and completion handling for oneway logout.

The browser runtime is supplied by the UI build, not downloaded at node startup.
Use the matching Apache Thrift 0.25.0 browser runtime (`lib/js/src/thrift.js` in
the Apache distribution). Configure its `JSONInt64.parse` using Thrift's
`json_parse` helper, and provide `Int64Util` and `node-int64`, before creating
clients. These helpers preserve signed i64 values beyond JavaScript's safe
integer range. Use `Int64Util.fromDecimalString` for large outgoing IDs and
`Int64Util.toDecimalString` for display; never round-trip SQL IDs through Number.
The raw generated declarations are not a complete Vue TypeScript facade: they
retain upstream global protocol references and require integration into the UI
type setup. The `.mjs` entry point is directly usable from JavaScript components.

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
with the TCP service. Browser WebAuthn option/response conversion is still UI
work; the transport sends the existing `optionsJson` and `responseJson` strings.

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

CircleCI uses the OpenJDK image with Node.js preinstalled. The compiler cache
key stays unchanged; a separate version-only cache stores `thrift.js` extracted
from the same official Apache Thrift archive. Missing browser runtime cache does
not cause a cached compiler to be rebuilt. Browser results are published by the
main build job, so failed JS tests prevent the dependent binkd/HPT jobs starting.

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
posting AKA persist in the account database. The current node-wide `ui` service
remains ADMIN-only, including over TCP: point mailbox access will use a separately
scoped API when the reader is implemented. Personal settings and passkeys remain
available to approved points through `profile` and `auth`.

| Methods | Behavior |
| --- | --- |
| `areas` | Existing echo names and message counts |
| `messages` | Cursor page of summaries, without bodies |
| `message` | Full stored message including control lines and attributes |
| `markRead` | Persistent per-account reader position for an individual message |
| `drafts`, `saveDraft`, `deleteDraft` | Persistent draft editing, including incomplete drafts |
| `send` | Validated local submission through the running Tosser |

Mailboxes are ECHO (specify its area), INBOX (local netmail) and OUTBOX (locally
originated netmail, queued and sent). Netmail uses an empty area string. The
OUTBOX message's sent attribute reflects transport acknowledgement. Transit
netmail is not exposed as incoming local mail.

Start pagination with afterId=0, limit=1..200. Pass nextId to continue while
hasMore is true. IDs are SQL row identities within a mailbox, not FTN MSGIDs;
the full FTN identifier is returned separately. Echo names are case-insensitive.
Unknown messages return NOT_FOUND, including an ID from another mailbox/area.
Reading a message does not mark it read automatically. Read marks and drafts
live in the separate H2 `Tmp/ui-state` database and survive RPC restarts.

A draft specifies mailbox, area, names, addresses, subject, body and optional
replyTo (FTN MSGID). All fields must be present, but saveDraft allows unfinished
addresses/names/subject. Send requires a configured local author AKA and a valid
recipient. Names fit 35 UTF-8 bytes and subjects 71; body fits 60000 bytes.
Clients send plain text; the server adds CHRS, MSGID and optional REPLY. Embedded
NUL/kludge controls and AREA headers are rejected. Request/draft IDs contain
1..80 letters, digits, `_` or `-`. Up to 200 drafts can be stored.

Use a new stable requestId for each logical send and retain it across retries.
The same ID/content yields the same MSGID and durable delivery identity; changing
content with that ID fails. Submission intents persist before mail storage;
the mail store's identity receipts prevent duplicates after interruption.
A successful submission deletes its draft. Send stores mail for normal periodic
export; call processMail to request an immediate processing pass.

## Node operations

| Methods | Behavior |
| --- | --- |
| `status` | API version 2.0, RPC-service uptime, local AKAs, heap usage, active UI polls |
| `links` | Configured peer addresses, hosts, ports, local AKAs and echo subscriptions |
| `subscribe`, `unsubscribe` | Managed SQL echo subscriptions; new subscriptions use NEW_ONLY |
| `rescan` | Explicit repeat-safe rescan of 1..10000 recent messages with a stable requestId |
| `processMail` | Run the existing inbound/scripts/export processing cycle |
| `poll`, `polls`, `cancelPoll` | Queue, inspect and cancel operator-initiated connections |
| `outbound` | Up to 200 prepared mail packets and the next file/TIC pair for a configured peer |
| `logs` | Bounded reads from the configured log file; no client-selected filesystem paths |

Echo administration requires `EchoSubscriptions=database`. Access is READ,
WRITE or BOTH. All mutations use MailService's processing lock, so UI operations
cannot race scripts or inbound/outbound scans.

Polls own independent SessionContexts and use the same Netty transport/mail
callbacks. States are QUEUED, CONNECTING, TRANSFERRING, SUCCEEDED, FAILED and
CANCELLED. Repeated poll requests for an already queued/active peer return the
existing job. One operator poll runs at a time, up to 64 wait; recent history is
bounded to 128 and kept in memory. Cancellation interrupts the job and closes
its client. Connection timeout is 30 seconds; transfer timeout is 120 seconds.
Only configured links can be dialed. Existing scheduler polls and incoming
connections are outside this UI queue and are not included in activePolls.

The outbound method is a snapshot of prepared disk files, including held mail;
it does not enumerate all unexported database deliveries or every queued file
job. Request processMail before reading it when you need freshly exported mail.
It cannot delete files currently owned by a transport session.

For logs, start offset=0 and limit=1..200. nextOffset is a byte cursor; rotated
is true if the file shrank below the cursor. Reads cover at most 64 KiB per call.
Unfinished final lines are deferred, oversized lines are skipped. Same-size or
larger file replacement cannot be detected by this cursor alone: reset offset
when switching a rotated log. RpcLogFile selects an existing log, it does not
configure logging or create a log file.

Errors use codes UNAUTHORIZED, INVALID_ARGUMENT, NOT_FOUND, UNSUPPORTED and
OPERATION_FAILED. Detailed backend failures remain in the node log.

Not implemented here: browser gateway, account administration UI/roles, global tracking
or termination of incoming/scheduled sessions, full-text mail search, trash,
message editing, remote configuration/link/routing-rule CRUD, directory search,
file subscription administration, and durable automatic retry scheduling for
polls. Existing AreaFix/FileFix can be addressed through ordinary netmail sends.

## Database accounts and optional passkeys

`rpc_users` stores login, salted password hash, enabled flag and a stable random
WebAuthn user handle. `rpc_passkeys` stores credential ID, owner, RP ID, public
COSE key, signature counter, backup flags, label and creation time. Private keys
and biometric data are never sent to the node or stored here. The tables belong
to the separate RPC authentication H2 database, not the FTN link configuration.
Existing mail schemas are unchanged. The account repository has an explicit
createUser API for provisioning; remote account creation and roles are not
provided in this version.

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
conversion. No browser UI or HTTP gateway is implemented by these RPC changes.

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
