# File echo plugin

Open **Station > Plugins > File echoes** to manage areas, per-link access,
automatic creation and FileFix/TIC credentials. Changes use the active provider
and apply without restart. READ means the link receives from us; WRITE means it
can send to us. Choose NONE to remove an access/creation rule. Disable an area
to retain its files and metadata while rejecting new imports.

The generic extension mechanism is described in [plugin-settings.md](plugin-settings.md).

The optional `fileecho` JAR owns TIC parsing, validation, durable storage and
forwarding. Shared script/queue contracts live in `mail-api`, outside the runner.
The editable `filefix.groovy` handles robot commands through `api.fileEcho` and
does not register a robot when the provider is unavailable.

`conf.d/32-fileecho.conf` contains `FileEchoEnabled=true`. Set it to `false`, or
remove the plugin JAR, and restart to disable file echo processing. Inbound
originals, receipts and pending delivery jobs remain intact. The mail service
does not load pending file delivery pairs while this provider is unavailable.
An already active transfer is not cancelled by editing a configuration file.

`FileEchoSource=database` is the default. The plugin owns area, access and
automatic-creation tables in the node's SQL database. Declared `FileArea` entries
seed missing initial records only; existing records and disabled areas are not
overwritten on startup. FileFix subscriptions update SQL live.

`FileEchoSource=files` keeps areas in `FileArea` declarations and FileFix
subscriptions in durable script state. In this mode, `AutoCreateFileFrom` uses
`peer|comma-separated-masks|WRITE-or-BOTH`. Automatically created areas and their
creator are retained in state across restarts. UI mutation is unavailable in
file mode. Files configuration is read at startup.

In database mode, configure initial area permissions and creation policies in
the plugin settings. READ permits delivery to a link; WRITE permits imports
from that link; BOTH permits both. A FileFix unsubscribe removes the link's
subscription, including write permission, consistently with AreaFix.

New areas are created only after TIC sender/password, loop, filename, payload
CRC and optional size checks pass. Creation grants access only to the matched
link. Existing areas never gain access implicitly through a creation policy.
Auto-created paths are derived from validated tags beneath `FileEchoRoot`.
An absent or corrupt payload does not create an area. Database creation and
the initial permission grant are one transaction.

TIC authorization retains its explicit database/files source, independently of
the area source. Existing `TicPeer` and `TicCharset` declarations remain supported.
TIC and FileFix passwords are separate from binkp and PKT passwords.

The parser retains unknown fields, validates the authenticated sender and TIC
password, checks CRC/optional size and loops, and limits TICs to 64 KiB, lines to
1024 characters and payloads to 128 MiB. Missing or invalid deliveries stay in
inbound for inspection/retry. Completed receipts suppress duplicate forwarding.

The plugin publishes accepted-file callbacks after durable storage; dependent
directory import scripts continue to receive the same callback. Direct inbound
directory feeds remain independent of TIC processing.

Accepted payloads retain their original names. Case-insensitive name collisions
preserve the incoming copy under `dupes/<receipt>/<name>` without replacing or
forwarding the existing file. Old receipt-prefixed archives are not renamed.
The optional external `fileecho-tools.groovy` script maintains `files.bbs` from
TIC Desc/LDesc fields and creates deduplicated private local netmail reports for
duplicates and errors. These reports never go to an untrusted remote sender.

`conf.d/44-fileecho-tools.conf` controls `FileEchoIndexEnabled`,
`FileEchoIndexName` (`files.bbs` or `file.bbs`), `FileEchoIndexCharset` (UTF-8)
and `FileEchoNotifyEnabled`. Reports go to the sysop at the first local AKA.
The index includes existing regular payloads without known descriptions by name,
excludes subdirectories/staging files, and is replaced atomically. Descriptions
are flattened to one line. Index and report settings apply after script reload.

Templates are `templates/fileecho-index.txt` and `templates/fileecho-notice.txt`
beneath the directory containing the external script. Override
`FileEchoIndexTemplate`/`FileEchoNoticeTemplate` with relative paths inside it;
outside paths and symbolic-link escapes are refused. Literal placeholders are
`${name}`, `${description}` for index rows, and `${status}`, `${area}`, `${name}`,
`${peer}`, `${detail}`, `${path}` for reports. Templates are re-read per operation;
received descriptions are never executable template source. Notification keys
survive restarts; a changed failure reason can generate a new report.

Existing installations retain their active `40-mail-scripts.conf`: explicitly add
`MailScript=./scripts/mail-scripts/fileecho-tools.groovy` before directory-import
and install the adjacent templates. Completed TICs are not replayed, and inbound
originals remain preserved. Java enforces collision safety even without the script.

TIC `Path` entries require a valid address and nonnegative Unix timestamp; optional
human-readable date comments are retained when forwarding.
