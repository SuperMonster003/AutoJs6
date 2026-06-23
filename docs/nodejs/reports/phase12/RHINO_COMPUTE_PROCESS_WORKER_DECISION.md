# Rhino Compute/Process Worker Decision

Status: P12-22 deferred replacement API decision.

P12-22 decides whether Phase 12 should add a stronger compute/process-worker
replacement for Rhino `threads` users. The answer is no for Phase 12:
AutoJs6 does not promote a new runtime API, does not rename Rhino `threads` to
a Node worker facade, and does not use default `worker_threads` as the
migration target.

## Decision

Defer the compute/process-worker replacement API until worker and execution-slot
promotion evidence exists.

- No `require("threads")`, `compat.threads`, global `threads`, or global
  `sync`.
- No default `worker_threads`, `require("worker_threads")`, or
  `require("node:worker_threads")` migration path.
- No Phase 12 `compute_worker`, `process_worker`, `worker`, or `threads`
  compatibility runtime surface is added.
- `worker_computation` remains `reserved` with no supported launch surface.
- `second_execution_slot` remains open in the final blocker registry.
- `threads.__module`, `threads.start`, `threads.pool`, `threads.interrupt`,
  `threads.disposable`, and `threads.sync` remain `blocked_by_design`.

Future work may evaluate a separately named API such as a compute worker or
process worker, but that API must not reuse the `threads` name and must not
claim same-runtime Rhino thread parity.

## Why Phase 12 Defers

The current worker/process evidence is not strong enough for a new script
author API:

| Area | Current state | P12-22 result |
| --- | --- | --- |
| Native worker availability | `worker_threads` preflight can still report `ERR_AUTOJS6_WORKER_DISABLED`. | Cannot promote a worker-backed API. |
| Default worker policy | `autojs.nodejs.worker_threads.experimental=false` and release/project overrides are denied. | Keep workers internal/default-off. |
| `worker_computation` execution mode | `reserved`, `supportedLaunchSurfaces = emptySet()`. | Cannot expose as a launch target. |
| `second_execution_slot` | Open for default scheduler, packaged behavior, per-slot crash evidence, and stress promotion. | Cannot treat process slots as solved. |
| Packaged worker source graph | Only an internal native-available smoke exists, skipped when native workers are unavailable. | Cannot promise packaged worker behavior. |
| Stress and cleanup | Worker stress and cleanup gates exist but skip before runnable worker creation on native-unavailable builds. | Cannot claim resource maturity. |

## Required Future API Gates

Any future compute/process-worker API must land behind a new decision and pass
all of these gates before it can become a script-author surface:

In short, worker/process permissions, scoped script path, message size, cleanup,
nested worker denial, and bridge module isolation remain mandatory gates.

| Gate | Required proof |
| --- | --- |
| API naming | The API name does not contain or reuse `threads`, does not shadow `worker_threads`, and clearly avoids Rhino parity claims. |
| Worker/process permissions | A worker/process profile defines a strict provider allowlist or pure-compute-only mode, with release/project override rules. |
| Scoped script path | Worker entry scripts and dependencies are scoped to the project/working directory or packaged source graph with lexical and realpath escape denial. |
| Message size and clone policy | Message payloads have a checked byte budget, queue limit, structured-clone/JSON-safe validation, and stable rejection errors. |
| Cleanup and lifecycle | Parent success, failure, timeout, user stop, and cancellation terminate workers/processes and release ports/registries with diagnostics. |
| Nested worker denial | Nested worker or nested process spawning is denied unless a separate gate explicitly enables it. |
| Bridge module isolation | `autojs6:bridge`, AutoJs bridge modules, Android/Java objects, shell/process handles, UI/accessibility/image handles, raw network, native addons, and inspector access are denied in worker/process scope. |
| Resource stress | Repeated create/terminate, failure recovery, timeout recovery, RSS/thread snapshots, and queue pressure pass focused stress gates. |
| Packaged behavior | Packaged APK worker/process source graphs, stack/source names, ABI/native library requirements, and failure behavior pass smoke and cleanup gates. |
| `second_execution_slot` relation | The future API must not close `second_execution_slot`; that blocker remains open until default scheduling, packaged behavior, per-slot crash evidence, and stress promotion are proven. |

## Current Migration Path

Script authors should keep using the P12-21 cookbook patterns:

- `async` / `await` for provider work;
- `AbortController`, `timeoutMs`, script stop, or provider cleanup for
  cancellation;
- bounded async queues, single-owner state, Promise queues, and
  MessageChannel-style handoffs for coordination;
- explicit `engines.execScriptFile()` / `engines.execScript()` slots for
  bounded separate scripts;
- `work_manager` for scheduled bounded work.

These are not same-runtime Rhino threads and do not expose raw Java thread,
pool, lock, or blocking disposable objects.

## Evidence

- `docs/nodejs/design/RHINO_THREADS_MIGRATION_POLICY.md`
- `docs/nodejs/design/WORKER_THREADS.md`
- `docs/nodejs/releases/V1_1_WORKER_POLICY.md`
- `docs/nodejs/reports/WORKER_EXPERIMENTAL_EVALUATION.md`
- `docs/nodejs/final/FINAL_BLOCKER_REGISTRY.md`
- `docs/nodejs/final/FINAL_BLOCKER_REGISTRY.json`
- `app/src/main/java/org/autojs/autojs/engine/NodeRuntimeCapabilities.kt`
- `app/src/main/java/org/autojs/autojs/engine/NodeExecutionModePolicy.kt`
- `app/src/androidTest/java/org/autojs/autojs/engine/NodeWorkerThreadsTestSupport.kt`
- `NodeWorkerThreadsDesignGateInstrumentationTest`
- `NodeWorkerThreadsExperimentalInstrumentationTest`
- `NodeWorkerThreadsResourceStressInstrumentationTest`
- `EmbeddedNodePackagedApkSmokeTest#installAndRunWorkerThreadsEsmEmbeddedNodePackagedApk`

## Verification

Run:

```powershell
.\gradlew.bat --console=plain :app:verifyNodeRhinoCompatComputeProcessWorkerDecision
```

The gate verifies that P12-22 defers the replacement API, keeps
`worker_threads` default-off, keeps `worker_computation` reserved, keeps
`second_execution_slot` open, preserves all `threads.*` entries as
`blocked_by_design`, and advances Phase 12 to the Rhino JSON-only shim track.
