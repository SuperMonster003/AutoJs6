# Phase 10 Worker/WASI Boundary

## Status

S10-37 through S10-42 are recorded as `ready_with_recorded_blockers`.

Pure WebAssembly compile/instantiate and scoped `.wasm` loading remain usable.
`worker_threads` is still default disabled and blocked by native availability.
Raw `wasi` / `node:wasi` remains disabled; a controlled `autojs6:wasi` facade is
not implemented.

## Gates

```powershell
.\gradlew.bat --console=plain :app:verifyNodePhase10WorkerWasiBoundary
.\gradlew.bat --console=plain :app:verifyNodeWorkerWasiGate "-Pautojs.nodejs.test.deviceProfile=sony"
```

`verifyNodePhase10WorkerWasiBoundary` writes:

- `build/reports/nodejs/phase10-worker-wasi-boundary.json`
- `build/reports/nodejs/phase10-worker-wasi-boundary.md`

`verifyNodeWorkerWasiGate` keeps the existing focused device coverage: default
worker denial/design-gate tests, native worker availability preflight, and the
WebAssembly/WASI focused test.

## Current Decisions

- S10-37 worker capability probe:
  native `worker_threads` remains unavailable in the embedded libnode build.
- S10-38 controlled Worker MVP:
  remains blocked; default `require("worker_threads")`,
  `require("node:worker_threads")`, and shadowing attempts fail closed.
- S10-39 MessageChannel and transfer:
  remains blocked because runnable worker creation still skips behind native
  availability.
- S10-40 WorkerPool and resource limits:
  remains blocked; stress tests are promotion gates only.
- S10-41 controlled WASI profile:
  deferred; raw `node:wasi` is not exposed as a sandbox.
- S10-42 WASM compile cache and packaged support:
  partial WASM only; pure WebAssembly works, but WASM compile cache, packaged
  `.wasm` asset manifests, worker+WASM, and controlled WASI module support
  remain future gates.

## Boundary

This phase does not enable native workers, worker pools, raw WASI, WASI
preopens, arbitrary host filesystem access, raw sockets, native addons, or
process-level escape hatches. Packages that probe `worker_threads` or WASI must
continue to degrade gracefully.
