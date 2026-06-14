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

Threshold checks are off by default. Enable them only for controlled local regression runs:

```powershell
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest `
  "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeEmbeddedPerformanceInstrumentationTest" `
  "-Pandroid.testInstrumentationRunnerArguments.autojs.nodejs.perf.failOnThreshold=true" `
  "-Pandroid.testInstrumentationRunnerArguments.autojs.nodejs.perf.maxColdWallMs=5000" `
  "-Pandroid.testInstrumentationRunnerArguments.autojs.nodejs.perf.maxWarmWallMs=3000" `
  "-Pandroid.testInstrumentationRunnerArguments.autojs.nodejs.perf.maxAppRssDeltaAfterDestroyKb=65536" `
  "-Pandroid.testInstrumentationRunnerArguments.autojs.nodejs.perf.maxServiceRssKb=262144"
```

Available arguments:

- `autojs.nodejs.perf.failOnThreshold`
- `autojs.nodejs.perf.maxColdWallMs`
- `autojs.nodejs.perf.maxWarmWallMs`
- `autojs.nodejs.perf.maxAppRssDeltaAfterDestroyKb`
- `autojs.nodejs.perf.maxServiceRssKb`

Use threshold mode for severe regression detection only. Do not use one device's raw numbers as a universal release policy.

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
