# Real BSO and HTick interoperability tests

These are explicit integration tasks, outside ordinary unit tests. They use real
binkd and HTick processes in isolated Linux containers. No installed station,
private configuration, host inbound or host container-engine socket is mounted.

## Build and run

Load the development environment first. Docker or Podman must be installed and
its Linux engine running. The engine CLI must work from the same shell as Gradle.

```powershell
podman info
podman build -t localhost/jftn-husky-interop:local jtoss/src/integrationTest/resources/hpt
.\gradlew.bat :jtoss:bsoInteropTest :jtoss:htickInteropTest '-DmailInterop.engine=podman' '-DmailInterop.image=localhost/jftn-husky-interop:local'
```

```sh
docker info
docker build -t jftn-hpt-interop:local jtoss/src/integrationTest/resources/hpt
./gradlew :jtoss:bsoInteropTest :jtoss:htickInteropTest \
  -DmailInterop.engine=docker -DmailInterop.image=jftn-hpt-interop:local
```

Quote dotted `-D` arguments in PowerShell. These suites use the engine CLI and
do not require Windows Testcontainers Docker API discovery. The older HPT/JAM
and transport suites retain their Testcontainers setup. There is no engine socket
inside a test container and no privileged container.

Java, binkd and HTick run as container UID/GID `10001:10001`. Configuration
copied from host temporary files is explicitly assigned to that identity with
mode `600`, and readability is checked before use. This avoids Docker's
root-owned copies of Linux temporary files preventing daemon startup. Only the
fixture's ownership/permission adjustment uses container UID 0; mail processes
continue to run unprivileged.

## Scenarios

The sender's production Java worker creates SQL deliveries and BSO handoffs, then
exits. A real binkd in that same container consumes FLO/REQ paths; a second binkd
receives over the private network. Fresh Java processes reconcile delivery state.
Each case has its own containers, network, SQL and journals, with generic 1:500
fixtures and intentional point/zone variants.

BSO cases cover PKT contents and independent packet passwords, echomail/netmail,
attachments, post-ACK KFS/TFS with retained originals, unavailable peers, standard
flavours, passive hold versus explicit polling, point/zone paths, busy ownership
and repeated processing after restart. They do not simulate ACK by deleting copies.

FRQ cases exercise binkd's actual immediate SRIF hook. A deliberately minimal
test-only responder serves one fixed payload or returns no matches. Both requests
can be delivered successfully; only the matching request returns a file. This is
not a production request processor. The SRIF contract is described in
[binkd's SRIF documentation](https://github.com/pgul/binkd/blob/master/doc/srif.txt).

HTick cases deliver JFtn-generated TIC/payload through binkd to `htick toss`, then
deliver `htick hatch` output back to JFtn. They check contents, descriptions,
authentication, CRC/size validation, Replaces retention and restart/idempotence.
Rejected real deliveries remain available for correction and retry.

The receiver binkd writes flat inbound files. A fixture records the configured
authenticated peer and original filenames before invoking JFtn's production
fileecho plugin. This boundary represents receipt metadata normally supplied by
the built-in binkp adapter; it does not claim to test that adapter again.

## Diagnostics and CI

Reports are under `jtoss/build/reports/tests/{bso,htick}InteropTest/`. Detailed
artifacts are under `jtoss/build/reports/{bso,htick}InteropTest/<case>/<run-id>/`:
argument lists, command output and a filesystem archive for each peer, including
binkd/HTick logs, pinned Husky revisions, SQL, TICs and queue journals. Only test
credentials are used. Cleanup targets only container IDs and the network created
by that test; diagnostics failure does not skip container removal.

GitHub Actions runs separate `bso` and `htick` matrix jobs after the build. They
verify revision/checksums and use the sealed `:jtoss:prepareCiIntegration` runtime
through `-PciIntegrationSnapshot`; they do not recompile application sources.
The UI/VRT/release dependencies wait for these jobs too.
