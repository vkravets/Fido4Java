# Browser interface

The node hosts its browser interface and Thrift HTTP endpoints in the same JVM.
TCP RPC remains available. No Node.js process is needed when running the node.

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
