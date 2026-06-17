# Phase 4 Sony Device Transition

Date: 2026-06-16

This record switches the current unattended Node Profile v1 GA gate target from OnePlus OPD2413 to Sony XQ-AT72.

## Reason

OnePlus OPD2413 requires manual confirmation during APK installation, so it is no longer suitable for unattended current-branch install, packaged smoke, nightly, release-candidate, or GA gates.

The current branch now uses the temporary package identity `org.autojs.autojs6.nodejs`, allowing Sony XQ-AT72 to run this branch side by side with other AutoJs6 branches.

## Selected Device

- serial: `QV710AF65F`
- manufacturer: Sony
- model: XQ-AT72
- product: XQ-AT72_CN
- Android: 12 / API 31
- ABI: `arm64-v8a`
- device profile: `sony`
- app package: `org.autojs.autojs6.nodejs`
- test package: `org.autojs.autojs6.nodejs.test`

## Gate Commands

```powershell
$env:ANDROID_SERIAL="QV710AF65F"
.\gradlew.bat --console=plain :app:printNodeTestDeviceProfile "-Pautojs.nodejs.test.deviceProfile=sony"
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pautojs.nodejs.test.deviceProfile=sony"
.\gradlew.bat --console=plain :app:verifyNodeReleaseGate "-Pautojs.nodejs.test.deviceProfile=sony"
```

Focused release-candidate rerun:

```powershell
$env:ANDROID_SERIAL="QV710AF65F"
.\gradlew.bat --console=plain :app:verifyEmbeddedNodeReleaseCandidate "-Pautojs.embeddedNode.adbSerial=QV710AF65F"
```

## Preparation

```powershell
adb -s QV710AF65F shell cmd deviceidle whitelist +org.autojs.autojs6.nodejs
adb -s QV710AF65F shell cmd deviceidle whitelist +org.autojs.autojs6.nodejs.test
adb -s QV710AF65F shell am start -n org.autojs.autojs6.nodejs/org.autojs.autojs.launcher.AdaptiveIconAlias
```

The OnePlus evidence directories under `docs/nodejs/reports/phase4/` remain valid historical evidence, but they are no longer the current unattended publication target.
