# Phase 10 Reports

Phase 10 is Real-World Project Runtime & Automation Activation. It starts by
freezing the runtime upgrade lane and turning packaged parity, capability truth,
skip audit, and focused gate coverage into repeatable evidence.

## First-Wave Gates

```powershell
.\gradlew.bat --console=plain :app:verifyNodePhase10RuntimeFreeze
.\gradlew.bat --console=plain :app:generateNodePhase10CapabilityTruth
.\gradlew.bat --console=plain :app:generateNodePhase10SkipAudit
.\gradlew.bat --console=plain :app:verifyNodePhase10PackagedParityHost
.\gradlew.bat --console=plain :app:verifyNodePhase10FocusedGateMatrix
.\gradlew.bat --console=plain :app:verifyNodePackagedParityGate "-Pautojs.nodejs.test.deviceProfile=sony"
```

`verifyNodePhase10Gate` aggregates the first-wave host gates, packaged parity,
package-manager boundary checks, multi-instance policy evidence, and adapter v1
ABI/export checks. It does not promote any blocked capability.

## Outputs

- `build/reports/nodejs/phase10-runtime-freeze.json`
- `build/reports/nodejs/phase10-runtime-freeze.md`
- `build/reports/nodejs/phase10-capability-truth.json`
- `build/reports/nodejs/phase10-capability-truth.md`
- `build/reports/nodejs/phase10-skip-audit.json`
- `build/reports/nodejs/phase10-skip-audit.md`
- `build/reports/nodejs/phase10-packaged-parity.json`
- `build/reports/nodejs/phase10-packaged-parity.md`
- `build/reports/nodejs/phase10-focused-gate-matrix.json`
- `build/reports/nodejs/phase10-focused-gate-matrix.md`

## Current Decisions

- `node24_5` remains the default runtime.
- Node 24.17 remains `waiting_for_community_fork`.
- Existing `libnode.so` artifacts must not change during Phase 10 feature work.
- Packaged ESM and local dynamic import use the existing Sony install-and-run
  smoke gate as the execution proof.
- The descriptor schema remains compatible with existing v1 projects while the
  version vector carries the v1.1 bootstrap, CJS loader, ESM loader, dynamic
  import, builtin profile, bridge protocol, capability manifest, and
  moduleSources schema fields required by the Phase 10 descriptor gate.
