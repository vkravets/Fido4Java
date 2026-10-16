# Browser interface

The node hosts its browser interface and Thrift HTTP endpoints in the same JVM.
TCP RPC remains available. No Node.js process is needed when running the node.

## Building and opening the interface

The browser application uses Vue 3 and TypeScript (`strict` mode), built with Vite.
All handwritten browser code and tests are TypeScript. Thrift generates JavaScript
bindings and matching TypeScript declarations from the RPC contracts; these build
outputs are not committed. `vue-tsc` checks Vue templates and API calls before the
production bundle is produced. TypeScript 6.0.3 is pinned because the current
`vue-tsc` does not support TypeScript 7's compiler package layout.

Build prerequisites are Java 17+, the configured Thrift compiler, Node.js 22.12+
and npm. The matching official Thrift browser runtime is supplied through
`THRIFT_BROWSER_RUNTIME`, as for the RPC browser tests. Gradle runs `npm ci` using
the committed lockfile, checks types, builds the browser application, and packages
HTML/CSS/JavaScript into the `web-ui` JAR. It does not install Node.js system-wide.

```sh
./gradlew :web-ui:build
./gradlew :web-ui:typecheckFrontend
./gradlew :web-ui:frontendTest
```

If `thrift` is not on PATH, pass `-PthriftExecutable` with an absolute path to
its native executable. Installing the Java/JavaScript Thrift libraries does not
install this compiler. `prepareFrontendRpc` needs both the compiler and the
matching browser runtime. For example, from the repository root in PowerShell,
using locally downloaded tools (adjust the paths to your installation):

```powershell
.\gradlew.bat :web-ui:prepareFrontendRpc `
    "-PthriftExecutable=$PWD/build/thrift-0.25.0.exe" `
    "-PthriftBrowserRuntime=$PWD/build/thrift-browser-official/thrift-0.25.0/lib/js/src/thrift.js" `
    "-PnodeExecutable=$PWD/build/node-v24.22.0-win-x64/node.exe"
```

These `build/` paths are local build outputs, not files supplied by Git; restore
or download the tools again after deleting that directory. The same properties
apply to `build`, `buildStorybook` and the other frontend tasks.
`nodeExecutable` and `npmExecutable` Gradle properties can select existing tools.
GitHub Actions sets up Java 17 and Node.js 24, caches npm downloads by lockfile,
and saves Java and TypeScript test reports. The integration jobs continue to use the
compiled runtimes produced by the main build.

For an already configured node, enable `RpcEnabled` and `RpcHttpEnabled`, then open
`http://localhost:9091/` (or the configured HTTP port/origin). HTTP serves both the
SPA and `/rpc/` endpoints. Light and dark themes can be changed in appearance
settings or with the header toggle and are saved in the signed-in profile.
Session tokens remain in browser memory: reloading the page requires signing in
again. Public registration submits a point AKA claim for administrator approval.
The sidebar reserves navigation for future mail readers; those items are disabled.

English is the default language; Ukrainian is the other supported language.
The header selector is available during setup and before sign-in. Its choice is
stored locally in the browser (`jftn-language`), independently of the account's
theme preference. Russian is not an interface language.

Notifications appear at the top of the browser viewport, including while the
page is scrolled. Information and warning notices have a close button. Error
notices stay visible until the underlying error is cleared or the action succeeds.

Frontend formatting uses Prettier with four-space indentation and a 100-column
limit: run `npm run format` from `web-ui/frontend`.

## Initial configuration

When `jftn.conf` is absent, the runner starts a setup listener on `127.0.0.1:9091`
before starting plugins and mail services. Open the full URL printed in the console;
its fragment contains a random setup token. It is never included in HTTP requests
as a URL parameter. Keep this URL private. Public point registration is unavailable
until setup completes.

The wizard creates an administrator account, station addresses, a primary link and
mail directories. RPC login credentials are independent of binkp link credentials.
The administrator password is hashed in the account database and is not written
to `jftn.conf`. The initial link password also protects PKT files and must contain
at most eight ASCII characters. `-` means an unprotected link.

For an existing configuration, setup is offered only when RPC is enabled, there are
no stored accounts and no configured bootstrap password. Removing `RpcPassword`
after creating an account never reopens setup. If account creation succeeds but
config publication is interrupted, completing setup requires that administrator's
credentials. Account creation and file publication are separate durable operations;
they do not form one database transaction.

Settings are written through a temporary file and atomic replacement. An external
edit detected before replacement prevents saving. Existing unrelated options and
additional links are retained; the settings form edits the first configured link.
Link and administrator passwords are never returned by the settings RPC. Leaving
the link password empty when editing preserves it. Config changes require a node
restart. The mail-directory field is editable only during initial setup and is
read-only afterwards. Initial setup derives Inbound/Outbound/Tmp/EchoPath from it;
later saves do not rewrite those paths, even if an API client changes `storage`.
The SQL mail database location is independent. Moving existing storage requires a
stopped-node migration and explicit path edits in configuration.

## Mail robot access

Administrators have a **Mail robots** page under node management. It lists
AreaFix, FileFix and TIC access rules with their link, area masks, enabled state
and AreaFix rights (READ/BOTH). Create a rule for a configured link, edit its
policy/password, disable it temporarily or delete it with confirmation. Changes
are applied to subsequent requests without restarting the node. Existing accepted
work is retained. Passwords are never returned to the browser; an empty password
while editing preserves the existing secret.

The default source is the mail database. With `RobotAccessSource=files`, the page
is read-only and explains how to select database mode and explicitly import file
rules. Point accounts have neither this navigation entry nor RPC permission to
inspect/change rules. Technical script loading, file-area paths and directory-feed
configuration remain in `conf.d`; see [robot access](robot-access.md).

## Outgoing connections

Administrators have a **Connections** page with active/waiting attempts, paged
history and a **Poll now** action for configured links. Scheduled and manual
attempts use the same queue and duplicate active requests select the existing
job. Attempts can be cancelled or retried; cancellation closes the connection
without deleting queued outgoing files. Incoming server sessions are not listed.

Selecting an attempt shows its source, start/end times, result and dedicated
journal. Prepared outbound files for that link are shown separately (up to 50);
this snapshot is not a list of messages still awaiting packaging. Protocol secrets
are not journalled, and log text is rendered as plain text. The page refreshes
every two seconds after the preceding request completes, pauses in hidden tabs,
and stops on navigation away. Auto refresh and following the journal can be toggled.
The browser retains only one history page and the last 1000 loaded log lines.

History and journals survive restarts under `Tmp/polls`. Unfinished attempts are
marked INTERRUPTED; they are not resumed automatically. `PollHistoryLimit`,
`PollTimeoutSeconds` and `PollLogLimitBytes` are file-only settings in
`conf.d/10-binkp.conf`; see [RPC details](rpc-ui.md). Point accounts cannot see
this section or call the underlying administrative methods.

## Nodelist lookup

Setup and administrator station settings can query the local directory or
explicitly download a full nodelist from `NodeListUrl`. An empty URL in a lookup
request means local lookup only; a nonempty URL requests a download/import. The default mirror is
`https://nodelist.fidonet.cc/download/latest?domain=fidonet`; availability is outside
JFtn's control and another HTTP(S) source may be configured. `NodeListCharset`
defaults to `CP866`. Downloads have size/time limits; parsing and checksum validation
must pass before publication. Setup imports into `NodeDirectoryPath` (default
`database/nodelist/catalog`). Lookup metadata is not proof of address ownership.
When only a point's boss is found, the result is marked as a boss record and does
not supply a sysop identity for the point. With matching records from several
sources, this settings lookup currently uses the first returned record; it has
no source-selection control.

## Component catalogue

Storybook renders the actual Vue components with isolated sample data, without
connecting to a node. The toolbar switches between light/dark themes and
English/Ukrainian. Stories cover administrator/point navigation, notifications,
station forms, poll states and journals, including empty and long-content cases.

From the repository root, start the live component catalogue through Gradle:

```powershell
.\gradlew.bat :web-ui:storybook
```

This prepares npm dependencies and starts Storybook at `http://127.0.0.1:6006/`
with live updates when sources change. Keep the terminal running; Ctrl+C stops
it. The interactive catalogue uses sample data and type-only RPC imports, so it
requires neither the Thrift compiler nor the browser runtime/generated RPC files.
Full contract/type checking remains in `typecheckStorybook` and `buildStorybook`,
which still use the normal Thrift build prerequisites.

Frontend Gradle tasks prefer an explicit `-PnodeExecutable`, then an existing
`build/node-v24.22.0-<platform>-<arch>` portable installation, then `node` on PATH.
Gradle does not currently download Node.js; keep the portable installation or
provide an installed executable. Its directory is added to the Storybook process
PATH so subprocesses can find Node too. Set `-PstorybookPort=6008` to choose a
port. These interactive tasks are not part of `build` or `check`.

`./gradlew :web-ui:previewStorybook` builds and serves the static catalogue.
For an already extracted CI artifact, skip compilation with:

```powershell
.\gradlew.bat :web-ui:previewStorybook "-PstorybookDirectory=C:/Downloads/storybook"
```

The selected directory must contain `iframe.html`. This mode prepares the local
npm preview tool but requires no Thrift compiler, browser runtime or Python.
Relative paths are resolved from the repository root. On Linux/macOS use `./gradlew`.
Direct `npm run storybook` remains available when Node/npm are already on PATH
and npm dependencies are installed.
`./gradlew :web-ui:buildStorybook` builds a standalone catalogue under
`web-ui/build/storybook`. CI publishes it as the `storybook` artifact; it is not
included in the application distribution.

To view the downloaded GitHub Actions artifact, extract the complete `storybook`
ZIP and serve its root over HTTP. Double-clicking `index.html` (`file://`) is not
supported. From the extracted directory containing `index.html` and `iframe.html`:

```powershell
py -3 -m http.server 6006 --bind 127.0.0.1
```

This requires Python 3; on Linux/macOS use `python3` instead of `py -3`. Open
`http://127.0.0.1:6006/`, keep the terminal running, and stop it with Ctrl+C.
No Java server, Thrift compiler, npm dependencies or Docker engine is needed to
view an already built catalogue. The artifact includes these instructions in
`README.txt`. GitHub's artifact download page does not host the catalogue itself.

Stories and Storybook configuration have their own `tsconfig.storybook.json`:
story source is checked, while upstream toolkit declaration files are skipped
because Storybook currently ships incompatible declarations for TypeScript 6.
The application keeps its existing strict declaration checking.

## Browser tests

Playwright uses headless Chromium. Install its browser once from `web-ui/frontend`
with `npm run test:ui:install`. To keep downloads inside the checkout, set
`PLAYWRIGHT_BROWSERS_PATH` to an absolute path such as `<checkout>/build/playwright-browsers`
for both installation and test runs.

```sh
./gradlew :web-ui:playwrightComponentsTest
./gradlew :web-ui:playwrightTest
```

The first task builds Storybook and checks its components in a browser: both themes
and languages, notification positioning during scroll, dismissal rules, unchanged
banner height with/without a close control, point/admin navigation, mobile forms,
disabled actions, and escaped journal text.
The second starts an isolated Java HTTP server on a system-assigned port, with a
temporary account database and the compiled production Vue assets. It exercises
real HTTP Thrift calls for sign-in, registration, administrator approval, point
navigation and profile/theme changes. A separate fresh server checks initial setup
and password validation. It never connects to an operator's running node. These
opt-in tests are excluded from ordinary `test`/`check`; the existing Java and
TypeScript tests continue to run normally. Hardware passkey dialogs and actual
binkp polling are outside these browser scenarios.

Playwright HTML reports live under `web-ui/build/reports/playwright` in
`components`, `application` and `setup`; their JUnit XML files are under
`web-ui/build/test-results/playwright`. The Java wrapper task also writes its own
report to `web-ui/build/reports/tests/playwrightTest` and XML to
`web-ui/build/test-results/playwrightTest`. Failures include screenshots, video and
Playwright traces. Open a trace from the frontend directory with
`npx playwright show-trace <trace.zip>`. These reports contain test account/session
data and should be shared only as debugging artifacts.

CI runs the UI job after both binkd/HPT integrations succeed, using the main
build's compiled test/runtime snapshot and Storybook artifact. It installs
Chromium and Linux dependencies and uploads browser diagnostics. The separate
VRT job runs next; both UI and VRT checks gate release packaging.

## Visual regression tests

Expected images are committed under
`web-ui/frontend/test/browser/snapshots/desktop/` and `mobile/`. CI uploads all of
them as the separate `vrt-baselines` artifact from the main build, before any
integration/browser tests. Download/extract it to open PNGs directly, without a
server. Names identify the scenario, theme and language. Successful VRT runs need
not attach every expected image to their report; `vrt-reports` is for comparison
results and failure diagnostics. Both artifacts are retained for 15 days.

`./gradlew :web-ui:playwrightVisualTest` compares 64 committed PNG test fixtures:
administrator/point navigation, station settings, poll queue/details, and
information/warning/error notifications. Each has light/dark and English/Ukrainian
variants at desktop (1280×900) and mobile (375×812) sizes.

Both Windows and CI run these comparisons in the same pinned Linux/amd64
Playwright image. Docker is used on Linux; Podman is the default on Windows.
Set `VRT_CONTAINER_ENGINE=docker` or `podman` to select another installed engine.
The container engine must already be running. The browser receives no external
network access; Storybook and the expected screenshots are mounted read-only.
Dates, timezone, locale, pixel scale and animations are fixed, and fonts are
loaded before capture. A per-pixel threshold of 0.1 permits minor antialiasing
differences; no pixels above that threshold are allowed to differ.
This follows [Playwright visual comparisons](https://playwright.dev/docs/test-snapshots)
and its [container guidance](https://playwright.dev/docs/docker).

After an intentional design change, build Storybook and explicitly regenerate
the reference images from `web-ui/frontend`:

```sh
npm run test:vrt:update
npm run test:vrt
```

Review the changed images in `test/browser/snapshots` and commit them with the
design change. Updates are rejected in CI; missing or different references fail
the build. `npm run test:vrt -- --project desktop --grep administrator-navigation`
limits a comparison when debugging. Reports are in
`web-ui/build/reports/playwright/visual/index.html`; mismatches retain actual,
expected and diff images plus a trace. CI uploads them in the separate
`vrt-reports` artifact (retained for 15 days). The `vrt` job runs after both
integration suites and the functional `ui` job, and blocks release packaging
on failure. It uses the build's Storybook artifact without
rebuilding the catalogue; no host Java or Chromium installation is required for
this job. VRT's JUnit XML is
`web-ui/build/reports/playwright/visual.xml`, separate from the other browser XML
results. PNGs in the source tree are
test fixtures; runtime/build reports remain untracked.

## Accounts and access

Public point registration is controlled by `RpcRegistrationEnabled`. New accounts
remain disabled until an administrator approves an AKA claim. Users can select
only approved identities as their default AKA. Personal profile and theme settings
are separate from node settings. The existing node-wide mail/control RPC remains
administrator-only; scoped point mail reading and sending belong to the next UI
phase. Netmail and echo readers are not part of this initial interface.

Passkeys are an additional sign-in method. A new localhost setup enables WebAuthn
for `localhost`. Remote access requires an HTTPS reverse proxy and matching
`RpcHttpOrigin`, `RpcWebAuthnOrigin` and `RpcWebAuthnRpId`. Numeric IP origins do not
automatically enable passkeys. Existing passkey configuration is preserved.

## Received originals and file-only configuration

Station settings include **Keep original received files**, enabled by default.
The main configuration stores this as `PreserveReceivedFiles=true` (or `false`).
Settings changes require restarting the node, like the other station settings.
The running service keeps its current configuration until restart.

Completed binkp transfers are copied before processing can remove them. The
standalone tosser also preserves loose PKT/bundle inputs before importing them.
The UI's HTTP nodelist download preserves the received response before parsing.
Archiving errors stop the destructive processing path; the source remains for
retry. Partial/incomplete network transfers are not archived as complete files.

The default archive is `received` beside the configured inbound directory,
usually `mail/received`. Originals are stored as `<SHA-256>/<filename>` with
streamed copies, without loading entire files into memory. Identical bytes and
names reuse an archive entry; different contents with the same name are kept
separately. There is no automatic expiration or quota: remove old archives
manually when they are no longer needed. Disabling preservation affects future
processing and never removes existing archives. `Deletetossed` retains its legacy
meaning for clearing the processed inbound queue; leaving it at its default `1`
avoids repeatedly tossing retained inputs. Originals live outside that queue.

The main `jftn.conf` remains managed by the UI. Create additional manual files
under `conf.d/` **beside that configuration file**, for example:

```ini
# conf.d/50-local.conf
ReceivedArchivePath=./mail/received
Deletetossed=1
```

Only `*.conf` files are loaded, in filename order. Each file replaces all values
of the same key from earlier layers; repeated entries within one file remain a
list. Manual file-only options override the main file. Relative value paths
continue to resolve from the node's working directory. Stream-based configuration
loading has no source directory and does not load fragments.

UI-managed fields are rejected in fragments: `Link`, `System`, `SysOp`,
`Location`, `NodeListUrl`, `NodeListCharset`, `RpcHttpOrigin`, `UiMailStorage`,
`PreserveReceivedFiles`. This prevents a saved UI value from being silently
masked by a manual override. Other settings remain available through files only;
the UI neither exposes them nor rewrites the fragments. Unknown settings already
present in the main file continue to be preserved on UI saves.

Restart after manual edits. On upgrade, preserve your main configuration and
`conf.d/`; distribution archives contain instructions and `*.conf.example` templates there,
not active manual overrides. Startup creates missing plugin files; see
[plugin settings](plugin-configuration.md). Private `conf.d/*.conf` files and the default received archive
are ignored by Git.
