# Test coverage and functional scenarios

Coverage is measured for handwritten Java code in every source module. Generated
Thrift classes are excluded. Unreachable branches, exception constructors and
unfinished features remain visible in the report; they are not excluded to improve
the percentage. A covered branch is evidence that a path ran, not proof that the
whole feature is correct.

## Reproduce

Run `./gradlew coverageStatistics` for unit tests and real TCP tests. Add
`-PcoverageWithBinkd` to run the Docker interoperability scenarios for both Netty
and MINA2. On Windows use `gradlew.bat`. RPC compilation requires Thrift 0.14.2:
add `-PthriftExecutable=/path/to/thrift` if it is not on PATH.

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
| Database | Real H2 round trips, transactions and rollback, area/subscription queries, paging through multiple pages, invalid bounds, failed queries/conversion, version tracking, initialization failure/retry and baseline storage/index persistence across restart. |
| JAM | File layout, CRCs, append/reopen, timestamps, malformed signatures/subfields, resource cleanup, unknown areas, invalid dates and truncated indexes. |
| Tosser | ZIP to FTN packet to H2; duplicate suppression, netmail separation, packet authentication, quarantine and configured bundle deletion; corrupt bundles; safe quarantine paths. |
| Plugins/scripts | Dependency order; absent/cyclic dependencies; failed load/unload; context cleanup; command/hook registration; reload, unload retry, GC survival, manager isolation, empty/broken/missing scripts and missing/non-invocable engines. |
| Scheduler/tools | Immediate/deferred and cron execution; cancellation and lifecycle/reload; concurrent date formatting/parsing, cache hash collisions and locale changes; file-resource closure. |
| RPC/Runner | Actual framed multiplexed TCP calls to RPC services; port conflicts and repeated stop/start; runner startup, cleanup on failure, shutdown-hook coordination, interrupted waits and isolated link polling. |

## Limits and decisions still needed

These cases are distinguished from ordinary remaining tests. The complete list of
uncovered source lines and branches is in the generated HTML/XML report.

| Area | Gap and proposed approach |
| --- | --- |
| Unfinished functionality | `JAMEchoBase.getMessage()` and `delMessage()` have empty bodies. Tests cannot prove reading/deletion works until behavior is implemented. RPC default login returns a constant session and message handling is a stub; current tests prove routing only. Define storage/authentication behavior before adding acceptance tests. |
| Runner process lifecycle | `JFtnRunner.main`, `System.exit` and a real JVM shutdown must be checked in a child process. The in-process startup/shutdown collaborators are tested. Use a temporary config, isolated ports and a separate JaCoCo agent/output for child-JVM tests. |
| Long protocol timeouts and races | `FilesSender` hardcodes 300-second waits for GET/acknowledgement; transport limits include connection-count and busy timeout paths. Cancellation/resume are tested, but deadline expiry and every packet/thread ordering are not. Add a configurable timeout/clock for deterministic fault scenarios rather than wait five minutes per test. |
| OS-level I/O failures | Disk full, failure during close/fsync, a lock held by a different process, canonical-path failures, quarantine symlinks and the TEMP environment differ by OS. Current tests cover same-process lock conflicts and actual Windows readback. Use platform-specific subprocess tests or injectable file factories; do not rely on chmod behaving the same under Windows or root in CI. |
| Database fault injection | Secondary failures while closing after SQLException and every failure stage inside a transaction need controllable DAO/connection factories. Real-H2 happy paths, conflicts, query errors and rollback are checked; a mock-only success is not evidence for JDBC behavior. |
| Alternative script runtime | `JythonScriptManager` needs a Python JSR-223 engine; the current runtime supplies Groovy. Decide whether Jython is supported and add its dependency/contract tests, or remove the unused manager. Missing/unsupported engine handling is tested. |
| Dead/internal paths | `FtsMsg` is package-private, has no consumers and exposes no parsed fields. Private unused helpers such as `ADRCommand.findLink`, `EchoMgr.createArea` and `PluginManager.removePlugin` need a decision to remove or expose them. Several branches check null in constructor-initialized fields (`FTNAddr`, `Password`), empty private config lists, or private equality checks never called by HashMap for identity/null. Reflection can force those branches, but would not validate public functionality. |
| Other reachable gaps | Some metadata/accessor combinations, malformed routing inputs and transport error branches still need tests. They remain in the report and are not classified as impossible. There is no claim of complete functional coverage or 100% branch coverage. |

The next useful work is to agree on unfinished features and add controlled fault
injection for timeouts/I/O, while continuing ordinary reachable-case tests from
the report. A blanket 100% threshold would currently include dead paths and
unfinished stubs; it should not substitute for those decisions.

## Measured snapshot (2026-10-14)

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
