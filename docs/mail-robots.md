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
