# Phase 14 Desktop Runtime Doc Sync

P14-32 is a documentation, declaration-reference, release-matrix, migration,
and security-corpus sync gate for the `desktop.worker_runtime` surface. It does
not promote additional runtime authority. It records P14-28 through P14-31 as
the current v1.2 state, then verifies that public docs do not claim runnable
worker or process-worker support.

## Synced State

| Area | Current state | Sync rule |
| --- | --- | --- |
| Native worker availability | P14-28 ready | Node 24.5 target ABI artifacts are audited, but current native preflight remains `worker.preflight.status=unavailable`. |
| Runnable and packaged workers | P14-29 ready, conditional skip | Source anchors are documented; runnable execution and packaged `.mjs` worker support are not release evidence. |
| Resource and RSS stress | P14-30 ready, conditional skip | Worker resource/RSS source anchors are documented; Java/native heap, fd, queue, active worker, and active WorkerPool archives remain future work. |
| Process-worker replacement | P14-31 ready | v1.2 keeps no user-facing process-worker facade and no raw process authority. |

## Release Policy

The v1.2 desktop runtime release decision is
`worker_intentionally_unsupported_v1_2`: runnable `worker_threads`, packaged
workers, WorkerPool replacement API, `process_worker`, `compute_worker`,
`autojs6:process-worker`, raw process handles, stdio streams, signals, `spawn`,
`fork`, and stable `child_process` authority remain unpromoted.

## Example Policy

Desktop worker examples remain guarded, static, or denial-oriented. No
`worker_threads`, WorkerPool, process-worker, or raw `child_process` example is
counted as v1.2 release smoke until native, resource, packaged, UX, security,
and rollback gates pass.

## Verification

```powershell
.\gradlew.bat --console=plain :app:verifyNodePhase14DesktopRuntimeDocSync
```

Expected reports:

```text
build/reports/nodejs/phase14-desktop-runtime-doc-sync.json
build/reports/nodejs/phase14-desktop-runtime-doc-sync.md
```
