JFtn v0.1
=========

### BUILD STATUS: 
[![CircleCI](https://circleci.com/gh/vkravets/Fido4Java.svg?style=svg)](https://circleci.com/gh/vkravets/Fido4Java)

### ABOUT
JFtn is a ftn-tosser written in Java. This is the fork from the origin http://sourceforge.net/projects/jftn/ project.
To use JFtn you need Java 11 or newer.
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

An optional Docker interoperability test exchanges files with an external binkd
peer using both JFtn client and server roles. It runs only through the explicit `:binkp-root:binkp-netty:binkdDockerTest`
task; see the [test plan, container lifecycle and diagnostics](binkp/binkp-netty/src/integrationTest/README.md).
Ordinary transfer and JFtn-requested NR were verified in both roles with binkd in Podman on Windows.

For now there are a few developers (see below list), but you can join for develompent anytime.
You can always get a fresh version at GitHub: github.com/vkravets/Fido4Java.git or git://github.com/vkravets/Fido4Java.git

JFtn team members:

* Vladimir Kravets -- (vova.kravets\<at\>gmail.com or 2:467/1313 AKA 2:467/70.113 )
* Dmitriy Tochansky -- (tochansky\<at\>gmail.com or 2:5030/1111) original author of JFtn

Feel free to contact to us!
