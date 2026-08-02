# P13-07 Packaged Long-Running Service

Status: ready; packaged long-running service evidence is closed for the
Phase 13 required blocker.

## Landed In Phase 13

- `NodeExecutionModePolicy` defines the `packaged_long_running` launch surface
  for `interactive_long_running`. The default `script` surface still rejects
  service mode before user code.
- `ProjectConfig.node.executionMode` and `.autojs6-node-launch.json`
  `executionMode` are parsed and carried through `NodeProjectLaunchOptions`,
  `NodeProjectLaunchSource`, `ProjectRequest`, `ProjectLaunchPlan`,
  diagnostics, and `EmbeddedScriptRequest`.
- `AssetsProjectLauncher` merges packaged `project.json` Node defaults with
  the internal launch descriptor. A mismatch between project and descriptor
  execution modes fails closed before service start.
- Packaged inrt Node projects that opt in with
  `node.executionMode=interactive_long_running` route through
  `NativeNodeEmbeddedScriptService.startPackagedLongRunning(...)` instead of
  the one-shot script engine path.
- Packaged lifecycle smoke now runs inside that `packaged_long_running`
  runtime and verifies `autojs6:lifecycle` checkpoint, read, clear, and
  post-clear read behavior with `launchSurface=packaged_long_running`.
- The foreground notification Stop action now uses
  `NativeNodeEmbeddedInteractiveStopReceiver` and `PendingIntent.getBroadcast`
  so Android 15 background-service-start policy does not block the packaged
  Stop path.
- Focused packaged smoke grants packaged runtime permissions including
  `POST_NOTIFICATIONS`, verifies the foreground notification Stop action, and
  uses an ordered shell broadcast to the exported
  `NativeNodeEmbeddedInteractiveShellReceiver`. That receiver is protected by
  `android.permission.DUMP`, accepts query/Stop requests from `adb shell`, and
  leaves the notification Stop receiver non-exported. The smoke observes
  stopped status with `stopRequested=true`, records `NodeCrashEvidenceStore`
  clean-exit evidence, and verifies the clean Stop is not collected as
  next-launch crash evidence.
- The same focused smoke removes the packaged Activity task with
  `am stack remove <taskId>`, verifies `NativeNodeEmbeddedScriptService`
  receives `onTaskRemoved`, writes stopped status with `stopRequested=true`,
  marks the active crash marker as `clean_exit`, and confirms task-removal
  cleanup is not collected as next-launch crash evidence.
- A subsequent running-service `am force-stop` is still collected on the next
  cold launch as `autojs6-node-phase13-next-launch-crash-bundle-v1`, preserving
  crash recovery while intentional Stop/task-removal exits stay clean.
- The packaged foreground path reuses the existing embedded Node project
  preprocessor, so encrypted project entries, CommonJS static preload,
  `moduleSources`, runtime module sources, `argv`, `env`, package script/bin
  bootstrap, and `timeoutMs` stay aligned with normal packaged Node execution.
- P13-45/P13-52 now record packaged metadata and disclosure policy:
  packaged `node.executionMode`/capability fields remain review metadata,
  unreviewed packaged high-privilege surfaces default to
  `deny_without_reviewed_disclosure_manifest`, and disclosure UI expansion is a
  future packaged-surface promotion item rather than a P13-07 blocker.

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

The gate is expected to report `ready` with zero blockers and zero policy
violations.

## Device Evidence

### Sony Android 12

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

### Xiaomi Android 15

Focused instrumentation:

```powershell
$env:ANDROID_SERIAL='968e9f18'
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest `
  "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.apkbuilder.EmbeddedNodePackagedApkSmokeTest#installAndRunCrashEvidenceEmbeddedNodePackagedApk" `
  "-Pandroid.testInstrumentationRunnerArguments.autojs.embeddedNode.packagedSmokeTemplateOverride=true" `
  "-Pautojs.nodejs.test.deviceProfile=full"
```

Result: passed on Xiaomi 23046RP50C / Android 15.

Evidence files:

```text
build/reports/nodejs/phase13/p13-07-packaged-smoke-android15-gradle-20260701-161727.out.log
app/build/outputs/androidTest-results/connected/debug/flavors/app/TEST-23046RP50C - 15-_app-app.xml
```

JUnit XML result:

```text
tests=1 failures=0 errors=0 skipped=0
testcase=installAndRunCrashEvidenceEmbeddedNodePackagedApk
time=134.352s
```

Observed Android 15 logcat evidence:

```text
AssetsProjectLauncher: autojs6.inrt.node_project_packaged_long_running.v1 executionId=... started=true state=starting ... packageName=org.autojs6.packaged.node.crashevidence.smoke
NativeNodeEmbeddedScriptService: node.runtime.state executionId=... CREATED -> STARTING reason=service.runScript ... processName=org.autojs6.packaged.node.crashevidence.smoke:nodejs_embedded
NativeNodeEmbeddedScriptService: node.runtime.state executionId=... STARTING -> RUNNING reason=native.enter ...
NodeLifecycleCheckpointStore: checkpoint.write schema=autojs6-node-lifecycle-checkpoint-v1 ... launchSurface=packaged_long_running executionMode=interactive_long_running ... checkpointBytes=47
NodeLifecycleCheckpointStore: checkpoint.read schema=autojs6-node-lifecycle-checkpoint-v1 ... exists=true ... launchSurface=packaged_long_running executionMode=interactive_long_running
NodeLifecycleCheckpointStore: checkpoint.clear schema=autojs6-node-lifecycle-checkpoint-v1 ... deleted=true ... launchSurface=packaged_long_running executionMode=interactive_long_running
NodeLifecycleCheckpointStore: checkpoint.read schema=autojs6-node-lifecycle-checkpoint-v1 ... exists=false ... launchSurface=packaged_long_running executionMode=interactive_long_running
NativeNodeEmbeddedScriptService: runtime.heartbeat.stop ... reason=interactive.stop
NodeCrashEvidenceStore: marker.terminal schema=autojs6-node-phase13-crash-marker-v1 ... state=clean_exit reason=native_process.exit ... exitCode=0 ... packageName=org.autojs6.packaged.node.crashevidence.smoke
NativeNodeEmbeddedScriptService: interactive.task_removed ... processName=org.autojs6.packaged.node.crashevidence.smoke:nodejs_embedded
NativeNodeEmbeddedScriptService: runtime.heartbeat.stop ... reason=interactive.task_removed
NodeCrashEvidenceStore: marker.terminal schema=autojs6-node-phase13-crash-marker-v1 ... state=clean_exit reason=native_process.exit ... exitCode=0 ... packageName=org.autojs6.packaged.node.crashevidence.smoke
NodeCrashEvidenceStore: marker.collect schema=autojs6-node-phase13-next-launch-crash-bundle-v1 ... state=collected_after_restart ... launchSurface=packaged_long_running executionMode=interactive_long_running
```

The Android 15 permission/background-start matrix is covered by this focused
smoke for the P13-07 acceptance surface: notification permission is requested
and granted, foreground notification Stop is delivered through a non-exported
broadcast receiver instead of a background `startService`, task-removal cleanup
is clean, and force-stop crash collection remains active. Broader disclosure UI
and high-privilege packaged capability prompts remain future packaged-surface
promotion work, not a blocker for the packaged long-running service mode.

## Closure

P13-07 remains closed once the verifier reports `ready`, the
`packaged_long_running` final blocker registry entry is `closed`, and the
Phase 13 summary reflects zero P13-07 blockers.
