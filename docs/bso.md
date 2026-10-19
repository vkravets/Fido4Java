# External Binkley-style outbound

Set `BsoEnabled=true` in `conf.d/26-bso.conf` and restart to hand delivery to an
external BSO mailer. Default operation keeps the built-in binkp transport.
`BsoOutbound` defaults to `Outbound`; `BsoZone` defaults to the first local AKA's
zone. Both can be overridden in this file. Native scheduled/manual outgoing polls
are disabled in external mode; use the external mailer's poll controls.

The handoff uses standard FLO lists: `.flo` normal, `.clo` crash, `.hlo` hold,
`.dlo` direct and `.ilo` immediate. Nodes use `nnnnffff` hexadecimal net/node
basenames; points use `nnnnffff.pnt/pppppppp`. Other zones use an `.zzz` outbound
suffix. Raw PKTs retain their eight-hex-digit `.pkt` names and are offered through
FLO, rather than appended to shared `.out` files. Fileecho pairs list the payload
before its TIC. Product identity remains JFtn, not HPT/HTick.

FLO entries use `^absolute-path`: the mailer must delete that **handoff copy**
after successful transmission. The authoritative queue remains separate. Packet
delivery is acknowledged into SQL only after its handoff copy disappears; a
file/TIC job waits for both copies. Published jobs are not recreated after restart
or repeated processing. Do not delete handoff copies manually to cancel mail:
deletion is the external mailer's acknowledgement signal.

`Tmp/bso-journal` contains the durable handoff records and copies. Back it up with
mail SQL, internal outbound, BSO control files and received-file archives. The
external mailer must see the same absolute filesystem paths. Drain all handoffs
before moving the node, changing paths or switching back to native transport;
ownership records prevent an outstanding BSO delivery from also being sent natively.

Publication honors `.bsy` and `.csy` and atomically replaces the FLO while holding
the peer's busy lock. A crash can leave `.bsy`; JFtn never deletes another
process's lock or guesses that it has expired. Stop the affected mail programs,
verify the owner is gone, then remove the stale lock before retrying.

Reference naming/locking:
[Husky NCreateOutboundFileNameAka](https://github.com/huskyproject/fidoconf/blob/62c963a6f078905660f2c0b3a6929383823190c5/src/common.c).
