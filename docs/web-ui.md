# Browser interface

The node hosts its browser interface and Thrift HTTP endpoints in the same JVM.
TCP RPC remains available. No Node.js process is needed when running the node.

The application header stays at the top of the viewport while pages scroll.
Desktop navigation stays directly below it; on mobile, the header keeps the menu
button accessible.

## Settings workspace

Station and personal settings share a compact workspace with a section menu and
one breadcrumb heading (`Station / General`, for example). The current section
owns the page heading; embedded panels do not repeat it or display management
eyebrows. Groups use smaller headings, compact cards and consistent form spacing.
On narrow screens, the section menu becomes a horizontally scrollable tab strip
that reveals the selected section when opened or changed.

Polling and the node directory use the same compact heading, cards and controls
in a single-column workspace, without a second section menu. Their refresh icon
is at the upper right. Polling retains its outgoing-attempt and binkp-log tabs;
directory search retains filtering and pagination, with an accessible search icon.

The station heading stays below the application header while scrolling.
**Reload configuration files** is an icon button at the upper right. It rereads
configuration through the existing authorized RPC operation; it does not restart
the node. Panel refresh actions use a separate icon and contextual tooltip and
appear beside it only while their panel is selected. Hover titles and accessible
names remain available when button text is hidden.

`SettingsWorkspace`, `SettingsPageHeader` and `SettingsSectionHeader` own this
presentation. `SettingsPageActions` places active-panel actions in the workspace
toolbar and leaves standalone panel actions local. Personal template leave guards,
station form drafts, RPC authorization and configuration persistence are unchanged.

Routing rule creation opens from **Add routing rule**; editing opens the same form.
Transit mask fields appear when transit is enabled and retain their values when
hidden. Hiding these controls does not clear saved configuration.

## Mail operations

Netmail composition uses **Add to send queue**. The MAIL navigation panel shows
the current account's **Awaiting send** count and a separate **Send queued mail**
button. Waiting messages survive restart but are not packed by the background
tosser, a poll or manual mail processing. Releasing the queue prepares PKTs; normal
poll scheduling/manual connection controls perform network delivery. Local mail
becomes available to its recipient when released, without a PKT.

**Delete message** removes the selected incoming/outgoing copy from this user's
mailbox after confirmation. Waiting outgoing mail is cancelled. Prepared PKTs
and delivery/duplicate receipts remain, so this action cannot recall mail already
released for delivery. Another local recipient's copy is independent. Deleted
state survives restart; the underlying mail/received-original retention policy
is separate from removing an entry in the browser.

Address suggestions share the node/point directory. `AddressInput` defaults to
including points; use `<AddressInput :include-points="false" />` for a node-only
field. The component passes the flag through Thrift `searchDirectory` to the
catalog's SQL query before pagination. The full Node directory includes points.
This is a presentation filter, not an authorization or link-trust decision.

## Mail workspace

See [Mail workspace](mail-workspace.md) for the three-pane layout, mobile screens,
visual/source FIDO editor, independent mail themes and safe reply templates.

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

For an already configured node with `rpc-node` and `web-ui` plugins installed,
enable `RpcEnabled`, `RpcHttpEnabled` and `WebUiEnabled`, then open
`http://localhost:9091/` (or the configured HTTP port/origin). HTTP serves both the
SPA and `/rpc/` endpoints. Light and dark themes can be changed in appearance
settings or with the header toggle and are saved in the signed-in profile.
Session tokens remain in browser memory: reloading the page requires signing in
again. Public registration submits a point AKA claim for administrator approval.
The sidebar opens netmail, echomail and saved drafts from both mail types.

English is the default language; Ukrainian is the other supported language.
The header selector is available during setup and before sign-in. Its choice is
stored locally in the browser (`jftn-language`), independently of the account's
theme preference. Russian is not an interface language.

Local component checks use two browser workers, matching CI, to keep cold catalogue
loads reliable on development machines. Catalogue smoke checks are divided into
eight batches; focused runs do not narrow the CI test matrix.

## Echomail reader

**Echomail** provides a searchable conference list, a message list and a reading
pane. Switch between **Threads** and **Flat** on desktop; the mobile selector
offers **Conversation tree** and **Flat list**. Unread messages are highlighted;
opening a message saves a read mark for the current account. Other readers' marks
are independent. Tree rows support arrow-key navigation and branch collapse.

The list loads the newest 50 received messages first. **Load older messages**
extends the local list; the tree represents only loaded pages. Threads use exact
FTN MSGID/REPLY links, never subjects. The reading pane can open the parent or
direct replies beyond the loaded list, with additional reply pages when needed.
Missing parents remain explicit; malformed cycles do not trap tree navigation.

**New echomail** and **Reply** open an area-specific editor with the permitted
sender AKAs. Replies quote plain message text and preserve the original MSGID in
REPLY. Saved echo drafts can be resumed from the conference or global **Drafts**
screen. Unsaved edits require confirmation before being discarded. **Post message**
submits through the tosser's normal echo export; it is separate from netmail's
explicit **Send queued mail** workflow. A successful post is not transport delivery
confirmation. Echo deletion is not exposed in this reader.

Administrators can read all local conferences and post from station AKAs.
Approved points can read only areas with READ/BOTH subscriptions for their exact
approved AKAs; composing requires WRITE/BOTH for the chosen AKA. A write-only grant
does not disclose stored messages or message counts. These checks run on the
server for both HTTP and TCP and are refreshed when permissions change. Revoked
write access hides the affected drafts without deleting them.

## Mail font

Under **My settings → Mail appearance**, **Mail font** sets the font family, size
and line height for both reading and composing messages. These preferences are
personal to the current account. Existing separate font preferences initialize
the shared setting from the reading font; subsequent saves store one typography
value. Reading and editor color themes remain independently selectable.

## Station settings groups

The administrator's **Station** screen groups configuration, routing,
logs, mail robots, account approval and plugin settings. Its tabs are **General**,
**Mail links**, **Netmail routing**, **Tosser settings**, **Echo areas and link
access**, **Packet quarantine**, **Logs**, **Mail robots**,
**Accounts and requests** and **Plugins**. **Links** in the main sidebar remains
a shortcut to **Station → Mail links** (Ukrainian: **Лінки**).
The shared node directory stays available to points and administrators; personal
settings stay separate from station administration. Mail templates are under
**My settings → Mail templates**, available to every authenticated account and
saved for that account only.
Only the active group is visible. Switching configuration tabs keeps forms mounted, so unsaved
station/link/tosser edits and quarantine selections survive navigation between
groups. Operational panels load when opened and stop polling when closed.
Switching is not a save; use the active group's own save/confirm action.
Page reload/navigation away and explicit configuration reload have their existing
state-reset behavior. **Reload configuration files** remains above all tabs.

Tabs wrap on narrow screens and support Left/Right, Home and End keyboard
navigation. Switching is disabled while an operation is busy. These tabs do not
change backend authorization: node management remains administrator-only.

## Multiple uplinks

The first-start wizard accepts one or more uplinks in **Station and connection**.
Use **Add link** to add another peer. Each entry has its own FTN address, host,
port, binkp session password and PKT password mode. An empty host creates an
incoming-only link. During setup, local AKAs follow the station address and
aliases, including edits made on the nodelist step. The confirmation screen lists
every peer without displaying passwords.

All entries are validated before creating the initial administrator, then written
to a single configuration candidate. Duplicate peer addresses (including equivalent
`.0` addresses) are rejected. For binkp passwords longer than eight ASCII characters,
select a separate PKT password or explicitly agree on unprotected packets.

After setup, administrators can add more peers under **Station → Mail links →
Add link**, edit per-link local AKAs and save all links together. Saving station
details preserves the additional links. Existing peer addresses cannot be renamed
or removed here because queues, subscriptions and routes require review first.
Adding a link does not automatically grant echo access or create routing rules.

To accept newly encountered echoes from selected peers, enable automatic echo
creation in **Tosser settings** and choose `WRITE` or `BOTH` under **Create new
echoes from this link** for each allowed sender. `NONE` is the default. This
requires database-managed subscriptions. Only a newly created area receives this
initial grant; existing echo rights and all other peers remain unchanged. The
configuration applies live, including when retrying quarantined packets. See
[mail processing](tosser.md#configuration) for file settings and migration.

RPC clients must submit `NodeConfiguration.initialLinks` during first setup.
The list is write-only and rejected after setup; use `links`/`saveLinks` for
subsequent management. Station configuration contains only station fields;
connection/password fields belong to `MailLinkConfiguration`. Each submitted link
must specify `autoCreateEchoAccess` as `NONE`, `WRITE` or `BOTH`. Missing modes
are rejected. There is no adapter for older RPC clients: regenerate the bindings
from the current contracts. Settings responses never include stored passwords.

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
the link password empty when editing preserves it. Supported runtime settings are
validated and applied immediately after saving (see live reload below).
The mail-directory field is editable only during initial setup and is
read-only afterwards. Initial setup derives Inbound/Outbound/Tmp/EchoPath from it;
later saves do not rewrite those paths, even if an API client changes `storage`.
The SQL mail database location is independent. Moving existing storage requires a
stopped-node migration and explicit path edits in configuration.

## Mail robot access

Administrators have a **Station → Mail robots** page. It lists
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

## Connections and binkp logs

Administrators open **Polling** directly from the main menu beside **Links**.
It has its own clock icon in the collapsed menu and is no longer a station tab.
It shows active/waiting attempts, paged history and a **Poll now** action for
configured links. Scheduled and manual
attempts use the same queue and duplicate active requests select the existing
job. Attempts can be cancelled or retried; cancellation closes the connection
without deleting queued outgoing files. The **Outgoing polls** tab lists these
attempts; incoming server sessions are outside that queue.

Selecting an attempt shows its source, start/end times, result and dedicated
journal. Prepared outbound files for that link are shown separately (up to 50);
this snapshot is not a list of messages still awaiting packaging. The journal
includes INFO-level `<<<` (incoming) / `>>>` (outgoing) commands and transfer
progress/completion, with passwords and CRAM responses redacted and binary file
contents omitted. Log text is rendered
as plain text. The page refreshes
every two seconds after the preceding request completes, pauses in hidden tabs,
and stops on navigation away. Auto refresh and following the journal can be toggled.
The browser retains only one history page and the last 1000 loaded log lines.

The **Binkp log** tab shows the same safe INFO format for both incoming and outgoing
connections from `logs/binkp.log`; **Station → Logs** opens this tab directly.
It starts with the latest 200 complete lines
within the final 64 KiB, then reads new events every two seconds while visible.
Use **Show latest events** to reset the cursor. Following the log can be toggled;
the browser keeps at most 1000 loaded lines. A detected file truncation clears the
old view and displays a rotation notice. Same-size/larger file replacement cannot
be detected by the byte cursor alone. Both tabs require administrator authorization.

History and journals survive restarts under `Tmp/polls`. Unfinished attempts are
marked INTERRUPTED; they are not resumed automatically. `PollHistoryLimit`,
`PollTimeoutSeconds` and `PollLogLimitBytes` are file-only settings in
`conf.d/10-binkp.conf`; see [RPC details](rpc-ui.md). Point accounts cannot see
this section or call the underlying administrative methods.

## Nodelist lookup

**Node directory** is a shared navigation item for administrators and points. It
browses imported nodelists and pointlists and searches partial AKA, sysop/station
name, location, phone, IP/hostname, flags and source. Rows include the source and
record type; identical addresses from different lists are retained separately.
Search is debounced and paged, with previous/next controls and empty/loading states.
Desktop results use a table; on small screens each record stacks its labelled
fields so names, endpoints and source remain readable without horizontal scrolling.

Free-form address fields use a keyboard-accessible suggestion list: Up/Down selects
a result, Enter inserts its address, Escape/Tab closes the popup, and clicking a
result works too. For multiple AKAs, only the token at the caret is replaced.
No match or a catalog failure leaves manual input available. The configured-peer
selectors for polls, robots, access rules and quarantine keep their trust boundary.
Suggestions also work before login during registration; the underlying local
catalog search is intentionally public and read-only. It cannot import data or
create links, accounts, routes or AKA ownership grants.

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
server. Names identify the scenario, theme and language. Each VRT test also
attaches its committed reference as **Baseline** in the HTML report, including
successful comparisons. Open a test to view that image; mismatches additionally
retain Playwright's actual/expected/diff diagnostics. A missing baseline still
fails comparison and is never silently created during an ordinary run. Both
artifacts are retained for 15 days.

`./gradlew :web-ui:playwrightVisualTest` compares committed PNG test fixtures for:
administrator/point navigation, directory results/empty/loading states,
address suggestions/multiple-AKA/offline states, first-run setup with multiple uplinks,
station identity and reload controls, six station settings tabs and their busy state,
adding/saving uplinks, duplicate-address feedback,
link/PKT password and tosser forms, echo access (database/file/empty/revocation), packet
quarantine (table/batch confirmation/source selection/results/legacy retry/archive/empty/long filename/unavailable), poll
queue/details, per-poll protocol output, global binkp logs (populated/empty/rotated/busy),
and information/warning/error/configuration-applied notifications.
Plugin scenarios cover areas, link permissions, automatic creation, FileFix/TIC credentials,
file-managed settings and unavailable providers. See [plugin settings](plugin-settings.md).
Mail scenarios include netmail reading, composition and drafts, plus echomail
tree/flat lists, reading, missing parents, composition/replies, read-only,
empty and unavailable conference states.
Each has light/dark and English/Ukrainian
variants at desktop (1280Г—900) and mobile (375Г—812) sizes.

Visible UI changes must add/update their Storybook states and VRT scenarios in
the same change. Generate and visually review affected references in the pinned
Linux container, then run comparison without snapshot updates. Functional browser
tests alone do not complete visual verification. Prefer `--update-snapshots=missing`
for added scenarios; update changed references only after reviewing intentional
differences, without replacing unrelated baselines.

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

Administrators also have a **Station → Netmail routing** page. It shows ordered rules,
offers database-rule creation/editing/deletion and changes the default hop and
transit policy through validated live configuration publication. Rule next hops
and transit preview senders must be configured links. File-managed rules are
read-only; changing the source does not copy rules between the file and database.
The UI confirms a source switch. Empty transit masks allow all configured links
when transit is enabled; transit itself is disabled by default.

**Check a route** explains local delivery, a matching rule, direct/default routing
or the rejection reason. It can simulate a configured transit sender or required
direct delivery. It does not create mail or initiate a connection. Changes affect
new routing decisions; existing queued mail keeps its selected next hop.
Local netmail deliveries appear sent once released locally; remote deliveries appear
sent after transport acknowledgement. A send without a route returns NO_ROUTE
and does not create a delivery or reserve a send receipt.

Public point registration is controlled by `RpcRegistrationEnabled`. New accounts
remain disabled until an administrator approves an AKA claim. Users can select
only approved identities as their default AKA. Personal profile and theme settings
are separate from node settings. Node control remains administrator-only; echo
reader operations use the subscription permissions described above.
Netmail reads, drafts and sending are scoped to approved account AKAs on both RPC
transports. The UI offers inbox/outbox pages, full plain-text message details,
read marks, replies with quoted text and REPLY linkage, draft saving/deletion and
composition with directory suggestions including points. Outgoing status changes
from queued to sent after transport acknowledgement; submission alone does not
mean delivery. Network retries retain the same send request ID. Unsaved edits are
protected when switching folders, leaving the reader or signing out. Netmail
deletion is available as described above; full archive search and trash are not implemented. The workspace offers a loaded-message filter.

Passkeys are an additional sign-in method. A new localhost setup enables WebAuthn
for `localhost`. Remote access requires an HTTPS reverse proxy and matching
`RpcHttpOrigin`, `RpcWebAuthnOrigin` and `RpcWebAuthnRpId`. Numeric IP origins do not
automatically enable passkeys. Existing passkey configuration is preserved.

## Received originals and file-only configuration

Station settings include **Keep original received files**, enabled by default.
The main configuration stores this as `PreserveReceivedFiles=true` (or `false`).
This setting is applied to subsequent operations immediately after saving.

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
```

Only `*.conf` files are loaded, in filename order. Each file replaces all values
of the same key from earlier layers; repeated entries within one file remain a
list. Manual file-only options override the main file. Relative value paths
continue to resolve from the node's working directory. Stream-based configuration
loading has no source directory and does not load fragments.

UI-managed fields are rejected in fragments: `Link`, `System`, `SysOp`,
`Location`, `NodeListUrl`, `NodeListCharset`, `RpcHttpOrigin`, `UiMailStorage`,
`PreserveReceivedFiles`. This prevents a saved UI value from being silently
masked by a manual override. Other advanced settings remain available through files only;
the UI neither exposes them nor rewrites the fragments. Unknown settings already
present in the main file continue to be preserved on UI saves.

Restart after manual edits. On upgrade, preserve your main configuration and
`conf.d/`; distribution archives contain instructions and `*.conf.example` templates there,
not active manual overrides. Startup creates missing plugin files; see
[plugin settings](plugin-configuration.md). Private `conf.d/*.conf` files and the default received archive
are ignored by Git.

### Echo areas and link permissions

Open **Station**, then **Echo areas and link access** below the mail settings.
This administrator-only section lists current permissions, supports search by area
or peer, and shows known areas even after their final subscription is revoked.

With `EchoSubscriptions=database` (the full package default), select a configured
link, enter an existing or new area name and choose:

- **WRITE**: receive echomail from that peer (the form's default).
- **READ**: send echomail to that peer.
- **BOTH**: allow both directions.

Save creates a missing area and applies permissions immediately; no restart is
needed. Edit changes rights without renaming the subscription's peer/area.
Revocation requires confirmation and retains stored mail, delivery checkpoints
and already prepared outbound packets. New subscriptions use NEW_ONLY history;
changing/restoring rights preserves existing delivery positions.

Configuration mode is read-only in this section, and RPC also rejects mutations
with `READ_ONLY`. Switch `EchoSubscriptions` in `conf.d/41-areafix.conf` and restart
deliberately if file-managed subscriptions should become database-managed.
Granting rights does not replay `.bad`/quarantined packets. Use **Station → Packet quarantine**
after correcting all packet errors; do not delete receipts or bypass passwords.
This section does not change AreaFix robot credentials or disable the tosser's
`EnforceEchoAccess` setting. Those are separate controls.

Storybook includes database, file-managed, empty and revocation states. Controller
tests cover mutations/failures, runner tests exercise SQL access over real HTTP/TCP,
and browser tests exercise the station workflow and component states.

### Mail links and tosser

Administrators can edit links and basic tosser settings under **Station settings**.
Point accounts and the initial setup token cannot access these RPC operations.
Changes are validated, written atomically to `jftn.conf` and applied live;
current connections retain their existing session configuration. After an
external file edit, use **Reload configuration files** before saving from the UI.

Each link has a remote address, local AKAs, optional DNS/IPv4 host (empty for incoming-only links), port and a write-only
binkp session password. Empty password fields retain stored values; `-` removes
the session password. PKT passwords have three explicit modes:

- **Use binkp password**: no `PacketPassword` override; the session password must
  fit the PKT format (at most eight ASCII characters).
- **Separate PKT password**: write a `PacketPassword=address,password` override.
  The password must match the peer's packet configuration, which can differ from
  its binkp password. An empty field retains an existing separate password.
- **Unprotected packets**: write `PacketPassword=address,-`; incoming packets
  must have an empty PKT password. This does not disable validation of protected
  packets or make a wrong packet password acceptable.

Passwords are never returned by the settings RPC. Links can be added and edited;
existing link addresses cannot be renamed or removed in the UI, because routes,
subscriptions and queued mail must first be reviewed manually. The primary link
remains first. Station AKAs in the main form belong to this primary link; each
additional link can advertise its own local AKAs.

The tosser form manages `TossIntervalSeconds`, `Deletetossed`, `EnforceEchoAccess`
and `AutoCreateEcho`. Received-file archiving remains a separate station option:
removing processed inbound files does not remove their archived originals.
Routes, transit policies and plugin configuration retain their existing management
mechanisms; echo permissions have the separate live database editor above.
If a mail setting is already overridden in
`conf.d/*.conf`, the UI reads that effective value but refuses to save that group
until its options are moved into `jftn.conf`; it never rewrites manual fragments.

Changing a PKT password does not automatically recover quarantined `.bad` files
or delete conflicting rejected packets. Retained bundles are retried by the
tosser; resolve any reported quarantine conflicts separately without overwriting
previously rejected mail.

## Packet quarantine

Administrators can open **Station → Packet quarantine** to inspect rejected
`.bad` packets under the tosser's temporary directory. Each page shows up to 25
files, their recorded receiving peer, failure reason, size, modification time
and retry history. Refresh returns to the first page; Next page advances through
the snapshot cursor. Only packet quarantine is listed, not corrupt ZIP bundles
still retained in inbound. Oversized files and damaged metadata need manual
inspection; their mutation controls are disabled.

Packets are shown in a table. Select individual rows or **Select all on this page**,
then choose **Restore selected** and confirm the batch. Selection is limited to
the current page and resets on refresh or page changes. Unknown source links can
be assigned independently in each row, including while reviewing confirmation;
recorded session peers cannot be overridden. Every selected packet must have a
valid configured source and checksum before any request is sent.

Batch restoration runs sequentially through the same administrator-only retry
API as individual restoration. It is not an all-or-nothing transaction: a failed
packet does not prevent later packets from being tried. The summary and per-file
results distinguish accepted packets, protocol rejections and request failures.
After completion the list refreshes; the results remain visible. If the browser
is interrupted, refresh and inspect the remaining quarantine before retrying.

For an individual packet, choose **Release and retry** and confirm. Existing
PKT password, destination and echo rights checks still apply. Newly quarantined
files retain their original receiving-session peer; legacy `.bad` files require
explicit selection of an expected configured link. Pending configuration changes
are applied live after a successful save; database echo rights also apply
immediately. A rejected retry stays in the list with an updated reason.

**Archive packet** has separate confirmation and removes an item from the active
list without importing it. Both actions preserve the exact original in
`Tmp/.quarantine/archive`. Successfully imported message journal entries remain
intact, so retrying an unchanged partial packet skips accepted messages. Changed
files cause a stale-selection error and must be refreshed. Existing retained
inbound files may be queued again independently; archival does not suppress them.
All three RPC operations require an administrator on both TCP and HTTP.

Storybook provides packet tables, mixed-source batch confirmation (missing/ready
links), batch results, legacy retry, archive confirmation and empty states.
Core tests exercise restart recovery and archive corruption; browser tests cover
confirmation, explicit peer selection and HTTP interaction with an isolated fixture.

## Live configuration reload

Station/link/tosser saves now validate and stage their runtime changes before
publication. **Reload configuration files** explicitly rereads `jftn.conf` and
ordered `conf.d/*.conf`; there is no automatic file watcher. Only administrators
can reload, through `SettingsService.reload` on HTTP or TCP.

Supported changes include station name/sysop/location, local AKAs, adding links,
link endpoints and binkp/PKT passwords, received-original retention, processed-file
removal, echo access/autocreation flags, the tosser interval and netmail routing
policy (rule source, configured rules, default hop and transit restrictions). Outgoing poll jobs
already queued keep their selected link and session configuration; future jobs
and new incoming connections use the new snapshot. Existing connections and RPC
login sessions are preserved. Changing autocreation does not grant echo rights.

Changes to storage paths, listeners/binds/ports, HTTP/WebAuthn origins,
subscription-source modes and plugin internals require a controlled
restart. Removing links requires reviewing their queues/subscriptions first.
Such candidates are rejected with the option name; UI saves leave the previous
file and running configuration intact instead of saving unapplied settings.

Parsing/validation failures appear as UI errors. File publication errors roll back
the SQL link update; a transaction failure after file publication attempts to
restore the previous file. Failed preparation cancels its staged timer and retains
the old schedule/snapshot. Concurrent external main-file or fragment edits reject
publication. Manually edited invalid files remain on disk for the administrator
to correct; rejection keeps the last valid runtime snapshot active. Startup still
requires valid on-disk configuration. This is runtime failure recovery, not a
cross-filesystem/database crash-atomic transaction.
