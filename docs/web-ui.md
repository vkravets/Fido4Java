# Browser interface

The node hosts its browser interface and Thrift HTTP endpoints in the same JVM.
TCP RPC remains available. No Node.js process is needed when running the node.

## Building and opening the interface

The browser application uses Vue 3 and TypeScript (`strict` mode), built with Vite.
All handwritten browser code and tests are TypeScript. Thrift generates JavaScript
bindings and matching TypeScript declarations from the RPC contracts; these build
outputs are not committed. `vue-tsc` checks Vue templates and API calls before the
production bundle is produced. TypeScript 6.0.3 is pinned because the current
`vue-tsc` does not support TypeScript 7's compiler package layout.

Build prerequisites are Java 17+, the configured Thrift compiler, Node.js 22.12+
and npm. The matching official Thrift browser runtime is supplied through
`THRIFT_BROWSER_RUNTIME`, as for the RPC browser tests. Gradle runs `npm ci` using
the committed lockfile, checks types, builds the browser application, and packages
HTML/CSS/JavaScript into the `web-ui` JAR. It does not install Node.js system-wide.

```sh
./gradlew :web-ui:build
./gradlew :web-ui:typecheckFrontend
./gradlew :web-ui:frontendTest
```

`nodeExecutable` and `npmExecutable` Gradle properties can select existing tools.
GitHub Actions sets up Java 17 and Node.js 24, caches npm downloads by lockfile,
and saves Java and TypeScript test reports. The integration jobs continue to use the
compiled runtimes produced by the main build.

For an already configured node, enable `RpcEnabled` and `RpcHttpEnabled`, then open
`http://localhost:9091/` (or the configured HTTP port/origin). HTTP serves both the
SPA and `/rpc/` endpoints. Light and dark themes can be changed in appearance
settings or with the header toggle and are saved in the signed-in profile.
Session tokens remain in browser memory: reloading the page requires signing in
again. Public registration submits a point AKA claim for administrator approval.
The sidebar reserves navigation for future mail readers; those items are disabled.

Frontend formatting uses Prettier with four-space indentation and a 100-column
limit: run `npm run format` from `web-ui/frontend`.

## Initial configuration

When `jftn.conf` is absent, the runner starts a setup listener on `127.0.0.1:9091`
before starting plugins and mail services. Open the full URL printed in the console;
its fragment contains a random setup token. It is never included in HTTP requests
as a URL parameter. Keep this URL private. Public point registration is unavailable
until setup completes.

The wizard creates an administrator account, station addresses, a primary link and
mail directories. RPC login credentials are independent of binkp link credentials.
The administrator password is hashed in the account database and is not written
to `jftn.conf`. The initial link password also protects PKT files and must contain
at most eight ASCII characters. `-` means an unprotected link.

For an existing configuration, setup is offered only when RPC is enabled, there are
no stored accounts and no configured bootstrap password. Removing `RpcPassword`
after creating an account never reopens setup. If account creation succeeds but
config publication is interrupted, completing setup requires that administrator's
credentials. Account creation and file publication are separate durable operations;
they do not form one database transaction.

Settings are written through a temporary file and atomic replacement. An external
edit detected before replacement prevents saving. Existing unrelated options and
additional links are retained; the settings form edits the first configured link.
Link and administrator passwords are never returned by the settings RPC. Leaving
the link password empty when editing preserves it. Config changes require a node
restart. Existing storage is not moved by this form; SQL database location is not
changed by the mail directory selection.

## Mail robot access

Administrators have a **Почтовые роботы** page under node management. It lists
AreaFix, FileFix and TIC access rules with their link, area masks, enabled state
and AreaFix rights (READ/BOTH). Create a rule for a configured link, edit its
policy/password, disable it temporarily or delete it with confirmation. Changes
are applied to subsequent requests without restarting the node. Existing accepted
work is retained. Passwords are never returned to the browser; an empty password
while editing preserves the existing secret.

The default source is the mail database. With `RobotAccessSource=files`, the page
is read-only and explains how to select database mode and explicitly import file
rules. Point accounts have neither this navigation entry nor RPC permission to
inspect/change rules. Technical script loading, file-area paths and directory-feed
configuration remain in `conf.d`; see [robot access](robot-access.md).

## Outgoing connections

Administrators have a **Соединения** page with active/waiting attempts, paged
history and a **Полл сейчас** action for configured links. Scheduled and manual
attempts use the same queue and duplicate active requests select the existing
job. Attempts can be cancelled or retried; cancellation closes the connection
without deleting queued outgoing files. Incoming server sessions are not listed.

Selecting an attempt shows its source, start/end times, result and dedicated
journal. Prepared outbound files for that link are shown separately (up to 50);
this snapshot is not a list of messages still awaiting packaging. Protocol secrets
are not journalled, and log text is rendered as plain text. The page refreshes
every two seconds after the preceding request completes, pauses in hidden tabs,
and stops on navigation away. Auto refresh and following the journal can be toggled.
The browser retains only one history page and the last 1000 loaded log lines.

History and journals survive restarts under `Tmp/polls`. Unfinished attempts are
marked INTERRUPTED; they are not resumed automatically. `PollHistoryLimit`,
`PollTimeoutSeconds` and `PollLogLimitBytes` are file-only settings in
`conf.d/10-binkp.conf`; see [RPC details](rpc-ui.md). Point accounts cannot see
this section or call the underlying administrative methods.

## Nodelist lookup

Setup can query the local directory or explicitly download a full nodelist from
`NodeListUrl`. The default mirror is
`https://nodelist.fidonet.cc/download/latest?domain=fidonet`; availability is outside
JFtn's control and another HTTP(S) source may be configured. `NodeListCharset`
defaults to `CP866`. Downloads have size/time limits; parsing and checksum validation
must pass before publication. Setup imports into `NodeDirectoryPath` (default
`database/nodelist/catalog`). Lookup metadata is not proof of address ownership.
When only a point's boss is found, the result is marked as a boss record and does
not supply a sysop identity for the point.

## Accounts and access

Public point registration is controlled by `RpcRegistrationEnabled`. New accounts
remain disabled until an administrator approves an AKA claim. Users can select
only approved identities as their default AKA. Personal profile and theme settings
are separate from node settings. The existing node-wide mail/control RPC remains
administrator-only; scoped point mail reading and sending belong to the next UI
phase. Netmail and echo readers are not part of this initial interface.

Passkeys are an additional sign-in method. A new localhost setup enables WebAuthn
for `localhost`. Remote access requires an HTTPS reverse proxy and matching
`RpcHttpOrigin`, `RpcWebAuthnOrigin` and `RpcWebAuthnRpId`. Numeric IP origins do not
automatically enable passkeys. Existing passkey configuration is preserved.

## Received originals and file-only configuration

Station settings include **Save original received files**, enabled by default.
The main configuration stores this as `PreserveReceivedFiles=true` (or `false`).
Settings changes require restarting the node, like the other station settings.
The running service keeps its current configuration until restart.

Completed binkp transfers are copied before processing can remove them. The
standalone tosser also preserves loose PKT/bundle inputs before importing them.
The UI's HTTP nodelist download preserves the received response before parsing.
Archiving errors stop the destructive processing path; the source remains for
retry. Partial/incomplete network transfers are not archived as complete files.

The default archive is `received` beside the configured inbound directory,
usually `mail/received`. Originals are stored as `<SHA-256>/<filename>` with
streamed copies, without loading entire files into memory. Identical bytes and
names reuse an archive entry; different contents with the same name are kept
separately. There is no automatic expiration or quota: remove old archives
manually when they are no longer needed. Disabling preservation affects future
processing and never removes existing archives. `Deletetossed` retains its legacy
meaning for clearing the processed inbound queue; leaving it at its default `1`
avoids repeatedly tossing retained inputs. Originals live outside that queue.

The main `jftn.conf` remains managed by the UI. Create additional manual files
under `conf.d/` **beside that configuration file**, for example:

```ini
# conf.d/50-local.conf
ReceivedArchivePath=./mail/received
Deletetossed=1
```

Only `*.conf` files are loaded, in filename order. Each file replaces all values
of the same key from earlier layers; repeated entries within one file remain a
list. Manual file-only options override the main file. Relative value paths
continue to resolve from the node's working directory. Stream-based configuration
loading has no source directory and does not load fragments.

UI-managed fields are rejected in fragments: `Link`, `System`, `SysOp`,
`Location`, `NodeListUrl`, `NodeListCharset`, `RpcHttpOrigin`, `UiMailStorage`,
`PreserveReceivedFiles`. This prevents a saved UI value from being silently
masked by a manual override. Other settings remain available through files only;
the UI neither exposes them nor rewrites the fragments. Unknown settings already
present in the main file continue to be preserved on UI saves.

Restart after manual edits. On upgrade, preserve your main configuration and
`conf.d/`; distribution archives contain instructions and `*.conf.example` templates there,
not active manual overrides. Startup creates missing plugin files; see
[plugin settings](plugin-configuration.md). Private `conf.d/*.conf` files and the default received archive
are ignored by Git.
