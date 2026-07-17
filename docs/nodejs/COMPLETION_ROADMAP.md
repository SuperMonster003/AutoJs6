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
The current "warm" sample still launches a fresh service process and fresh
Node runtime; it is only warm with respect to app and filesystem caches.

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
- [ ] Run the complete lifecycle stress and process-pool policy classes before
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
per-script V8 runtime. AutoJs6 currently exits its one-shot service process and
tears down the process-global Node platform after every script.

- [ ] Add a persistent service/session manager with explicit idle TTL,
  trim-memory, force-stop, app-upgrade, and shutdown behavior.
- [ ] Initialize `libnode`, `MultiIsolatePlatform`, and other process-global
  Node/V8/libuv state once per service-process lifetime.
- [ ] Keep a fresh isolate, context, Node Environment, cwd/env, capabilities,
  module cache, ResourceRegistry, and bridge handles for every execution.
- [ ] Never reuse user globals, mutable user module state, pending callbacks,
  timers, or provider-owned resources across scripts.
- [ ] Give the primary and overflow slots independent process-global runtimes.
- [ ] Quarantine and rebuild a slot after native crash, failed teardown,
  heartbeat loss, or Binder death.
- [ ] Upgrade process-only prewarm to runtime-ready prewarm, with the current
  one-shot path retained as a rollback fallback.
- [ ] Repeat isolation, lifecycle, crash, packaged APK, and soak evidence after
  the lifecycle model changes.

Provisional performance SLO for 30 executions on the primary device:

- Cold p50 `<= 1000 ms`.
- Runtime-ready warm p50 `<= 250 ms` and p95 `<= 400 ms`.
- Native result available to engine-finished p95 `<= 50 ms`.
- 100 sequential executions show no monotonic RSS, native heap, Java heap,
  thread, fd, libuv handle, or bridge-resource growth.

## R3 - Measure And Optimize The Remaining Startup Chain

Do this after R2 so effort targets the persistent architecture rather than the
temporary one-shot lifecycle.

- [ ] Report discover/bind, process ready, native load, platform init,
  isolate/Environment creation, bootstrap, module preload, script execution,
  result marshalling, and teardown separately.
- [ ] Rename the existing second one-shot sample to `appSideWarm`; reserve
  `runtimeWarm` for reuse of the same process-global runtime.
- [ ] Add configurable sample count plus p50/p95 output to the performance
  instrumentation report.
- [ ] Cache only immutable descriptor, dependency, and bootstrap inputs with
  explicit version and capability keys.
- [ ] Eliminate repeated plugin discovery, descriptor parsing, module-source
  scans, and immutable bootstrap construction when measurements justify it.
- [ ] Evaluate compile cache or startup snapshots only after timeline evidence
  identifies them as material, and keep a non-snapshot fallback.

## R4 - Align The External Runtime Plugin Backend

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

## R5 - Reopen And Close Production Evidence For The New Lifecycle

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

## R6 - Auto.js Pro Compatibility Providers

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

## R7 - Release And Maintenance Handoff

- [ ] Verify native load, adapter exports, and packaged static policy for
  `arm64-v8a`, `armeabi-v7a`, and `x86_64`.
- [ ] Run the Node/Rhino differential corpus, encrypted scripts, projects,
  packaged scripts, and inrt templates.
- [ ] Archive crash symbols, native hashes, performance percentiles, device
  reports, rollback evidence, and the error catalog for the release version.
- [ ] Replace the broad severe-regression performance limits with reviewed
  cold, app-side warm, and runtime-ready warm SLOs.
- [ ] Update version metadata, changelog, release notes, capability matrices,
  known issues, and migration guidance from the same verified gate state.

After R5, Core work moves to maintenance. R6 can continue as an independent
compatibility track without reopening Core completion unless it changes a
stable Core contract or lifecycle invariant.
