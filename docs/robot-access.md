# Mail robot access: files or database

AreaFix, FileFix and TIC authorization share one Java API and two interchangeable
providers. This is independent of whether the UI/RPC listeners run. The node still
uses its normal mail database, subscription state and durable delivery queues.
The selected source controls authorization rules, not message storage.

The full distribution defaults to `RobotAccessSource=database`, including when
the setting is absent. Administrative RPC rule editing and explicit file import
use the same repository. Existing configuration files are
never overwritten: an explicit `RobotAccessSource=files` keeps file mode active.

## File-only administration

Select file mode explicitly with this in `conf.d/40-mail-scripts.conf`:

```ini
RobotAccessSource=files
MailScript=./scripts/mail-scripts/areafix.groovy
MailScript=./scripts/mail-scripts/filefix.groovy
MailScript=./scripts/mail-scripts/directory-import.groovy
```

In `conf.d/41-areafix.conf`:

```ini
EchoSubscriptions=database
AreaFixPeer=1:500/101|separate-areafix-password|test.*,local.news|READ
```

In `conf.d/42-filefix.conf`:

```ini
FileArea=lists|./fileecho/lists|Node and point lists
FileFixPeer=1:500/101|separate-filefix-password|lists
TicPeer=1:500/101|separate-tic-password|lists
```

Each peer must also have a configured station `Link`. Rules are enabled by their
presence; remove/comment an entry to disable it. Duplicate peer/service entries,
unknown links, empty passwords/masks and invalid AreaFix rights fail validation.
AreaFix supports READ or BOTH. Command passwords are case-sensitive; TIC retains
its existing case-insensitive wire-password behavior. Area masks are matched
case-insensitively, with `*`, `?`, and comma-separated alternatives.

File rules form an immutable snapshot at script initialization. Validate your
edits and restart to apply them. File mode never imports into or reads the robot
rule table; an absent file rule denies access even if the database has one.

For a node without UI, first create a complete `jftn.conf` using the distributed
example, with your real station identity and links. Put `RpcEnabled=false` in
`conf.d/90-rpc.conf`. The normal launchers then run mail services without starting
the first-run wizard or RPC listeners. No browser/account registration is needed.
EchoSubscriptions=database is compatible with this mode: subscriptions are
operational state, independent of the source of access rules.

Validate without starting the node, listeners or plugins:

```sh
./jftn.sh --check-robot-access
./jftn.sh --check-robot-access /path/to/jftn.conf
```

Windows launchers accept the same arguments (`jftn.cmd` or `jftn.ps1`). The command
reports only the number of rules and does not open a database or change files.
It always validates the file rules, including when preparing an import.

## Database administration

The packaged `conf.d/40-mail-scripts.conf` selects `RobotAccessSource=database`.
This is also the runtime default when the setting is absent.
Only one source is active for all three services. There is no merge, automatic
import or fallback: an empty store, disabled rule or deleted rule denies access.
A file rule cannot restore permissions revoked in the database. An unknown mode
is an error. Changing the source requires a restart.

The independent `mail_robot_access` table is part of the mail database, not the
RPC account database. `DatabaseManager.getRobotRules()` exposes the repository
for trusted administration code. Updates/deletions are read on the next command
or TIC authorization without restarting scripts. Existing accepted/queued work
is not cancelled retroactively.

The authenticated `ui` RPC service exposes `robotAccess`, `saveRobotRule` and
`deleteRobotRule` over both HTTP/JSON and TCP. Every method requires an administrator
session; point accounts cannot read or change these rules. Responses contain only
service, address, area masks, AreaFix rights and enabled state, never credentials.
A new rule requires a password. Omit it (or send an empty string) when editing to
keep the existing credential, including while disabling/re-enabling a rule. A
nonempty password replaces it. Each service/address pair identifies one rule;
to change its identity, create a new rule and delete the old one.

In file mode the API returns the file snapshot for inspection, but save/delete
fail with `READ_ONLY`. It never writes configuration fragments, switches sources
or imports rules implicitly. Switch the source explicitly and restart if needed.

AreaFix/FileFix passwords are salted PBKDF2-HMAC-SHA256 verifiers (600000
iterations). The public rule API does not expose their plaintext. TIC forwarding
must emit its shared secret, so TIC credentials remain recoverable in the private
store, as in file configuration. Protect configuration files and database backups.
Rules and passwords are never included in command output.

## Explicit one-time import

Stop the node first, then run from its installation directory:

```sh
./jftn.sh --check-robot-access
./jftn.sh --import-robot-access
```

The optional configuration path works for both commands. The database path is
relative to the node working directory and is the same one used by the database
plugin. The import validates every file rule and writes them in one transaction.
It requires an empty rule table; existing rules are never replaced. Any failure
rolls back the import. Other mail data is retained. The command starts no RPC,
UI, scheduler or binkp listener and does not change configuration files.

After a successful import, select `RobotAccessSource=database` and restart.
Retain the original files as a backup; they are ignored for runtime authorization
in database mode. Switching back to files deliberately restores the file snapshot,
so review old rules before doing so.

## Export for operation without UI/RPC

Stop the node and run from the installation directory. Export requires the existing
mail database; it refuses to create one when run from the wrong directory.

```sh
./jftn.sh --export-robot-access conf.d/49-robot-access.conf
./jftn.sh --check-robot-access
```

Windows: `jftn.cmd --export-robot-access conf.d/49-robot-access.conf` (PowerShell
launcher accepts the same arguments). An optional third argument selects the main
configuration: `--export-robot-access output.conf /path/to/jftn.conf`. Paths are
relative to the working directory, including the normal mail database path.

The destination must not exist. The command writes a completed temporary file,
then publishes it under the requested name without replacing another file. Other
configuration files and the database rules remain unchanged. To refresh an export,
write to a new filename outside `conf.d`, review/validate it, then replace the old
fragment while the node is stopped. Keep one active snapshot fragment.

The exported fragment sets `RobotAccessSource=files` and `RobotAccessFormat=1`.
Putting it in `conf.d` after `40-mail-scripts.conf` selects file mode on next start.
If saved elsewhere, it is a backup until you install it in `conf.d`. It preserves
all services, masks, rights, password verifiers and enabled/disabled states. All
referenced peers must still have configured station links. With format 1 selected,
the snapshot is the entire rule store: legacy `AreaFixPeer`, `FileFixPeer` and
`TicPeer` entries are ignored. An empty export explicitly denies all robot access.

In `conf.d/90-rpc.conf`, disable the listeners separately:

```ini
RpcEnabled=false
RpcHttpEnabled=false
```

Keep the existing complete station configuration; then start normally. Mail robots
use the exported rules without UI/RPC. The mail database still stores messages,
subscriptions and queues. File-rule changes require a restart.

`RobotAccessRule` rows have six pipe-separated fields:
`service|address|base64(credential)|base64(masks)|access|enabled`.
The command verifiers stay PBKDF2; they are not converted to plaintext or rehashed.
TIC secrets are Base64-encoded for delimiter-safe storage; **Base64 is not
encryption**. Treat the snapshot as a private credential file. On POSIX it is
created with mode `0600`; on Windows it inherits the destination directory ACL.
No credentials are printed to the console.

For an empty store the row is `RobotAccessRule=none`, so a later empty snapshot
also replaces entries inherited from an earlier fragment. Do not combine snapshot
rows across fragments. To hand-author plaintext rules instead, remove the snapshot
settings and use the legacy `*Peer` format described above.

The existing `--check-robot-access` validates snapshots, and
`--import-robot-access` can restore one into an empty database rule table without
changing credentials or enabled states. To return to database mode, import if
needed, remove the active snapshot fragment (which selects `files`), and re-enable
RPC in `90-rpc.conf`. The normal packaged default then selects the database.

Technical script loading, charset, file-area paths and directory-feed settings
remain in conf.d in both modes. Scripts retain command parsing and actions;
credential checks and access policy selection are implemented in Java.
