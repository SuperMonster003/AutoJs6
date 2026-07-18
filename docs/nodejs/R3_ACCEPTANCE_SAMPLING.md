# R3 Startup-Chain Acceptance Sampling

This note records the R3 real-engine regression, the expanded timing contract,
and the focused Sony acceptance procedure. It supplements the R2 persistent
runtime report. It does not replace packaged APK, multi-device, memory-growth,
or 8-hour/24-hour soak gates.

## Baseline And Coverage Gap

The reported script is intentionally minimal:

```javascript
"nodejs";
console.log(1);
```

On Sony XQ-AT72, a traced real-file execution recorded:

| Boundary | Elapsed |
| --- | ---: |
| Activity launch to engine request prepared | about 4082 ms |
| Native execution | 144 ms |
| End-to-end script finish | 4598 ms |

The script working directory contained 4258 files, including 2036 JavaScript
files. Before R3, JavaScript-engine request preparation always collected
encrypted runtime-module sources. It walked the working directory and read an
entire JavaScript-like file before checking its short encrypted-file header.
That work happened before the Binder request and dominated the user-visible
latency.

R2 measured the embedded service directly. That harness was valid for proving
persistent process/runtime reuse, but it bypassed
`NativeNodeEmbeddedJavaScriptEngine` and supplied an already prepared request.
It therefore could not observe the 4082 ms engine-side preparation gap. R3
keeps the direct harness for stable native distributions and adds a separate
real-file engine-path regression suite.

The final normal-launcher validation exposed a second real-entry-path issue.
Closing the short-lived file launcher Activity emits Android's
`TRIM_MEMORY_UI_HIDDEN` level (20). Trim levels are split into foreground and
background ranges rather than forming a single pressure scale, but the R2
manager treated every numeric level at or above `RUNNING_LOW` (10) as memory
pressure. It therefore retired an otherwise healthy idle Node process between
launcher runs. R3 now ignores `UI_HIDDEN`, while retaining retirement for the
foreground low/critical range and the actual background pressure range. A
focused lifecycle test requires the next execution to retain the PID and
session generation after `UI_HIDDEN`.

Do not compare the R2 XQ-DQ72 percentiles directly with the R3 XQ-AT72
percentiles. Device, storage, thermal state, app install state, and background
load are different.

## Request-Preparation Fix And Safety Boundary

A script may skip the encrypted fallback scan only when its complete source
matches the deliberately small module-free grammar used by the minimal
`console` probe. This is a positive, fail-closed proof rather than a loader
keyword blacklist. Every other source, including a static CommonJS graph, ESM,
dynamic code, or an unresolved dependency, keeps the conservative scan.

When a scan is required, it remains bounded and conservative:

- directory and file candidates are resolved under the canonical working root;
- duplicate or outside-root paths are rejected and repeated directory trees
  are pruned;
- only supported JavaScript-like candidates are probed;
- a short header is checked before a bounded payload is read and decrypted;
- existing module count, per-module size, and total-source budgets remain in
  force;
- scan decisions, candidate/header/full-read counts, rejected paths, budget
  failures, and duration are exported as diagnostics.

Android 8.0 and newer use `DirectoryStream`, so directory enumeration itself
is streamed under the path and candidate limits. Android 7.0/7.1 applies the
same logical limits through the legacy file walker, whose per-directory
`listFiles()` allocation is a known residual memory boundary. Keep API 24/25
large-directory stress in the R5 multi-device gate; it does not affect the
Android 12 acceptance device used here.

The focused engine suite covers the zero-scan minimal path and retained
fallback behavior for actual encrypted modules reached through computed or
dynamic loaders. It also covers conservative handling of non-trivial static
CommonJS, ESM, dynamic-code and ambiguous syntax, plus payload bounds,
traversal budgets, and symlink-tree pruning.

## Cache And Snapshot Decisions

R3 caches only packaged host native-library resolution for embedded
`nodeFull`/`nodeMini` builds. The cache key includes its schema, app package and
version, APK and native-library paths, release flavor, runtime backend, runtime
slot and target, supported ABIs, and library name. This avoids repeated plugin
candidate discovery without caching mutable project state. External plugin
candidate/session caching remains R4 work.

Compile-cache preparation and pruning are timed on every applicable request.
A process-local time throttle was evaluated and rejected: primary and overflow
service processes share the cache root, so an uncoordinated throttle could
remove another process's active directory. The existing bounded, fail-soft
prune path remains active.

Execution-source construction measured about 0-1 ms in the focused timeline,
so caching the generated wrapper was not justified. Project descriptors,
dependency graphs, and module sources remain request-scoped. Startup snapshots
remain disabled; normal bootstrap remains the supported fallback.

## Timing And Report Contract

`NodeEmbeddedPerformanceInstrumentationTest` writes schema
`autojs6-node-performance-v4` and uses nearest-rank percentiles. Its default
sample plan is one cold execution followed by 29 runtime-warm executions.
Acceptance-grade cold percentiles require 30 independent cold cycles.

`runtimeWarm` is accepted only when the PID, pool slot, session generation, and
process-runtime generation match the preceding cold execution and execution
sequence increases by one. The R1 `appSideWarm` name described a second
one-shot process and is no longer a current benchmark mode.

Phase distributions have four scopes:

- `clientService`: end-to-end client attempt totals, host segments, and
  callback boundary aggregates;
- `nativeLifecycle`: process runtime, isolate/Environment, bootstrap,
  module-preload, entry-to-terminal script, event-loop, result, and teardown
  intervals;
- `engine`: real JavaScript-engine request preparation and module scan phases;
- `hostPreparation`: native-library load and compile-cache prepare/prune work.

These values are non-additive. Session acquisition contains its discover,
bind, process-ready, and runtime-ready children. Callback-to-return contains
client payload decode and cleanup. Native bootstrap and script execution are
nested in, or can overlap, LoadEnvironment and event-loop intervals. Missing,
`skipped`, and `not_applicable` phases are excluded from distributions rather
than synthesized as zero.

## Reproduction Commands

Set the device and sibling runtime once:

```powershell
$env:ANDROID_SERIAL = "QV710AF65F"
$nodeRuntimeProject = (Resolve-Path ..\AutoJs6-Plugin-NodeJs-Runtime).Path
$nodeBuildArgs = @(
    '-Pautojs.nodejs.releaseFlavor=nodeMini'
    '-Pautojs.nodejs.releaseAbis=arm64-v8a'
    "-Pautojs.nodejs.pluginProjectDir=$nodeRuntimeProject"
    "-Pautojs.nodejs.runtimeJniLibs=$nodeRuntimeProject\app\src\main\jniLibs"
    '-Pksp.incremental=false'
    '--console=plain'
)
```

Run the real-engine preparation regression:

```powershell
$gradleArgs = @(
    ':app:connectedAppDebugAndroidTest'
    '-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeEmbeddedEngineRequestPreparationInstrumentationTest'
) + $nodeBuildArgs

& .\gradlew.bat @gradleArgs
exit $LASTEXITCODE
```

Collect one cold plus 29 runtime-warm samples:

```powershell
$gradleArgs = @(
    ':app:connectedAppDebugAndroidTest'
    '-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeEmbeddedPerformanceInstrumentationTest#embeddedStartupAndMemoryReportIsWritten'
    '-Pandroid.testInstrumentationRunnerArguments.autojs.nodejs.perf.coldCycles=1'
    '-Pandroid.testInstrumentationRunnerArguments.autojs.nodejs.perf.runtimeWarmSamplesPerCold=29'
) + $nodeBuildArgs

& .\gradlew.bat @gradleArgs
exit $LASTEXITCODE
```

For acceptance-grade cold and runtime-warm distributions, collect 30
independent pairs:

```powershell
$gradleArgs = @(
    ':app:connectedAppDebugAndroidTest'
    '-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeEmbeddedPerformanceInstrumentationTest#embeddedStartupAndMemoryReportIsWritten'
    '-Pandroid.testInstrumentationRunnerArguments.autojs.nodejs.perf.coldCycles=30'
    '-Pandroid.testInstrumentationRunnerArguments.autojs.nodejs.perf.runtimeWarmSamplesPerCold=1'
) + $nodeBuildArgs

& .\gradlew.bat @gradleArgs
exit $LASTEXITCODE
```

Pull the generated JSON after each run:

```powershell
.\gradlew.bat --console=plain :app:pullNodeEmbeddedPerformanceReports
```

Finally, run the minimal script as a real file from the normal AutoJs6 script
launcher in the same large working directory used for the baseline. Record the
engine request-preparation diagnostics and the console's end-to-end duration;
the direct-service report alone is not sufficient evidence for this
regression.

## Final Sony XQ-AT72 Results

The direct-service distribution uses 30 independent cold/runtime-warm pairs.
The real-file check uses one force-stopped cold launch followed immediately by
30 normal launcher runs in the same persistent process. All percentiles use
the nearest-rank method.

<!-- FINAL_SONY_RESULTS_START -->

| Mode or boundary | Samples | Minimum | p50 | p95 | Maximum |
| --- | ---: | ---: | ---: | ---: | ---: |
| Cold wall | 30 | 1335 ms | 1437 ms | 1516 ms | 1562 ms |
| Runtime-warm wall | 30 | 354 ms | 378 ms | 440 ms | 460 ms |
| Cold callback-to-return | 30 | 7 ms | 10 ms | 21 ms | 52 ms |
| Runtime-warm callback-to-return | 30 | 6 ms | 8 ms | 20 ms | 30 ms |
| Native total, cold | 30 | 143 ms | 151 ms | 165 ms | 167 ms |
| Native total, runtime-warm | 30 | 139 ms | 144 ms | 153 ms | 159 ms |

| Real-file regression check | Before R3 | After R3 |
| --- | ---: | ---: |
| Engine request preparation | about 4082 ms | cold 18 ms; warm p50 5 ms / p95 6 ms |
| End-to-end minimal script | 4598 ms | cold 1577 ms; warm p50 316 ms / p95 347 ms |

<!-- FINAL_SONY_RESULTS_END -->

Acceptance evidence:

- Direct report:
  `app/build/reports/nodejs/perf/embedded-node-performance-1784338883482.json`
  (`SHA-256 F12CC0C421A924D1D899DB03310093B9500099C2AC1E19055444DFB0C0998666`).
- APK: `autojs6-v6.8.0-alpha7-arm64-v8a.apk`, version code 5219
  (`SHA-256 A14AEC58EE392108D11989234682C1DFFC1679871964694E6DD40BE36F43E426`).
- Runtime: packaged `nodeMini`, arm64-v8a, from the sibling runtime R3 working
  tree based on R2 commit `d5159a2`.
- App implementation: R3 working tree based on R2 commit `c5e5e978`.
- Device: Sony XQ-AT72, serial `QV710AF65F`, Android 12 / API 31,
  arm64-v8a. No external Node.js Runtime plugin was installed.
- Device state: battery 100 percent and 34.2 degrees Celsius before and after
  real-file sampling.
- Working directory: 4258 files / 2036 JavaScript files at baseline; 4259 /
  2037 during acceptance after adding the 26-byte probe file.
- Real-file warm gate: 30/30 successful, zero encrypted-scan candidates, zero
  session retirements, and the embedded process remained PID 2893.
