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
completion gate. R6 remains pending until the final reports are regenerated
from the current build and the required device evidence passes.

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
required capability item; R6 production-evidence freshness still blocks Core
completion until the current lifecycle and device matrix are revalidated.

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
