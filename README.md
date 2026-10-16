JFtn v0.1
=========

### BUILD STATUS:
[![CircleCI](https://circleci.com/gh/vkravets/Fido4Java.svg?style=svg)](https://circleci.com/gh/vkravets/Fido4Java)

### ABOUT
JFtn is a ftn-tosser written in Java. This is the fork from the origin http://sourceforge.net/projects/jftn/ project.
To use JFtn you need Java 11 or newer.
The application bundle uses Netty as its default BinkP transport.
`JFtnRunner` reads connections and local AKAs from the `Link` entries in `jftn.conf`.
Station metadata uses `SysOp` and the optional `System`, `Location` and `NDL` keys
(defaults: `JFtn`, an empty location and `BINKP`).
Feel free to report bugs and request for features.
Its very simple for now and can be used only for read-only system. It can toss netmail, have bugs,
and still under develompent.

### FEATURES
Can store echomail in jam-base.
Support several echo-links.
Store areas configuration in popular fidoconfig format. So you can use it with your favorite message editor.

### TODO

### INSTALL
To install simply copy the file jftn.jar to the desired location. Edit jftn.conf-dist to your liking and
save it in the same directory called jftn.conf.

### USAGE
Starting from the command line. The first parameter must be action. Action can be:

    help - print help for possible command-line options
    tosss - start tossing mail in inbound directory
    pull - start pulling the node via binkp.

### DEVELOPERS
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
alongside `docs/binkp/binkp11.rus.txt`. ND/NDA, compression, CRC and a FREQ service
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
The application does not convert old database files automatically.

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
light/dark themes and account security settings. It is served by the same JVM as
HTTP RPC; Node.js 22.12+ and npm are needed only to build and test it.

The RPC module requires the Apache Thrift 0.25.0 compiler on `PATH`.
Run the full build, including tests:

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

Coverage reports are reproducible through `./gradlew coverageStatistics` (or
`.\gradlew.bat coverageStatistics` on Windows). Add `-PcoverageWithBinkd` to run
both transports' Docker interoperability tests and include their coverage.
Specify `-PthriftExecutable=/path/to/thrift` when needed. HTML and XML reports
are written to `build/reports/coverage/unit` / `unit.xml`, or `all` / `all.xml`
with the Docker option. Generated Thrift sources are excluded; handwritten
application code is included. See [coverage scenarios and remaining gaps](docs/test-coverage.md).

For now there are a few developers (see below list), but you can join for develompent anytime.
You can always get a fresh version at GitHub: github.com/vkravets/Fido4Java.git or git://github.com/vkravets/Fido4Java.git

JFtn team members:

* Vladimir Kravets -- (vova.kravets\<at\>gmail.com or 2:467/1313 AKA 2:467/70.113 )
* Dmitriy Tochansky -- (tochansky\<at\>gmail.com or 2:5030/1111) original author of JFtn

Feel free to contact to us!
