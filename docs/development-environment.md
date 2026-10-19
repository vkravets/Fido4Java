# Development environment and recovery runbook

Last reconciled: 2026-10-17. This guide is for a developer checkout, not a running
mail node. Start with [decisions](decisions.md), [TODO](../TODO) and the current Git
state to restore project context. The top-level [AGENTS.md](../AGENTS.md) is the
entry point for coding agents. User-facing operation remains documented in the service guides.

## What is reproducible, and what is not

The development lock pins downloaded Node, Temurin JDK, Thrift source/compiler and
the Windows Python bootstrap by URL and SHA-256. Gradle has its own wrapper lock;
npm uses the committed package lock; release JREs have a separate runtime lock.
The VRT container has a pinned Playwright image digest.

This is a reproducible tool profile, not a byte-for-byte snapshot of an operating
system. CI selects Ubuntu 24.04, Java major 17, Node major 24 and Python major 3.13;
those action-selected patch versions can move. The local profile fixes exact JDK
and Node downloads instead. Unix Thrift is compiled locally, so its binary also
depends on the host C++ compiler, Boost, libc and build tools. Gradle dependencies
are declared in project build files, but there is no claim that every Maven
transitive dependency is locked by content hash. `mavenLocal()` can also affect
resolution; use a clean machine/container when investigating local-only differences.
User npm configuration, registry/proxy variables and Gradle properties/init scripts
can also affect a build. Bootstrap does not clone those private settings. Check
them when results differ, and transfer required credentials separately from caches.

The scripts do not install Docker/Podman, repair WSL/cgroups, install system Linux
browser libraries, change system Java/Python, modify the production node, log in to
GitHub, restore credentials or start interoperability tests automatically.

## Files to preserve in Git

| File | Authority |
| --- | --- |
| `AGENTS.md` | Stable editing, testing and collaboration instructions |
| `docs/decisions.md` | Accepted architecture and the reasons for it |
| `tools/development-tools.lock.json` | Download URLs, versions, platform variants and SHA-256 |
| `tools/bootstrap.py`, `.ps1`, `.sh` | Tool preparation and environment generation |
| `gradle/wrapper/gradle-wrapper.properties` | Gradle distribution and external wrapper pins |
| `gradle/runtime-lock.json` | Packaged release JREs; **not** the development JDK |
| `web-ui/frontend/package.json`, `package-lock.json` | Frontend dependency graph |
| `web-ui/frontend/test/browser/visual.Dockerfile` | VRT browser/OS container |
| `ci/`, `.github/workflows/` | CI execution, snapshot verification and release scripts |

Do not commit tool archives, installed tools, `node_modules`, generated Thrift
code, private configuration, databases, mail, auth stores or Gradle cache contents.
Intentional binary test fixtures and VRT baselines remain versioned exceptions.

## Recorded tool profile

The manifest is authoritative; this table is a readable checkpoint.

| Tool | Development profile | Notes |
| --- | --- | --- |
| JDK | Temurin 17.0.20.1+1 | Full JDK, including `javac`; aligns with CI's Java major |
| Java source/bytecode | 11 | Root JavaCompile uses `options.release=11`; does not make all build tooling Java-11-compatible |
| Gradle | 9.8.1 | Use the wrapper, not a separately installed global Gradle |
| Node | 24.22.0 | Includes npm; used for TypeScript, Vite, Storybook and Playwright |
| Python on Windows | 3.13.9 embedded x64 | Standard-library-only bootstrap and CI helper tests; no pip required |
| Python on Unix | Installed Python 3.12+ | Prefer the project's CI major 3.13; bootstrap does not compile/install Unix Python |
| Thrift compiler/runtime | 0.25.0 | Browser runtime comes from the matching official source archive |
| Playwright | 1.64.1 | Package lock selects matching browser revisions |
| Release JRE | See `gradle/runtime-lock.json` | Existing JRE 21 profile is independent of the build JDK |

Supported pinned profiles: Windows x64; Linux x64/arm64; macOS x64/arm64.
The PowerShell entry point is Windows x64 only. There is no complete Windows
ARM64 profile in this lock. Linux artifacts expect a compatible glibc distribution,
not an arbitrary musl/Alpine host. Unix implementations require host prerequisites;
the Windows profile is the locally exercised reference, not proof of every Unix host.

The original workstation used Oracle JDK **25.0.4.1** on PATH before this bootstrap.
This explains older test/build logs without making Oracle JDK 25 a prerequisite.
Use `-SystemJava` / `--system-java` to deliberately retain an installed JDK 17+;
that is a diagnostic/local profile, not the pinned Temurin baseline.

## Windows: clean clone to ready tools

Prerequisites: Git, Windows x64 and 64-bit PowerShell. Internet access is needed for
uncached archives/dependencies. The launcher downloads Python into the checkout
using its locked hash; a global Python installation is not required.

```powershell
git clone https://github.com/vkravets/Fido4Java.git
Set-Location Fido4Java
.\tools\bootstrap.ps1 -Frontend
. .\.dev-tools\dev-env.ps1
.\gradlew.bat --version
.\tools\bootstrap.ps1 -Check
```

If local PowerShell policy blocks the script, an explicitly scoped alternative is
`powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\bootstrap.ps1 -Frontend`.
This applies to that child process; do not change machine-wide policy for this task.
The child process cannot change its parent's environment: dot-source the generated
profile afterwards in the terminal where Gradle will run.
If policy also blocks dot-sourcing the generated profile, use
`Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass` in that terminal before
loading it, where local policy permits. That setting ends with the terminal and
does not change machine/user-wide execution policy.

Optional browser preparation:

```powershell
.\tools\bootstrap.ps1 -Frontend -Browsers
. .\.dev-tools\dev-env.ps1
```

Optional existing-JDK profile:

```powershell
.\tools\bootstrap.ps1 -SystemJava -Frontend
. .\.dev-tools\dev-env.ps1
```

There is no background service installed by these commands. Close/reopen your
terminal to discard the environment; the generated profile can be loaded again.
The bootstrap does not launch the node or bind its binkp/RPC ports.

## Linux: prerequisites and bootstrap

Use a supported Ubuntu/glibc environment; Ubuntu 24.04 is the CI reference. Install
the OS build tools deliberately before invoking the bootstrap:

```bash
sudo apt-get update
sudo apt-get install -y python3 git ca-certificates build-essential bison flex libtool pkg-config libboost-dev
git clone https://github.com/vkravets/Fido4Java.git
cd Fido4Java
bash tools/bootstrap.sh --frontend
source .dev-tools/dev-env.sh
./gradlew --version
bash tools/bootstrap.sh --check
```

Python must be 3.12+; the safe tar extraction uses its `data` filter. For another
Python location, set `JFTN_BOOTSTRAP_PYTHON=/absolute/path/to/python3` before running
the launcher. Node, JDK and source archives are downloaded locally; Thrift is built
under `.dev-tools/thrift-source`, with no `sudo make install` or global PATH edits.

For browser tests, `--browsers` downloads Chromium, but does not install OS shared
libraries. Use the supported Playwright dependency setup for your distribution,
for example `npx playwright install-deps chromium` from the frontend after loading
the profile (this can require administrative package installation). CI uses
`npx playwright install --with-deps chromium` on its disposable Ubuntu runner.

## macOS: prerequisites and bootstrap

Install Git/Xcode Command Line Tools, Python 3.12+ and the native Thrift build
dependencies. If using Homebrew, the expected packages are Python, Boost, bison,
flex, libtool and pkg-config; Homebrew's bison/flex may need their `bin` paths
explicitly added ahead of Apple's older system tools. For example, with Homebrew
already installed and initialized:

```bash
brew install python@3.13 boost bison flex libtool pkg-config
export PATH="$(brew --prefix bison)/bin:$(brew --prefix flex)/bin:$PATH"
export CPPFLAGS="-I$(brew --prefix boost)/include ${CPPFLAGS:-}"
export LDFLAGS="-L$(brew --prefix boost)/lib ${LDFLAGS:-}"
export JFTN_BOOTSTRAP_PYTHON="$(brew --prefix python@3.13)/bin/python3.13"
bash tools/bootstrap.sh --frontend
source .dev-tools/dev-env.sh
./gradlew --version
```

The JDK's macOS `Contents/Home` layout is handled by the manifest. This checkout's
bootstrap logic and archive safeguards have unit tests, but a successful native
macOS compile/browser/container run must still be recorded on an actual Mac.

## What bootstrap creates

```text
.dev-tools/                         ignored, survives Gradle clean
  downloads/                       official archives, verified on reuse
  python-3.13.9/                   Windows launcher-owned Python
  node/                            pinned Node/npm
  jdk/                             pinned JDK, unless system-java was selected
  thrift.exe                       Windows compiler
  thrift-source/                   matching browser runtime; Unix native compiler build
  playwright-browsers/              only populated when browsers are installed
  environment.json                 local profile/version/path record, not a secret dump
  dev-env.ps1 / dev-env.sh          generated session environment
.gradle-user-home/                 Gradle distributions, dependencies and npm cache
build/                             generated/rebuildable outputs and reports
```

Bootstrap reuses matching old archives from `build/` after verifying SHA-256.
Spotless excludes `.dev-tools/` so the downloaded Thrift Java sources are not
treated as project code or reformatted by `spotlessApply`.
It does not trust an archive merely because its filename matches. A corrupt cache
entry is rejected, not silently accepted. Move a corrupt file aside and retry.
An existing unmanaged/differently pinned tool directory is not deleted automatically;
move that directory aside when deliberately switching tool versions.

The generated profile sets:

| Environment | Purpose |
| --- | --- |
| `JAVA_HOME`, leading PATH directories | Selected JDK, Node/npm and compiler |
| `GRADLE_USER_HOME` | Checkout-local `.gradle-user-home`; avoids an unintended unwritable global cache |
| `THRIFT_EXECUTABLE` | Native compiler path for shell tools |
| `THRIFT_BROWSER_RUNTIME` | Official matching `lib/js/src/thrift.js` |
| `ORG_GRADLE_PROJECT_thriftExecutable` | Existing Gradle property, supplied without machine-specific source edits |
| `ORG_GRADLE_PROJECT_thriftBrowserRuntime` | Matching browser runtime Gradle property |
| `ORG_GRADLE_PROJECT_nodeExecutable` | Explicit Node for both web-ui and RPC tests |
| `PLAYWRIGHT_BROWSERS_PATH` | Project-local browser download directory |
| `npm_config_cache` | `.gradle-user-home/npm-cache` |

The environment scripts use their checkout location for project-owned paths.
After moving a prepared checkout, rerun bootstrap: `environment.json` also contains
absolute paths for its validation record. Do not commit that generated file.

## Build and test commands

Load the generated profile first. Unix uses `./gradlew` instead of `gradlew.bat`.
These commands are examples for a developer checkout, not the installed node.

```powershell
# Validate and format Java. These are root tasks, not per-module Spotless tasks.
.\gradlew.bat spotlessCheck verifyJavaFormatting
.\gradlew.bat spotlessApply

# Full ordinary build: compile, generate contracts/assets, unit tests and checks.
.\gradlew.bat build --no-daemon --console=plain

# Focused examples; choose checks relevant to your change.
.\gradlew.bat :jtoss:test
.\gradlew.bat :nodelist:test :jftn-bundle:test --tests '*Directory*'
.\gradlew.bat :web-ui:test :web-ui:frontendTest :web-ui:typecheckFrontend :web-ui:typecheckStorybook

# Coverage; container-backed coverage is an explicit separate choice.
.\gradlew.bat coverageStatistics
.\gradlew.bat coverageStatistics -PcoverageWithBinkd

# Portable no-JRE package; runtime bundles also use gradle/runtime-lock.json.
.\gradlew.bat :jftn-bundle:distZip
.\gradlew.bat :jftn-bundle:releaseDistributions
```

Gradle task options such as `--tests` apply to their associated Test task; do not
assume a trailing filter restricted every earlier task in a multi-task command.
Use explicit separate invocations if exact test selection matters.

Frontend assets and RPC generation:

```powershell
.\gradlew.bat :web-ui:prepareFrontendRpc
.\gradlew.bat :web-ui:buildFrontend
.\gradlew.bat :web-ui:storybook
```

The Storybook development server runs on `http://127.0.0.1:6006`. Its launcher uses
npm/Node and deliberately does not require Thrift just to show component stories.
The static build (`:web-ui:buildStorybook`) can have additional dependencies through
the project task graph. To view a downloaded artifact, serve it over HTTP:

```powershell
.\gradlew.bat :web-ui:previewStorybook "-PstorybookDirectory=C:/Downloads/storybook"
```

Use `:web-ui:previewStorybook` with its default directory for a locally built
catalogue. Do not double-click `index.html` under `file://`; module fetches do not
behave like HTTP. Do not stop another user's listener to claim port 6006.

Local Chromium tests (install browsers first):

```powershell
.\gradlew.bat :web-ui:playwrightComponentsTest :web-ui:playwrightTest
```

Reports include:

- Java tests: `<module>/build/reports/tests/<task>/index.html`.
- Coverage: `build/reports/coverage/unit/index.html`, or `all/` with binkd coverage.
- Storybook: `web-ui/build/storybook/`.
- Browser reports: `web-ui/build/reports/playwright/{components,application,setup}/`.
- VRT: `web-ui/build/reports/playwright/visual/` and `visual-results/`.
- Binkd: backend `build/reports/binkdDockerTest/` including protocol/container logs.
- HPT: `jtoss/build/reports/` and the `hptInteropTest` test report.

## Docker/Podman and interoperability

A container engine is a separately installed OS prerequisite. Ordinary Gradle
`build`/unit testing does not start binkd/HPT/VRT containers. First verify the engine
actually works, not only that a compatibility executable is installed:

```powershell
podman version
podman info
podman run --rm quay.io/podman/hello
docker version
docker info
```

On Windows, Testcontainers uses the Docker-compatible API. Configure its endpoint
for your actual Podman Desktop/machine connection; do not copy another machine's
socket, VM identity, private SSH keys or stale connection URI. Keep the engine
running and confirm that `docker info` reaches it before Java integration tests.
Some Podman configurations need explicit Ryuk support/disablement and a reachable
host address for the peer container. Resolve that against the actual installation;
do not disable cleanup or change global Podman settings blindly.

An earlier workstation setup failed with `crun: controller pids is not available`.
That error occurs even for `podman run hello`, below Java/Testcontainers. Repair
the Podman machine/WSL cgroup environment first; it is not evidence of a binkp bug.
Do not delete/recreate a VM with user data as an automatic bootstrap action.

Once the API is healthy:

```powershell
.\gradlew.bat :binkp-root:binkp-netty:binkdDockerTest :binkp-root:binkp-mina2:binkdDockerTest
docker build -t jftn-hpt-interop:local jtoss/src/integrationTest/resources/hpt
.\gradlew.bat :jtoss:hptInteropTest -Dhpt.image=jftn-hpt-interop:local
.\gradlew.bat :web-ui:playwrightVisualTest
```

BSO/HTick process-level tests also support direct Docker/Podman CLI orchestration,
without relying on Testcontainers API discovery on Windows. See the separate
[BSO/HTick runbook](husky-integration.md) for image building, PowerShell quoting,
diagnostics and the sealed CI runtime.

VRT defaults to Podman on Windows and Docker on Unix; override with
`VRT_CONTAINER_ENGINE` if needed. It explicitly runs Linux/amd64, including on ARM
hosts where emulation must work. It needs the static catalogue. Update snapshots
only for reviewed intentional changes:

```powershell
Set-Location web-ui/frontend
node --experimental-strip-types test/browser/run-visual.ts --update-snapshots=changed --grep 'affected-screen'
```

Use the pinned container to generate baselines. Never accept native Windows font
rendering as the Linux baseline. CI rejects snapshot-update requests.

## Offline recovery and cache transfer

The source/lock files are the durable description. Cache archives are optional
acceleration and must be stored outside Git. `--offline` prevents tool downloads;
it does not promise that Gradle, npm, browsers or containers already have every
dependency needed for a full build.

On an online prepared checkout:

1. Keep a backup of `.dev-tools/downloads/` and the exact Git revision/lock.
2. Optional: run bootstrap with frontend/browsers, then prepare the Gradle tasks
   and container images you plan to use offline.
3. Stop Gradle daemons (`gradlew --stop`) before archiving dependency caches.
4. Save `.gradle-user-home/caches`, `.gradle-user-home/wrapper` and the matching
   npm cache separately. Do not include daemon sockets/locks, workers or old probes
   as required inputs. A live cache copy can be inconsistent; regenerate it online
   rather than assuming it is a valid offline bundle.
5. Also save `.gradle/wrapper-bootstrap/gradle-wrapper.jar` from a successful wrapper
   invocation. A clean clone has no wrapper JAR: `--offline` reaches Gradle only
   after the external wrapper bootstrap has run. Restore this JAR at the same
   checkout-relative path; the Java launcher verifies `wrapperSha256Sum` again.
   Its adjacent properties are regenerated from the committed wrapper properties;
   do not restore `download.lock` or unrelated project `.gradle` state.
6. Save the Playwright browser cache for the same OS/architecture and Playwright
   package version. `node_modules` is not a portable cross-platform cache; run
   `npm ci` again.
7. Export required Docker/Podman images separately if an offline engine must run
   interoperability/VRT. Engine image storage is not in Gradle home.

Restore the matching tool archives, then:

```powershell
.\tools\bootstrap.ps1 -Offline -DownloadDirectory D:/Backups/jftn-tool-downloads
. .\.dev-tools\dev-env.ps1
.\tools\bootstrap.ps1 -Check
.\gradlew.bat build --offline --no-daemon --console=plain
```

```bash
bash tools/bootstrap.sh --offline --download-dir /backups/jftn-tool-downloads
source .dev-tools/dev-env.sh
./gradlew build --offline --no-daemon --console=plain
```

Windows offline bootstrap also needs the locked embedded Python ZIP in that
archive directory (or its existing prepared `.dev-tools/python-3.13.9`). A backup
made for one platform does not contain the other platforms' JDK/Node distributions
unless you explicitly collected them. The Unix native build still needs its host
compiler prerequisites even when the Thrift source archive is cached.

`--frontend --offline` uses npm's offline cache; it will fail if a package is absent.
`--browsers --offline` is not a download mode: restore the browser cache instead.
Gradle `--offline` likewise fails on missing dependencies. Container image layers
and native packages have their own offline requirements. Online tool downloads
are checked against the committed SHA-256, including when a shared archive cache
is used. Do not share private Gradle properties or credentials as a cache bundle.

## Recovering this workstation's older layout

Earlier work placed downloaded tools directly under ignored `build/`:

- `build/node-v24.22.0-win-x64/` and its ZIP.
- `build/python-3.13.9/` and `python-3.13.9-embed-amd64.zip`.
- `build/thrift-0.25.0.exe` and `thrift-0.25.0.tar.gz`.
- `build/thrift-browser-official/thrift-0.25.0/lib/js/src/thrift.js`.
- `build/playwright-browsers/`.

Bootstrap can reuse the matching original archives without a network fetch. It
does not depend on old extracted folders or Java helper probes. Old reports,
one-off `.java`/`.class` files and CircleCI experiments in `.gradle-user-home/`
are historical scratch data, not essential environment definitions.

For a one-time local browser-cache reuse after loading the new profile:

```powershell
$env:PLAYWRIGHT_BROWSERS_PATH = Join-Path $PWD 'build/playwright-browsers'
```

This only works when that cache has the browser revisions required by the current
Playwright package. Prefer the new default directory for a fresh workstation.
Do not delete the older layout just because the new one is available; no cleanup
of existing caches/user data is part of this bootstrap task.

## Common failures and what to check

| Symptom | Check/action |
| --- | --- |
| Cannot start process `thrift` | Load `dev-env`; check `ORG_GRADLE_PROJECT_thriftExecutable` and run the compiler's `-version` |
| Browser runtime missing | Use the matching official `thrift.js`; generated RPC bindings and npm's package are not interchangeable substitutes |
| Global npm/node not found | Load the profile; it supplies pinned Node/npm and the explicit Gradle Node property |
| Gradle tries an unwritable global cache | Set `GRADLE_USER_HOME` **before** invoking the wrapper, not only `-g` after wrapper startup |
| Tool archive hash mismatch | Preserve/move the suspect cache entry, obtain the official pinned artifact again; do not edit the hash to match arbitrary bytes |
| Tool directory uses an older pin | Move that specific project-owned tool directory aside and rerun bootstrap; no recursive wipe of the checkout |
| Java cache JAR AccessDenied in an agent sandbox | Distinguish sandbox/ACL restrictions from compilation errors; rerun the permitted read/build operation with the appropriate execution permission |
| Java class version mismatch after switching JDK | Confirm selected JDK and task inputs; rebuild affected generated/buildSrc outputs, preserving source and runtime data |
| Storybook `file://` fails | Serve the artifact over HTTP with `previewStorybook` |
| Browser executable missing | Install matching Playwright Chromium or restore the correct OS/version browser cache |
| Linux browser missing shared libraries | Install Playwright's OS dependencies on the supported distro; a downloaded browser alone is insufficient |
| Docker API unavailable | Fix the running engine/socket/VM before Testcontainers; a `docker` alias alone is insufficient |
| VRT changed on a different OS | Reproduce in the pinned Linux container and review actual differences |
| Download/network failure | Retry the pinned URL or restore a verified archive cache; do not silently switch versions |

On Windows PowerShell 5, avoid piping source containing Cyrillic through a native
Python stdin with implicit code pages. Use explicit UTF-8 file operations or the
patch tool; generated Java source should not have a UTF-8 BOM. PowerShell redirected
logs may be UTF-16. Do not diagnose mojibake from console rendering alone; verify
the actual file encoding. Documents/frontend strings must remain valid UTF-8.

## Running-node recovery is a separate task

The developer tool profile does not restore a live node. Its state can include
`jftn.conf`, active `conf.d`, customized scripts, SQL mail/account/catalog databases,
inbound/outbound, partial transfers, retained list bases and update journals,
`Tmp/scripts`, toss/delivery journals, JAM bridge state and received archives.
Coordinate a consistent backup/restore with a stopped node or the stores' backup
mechanisms. Do not copy live H2 files and call that a verified backup. Restore
related mail/receipt/state resources together to avoid lost work or duplicate sends.
Passwords, setup URLs/tokens and passkey/account data belong to that private backup,
not this repository. See [distribution/upgrade operations](distributions.md).

## Updating or extending the bootstrap

1. Update the project dependency/tool requirement and determine affected platforms.
2. Fetch official metadata; record exact archive URL and SHA-256 together in the lock.
3. Update the readable tool table, CI if relevant, and tests that check version agreement.
4. Run `python -m unittest discover -s tools/tests -p 'test_*.py'`.
5. Exercise bootstrap and `--check`; test offline reuse and a real Gradle/frontend
   task. For Unix extraction/compiler changes, record a real Unix verification.
6. Update this runbook, including which host/tool profile was verified.

The lock's URLs are fetched artifacts, not a `latest` resolver at bootstrap time.
SHA-256 values were collected from official Node/Apache/Adoptium metadata and the
Python artifact's published Sigstore message digest; bootstrap checks those fixed
digests, not the full vendor signing chains. Project tests/CI still need to run
after changing tools. Never commit the binary download to simplify a tool upgrade.
