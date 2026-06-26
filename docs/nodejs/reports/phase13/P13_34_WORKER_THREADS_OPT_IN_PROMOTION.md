# P13-34 Worker Threads Opt-In Promotion

Status: partial

P13-34 records `worker_threads` opt-in promotion as partial. The default Safe
Node Profile still rejects bare `worker_threads` and runnable worker authority,
while `node:worker_threads` exposes a shape-only npm compatibility facade for
feature detection. The request-local experimental path exposes explicit
diagnostics, a native availability preflight, and native-available promotion
gates for pure-JS workers, MessageChannel, transfer lists, WorkerPool, resource
stress, and packaged `.mjs` worker smoke.

## Decision

- `require("autojs6:profile").workerThreadsProfile` exposes a frozen diagnostic
  snapshot for default denial and request-local experimental preflight.
- `workerThreadsProfile.defaultEnabled=false`; no default worker authority is
  promoted.
- `require("node:worker_threads")` is shape-only in the default profile:
  package top-level detection can read `Worker`, `MessageChannel`,
  `MessagePort`, and `receiveMessageOnPort`; `new Worker()` creates an inert
  disabled worker object for npm initialization paths, but `postMessage()` and
  real execution still reject with `ERR_AUTOJS6_WORKER_DISABLED`.
- If the request-local experimental flag is enabled on a native-worker-unavailable
  build, `require("worker_threads")` and `require("node:worker_threads")` return
  the same shape-only facade with `nativeAvailable=false` instead of aborting
  package initialization.
- `workerThreadsProfile.requestLocalOptIn` reflects the per-request
  experimental flag.
- `nativeAvailability="preflight_required"` remains explicit. A runnable worker
  claim requires the native preflight to report the limited `worker_threads`
  surface on the target ABI/device.
- Worker bridge modules, raw native handles, and nested workers remain denied.
- Worker policy remains bounded to one active worker, 5000 ms startup timeout,
  65536 byte messages, 32 queued messages, scoped worker fs, WorkerPool
  `maxWorkers=1`, and capped resource limits.

## Boundary

The following remain future work before opt-in promotion can close:

- native worker availability on the target promotion ABIs;
- current connected evidence for `verifyNodeWorkerGate` with
  `-Pautojs.nodejs.worker_threads.experimental=true`;
- current device-exported packaged `.mjs` worker smoke evidence;
- crash evidence for worker startup failure, worker exception, timeout,
  parent-completion cleanup, and packaged worker failure modes;
- final Desktop policy for nested workers, SharedArrayBuffer/Atomics,
  resourceLimits, Android thread/RSS ceilings, and release disclosure.

## Verification

- Host verifier: `tools/nodejs/phase13/verify-worker-threads-opt-in-promotion.js`
- Gradle gate: `:app:verifyNodePhase13WorkerThreadsOptInPromotion`
- Focused P13 gate:
  `NodeWorkerThreadsPhase13DecisionInstrumentationTest`
- Existing worker gates:
  `NodeWorkerThreadsBootstrapInstrumentationTest`,
  `NodeWorkerThreadsDesignGateInstrumentationTest`,
  `NodeWorkerThreadsExperimentalInstrumentationTest`,
  `NodeWorkerThreadsResourceStressInstrumentationTest`, and
  `EmbeddedNodePackagedApkSmokeTest#installAndRunWorkerThreadsEsmEmbeddedNodePackagedApk`
- Existing worker verification entry:
  `:app:verifyNodeWorkerGate`
- Type declarations:
  `docs/nodejs/types/autojs6-node/autojs_global.d.ts` and
  `sample/nodejs/typescript-smoke/src/main.ts`

## Remaining Work

- Run and archive the full experimental worker gate on a native-worker-capable
  build/device.
- Add current crash evidence references to the P13 gate output.
- Promote packaged worker smoke evidence from source markers to current
  device-exported reports.
- Decide whether worker promotion belongs to Desktop opt-in only or a narrower
  debug unsafe lab profile until Android resource ceilings are proven.
