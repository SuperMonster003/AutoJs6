# Embedded Node Device Matrix

This matrix defines which devices run which Node Profile v1 gates. It is intended to keep heavy stress work off unsuitable devices while preserving a reproducible release path.

## Gradle Device Profile

Use `autojs.nodejs.test.deviceProfile` to select a known instrumentation subset when running `connectedAppDebugAndroidTest`.

```powershell
.\gradlew.bat --console=plain :app:printNodeTestDeviceProfile "-Pautojs.nodejs.test.deviceProfile=oneplus"
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pautojs.nodejs.test.deviceProfile=oneplus"
```

Supported values:

- `oneplus`: primary full-gate physical device profile
- `emulator`: x86_64 emulator profile for resolver, corpus, and policy subsets
- `smoke`: short physical-device smoke profile
- `full`: expanded stress profile for nightly or release investigation

If `android.testInstrumentationRunnerArguments.class` is passed explicitly, it wins and the device profile does not override it. This keeps focused single-class and single-method commands deterministic.

## Primary Device

OnePlus OPD2413 is the primary full-gate device for the current branch.

- serial: `63751f63`
- profile: `oneplus`
- use: release-candidate verification, packaged APK smoke, lifecycle stress, reduced/full memory stress, bridge/security/corpus coverage
- preparation:

```powershell
adb -s 63751f63 shell cmd deviceidle whitelist +org.autojs.autojs6
adb -s 63751f63 shell cmd deviceidle whitelist +org.autojs.autojs6.test
adb -s 63751f63 shell am start -n org.autojs.autojs6/org.autojs.autojs.launcher.AdaptiveIconAlias
```

Recommended command:

```powershell
$env:ANDROID_SERIAL="63751f63"
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pautojs.nodejs.test.deviceProfile=oneplus"
```

## x86_64 Emulator

The x86_64 emulator profile is for deterministic resolver and compatibility checks when a physical device is unavailable.

- profile: `emulator`
- use: embedded MVP, CommonJS compatibility corpus, fixed real npm corpus, resolver fuzzer, scoped fs fuzzer
- not for: packaged APK install smoke as the primary signal, memory leak stress, Android/OEM service policy decisions

Recommended command:

```powershell
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pautojs.nodejs.test.deviceProfile=emulator"
```

## Smoke Device

Use `smoke` for a short secondary physical-device sanity check.

- profile: `smoke`
- use: embedded MVP, bridge smoke, packaged toast bridge smoke
- not for: release sign-off, memory stress, lifecycle stress, full packaged smoke matrix

Recommended command:

```powershell
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pautojs.nodejs.test.deviceProfile=smoke"
```

## Full Stress Profile

Use `full` for nightly gates or targeted release investigations.

- profile: `full`
- use: broad embedded/bridge/security/corpus/stress coverage
- includes worker resource stress in addition to memory leak stress
- must be run on a prepared high-performance device

Recommended command:

```powershell
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pautojs.nodejs.test.deviceProfile=full"
```

## Excluded Device

Sony XQ-AT72 is excluded for this branch's Node Profile v1 gates because it is used for another AutoJs6 branch and can hit signature conflicts.

Do not use it for current-branch install, packaged smoke, or release-candidate gates unless the conflicting branch is removed and the exclusion is updated here.

## Selection Rules

- Use OnePlus OPD2413 for release-candidate and GA publication evidence.
- Use x86_64 emulator for resolver/corpus/policy feedback, not for final packaged APK confidence.
- Use smoke profile for quick validation on optional secondary devices.
- Avoid low-performance devices for memory/lifecycle stress unless the task is explicitly classifying device-specific flake.
- Always record serial, model, profile, ABI, and command line in `docs/nodejs/reports/phase4/` or later release report directories.
