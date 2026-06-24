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

The gate is expected to report `partial` until packaged install/run evidence,
lifecycle restore/cleanup evidence, and Android permission/background policy
coverage land.

## Open Before Closure

- Add an install-and-run packaged APK smoke that builds a project with
  `node.executionMode=interactive_long_running`, starts it from the packaged
  app, observes the foreground notification, then stops it through the Stop
  action.
- Prove `autojs6:lifecycle` checkpoint, explicit restore, and cleanup from
  packaged mode, not only from the main app interactive helper.
- Record Android API-level behavior for foreground-service start restrictions,
  notification permission denial, missing foreground service permissions/types,
  and background launch limits.
- Add packaged quota/resource stress for timers, bridge calls, provider-owned
  handles, status snapshots, destroy cleanup, and next-launch crash evidence.

P13-07 remains unchecked until these final acceptance items pass.
