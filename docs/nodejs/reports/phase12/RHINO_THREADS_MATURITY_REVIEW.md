# Rhino Threads Maturity Review

Status: P12-20 design-locked maturity review.

P12-20 rechecks whether Phase 12 can use current `worker_threads`, process
slot, or execution-mode infrastructure as a Rhino `threads` migration path. The
decision is conservative: AutoJs6 must not expose default `worker_threads`,
must not add `require("threads")`, `compat.threads`, a global `threads`, or a
global `sync`, and must not claim Rhino synchronous thread or shared-object
parity.

## Decision

Phase 12 cannot承接 Rhino `threads` as a default runnable worker surface.

- `worker_threads` remains disabled by default.
- The internal `autojs.nodejs.worker_threads.experimental` flag remains a
  native-availability/preflight unblock/debug gate only, not a user-facing
  release opt-in.
- Native worker availability is still the gatekeeper. Current evidence keeps
  runnable worker creation, stress, and packaged worker source graphs blocked
  until native workers are available and the focused suites pass.
- `worker_computation` remains `reserved` with no supported launch surface.
- `second_execution_slot` remains open in the final blocker registry; explicit
  overflow slot routing does not close default scheduling, packaged behavior,
  per-slot crash evidence, or stress promotion gaps.
- P12-21 cookbook examples must use `async` / `await`, `AbortController`,
  explicit `engines` execution slots, and `work_manager`, not default workers.
- P12-22 may design a separately named compute/process-worker API, but it must not reuse the `threads` name and must not advertise Rhino parity.

## Maturity Matrix

| Area | P12-20 result | Evidence |
| --- | --- | --- |
| Default `worker_threads` | blocked, default-off | `NodeRuntimeCapabilities.WORKER_THREADS_EXPERIMENTAL_DEFAULT=false`; normal builds deny bare and `node:` `worker_threads` before shadowing. |
| Native availability | blocked | `NodeWorkerThreadsTestSupport.probeNativeWorkerThreads()` accepts only a usable limited surface or stable `ERR_AUTOJS6_WORKER_DISABLED` with zero active workers. Current release docs record the native-unavailable result. |
| Capability isolation | prepared, not promotable | Native-available tests cover worker-local denial of bridge modules, `toast`, `accessibility`, `image`, `shell`, Java interop, inspector, raw network, and nested workers, but they skip when native workers are unavailable. |
| Resource cleanup | prepared, not promotable | Runtime tracks worker, MessagePort, WorkerPool, and cleanup diagnostics; resource stress remains promotion-only and native-availability gated. |
| Packaged worker | prepared, not promotable | The packaged `.mjs` worker smoke exists, but skips before APK construction when native worker preflight is unavailable. |
| Process/execution slot | reserved/partial, not Rhino parity | `worker_computation` is reserved; `second_execution_slot` remains open for default scheduling, packaged behavior, per-slot crash evidence, and stress promotion. |
| Rhino `threads` parity | rejected | `threads.__module`, `threads.start`, `threads.pool`, `threads.interrupt`, `threads.disposable`, and `threads.sync` remain `blocked_by_design`. |

## Accepted Migration Targets

Use these by intent:

- provider or UI work: `Promise`, `async` / `await`, and bridge APIs with
  `timeoutMs`;
- cancellation or deadline: `AbortController`, explicit timeout options, script
  stop, or provider cleanup;
- serialized state: one-owner state, immutable JSON snapshots, or a Promise
  queue;
- bounded separate execution: explicit `engines.execScriptFile()` /
  `engines.execScript()` with JSON arguments;
- scheduled bounded work: `work_manager`;
- future CPU-bound work: P12-22 may evaluate a separately named
  compute/process-worker API after native worker and slot blockers are
  addressed.

## Non-Goals

- No `require("threads")`.
- No `require("autojs6:compat").threads`.
- No global `threads` or global `sync`.
- No default `worker_threads`.
- No same-runtime `ScriptRuntime` callback execution.
- No raw Java `Thread`, `ThreadPoolExecutor`, `Synchronizer`,
  `ReentrantLock`, `AtomicLong`, or `VolatileDispose` object transfer.
- No worker fallback to Rhino, parent-thread execution, or unrestricted Node
  behavior.

## Evidence

- `docs/nodejs/design/RHINO_THREADS_MIGRATION_POLICY.md`
- `docs/nodejs/design/WORKER_THREADS.md`
- `docs/nodejs/releases/V1_1_WORKER_POLICY.md`
- `docs/nodejs/reports/WORKER_PRE_GA_EVALUATION.md`
- `docs/nodejs/final/FINAL_BLOCKER_REGISTRY.md`
- `app/src/main/java/org/autojs/autojs/engine/NodeRuntimeCapabilities.kt`
- `app/src/main/java/org/autojs/autojs/engine/NodeExecutionModePolicy.kt`
- `app/src/androidTest/java/org/autojs/autojs/engine/NodeWorkerThreadsTestSupport.kt`
- `NodeWorkerThreadsBootstrapInstrumentationTest`
- `NodeWorkerThreadsDesignGateInstrumentationTest`
- `NodeWorkerThreadsExperimentalInstrumentationTest`
- `NodeWorkerThreadsResourceStressInstrumentationTest`
- `EmbeddedNodePackagedApkSmokeTest#installAndRunWorkerThreadsEsmEmbeddedNodePackagedApk`

## Verification

Run:

```powershell
.\gradlew.bat --console=plain :app:verifyNodeRhinoCompatThreadsMaturityReview
```

The gate verifies that default `worker_threads` remains closed, current worker
evidence is native-availability gated, process/slot blockers remain open, the
Rhino `threads` status bucket stays `blocked_by_design`, and the Phase 12 matrix
advances to P12-21 cookbook work.
