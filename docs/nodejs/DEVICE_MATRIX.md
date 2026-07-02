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
- `accessibility`: optional real-device accessibility smoke, requiring the AutoJs6 accessibility service to be enabled manually
- `screen_capture`: optional real-device screen-capture smoke, requiring manual approval of the Android MediaProjection prompt
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
- default package identity: `org.autojs.autojs6`
- side-by-side test identity: `org.autojs.autojs6.nodejs` with `-Pautojs.nodejs.identity=nodejsTest`
- preparation:

```powershell
$env:ANDROID_SERIAL="QV710AF65F"
adb -s QV710AF65F shell cmd deviceidle whitelist +org.autojs.autojs6
adb -s QV710AF65F shell cmd deviceidle whitelist +org.autojs.autojs6.test
adb -s QV710AF65F shell am start -n org.autojs.autojs6/org.autojs.autojs.launcher.AdaptiveIconAlias
```

Recommended command:

```powershell
$env:ANDROID_SERIAL="QV710AF65F"
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pautojs.nodejs.identity=production" "-Pautojs.nodejs.test.deviceProfile=sony"
```

For a side-by-side branch gate, add `-Pautojs.nodejs.identity=nodejsTest` and use the `.nodejs` package in the
preparation commands.

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

## Accessibility Smoke Profile

Use `accessibility` only for manual physical-device validation after enabling the current branch's AutoJs6 accessibility service in Android settings. It is intentionally excluded from `sony`, `smoke`, `full`, PR, nightly, and release gates.

- profile: `accessibility`
- primary device: Sony XQ-AT72 `QV710AF65F`
- use: controlled real-device `accessibility` provider smoke for `isEnabled`, `ensureEnabled`, `findByText`, selector `findOne`, selector `click`, and selector `longClick`
- optional extra: pass `-Pandroid.testInstrumentationRunnerArguments.autojs.nodejs.accessibilitySmoke.globalActions=true` only when it is safe for the test to trigger Back/Home/Recents
- P13-19 keeps optional accessibility smoke manual: the promotion gate records
  selector/provider evidence, but it still requires explicit user-side service
  enablement and does not modify Android accessibility settings.
- P14-12 keeps Android 12 and Android 15+ selector/action archives open until
  user-enabled service state, missing service, missing capability,
  timeout/cancellation, safe Back/Home/Recents, and unsupported
  gesture/root/Shizuku diagnostics are recorded.

Recommended command:

```powershell
$env:ANDROID_SERIAL="QV710AF65F"
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pautojs.nodejs.test.deviceProfile=accessibility"
```

## Screen Capture Smoke Profile

Use `screen_capture` only for manual physical-device validation after preparing the Sony test device and approving the Android screen-capture prompt. It is intentionally excluded from `sony`, `smoke`, `full`, PR, nightly, and release gates.

- profile: `screen_capture`
- primary device: Sony XQ-AT72 `QV710AF65F`
- use: real-device MediaProjection and image-frame smoke for permission request, one captured frame, dimension checks, bitmap decode/recycle, and capturer shutdown
- expected denial behavior: if the system prompt is denied or canceled, the optional test fails with a clear permission-denied message
- P13-20 keeps screen_capture smoke manual: the promotion gate records the
  optional Android capture lifecycle evidence, but the Node bridge
  MediaProjection provider remains unavailable until permission prompt,
  foreground disclosure, stop cleanup, and packaged behavior are promoted.
- P14-13 imports this smoke as an Activity-owned MediaProjection provider
  anchor and keeps Android 12 class, Android 15+ class, foreground
  disclosure/stop action, and task-removal/relaunch/crash archives open before
  any `media_projection` promotion.

Recommended command:

```powershell
$env:ANDROID_SERIAL="QV710AF65F"
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pautojs.nodejs.test.deviceProfile=screen_capture"
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
- Use `accessibility` and `screen_capture` only for manual capability-provider validation that requires device-side user action.
- Avoid low-performance devices for memory/lifecycle stress unless the task is explicitly classifying device-specific flake.
- Always record serial, model, profile, ABI, package identity, and command line in `docs/nodejs/reports/phase4/` or later release report directories.
