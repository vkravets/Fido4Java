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
