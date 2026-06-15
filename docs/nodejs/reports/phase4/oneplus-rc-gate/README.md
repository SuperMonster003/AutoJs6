# Phase 4 OnePlus RC Gate Attempt

Date: 2026-06-15

Device:

- model: OnePlus OPD2413
- adb serial: `63751f63`

Command:

```powershell
$env:ANDROID_SERIAL = '63751f63'
.\gradlew.bat --console=plain :app:verifyEmbeddedNodeReleaseCandidate
```

Result:

- status: failed
- failing task: `:app:embeddedNodePackagedDiagnosticReport`
- category: `packaged-output-failure`
- runtime/device tests reached: no

Summary:

The first OnePlus RC gate attempt did not reach packaged smoke execution or memory/lifecycle stress. The release-candidate aggregate invoked `embeddedNodePackagedDiagnosticReport` without a concrete packaged APK path, and that standalone task intentionally requires `-Pautojs.embeddedNode.packagedApk=<apk>`.

Action:

- Keep standalone packaged APK diagnostics gated when an explicit packaged APK path is supplied.
- Make the aggregate packaged debug gate rely on `embeddedNodePackagedSmokeHostVerify` when no explicit packaged APK path is supplied, because that task verifies the success and diagnostics APKs exported by `EmbeddedNodePackagedApkSmokeTest`.

Captured files:

- `gradle-output.txt`
- `packaged-apk-output-verify.txt`
- `packaged-diagnostic-report.txt`
- `adb-devices-before.txt`
- `adb-devices-after.txt`
- `logcat-excerpt.txt`
- `exit-code.txt`
