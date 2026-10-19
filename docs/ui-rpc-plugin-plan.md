# UI and RPC plugin refactoring plan

Recorded: 2026-10-19.
Status: all seven implementation steps completed and locally verified on 2026-10-19.
Step 1 completed: explicit service context, preparation/start/close lifecycle,
dependency ordering and failure cleanup. Plugin-manager tests passed.
Step 2 completed: node-runtime contains shared node services; rpc-node owns the
RPC plugin, handlers, transports and server configuration previously located in
web-ui. RPC-plugin tests passed without browser classes; existing authenticated
RPC and live-configuration tests passed. At this intermediate step, the old
runner startup remained until step 5.
Step 3 completed: WebUiPlugin depends on RPC; WebUiEnabled controls the browser
HTTP routes independently of RPC. Configuration lives in 95-web-ui.conf.
RPC backend tests and the focused Web UI plugin tests passed.
Step 4 completed: preparation invokes initial setup through WebUiPlugin, skips
disabled UI, validates HTTP availability and preserves operator fragments.
Focused setup/configuration tests passed.
Step 5 completed: the runner discovers plugins once, prepares them before
storage, filters disabled services, and starts/closes loaded service contexts.
FullProfileServices/ProfileServices were removed. UI is no longer hardcoded to
Full; RPC validates its SQL capability requirement. Node bootstrap, config,
RPC capability and runner startup tests passed.
Step 6 completed: RPC/Web UI are shipped under plugins/, with no compile-time
RPC/UI implementation dependency in the runner. All eight ZIPs passed CRC and
layout inspection; reduced-profile PKT/TIC/startup tests and the fresh-JVM
packaged-plugin probe passed.
Step 7 completed: server-side tests moved into rpc-node and pass with no browser
implementation/setup on the classpath. Installed JVMs verify eight service/package
combinations, PKT import with optional services removed, framed TCP and HTTP/JSON
authentication/authorization, missing dependencies, manual configuration without
UI, first-run setup ordering, rejection without SQL, and rollback/restart after
an occupied HTTP port. Runner shutdown closes a plugin-owned real socket.
The full focused backend/node regression, formatting checks and all eight ZIP
builds passed (build/plugins-final-verification.log; ignored local evidence).
No Playwright/VRT run was needed: browser application/layout code was unchanged.
HTTP asset safety and generated-client RPC checks ran as backend checks.

## Intent and current checkpoint

Restore the original extension model: a JAR in `plugins/` makes a capability
available; its configuration can disable it without removing the JAR. A missing
optional plugin must not prevent ordinary mail processing. Distribution profiles
provide useful default plugin selections rather than replacing plugin discovery.

At checkpoint `51964ef5` (MSG netmail storage), UI/RPC are still libraries in
`lib/`. `JFtnRunner` calls `ProfileServices.forProfile()`, which loads
`FullProfileServices` for Full; that implementation invokes `InitialSetup` and
`NodeRpc` directly. `RuntimeProfile.hasUi()` permits only Full, and profile
validation rejects enabled RPC elsewhere. `RpcEnabled=false` disables listeners,
but removing UI/RPC JARs from Full is not a supported optional-plugin operation.
These are current limitations, not the target architecture.

## Implementation sequence

Each numbered step gets a separate coherent commit, with its applicable tests
and documentation. Use concise English past-tense commit messages. Report the
current step and concrete progress in chat while continuing authorized work.

1. **Add a common lifecycle API for service plugins.**
   Supply configuration, mail storage, mail runtime and polling through defined
   interfaces. Separate preparation, startup and shutdown. Account for startup
   ordering and cleanup after partial failure. The runner must not depend on
   concrete UI/RPC implementations.

2. **Make RPC an independently discovered plugin.**
   Move service/handler assembly, authentication, sessions and TCP/HTTP transport
   startup behind the plugin boundary. Retain file-only internals in
   `conf.d/90-rpc.conf`. No RPC JAR means no RPC capability; configuration can
   disable an installed plugin. Ordinary mail processing must still start.

3. **Make Web UI a separate plugin depending on RPC.**
   Own browser resources and UI HTTP routes, registration, settings and passkey
   integration in that plugin. RPC must work without Web UI. Installing Web UI
   without its required RPC dependency must produce a clear dependency error.
   Decide concrete API/package boundaries from the current source during this
   step; do not imply that a proposed split already exists.

4. **Move initial setup into the UI plugin.**
   Provide preparation before opening mail storage or starting mail services.
   With missing configuration and available/enabled UI, run the setup wizard.
   Without UI, explain how to prepare the configuration file. Preserve existing
   `conf.d`, credentials and operational data. Resolve bootstrap discovery before
   relying on a fully constructed runtime or a configuration that does not yet
   exist.

5. **Remove direct profile-driven UI/RPC startup from the runner.**
   Replace `FullProfileServices` and its concrete-class loading path with plugin
   discovery/lifecycle operations. The runner coordinates configuration,
   discovery, preparation, startup and shutdown. Validate storage requirements
   and service capabilities explicitly; SQL-dependent plugins must not create
   a dummy database in SQL-free installations. Adjust profile validation so it
   does not merely hardcode UI/RPC availability by profile name.

6. **Update all four distribution compositions.**
   Retain eight ZIPs: Full, Headless, Legacy and Transit, each with/without JRE.
   Put the actual RPC and UI plugin JARs in `plugins/`; retain shared dependencies
   in `lib/`, and the separate Groovy/scripts layout. Full includes both plugins
   by default. Other profiles retain their agreed default capabilities. Ensure
   missing optional JARs do not trigger class loading from the runner. Preserve
   sealed revision/checksum verification across CI packaging jobs.

7. **Verify installed combinations and failure recovery.**
   Exercise unpacked artifacts with neither plugin, RPC only, both plugins,
   installed-but-disabled plugins, missing dependencies, first startup and
   partial startup failures. Verify ports/resources are released and mail keeps
   working when optional UI/RPC JARs are removed. Cover dependency/capability
   failures with actionable diagnostics rather than raw missing-class errors.

## Working agreements and scope

- This task covers UI/RPC and the lifecycle API needed by them. Moving SQL/JAM/MSG
  storage selection into plugin factories is a separate future task.
- Do not modify the installed test station, its configuration, databases or mail
  as a side effect of development or tests.
- Keep plugin configuration in ordered `conf.d` fragments; UI/startup must not
  overwrite existing fragments. Do not assume a fragment is itself a plugin.
- Backend authorization and separate UI/robot/PKT/binkp credentials remain intact.
- Keep both HTTP/JSON and TCP Thrift transports and their separate lifecycle.
- Run focused backend/lifecycle/distribution checks. Run local browser UI/VRT
  suites only when relevant UI changes require them; CI retains the full chain.
  Visible UI changes require the corresponding Storybook/VRT updates, themes
  and languages, using pinned Linux baselines.
- Do not push, publish, or upgrade an installed station without a request.

## Resume instructions

Read `AGENTS.md`, `docs/decisions.md`, this plan and
`docs/development-environment.md`; check Git status and recent commits. Reconcile
the checkpoint with actual source before editing. All seven steps are complete;
do not restart this implementation from step 1. Follow decision 36 and the current
plugin/distribution runbooks. Storage-provider plugins remain a separate proposal.
No deployment to the installed test node or push to the remote was performed.
