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
