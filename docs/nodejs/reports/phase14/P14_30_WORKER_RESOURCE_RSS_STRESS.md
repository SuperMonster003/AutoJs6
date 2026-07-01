# P14-30 Worker Resource RSS Stress

Status: ready

P14-30 deploys the v1.2 worker resource/RSS stress decision gate. The current
result is a conditional skip because P14-28 still reports native workers as
unavailable.

## Deployed

- Added `docs/nodejs/phase14/worker-resource-rss-stress-plan.json`.
- Added `docs/nodejs/phase14/PHASE14_WORKER_RESOURCE_RSS_STRESS.md`.
- Added `tools/nodejs/phase14/verify-node-phase14-worker-resource-rss-stress.js`.
- Added Gradle gate `:app:verifyNodePhase14WorkerResourceRssStress`.
- Imported P14-28 native availability and P14-29 runnable/packaged smoke
  reports.
- Verified source anchors for sequential Worker create/terminate, WorkerPool
  reuse/failure/cancel/timeout/transfer/shutdown, parent cleanup, pending
  worker-message cleanup, exception recovery, timeout recovery, RSS snapshots,
  and thread-count bounded assertions.
- Recorded Java heap, native heap, and fd count as future worker-specific
  native-available archive requirements.
- Kept resource stress execution and worker authority promotion disabled.

## Decision

- P14-30 decision: `conditional_skip_native_unavailable`.
- Resource stress executed: `false`.
- Runtime authority changed: `false`.
- Worker authority remains disabled until native, packaged, and resource gates
  pass together.

## Verification

```powershell
node --check tools/nodejs/phase14/verify-node-phase14-worker-resource-rss-stress.js
node tools/nodejs/phase14/verify-node-phase14-worker-resource-rss-stress.js --repo-root . --report-dir build/reports/nodejs
.\gradlew.bat --console=plain :app:verifyNodePhase14WorkerResourceRssStress
```
