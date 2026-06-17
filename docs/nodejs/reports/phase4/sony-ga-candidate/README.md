# Phase 4 Sony GA Candidate Gate

Date: 2026-06-16

Device:

- model: Sony XQ-AT72
- adb serial: `QV710AF65F`
- Android: 12 / API 31
- primary ABI: `arm64-v8a`
- device profile: `sony`
- app package: `org.autojs.autojs6.nodejs`
- test package: `org.autojs.autojs6.nodejs.test`

## Result

- status: passed
- command: `:app:verifyNodeReleaseGate "-Pautojs.nodejs.test.deviceProfile=sony" "-Pautojs.nodejs.fuzzerBudget=normal"`
- duration: `2h 5m 30s`
- Android test device: `XQ-AT72 - 12`
- Android tests: `310`
- failures: `0`
- errors: `0`
- skipped: `2`
- memory stress scenarios: `7`
- memory stress failures: `0`
- packaged smoke aggregate ready: `true`
- crash buffer after final aggregate: clean for foreground-service startup, fatal exception, request spool, and embedded service crash markers

## Gate Coverage

The aggregate gate ran the host release checks, packaged template checks, packaged smoke host verification, ABI size report, compatibility profile checks, release manifest generation, nightly report generation, and the Sony connected instrumentation profile.

Key generated evidence from this run:

- raw Gradle log: `app/build/reports/nodejs/phase4/sony-ga-candidate/verify-node-release-gate.stdout.log`
- Android test XML: `app/build/outputs/androidTest-results/connected/debug/flavors/app/TEST-XQ-AT72 - 12-_app-app.xml`
- nightly report: `build/reports/nodejs/nightly-report.md`
- memory stress report: `app/build/reports/nodejs/perf/embedded-node-memory-leak-stress-1781588688860.json`
- ABI size report: `app/build/reports/nodejs/abi-size-report.md`

The nightly report was generated while the Task 128 changes were still uncommitted, so it is GA-candidate evidence, not the final tagged publication manifest.

## Expected Skips

- `EmbeddedScriptMvpInstrumentationTest#disabledBuildGateReturnsActionableMessage` is skipped because this build has embedded script execution enabled.
- `EmbeddedNodePackagedApkSmokeTest#installAndRunExperimentalEsmEmbeddedNodePackagedApk` is skipped because Node Profile v1 keeps ESM and dynamic import experimental and default-off.

`NodeWorkerThreadsResourceStressInstrumentationTest` is not part of the default full device profile unless `autojs.nodejs.worker_threads.experimental=true`, because worker threads remain experimental and default-off for GA.

## Invalidated Attempt

An earlier Sony full-profile run was invalidated because unrelated `org.autojs.autojs6.ace` install/instrumentation activity overlapped the packaged smoke marker window. The affected packaged launch-failure method and the packaged tail methods were rerun cleanly before the final aggregate gate, and the final `verifyNodeReleaseGate` pass above is the valid Task 128 evidence.
