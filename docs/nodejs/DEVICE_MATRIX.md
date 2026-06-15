# Embedded Node Device Matrix

This matrix defines which devices run which Node Profile v1 gates. It is intended to keep heavy stress work off unsuitable devices while preserving a reproducible release path.

## Gradle Device Profile

Use `autojs.nodejs.test.deviceProfile` to select a known instrumentation subset when running `connectedAppDebugAndroidTest`.

```powershell
.\gradlew.bat --console=plain :app:printNodeTestDeviceProfile "-Pautojs.nodejs.test.deviceProfile=sony"
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pautojs.nodejs.test.deviceProfile=sony"
```

Supported values:

- `sony`: primary full-gate physical device profile
- `emulator`: x86_64 emulator profile for resolver, corpus, and policy subsets
- `smoke`: short physical-device smoke profile
- `full`: expanded stress profile for nightly or release investigation

If `android.testInstrumentationRunnerArguments.class` is passed explicitly, it wins and the device profile does not override it. This keeps focused single-class and single-method commands deterministic.

Fuzzer budget selection is independent:

- `autojs.nodejs.fuzzerBudget=quick`: short PR/emulator feedback
- `autojs.nodejs.fuzzerBudget=normal`: focused local default
- `autojs.nodejs.fuzzerBudget=extended`: nightly, release, and failure investigation

The `full` profile and nightly/release gate tasks default to `extended`; `emulator` and `smoke` default to `quick`.

## Primary Device

Sony XQ-AT72 is the primary full-gate device for the current branch.

- serial: `QV710AF65F`
- profile: `sony`
- Android: 12 / API 31
- ABI: `arm64-v8a`
- use: release-candidate and GA verification, packaged APK smoke, lifecycle stress, reduced/full memory stress, bridge/security/corpus coverage
- current branch package identity: `org.autojs.autojs6.nodejs`
- preparation:

```powershell
$env:ANDROID_SERIAL="QV710AF65F"
adb -s QV710AF65F shell cmd deviceidle whitelist +org.autojs.autojs6.nodejs
adb -s QV710AF65F shell cmd deviceidle whitelist +org.autojs.autojs6.nodejs.test
adb -s QV710AF65F shell am start -n org.autojs.autojs6.nodejs/org.autojs.autojs.launcher.AdaptiveIconAlias
```

Recommended command:

```powershell
$env:ANDROID_SERIAL="QV710AF65F"
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pautojs.nodejs.test.deviceProfile=sony"
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
- use: broad embedded/bridge/security/corpus/stress coverage, including resolver/scoped fs/bridge RPC fuzzers
- includes worker resource stress in addition to memory leak stress
- must be run on a prepared high-performance device

Recommended command:

```powershell
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pautojs.nodejs.test.deviceProfile=full"
```

## Excluded Device

OnePlus OPD2413 is excluded from current unattended Node Profile v1 gates because APK installation requires manual confirmation on that device. Do not use it for current-branch install, packaged smoke, nightly, release-candidate, or GA gates.

## Selection Rules

- Use Sony XQ-AT72 for release-candidate and GA publication evidence.
- Do not use OnePlus OPD2413 for unattended current-branch gates.
- Use x86_64 emulator for resolver/corpus/policy feedback, not for final packaged APK confidence.
- Use `smoke` profile for quick validation on optional secondary devices.
- Avoid low-performance devices for memory/lifecycle stress unless the task is explicitly classifying device-specific flake.
- Always record serial, model, profile, ABI, package identity, and command line in `docs/nodejs/reports/phase4/` or later release report directories.
