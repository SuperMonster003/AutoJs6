# P13-05 Crash Evidence Automation

Status: partial runtime automation landed; remaining blockers remain open.

P13-05 now has a runtime crash marker store and a Phase 13 host gate:

- Android marker store: `app/src/main/java/org/autojs/autojs/engine/NodeCrashEvidenceStore.kt`
- Next-launch collector: `App.onCreate()` calls `NodeCrashEvidenceStore.collectPending(...)`
- Embedded script path: `NativeNodeEmbeddedScriptService` writes begin, terminal, timeout, and service-destroy marker states.
- Desktop/native process path: `NativeNodeProcessService` writes begin, terminal, exception, and service-destroy marker states.
- Bridge-provider crash assertion: `NodeCrashEvidenceStoreInstrumentationTest` verifies plugin crash diagnostics are recorded in marker and next-launch bundle form.
- Simulated native fatal running-marker assertion: `collectsRunningMarkerAfterSimulatedNativeFatalProcessDeath` verifies a `running` native marker is promoted to a next-launch bundle.
- Real fatal native chaos assertion: `collectsRunningMarkerAfterRealFatalNativeChaosProbeDeath` starts `NativeNodeEmbeddedProbeService` in `:nodejs_embedded`, writes a marker, injects `SIGKILL`, and verifies next-launch collection.
- Packaged launch-surface marker contract: `markerRecordsPackagedLaunchSurfaceForFutureCrashSmoke` verifies packaged long-running crash metadata can enter the marker schema.
- Packaged APK crash evidence smoke: `installAndRunCrashEvidenceEmbeddedNodePackagedApk` builds and installs a packaged `interactive_long_running` APK, verifies marker begin logs with `launchSurface=packaged_long_running`, force-stops the package, relaunches it, and verifies `App.onCreate` promotes the leftover marker into the next-launch bundle path. The focused device run passes with a fresh inrt template override.
- Signal-safe marker boundary assertion: `markerRecordsSignalSafeNativeMarkerBoundary` records the current boundary before a native fixed-size writer lands.
- Host collector: `tools/nodejs/phase13/collect-crash-evidence.js`
- Gradle task: `:app:collectNodePhase13CrashEvidence`

The marker schema includes `executionId`, `runtimeSlot`, `runtimeSlotId`, `abi`,
`profile`, `featureFlags`, `resourceSnapshot`, and `timelineTail`. A hard native
process crash leaves a `running` marker in app files; the next main-process launch
promotes that marker into a next-launch crash bundle.

Current remaining blocker items:

- Add native signal-safe fixed-size marker writer proof; the current gate only records the boundary and keeps the writer separate from JSON/JNI work.

Until those items pass, `crash_evidence_automation` remains open in
`docs/nodejs/final/FINAL_BLOCKER_REGISTRY.json`.
