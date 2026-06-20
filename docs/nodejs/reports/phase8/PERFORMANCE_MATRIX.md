# Phase 8 Performance Matrix

S8-36 adds a focused device report for the current startup and cache evidence
without widening the Safe Node Profile runtime policy.

## Scope

`NodePerformanceMatrixInstrumentationTest` writes
`autojs6-node-phase8-performance-matrix-v1` JSON reports under:

```text
/sdcard/Download/AutoJs6/nodejs/perf/
```

Pull the reports with the existing performance report task:

```powershell
.\gradlew.bat --console=plain :app:pullNodeEmbeddedPerformanceReports
```

## Focused Gate

Run the matrix on the Sony gate device:

```powershell
$env:ANDROID_SERIAL='QV710AF65F'
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodePerformanceMatrixInstrumentationTest" "-Pautojs.nodejs.test.deviceProfile=sony"
```

The test records:

- native library load duration when exposed by the runtime;
- Node environment creation timing;
- bootstrap timing;
- first local module load timing;
- first fake-transport bridge call timing through `device.isScreenOn()`;
- service/app RSS snapshots when `/proc` is readable;
- app-private compile-cache directory size and file count;
- optional runtime timeline event count;
- prewarm status and consumed process id for the explicit process-only prewarm
  axis.

## Mode Classification

The report keeps the S8-36 matrix explicit:

| Mode | Current status | Reason |
| --- | --- | --- |
| `no-cache-no-snapshot` | unavailable | S8-33 makes module compile cache an internal default and intentionally exposes no user or instrumentation disable switch. |
| `compile-cache-only` | measured | Normal one-shot execution with the app-private compile cache enabled. |
| `snapshot-only` | unavailable | S8-34 keeps startup snapshots design-gated; no snapshot blob is generated or loaded. |
| `compile-cache-plus-snapshot` | unavailable | Snapshot runtime support is not implemented, so interaction cannot be measured. |
| `compile-cache-plus-process-prewarm` | measured separate axis | S8-35 process-only prewarm starts the service process but does not load Node/V8, bridge providers, modules, env, cwd, or capabilities. |

Process prewarm is intentionally not treated as a snapshot substitute. It is a
separate startup axis that can reduce Android service-process cold-start cost
without changing isolate, environment, bootstrap, or module-cache behavior.

## Packaged Startup

This focused embedded instrumentation report does not export or install a
packaged APK, so `packagedStartupMs` is recorded as
`not_measured_in_embedded_instrumentation`. Packaged startup remains covered by
the packaged smoke gates until those gates export a launch-to-marker metric that
can be merged into this matrix schema.

## Safety Boundary

S8-36 does not add runtime switches for cache or snapshots. The compile-cache
controls remain internal, user env override keys remain filtered, and startup
snapshot paths remain absent. The matrix reports unavailable modes explicitly
instead of inferring them from warm runs or process prewarm.
