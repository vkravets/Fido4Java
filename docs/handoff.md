# Development handoff

Checkpoint date: **2026-10-17**, Europe/Kiev. Read this at the beginning of a new
project chat or on a new workstation, then check the actual source and Git state.
This is a resumable engineering checkpoint, not a promise of automatic chat memory.

## Start here in a new session

1. Open this repository as the workspace and read [AGENTS.md](../AGENTS.md).
2. Read this checkpoint and [decisions](decisions.md). Use the
   [environment runbook](development-environment.md) for setup/tests.
3. Run `git status --short` and `git log -8 --oneline`. Preserve any uncommitted work.
4. Verify the bootstrap/profile before running builds. Do not assume the old
   machine's absolute paths or globally installed Node/Thrift still exist.
5. Read the specific service guide/tests for the requested change. Consult [TODO](../TODO)
   for remaining work, rather than treating older chat proposals as implemented.
6. Give a short status/proposed action and continue the user's actual request.

Suggested first message in a new Codex workspace chat:

> Read AGENTS.md, docs/handoff.md, docs/decisions.md and the development environment
> runbook. Check Git status and the latest commits. Summarize the current checkpoint,
> verify the selected tool profile, and continue with the next task I specify.

In a chat without repository/file access, attach these text files or provide their
contents. Do not assume a new chat has access to earlier messages, installed tools,
running containers, private node configuration or local cache archives.

## Current work: preserving context and rebuilding the environment

The latest requested task is to create comprehensive persistent project context
and scripts so another developer can reconstruct the development environment.
The work introduces:

- Root `AGENTS.md`, the decisions record, this checkpoint and the environment guide.
- `tools/development-tools.lock.json` with fixed artifact URLs/SHA-256.
- `tools/bootstrap.ps1` for Windows x64 and `tools/bootstrap.sh` for Unix, sharing
  the standard-library Python engine in `tools/bootstrap.py`.
- Ignored `.dev-tools/`, generated environment scripts and an environment record.
- Bootstrap unit tests under `tools/tests`, included in the normal CI build job.

The environment/context task is complete, including a ready-to-copy new-session
prompt in the README's developer section. The user requested a local commit and
ended work for today; resume only when they return with the next task.
This checkpoint is included in the commit titled
`Added reproducible development bootstrap and project handoff documentation`.
Use Git to obtain its actual hash; no push was requested or performed.
Verification is recorded below. Linux/macOS native installation remains unverified.

## Recent committed work

These hashes identify useful checkpoints; verify them against the current branch.

| Commit | Result |
| --- | --- |
| `912aafa` | Configurable direct node/point directory imports from completed binkp inbound |
| `216a798` | INFO logging for binkp/mail processing in console and file |
| `1d74607` | Administrative web UI for links, PKT password modes and basic tosser settings |
| `bb56768` | RPC/settings persistence and backend tests for those mail settings |
| `178d225` | Receipt metadata excluded from bundle recognition; regression tests |
| `d01fa76` | Baseline images attached to successful VRT reports |

The backend and browser mail-settings changes were intentionally split. The
mixed Tosser.java working diff was staged by hunk so receipt recognition did not
become part of the logging commit. All those feature commits were local; no push
was performed as part of the work in this conversation.

## Implemented behavior relevant to the next developer

### Station, link and tosser settings

`rpc/src/main/thrift/settings.thrift` declares the new settings structures/methods.
`SettingsHandler` authorizes and delegates; `NodeConfigurationStore` owns atomic
writing/conflict detection; `MailConfigurationStore` owns mail-option validation
and serialization. `NodeSettings`, `MailSettings`, `MailLinkForm` and `TosserForm`
are the browser components. Password reads are redacted. Empty edits preserve
the stored password; PKT mode is INHERIT, SEPARATE or UNPROTECTED.

Adding/editing links works. Existing link removal/renaming is deliberately refused
until routes/subscriptions/queued mail are reviewed. The primary link stays first.
Incoming-only links can omit a host. Tosser UI manages interval, processed-file
removal, echo access enforcement and automatic echo-area creation. Updates require
restart; running connections retain the previous configuration. Point accounts
and the initial setup token cannot call these administrative mail-settings methods.

### Logging

The default `jftn-bundle/src/main/resources/log4j2.xml` routes INFO and higher to
the console and rolling `logs/jftn.log`, with full exception traces. Operational
logs now cover binkp session readiness, TCP lifetime, acknowledged transfers,
mail-service startup, inbound files and packet summaries. Idle scans/frame details
stay DEBUG. PKT password failures identify peers without showing passwords.
The common session event logging benefits both transports; Netty adds TCP details.

### Directory imports

The external `directory-import.groovy` script has two paths:

- Existing TIC `DirectoryFeed` callbacks after FileFix validates/stores a delivery.
- New explicit `DirectoryInboundFeed` rules, enabled by `DirectoryInboundEnabled`.
  They scan completed peer inboxes using `.received` names and run on the mail
  trigger/startup/periodic processing, without a TIC requirement.

The script owns sender/name/ordering/retry policy. Java `nodelist` owns parsing,
CRC validation, streaming plain/ZIP input, SQL source replacement and recoverable
base/diff publication. `DirectoryEdition` reads dated header metadata for ordering.
For a dated backlog, newest full publications are tried first; older full lists
do not roll the base back. Durable script receipts prevent unchanged files being
reapplied across scans/restarts. Failed inputs remain available and use retry
backoff. Headerless/custom-date feeds need explicit opt-out of date enforcement.

Direct mode is disabled by default and requires sender-specific rules in
`conf.d/43-directory-feed.conf`. The documentation contains a complete example for
the actual peer below. Existing operator scripts/configurations are not overwritten
by startup: deploy the updated external script as well as the matching plugin JAR.
No directory record is promoted to a trusted link/routing/password configuration.

## Real-data incident and unresolved operational work

The original workstation checked the installation under
`C:\Users\vovak\Downloads\jftn`, separately from the development checkout
`C:\Users\vovak\jftn`. These are observations, not portable default paths.
No installed-node files, configuration, database or credentials were changed.

Peer inbox: `mail/inbound/peers/2-467-70-0`, authenticated/configured peer `2:467/70`.
At inspection there were 75 editions each of NODELIST, REGION46 and PNT46REG,
covering publications from 2025 into October 2026. Those counts can change.

### PNT46REG CRC mismatch remains unresolved

All 75 inspected PNT46REG files failed the parser's CRC calculation. For
`PNT46REG.289` (2026-10-16), header CRC is **45257**, calculated canonical CRC is
**40818**. The payload uses LF-only lines; the parser canonicalizes line endings
as documented. Additional raw/canonical/EOF variants were checked during diagnosis;
the task did not modify or bypass checksum validation.

An isolated test catalog under ignored `build/directory-preview` successfully
imported NODELIST.Z89 (**1246 records**) and REGION46.Z89 (**61 records**).
PNT46REG.289 was rejected and did not publish a point source. The actual installed
catalog was not touched. `requireChecksum=false` allows a missing checksum but
does not bypass a present bad checksum. Obtain/verify a corrected publication or
discuss a separate explicit policy decision; do not describe this data as already
successfully imported by the installed node.

### Earlier mail quarantine conflict

An earlier retained `.SA0` ZIP contained 124 PKTs rejected for an incorrect PKT
password. Packet source was `2:467/70`, destination `2:467/70.113`; configured
local AKAs included `2:467/1313` and `2:467/70.113`. A differing existing
`mail/tmp/DF2C779E.bad` prevented safe quarantine replacement, leaving the bundle
retryable. Updating the password alone does not overwrite that `.bad` conflict.
Inspect the current runtime state before any recovery operation; do not delete
or overwrite rejected mail based solely on this historical snapshot.

The `.MO0.received` ZIP-END-header error was a separate bug: a metadata file was
misidentified as a bundle. Commit `178d225` fixes the complete-filename match.

## Verification checkpoints

Results refer to the source/profile at the time of each check, not a universal
claim that every suite or external machine has just been tested.

| Checkpoint | Recorded evidence |
| --- | --- |
| Mail-settings feature | Java settings/persistence tests, HTTP Thrift round trip, TypeScript checks, 27 frontend tests, 13 component browser tests and three application/setup browser scenarios passed |
| Logging feature | 83 common-binkp + 39 Netty + 40 jtoss + 85 bundle tests passed; isolated SLF4J/Log4j2 probe verified INFO and complete exception traces in both console/file, with DEBUG excluded |
| Direct directory import | 30 nodelist + 92 bundle tests passed (122 total), including seven new direct-import scenarios and two edition-metadata tests |
| Actual directory payloads | Isolated NODELIST/REGION46 import succeeded; PNT46REG CRC rejection recorded above |
| Bootstrap helper | Nine stdlib unit tests passed: cache/hash validation, safe archive extraction, idempotence, refusal to overwrite unmanaged tools, platform mapping, manifest agreement and environment generation |
| Pinned Windows tool installation | Temurin JDK 17.0.20.1+1, Node 24.22.0, Python 3.13.9 and Thrift 0.25.0 prepared locally; npm dependencies installed through the new entry point |
| Offline tool reuse and profile validation | `tools/bootstrap.ps1 -Offline` and `-Check` passed; `gradlew --version` confirmed Gradle 9.8.1 on the local Temurin JDK 17 |
| CI helper regression checks | Seven existing CI Python tests passed; the Bash launcher passed `bash -n` using Git Bash on Windows |
| Full pinned-profile Gradle verification | `build :web-ui:typecheckStorybook --no-daemon --max-workers=2 --warning-mode fail --console=plain` passed on Windows/JDK 17 in 17m 48s: 111 tasks, 93 executed and 18 up-to-date; ordinary Java/browser-adapter/frontend tests, formatting, TypeScript checks, generated contracts/assets and the no-JRE ZIP were verified |

Linux/macOS native tool installation was not exercised. Playwright browser and
container interoperability/VRT suites were not rerun for this environment task;
they retain their separate prerequisites and commands. Do not turn Windows/unit
test results into a claim of native cross-platform or container validation.

Useful ignored evidence locations on the original checkout:

- `build/mail-settings-final.log` — settings Java/frontend/Storybook/browser checks.
- `build/info-logging-tests.log` — logging-related module tests.
- `build/logging-smoke/` — isolated console/file log output and helper.
- `build/directory-inbound-verified.log` — full directory/bundle checks plus valid real sources.
- `build/directory-inbound-final.log` — earlier run exposing the real PNT CRC failure.
- `build/development-bootstrap.log` — initial pinned tool preparation.
- `build/development-profile-final.log` — Gradle build with the pinned JDK/Node/Thrift profile.

The initial `development-profile-build.log` run was stopped after discovering that
Spotless included downloaded Thrift Java sources. `gradle/formatting.gradle` now
excludes `.dev-tools/`; the `final` log is the subsequent verification, not that
deliberately interrupted run.

These logs/probes are disposable evidence, not inputs required to reconstruct the
project. They are not committed or automatically transferred to another machine.

## Remaining work and deliberate limits

Use [TODO](../TODO) as the maintained work list. Key unfinished areas include:

- Browser netmail/echomail reader, composition and drafts; navigation is reserved.
- Point-scoped mailbox APIs/permissions; existing general UiService mail methods
  remain administrator-only despite approved AKA claims.
- RPC/UI netmail routing-rule editing and safe link removal/renaming.
- Durable automatic poll retries/backoff and incoming-session lifecycle tracking.
- Explicit administration of prepared outbound deliveries.
- Retention/cleanup policy for archives, receipt histories and journals.
- Additional disk/close/fsync and crash-boundary fault injection.
- Actual PNT46REG input problem, if the user chooses to pursue it.

Storybook includes the new mail settings forms with component interaction tests.
The existing VRT screen list still covers its original eight screens across
themes/locales/viewports (64 baselines); it does not yet add dedicated snapshots
for every new mail form. Do not claim 100% visual or branch coverage. Generate
fresh coverage reports before reporting current percentages.

No next product feature beyond the current environment/context task has been
selected. Do not resume an old proposal as an automatically authorized new task.

## How to update this checkpoint

At a meaningful task boundary, record the resulting behavior, relevant commit(s),
tests actually run, remaining failures and a concrete next step. Keep private
credentials/setup tokens out. If the user requests a pause, leave a clear status
without claiming incomplete work is finished. Before a new chat resumes, compare
this snapshot with Git and the implementation; update rather than accumulating
contradictory layers of old plans.
