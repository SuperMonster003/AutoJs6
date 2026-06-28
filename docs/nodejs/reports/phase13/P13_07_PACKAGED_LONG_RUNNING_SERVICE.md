# P13-07 Packaged Long-Running Service

Status: partial packaged dispatch landed; final blocker remains open.

## Landed In Phase 13

- `NodeExecutionModePolicy` now defines the `packaged_long_running` launch
  surface for `interactive_long_running`. The default `script` surface still
  rejects service mode before user code.
- `ProjectConfig.node.executionMode` and `.autojs6-node-launch.json`
  `executionMode` are parsed and carried through `NodeProjectLaunchOptions`,
  `NodeProjectLaunchSource`, `ProjectRequest`, `ProjectLaunchPlan`, diagnostics,
  and `EmbeddedScriptRequest`.
- `AssetsProjectLauncher` merges packaged `project.json` Node defaults with the
  internal launch descriptor. A mismatch between project and descriptor
  execution modes fails closed before service start.
- Packaged inrt Node projects that opt in with
  `node.executionMode=interactive_long_running` route through
  `NativeNodeEmbeddedScriptService.startPackagedLongRunning(...)` instead of the
  one-shot script engine path.
- Focused packaged crash evidence smoke now installs a packaged
  `interactive_long_running` APK with a fresh inrt template override, verifies
  `launchSurface=packaged_long_running`, force-stops the package, and verifies
  next-launch crash marker collection.
- Packaged lifecycle smoke now runs inside that `packaged_long_running`
  runtime and verifies `autojs6:lifecycle` checkpoint, read, and clear
  behavior with `launchSurface=packaged_long_running` before the forced
  restart evidence is collected. The QV710AF65F focused run records
  `NodeLifecycleCheckpointStore` `checkpoint.write`, `checkpoint.read`,
  `checkpoint.clear`, and post-clear `checkpoint.read exists=false` logcat
  markers before next-launch crash marker collection.
- The packaged foreground path reuses the existing embedded Node project
  preprocessor, so encrypted project entries, CommonJS static preload,
  `moduleSources`, runtime module sources, `argv`, `env`, package script/bin
  bootstrap, and `timeoutMs` stay aligned with normal packaged Node execution.
- Interactive status snapshots now preserve the actual launch surface. Packaged
  sessions report `launchSurface=packaged_long_running` while keeping the shared
  foreground notification, Stop action, heartbeat/budget summaries, checkpoint
  metadata, and restart policy `never`.
- The `long-running-service` sample now documents packaged long-running as a
  partial opt-in path.

## Host Gate

Gradle task:

```powershell
.\gradlew.bat --console=plain :app:verifyNodePhase13PackagedLongRunningService
```

Node script:

```text
tools/nodejs/phase13/verify-packaged-long-running-service.js
```

Generated reports:

```text
build/reports/nodejs/phase13-packaged-long-running-service.json
build/reports/nodejs/phase13-packaged-long-running-service.md
```

The gate is expected to report `partial` until full packaged foreground
notification Stop action evidence, Android permission/background policy
coverage, task-removal cleanup, and quota/resource stress coverage land.

## Device Evidence

Focused instrumentation:

```powershell
$env:ANDROID_SERIAL='QV710AF65F'
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest `
  "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.apkbuilder.EmbeddedNodePackagedApkSmokeTest#installAndRunCrashEvidenceEmbeddedNodePackagedApk" `
  "-Pandroid.testInstrumentationRunnerArguments.autojs.embeddedNode.packagedSmokeTemplateOverride=true" `
  "-Pautojs.nodejs.test.deviceProfile=sony"
```

Result: passed on QV710AF65F / Sony XQ-AT72 / Android 12 with a fresh
`inrt-v6.7.1-alpha4-universal.apk` template override pushed to
`/data/local/tmp/autojs6-inrt-debug-template.apk`.

Observed logcat evidence:

```text
NodeLifecycleCheckpointStore: checkpoint.write schema=autojs6-node-lifecycle-checkpoint-v1 ... launchSurface=packaged_long_running executionMode=interactive_long_running ... checkpointBytes=47
NodeLifecycleCheckpointStore: checkpoint.read schema=autojs6-node-lifecycle-checkpoint-v1 ... exists=true ... launchSurface=packaged_long_running executionMode=interactive_long_running
NodeLifecycleCheckpointStore: checkpoint.clear schema=autojs6-node-lifecycle-checkpoint-v1 ... deleted=true ... launchSurface=packaged_long_running executionMode=interactive_long_running
NodeLifecycleCheckpointStore: checkpoint.read schema=autojs6-node-lifecycle-checkpoint-v1 ... exists=false ... launchSurface=packaged_long_running executionMode=interactive_long_running
NodeCrashEvidenceStore: marker.collect schema=autojs6-node-phase13-next-launch-crash-bundle-v1 ... state=collected_after_restart ... launchSurface=packaged_long_running executionMode=interactive_long_running
```

## Open Before Closure

- Add a full lifecycle packaged APK smoke that observes the foreground
  notification and stops the packaged long-running service through the Stop
  action.
- Record Android API-level behavior for foreground-service start restrictions,
  notification permission denial, missing foreground service permissions/types,
  and background launch limits.
- Add packaged quota/resource stress for timers, bridge calls, provider-owned
  handles, status snapshots, destroy cleanup, and next-launch crash evidence.

P13-07 remains unchecked until these final acceptance items pass.
