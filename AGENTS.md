# JFtn repository instructions

## Recover context at the start of a new session

- Read `docs/decisions.md` for accepted architecture and working agreements.
- Use `docs/development-environment.md` for setup, commands, cache recovery and
  platform limitations. `TODO` tracks unfinished product work.
- Verify `git status --short` and recent commits before editing or staging.
- Treat those documents as explicit snapshots: current user instructions, source
  and tests take precedence over stale prose. Do not assume another chat's hidden
  state, credentials, running processes or absolute machine paths were restored.

## Implementation conventions

- Java: four-space indentation, 100-column target, explicit imports; never use
  wildcard imports, including temporary/helper source. Prefer short imported type
  names unless a collision requires qualification.
- Import groups: `org.fidonet`, third-party libraries, `java`/`javax`, static imports.
- Put method annotations such as `@Test` on separate lines. Keep original multiline
  `Created by` metadata. One statement per line; separate `try`/`finally` blocks.
- Use the root Spotless formatter/configuration. It supplies custom aligned long
  parameters, selector chains and string operands. Do not substitute generic
  google-java-format/import sorting or manually reformat unrelated files.
- Minimize nesting with guard clauses and focused methods; extract reusable test
  fixtures/helpers. Avoid `try -> loop -> try` and deeply nested conditionals when
  a meaningful method boundary makes the flow easier to follow.
- Handwritten browser application/tests use TypeScript and Vue. Generated Thrift
  JS/types are build outputs. UI languages are English (default) and Ukrainian;
  support both light and dark themes. Repository documentation is English.
- New generic FTN test data normally uses zone/net `1:500`; preserve intentional
  cross-zone/address-boundary fixtures rather than blindly rewriting addresses.

## Architecture invariants

- SQL is authoritative mail storage. JAM is a configurable optional bridge.
- Netty is the default transport; MINA2 is retained; MINA3 was removed.
- Thrift supports both TCP and HTTP/JSON. Keep transport lifecycle separate from
  service authorization/handlers. Edit contracts, never generated bindings.
- Binkp, PKT, robot and UI passwords are separate. Settings must not return stored
  passwords or log them. Empty password edits retain the previous secret.
- Node management is admin-only on the backend, not only hidden in the UI.
- UI writes the main configuration atomically. Plugin internals live in ordered
  `conf.d` files, which UI/startup must not overwrite when already present.
- External mail scripts own automation policy; parsers/validation stay in reusable
  Java modules. Directory imports must not create trusted links/routing rules.
- Preserve received originals by default. Receipt files, mail journals, retained
  list bases and runtime databases are operational state, not disposable caches.
- Distribution uses separate libraries/plugins/Groovy/scripts and platform
  launchers, not a fat JAR. No binary tool downloads/cache trees in Git; deliberate
  binary test fixtures and VRT baselines are exceptions.

## Environment and checks

- Use `tools/bootstrap.ps1` on Windows x64 or `bash tools/bootstrap.sh` on Unix,
  then load `.dev-tools/dev-env.ps1` / `.dev-tools/dev-env.sh` in the active shell.
- Download versions/SHA-256 are in `tools/development-tools.lock.json`; Gradle's
  wrapper and packaged JREs have their own locks. `.dev-tools/`, build outputs and
  `.gradle-user-home/` stay ignored. Use `GRADLE_USER_HOME` before wrapper startup.
- Java source target is 11, but the development/runtime tooling requires JDK 17+.
  The pinned development profile uses Temurin 17; an explicit system-JDK profile
  is supported. Do not assume global Node/npm/Thrift are installed.
- Relevant checks: root `spotlessCheck verifyJavaFormatting`; focused `:<module>:test`;
  `:web-ui:frontendTest`, `typecheckFrontend`, `typecheckStorybook` for browser work.
- Visible UI changes must include relevant Storybook states and VRT scenarios.
  Generate/review affected baselines in the pinned Linux container, then run a
  comparison without snapshot updates. Cover both themes/languages and desktop/
  mobile. Functional browser tests do not replace VRT; report any blocked visual
  verification explicitly. Do not refresh unrelated baselines to hide regressions.
- JUnit Jupiter only. Use temporary files/databases and meaningful behavior,
  failure/restart and authorization tests. Formatting-only changes do not require
  application or container suites. Broaden tests when unresolved risk/failures
  justify it, not to repeatedly rerun already passing checks without changes.
- Container suites are explicit: binkd for Netty/MINA2, HPT for the tosser, and
  pinned Linux Playwright for VRT. Verify the engine/API first; do not replace Linux
  baselines with native Windows screenshots. See the environment guide for tasks.
- Bootstrap unit tests: `python -m unittest discover -s tools/tests -p 'test_*.py'`.
- CI is GitHub Actions: build -> binkd/HPT -> UI -> VRT -> tagged release packaging
  and publishing. Preserve sealed revision/checksum verification across jobs.

## Collaboration and handoff

- Follow the user's requested scope. For sustained work, report concise progress,
  what is being checked and any concrete limitation while continuing useful work.
- When commits are requested, stage coherent changes/hunks as separate commits;
  use concise English past-tense messages (`Added ...`, `Fixed ...`). Do not push
  or publish without a request. Preserve unrelated/user work.
- Update relevant decisions/runbooks when behavior or recovery steps change.
  Track unfinished product work in `TODO`. Mark proposals and
  unverified platforms as such; distinguish test evidence from assumptions.
- Do not modify an installed/running node's private configuration, databases or
  mail as a side effect of development tests. Read-only diagnosis can inform the
  task; use isolated project/temp state for verification.
