# AutoJs6 Runtime Prewarm Pool

Status: process-only MVP. S8-35 adds an explicit one-shot prewarm for the
`:nodejs_embedded` Android service process. It does not pool a V8 isolate,
Node environment, module cache, bridge provider, or user execution state.

## Goal

Reduce first-script latency where Android service process startup is a visible
part of the cold path, while keeping each script on a clean normal Embedded Node
bootstrap path.

## Current Scope

The current API is Kotlin-only and explicit:

```kotlin
NativeNodeEmbeddedScriptService.prewarmRuntime(context, ttlMs)
```

Behavior:

- starts `NativeNodeEmbeddedScriptService` in the `:nodejs_embedded` process;
- keeps the foreground service process alive for a bounded TTL;
- loads no user source, no modules, and no runtime module sources;
- grants no bridge capabilities and starts no bridge provider session;
- loads no Node/V8 environment before the real execution starts;
- consumes the prewarm state once when the next script enters the service;
- runs the script through the existing normal bootstrap path;
- stops and exits the process after that script using the existing lifecycle;
- expires on TTL or Android low-memory signals.

Current limits:

- default TTL: `5000 ms`
- maximum TTL: `30000 ms`
- maximum prewarmed processes: `1`
- prewarm type: process-only

## Non-Goals

S8-35 does not implement:

- a reusable V8 isolate pool;
- a reusable Node `Environment`;
- a preloaded AutoJs6 bootstrap, module graph, or user module cache;
- pre-granted permissions or providers;
- pre-created bridge file transport directories;
- long-running runtime reuse;
- multiple simultaneous embedded process slots;
- packaged APK automatic prewarm;
- a hidden background service without foreground disclosure.

Those remain future design tasks because a contaminated or partially initialized
Node environment could leak cwd, env, capabilities, module cache, providers, or
open resources across scripts.

## Lifecycle

Prewarm starts with a foreground service action and returns a
`PrewarmRuntimeResult` to the caller. If no script consumes it before the TTL,
the service stops and schedules process exit.

When a script arrives during the prewarm TTL:

1. the prewarm state is marked `consumed`;
2. the prewarm expiry callback is cancelled;
3. the script request is prepared and validated as usual;
4. a normal execution slot is acquired;
5. bridge providers, runtime modules, cwd, env, and capabilities are installed
   only for that request;
6. the existing native `runEmbeddedScriptInCurrentProcess()` path creates the
   Node runtime and runs the generated bootstrap;
7. the process exits after the script, so the consumed prewarm cannot be reused.

Low-memory callbacks discard an unused prewarm and stop the service if there is
no active or queued execution.

## Diagnostics

Prewarm results and script result native values use:

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

`status=ready` means the process-only prewarm is waiting. `status=consumed`
means the next script used the prewarmed service process. `status=not_requested`
means the script started with no active prewarm.

## Security Requirements

Prewarm must not change Safe Node authority:

- no bridge provider is created during prewarm;
- no capability manifest is read during prewarm;
- no user cwd, source path, env, module source, runtime module source, or
  execution id is stored in the prewarm state;
- no Node/V8 isolate, context, or environment is reused;
- no module cache can cross from one script to another;
- default concurrent execution policy remains one active execution per
  `:nodejs_embedded` process;
- normal script preparation, scoped fs, env filtering, builtin policy, and
  resource cleanup still run for the consuming script.

## Tests

Focused S8-35 coverage:

```powershell
$env:ANDROID_SERIAL='QV710AF65F'
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeRuntimePrewarmPoolInstrumentationTest" "-Pautojs.nodejs.test.deviceProfile=sony"
```

The test verifies:

- process-only prewarm returns `ready`;
- Node runtime is not loaded during prewarm;
- no capabilities or user modules are loaded during prewarm;
- the next one-shot script consumes the prewarmed service process;
- the consumed script still receives a clean cwd/env/bootstrap;
- expired prewarm state does not contaminate the next execution.

