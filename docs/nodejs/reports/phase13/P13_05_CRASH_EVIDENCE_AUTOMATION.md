# P13-05 Crash Evidence Automation

Status: partial runtime automation landed; final blocker remains open.

P13-05 now has a runtime crash marker store and a Phase 13 host gate:

- Android marker store: `app/src/main/java/org/autojs/autojs/engine/NodeCrashEvidenceStore.kt`
- Next-launch collector: `App.onCreate()` calls `NodeCrashEvidenceStore.collectPending(...)`
- Embedded script path: `NativeNodeEmbeddedScriptService` writes begin, terminal, timeout, and service-destroy marker states.
- Desktop/native process path: `NativeNodeProcessService` writes begin, terminal, exception, and service-destroy marker states.
- Bridge-provider crash assertion: `NodeCrashEvidenceStoreInstrumentationTest` verifies plugin crash diagnostics are recorded in marker and next-launch bundle form.
- Simulated native fatal running-marker assertion: `collectsRunningMarkerAfterSimulatedNativeFatalProcessDeath` verifies a `running` native marker is promoted to a next-launch bundle.
- Packaged launch-surface marker contract: `markerRecordsPackagedLaunchSurfaceForFutureCrashSmoke` verifies packaged long-running crash metadata can enter the marker schema.
- Signal-safe marker boundary assertion: `markerRecordsSignalSafeNativeMarkerBoundary` records the current boundary before a native fixed-size writer lands.
- Host collector: `tools/nodejs/phase13/collect-crash-evidence.js`
- Gradle task: `:app:collectNodePhase13CrashEvidence`

The marker schema includes `executionId`, `runtimeSlot`, `runtimeSlotId`, `abi`,
`profile`, `featureFlags`, `resourceSnapshot`, and `timelineTail`. A hard native
process crash leaves a `running` marker in app files; the next main-process launch
promotes that marker into a next-launch crash bundle.

Current remaining blocker items:

- Add real fatal native chaos injection evidence; the current gate proves next-launch collection of a simulated fatal `running` marker, not actual process-death injection.
- Add real packaged APK crash evidence smoke; the current gate proves packaged launch metadata can be written into the marker contract.
- Add native signal-safe fixed-size marker writer proof; the current gate only records the boundary and keeps the writer separate from JSON/JNI work.

Until those items pass, `crash_evidence_automation` remains open in
`docs/nodejs/final/FINAL_BLOCKER_REGISTRY.json`.
