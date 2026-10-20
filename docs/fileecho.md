# File echo plugin

## Husky compatibility and Replaces

The reference is HTick (Husky's file ticker), not HPT's netmail scanner. TIC `To`
accepts either an address or `Name, address`, `Size` is compared numerically, and
`Date` supplies the file date. Missing Path/Seenby lists are accepted like HTick;
present entries remain validated. Relay preserves Origin, descriptions, Date,
unknown extension lines and existing Path entries, replaces From/To/Pw, appends a
UTC Path annotation and sorts unique Seenby addresses numerically. Passwords are
still checked against the authenticated transport peer's independent TIC rule.

`TicReplaces=true` in `conf.d/32-fileecho.conf` enables authenticated `Replaces`
processing after payload CRC/size validation. Matching regular files are removed
from the active area before the new payload is installed. Old copies are retained
under `dupes/<receipt>/replaced/`; indexes/subdirectories/symlinks are excluded.
Like HTick, a mask beginning with `*` is ignored. Other masks must be bounded
filename patterns without paths or traversal. A durable receipt prevents retry
after a callback failure from replacing the newly installed file again. The
fileecho-tools script removes stale index rows when updating `files.bbs`.
Set `TicReplaces=false` and restart to retain collision-only behavior.

Reference: [HTick toss.c](https://github.com/huskyproject/htick/blob/b146b48b8ff4e1e86179bc68dccc8fc19b37c464/src/toss.c).

## Administration

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

`FileEchoSource=database` is the default. FileEcho obtains area, access and
automatic-creation storage from the DB plugin through `FileEchoStorage`.
The `fileecho-storage-h2` adapter uses the node's existing SQL source; FileEcho
does not own a connection or contain SQL/ORM mappings. Existing tables are kept
in place. See [storage adapters](storage.md#fileecho-storage).
Declared `FileArea` entries
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
`${name}`, `${size}`, `${date}`, `${description}` for index rows, and `${status}`, `${area}`, `${name}`,
`${peer}`, `${detail}`, `${path}` for reports. Templates are re-read per operation;
received descriptions are never executable template source. Notification keys
survive restarts; a changed failure reason can generate a new report.

The default index template aligns filename, size, date and description:

```text
${name:<14} ${size:>10}  ${date}  ${description}
```

`${size}` is the validated payload size in bytes with comma thousands separators,
independent of the host locale. TIC `Size`, when present, must match those bytes;
when absent, the actual payload size is used. `${date}` uses the TIC `Date`
Unix timestamp when present, otherwise the first TIC `Path` timestamp, formatted
as `MM-dd-yy` in UTC. Human-readable Path comments are not parsed. For matching archived copies,
the earliest first-hop timestamp is retained. A missing/unrepresentable date
prints `-- -- --`; files without matching archived TICs still show their actual
size. The format is shared by live delivery and offline rebuild.

An optional `:<width>` or `:>width` aligns a literal placeholder left/right; widths
are 1..256 characters. Widths are minimums: long values are not truncated. Choose
a larger filename width when an area contains long names. The example uses
`${name:<14}` and `${size:>10}`; `${name}` still expands without padding in custom
templates. Rebuild existing indexes once after changing their column layout;
live updates retain untouched rows already present in the index.

Existing installations retain their active `40-mail-scripts.conf`: explicitly add
`MailScript=./scripts/mail-scripts/fileecho-tools.groovy` before directory-import
and install the adjacent templates. Completed TICs are not replayed, and inbound
originals remain preserved. Java enforces collision safety even without the script.

TIC `Path` entries require a valid address and nonnegative Unix timestamp; optional
human-readable date comments are retained when forwarding.

## Rebuilding an index from retained TICs

Stop the station, then run from the installation root:

```powershell
.\jftn.cmd --rebuild-fileecho-index nodelisz .\fileecho\nodelisz
.\jftn.cmd --rebuild-fileecho-index r46_node .\fileecho\r46_node
.\jftn.cmd --rebuild-fileecho-index r46_pnt .\fileecho\r46_pnt
```

On Unix use `./jftn.sh` with the same arguments. The first argument after the
command is the TIC **Area tag**, not an arbitrary folder label; the second is
the existing payload directory. An optional final argument selects a configuration
file instead of `jftn.conf`. Its adjacent `conf.d` fragments are loaded normally.
The optional fileecho plugin must be installed and the external
`fileecho-tools.groovy` must be listed in `MailScript`.

The command holds the same `Tmp/mail-service.lock` as the mail runtime and refuses
to run while that runtime is active. It starts no listeners, database or mail
processing. It recursively reads regular `*.tic` files beneath
`ReceivedArchivePath` (default: `mail/received`, relative to the configured inbound
parent). Archive subdirectory symlinks are not followed. Originals still in inbound,
TICs inside compressed bundles, and missing/deleted archives are not reconstructed.

Descriptions are recovered only when Area, case-insensitive File name, CRC and
optional Size match a current payload. Desc/LDesc use `TicCharset` (IBM437 by
default). Invalid TICs are skipped with a warning; conflicting descriptions for
the same matching payload abort before index replacement. This is metadata
recovery from an operator-controlled archive, not sender authorization or TIC
reprocessing. Passwords, forwarding, import receipts and duplicate notifications
are not changed.

The external script renders a fresh index using `FileEchoIndexName`,
`FileEchoIndexCharset` and `FileEchoIndexTemplate`, even if automatic indexing is
disabled. Files without matching metadata retain blank descriptions and an unknown date. The command
prints counts with/without descriptions. It indexes only direct regular payloads,
excluding hidden files, existing indexes, symlinks and subdirectories such as
`dupes`. Filename whitespace/control characters and case-insensitive collisions
are refused. Legacy hash prefixes must be removed separately before recovery.

An existing index is copied to a unique hidden `.files.bbs-backup-*.bak` (or
`.file.bbs-backup-*.bak`) in the area before atomic replacement. Old rows are not
merged, so removed or renamed filenames disappear. Payloads and retained TICs
are never modified. Repeating the command produces the same index and another
backup.
