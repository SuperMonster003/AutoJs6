# P14-29 Worker Runnable Packaged Smoke

Status: ready

P14-29 deploys the v1.2 worker runnable and packaged smoke decision gate. The
current result is a conditional skip because P14-28 imports
`worker.preflight.status=unavailable` with `ERR_AUTOJS6_WORKER_DISABLED`.

## Deployed

- Added `docs/nodejs/phase14/worker-runnable-packaged-smoke-plan.json`.
- Added `docs/nodejs/phase14/PHASE14_WORKER_RUNNABLE_PACKAGED_SMOKE.md`.
- Added `tools/nodejs/phase14/verify-node-phase14-worker-runnable-packaged-smoke.js`.
- Added Gradle gate `:app:verifyNodePhase14WorkerRunnablePackagedSmoke`.
- Imported the P14-28 worker native availability report as the required
  precondition.
- Verified source anchors for basic Worker lifecycle, CJS/ESM entries, worker
  capability isolation, MessageChannel/transfer, WorkerPool, exception/timeout
  recovery, parent cleanup, nested Worker denial, and packaged `.mjs` smoke.
- Kept runnable smoke execution, packaged smoke execution, default authority,
  project opt-in, packaged opt-in, and process-worker facade promotion disabled.

## Decision

- P14-29 decision: `conditional_skip_native_unavailable`.
- Runtime authority changed: `false`.
- Runnable smoke executed: `false`.
- Packaged smoke executed: `false`.
- Worker authority remains disabled by default until native, packaged, and
  resource gates pass.

## Verification

```powershell
node --check tools/nodejs/phase14/verify-node-phase14-worker-runnable-packaged-smoke.js
node tools/nodejs/phase14/verify-node-phase14-worker-runnable-packaged-smoke.js --repo-root . --report-dir build/reports/nodejs
.\gradlew.bat --console=plain :app:verifyNodePhase14WorkerRunnablePackagedSmoke
```
