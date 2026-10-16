# Docker interoperability test

Scenarios cover **JFtn client -> external binkd server in Docker** and
**external binkd client in Docker -> JFtn server**.
Both peers send a file in the same authenticated TCP session. JFtn then queues
a second file after the first batch, and both peers complete a final empty batch.
Assertions require negotiated binkp 1.1 and at least three EOBs in each direction.
The test uses
production JFtn handlers and codecs and an independent binkd process; it does
not replay a recorded stream. Each role runs with and without an NR request from
JFtn, giving four cases per backend (Netty and MINA2), eight in total. NR assertions check binkd sending to JFtn.

The Netty backend uses Netty 4.2 with NIO I/O handlers. Production connections
and Docker test peers explicitly use the pooled allocator to preserve Netty 4.1
buffer allocation behavior during migration; the adaptive allocator has not
been load-tested here.

## Test structure

The shared `AbstractBinkdInteropTest` describes connection setup and the
shared assertions. Supporting classes live only in the
integration-test source set:

- `ExchangeFiles`: deterministic file contents and outgoing JFtn streams.
- `BinkdTestPeer`: container startup in either role, configuration, file copies,
  client process, logs and container cleanup.
- `AbstractJftnPeer`: session configuration, receive events and completion checks shared by all backends.
- Per-module `JftnTestPeer`: production codecs and handlers, connection
  acceptance and network-resource cleanup.
- `ProtocolTrace`: decoded command recording, password redaction and protocol/NR
  assertions shared by both roles.

Each peer implements `AutoCloseable`; the test uses try-with-resources and saves
diagnostics before closing them. The `binkd.cfg` resource is a template:
`BinkdTestPeer` substitutes `@PEER_ENDPOINT@` before copying it into the container.

## Container lifecycle

1. The explicitly requested `binkdDockerTest` task loads Testcontainers 2.0.5.
2. Testcontainers builds the small [Dockerfile](resources/binkd/Dockerfile),
   using Debian bookworm and the pinned `binkd=1.1a-115-1` package.
3. Each parameter case gets a fresh container, configuration and outbound file.
   Files are copied through Docker's API; host directory mounts are not needed.
4. In the JFtn-client scenario, binkd runs in the foreground as UID 10001, in server-only mode. Testcontainers
   maps its internal port 24554 to a random available host port and waits up to
   90 seconds for the listener. Image pulling/building happens before this
   readiness wait and can take longer on the first run.
   In the JFtn-server scenario, JFtn binds a random loopback port first, then
   `Testcontainers.exposeHostPorts` makes it reachable as
   `host.testcontainers.internal:<port>` through an SSH tunnel. A fresh container
   starts with a readiness marker and idle process; `execInContainer` runs binkd
   in client-only polling mode (`-p -P`). Its exit code must be zero. The caller
   thread has bounded waits and is shut down in cleanup.
5. JFtn connects using `getHost()` and `getMappedPort(24554)`, with a five-second
   connection timeout. Reception and TCP closure each have a 30-second limit.
6. Assertions check both files byte-for-byte, M_GOT in both directions, M_EOB,
   complete JFtn transfer state, and removal of binkd's acknowledged outbound file.
7. Cases cover ordinary transfer and NR requested by JFtn. The NR case requires
   binkd's first M_FILE offset to be -1 and JFtn to answer with M_GET.
8. In `finally`, the test saves diagnostics, closes the connection and Netty
   threads, shuts down the event bus, and stops/removes its container. Testcontainers'
   default Ryuk cleanup also handles resources left by a terminated test JVM.

Plaintext authentication with an isolated test password is intentional in these
scenarios; CRAM-MD5, encryption, resume after disconnect and NR requested by binkd
are separate scenarios to add later. No real FTN nodes are contacted.

## Explicit launch

Requires JDK 17+ and a running Docker-compatible engine for **Linux containers**.
All eight cases were verified on Windows with Podman 6.0.2, a rootful WSL machine,
Docker compatibility enabled, and the WSL host override below. This verifies
ordinary transfer and JFtn-requested NR in both JFtn roles with binkd 1.1a-115, not the other scenarios
listed above.

```powershell
.\gradlew.bat :binkp-root:binkp-netty:binkdDockerTest --console=plain
```

Linux/macOS:

```bash
./gradlew :binkp-root:binkp-netty:binkdDockerTest --console=plain
```

### Podman on Windows / WSL

Docker compatibility exposes the Docker API, but published TCP ports may be
reachable only through the WSL machine's IP rather than Windows localhost.
Without an override, Testcontainers can fail before starting binkd with
`Could not connect to Ryuk at localhost:<port>`.
Run these commands in PowerShell before the Gradle command:

```powershell
$podmanAddress = (podman machine ssh ip -4 -o addr show eth0) -join " "
if ($LASTEXITCODE -ne 0 -or $podmanAddress -notmatch 'inet\s+([0-9.]+)/') {
    throw 'Could not determine the Podman WSL machine address'
}
$env:TESTCONTAINERS_HOST_OVERRIDE = $Matches[1]
.\gradlew.bat :binkp-root:binkp-netty:binkdDockerTest --console=plain
```

Recalculate the address after restarting the machine; it is not a fixed address.
The override affects only this terminal and child processes. To switch back to
Docker Desktop, use `Remove-Item Env:TESTCONTAINERS_HOST_OVERRIDE`.
Ryuk remains enabled; no cleanup mechanism is disabled for Podman.

`test`, `check` and `build` do not depend on this task.
There is no automatic skip when Docker is missing: an explicitly requested run
fails with Testcontainers' diagnostic instead of reporting an unexecuted test
as successful. Each explicit invocation runs again, even with unchanged sources.

## GitHub Actions

The `integration` matrix job in `.github/workflows/ci.yml` runs `binkd` and `hpt`
suites after a successful `build`, on Ubuntu 24.04 with Java 17 and the runner's
Docker daemon. No remote Docker setup, Podman host override or Thrift compiler
is needed in the integration jobs.

The build publishes a compiled snapshot as the `compiled-release` artifact.
`ci/test-integration.sh` verifies its Git revision and SHA-256 checksums before
running tests. The binkd suite runs `binkdDockerTest` for Netty and MINA2, with
`--continue` so both backends can produce diagnostics if one fails. Reports and
per-case logs remain under each backend's `build/reports/binkdDockerTest`, with
JUnit XML under `build/test-results/binkdDockerTest`. The workflow uploads those
module report/result directories as `integration-binkd-reports`, including after
failure; there is no additional `build/binkd-integration` collection step.
Ryuk remains enabled.

`prepareCiIntegration` packages each module's compiled integration test classes,
resources and ordered runtime classpath under `build/ci-integration/<module>/`.
The HPT snapshot also contains Java worker JARs. `-PciIntegrationSnapshot` uses
these files without compiling application/test sources or repackaging their JARs.
Gradle may still compile `buildSrc`, which is build tooling. Local commands retain
their normal compile-and-test behavior unless this property is supplied.
Download caches are an optimization; they never supply the application classes
under test.

After both integration suites succeed, the `ui` job tests component behavior and
the compiled browser application. A separate `vrt` job then compares the compiled
Storybook catalogue with visual baselines. Tag-only packaging waits for
integrations, VRT and UI checks, then packages the same tested binaries. See [distribution and release flow](../../../docs/distributions.md).

## Diagnostics and manual observation

Per-case artifacts are under `binkp/binkp-<backend>/build/reports/binkdDockerTest/`,
with role directories `jftn-client` / `jftn-server`, each containing `ordinary`
and `nr`: `container.txt` (ID and endpoint), `binkd-console.log`,
`binkd.log`, and `protocol.log`. Server-role cases also save `binkd-client.log`
with the client's process output. Password commands are redacted in `protocol.log`;
the binkd logs contain only disposable test credentials. HTML and JUnit reports
are under `build/reports/tests/binkdDockerTest` and
`build/test-results/binkdDockerTest` within each backend module.

While a test is running, the ID in `container.txt` can be used with:

```bash
docker logs -f <container-id>
docker inspect <container-id>
```

The container is removed after the case; use the saved files afterwards.
The Debian base tag can receive updates; the binkd package is pinned. If Debian
removes that package version, image building fails rather than silently testing
a different version. A digest/snapshot pin can be added for fully reproducible
image builds after the first run is reviewed.

References: [binkd command options](https://manpages.debian.org/bookworm/binkd/binkd.8.en.html),
[binkd configuration](https://github.com/pgul/binkd/blob/master/binkd.cfg),
[Testcontainers networking](https://java.testcontainers.org/features/networking/).

## All backends

Each backend has its own `binkdDockerTest` task and isolated test JVM.
Shared source code and resources are compiled separately by
`gradle/binkd-integration.gradle`; production artifacts have no Testcontainers dependency.

```bash
./gradlew :binkp-root:binkp-netty:binkdDockerTest \
  :binkp-root:binkp-mina2:binkdDockerTest --continue --console=plain
```

On Windows use `gradlew.bat` and put the two task names on one line; apply the
Podman host override described above first. An individual backend can also be run
by supplying only its task name.
