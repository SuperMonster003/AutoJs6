# Phase 14 Process Worker Replacement Decision

Status: ready

P14-31 closes the v1.2 process-worker replacement decision. It carries forward
the P13-35 diagnostic profile, imports the P14 worker availability/runnable/
resource decisions, and keeps the v1.2 runtime without a user-facing
process-worker facade.

## Decision

| Area | v1.2 state |
| --- | --- |
| Decision | `no_user_facing_facade_v1_2` |
| Diagnostic profile | `autojs6:profile.processWorkerReplacementProfile` |
| Preferred future primitive | `worker_threads.WorkerPool` |
| Callable `process_worker` / `compute_worker` API | Not introduced |
| Native worker precondition | `worker.preflight.status=available` required |
| Current worker precondition | `worker.preflight.status=unavailable` |
| Current worker error | `ERR_AUTOJS6_WORKER_DISABLED` |
| Runtime authority changed | `false` |

## Boundary

The P14-31 decision does not expose raw process handles, `spawn`, `fork`, stdio
streams, signal handles, `child_process`, or packaged process-worker behavior.
`shell` remains the separate scoped command bridge, and any future
`child_process` scoped runner stays owned by its own evidence track.

`worker_computation` remains reserved. P13-06 closed the second execution slot
issue for the scheduler/runtime lifecycle, but that closure is not process-
worker API authority. A future replacement API must account for execution slot
ownership before it can become callable.

## Future WorkerPool-Compatible API Requirements

A future phase may define a WorkerPool-compatible replacement API only after it
archives:

- native WorkerPool evidence on selected target ABIs;
- execution slot accounting tied to scheduler and service lifecycle cleanup;
- WorkerPool cancellation, timeout, exception, parent-completion, and
  resource/RSS stress evidence;
- packaged project metadata, disclosure, downgrade, and source graph behavior;
- user-visible launch UX and rollback policy;
- explicit separation from `child_process`, `shell`, and raw process handles.

## Verification

- Host verifier: `tools/nodejs/phase14/verify-node-phase14-process-worker-replacement-decision.js`
- Gradle gate: `:app:verifyNodePhase14ProcessWorkerReplacementDecision`
- Generated report: `build/reports/nodejs/phase14-process-worker-replacement-decision.json`
