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

## Worker Policy Boundary

- Nested workers remain denied for P13-34 v1.1. No nested worker tier is
  promoted by the default profile or by the request-local experimental preflight.
- SharedArrayBuffer and Atomics are not promoted as cross-worker compatibility
  guarantees. The current gate only covers bounded structured-clone and
  transfer-list message paths under the existing message budgets.
- Resource limits stay explicit and conservative: one active worker, one
  WorkerPool worker, 5000 ms startup/task timeout, 65536 byte messages, 32
  queued messages, and V8 resource limits of 64 MiB old generation, 16 MiB
  young generation, 32 MiB code range, and 8 MiB stack.
- Android thread and RSS behavior remains evidence-gated. P13-34 documents the
  current one-worker thread ceiling, but it does not promote a release RSS
  ceiling until the resource/crash anchors pass on a native-worker-capable
  target.

## Current Device Evidence

QV710AF65F / XQ-AT72 / Android 12 ran the current experimental worker gate on
2026-06-28 with:

```powershell
$env:ANDROID_SERIAL='QV710AF65F'
.\gradlew.bat --console=plain :app:verifyNodeWorkerGate "-Pautojs.nodejs.worker_threads.experimental=true" "-Pautojs.nodejs.test.deviceProfile=sony"
```

Result: passed with native-unavailable skips (`tests=16, failures=0,
skipped=14`). The runnable, resource-stress, and packaged worker paths reached
the native preflight and skipped with `worker.preflight.status=unavailable` and
`ERR_AUTOJS6_WORKER_DISABLED`. This archives the current device gate without
claiming runnable worker support.

## Resource And Crash Evidence References

- `NodeWorkerThreadsResourceStressInstrumentationTest` records source anchors
  for worker startup/resource lifecycle, worker exception recovery, timeout
  recovery, parent-completion cleanup, WorkerPool failure/cancel/timeout
  recovery, and bounded RSS/thread snapshots.
- The P13-34 verifier now tracks the concrete stress markers:
  `worker.resource.pool.failures`, `worker.resource.pool.cleanup.before=1|1`,
  `worker.resource.throw=true|`, `worker.resource.timeout.recovery=v24.5.0`,
  and `assertResourceGrowthBounded`.
- These references are current promotion anchors only. They still skip on
  QV710AF65F because native worker preflight reports unavailable, and they must
  be re-run on a native-worker-capable target before runnable worker authority
  can be promoted.
- Packaged worker failure evidence remains native-available promotion work
  because the current packaged `.mjs` worker smoke is source/skip evidence, not
  device-exported runnable worker behavior.

## Boundary

The following remain future work before opt-in promotion can close:

- native worker availability on the target promotion ABIs/devices;
- native-available `verifyNodeWorkerGate` evidence with
  `-Pautojs.nodejs.worker_threads.experimental=true`;
- native-available device-exported packaged `.mjs` worker smoke evidence;
- release disclosure for any future worker profile promotion.

The docs, release matrix, desktop matrix, surface manifest, and Gradle gate are
evidence-only for this partial decision. They do not add a separate P13-34
blocker beyond native availability, the experimental gate archive, and
native-available packaged worker smoke evidence.

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

- Run and archive the full runnable experimental worker gate on a
  native-worker-capable build/device.
- Re-run the resource/crash stress anchors on a native-worker-capable target.
- Promote packaged worker smoke evidence from source markers to current
  device-exported reports.
- Decide whether worker promotion belongs to Desktop opt-in only or a narrower
  debug unsafe lab profile until Android resource ceilings are proven.
