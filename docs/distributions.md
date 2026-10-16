# Standalone distributions

JFtn uses an ordinary classpath distribution, not a fat JAR. Run:

```sh
./gradlew :jftn-bundle:distZip
./gradlew :jftn-bundle:releaseDistributions
```

The first task builds the portable package requiring installed Java 17+. The second
also downloads the locked Temurin OpenJDK JRE 21 LTS archives and produces Linux x64
and Windows x64 packages. Downloads are SHA-256 checked before use. URLs, versions
and hashes are recorded in `gradle/runtime-lock.json`. Runtime binaries are build
outputs and are not stored in Git. Vendor licenses and notices remain in `jre/`.
Other platforms can use the no-JRE ZIP with a matching installed Java runtime.

ZIPs are in `jftn-bundle/build/distributions`. Use `-PreleaseVersion=1.2.3` to set the
archive and JAR version; the default is `1.0-SNAPSHOT`.

```text
jftn-<version>/
  jftn.cmd                Windows command prompt launcher
  jftn.sh                 Linux/macOS launcher
  jftn.ps1                PowerShell launcher
  lib/                    Application, shared libraries and browser UI JARs
    groovy/               Groovy runtime JARs
  plugins/                Database, scheduler, Netty, nodelist and JAM bridge JARs
  scripts/mail-scripts/   Editable AreaFix, FileFix and directory import scripts
  jre/                    Only present in a platform-specific runtime package
  jftn.conf.example       Manual configuration template, never activated automatically
  database/ logs/ lists/ fileecho/
  mail/inbound/ mail/outbound/ mail/tmp/ mail/echo/
  docs/ LICENSE README.txt
```

Launchers set the working directory to the installation root and load `scripts/`,
`lib/*`, `lib/groovy/*` and `plugins/*` as the classpath. They prefer the bundled
JRE, then `JAVA_HOME`, then Java on `PATH`. PowerShell uses the platform's classpath
separator and works on Windows and Unix. Pass `--version` for a version-only check.
The plain application JAR is not self-contained; use a launcher.

Plugins use `ServiceLoader` on this shared classpath; restart to load changed JARs.
There is no hot reload or dependency isolation. Database, scheduler and Netty are
required by the current runner. Nodelist is used by setup/settings lookup. The JAM
bridge can be disabled through its configuration. Do not remove a required module
or its shared dependencies just because its JAR is under `plugins/`.

Mail script resources are excluded from the application JAR and supplied externally.
Existing `MailScript=classpath:mail-scripts/areafix.groovy` settings therefore continue
to work and read the editable file under `scripts/mail-scripts/`.

On first launch, missing working directories are created and the private setup URL
is printed. Completing the wizard creates the live configuration and initial admin.
Configured inbound/outbound/temp/echo paths are created before plugins start on
subsequent launches too. Existing data is never replaced. A regular file occupying
a required directory path is an error, not a reason to overwrite it.

For upgrades, unpack into a new directory and deliberately move/copy your existing
configuration and data after making a backup. ZIP packaging does not migrate or
replace a running installation. See [browser setup](web-ui.md) for account setup.

## CircleCI and GitHub Releases

The main `build` job compiles/tests the application and seals the no-JRE ZIP with
the source revision and SHA-256 checksums. The separate `package-distributions`
job requires `build` and creates the runtime packages from that ZIP using
`-PdistributionInput=<zip>`. No application Java, Thrift or Vue sources are
recompiled in the packaging job. OpenJDK downloads are cached by the runtime lock
checksum; restored archives are verified before use.

Packaging also starts the Linux ZIP using its own launcher and bundled JRE, checks
the embedded browser page and first-run RPC, and verifies missing directories were
recreated. All three ZIPs, `runtime-lock.json` and `SHA256SUMS` are saved as CircleCI
artifacts. CircleCI builds branches only; tag releases run in GitHub Actions.

## GitHub Actions releases

The `.github/workflows/release.yml` workflow starts on version tag pushes. Its
build job validates `vMAJOR.MINOR.PATCH` (optionally with a prerelease suffix),
compiles/tests the application, and seals the compiled integration runtimes and
no-JRE ZIP. Artifacts are passed between jobs in the same workflow run. A tar
preserves the compiled snapshot's file permissions and hidden resources.

```mermaid
flowchart LR
    tag[Push version tag] --> build[GitHub Actions build]
    build --> package[Package and smoke test ZIPs]
    build --> binkd[binkd integration]
    build --> hpt[HPT integration]
    package --> publish[Publish GitHub Release]
    binkd --> publish
    hpt --> publish
```

Packaging and both integrations require the main build. They verify checksums
and the source revision and use the compiled snapshot without rebuilding Java,
Thrift or Vue sources. The packaging job reuses the same script as CircleCI.
Thrift and locked OpenJDK downloads are cached. Intermediate workflow artifacts
and test reports are retained for 15 days; published release assets are separate
from these temporary artifacts.

No personal access token, custom GitHub App or CircleCI release context is needed.
The publication job uses the automatic `GITHUB_TOKEN` with `contents: write`;
all other jobs have only `contents: read`. Checkout does not persist credentials.
Enable GitHub Actions for this repository and allow the official `actions/*`
actions if organization policy restricts them. Organization policy must also
allow the workflow's requested token permissions.

Push a tag on the commit containing this workflow, for example:

```sh
git tag v1.2.3
git push origin v1.2.3
```

Branch builds do not publish releases. Suffix tags such as `v1.2.3-rc.1` become
prereleases. GitHub tag filters are globs; the build script performs the strict
version validation before any build or publication. Re-running a failed workflow
uses the same tag; concurrency serializes runs for that tag.

Publication requires successful packaging and both binkd/HPT integrations. The
publisher verifies hashes, ZIP names and that the remote GitHub tag resolves to
the compiled revision, including annotated tags. It creates a draft first,
uploads and verifies every asset, then publishes it with generated release notes.
An interrupted upload can resume its own draft. Conflicting assets are never
overwritten; a conflicting release requires manual resolution.

CircleCI's former `github-release` job and context reference have been removed.
An existing release context/token can be removed from CircleCI if nothing else
uses it. This change does not publish anything until the workflow is pushed and
a matching tag is pushed.
