# AreaFix and FileFix interoperability

JFtn implements its own robots in external Groovy scripts. Husky is a behavioral
reference, not a source-code dependency. HPT's [AreaFix help](https://github.com/huskyproject/hpt/blob/master/misc/areafix.hlp)
and HTick's [FileFix commands](https://github.com/huskyproject/htick#filefix-commands)
describe the corresponding request syntax. This guide documents JFtn's supported
subset rather than claiming every Husky administration feature.

## Message envelope

Requests are private netmail, not echomail and not TIC attachments:

```text
From: Example Sysop (1:500/100.1)
To:   AreaFix (1:500/100)
Subj: separate-robot-password
Attr: Private

%LIST
+TEST.GENERAL
-TEST.OLD
%QUERY
---
```

For FileFix, replace the recipient name with `FileFix` and use its separate
password. Commands and area matching are case insensitive. Passwords retain
their case. The subject is not a binkp session password, PKT password or TIC
password. Never place these credentials in a public echo. Replies are ordinary
netmail and appear in the netmail reader; they are not automatically trusted as
configuration updates or subscription confirmations from third-party software.

Incoming JFtn requests require an enabled robot access rule and a configured
direct peer whose address equals the message author. See [robot access](robot-access.md).
Unauthorized requests are consumed without a credential-bearing reply. LF, CRLF
and FTN CR line endings are accepted. Processing stops at a `---` tear line.
Requests are limited to 200 lines/64000 characters. Per-command errors do not
prevent later valid commands. Unsupported command arguments are not reflected
in replies, preventing password-changing commands from leaking their arguments.

On the wire these are FTN netmail messages inside ordinary PKTs (possibly bundled
by the other mailer), with MSGID and appropriate INTL/FMPT/TOPT controls. Robot
commands are plain message text, not JSON, a special PKT type or a TIC document.
Our replies set REPLY when the request has MSGID and keep a persistent normal
32-bit MSGID serial when retried. Husky report subjects, sender display names,
help text, language and list layouts can be configured; they are human-readable
reports rather than a single universal machine-readable response schema.

## AreaFix

- `%HELP`, `%INFO`.
- `%LIST`, `%QUERY`/`%LINKED`, `%UNLINKED`, each optionally followed by a mask or
  by `! mask` to exclude matches. Only policy-allowed local areas are listed.
- `area`, `+area`, `-area`, including `*`/`?` masks, operate on allowed areas.
- `+area /r` and `+area /r=N` subscribe and request a rescan. `%RESCAN area [N]`
  rescans readable subscriptions. A standalone `%RESCAN` enables rescan for
  subsequent subscription lines in that request.
- Rescans default to the most recent 100 messages and are capped at 10000.
  HPT's unlimited/default-all and `N days` forms are not implemented. Invalid
  subscription/rescan options are rejected before changing the subscription.
- `%PAUSE`/`%RESUME` persistently suspend/resume echo delivery without deleting
  subscriptions or queued packets. Mail continues to be prepared while paused;
  retained backlog becomes available after resume. Netmail, including robot
  replies, remains available. A transfer already offered in an active session
  can finish; the pause applies when subsequent queues are loaded.

Pause state is operational data under `Tmp/robot-delivery`; keep it in backups.
Deleting/unloading a script does not implicitly resume delivery.

JFtn does not implement `%AVAIL` forwarding/catalog aggregation, destructive
`~area`, remote credential changes, packer/bundle settings, reduced SEEN-BY or
automatic rules delivery. Such commands report unsupported instead of silently
changing unrelated configuration. Local `%LIST` is not an uplink `%AVAIL` catalog.

## FileFix

FileFix is registered only when the optional fileecho provider is present and
enabled. Its commands are `%HELP`, `%INFO`, `%LIST`, `%QUERY`/`%LINKED`, `%UNLINKED`,
`+area`/`area`/`-area`, `%PAUSE`, `%RESUME` and `%RESEND filename area`. Listing
commands accept a mask or `! mask`; subscription masks cannot escape the peer's
FileFix access policy. AreaFix and FileFix pauses are independent.

`%RESEND NODELIST.288 LISTS` queues an exact stored file with a new authenticated
TIC. The request requires an enabled area, a current subscription, FileFix access
and separate outgoing TIC access/credentials. Wildcards, path components, symlinks,
ambiguous case variants and non-DOS-8.3 names are rejected. The stored file is not
modified. Resending twice with the same request identity does not create another
job, even after acknowledgement; a new request identity deliberately sends again.

The fresh TIC describes a resend originating at this station, with recomputed
size/CRC, a new local Path and a generic resend description. It does not pretend
to reproduce the upstream delivery's original Origin, Path or descriptions.
Payload/TIC copies remain in the normal queue until acknowledged. FileFix pause
retains queued copies, including payloads already waiting when pause was requested;
incoming files can still be stored and queued for eventual delivery after resume.
Files already offered in an active binkp session may finish transferring.

`%AVAIL` upstream catalog aggregation is not implemented. An unsupported command
is reported explicitly; no successful subscription/forward request is fabricated.

## Requests to uplink robots

In the existing web netmail composer select your allowed author AKA, enter the
uplink address, put `AreaFix` or `FileFix` in the recipient-name field, the remote
robot's password in the subject and commands in the body. Responses arrive as
normal netmail. This does not use the local robot's inbound-access password.
No new dedicated robot browser form or automatic report-to-catalog import is
provided. An untrusted remote report cannot grant local permissions.

Trusted external scripts can queue requests through the same durable netmail
pipeline:

```groovy
import org.fidonet.types.FTNAddr

api.remoteRobots.submit('AreaFix', new FTNAddr('1:500/101'),
        new FTNAddr('1:500/100'), remotePassword,
        ['%LIST', '+TEST.GENERAL', '%QUERY'], 'subscription-update-001')
```

Supply the remote secret from a protected operator-owned source; do not hardcode
real passwords in distributed scripts. The target must be a configured remote
link and the author must be one of its local AKAs. Each list entry is one command;
embedded control/newline injection is rejected. The helper generates private
UTF-8 netmail with normal MSGID/INTL/addressing via the tosser. It accepts command
text for the remote robot, including extensions not supported by our local robot.
Reusing the same request ID and content does not enqueue another delivery;
changed content/password requires a new ID. Keep `Tmp/scripts` with the mail DB.

Without UI/RPC, stop the station and create a private UTF-8 Java-properties file:

```properties
Robot=FileFix
To=1:500/101
From=1:500/100
Password=remote-filefix-password
RequestId=files-subscribe-001
Commands=%LIST\n+LISTS\n%QUERY
```

`Commands` uses literal `\n` escapes in the properties file; the parser turns
them into separate command lines. Passwords containing properties escape
characters must be escaped using Java-properties syntax too. Protect this file
as a credential, keep it out of Git and remove it when no longer needed.

Run from the installation directory:

```sh
./jftn.sh --robot-request /private/request.properties
```

Windows supports the same arguments with `jftn.cmd`/`jftn.ps1`. An optional final
argument selects `jftn.conf`. The offline command obtains the normal mail-service
lock, requires an existing mail database, queues the request and exits without
listeners, scripts or network connections. Restart the station and poll the
uplink to transmit it. Only an opaque request key is printed, never the password
or command arguments. Do not use the offline command concurrently with a node.

## Updating an existing installation

The distribution runs external `scripts/mail-scripts/areafix.groovy` and
`filefix.groovy`. Updating only JARs leaves the operator's existing scripts intact
and therefore does not enable new script commands. Back up and review/merge these
two script files along with the updated libraries, then restart the station.
Preserve `conf.d`, access rules, subscriptions and `Tmp/scripts`/`Tmp/robot-delivery`.
