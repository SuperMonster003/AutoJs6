# Worker Threads

Status: stable and enabled by default in Node Profile v1.2.

## Runtime Contract

`worker_threads` is compiled into and requested by every Node-enabled Debug and
Release build. It has no Gradle opt-in, project/package override, or packaged-APK
override.

Current wiring:

- BuildConfig: `NODEJS_WORKER_THREADS_ENABLED=true`
- host/plugin request contract v2: `workerThreadsEnabled=true`
- profile snapshot: `featureStates.workerThreads=true`
- capability status: `stable`
- primary instrumentation:
  `NodeWorkerThreadsStableInstrumentationTest`

Both `require("worker_threads")` and
`require("node:worker_threads")` resolve to the supported worker surface. A
missing native Worker implementation on any published ABI is an artifact or
runtime conformance failure; it is no longer represented as a user-selectable
mode.

## Supported Surface

The bounded worker integration covers:

- local CommonJS and ESM worker scripts;
- `workerData`, `isMainThread`, `parentPort`, and `threadId`;
- parent/worker `postMessage()`;
- `MessageChannel` and `MessagePort`;
- validated transfer lists and ArrayBuffer detachment;
- structured-clone-compatible values and stable clone errors;
- `terminate()`, worker exit/error events, startup timeout, and parent-owned
  cleanup;
- scoped worker `fs`/`fs.promises` helpers rooted at the execution working
  directory;
- the AutoJs6 `WorkerPool` helper with bounded queueing, cancellation,
  timeout, close, and reuse behavior;
- packaged local worker graphs.

## Worker Authority

A worker intentionally receives less authority than its parent:

- maximum active workers: 1 unless the runtime profile is deliberately revised;
- message and queue sizes are bounded by the published worker policy;
- source paths remain scoped and cannot escape through relative paths, file
  URLs, package targets, or symlinks;
- AutoJs bridge modules, Android objects, Java interop, shell, controlled/raw
  network access, Inspector, native addons, and nested workers are denied;
- safe pure-JavaScript builtins and scoped filesystem helpers remain available;
- all ports, queues, workers, and pool tasks are closed on completion,
  cancellation, timeout, execution stop, or service destruction.

The main Node process may use other stable capabilities; those capabilities are
not inherited automatically by workers.

## Error And Diagnostic Contract

Stable errors distinguish native unavailability, path escape, bridge denial,
worker limit, message size/queue limits, clone/transfer errors, timeout,
cancellation, and closed-pool submission. The profile reports native
availability, worker limits, permitted builtins, denied authorities, and active
resource counters.

## Verification

```powershell
.\gradlew.bat --console=plain :app:verifyNodeWorkerGate "-Pautojs.nodejs.test.deviceProfile=sony"
```

The gate must run without a capability property and cover native bootstrap,
CommonJS/ESM workers, messages and transfers, `WorkerPool`, scoped paths,
denials, packaged execution, termination, lifecycle cleanup, and memory/thread
stress on every supported ABI.

Historical pre-GA evaluation is retained in
[../reports/WORKER_PRE_GA_EVALUATION.md](../reports/WORKER_PRE_GA_EVALUATION.md).
