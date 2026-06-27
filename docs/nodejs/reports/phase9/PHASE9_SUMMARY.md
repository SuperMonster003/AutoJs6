# Phase 9 Summary

Phase 9 is complete as a capability-expansion and evidence-gathering phase for
native runtime modernization, production performance paths, and the safe
extension platform. It does not promote Node 24.17 as the default runtime.

Generated summary:

```powershell
.\gradlew.bat --console=plain :app:generateNodePhase9Summary "-Pautojs.nodejs.test.deviceProfile=sony"
```

Outputs:

- `build/reports/nodejs/phase9-summary.json`
- `build/reports/nodejs/phase9-summary.md`

## Final Decision

The summary decision is expected to be
`completed_with_recorded_blockers`.

This means the Phase 9 evidence is complete enough to close the phase, while
known production blockers remain explicit:

- Node 24.17 Android `libnode.so` artifacts are not currently producible.
- The default runtime remains `node24_5`.
- 16 KB page-size and runtime-integrity evidence is present but not fully
  production-ready for every native artifact.
- Startup snapshots, reusable runtime pooling, positive debug Inspector,
  CPU/heap profiling, isolated Binder broker, controlled WASI, and plugin
  extension production paths remain gated.

## Completed Scope

Phase 9 completed the following work:

- Versioned C ABI adapter contract and Node 24.5 adapter export evidence.
- Reproducible Android runtime build-plan skeleton with Node 24.17 upstream
  provenance and current 24.5 fork/artifact provenance.
- Runtime artifact manifest, integrity, distribution, and 4 KB / 16 KB page
  validation gates.
- Performance policy gate covering compile cache, startup snapshot boundary,
  process-only prewarm, memory-pressure hooks, and performance matrix state.
- Inspector/security policy gate that keeps production Inspector disabled.
- Isolation/process-pool/worker policy gate with worker promotion still
  blocked by native availability.
- WebAssembly/WASI focused gate: pure WebAssembly works, raw `wasi` and
  `node:wasi` remain disabled.
- Plugin policy gate that keeps JS-only plugins as the active surface and
  leaves WASM/native service plugin paths gated.
- Native crash pipeline and chaos/security corpus policy gates.
- Real npm corpus expansion from 20 to 30 fixtures.
- Example catalog expansion from 33 to 42 examples, including 9 Phase 9
  examples.
- Aggregate `verifyNodePhase9Gate` and generated Phase 9 summary.

## Current Evidence Snapshot

Latest Sony focused aggregate evidence:

- `NodeWasmWasiInstrumentationTest`: 2 tests passed.
- `NodeRealNpmCompatInstrumentationTest`: 2 tests passed.
- Real npm corpus: 30 fixtures, delta +10 from Phase 8.
- Example catalog: 42 examples, delta +9 from Phase 8.
- Page-size validation: 9/9 checked artifacts are 4 KB compatible; 5/9 are
  currently 16 KB compatible.
- Performance matrix evidence records compile-cache-only wall time at 1150 ms
  and compile-cache plus process-prewarm wall time at 382 ms on the pulled Sony
  report, while numeric SLO enforcement remains partial.

## Capability Matrix

| Capability | Phase 9 level | Status |
| --- | --- | --- |
| Node 24.5 default runtime | stable | ready |
| Versioned C ABI adapter surface | partial | ready evidence, boundary migration remains |
| Node 24.17 promotion | experimental | blocked |
| Runtime artifact manifest/integrity/distribution | partial | bootstrap-only |
| 16 KB page-size readiness | partial | bootstrap-only |
| Compile cache | stable | ready |
| Startup snapshot | experimental | deferred |
| Process prewarm and performance SLO | partial | partial |
| Inspector, CPU profile, heap snapshot | experimental | release denial ready, positive tooling deferred |
| Isolation, broker, process pool | partial | bootstrap-only |
| WebAssembly basic loading | stable | ready |
| Controlled WASI facade | experimental | deferred |
| WASM plugin/native service plugin | experimental | gated |
| Native crash pipeline | partial | bootstrap-only |
| Chaos/security corpus | partial | bootstrap-only |
| Real npm corpus | stable | ready |
| Phase 9 user examples | stable | ready |

## Safety Boundary

Phase 9 preserves the Safe Node Profile:

- `.node` native addons remain denied.
- `child_process` remains denied.
- Raw `net` / `tls` / `dns` / `dgram` remain denied.
- Raw `wasi` / `node:wasi` remain denied.
- Production Inspector remains denied.
- `process.binding`, `process.dlopen`, and private runtime entry points remain
  denied.
- Scoped filesystem, Rhino compatibility, backend switches, and native
  one-shot behavior remain intact.
