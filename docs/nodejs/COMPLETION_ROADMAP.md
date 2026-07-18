# AutoJs6 Node.js Completion Roadmap

Last updated: 2026-07-18

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
- [ ] Re-run the final aggregate gates before declaring Core completion. The
  blocker registry is closed, but some narrative status documents still
  describe older blocked states.

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
- [ ] Run at least 30 simple-script samples and publish p50/p95 for cold and
  app-side warm one-shot execution.
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

- [ ] Re-run packaged APK and multi-device gates under R6.
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

- [ ] Decide and document whether `plugin` is an optional backend, the default
  distribution backend, or both through separate release flavors.
- [ ] Cache authorized plugin candidates and package-version validation;
  invalidate on install, uninstall, upgrade, trust, or permission changes.
- [ ] Maintain a Binder session instead of discover/bind/unbind for every
  operation; reconnect atomically after Binder death.
- [ ] Reuse process-global Node/V8 state in the plugin service while preserving
  a fresh per-execution isolate and Environment.
- [ ] Run the same corpus through `embedded` and `plugin`, comparing stdout,
  stderr, error identity, cancellation, lifecycle, and native diagnostics.
- [ ] Verify required-plugin failure, optional-plugin fallback, backpressure,
  contract negotiation, trust/signature checks, crash attribution, and all
  supported ABIs without skipped live tests on release devices.
- [ ] Meet the R2 runtime-warm p50/p95 target and report discovery/bind overhead
  separately.

## R6 - Reopen And Close Production Evidence For The New Lifecycle

- [ ] Re-run all Phase 11 final reports from current sources.
- [ ] Resolve status drift among `FINAL_BLOCKER_REGISTRY`, `final/README.md`,
  `INTEGRATION_STATUS_PRO9_COMPARISON.md`, and this roadmap.
- [ ] Reopen lifecycle, crash, memory, ABI, packaged, and soak evidence made
  stale by persistent runtime reuse, then close it with new reports.
- [ ] Pass Rhino/Node alternating execution and backend-switch regression.
- [ ] Pass main app and packaged APK gates on Sony, a second OEM device, and an
  x86_64 emulator.
- [ ] Pass 8-hour quick soak and 24-hour final soak without monotonic leaks,
  orphan processes, or unrecoverable slots.
- [ ] Make `verifyNodeFinalIntegrationGate` and `verifyNodeReleaseGate` pass
  from a clean checkout with zero release-blocking reports.

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
