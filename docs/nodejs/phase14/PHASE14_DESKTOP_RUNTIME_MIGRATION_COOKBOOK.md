# Phase 14 Desktop Runtime Migration Cookbook

This cookbook records the v1.2 migration posture for desktop-style worker and
process APIs. It is policy evidence for P14-32 only and does not make any API
callable.

## Worker Threads

Do not migrate stable scripts to runnable `worker_threads` for v1.2. The
default profile keeps `require("worker_threads")` denied and
`require("node:worker_threads")` as a shape-only compatibility facade for npm
feature detection. Current P14-28 evidence records
`worker.preflight.status=unavailable` with `ERR_AUTOJS6_WORKER_DISABLED`.

Use normal async JavaScript, bounded bridge calls, `AbortController`, timers,
or `work_manager` scheduled work for v1.2 production scripts. CPU-bound worker
examples should remain static, guarded, or denial-oriented.

## WorkerPool

`WorkerPool` remains a future replacement primitive, not a stable public API.
P14-29 and P14-30 keep WorkerPool lifecycle, transfer, exception/timeout,
cleanup, and resource/RSS source anchors documented, but they do not execute on
the current native-unavailable target.

A future WorkerPool-compatible API must provide native availability, execution
slot accounting, cancellation, parent-completion cleanup, packaged source graph
behavior, UX, security corpus, and rollback evidence.

## Process-Worker Replacement

P14-31 closes process-worker replacement for v1.2 as
`no_user_facing_facade_v1_2`. Do not use or document `process_worker`,
`compute_worker`, or `autojs6:process-worker` as callable APIs. The diagnostic
`autojs6:profile.processWorkerReplacementProfile` is the only supported surface.

## Child Process And Shell

`child_process` remains denied by default. `shell` is a separate scoped command
bridge and is not process-worker authority. Scripts that need command execution
should use `require("shell").exec()` only when the project declares the scoped
`shell` capability and accepts its bounded, non-interactive semantics.

Raw process handles, stdio streams, signals, `spawn`, `fork`, detached
processes, arbitrary executable paths, and packaged process-worker behavior
remain outside v1.2.

## Future Promotion Checklist

Before v1.2 or a later profile can claim runnable worker/process compatibility,
the project must archive:

- native Worker availability on selected target ABIs;
- runnable Worker and packaged `.mjs` worker smoke;
- WorkerPool cancellation, timeout, transfer, exception, parent cleanup, and
  resource/RSS stress;
- Java heap, native heap, fd, queue, active worker, and active WorkerPool
  deltas;
- explicit execution slot accounting and launch UX;
- security corpus positive, negative, and abuse cases;
- packaged metadata, disclosure, downgrade, and rollback policy.
