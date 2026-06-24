# AutoJs6 Embedded Node Performance

This page documents the optional local benchmark for Embedded Node startup and memory diagnostics. It is not a default release gate because device load, thermal state, ABI, and background services can make raw timings noisy.

## Instrumentation Benchmark

Run the focused benchmark on a connected device:

```powershell
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeEmbeddedPerformanceInstrumentationTest"
```

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

## Report Schema

Reports use schema `autojs6-node-performance-v1` and include:

- ABI and device metadata
- cold run wall time
- warm run wall time
- embedded result `elapsedMs`
- `load.duration.ms` when the current runtime exposes it
- app process RSS, PSS, thread count, and fd count before and after script execution
- embedded service process RSS, thread count, and fd count when `/proc/<pid>` is readable
- Node payload from `process.memoryUsage()`, `process.resourceUsage()`, `process.version`, and `process.versions`
- pull instructions for host collection

`serviceProcessStartTimeMs` is currently measured as cold `runScript` wall time. It includes service start/bind overhead, native load, script execution, and result return. Treat it as a stable trend metric, not a pure Android service-start timestamp.

## Threshold Mode

Threshold checks are off by default. Enable them only for controlled local regression runs. For the current GA gate, pin the device to Sony XQ-AT72 `QV710AF65F` and use the failure thresholds from the `sony-xq-at72-arm64-ga` profile:

```powershell
$env:ANDROID_SERIAL = "QV710AF65F"
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest `
  "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeEmbeddedPerformanceInstrumentationTest" `
  "-Pandroid.testInstrumentationRunnerArguments.autojs.nodejs.perf.failOnThreshold=true" `
  "-Pandroid.testInstrumentationRunnerArguments.autojs.nodejs.perf.maxColdWallMs=6000" `
  "-Pandroid.testInstrumentationRunnerArguments.autojs.nodejs.perf.maxWarmWallMs=4000" `
  "-Pandroid.testInstrumentationRunnerArguments.autojs.nodejs.perf.maxAppRssDeltaAfterDestroyKb=131072" `
  "-Pandroid.testInstrumentationRunnerArguments.autojs.nodejs.perf.maxServiceRssKb=393216"
```

Available arguments:

- `autojs.nodejs.perf.failOnThreshold`
- `autojs.nodejs.perf.maxColdWallMs`
- `autojs.nodejs.perf.maxWarmWallMs`
- `autojs.nodejs.perf.maxAppRssDeltaAfterDestroyKb`
- `autojs.nodejs.perf.maxServiceRssKb`

Use threshold mode for severe regression detection only. Do not use one device's raw numbers as a universal release policy.

## Release Threshold Profiles

Warning thresholds are advisory review lines for mild regressions. Failure thresholds are the severe-regression lines that should fail a release gate when the corresponding instrumentation argument exists.

Current evidence:

- Sony XQ-AT72 Android 12 arm64 benchmark: cold run `1130 ms`, warm run `1050 ms`, first script `132 ms`, warm script `127 ms`, app RSS retained after destroy `17540 KB`, embedded service RSS about `253004 KB`, app thread delta `-4`, app fd delta `0`.
- Sony XQ-AT72 full memory stress: no failures; largest per-scenario RSS sample range `67940 KB`, thread sample range `4`, fd sample range `1`.
- OnePlus OPD2413 Android 15 arm64 reduced stress: no failures; largest per-scenario RSS sample range `4348 KB`, thread sample range `3`, fd sample range `1`. This is historical evidence only; do not use OnePlus OPD2413 for current unattended gates because APK installation requires manual confirmation.

### `sony-xq-at72-arm64-ga`

Use this as the primary Node Profile v1 GA release gate profile.

| Metric | Report field or source | Warning | Failure | Enforcement |
| --- | --- | ---: | ---: | --- |
| Cold embedded startup | `metrics.coldRunWallMs` | `3000 ms` | `6000 ms` | `autojs.nodejs.perf.maxColdWallMs` |
| Warm script startup | `metrics.warmRunWallMs` | `2500 ms` | `4000 ms` | `autojs.nodejs.perf.maxWarmWallMs` |
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

Use cold and warm timings comparatively:

- cold run: first benchmark execution after service settle
- warm run: second execution after the app process has already exercised the embedded path
- app RSS after destroy: checks whether the app process keeps unexpected memory after service stop
- service RSS: useful when the service process is still readable after result return
- `load.duration.ms`: current native bridge load measurement when present

Recommended comparison method:

1. Run the benchmark three to five times on the same device and ABI.
2. Compare medians, not single samples.
3. Keep screen, power, and background load consistent.
4. Record the report JSON with any lifecycle or native-load change.

## Compile Cache And Startup Snapshot Modes

S8-33 enables the module compile cache as an internal fail-soft optimization.
It may improve repeated module compilation without changing isolate, context,
or environment creation.

S8-34 keeps bootstrap startup snapshots disabled behind a design gate. The
current runtime does not generate, package, load, or request a snapshot blob;
normal bootstrap remains the active path and the required fallback path.

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
processes, or unrecoverable runtime state.
