# S8-35 Runtime Prewarm Pool

Status: process-only MVP complete

S8-35 adds an explicit prewarm path for the Android
`NativeNodeEmbeddedScriptService` process. It is intentionally process-only:
AutoJs6 does not reuse a V8 isolate, Node environment, bridge provider,
module cache, cwd, env, or user resource state.

## Implementation

New service behavior:

- `ACTION_PREWARM_RUNTIME` starts the `:nodejs_embedded` foreground service and
  keeps it alive for a bounded TTL.
- `NativeNodeEmbeddedScriptService.prewarmRuntime(context, ttlMs)` exposes the
  explicit Kotlin caller API.
- Default TTL is `5000 ms`; maximum TTL is `30000 ms`.
- Android low-memory callbacks discard unused prewarm state.
- The next `ACTION_RUN_SCRIPT` consumes the prewarm once and cancels the expiry
  callback.
- The consuming script still uses the normal request preparation, validation,
  bridge provider setup, scoped fs, env filtering, and native bootstrap path.
- After the script exits, existing service cleanup calls `stopSelf()` and
  schedules process exit, so a consumed prewarm cannot be reused.

## Diagnostics

Prewarm result and script native values record:

- `embedded_script.prewarm_pool.enabled`
- `embedded_script.prewarm_pool.status`
- `embedded_script.prewarm_pool.started`
- `embedded_script.prewarm_pool.consumed`
- `embedded_script.prewarm_pool.process_only`
- `embedded_script.prewarm_pool.runtime_loaded_before_execution`
- `embedded_script.prewarm_pool.capabilities_granted`
- `embedded_script.prewarm_pool.user_modules_loaded`
- `embedded_script.prewarm_pool.ttl_ms`
- `embedded_script.prewarm_pool.age_ms`
- `embedded_script.prewarm_pool.reason`
- `embedded_script.prewarm_pool.policy`

The current status values used by the focused gate are:

- `ready`
- `consumed`
- `not_requested`
- `busy`
- `timeout`
- `start_failed`

## Security Boundary

S8-35 is not a reusable runtime pool. During prewarm:

- no Node/V8 environment is loaded;
- no user source, path, env, module graph, or runtime module source is stored;
- no bridge provider or capability provider is created;
- no permissions are granted;
- no resources are opened on behalf of a script.

This keeps the prewarm path compatible with the existing single-active
execution model and avoids cross-script contamination. Reusable V8/Node
environment pooling remains deferred until it can prove no cwd, env, module
cache, provider, permission, timer, fd, watcher, or bridge resource leaks
between scripts.

## Gate

Focused Sony gate:

```powershell
$env:ANDROID_SERIAL='QV710AF65F'
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeRuntimePrewarmPoolInstrumentationTest" "-Pautojs.nodejs.test.deviceProfile=sony"
```

Static build gate:

```powershell
.\gradlew.bat --console=plain :app:compileAppDebugKotlin :app:compileAppDebugAndroidTestKotlin
```

## Performance Follow-Up

S8-35 does not replace S8-36. The performance matrix should measure process-only
prewarm separately from module compile cache and startup snapshots because it
affects Android service/process cold start rather than Node module compilation
or V8 context creation.

