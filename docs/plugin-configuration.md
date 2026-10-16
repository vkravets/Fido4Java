# File-only plugin configuration

Plugin implementation settings belong in `conf.d/*.conf` beside `jftn.conf`.
They are not part of the UI's node configuration model. Basic station identity,
links, mail storage, browser origin and the received-file preservation switch
remain UI-managed settings in the main file.

The distribution supplies these templates as `*.conf.example`. On startup the
node creates missing editable `*.conf` files from the bundled templates, keeping
all existing files byte-for-byte. The default script list enables AreaFix, FileFix and directory import using
external `./scripts/mail-scripts/*.groovy` files, in that order, with
`EchoSubscriptions=database`. Authorization and feed examples remain commented:
configure trusted peers, areas and feeds explicitly, then restart. An empty feed
list is supported and does not create a directory catalog. Other plugin examples
remain commented. Existing active files are never rewritten, including older
script lists; update those manually to adopt the new defaults.

| File | Configuration |
| --- | --- |
| `10-binkp.conf` | Transport listen port and advertised capabilities (`BinkpPort`, `NDL`) |
| `20-jam-bridge.conf` | `JamBridgeEnabled`, `JamCharset`, `JamEcho`, `JamNetmail` |
| `30-nodelist.conf` | `NodeDirectoryEnabled`, `NodeDirectoryPath`, `DirectoryRefreshSeconds`, `DirectorySource` |
| `40-mail-scripts.conf` | `RobotAccessSource` and all `MailScript` entries, in handler registration order |
| `41-areafix.conf` | `EchoSubscriptions`, `AreaFixPeer` |
| `42-filefix.conf` | `FileArea`, `FileFixPeer`, `TicPeer`, `TicCharset` |
| `43-directory-feed.conf` | `DirectoryFeed`; shared catalog path is in `30-nodelist.conf` |
| `49-robot-access.conf` (optional export) | Complete versioned `RobotAccessFormat`/`RobotAccessRule` snapshot; selects file mode, replaces legacy peer rules |
| `90-rpc.conf` | File-only listener, session, registration and passkey internals |

The database and scheduler plugins currently expose no configuration keys.
The database URL and scheduler policy are implemented in code; no dummy options
are provided for them. Station links and AKAs are shared node data, not private
transport plugin options, and remain in the main configuration.

`NodeListUrl` and `NodeListCharset` configure the UI's lookup/download operation;
they remain in the main file. They are separate from the directory plugin's
`DirectorySource` and refresh settings. The browser origin is similarly distinct
from the RPC listener's bind/port and WebAuthn relying-party settings.

Keep all repeated values for one key in one file. A later fragment replaces the
entire earlier list for that key. In particular, splitting `MailScript` across
the AreaFix/FileFix/feed files would leave only the last script enabled.
The numeric file names do not determine handler order; the `MailScript` list does.

An existing main configuration remains readable for compatibility. To move an
operator option, transfer all its entries and associated comments to the matching
fragment, then remove them from `jftn.conf`. A fragment takes precedence over the
main file; do not keep two editable copies of an option. The repository's main
examples no longer contain plugin configuration blocks. No installed user
configuration is silently rewritten during startup.

Initial setup bootstraps RPC/account defaults once in the main file. Later UI
saves do not re-enable RPC or replace its authentication path. Manual RPC options
can be moved to `90-rpc.conf`; the UI never writes that file. Keep the UI-managed
`RpcHttpOrigin` in `jftn.conf`, and update file-only WebAuthn settings explicitly
if the public browser address changes.

For upgrades, preserve `jftn.conf`, `conf.d/`, scripts and all data, then replace
application binaries. ZIPs contain examples, not active plugin configuration,
so extracting an upgrade does not overwrite your active `*.conf` files.

AreaFix/FileFix/TIC access rules support file-only and database administration.
See [source selection and offline commands](robot-access.md); neither mode requires RPC.
