# Test coverage and functional scenarios

Coverage is measured for handwritten Java code in every source module. Generated
Thrift classes are excluded. Unreachable branches, exception constructors and
unfinished features remain visible in the report; they are not excluded to improve
the percentage. A covered branch is evidence that a path ran, not proof that the
whole feature is correct.

## Reproduce

Run `./gradlew coverageStatistics` for unit tests and real TCP tests. Add
`-PcoverageWithBinkd` to run the Docker interoperability scenarios for both Netty
and MINA2. On Windows use `gradlew.bat`. The task runs each Java module's `test`
task, not the complete `check` lifecycle. It does not run the standalone RPC
JavaScript tests, frontend unit tests, Storybook, Playwright or HPT integration.
Even running those suites separately does not add their execution data to this
aggregate; its inputs are `test.exec` and, optionally, `binkdDockerTest.exec`.

Compilation/resource preparation still builds the embedded Vue assets. A clean
checkout therefore needs Java 17+, Thrift 0.25.0, Node.js 22.12+, npm and the
matching Thrift browser runtime. For example:

```sh
THRIFT_BROWSER_RUNTIME=/path/to/thrift/lib/js/src/thrift.js ./gradlew coverageStatistics -PthriftExecutable=/path/to/thrift
```

Alternatively pass `-PthriftBrowserRuntime=/absolute/path/to/thrift.js`. On
PowerShell set `$env:THRIFT_BROWSER_RUNTIME` before invoking `gradlew.bat`.
Use `build` for the ordinary Java and JavaScript/TypeScript checks, and the
explicit integration/browser tasks for their separate results.

Reports: `build/reports/coverage/unit/index.html` and `unit.xml`, or
`build/reports/coverage/all/index.html` and `all.xml` with Docker enabled.
The combined report includes calls to one module from another module's tests.
Docker scenarios remain optional for ordinary `build` and `test` runs.

## Functional checks

| Component | Checked behavior |
| --- | --- |
| binkp common | Command routing and mandatory arguments; frame lengths and binary/command round trips; NR policy in both roles; resume offsets; short/oversized streams; rewind; interrupted transfers; SKIP deferral and reconnect; duplicate GOT; completion requires both EOBs and no pending files; CRAM against JCE HMAC including long UTF-8 passwords; streaming encryption, reset and saved decryption state; partial-file locking, append, readback and reopen. |
| Netty and MINA2 | Production client/server TCP exchange; wrong-password rejection; terminal states; cancelled/concurrent polling; malformed frames and handler errors; Docker binkd exchange in both roles with and without requested NR. Embedded/DummySession tests check handler failures, while TCP and Docker tests check actual transport interoperability. |
| FTN formats | Type-2 and extended packets; unsigned address fields; legacy zone fallback; capability words and points; packet/message truncation, field limits and terminators; charset aliases; routing lists; address identity and ordering; text wrapping. |
| Configuration | Required options, repeated values, case/locale-independent keys, equals in values, comments, whitespace, empty entries, malformed lines, input closure, directories and area-list creation. |
| Message/EchoList | Message addressing, malformed controls and hook edits; repeated kludges; serialization; echo-list parsing, all supported options, append/reload, duplicate areas and preservation after failed I/O. |
| Database | Real H2 round trips, transactions and rollback, area/subscription queries, paging through multiple pages, invalid bounds, failed queries/conversion, initialization failure/retry and baseline storage/index persistence across restart. |
| JAM and bridge | File layout, CRCs, append/readback, deletion flags, timestamps, malformed signatures/subfields and truncated indexes; SQL/editor synchronization, submission policy, receipts and recovery. The explicit HPT suite checks real external JAM/PKT interchange and cross-process locking. |
| Tosser | ZIP/loose PKT intake, packet authentication and quarantine; netmail route/transit/Hold policy, echo access and fan-out, duplicate receipts, local submissions, outbound acknowledgement recovery and historical JAM import. |
| Plugins/scripts | Dependency order; absent/cyclic dependencies; failed load/unload; context cleanup; command/hook registration; reload, unload retry, GC survival, manager isolation, empty/broken/missing scripts and missing/non-invocable engines. |
| Scheduler/tools | Immediate/deferred and cron execution; cancellation and lifecycle/reload; concurrent date formatting/parsing, cache hash collisions and locale changes; file-resource closure. |
| RPC/Runner | Real framed TCP and HTTP calls, account/password/passkey verification, roles and moderated AKA claims, mail/draft operations, robot access, poll queue/history/journals, transport lifecycle and startup cleanup. Distribution tests check layout and launch behavior. |
| Directory and mail scripts | Full/ZIP lists, hierarchy, CRC, endpoint interpretation, transactional catalog replacement, HTTP downloads and diff recovery; AreaFix/FileFix authorization, subscriptions, TIC delivery and directory feeds. |
| Browser UI | TypeScript checks and unit tests run in the normal build. Opt-in Playwright tests exercise components and real HTTP setup/login/settings flows; VRT checks 64 fixed screenshot cases. CI runs integrations, then functional browser checks, then VRT, using main-build artifacts. |

## Limits and decisions still needed

These cases are distinguished from ordinary remaining tests. The complete list of
uncovered source lines and branches is in the generated HTML/XML report.

| Area | Gap and proposed approach |
| --- | --- |
| Legacy JAM facade | `JAMEchoBase.getMessage()` and `delMessage()` still have empty bodies. The newer `JamArea` API and bridge provide the exercised read/write path; do not infer support for those legacy methods from bridge coverage. |
| Browser mail reader | The RPC mail APIs exist, but browser netmail/echo readers are not implemented. Navigation is reserved. UI screenshots do not prove mailbox reading or composition works. |
| Runner process lifecycle | Startup collaborators and distribution subprocess probes are tested. These are not exhaustive checks of forced termination, shutdown races or crash recovery at every persistence boundary. |
| Long protocol timeouts and races | `FilesSender` still uses 300-second GET/acknowledgement waits. Poll deadlines are configurable and have separate tests; that does not cover every sender deadline or packet/thread ordering. Prefer controlled clocks/fault injection to five-minute tests. |
| OS-level I/O failures | HPT tests exercise a JAM lock held by another process in Linux. Disk full, close/fsync failure, symlink/path races and behavior across Windows/Linux still need targeted fault injection. Permission-only tests are not portable to Windows or root CI. |
| Database fault injection | Real-H2 transactions, conflicts, errors and rollback are tested. Secondary cleanup failures and interruption at every transaction/publication stage still need controlled DAO/connection factories. |
| Alternative script runtime | `JythonScriptManager` needs a Python JSR-223 engine; the packaged runtime supplies Groovy. Python runtime compatibility is not covered by Groovy tests. |
| Physical authenticators | Software passkey tests verify signed ceremonies and validation. Real Windows Hello, Chrome password-manager sync, mobile/security-key UX and HTTPS proxy deployments need device/browser checks. |
| External interoperability | HPT and binkd cover the documented scenarios, not every FTN implementation or optional extension. Interactive GoldED+ compatibility has not been certified. |
| Other reachable gaps | Regenerate the report to locate current missed paths. Refactoring and new modules have invalidated old source line numbers. There is no claim of complete functional or 100% branch coverage. |

See [browser testing](web-ui.md#browser-tests),
[HPT scenarios](tosser.md#real-hpt-interoperability-tests) and
[binkd scenarios](../binkp/src/integrationTestShared/README.md) for commands and scope.
Browser/VRT results are separate from the Java JaCoCo percentages. Neither a
screenshot match nor a covered branch replaces behavior assertions.

## Historical measured snapshot (2026-10-14)

The following figures describe the earlier coverage round, not the current tree.
Later additions include the JAM bridge, directory services, expanded RPC and UI.
No new coverage measurement was taken for this documentation update.

Full build and eight Docker scenarios passed. The final unit-report refresh also passed.
83 new test methods were added in the coverage round; the style cleanup adds no test cases.
Results: 261 passed, 3 skipped, 0 failed.
The skips in this historical snapshot were Mina2ServerTest.test1/test2 and NettyClientTest.testBaseFlow.
Those retired tests have since been removed. Their useful Netty lifecycle and
fragmentation checks now run in NettyPeerTransferTest and BinkDataDecoderTest.

Overall: **91.1% lines (4514/4955)** and **90.2% branches (1342/1487)**.

| Module | Lines | Branches |
| --- | ---: | ---: |
| binkp/binkp-common | 93.6% | 91.9% |
| binkp/binkp-mina2 | 87.5% | 76.9% |
| binkp/binkp-netty | 89.7% | 81.7% |
| event-bus | 100.0% | 100.0% |
| fido-config | 89.4% | 87.5% |
| fido-database | 90.2% | 86.9% |
| fido-fts | 88.2% | 86.9% |
| fido-tools | 94.7% | 99.3% |
| jambase | 96.1% | 96.8% |
| jftn-bundle | 73.0% | 100.0% |
| jtoss | 89.8% | 93.8% |
| plugin-manager | 89.1% | 91.7% |
| rpc | 89.7% | 85.7% |
| scheduler | 100.0% | 100.0% |
| script-api | 85.7% | 97.4% |
| tools | 93.5% | 81.2% |

See the [source-by-source gap inventory](test-coverage-gaps.md). This is a snapshot; rerun coverageStatistics after changes.
