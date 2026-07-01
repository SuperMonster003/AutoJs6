# Node Profile v1.2 Desktop Runtime Matrix

This matrix records the Phase 14 `desktop.worker_runtime` documentation and
release-policy sync state. It is evidence for P14-32 docs sync only; it does
not promote `worker_threads`, process-worker, or `child_process` authority.

| Area | Source gate | Current v1.2 state | Runtime availability | Example policy | Remaining promotion evidence |
| --- | --- | --- | --- | --- | --- |
| Native `worker_threads` availability | P14-28 | `intentional_non_support_refreshed` | Target ABI artifacts are audited for Node 24.5, but current preflight remains `worker.preflight.status=unavailable` with `ERR_AUTOJS6_WORKER_DISABLED`. | Worker examples remain guarded or denial-oriented. | Native preflight must report available on every selected target ABI/device. |
| Runnable and packaged workers | P14-29 | `conditional_skip_native_unavailable` | Worker lifecycle, CJS/ESM entries, MessageChannel/transfer, WorkerPool, nested-denial, exception/timeout, cleanup, and packaged `.mjs` smoke source anchors are documented but not executed. | No runnable worker smoke counts for v1.2 release. | Native-available runnable gate plus packaged `.mjs` source graph archive. |
| Resource and RSS stress | P14-30 | `conditional_skip_native_unavailable` | Sequential Worker cycles, WorkerPool stress, cleanup, exception/timeout recovery, RSS snapshots, and thread-count assertions are source-anchored only. | Resource examples remain non-release evidence. | Java heap, native heap, fd count, queue, active worker, active WorkerPool, RSS, and thread deltas on native-worker-capable devices. |
| Process-worker replacement | P14-31 | `no_user_facing_facade_v1_2` | `processWorkerReplacementProfile` remains diagnostic-only. `process_worker`, `compute_worker`, `autojs6:process-worker`, WorkerPool replacement API, and packaged process-worker behavior are not introduced. | Process-worker examples are not runnable release examples. | WorkerPool-compatible API design, native evidence, slot accounting, cleanup/resource proof, packaged behavior, UX, security corpus, and rollback evidence. |
| `child_process` / `shell` boundary | P13-40 plus P14-31 sync | stable denied / separate bridge | `child_process` remains denied by default. `shell` remains a separately permissioned scoped command bridge and does not imply raw process handles, stdio streams, signals, `spawn`, or `fork`. | Command examples must use the scoped `shell` bridge only when declared. | Separate scoped-runner evidence with app-private allowlists, bounded stdio, timeout/cancel/kill cleanup, packaged behavior, and hostile corpus coverage. |

P14-32 docs sync verifies that this matrix, TypeScript declaration references,
worker design docs, desktop examples, migration cookbook, compatibility profile,
security model, security corpus, error catalog, Pro/AutoX migration notes,
testing docs, and CI gates all use the same
`worker_intentionally_unsupported_v1_2` release decision for high-risk desktop
worker/process surfaces.
