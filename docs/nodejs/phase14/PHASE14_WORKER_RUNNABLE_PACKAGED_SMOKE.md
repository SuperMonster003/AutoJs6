# Phase 14 Worker Runnable Packaged Smoke

Status: ready

P14-29 records the runnable and packaged `worker_threads` smoke gate for v1.2.
Because P14-28 currently reports `worker.preflight.status=unavailable`, this
gate is a conditional skip: it verifies that source anchors exist, but it does
not count them as runnable or packaged proof.

## Decision

| Area | v1.2 state |
| --- | --- |
| Native precondition | `worker.preflight.status=available` required |
| Current preflight | `worker.preflight.status=unavailable` |
| Current error | `ERR_AUTOJS6_WORKER_DISABLED` |
| Runnable smoke executed | `false` |
| Packaged smoke executed | `false` |
| Runtime authority changed | `false` |
| Default/project/package worker authority | Not promoted |

## Source Anchors

The existing native-available source anchors remain promotion targets:

- basic Worker construction, `workerData`, `parentPort.postMessage`, exit,
  `terminate()`, max-worker rejection, restricted bridge imports, nested Worker
  denial, and path escape denial;
- CJS, `.mjs`, and `package.json` `type=module` worker entry resolution;
- worker-local capability profile and scoped worker filesystem boundary;
- `MessageChannel`, `MessagePort`, transfer lists, clone errors, message-size
  limits, queued-message limits, and cleanup diagnostics;
- WorkerPool reuse, queue overflow, cancellation, running timeout, shutdown,
  and diagnostics;
- packaged `.mjs` Worker smoke selected by `verifyNodeWorkerGate`.

These anchors remain source evidence only until P14-28 produces
`nativeAvailable=true` on the target promotion ABI/device set.

## Verification

- Host verifier: `tools/nodejs/phase14/verify-node-phase14-worker-runnable-packaged-smoke.js`
- Gradle gate: `:app:verifyNodePhase14WorkerRunnablePackagedSmoke`
- Generated report: `build/reports/nodejs/phase14-worker-runnable-packaged-smoke.json`
