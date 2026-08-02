# Final Node Platform Reports

Phase 11 tracks AutoJs6 Node Platform graduation. The final stage is stricter
than previous phases: required internal blockers must be closed and all current
production-evidence reports must be ready before the final decision can be
`completed`.

The machine-readable registry currently records zero unresolved required
internal blockers. Those historical Phase 11/13 capability closures remain
`closed`. They do not, by themselves, complete R6: persistent process-global
runtime reuse and the required external `plugin` lifecycle make lifecycle,
crash, memory, ABI, packaged, and soak evidence freshness an independent Core
completion gate. The archived `ee97b790` host / `a904ee20` plugin candidate has
a complete four-device and 8h/24h R6 payload, but current successor corrections
change the host source identity. R6 therefore remains pending until matching
evidence is recollected and strict final/release verification passes from that
clean successor build.

Current entry points:

- [FINAL_INTEGRATION_DEFINITION.md](FINAL_INTEGRATION_DEFINITION.md)
- [FINAL_BLOCKER_REGISTRY.md](FINAL_BLOCKER_REGISTRY.md)
- [FINAL_BLOCKER_REGISTRY.json](FINAL_BLOCKER_REGISTRY.json)
- [../R6_ACCEPTANCE_EVIDENCE.md](../R6_ACCEPTANCE_EVIDENCE.md)

Host report tasks:

```powershell
.\gradlew.bat --console=plain :app:generateNodeFinalCapabilityTruth
.\gradlew.bat --console=plain :app:verifyNodeFinalFocusedGateTopology
.\gradlew.bat --console=plain :app:generateNodeFinalAdapterStatus
.\gradlew.bat --console=plain :app:generateNodeFinalCrashStatus
.\gradlew.bat --console=plain :app:generateNodeFinalPackageManagerStatus
.\gradlew.bat --console=plain :app:generateNodeFinalLongRunningStatus
.\gradlew.bat --console=plain :app:generateNodeFinalMultiInstanceStatus
.\gradlew.bat --console=plain :app:generateNodeFinalWorkerDecisionStatus
.\gradlew.bat --console=plain :app:generateNodeFinalSoakStatus
.\gradlew.bat --console=plain :app:generateNodeFinalProviderStatus
.\gradlew.bat --console=plain :app:generateNodeFinalAbiStatus
.\gradlew.bat --console=plain :app:generateNodeFinalSecurityStatus
.\gradlew.bat --console=plain :app:generateNodeFinalIntegrationSummary
```

Reports are written under `build/reports/nodejs`. The aggregate generation task
regenerates all final subreports before writing
`final-integration-summary.{json,md}`.

Strict final gates such as `verifyNodeFinalCapabilityTruth`,
`verifyNodeFinalAdapterGate`, `verifyNodeFinalCrashGate`,
`verifyNodeFinalPackageManagerGate`, `verifyNodeFinalLongRunningGate`,
`verifyNodeFinalMultiInstanceGate`, `verifyNodeFinalWorkerDecisionGate`,
`verifyNodeFinalSoakGate`, `verifyNodeFinalProviderGate`,
`verifyNodeFinalAbiGate`, `verifyNodeFinalSecurityGate`, and
`verifyNodeFinalIntegrationGate` must fail closed on missing, stale, blocked, or
failed required evidence. The registry no longer blocks them through an open
required capability item. On 2026-08-02 the regenerated aggregate accepted 10
of 13 subreports. Final-soak and the R6 aggregate both propagate the canonical
dirty-host identity blocker; final-provider also rejects its otherwise passing
Sony run because the new before/after provenance records that collection took
place while these corrections were uncommitted.

The final status graph regenerates host-only Phase 10/13 prerequisite reports.
The live-provider summary remains a separate debug-device prerequisite because
its required network experiment is forbidden in release tasks. Its schema binds
the result to a clean, stable host commit and snapshots exactly one 27-case
JUnit XML plus the UTP/aapt-selected app/androidTest APKs, selection logs, and
device bindings under a unique commit-and-run key. A hashed manifest and archived
summary make each attempt replayable, while canonical publication is atomic.
Final-provider reparses JUnit, validates manifest/summary agreement and artifact
real paths, and compares hashes plus source identity with the current checkout.
The final soak status consumes the current
R6 8-hour/24-hour reports through the same canonical R6 contract, including
source identity; the older Phase 13 final-soak paths remain historical Phase 13
gate inputs.

R6 uses four canonical device reports: Sony `arm64-v8a`, Sony
`armeabi-v7a`, a non-Sony physical OEM device on `arm64-v8a`, and an x86_64
emulator. `tools/nodejs/r6/run-device-evidence.js` is the production collector.
It requires clean, unchanged host and plugin commits, builds and installs a
fresh signed target-ABI plugin APK, compares installed APK bytes with fresh
build artifacts, and captures a fresh JUnit XML containing exactly the fixed
13 selectors with no failure, error, or skip. Hand-authored JSON and direct
focused Gradle runs do not provide that complete attestation.

The production soak runner fixes `quick` to 8 hours and `final` to 24 hours:

```powershell
node tools/nodejs/r6/run-production-soak.js --kind quick --serial DEVICE_SERIAL
node tools/nodejs/r6/run-production-soak.js --kind final --serial DEVICE_SERIAL
```

Short `--smoke` runs and `verifyNodeR6PersistentPluginSoakSmoke` are explicitly
non-archivable. They may validate the harness but cannot satisfy either long
soak requirement.

Use `generateNodeR6ProductionEvidenceStatus` to record the current blocked or
completed state without hiding missing evidence. Use
`verifyNodeR6ProductionEvidenceGate` for the strict decision. The final
integration summary consumes the R6 report, `verifyNodeFinalIntegrationGate`
fails closed on any missing, stale, dirty, mismatched, skipped, under-duration,
or failed requirement, and `verifyNodeReleaseGate` depends on that strict final
gate. See [R6_ACCEPTANCE_EVIDENCE.md](../R6_ACCEPTANCE_EVIDENCE.md) for the four
collector commands, exact selectors, artifacts, and canonical report paths.

The current conditional decisions are resolved for the Safe profile:
`worker_threads` is intentionally unsupported, controlled WASI is closed by
supporting pure WebAssembly while raw WASI stays denied, and `x86` is optional.
The Node 24.17 Android artifact lane remains deferred external runtime
maintenance and does not block completion on the Node 24.5 baseline.
