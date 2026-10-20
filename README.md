JFtn
====

### BUILD STATUS:
[![CI](https://github.com/vkravets/Fido4Java/actions/workflows/ci.yml/badge.svg)](https://github.com/vkravets/Fido4Java/actions/workflows/ci.yml)

### ABOUT
JFtn is a Java FTN mail node and tosser, derived from the original
[JFtn project](https://sourceforge.net/projects/jftn/). It is under active development.
The standalone distribution requires Java 17+ or a package with a bundled JRE.
The application bundle uses Netty as its default BinkP transport.
`JFtnRunner` reads connections and local AKAs from the `Link` entries in `jftn.conf`.
Station metadata uses `SysOp` and the optional `System`, `Location` and `NDL` keys
(defaults: `JFtn`, an empty location and `BINKP`).
Report bugs and feature requests through the repository's issue tracker.

### FEATURES
- Full/Headless with SQL, Legacy with JAM/MSG mailboxes, and SQL-free Transit profiles.
- Netmail and echomail routing, subscriptions and durable outbound queues.
- Binkp 1.0/1.1, NR and resumable transfers; Netty by default, with MINA2 available.
- Optional JAM editor views and historical archive import, checked against HPT.
- Scripted AreaFix, FileFix, TIC distribution and node/point directory imports.
- Embedded Vue/TypeScript UI with English/Ukrainian, light/dark themes, password
  and passkey login, point registration, node settings and outgoing connection logs.
- Optional RPC/Web UI service plugins, controlled by installed JARs and conf.d.
- Thrift RPC over framed TCP and HTTP for administration and mail APIs.
- Node/point directory browsing and FTN address suggestions from imported lists.

The browser supports netmail and threaded echomail reading, composition, replies,
forwarding and drafts for permitted account AKAs, plus administrator routing controls.
Its mail workspace includes resizable previews, a plain-text FIDO visual editor,
personal themes and templates.
See [remaining work](TODO) and the limitations in each service guide.

### INSTALL
Download and unpack a [release](https://github.com/vkravets/Fido4Java/releases).
Packages contain separate application libraries, plugins and editable scripts.
Use `jftn.cmd` on Windows, `./jftn.ps1` in PowerShell or `./jftn.sh` on Linux/macOS.
Full opens a first-run setup wizard through the private URL printed in the console.
For Headless, Legacy and Transit, configure the supplied `jftn.conf.example` before
starting. Missing working directories are created.
See [distribution layout, upgrades and releases](docs/distributions.md).

### USAGE
Start a launcher without arguments to run the node. `--version` prints its version.
Offline [robot rule validation, import and export](docs/robot-access.md) are also
available. For operation without UI/RPC, create `jftn.conf` from the supplied
profile example; see [runtime profiles](docs/transit-profiles.md).

### DOCUMENTATION

- [Developer environment, bootstrap and offline recovery](docs/development-environment.md)
- [Architecture decisions and working agreements](docs/decisions.md)
- [Browser setup, settings and UI testing](docs/web-ui.md)
- [Mail layouts, FIDO editor, themes and templates](docs/mail-workspace.md)
- [Mail processing, routing, JAM and HPT interoperability](docs/tosser.md)
- [External BSO delivery, attachments and file requests](docs/bso.md)
- [Real BSO/HTick container integration tests](docs/husky-integration.md)
- [Full, Headless, Legacy and Transit profiles](docs/transit-profiles.md)
- [RPC contracts, transports and authentication](docs/rpc-ui.md)
- [Plugin configuration and upgrade rules](docs/plugin-configuration.md)
- [AreaFix, FileFix, TIC and directory feed scripts](docs/mail-scripts.md)
- [Mail robot access and offline administration](docs/robot-access.md)
- [Node and point directory parsing](docs/nodelist.md)
- [Binkp references](docs/binkp/README.md)
- [Test coverage and its limitations](docs/test-coverage.md)

### DEVELOPERS

When starting a new development session, read [AGENTS.md](AGENTS.md) and
[architecture decisions](docs/decisions.md),
then check `git status --short` and `git log -8 --oneline`.
For a new coding-agent chat with this repository open, use:

> Read AGENTS.md and docs/decisions.md. Check Git status, TODO and the
> latest commits, restore the project context, and summarize the current state
> before continuing with the next task I specify.

For a clean Windows checkout, run `tools/bootstrap.ps1 -Frontend`, then load
`. ./.dev-tools/dev-env.ps1`. On Linux/macOS, use `bash tools/bootstrap.sh --frontend`
and `source .dev-tools/dev-env.sh` after installing the documented host prerequisites.
The [environment runbook](docs/development-environment.md) covers pinned downloads,
checksums, offline cache transfer and browser/container tests.

All tests use JUnit Jupiter 5.14.4 directly through Gradle's JUnit Platform
support. The shared BOM, Jupiter dependencies and platform launcher are declared
in the root `build.gradle` and inherited by integration tests. JUnit 4, Vintage
and the migration-support module are not used. Use `@TempDir` for temporary
storage, Jupiter lifecycle annotations and `assertThrows` for expected failures.
Diagnostic assertion messages are the final argument. Parameterized transport
scenarios use Jupiter's `@ParameterizedClass` with `@MethodSource`.

Java sources follow Google Java formatting with four-space indentation
and a 100-character line limit. `.editorconfig` defines the editor settings.
Formatting is managed by Gradle through Spotless 8.10.4 and Eclipse JDT 4.34
(compatible with the JDK 17 CI image). The shared configuration is in
`gradle/formatting.gradle` and `gradle/java-formatter.xml`; an AST-based step in
`buildSrc` supplies the custom string-expression alignment.

```powershell
.\gradlew.bat spotlessApply
.\gradlew.bat spotlessCheck verifyJavaFormatting
```

On Linux/macOS, use `./gradlew` with the same tasks. These commands format or
check Java sources without running application tests or generating Thrift code.
The root `check`/`build` lifecycle also checks formatting, including the style
fixtures. Main, test, shared integration-test, build helper and wrapper sources
are included; generated sources and build directories are excluded.

Keep imports in separate groups: project (`org.fidonet`), third-party libraries,
Java platform (`java` and `javax`), then static imports. Sort each group
alphabetically and separate groups with a blank line. Preserve this order
when formatting; do not use automatic Google import sorting.
Keep the original multiline `Created by` metadata comments.
Use explicit imports and place method annotations such as `@Test` on their own lines.
For declarations exceeding 100 characters, keep the first parameter beside the
method or constructor name, put each remaining parameter on its own line, and
align it with the first parameter.
For long chained method calls, keep the first call beside the receiver and put
each subsequent call on its own line, aligning its dot with the first call's dot.
For multiline string concatenations in method and constructor arguments, keep
the first operand beside the opening parenthesis when it fits within 100 characters.
Align continuation `+` operators with the start of that argument and align chained
calls within each operand by their dots. Use the same operand alignment for
multiline concatenations in assignments and return statements.
Spotless rejects wildcard imports and does not resolve or rewrite ambiguous
fully qualified types. Expression alignment preserves comments and text blocks;
when the requested alignment cannot fit within 100 characters, it retains the
Eclipse layout. Common test-code extraction remains a manual refactoring task.

The BinkP implementation advertises version 1.1 and falls back to 1.0 when the
peer does not identify a newer protocol before authentication completes.
`StationConfig.setBinkp11Enabled(false)` explicitly selects 1.0. Disabling NR
with `setNREnabled(false)` also advertises 1.0, preserving the local NR policy
without claiming the mandatory NR support of 1.1.
Version 1.1 supports multiple file batches in the same authenticated connection,
ending after an empty exchange of `M_EOB`. Authentication, NR, and the CRYPT
cipher state survive batch boundaries. `SessionContext.setOutboundLoader(loader)`
installs a synchronous callback on the sending worker before each batch; it
receives the actual session context and should enqueue only new eligible files.
Skipped/deferred files are not automatically retried in subsequent batches.
The callback is also copied to accepted server sessions. Applications producing
responses asynchronously must await their own work inside the loader before
it returns; the protocol does not wait indefinitely for future files.

The implementation follows the batch completion and command activity logic in
[binkd protocol.c, revision 5a84e85](https://github.com/pgul/binkd/blob/5a84e8596a96ca8b1f4ec69d9a608cd264aa06ba/protocol.c#L3113),
alongside the [English protocol references](docs/binkp/README.md).
ND/NDA, compression, CRC and a FREQ service
are separate extensions and are not advertised by this change.

NR policy is identical for client and server sessions. `setNREnabled(false)`
disables NR: no `OPT NR` is advertised and peer NR requests do not change
outbound transfers. NR is allowed by default. `setNRRequested(true)` requests
NR for incoming files; `setNRMode(boolean)` remains an alias for this request.
When NR is allowed, received `OPT NR` requests control outbound transfers,
independently of whether we sent `OPT NR`. Configure these flags before opening
a session. Partial incoming files
are stored under `inbound/.binkp-partials`, separated by peer and file metadata,
and reused after reconnecting. Configure this directory with
`StationConfig.setPartialDirectory(Path)`; `null` selects in-memory reception.
Completed receive streams remain readable through the byte-array event API.
The application should manage retention of the files in the configured directory.

Building requires JDK 17 or newer. The included Gradle 9.8.1 distribution supports
JDK 25, so a separate Gradle installation is not required. All modules compile
with `--release 11`, targeting Java 11 bytecode and APIs. The scripting engine
uses Groovy 5.1.3.

The Wrapper JAR is not committed. On first launch, `gradlew` / `gradlew.bat`
download it from the official Gradle repository at the pinned release tag and
verify SHA-256 before execution. It is cached in `.gradle/wrapper-bootstrap`,
so subsequent launches can work offline. Both launchers use the JDK to run
`gradle/wrapper/DownloadWrapper.java`; no curl or PowerShell is required.
When upgrading the Wrapper, update `wrapperUrl`, `wrapperSha256Sum`,
`distributionUrl` and `distributionSha256Sum` in `gradle-wrapper.properties`.
Binary test fixtures remain in version control.

The Gradle 9.8.1 distribution is launched using the official 9.8.0 Wrapper JAR.
The JAR in Gradle's 9.8.1 source tag is also the 9.8.0 binary; both checksums
are pinned against the [official release checksums](https://gradle.org/release-checksums/).

The bundled database driver is H2 2.5.252. Existing H2 1.x database files cannot
be opened directly with H2 2.x. Stop JFtn and back up the complete database
directory before upgrading. Export with the **old** driver, then import into a
new database path with the new driver, using the same database credentials:

```sh
java -cp h2-1.4.192.jar org.h2.tools.Script -url jdbc:h2:./database/jftn10 -script backup.sql
java -cp h2-2.5.252.jar org.h2.tools.RunScript -url jdbc:h2:./database/jftn-new -script backup.sql -options FROM_1X
```

Keep the original files and SQL backup until the imported mail and subscriptions
have been checked. The default database path is `./database/jftn10`: after
validation, use the new database files at that path while JFtn is stopped.
See the [H2 migration guide](https://h2database.github.io/html/migration-to-v2.html).
The application does not convert old database files automatically. H2's export/import
only changes the database engine format; it does not migrate an old JFtn schema.
The current application schema is the baseline, so incompatible older schemas
require a separately planned data conversion before use.

The Runner now connects received binkp files to the tosser and sends queued
netmail and subscribed echomail. See [mail processing, configuration and limitations](docs/tosser.md).
The optional [node and point directory plugin](docs/nodelist.md) imports FTSC
nodelists and pointlists from local files or ZIP archives into its own catalog.
Bundled [Groovy mail scripts](docs/mail-scripts.md) provide AreaFix, FileFix,
TIC file distribution and automatic directory imports with NODEDIFF support.
The optional authenticated [UI RPC service](docs/rpc-ui.md) exposes mail reading,
drafts and submission, echo subscriptions, manual polls, queues and node logs.
RPC accounts are stored separately with hashed passwords and optional WebAuthn
passkeys as an additional login method.

The embedded [Vue/TypeScript browser interface](docs/web-ui.md) provides initial
node setup, sign-in, moderated point registration, approved AKA selection,
light/dark themes, English/Ukrainian, robot access rules, outgoing connection
queues/logs and account security settings. It is served by the same JVM as
HTTP RPC; Node.js 22.12+ and npm are needed only to build and test it.

A clean full build requires JDK 17+, Node.js 22.12+, npm and the Apache Thrift
0.25.0 compiler. Supply its matching `lib/js/src/thrift.js` browser runtime as well;
the compiler executable alone is insufficient. For example, in PowerShell:

```powershell
$env:THRIFT_BROWSER_RUNTIME = 'C:/tools/thrift-0.25.0/lib/js/src/thrift.js'
```

On Linux/macOS, export the same variable with an absolute local path, or pass
`-PthriftBrowserRuntime=/absolute/path/to/thrift.js` on either platform.
Run the full build, including ordinary tests:

```powershell
.\gradlew.bat build
```

If Thrift is not on `PATH`, specify its executable explicitly:

```powershell
.\gradlew.bat build "-PthriftExecutable=C:/tools/thrift-0.25.0.exe"
```

On Linux/macOS, use `./gradlew build` with the same optional
`-PthriftExecutable=/path/to/thrift` property.

Optional Docker interoperability tests for Netty and MINA2 exchange files with an external binkd
peer using both JFtn client and server roles. They run only through each backend's explicit
`binkdDockerTest` task; see the [test plan, container lifecycle and diagnostics](binkp/src/integrationTestShared/README.md).
Ordinary transfer and JFtn-requested NR were verified in both roles with binkd in Podman on Windows.
HPT and Playwright also have explicit tasks; see [HPT checks](docs/tosser.md#real-hpt-interoperability-tests)
and [UI tests](docs/web-ui.md#browser-tests). Ordinary `build` does not run these
container/browser suites. CI runs them afterwards and gates release packaging.

Coverage reports are reproducible through `./gradlew coverageStatistics` (or
`.\gradlew.bat coverageStatistics` on Windows). Add `-PcoverageWithBinkd` to run
both transports' Docker interoperability tests and include their coverage.
Specify `-PthriftExecutable=/path/to/thrift` when needed. HTML and XML reports
are written to `build/reports/coverage/unit` / `unit.xml`, or `all` / `all.xml`
with the Docker option. Generated Thrift sources are excluded; handwritten
application code is included. See [coverage scenarios and remaining gaps](docs/test-coverage.md).

Contributions are welcome. Source code is available at
[vkravets/Fido4Java](https://github.com/vkravets/Fido4Java).

JFtn team members:

* Vladimir Kravets -- (vova.kravets\<at\>gmail.com or 2:467/1313 AKA 2:467/70.113 )
* Dmitriy Tochansky -- (tochansky\<at\>gmail.com or 2:5030/1111) original author of JFtn

Feel free to contact us.
