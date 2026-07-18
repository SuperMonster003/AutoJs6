# R2 Persistent Runtime Acceptance Sampling

This note defines the focused device sampling added for R2. It does not replace
the longer leak, crash, packaged APK, or final soak gates.

## Lifecycle and rollback boundaries

The native bridge defaults to one-shot mode. The AutoJs6 embedded service must
explicitly opt in through the `autojs.nodejs.embedded.persistentRuntime` build
property; setting it to `false` retains process-only prewarm, one-shot process
exit, and the asynchronous 650 ms slot-settle safety window. This avoids
silently changing the external runtime plugin lifecycle before R5.

Idle TTL, main-process memory trim, and explicit app exit use the session
manager's drain, native terminal-shutdown, unbind, and process-exit handshake.
Force-stop and app upgrade are hard process boundaries: Android terminates the
old processes, no teardown callback is required, and no session/runtime state
is persisted. A later app start creates a fresh manager epoch and runtime.

## Latency percentiles

`NodeEmbeddedPerformanceInstrumentationTest` uses nearest-rank percentiles and
writes `embedded-node-performance-*.json` under
`/sdcard/Download/AutoJs6/nodejs/perf/`.

The default focused run executes 30 scripts without retiring the session between
runtime-warm samples:

- 1 cold service/runtime execution;
- 29 runtime-warm executions in the same PID, process-pool slot, session
  generation, and process-runtime generation;
- one process-global initialization and a sequence increment of exactly one for
  every execution;
- p50/p95 for client wall time, native result elapsed time, and result callback
  to client return time.

Use 30 independent cold cycles when an acceptance-grade cold p50/p95 is needed.
Each cycle retires the previous persistent session once, then records one cold
and one runtime-warm execution:

```powershell
$nodeRuntimeProject = (Resolve-Path ..\AutoJs6-Plugin-NodeJs-Runtime).Path
$gradleArgs = @(
    ':app:connectedAppDebugAndroidTest'
    '-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeEmbeddedPerformanceInstrumentationTest#embeddedStartupAndMemoryReportIsWritten'
    '-Pandroid.testInstrumentationRunnerArguments.autojs.nodejs.perf.coldCycles=30'
    '-Pandroid.testInstrumentationRunnerArguments.autojs.nodejs.perf.runtimeWarmSamplesPerCold=1'
    '-Pandroid.testInstrumentationRunnerArguments.autojs.nodejs.perf.failOnThreshold=true'
    '-Pandroid.testInstrumentationRunnerArguments.autojs.nodejs.perf.maxColdP50Ms=1000'
    '-Pandroid.testInstrumentationRunnerArguments.autojs.nodejs.perf.maxRuntimeWarmP50Ms=250'
    '-Pandroid.testInstrumentationRunnerArguments.autojs.nodejs.perf.maxRuntimeWarmP95Ms=400'
    '-Pandroid.testInstrumentationRunnerArguments.autojs.nodejs.perf.maxResultToReturnP95Ms=50'
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

The JSON `samplePlan` states the actual cold, runtime-warm, and total execution
counts. A single cold sample is explicitly labelled as non-acceptance-grade;
its p50/p95 values must not be presented as a 30-run cold percentile.

The 2026-07-18 Sony XQ-DQ72 acceptance run passed all configured thresholds:

| Metric | Samples | p50 | p95 | Maximum |
| --- | ---: | ---: | ---: | ---: |
| Cold wall | 30 | 612 ms | 633 ms | 676 ms |
| Runtime-warm wall | 30 | 161 ms | 196 ms | 210 ms |
| Cold callback-to-return | 30 | 2 ms | 3 ms | 3 ms |
| Runtime-warm callback-to-return | 30 | 2 ms | 3 ms | 3 ms |

## Persistent resource growth

`NodeEmbeddedLifecycleStressInstrumentationTest#runSimpleScript100Times`
executes 100 fresh isolates in one persistent service process. There is no
per-execution service settle. The test always requires:

- stable PID, pool slot, session generation, and process-runtime generation;
- exactly one process-runtime initialization;
- a contiguous execution sequence;
- clean native, execution, service, and bridge cleanup;
- zero retained bridge resources;
- client result delivery without the retired one-shot settle window.

The report samples the service after iteration 1, every 10 iterations, and the
final iteration. It records RSS, total/Java/native/other PSS, threads, file
descriptors, Node heap and external memory, libuv handles, and active bridge
resources. The complete 100 compact per-run payloads are also retained.

```powershell
$nodeRuntimeProject = (Resolve-Path ..\AutoJs6-Plugin-NodeJs-Runtime).Path
$gradleArgs = @(
    ':app:connectedAppDebugAndroidTest'
    '-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeEmbeddedLifecycleStressInstrumentationTest#runSimpleScript100Times'
    '-Pandroid.testInstrumentationRunnerArguments.autojs.nodejs.r2.resource.iterations=100'
    '-Pandroid.testInstrumentationRunnerArguments.autojs.nodejs.r2.resource.sampleEvery=10'
    '-Pandroid.testInstrumentationRunnerArguments.autojs.nodejs.r2.resource.failOnGrowth=true'
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

The resource report is named
`embedded-node-r2-resource-growth-*.json`. Growth analysis requires at least
five samples and reports a failure only when a metric both exceeds its bounded
delta and follows the monotonic-runaway shape. Thresholds can be overridden
with the `autojs.nodejs.r2.resource.max*` instrumentation arguments recorded in
the test source. Raw samples and thresholds remain in the JSON even when
`failOnGrowth` is false.

The 2026-07-18 XQ-DQ72 run completed all 100 executions in PID 28916,
session/runtime generation 1, with execution sequence 1 through 100. Across 11
resource snapshots, thread count changed from 24 to 20, fd count from 107 to
105, Node heap used from 7304 KiB to 7280 KiB, libuv handles stayed at 2, and
active bridge resources stayed at 0. All required metrics were available and
the report detected no monotonic growth.

Pull both report types with:

```powershell
adb pull /sdcard/Download/AutoJs6/nodejs/perf/. app/build/reports/nodejs/perf
```
