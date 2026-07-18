# AutoJs6 Node Runtime Performance

This page documents the Embedded Node startup/memory benchmark and the R5
external runtime plugin performance gate. Device load, thermal state, ABI, and
background services still need to be controlled when recording results.

## Instrumentation Benchmark

Run the focused benchmark against the sibling Node runtime artifacts on a
connected arm64 device. The default sample plan is one cold execution followed
by 29 runtime-warm executions in the same persistent process runtime:

```powershell
$nodeRuntimeProject = (Resolve-Path ..\AutoJs6-Plugin-NodeJs-Runtime).Path
$gradleArgs = @(
    ':app:connectedAppDebugAndroidTest'
    '-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeEmbeddedPerformanceInstrumentationTest#embeddedStartupAndMemoryReportIsWritten'
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

Use `autojs.nodejs.perf.coldCycles` and
`autojs.nodejs.perf.runtimeWarmSamplesPerCold` to change the sample plan. Set
the former to `30` and the latter to `1` when independent cold and
runtime-warm 30-sample distributions are required.

The performance class calls the embedded service directly. That makes native
lifecycle measurements repeatable, but deliberately bypasses the
JavaScript-engine request-preparation path used by a real script file. Run the
engine-path regression class whenever working-directory traversal or module
preloading changes:

```powershell
$gradleArgs[1] = '-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeEmbeddedEngineRequestPreparationInstrumentationTest'
& .\gradlew.bat @gradleArgs
exit $LASTEXITCODE
```

R4 makes the zero-scan path the default for every source shape. Request
preparation may preload a statically resolved dependency closure as an
optimization, but arbitrary business logic, dynamic `require()`, ESM, and
dynamic `import()` do not opt into a working-directory walk. Encrypted source
is requested only after the runtime resolves an exact module candidate.

The benchmark writes a JSON report on the device under:

```text
/sdcard/Download/AutoJs6/nodejs/perf/
```

Pull reports to the host with:

```powershell
.\gradlew.bat --console=plain :app:pullNodeEmbeddedPerformanceReports
```

Host reports are written to:

```text
app/build/reports/nodejs/perf/
```

If a device or test environment uses a different report directory, pass:

```powershell
.\gradlew.bat --console=plain :app:pullNodeEmbeddedPerformanceReports -Pautojs.nodejs.perf.deviceDir=<device-dir>
```

## R4 Request-Preparation Gate

`NodeEmbeddedEngineRequestPreparationInstrumentationTest` runs through the
real file engine path. Its R4 corpus places at least 4000 unrelated
JavaScript-like files under the working root, then executes non-trivial
module-free logic, static and dynamic CommonJS, package resolution, missing
modules, partial ESM, and plain and encrypted module variants. Every case
requires:

- scan decision `on_demand_provider`;
- zero examined paths, walked files, candidates, header probes, and full-file
  reads in the legacy scan diagnostics;
- `embedded_script.module_provider.legacy_directory_scan_ran=false`;
- non-negative provider request, resolved-source, decrypted-source, byte, and
  elapsed-time diagnostics;
- encrypted cases to touch only the actual dependency closure: exact static
  CommonJS preload may use zero provider requests, while on-demand cases report
  only the candidates actually resolved.

This focused class is a correctness and regression gate, not a replacement for
30-sample device evidence. R4 acceptance uses runtime-warm real-file runs for
representative module-free, static CommonJS, and ESM entries. Module-free and
single-module request-preparation p95 must be at most 50 ms; adding the
unrelated-file corpus may increase request-preparation p95 by at most 20 ms;
runtime-warm end-to-end p95 must remain at most 400 ms. Record commands and
results using [R4_ACCEPTANCE_SAMPLING.md](R4_ACCEPTANCE_SAMPLING.md). An
unfilled template or a successful compile/instrumentation command is not
device acceptance evidence.

## R5 External Runtime Plugin Gate

`NodeRuntimePluginR5InstrumentationTest` measures the external Binder backend
through the real host bridge. It first runs a primer that includes discovery,
contract negotiation, binding, and process startup as needed. It then records
at least 30 runtime-warm executions that must keep the same plugin PID, process
runtime generation, and host session generation while reusing both candidate
and Binder caches.

Run the focused gate with an installed, enabled, authorized, ABI-compatible
runtime plugin:

```powershell
$nodeRuntimeProject = (Resolve-Path ..\AutoJs6-Plugin-NodeJs-Runtime).Path
$gradleArgs = @(
    ':app:verifyNodeR5RuntimePluginGate'
    '-Pautojs.nodejs.test.deviceProfile=sony'
    '-Pautojs.nodejs.releaseFlavor=nodeMini'
    '-Pautojs.nodejs.backend=pluginOptional'
    '-Pautojs.nodejs.embedded.script.enabled=true'
    '-Pautojs.nodejs.embedded.persistentRuntime=true'
    '-Pautojs.nodejs.releaseAbis=arm64-v8a'
    "-Pautojs.nodejs.pluginProjectDir=$nodeRuntimeProject"
    "-Pautojs.nodejs.runtimeJniLibs=$nodeRuntimeProject\app\src\main\jniLibs"
    '-Pandroid.testInstrumentationRunnerArguments.autojs.nodejs.r5.pluginWarmSamples=30'
    '-Pksp.incremental=false'
    '--console=plain'
)

& .\gradlew.bat @gradleArgs
exit $LASTEXITCODE
```

The instrumentation argument
`autojs.nodejs.r5.pluginWarmSamples` defaults to and is clamped to at least
`30`; the maximum is `200`. The gate requires runtime-warm wall-clock p50 at
most `250 ms`, p95 at most `400 ms`, and every warm
`embedded_script.runtime_plugin.bind_elapsed_ms` value to be `0`.

The class prints JSON with schema
`autojs6-node-runtime-plugin-r5-performance-v1`. It separates primer discovery
and bind time from warm discovery and bind distributions and includes plugin
package, PID, process runtime generation, and host session generation. A
successful build or an unarchived single run is not Roadmap acceptance
evidence; preserve the live device output and JSON through the R5 acceptance
process. The completed `arm64-v8a`, `armeabi-v7a`, and `x86_64` matrix recorded
8/8 tests with zero skips and at least 30 warm samples per ABI; archived
commands, distributions, device identity, and artifact hashes are in
[R5_ACCEPTANCE_SAMPLING.md](R5_ACCEPTANCE_SAMPLING.md).

## Embedded Report Schema

Reports use schema `autojs6-node-performance-v4`. They include device and ABI
metadata, the exact sample plan, nearest-rank distributions, raw run records,
process snapshots, Node memory/resource payloads, and pull instructions.

The `phaseTimings` object separates four scopes:

- `clientService` contains an end-to-end client attempt total, host segments,
  and callback boundary aggregates. Session acquisition contains discovery,
  declaration checks, bind, process-ready, and runtime-ready work. Result
  payload encoding ends before Android parcel serialization and Binder
  transfer. Client decoding starts at the result callback, excludes Binder
  transfer, and can include lazy Bundle unparceling plus field parsing.
- `nativeLifecycle` contains process-runtime initialization, per-execution
  isolate and Environment setup, Node bootstrap, module preload, user-script
  execution, event-loop work, result creation, and teardown. Bootstrap and
  script intervals are nested in other native intervals, so they must not be
  summed.
- `engine` contains JavaScript-engine request preparation, CommonJS preload,
  and the legacy encrypted runtime-module scan timing. Under R4 the scan phase
  is normally `skipped`, while on-demand provider diagnostics are reported
  separately as request count, resolved/decrypted source count, source bytes,
  elapsed time, denial reason, and legacy-scan use. Engine values are normally
  missing in the direct-service benchmark and are never synthesized as zero.
- `hostPreparation` contains native-library load and compile-cache
  prepare/prune timings when the corresponding operation runs.

The report marks all timing scopes as non-additive. Totals, boundary
aggregates, host segments, and nested native intervals overlap. Status values
such as `skipped`, `not_applicable`, or a missing value are excluded from
duration distributions instead of being counted as zero.

`cold` means the first execution after explicit persistent-session retirement.
`runtimeWarm` requires the same PID, pool slot, session generation, and
process-runtime generation, with a monotonically increasing execution
sequence. The old R1 one-shot `appSideWarm` concept is not a current mode.

`serviceProcessStartTimeMs` remains a compatibility trend metric measured as
cold `runScript` wall time. It includes service start, bind, native load,
script execution, and result return; it is not a pure Android process-start
timestamp.

## Threshold Mode

Threshold checks are off by default. Enable them only for controlled local
regression runs. For the current GA gate, pin the device to Sony XQ-AT72
`QV710AF65F` and use the severe-regression limits from the
`sony-xq-at72-arm64-ga` profile:

```powershell
$env:ANDROID_SERIAL = "QV710AF65F"
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest `
  "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeEmbeddedPerformanceInstrumentationTest" `
  "-Pandroid.testInstrumentationRunnerArguments.autojs.nodejs.perf.failOnThreshold=true" `
  "-Pandroid.testInstrumentationRunnerArguments.autojs.nodejs.perf.maxColdWallMs=6000" `
  "-Pandroid.testInstrumentationRunnerArguments.autojs.nodejs.perf.maxRuntimeWarmWallMs=4000" `
  "-Pandroid.testInstrumentationRunnerArguments.autojs.nodejs.perf.maxAppRssDeltaAfterDestroyKb=131072" `
  "-Pandroid.testInstrumentationRunnerArguments.autojs.nodejs.perf.maxServiceRssKb=393216"
```

Available arguments:

- `autojs.nodejs.perf.failOnThreshold`
- `autojs.nodejs.perf.maxColdWallMs`
- `autojs.nodejs.perf.maxRuntimeWarmWallMs`
- `autojs.nodejs.perf.maxColdP50Ms`
- `autojs.nodejs.perf.maxRuntimeWarmP50Ms`
- `autojs.nodejs.perf.maxRuntimeWarmP95Ms`
- `autojs.nodejs.perf.maxResultToReturnP95Ms`
- `autojs.nodejs.perf.maxAppRssDeltaAfterDestroyKb`
- `autojs.nodejs.perf.maxServiceRssKb`

`autojs.nodejs.perf.maxWarmWallMs` remains a legacy alias for the runtime-warm
single-sample limit. Use threshold mode for severe regression detection only.
Do not use one device's raw numbers as a universal release policy.

## Release Threshold Profiles

Warning thresholds are advisory review lines for mild regressions. Failure
thresholds are severe-regression lines that should fail a release gate when
the corresponding instrumentation argument exists. The timing evidence in
this section predates R2 persistent-runtime semantics and remains as historical
GA guardrail evidence; use `R3_ACCEPTANCE_SAMPLING.md` for current latency
acceptance.

Current evidence:

- Sony XQ-AT72 Android 12 arm64 benchmark: cold run `1130 ms`, warm run `1050 ms`, first script `132 ms`, warm script `127 ms`, app RSS retained after destroy `17540 KB`, embedded service RSS about `253004 KB`, app thread delta `-4`, app fd delta `0`.
- Sony XQ-AT72 full memory stress: no failures; largest per-scenario RSS sample range `67940 KB`, thread sample range `4`, fd sample range `1`.
- OnePlus OPD2413 Android 15 arm64 reduced stress: no failures; largest per-scenario RSS sample range `4348 KB`, thread sample range `3`, fd sample range `1`. This is historical evidence only; do not use OnePlus OPD2413 for current unattended gates because APK installation requires manual confirmation.

### `sony-xq-at72-arm64-ga`

Use this as the primary Node Profile v1 GA release gate profile.

| Metric | Report field or source | Warning | Failure | Enforcement |
| --- | --- | ---: | ---: | --- |
| Cold embedded startup | `metrics.coldRunWallMs` | `3000 ms` | `6000 ms` | `autojs.nodejs.perf.maxColdWallMs` |
| Runtime-warm startup | `metrics.runtimeWarmRunWallMs` | `2500 ms` | `4000 ms` | `autojs.nodejs.perf.maxRuntimeWarmWallMs` |
| Packaged startup | launch-to-success diagnostic marker, excluding build/install | `30000 ms` | `60000 ms` | release review until packaged smoke exports a startup metric |
| App RSS after simple script | `metrics.appRssDeltaAfterDestroyKb` | `65536 KB` | `131072 KB` | `autojs.nodejs.perf.maxAppRssDeltaAfterDestroyKb` |
| Embedded service RSS after simple script | max service snapshot `rssKb` | `307200 KB` | `393216 KB` | `autojs.nodejs.perf.maxServiceRssKb` |
| RSS after stress | per-scenario final-minus-baseline runaway delta | `196608 KB` | `262144 KB` | `autojs.nodejs.leak.maxRssDeltaKb` |
| Java heap after stress | per-scenario final-minus-baseline runaway delta | `98304 KB` | `131072 KB` | `autojs.nodejs.leak.maxJavaHeapDeltaKb` |
| Native heap after stress | per-scenario final-minus-baseline runaway delta | `98304 KB` | `131072 KB` | `autojs.nodejs.leak.maxNativeHeapDeltaKb` |
| Thread count after stress | per-scenario final-minus-baseline runaway delta | `16` | `24` | `autojs.nodejs.leak.maxThreadDelta` |
| FD count after stress | per-scenario final-minus-baseline runaway delta | `48` | `64` | `autojs.nodejs.leak.maxFdDelta` |

The cold and warm failure lines are deliberately several times higher than the current Sony benchmark sample so thermal or background noise does not fail a normal gate. RSS, thread, and fd failure lines match the existing conservative runaway defaults unless the performance benchmark already exposes a stricter supported argument.

Run the stress gate with the same failure profile:

```powershell
$env:ANDROID_SERIAL = "QV710AF65F"
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest `
  "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeEmbeddedMemoryLeakStressInstrumentationTest" `
  "-Pandroid.testInstrumentationRunnerArguments.autojs.nodejs.leak.maxRssDeltaKb=262144" `
  "-Pandroid.testInstrumentationRunnerArguments.autojs.nodejs.leak.maxJavaHeapDeltaKb=131072" `
  "-Pandroid.testInstrumentationRunnerArguments.autojs.nodejs.leak.maxNativeHeapDeltaKb=131072" `
  "-Pandroid.testInstrumentationRunnerArguments.autojs.nodejs.leak.maxThreadDelta=24" `
  "-Pandroid.testInstrumentationRunnerArguments.autojs.nodejs.leak.maxFdDelta=64"
```

### Historical and Triage Profiles

`oneplus-opd2413-arm64-historical` is a comparison profile, not a current gate profile. Its Android 15 reduced stress report used the same runaway failure defaults (`262144 KB` RSS, `131072 KB` Java/native heap, `24` threads, `64` fds) and passed with small deltas. Because the device requires manual APK install confirmation, use the report only to spot obvious cross-device regressions or to justify keeping the default failure lines broad.

`low-power-arm64-triage` is for exploratory devices such as older or thermally constrained phones. Treat warnings as notes and do not fail GA solely on this profile unless the Sony GA profile also fails or the report shows monotonic runaway growth. Suggested failure lines are `10000 ms` cold startup, `7000 ms` warm startup, `131072 KB` app RSS retained after simple script, and the same runaway failure defaults as the Sony profile.

Packaged startup is currently a release-review threshold, not a separate machine-enforced argument. A severe packaged startup regression is any packaged smoke run where the app installs and launches but no success or diagnostic marker appears within `60000 ms` from launch, or where the existing packaged smoke method times out. When packaged smoke starts exporting a launch-to-marker metric, wire the failure line above into the aggregate gate.

## Reading Results

Use cold and runtime-warm timings comparatively:

- cold run: first execution after explicit persistent-session retirement
- runtime-warm run: execution in the same PID, slot, session generation, and
  process-runtime generation as its cold execution
- app RSS after destroy: checks whether the app process keeps unexpected memory after service stop
- service RSS: useful when the service process is still readable after result return
- `process_runtime.library_load_ms`: actual process-runtime native-library load
  measured only on the acquire that initialized the persistent runtime

Recommended comparison method:

1. Run the benchmark three to five times on the same device and ABI.
2. Compare medians, not single samples.
3. Keep screen, power, and background load consistent.
4. Record the report JSON with any lifecycle or native-load change.

## Compile Cache And Startup Snapshot Modes

S8-33 enables the module compile cache as an internal fail-soft optimization.
It may improve repeated module compilation without changing isolate, context,
or environment creation. R3 reports compile-cache preparation and pruning
separately. Pruning runs with the existing bounded policy; a time-based
in-process throttle was rejected because primary and overflow service
processes share the cache root and cannot safely coordinate such a throttle.

R3 measured immutable execution-source construction at about 0-1 ms on the
focused Sony run, so the wrapper is not process-cached. Mutable project
descriptors, dependency graphs, and source maps also remain request-scoped.
Packaged host native-library resolution is the only new R3 process cache, and
its key includes the package/version, install paths, release flavor, backend,
runtime slot/target, ABI set, and library name.

S8-34 keeps bootstrap startup snapshots disabled behind a design gate. The
current runtime does not generate, package, load, or request a snapshot blob;
normal bootstrap remains the active path and the required fallback path.

R3 did not change this decision. The measured startup chain did not justify
introducing snapshot generation, versioning, capability invalidation, and
rollback risk while normal persistent-runtime bootstrap remains available.

S8-35 adds an explicit process-only prewarm. It may reduce Android service
process cold-start cost, but it does not preload Node/V8 or user modules and
does not replace the snapshot modes below.

S8-36 records the current available performance matrix with:

```powershell
$env:ANDROID_SERIAL = "QV710AF65F"
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodePerformanceMatrixInstrumentationTest" "-Pautojs.nodejs.test.deviceProfile=sony"
.\gradlew.bat --console=plain :app:pullNodeEmbeddedPerformanceReports
```

Reports use schema `autojs6-node-phase8-performance-matrix-v1` and compare
only modes that the current runtime can truthfully execute. Unavailable modes
are included in the JSON with an explicit reason.

The current mode matrix is:

| Mode | Current v1.1 status | Notes |
| --- | --- | --- |
| no cache / no snapshot | unavailable | Compile cache is an internal default after S8-33; no user or instrumentation switch disables it. |
| compile cache only | supported | Internal app-private module compile cache |
| snapshot only | unavailable | Requires S8-34 runtime implementation |
| compile cache plus snapshot | unavailable | Requires S8-34 runtime implementation |
| compile cache plus process prewarm | supported separate axis | S8-35 explicit process-only prewarm; not a snapshot substitute |

Measured rows record native load, environment creation, bootstrap, first local
module, first fake-transport bridge call, RSS snapshots, compile-cache size,
and runtime timeline event count. `packagedStartupMs` is recorded as
`not_measured_in_embedded_instrumentation` until packaged smoke exports a
launch-to-marker metric.

Measure process-only prewarm as a separate axis: prewarm off versus explicit
prewarm consumed. Report `embedded_script.prewarm_pool.status`, process id
reuse, wall time, native load timing, and RSS so service-process wins are not
confused with V8 or module-cache wins.

Detailed S8-36 coverage is recorded in
`docs/nodejs/reports/phase8/PERFORMANCE_MATRIX.md`.

## Memory Leak Stress

Run the extended leak stress gate on a connected device:

```powershell
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeEmbeddedMemoryLeakStressInstrumentationTest"
.\gradlew.bat --console=plain :app:pullNodeEmbeddedPerformanceReports
```

Reports use schema `autojs6-node-memory-leak-stress-v1` and are pulled to the same host directory:

```text
app/build/reports/nodejs/perf/
```

Default scenarios:

- 100 simple Embedded Node scripts
- 100 scoped `fs` callback scripts
- 100 `fs.promises` scripts when available
- 100 fake-transport `toast`/`app` bridge scripts
- 50 fake image-handle create/use/recycle cycles
- 10 timeout scripts
- 10 uncaught exception scripts

Recorded metrics:

- RSS from `/proc/self/status`
- Java heap and committed Java heap
- native heap from `Debug.getNativeHeapAllocatedSize()`
- total/dalvik/native/other PSS from `Debug.MemoryInfo`
- thread count from `/proc/self/task`
- fd count from `/proc/self/fd`

The stress gate does not require exact memory stability. It fails only when a metric keeps rising across samples and exceeds a conservative threshold. Defaults:

- `autojs.nodejs.leak.maxRssDeltaKb=262144`
- `autojs.nodejs.leak.maxJavaHeapDeltaKb=131072`
- `autojs.nodejs.leak.maxNativeHeapDeltaKb=131072`
- `autojs.nodejs.leak.maxThreadDelta=24`
- `autojs.nodejs.leak.maxFdDelta=64`

Useful local overrides:

```powershell
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest `
  "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeEmbeddedMemoryLeakStressInstrumentationTest" `
  "-Pandroid.testInstrumentationRunnerArguments.autojs.nodejs.leak.iterations=20" `
  "-Pandroid.testInstrumentationRunnerArguments.autojs.nodejs.leak.imageIterations=10" `
  "-Pandroid.testInstrumentationRunnerArguments.autojs.nodejs.leak.faultIterations=3"
```

Set `autojs.nodejs.leak.failOnRunaway=false` to collect a report without failing the test. Use that only for exploratory local profiling.

Phase 13 full heavy resource stress consumes long-duration reports at:

- `build/reports/nodejs/final-soak/quick-soak-8h.json`
- `build/reports/nodejs/final-soak/final-soak-24h.json`

Each report must provide a duration (`durationHours`, `durationMs`, or
parseable start/end timestamps), RSS, Java heap, native heap, thread count, fd
count, and bridge diagnostics. `verifyNodePhase13FullHeavyResourceStress`
keeps the final blocker open when either report is missing, below duration,
missing comparable metrics, or reporting failures, monotonic leaks, orphan
processes, or unrecoverable runtime state. The current Phase 13 closure uses
the archived Xiaomi 23046RP50C / Android 15 quick and final reports at these
canonical paths.

The verifier also contains contract fixtures under
`tools/nodejs/phase13/fixtures/full-heavy-resource-stress/`. They prove that the
parser accepts the intended 8h/24h report shapes and rejects under-duration,
missing-metric, leak/orphan, or unrecoverable runtime samples. These fixtures
are not performance evidence and must not be copied into
`build/reports/nodejs/final-soak/`.

Use the real-soak runner for the device runs. It pins the Android serial,
starts the focused final-soak instrumentation, pulls the JSON report from the
device, and optionally archives a completed report:

```powershell
node tools/nodejs/phase13/run-full-heavy-resource-soak.js --kind quick --serial QV710AF65F --device-profile sony --archive
node tools/nodejs/phase13/run-full-heavy-resource-soak.js --kind final --serial QV710AF65F --device-profile sony --archive
```

For smoke validation, pass `--duration-ms` and leave `--archive` off so
under-duration reports cannot replace canonical 8h/24h evidence.

Use `tools/nodejs/phase13/archive-full-heavy-resource-soak.js` to copy only
validated real soak outputs into `build/reports/nodejs/final-soak/`. Run it with
`--dry-run` first when checking a candidate report.
