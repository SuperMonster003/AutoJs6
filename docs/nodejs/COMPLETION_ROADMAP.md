# AutoJs6 Node.js Completion Roadmap

Last updated: 2026-08-02

This checklist separates two completion targets:

1. **Core / Safe Node completion** means the Node 24.5 runtime, Rhino
   coexistence, routing, module system, controlled bridge, packaging,
   lifecycle, security boundaries, and release gates are production-ready.
2. **Auto.js Pro / Desktop parity** is a later opt-in capability expansion. It
   does not block Core completion.

Permanent Safe Node denials such as native addons, unrestricted child
processes, raw network modules, private bindings, raw Android objects, and a
production Inspector are intentional boundaries rather than unfinished work.

## Current Verified Checkpoint

- [x] Rhino remains the default for ordinary JavaScript and coexists with
  explicit Node routing through directives, extensions, project metadata, and
  encrypted script metadata.
- [x] Embedded Node 24.5 executes through an isolated Android service process,
  V8/libuv, the native bridge, scoped filesystem policy, and the controlled
  AutoJs bridge.
- [x] CommonJS, partial ESM, local dynamic import, package resolution,
  moduleSources, packaged runtime support, diagnostics, and release-gate
  foundations exist.
- [x] The default two-slot scheduler, explicit execution queue, timeout,
  cancellation, crash diagnostics, and long-running execution foundations
  exist.
- [x] The fixed 650 ms process-settle wait no longer blocks delivery of a
  completed one-shot script result.
- [x] The scheduler still retains the process slot asynchronously during that
  650 ms window, preventing a second Node/V8/libuv startup in the service
  process that is still settling.
- [x] The 2026-07-18 Sony XQ-DQ72 focused device run passed the performance
  test and both queue regressions.
- [x] R2 focused acceptance on the same device passed persistent runtime,
  runtime-ready prewarm, process-pool recovery, 30-cycle latency, and
  100-execution resource-growth gates.
- [x] R3 traced the real JavaScript-engine path, fixed the repeated encrypted
  runtime-module scan for the strictly proven trivial module-free path, and added
  non-additive phase distributions for the client, service, native runtime,
  engine request preparation, and host preparation boundaries.
- [x] R4 removes project-wide encrypted-module scanning from the normal
  execution path through request-scoped, on-demand module resolution.
- [x] R5 makes `nodePlugin` plus the required `plugin` backend the default
  main-app delivery, adds a persistent external runtime with fresh execution
  state and zero-scan workspace archive transport v2, and passes the focused
  live gate on `arm64-v8a`, `armeabi-v7a`, and `x86_64` with no skipped tests.
- [x] Resolve the narrative status drift between the closed historical blocker
  registry and the current R6 production-evidence requirement.
- [ ] Land the current R6 final-gate and live-provider corrections, recollect
  production evidence for that clean successor source identity, and pass the
  strict final/release gates before declaring Core completion.

The focused performance reports on XQ-DQ72 recorded:

| Build | Slot | Cold wall | App-side warm wall | Native result elapsed, cold/warm | Result callback to return, cold/warm |
| --- | --- | ---: | ---: | ---: | ---: |
| Before result-delivery fix | primary | 3100 ms | 1592 ms | 126 / 100 ms | not instrumented |
| After result-delivery fix | primary | 598 ms | 585 ms | 105 / 101 ms | 4 / 3 ms |

Each row is one cold/warm sample, not a percentile claim. The benchmark now
waits beyond the asynchronous settle window and requires cold and app-side
warm runs to use the same process-pool slot.
The R1 "warm" sample still launched a fresh service process and fresh Node
runtime; it was only warm with respect to app and filesystem caches.

The R2 acceptance run on the same device recorded nearest-rank percentiles:

| R2 metric | Samples | p50 | p95 | Maximum |
| --- | ---: | ---: | ---: | ---: |
| Cold wall | 30 | 612 ms | 633 ms | 676 ms |
| Runtime-warm wall | 30 | 161 ms | 196 ms | 210 ms |
| Cold result callback to client return | 30 | 2 ms | 3 ms | 3 ms |
| Runtime-warm result callback to client return | 30 | 2 ms | 3 ms | 3 ms |

The 100-execution resource gate reused PID 28916 and process-runtime
generation 1 with execution sequence 1 through 100. Eleven snapshots showed
no monotonic growth: thread count changed from 24 to 20, fd count from 107 to
105, Node heap used from 7304 KiB to 7280 KiB, libuv handles stayed at 2, and
active bridge resources stayed at 0.

The focused manager/prewarm/process-pool suites passed 21 tests, and the full
persistent lifecycle stress class passed 16 tests in one final run.

## R1 - Close The One-Shot Result-Delivery Regression

- [x] Move process settling off the caller's return path.
- [x] Export `embedded_script.client.result_to_return_ms`.
- [x] Export whether settling was deferred and the configured delay.
- [x] Fail the performance instrumentation test if result delivery exceeds
  250 ms or the process-settle safety window is not deferred.
- [x] Pass
  `defaultConcurrentExecutionQueuesInsteadOfEngineBusy` and
  `queuedExecutionRunsAfterActiveExecution` on the primary Sony device.
- [x] Retire the planned R1 30-sample app-side-warm one-shot run after R2
  replaced it with 30 cold/runtime-warm samples on the persistent lifecycle;
  the single R1 row remains explicitly non-percentile evidence.
- [x] Run the complete lifecycle stress and process-pool policy classes before
  release, including success, error, timeout, cancellation, service death,
  primary/overflow concurrency, queue full, and queue timeout.

Immediate acceptance criteria:

- `result_to_return_ms <= 250 ms` for cold and app-side warm runs.
- Simple one-shot p50 `<= 1100 ms` on the primary Sony device.
- No new engine-busy result, overlapping service startup, orphan process, or
  queue starvation.

Focused local command for an arm64 device when native runtime artifacts come
from the sibling plugin project:

```powershell
$nodeRuntimeProject = (Resolve-Path ..\AutoJs6-Plugin-NodeJs-Runtime).Path
$gradleArgs = @(
    ':app:connectedAppDebugAndroidTest'
    '-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeEmbeddedPerformanceInstrumentationTest'
    '-Pautojs.nodejs.releaseFlavor=nodeMini'
    '-Pautojs.nodejs.releaseAbis=arm64-v8a'
    "-Pautojs.nodejs.pluginProjectDir=$nodeRuntimeProject"
    "-Pautojs.nodejs.runtimeJniLibs=$nodeRuntimeProject\app\src\main\jniLibs"
    '-Pksp.incremental=false'
    '--console=plain'
)

& .\gradlew.bat @gradleArgs
exit $LASTEXITCODE
```

## R2 - Persistent Process And Process-Global Node Runtime

This is the prerequisite for approaching Auto.js Pro warm-start latency. Pro
keeps its script process and service connection alive and initializes the
process-global Node platform once, while still creating and destroying the
per-script V8 runtime. Before R2, AutoJs6 exited its one-shot service process
and tore down the process-global Node platform after every script.

R2 focused implementation and acceptance are complete. Packaged APK, multi-
device, and long-soak evidence remain release gates in R6 rather than blockers
for the R2 architecture.

- [x] Add a persistent service/session manager with explicit idle TTL,
  trim-memory, and app shutdown behavior. Force-stop and app upgrade are hard
  process boundaries: no teardown callback is assumed, no session state is
  persisted, and the next app process creates a new manager epoch and runtime.
- [x] Initialize `libnode`, `MultiIsolatePlatform`, and other process-global
  Node/V8/libuv state once per service-process lifetime.
- [x] Keep a fresh isolate, context, Node Environment, cwd/env, capabilities,
  module cache, ResourceRegistry, and bridge handles for every execution.
- [x] Never reuse user globals, mutable user module state, pending callbacks,
  timers, or provider-owned resources across scripts.
- [x] Give the primary and overflow slots independent process-global runtimes.
- [x] Quarantine and rebuild a slot after native crash, failed teardown,
  heartbeat loss, or Binder death.
- [x] Upgrade process-only prewarm to runtime-ready prewarm, with the current
  one-shot path retained as a rollback fallback.
- [x] Repeat focused isolation, lifecycle, service-death, process-pool, and
  resource-growth evidence after the lifecycle model changes.

R2 focused verification tracker:

- [x] Compile the app and Android instrumentation sources with the persistent
  runtime/session-manager implementation enabled.
- [x] Make the performance instrumentation compare a cold execution with a
  runtime-warm execution in the same PID and session/runtime generation, with
  one process initialization and a monotonically increasing execution
  sequence.
- [x] Add focused sequential-execution coverage for fresh globals, mutable
  CommonJS state, request environment, timers, bridge request state, and zero
  retained bridge resources while reusing the process-global runtime.
- [x] Add focused runtime-ready prewarm, idle-TTL retirement, explicit
  shutdown, and trim-memory retirement coverage, and make instrumentation
  teardown use `shutdownAllAndAwait` for persistent sessions.
- [x] Pass `NodeEmbeddedPerformanceInstrumentationTest`,
  `NodeEmbeddedPersistentRuntimeInstrumentationTest`, and
  `NodeRuntimePrewarmPoolInstrumentationTest` on the primary device.
- [x] Pass focused process-pool, Binder-death, timeout/recovery, and leak
  checks under the persistent lifecycle.
- [x] Collect 30-run cold/runtime-warm percentiles and the 100-execution
  resource-growth report before accepting the provisional SLO.

Provisional performance SLO for 30 samples per mode on the primary device
(30 cold cycles and 60 executions total):

- Cold p50 `<= 1000 ms`.
- Runtime-ready warm p50 `<= 250 ms` and p95 `<= 400 ms`.
- Result callback to client return p95 `<= 50 ms`.
- 100 sequential executions show no monotonic RSS, Java/native PSS,
  thread, fd, libuv handle, or bridge-resource growth.

All provisional SLOs passed on the 2026-07-18 XQ-DQ72 run. The reproducible
commands and report schema are documented in `R2_ACCEPTANCE_SAMPLING.md`.

Release follow-ups that do not reopen R2:

- [x] Re-run packaged APK and multi-device gates under R6.
- [ ] Re-run the 8-hour and 24-hour leak/soak gates under R6.

## R3 - Measure And Optimize The Remaining Startup Chain

R3 is complete for the focused embedded-runtime scope. The reported Sony
XQ-AT72 regression primarily came from a real file execution spending about
4082 ms preparing the request before its Binder call, while native execution
itself took 144 ms. The working directory contained 4258 files, including 2036
JavaScript files. The R2 direct-service harness bypassed this JavaScript-engine
preparation path, so it could not expose the regression. Final normal-launcher
validation also found that `TRIM_MEMORY_UI_HIDDEN` incorrectly retired the
idle persistent process; R3 now distinguishes that lifecycle signal from
actual memory pressure.

- [x] Report discover/bind, process ready, native load, platform init,
  isolate/Environment creation, bootstrap, module preload, script execution,
  result payload encode/decode, callback delivery, client cleanup, and native
  teardown separately. The report declares totals, boundary aggregates, and
  nested native timings as non-additive.
- [x] Reserve `runtimeWarm` for reuse of the same PID, process-pool slot,
  session generation, and process-runtime generation. The planned
  `appSideWarm` rename applied only to the retired R1 one-shot model and is now
  superseded by the R2 persistent lifecycle; it is not emitted as a current
  benchmark mode.
- [x] Add configurable sample count plus p50/p95 output to the performance
  instrumentation report.
- [x] Cache only measured immutable inputs. Packaged host native-library
  resolution is cached behind explicit schema, package/version, APK/native
  directory, release flavor, backend, runtime slot/target, ABI, and library
  keys. Mutable project descriptors and source graphs remain request-scoped.
- [x] Eliminate repeated package-manager plugin discovery for packaged
  `nodeFull`/`nodeMini` host libraries. The minimal module-free script grammar
  now proves that empty/trivia-only source, the editor's directive-only
  placeholder, and the primitive-console probe need no encrypted fallback
  scan. Every source containing another executable token, including static
  CommonJS graphs, ESM, dynamic or unclassified loaders, and unresolved
  dependencies, retains the conservative, bounded encrypted-header scan.
- [x] Preserve the persistent process when the short-lived file launcher UI
  becomes hidden, while retaining idle-TTL and actual memory-pressure
  retirement. Verify the next run keeps the PID and session generation.
- [x] Evaluate compile cache and startup snapshots from the expanded timeline.
  Compile-cache prepare and prune costs are reported, but no cross-process
  prune throttle is used. Execution-source construction measured about
  0-1 ms and is not cached. Startup snapshots remain disabled; normal
  bootstrap is the required fallback.

The root-cause record, acceptance commands, timing interpretation, and final
Sony result table are maintained in `R3_ACCEPTANCE_SAMPLING.md`. On the final
real-file gate, cold execution took 1577 ms and 30 runtime-warm executions had
p50 316 ms / p95 347 ms, with request preparation p50 5 ms / p95 6 ms.
The directive-only editor placeholder follow-up recorded warm p50 302 ms / p95
322 ms and request-preparation p50 5 ms / p95 7 ms over 30 runs.

## R4 - Remove Project-Wide Module Scans From Normal Execution

R3 added a fail-closed fast path for a deliberately small module-free grammar.
Any other executable token still selects the `conservative_source` fallback,
even when the script cannot load another module. On the XQ-AT72 acceptance
directory, that fallback examined 2562 candidates and added about 2.2 seconds
to every warm request. The scan preserves compatibility with encrypted modules
whose paths may be computed at runtime, but a source-shape whitelist and a
project-wide walk cannot remain the normal execution model.

R4 implementation and primary-device acceptance are complete. The normal path
now declares an on-demand source provider, keeps exact static preloading as an
optional optimization, and reports the legacy directory scan as skipped. The
native script path also omits unrequested probe placeholders before payload
construction so the provider diagnostics do not consume the warm-start budget.

- [x] Make module preparation depend on actual module resolution and encryption
  requirements rather than whether the entry source matches a small list of
  accepted script shapes. Ordinary business logic must never trigger a
  project-wide walk merely because it is non-trivial.
- [x] For statically resolvable CommonJS and ESM graphs, resolve only the actual
  dependency closure. Probe and decrypt only the resolved candidate files;
  unrelated JavaScript files in the working directory must not be enumerated.
- [x] Add a request-scoped, on-demand encrypted module source provider. After
  the runtime resolves a dynamic `require()` or `import()` candidate, the host
  must canonicalize and authorize that exact path, reject escapes and symlink
  drift, probe its encrypted header, and return either decrypted source or an
  explicit not-encrypted/not-found result.
- [x] Define the provider contract so embedded and external-plugin transports
  share the same path, size, count, cancellation, timeout, and error semantics;
  R5 may change connection lifecycle without restoring directory scanning.
- [x] Keep plaintext modules on the runtime's scoped filesystem path and keep
  static preloading only as an optional measured optimization, not a
  correctness requirement for arbitrary scripts.
- [x] Remove the bounded full-directory scan from the default request critical
  path. If a legacy encrypted-project fallback remains temporarily necessary,
  require an explicit compatibility signal and expose its use in diagnostics;
  never infer it merely from arbitrary executable source.
- [x] Preserve source-size and aggregate budgets, sandbox containment, root and
  file identity checks, symlink-escape denial, error identity, and fresh
  per-execution module state for both preloaded and on-demand sources.
- [x] Export module-provider request count, resolved/decrypted source count,
  bytes, elapsed time, denial reason, and whether a legacy directory scan ran.
- [x] Add a corpus covering module-free business logic, expressions and
  multiple statements, static CommonJS, ESM, dynamic `require()`/`import()`,
  computed loaders, plain modules, encrypted modules, missing modules, package
  resolution, cancellation, and provider failure.

R4 acceptance criteria on the primary Sony device:

- Thirty runtime-warm executions of representative module-free, static
  CommonJS, and ESM entries in a directory with at least 4000 unrelated files
  complete with zero project-wide scan candidates.
- Module-free and single-module request-preparation p95 is `<= 50 ms`; adding
  the unrelated-file corpus changes request-preparation p95 by no more than
  20 ms, and runtime-warm end-to-end p95 remains `<= 400 ms`.
- Static and dynamic encrypted-module cases touch only actually resolved
  candidates and retain the existing compatibility and security corpus.
- No source-pattern allowlist is required for acceptable performance: at
  minimum `const`/`let`, expressions, functions, timers, multiple console
  calls, and non-loading async scripts pass the same zero-scan gate.

The final Sony XQ-AT72 run used one primer and 30 qualified runtime-warm
samples for each paired scenario. All 186 executions succeeded, all scan
counters remained zero, and the same primary process slot and runtime
generation advanced execution sequence 1 through 186.

| Fixture | Entry | Preparation p50 / p95 | Warm wall p50 / p95 / max |
| --- | --- | ---: | ---: |
| Baseline | Module-free | 3 / 6 ms | 325 / 371 / 373 ms |
| 4000 unrelated files | Module-free | 3 / 5 ms | 320 / 340 / 345 ms |
| Baseline | Static CommonJS | 6 / 8 ms | 310 / 333 / 343 ms |
| 4000 unrelated files | Static CommonJS | 5 / 7 ms | 305 / 322 / 329 ms |
| Baseline | Static ESM | 4 / 5 ms | 338 / 362 / 369 ms |
| 4000 unrelated files | Static ESM | 3 / 4 ms | 324 / 341 / 349 ms |

The paired preparation-p95 delta was 1 ms for every entry type. The full
commands, immutable build identities, report hashes, correctness corpus, and
reviewed acceptance checklist are maintained in
`R4_ACCEPTANCE_SAMPLING.md`.

## R5 - Align The External Runtime Plugin Backend

- [x] Use `nodePlugin` plus required `plugin` as the default distribution
  policy. Permit `pluginOptional` only for `nodeFull` and `nodeMini`, with one
  embedded fallback only after a proven pre-dispatch failure.
- [x] Cache authorized plugin candidates and package-version validation;
  invalidate on install, uninstall, upgrade, trust, or permission changes.
- [x] Maintain a Binder session instead of discover/bind/unbind for every
  operation; attribute in-flight Binder death to
  `ERR_AUTOJS6_NODE_PLUGIN_EXECUTION_LOST` without replay or fallback, then
  reconnect atomically for a later request.
- [x] Reuse process-global Node/V8 state in the plugin service while preserving
  a fresh per-execution isolate and Environment.
- [x] Run the same corpus through `embedded` and `plugin`, comparing stdout,
  stderr, error identity, active cancellation, lifecycle, and native
  diagnostics. Active cancellation retires the old process and recovery uses a
  new PID.
- [x] Transport only explicitly named workspace inputs through archive contract
  v2, execute from a plugin-private mirror, write outputs back with a final
  deletion tombstone manifest, and keep host-sandbox scan count at zero.
- [x] Verify required-plugin failure, optional-plugin fallback, backpressure,
  contract negotiation, trust/signature checks, crash attribution, and all
  supported ABIs without skipped live tests on the acceptance hardware/emulator
  matrix.
- [x] Meet the R2 runtime-warm p50/p95 target and report discovery/bind overhead
  separately.

The final R5 live gate passed all eight ordered cases with zero skipped tests
for every supported ABI. Each performance result uses 30 runtime-warm samples:

| Plugin ABI | Acceptance device | Tests | Skipped | Warm wall p50 / p95 |
| --- | --- | ---: | ---: | ---: |
| `arm64-v8a` | Sony XQ-AT72 | 8 / 8 | 0 | 231 / 254 ms |
| `armeabi-v7a` | Sony XQ-AT72 | 8 / 8 | 0 | 240 / 265 ms |
| `x86_64` | Android AVD | 8 / 8 | 0 | 182 / 212 ms |

Commands, immutable build identities, artifact and report hashes, diagnostics,
and the semantic evidence matrix are maintained in
[R5_ACCEPTANCE_SAMPLING.md](R5_ACCEPTANCE_SAMPLING.md).

## R6 - Reopen And Close Production Evidence For The New Lifecycle

- [x] Re-run all Phase 11 final reports from current sources.
- [x] Resolve status drift among `FINAL_BLOCKER_REGISTRY`, `final/README.md`,
  `INTEGRATION_STATUS_PRO9_COMPARISON.md`, and this roadmap.
- [x] Reopen lifecycle, crash, memory, ABI, packaged, and soak evidence made
  stale by persistent runtime reuse through a separate fail-closed R6 evidence
  contract without rewriting the historical capability closures.
- [ ] Close the reopened evidence requirement with device and soak reports that
  match the next clean source identity.
- [x] Pass Rhino/Node alternating execution and backend-switch regression.
- [x] Pass the fixed main app and packaged APK matrix: Sony `arm64-v8a`, Sony
  `armeabi-v7a`, a second-OEM physical `arm64-v8a` device, and an x86_64
  emulator.
- [ ] Pass 8-hour quick soak and 24-hour final soak for that same clean source
  identity without monotonic leaks, orphan processes, or unrecoverable slots.
- [ ] Make `verifyNodeFinalIntegrationGate` and `verifyNodeReleaseGate` pass
  from a clean checkout with zero release-blocking reports.

The archived candidate at host commit
`ee97b7909eaf64daf1521e6f840c65c94569d2b5` and runtime-plugin commit
`a904ee2011e93031db1178a0b2ff624b970e460f` reached a complete R6 evidence
payload before the current corrections began. All four canonical device
reports passed 13/13 selectors with zero failures, errors, or skips. The quick
soak ran for 8.000056 hours with 979 cycles, 126 samples, and 49
liveness/recoverability checks; the final soak ran for 24.000057 hours with
2890 cycles, 329 samples, and 145 checks. Both soaks reported clean cleanup,
continuous runtime reuse with fresh execution state, zero derived suspend,
and no monotonic leak, orphan process, or unrecoverable runtime/slot. Against
that clean identity the strict R6 verifier reported `completed`, zero blockers,
and zero policy violations.

The 2026-08-02 final-gate audit then corrected stale Phase 10/13 producer
dependencies, made long-running consume the real Phase 13 JSON decision,
reused the canonical R6 contract for final-soak validation, and corrected
live-provider report discovery. A Sony live-provider run also exposed and
fixed two stale external-plugin availability assertions plus a real WebSocket
destroy race: provider shutdown had cancelled the OkHttp dispatcher before its
queued Close frame could be sent. Every close path now sends the protocol
Close, retains transport quota until a terminal callback or directed cancel,
and selectively cancels a non-ACK peer after a two-second grace period without
blocking provider destroy. The regression includes explicit
close -> non-ACK -> destroy. The focused reruns and the complete 27-case
live-provider gate pass; the complete summary records zero failures/errors,
two policy-approved skips, and zero policy violations.

Those corrections change the host source after the archived `ee97b790`
identity. Live-provider evidence now records clean test-before/test-after host
identity, requires one device/one exact 27-case XML, and snapshots that XML plus
the UTP/aapt-selected app/androidTest APKs, selection logs, and device bindings.
Each unique snapshot retains a hashed manifest and archived summary; canonical
publication is atomic, and the final verifier reparses JUnit and validates real
paths and hashes. Old summaries can no longer cross a commit boundary or be
invalidated by a later connected test overwriting producer outputs. That overwrite/reverification regression
passes with only the expected dirty-identity blockers. The regenerated
non-strict final summary
accepts 10 of 13 subreports. Final-soak, final-provider, and the R6 aggregate
all fail closed on the same underlying condition: the current source is dirty
and therefore cannot authorize archived or newly collected production
evidence. The release gate remains transitively unavailable. Therefore the
checkboxes above remain open until these changes are committed and the live
provider summary, four device reports, both production soaks, and strict
final/release decision are recollected from one clean successor identity.

The R6 implementation checkpoint now includes a real multi-round engine-switch
instrumentation gate, a persistent required-plugin soak harness, an evidence
schema/contract-fixture gate, external-plugin native source/artifact
verification, and a final aggregate that requires the R6 production-evidence
report. Missing hardware, skipped tests, stale host/plugin commits, dirty
worktrees, short soaks, and missing resource metrics remain explicit blockers;
contract fixtures and short smoke runs cannot satisfy production evidence.

The complete non-strict Phase 11 report set was regenerated from the current
sources on 2026-08-02 with zero required-internal blockers and zero policy
violations. Capability truth, focused topology, adapter, crash, package
manager, long-running, multi-instance, Worker/WASI, ABI, and security are
accepted. Final-soak and the R6 aggregate propagate the dirty host identity;
final-provider additionally rejects the live run because its before/after
identity correctly records that these changes were uncommitted during
collection.

The focused `verifyNodeR6EngineSwitchGate` run on Sony XQ-AT72 passed both
selectors with zero failures, errors, or skips on 2026-07-18. This closes only
the alternating/backend-switch regression above; it is not one of the four
complete 13-selector device reports. On 2026-07-19, the clean candidate source
then passed all four canonical device targets at 13/13 with zero failures,
errors, or skips: Sony XQ-AT72 `arm64-v8a` and `armeabi-v7a`, Xiaomi 23046RP50C
`arm64-v8a`, and the API 37 AVD `x86_64`. The real 8-hour and 24-hour soaks and
the clean strict final/release result remain unchecked. Collection commands and
the fail-closed evidence contract are documented in
[R6_ACCEPTANCE_EVIDENCE.md](R6_ACCEPTANCE_EVIDENCE.md).

The same engine-switch selectors were revalidated `2/2` against the fresh R6
host/test/plugin installation on 2026-07-19, together with the required-plugin
permanent Safe Node denial regression at `1/1`.

The explicitly non-archivable 5-second persistent-plugin smoke on the same Sony
device also passed on 2026-07-19 after a fresh plugin build and install: 6
cycles, 6 resource samples, 3 liveness/recoverability checks, clean JUnit, and
verified installed/fresh APK identity. This validates the collection path and
the `/data/user/0` to `/data/data` workspace alias handling only; it does not
close either production-soak checkbox.

The first real 8-hour quick soak completed its full workload on 2026-07-20
with 961 cycles, 108 samples, 49 liveness/recoverability checks, clean JUnit,
stable runtime identity, and no detected leak or cleanup failure. It was
correctly left unarchived because the final verifier treated Android's valid
zero currently allocated native-heap value as a missing metric. The evidence
contract now accepts non-negative allocated native heap only when committed
native heap and native PSS remain positive. At that checkpoint both production
soaks still required fresh execution against the corrected source commit.

Subsequent long-run attempts exposed two harness defects rather than a runtime
leak: a redundant cross-UID `/proc/<pid>/status` observation could fail even
while the plugin's canonical `/proc/self` metrics and Binder identity remained
healthy, and the device could enter deep sleep during the intended continuous
runtime experiment. The latter consumed the request's 15-second wall-clock
module-source budget before native execution resumed. Historical failing runs
retained a stable PID, Binder session, runtime generation, and bounded resource
profile up to those observation/suspend failures.

The 2026-07-24 repair keeps the 15-second per-cycle timeout, holds one bounded
partial wake lock over the exact reported soak interval, proves acquisition,
end-of-soak ownership, zero suspend time, and final release, and revalidates
plugin PID/session liveness before tolerating an unavailable external proc
cross-check. Canonical plugin-self metrics and PSS remain fail-closed. The
runner also uses a dedicated single-use Gradle process with in-process Kotlin
compilation and a per-run daemon registry for both the plugin and connected
builds, and compact cycle records no longer retain the large bridge-health JSON
on every iteration.

An explicitly non-archivable, screen-off Sony XQ-AT72 smoke then passed for
120.743 seconds on 2026-07-24: 100 continuous cycles, 25 resource samples, clean
JUnit and cleanup, `wakeLockHeldElapsedMs=120743`,
`wakeLockHeldUptimeMs=120744`, derived suspend time `0 ms`, and no retained wake
lock after completion. This validates the repaired collection path only; the
clean-source 8-hour and 24-hour production reports remain required.

The first current-source Sony arm64 device-collection attempt on 2026-07-24
then passed all ten main-app selectors but skipped the three packaged-APK
selectors, so the verifier archived nothing. This exposed collector drift after
the 2026-07-23 migration to the remote APK Builder plugin, not a passing device
report. The collector now builds
`:plugins:apk-builder-template:packageRelease` from the same clean host commit
and generated Runtime Kit, enables remote build explicitly, requires the host
release signer and exact Builder service, and proves the installed Builder APK
matches the fresh build and remains unchanged before and after testing. All
four collector Gradle invocations are single-use with in-process Kotlin
compilation and a per-run daemon registry, so another checkout's global
`gradle --stop` cannot terminate the evidence build. The target-ABI
`packageInrtDebug` template and Runtime-Kit-backed Builder release are also
compiled in separate sequential processes: their former combined 4 GB process
failed with `GC overhead limit exceeded`, while both isolated rebuilds passed
from the same clean commit. At that checkpoint all four device reports still
required recollection.

A subsequent clean Sony arm64 collection exercised all 13 selectors with zero
skips and reached 12/13. Its only failure was the packaged crash-evidence
status poll: runtime logcat proved the production package had entered
`RUNNING`, while the test's `run-as` read returned no data because the generated
Release APK was correctly non-debuggable. The production package remains
non-debuggable. Packaged lifecycle evidence now uses an explicit ordered
broadcast receiver protected by the platform `android.permission.DUMP`
permission to query Base64-encoded status and request Stop from `adb shell`;
the notification receiver remains non-exported. The R6 source contract rejects
a return to `run-as`. A direct Sony rerun of the formerly failing selector then
passed 1/1 with zero skips in 76.611 seconds, covering Stop, task removal, clean
terminal markers, forced-process loss, and next-launch crash collection. This
focused dirty-source run validates the repair but is not archivable production
evidence, so a clean complete 13/13 collection is still required.

The following clean Sony arm64 collection on commit `287fe58c` then passed and
archived all 13 selectors with zero failures, errors, or skips. The canonical
report SHA-256 was
`699bd49a722b1ed7e9b24e60091de298b72dbef66c25b99b87bcfce92120993c`.
The clean Sony v7a collection passed all ten main-app selectors but failed all
three packaged selectors before APK installation: the Runtime-Kit-backed
Builder could not find v7a `libjackpal-androidterm5.so` and
`libjackpal-termexec2.so`. The arm64 host APK was correct under the evidence
contract, and the selected v7a Node plugin already supplied its own Node JNI
triplet; the generated Runtime Kit had incorrectly copied a zero-native
universal INRT template and therefore could not provide the target-independent
terminal baseline.

The Runtime Kit generator now injects the four supported Builder ABI copies of
those two terminal libraries directly from the version-controlled
`libtermexec-release.aar`, records their ABI/library policy and hashes, and
continues to reject prepackaged Node JNI. Builder packaging verifies every
entry, and the remote builder accepts required libraries supplied by either the
template or host build inputs. A focused Sony v7a rerun on 2026-07-25 passed
all three packaged selectors with zero failures, errors, or skips in 127.477
seconds; the individual test times were 25.345, 30.358, and 68.943 seconds.
This focused dirty-source run was not archivable. After the Runtime Kit repair
landed, the clean `ee97b790` host / `a904ee20` plugin candidate recollected all
four device targets at 13/13 with zero failures, errors, or skips. It then
completed the 8-hour and 24-hour production soaks described above, making that
candidate's R6 payload complete. The current successor corrections reopen only
the immutable source-identity requirement; they do not erase the archived
candidate result or authorize reusing it for different source bytes.

Core completion command:

```powershell
$gradleArgs = @(
    ':app:verifyNodeFinalIntegrationGate'
    ':app:verifyNodeReleaseGate'
    '-Pautojs.nodejs.test.deviceProfile=sony'
    '--console=plain'
)

& .\gradlew.bat @gradleArgs
exit $LASTEXITCODE
```

Core completion is achieved only when the generated final decision is
`completed`, all required device evidence is non-skipped, permanent denials
remain tested, and the published documentation matches the generated gate
state.

## R7 - Auto.js Pro Compatibility Providers

This phase does not block Core / Safe Node completion.

- [ ] Complete the Phase 16 live provider promotion tasks for UI, overlay,
  accessibility, MediaProjection, image/OCR, tasks, broadcast/IntentTask,
  notifications/power, recorder, and media surfaces.
- [ ] Require each promoted provider to have a real implementation rather than
  fake-only coverage, capability denial, cancellation, cleanup, process-death
  recovery, Android 12 and Android 15+ evidence, packaged behavior, security
  corpus, declarations, docs, disclosure, and rollback.
- [ ] Keep raw Android object access denied and define explicit Safe-profile
  downgrade behavior for every Pro-profile capability.
- [ ] Complete the Phase 16 summary/handoff with zero v1.4 release blockers.

## R8 - Release And Maintenance Handoff

- [ ] Verify native load, adapter exports, and packaged static policy for
  `arm64-v8a`, `armeabi-v7a`, and `x86_64`.
- [ ] Run the Node/Rhino differential corpus, encrypted scripts, projects,
  packaged scripts, and inrt templates.
- [ ] Archive crash symbols, native hashes, performance percentiles, device
  reports, rollback evidence, and the error catalog for the release version.
- [ ] Replace the broad severe-regression performance limits with reviewed
  cold and runtime-ready warm SLOs.
- [ ] Update version metadata, changelog, release notes, capability matrices,
  known issues, and migration guidance from the same verified gate state.

After R6, Core work moves to maintenance. R7 can continue as an independent
compatibility track without reopening Core completion unless it changes a
stable Core contract or lifecycle invariant.
