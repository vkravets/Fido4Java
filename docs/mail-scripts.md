# Scripted mail services

`MailScript` is repeatable. A value is a local Groovy filename or a bundled
resource such as `classpath:mail-scripts/areafix.groovy`. Scripts are trusted
operator code and have the JVM's permissions.

MailService loads scripts before its first inbound scan. Each script supplies
`register(api)` and optionally `unload(api)`. Startup fails if registration
fails. Scripts unload in reverse order after processing stops. Automatic hot
reload is deliberately not enabled while messages are being processed.

The API exposes `config`, `database`, `tosser`, and `state`. Register synchronous
callbacks with `onNetmail { message, packetPeer, deliveryKey -> boolean }` and
`onScan { ... }`. Netmail callbacks run only for local destinations, after
packet checks and durable storage. The first `true` consumes the request for
subsequent script handlers; the original remains in local mail. Throwing an
exception retains the packet without advancing its processing journal.
Never use `message.setValid(false)` to indicate successful robot handling.

`packetPeer` identifies the validated packet origin. It does not by itself
prove that a routed message's author is that peer. Robots must authorize the
message origin and their own password before making changes.

`state.get(key)` and `state.put(key, text)` store small durable values below
`Tmp/scripts`, using hashed filenames, forced writes and atomic replacement.
Multi-value updates are not a transaction. Changes must be retry-safe; outgoing
mail should use stable delivery keys. Missing atomic move support is an error.
Scans execute under the same processing lock before outbound mail export.
Failed scans are logged and retried on subsequent scans; they must preserve
their source inputs until completion.

## AreaFix

```ini
EchoSubscriptions=database
MailScript=classpath:mail-scripts/areafix.groovy
AreaFixPeer=1:500/101|separate-robot-password|test.*,local.news|READ
```

The last field is READ or BOTH and controls access granted by new subscriptions.
Requests to AreaFix or AreaMgr are case-insensitive. Their destination must be
a local AKA and their author must equal the validated packet peer. Forwarded
requests are not supported. A robot password is mandatory and distinct from
the packet/session password. Unauthorized requests receive no automatic reply.

Supported commands: area or +area, -area, wildcard masks with `*`/`?`, %HELP,
%LIST [mask], %QUERY/%LINKED [mask], %UNLINKED [mask], %RESCAN area [count].
Rescan defaults to 100 recent messages, capped at 10000, and requires a readable
subscription. Latest messages are selected by SQL ID in descending order; gaps
in IDs do not affect the limit. New subscriptions start with NEW_ONLY; request
a rescan explicitly to fetch history. Unsubscribe removes the subscription,
including write access. Existing readable subscriptions are preserved.
Other commands receive an unsupported-command response. A tearline ends input.

The persistent reply cache uses origin plus MSGID (or packet delivery identity
when MSGID is absent); successful replays resend the cached response with the
same outgoing delivery key. A crash between a subscription change and response
storage can retry that change; subscribe/unsubscribe are idempotent and rescans
use stable spool keys.

Command syntax was checked against
[HPT AreaFix help](https://github.com/huskyproject/hpt/blob/master/misc/areafix.hlp).
This is the supported subset, not every HPT extension (pause, remote password
changes, day-based rescans and forwarded uplink requests are not implemented).
