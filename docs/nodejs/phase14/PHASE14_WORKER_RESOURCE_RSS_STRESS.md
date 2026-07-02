# Phase 14 Worker Resource RSS Stress

Status: ready

P14-30 records the worker resource and RSS stress gate for v1.2. Since P14-28
currently reports native workers unavailable, the gate is a conditional skip:
source anchors are verified, but no resource/RSS execution evidence is counted
for promotion.

## Decision

| Area | v1.2 state |
| --- | --- |
| Native precondition | `worker.preflight.status=available` required |
| Current preflight | `worker.preflight.status=unavailable` |
| Current error | `ERR_AUTOJS6_WORKER_DISABLED` |
| Resource stress executed | `false` |
| RSS/thread source anchors | Present |
| Java/native heap/fd worker proof | Not ready |
| Runtime authority changed | `false` |

## Source Anchors

The existing native-available resource anchors cover:

- 100 sequential Worker create/terminate cycles;
- WorkerPool 100-job reuse, failure recovery, queued cancellation, ArrayBuffer
  transfer, task timeout, shutdown, and diagnostics;
- parent-completion cleanup for active WorkerPool and pending worker messages;
- worker exception recovery and next-run recovery;
- infinite-loop worker timeout and next-run recovery;
- coarse RSS and thread-count snapshots with bounded delta assertions.

## Future Native-Available Requirements

Before any worker authority can be promoted, a future native-worker-capable
target must archive:

- RSS, Java heap, native heap, fd count, thread count, message queue, active
  worker, and active WorkerPool deltas;
- repeated create/terminate and WorkerPool stress with no monotonic leaks;
- exception, timeout, parent-completion cleanup, and crash recovery without
  orphan workers, pending messages, or unrecoverable runtime;
- packaged resource behavior aligned with the P14-29 packaged smoke gate.

## Verification

- Host verifier: `tools/nodejs/phase14/verify-node-phase14-worker-resource-rss-stress.js`
- Gradle gate: `:app:verifyNodePhase14WorkerResourceRssStress`
- Generated report: `build/reports/nodejs/phase14-worker-resource-rss-stress.json`
