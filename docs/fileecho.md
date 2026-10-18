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
