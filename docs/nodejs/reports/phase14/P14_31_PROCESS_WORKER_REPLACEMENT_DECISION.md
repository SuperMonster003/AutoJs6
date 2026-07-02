# P14-31 Process Worker Replacement Decision

Status: ready

P14-31 deploys the v1.2 process-worker replacement decision gate. The current
decision is to keep no user-facing process-worker facade for v1.2 and require a
future WorkerPool-compatible API gate before any replacement surface becomes
callable.

## Deployed

- Added `docs/nodejs/phase14/process-worker-replacement-decision.json`.
- Added `docs/nodejs/phase14/PHASE14_PROCESS_WORKER_REPLACEMENT_DECISION.md`.
- Added `tools/nodejs/phase14/verify-node-phase14-process-worker-replacement-decision.js`.
- Added Gradle gate `:app:verifyNodePhase14ProcessWorkerReplacementDecision`.
- Imported P13-35 process-worker replacement, P14-28 native availability,
  P14-29 runnable/packaged smoke, and P14-30 resource/RSS stress reports.
- Verified the diagnostic `processWorkerReplacementProfile`, absence of
  `process_worker` / `compute_worker` facades, default `child_process` denial,
  separate `shell` bridge policy, reserved `worker_computation`, and current
  native-worker-unavailable state.
- Kept raw process handles, `spawn`, `fork`, stdio streams, signal handles,
  packaged process-worker behavior, and WorkerPool replacement API authority
  unpromoted.

## Decision

- P14-31 decision: `no_user_facing_facade_v1_2`.
- Preferred future primitive: `worker_threads.WorkerPool`.
- Runtime authority changed: `false`.
- Process-worker facade promoted: `false`.
- Child-process authority promoted: `false`.
- Future replacement API requires native, slot-accounting, cleanup/resource,
  packaged, UX, and security evidence.

## Verification

```powershell
node --check tools/nodejs/phase14/verify-node-phase14-process-worker-replacement-decision.js
node tools/nodejs/phase14/verify-node-phase14-process-worker-replacement-decision.js --repo-root . --report-dir build/reports/nodejs
.\gradlew.bat --console=plain :app:verifyNodePhase14ProcessWorkerReplacementDecision
```
