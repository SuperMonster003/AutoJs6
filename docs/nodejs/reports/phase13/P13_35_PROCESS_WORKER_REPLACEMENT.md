# P13-35 Process Worker Replacement

P13-35 records process-worker replacement as partial. The current Phase 13
decision is to avoid introducing a new `process_worker`, `compute_worker`, or
`autojs6:process-worker` runtime facade while the native worker and execution
slot evidence remains incomplete.

## Decision

- Replacement primitive: request-local `worker_threads.WorkerPool`.
- Runtime facade: not introduced.
- `child_process`: denied.
- `shell`: remains a separate scoped command bridge, not a process-worker API.
- `worker_computation`: reserved.
- `second_execution_slot`: not closed by this decision.
- Scoped script path: `workingDirectory`.
- Message policy: `65536` byte messages and `32` queued messages, inherited
  from the worker policy.
- Cleanup policy: `WorkerPool.close()` or execution-owned runtime destroy.
- Packaged behavior: not promoted.

The runtime exposes this decision through
`autojs6:profile.processWorkerReplacementProfile`. This is diagnostic metadata
only; it does not create a callable process-worker surface or grant raw process
handles, spawn/fork, stdio streams, signals, or shell authority.

For P13-35 v1.1, there is no user-facing process-worker API or UX. The
diagnostic profile is the only accepted surface, and `process_worker`,
`compute_worker`, and `autojs6:process-worker` remain absent until a future
provider owns native WorkerPool evidence, slot accounting, cleanup, packaged
behavior, and launch UX.

P13-40 is a separate child_process scoped-runner track. P13-35 delegates raw
spawn, fork, stdio, signals, and process handles to that track and does not
wait for a scoped child_process runner before closing its own replacement
facade decision.

## Evidence

- `NodeProcessWorkerReplacementPhase13DecisionInstrumentationTest` verifies the
  frozen profile snapshot, absence of global process-worker facades, default
  `child_process` denial, default `worker_threads` denial, and shadow-package
  protection.
- Existing worker gates provide the native-preflight, WorkerPool, message-size,
  queue, cleanup, and resource-stress source evidence.
- Existing shell tests keep `shell` as a scoped command bridge and preserve
  `child_process` denial.
- `verifyNodePhase13ProcessWorkerReplacementDecision` records the host gate and
  writes `phase13-process-worker-replacement-decision.json` and `.md`.

## Remaining Blockers

- Native WorkerPool evidence must be archived on target ABIs.
- Process-worker packaged behavior is not defined.
- Slot accounting and cleanup stress must be tied to any future user-facing API.
- `worker_computation` needs a supported launch surface before it can execute.
- `second_execution_slot` remains open for default scheduling, packaged
  behavior, per-slot crash evidence, and stress promotion.

## Rollback

Remove `processWorkerReplacementProfile`, the focused instrumentation test, the
P13-35 verifier, and the Phase 13 docs/report references. Runtime behavior
falls back to the P13-34 worker decision: default worker denial, request-local
worker preflight, and no process-worker facade.
