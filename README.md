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

The BinkP implementation advertises version 1.0 and supports the NR extension.
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

Building requires JDK 17 or newer. The included Gradle 9.1 Wrapper supports
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

The RPC module requires the Apache Thrift 0.14.2 compiler on `PATH`.
Run the full build, including tests:

```powershell
.\gradlew.bat build
```

If Thrift is not on `PATH`, specify its executable explicitly:

```powershell
.\gradlew.bat build "-PthriftExecutable=C:/tools/thrift-0.14.2.exe"
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
