# File echo plugin

The optional `fileecho` JAR owns TIC parsing, validation, durable storage and
forwarding. Shared script/queue contracts live in `mail-api`, outside the runner.
The editable `filefix.groovy` handles robot commands through `api.fileEcho` and
does not register a robot when the provider is unavailable.

`conf.d/32-fileecho.conf` contains `FileEchoEnabled=true`. Set it to `false`, or
remove the plugin JAR, and restart to disable file echo processing. Inbound
originals, receipts and pending delivery jobs remain intact. The mail service
does not load pending file delivery pairs while this provider is unavailable.
An already active transfer is not cancelled by editing a configuration file.

Existing `FileArea`, `TicPeer`, `TicCharset` and FileFix declarations continue to
apply. FileFix/TIC authorization retains its explicit database/files source.
TIC and FileFix passwords are separate from binkp and PKT passwords.

The parser retains unknown fields, validates the authenticated sender and TIC
password, checks CRC/optional size and loops, and limits TICs to 64 KiB, lines to
1024 characters and payloads to 128 MiB. Missing or invalid deliveries stay in
inbound for inspection/retry. Completed receipts suppress duplicate forwarding.

The plugin publishes accepted-file callbacks after durable storage; dependent
directory import scripts continue to receive the same callback. Direct inbound
directory feeds remain independent of TIC processing.
