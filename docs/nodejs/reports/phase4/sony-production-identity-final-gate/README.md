# Phase 4 Sony Production Identity Final Gate

Date: 2026-06-17

Commit under test: `857e6e82`

Device:

- model: Sony XQ-AT72
- adb serial: `QV710AF65F`
- Android: 12 / API 31
- primary ABI: `arm64-v8a`
- device profile: `sony`
- identity profile: `production`
- app package: `org.autojs.autojs6`
- app label: `AutoJs6`
- test package: `org.autojs.autojs6.test`

## Result

- status: passed
- command: `:app:verifyNodeReleaseGate "-Pautojs.nodejs.identity=production" "-Pautojs.nodejs.test.deviceProfile=sony" "-Pautojs.nodejs.fuzzerBudget=normal"`
- duration: `2h 2m 10s`
- Android test device: `XQ-AT72 - 12`
- Android test XML: `310` tests, `0` failures, `0` errors, `2` expected skips
- Gradle stderr: empty
- final Gradle tasks: `:app:verifyNodeReleaseGate`, `:app:generateNodeNightlyReport`
- packaged smoke aggregate: ready
- production release manifest identity: `org.autojs.autojs6` / `AutoJs6`
- packaged template SHA-256: `f206d9c9b18f0202161349ce244efa1b98e4ebd9b07da5ac1bbf2f92bad8db54`

## Evidence

Generated local evidence from this run:

- raw Gradle log: `build/reports/nodejs/final-identity-gate/full-gate-857e6e82/gradle-stdout.log`
- Gradle stderr log: `build/reports/nodejs/final-identity-gate/full-gate-857e6e82/gradle-stderr.log`
- command record: `build/reports/nodejs/final-identity-gate/full-gate-857e6e82/command.txt`
- Android test XML: `app/build/outputs/androidTest-results/connected/debug/flavors/app/TEST-XQ-AT72 - 12-_app-app.xml`
- release manifest: `app/build/reports/nodejs/release-manifest.json`
- release manifest Markdown: `app/build/reports/nodejs/release-manifest.md`
- nightly report: `build/reports/nodejs/nightly-report.json`
- Node Doctor report: `app/build/reports/nodejs/doctor/node-doctor-report.json`
- ABI size report: `app/build/reports/nodejs/abi-size-report.md`

Device cleanup after the gate left no `org.autojs.autojs6`, `org.autojs.autojs6.test`, or `org.autojs6.packaged.*`
packages from the run. The Sony device still had unrelated pre-existing packages `org.autojs.autojs6.ace` and
`org.autojs.apkbuilderplugin`.

## Expected Skips

- `EmbeddedScriptMvpInstrumentationTest#disabledBuildGateReturnsActionableMessage` is skipped because this build has embedded script execution enabled.
- `EmbeddedNodePackagedApkSmokeTest#installAndRunExperimentalEsmEmbeddedNodePackagedApk` is skipped because Node Profile v1 keeps ESM and dynamic import experimental and default-off.

The Gradle progress stream printed `311/310` after the memory-stress tail, but the Android test XML is authoritative and
records `310` tests with zero failures and zero errors.

## Release Notes

`assembleAppDebug` auto-incremented local `version.properties` during the gate. The generated release manifest from this
run therefore records `VERSION_BUILD=3924` and `BUILD_TIME=1781651034113`; the tracked `version.properties` file was
restored afterward. Before creating a public tag, select the approved final version state deliberately and regenerate the
release manifest with:

```powershell
.\gradlew.bat --console=plain :app:generateNodeProfileReleaseManifest "-Pautojs.nodejs.identity=production" "-Pautojs.nodejs.releaseTag=node-profile-v1.0.0" "-Pautojs.nodejs.releaseIdentityStatus=production-identity-final-gate-passed"
```
