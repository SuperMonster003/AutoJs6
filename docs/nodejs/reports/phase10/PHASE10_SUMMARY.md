# Phase 10 Summary

S10-56 adds the final Phase 10 aggregate summary:

```powershell
.\gradlew.bat --console=plain :app:generateNodePhase10Summary
.\gradlew.bat --console=plain :app:verifyNodePhase10Gate "-Pautojs.nodejs.test.deviceProfile=sony"
```

The summary writes:

- `build/reports/nodejs/phase10-summary.json`
- `build/reports/nodejs/phase10-summary.md`

## Current Result

The current Phase 10 summary decision is `completed_with_recorded_blockers`.
`node24_5` remains the default runtime, Node 24.17 promotion remains blocked,
and no blocked capability is promoted.

Current aggregate evidence:

- Examples: 55/55, including 13 Phase 10 examples.
- Real npm corpus: 50/50 fixtures, +20 from the Phase 9 baseline.
- Security corpus: 11 attack surfaces, 7 covered and 4 blocked by policy.
- Sony focused Phase 10 gate: 25 tests, 0 failures, 0 errors, 0 skipped.
- Policy violations: 0.

It combines the Phase 10 host reports, focused gate matrix, capability truth,
security corpus, Node examples report, real npm corpus manifest, and currently
available focused device XML. It fails if the example catalog remains below 55
or the real npm corpus remains below 50 fixtures.

The final decision keeps `node24_5` as the default runtime. Node 24.17,
`adapter_v1`, raw WASI, `worker_threads`, unrestricted package install, native
addons, raw network modules, unrestricted `child_process`, and production
Inspector remain blocked or disabled unless a later phase adds separate
implementation and security evidence.

Recorded blockers:

- Image: full live image provider remains blocked; fail-closed behavior is
  covered.
- Workers: `worker_threads` remains disabled by default and blocked by native
  availability.
- WASI: raw `node:wasi` and controlled `autojs6:wasi` promotion remain disabled
  or deferred.
- Adapter v1: ABI/export gates are ready, but the Node 24.5 adapter lifecycle is
  still `surface_only`, so `legacy_jni` remains the default execution path.
- Multi-instance: reserved slots exist, but effective active execution remains
  primary-only.
