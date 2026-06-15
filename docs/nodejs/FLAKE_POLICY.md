# AutoJs6 Node.js Flake Policy

This policy applies to Embedded Node release-candidate and GA gates. It exists to separate device or infrastructure noise from real Node runtime failures.

## Categories

### Infrastructure Flake

Build host, Gradle daemon, Android SDK, or filesystem failures outside the app/runtime under test.

Examples:

- Gradle daemon disappeared before executing tests.
- Android SDK tool path is temporarily unavailable.
- Host disk or antivirus temporarily prevents reading a generated APK.

### ADB Or Device Flake

Failures caused by Android device transport or setup before the test assertion starts.

Examples:

- `adb get-state` cannot reach an approved device.
- APK install fails because the device disconnects.
- Multiple devices are attached and no serial was selected.
- Instrumentation cannot start because the device is offline.
- A vendor background-start manager blocks `NativeNodeEmbeddedScriptService` before `script.enter`, and the same test passes after documented device preparation.

### Timing Threshold Flake

A performance or stress threshold is too tight for the selected device profile, while the report does not show monotonic resource growth, native crash, or failed runtime assertions.

Examples:

- A stress suite exceeds the local tool timeout on a low-performance device.
- A startup latency warning crosses a warning threshold but stays below the hard failure threshold.

### Real Runtime Flake

Failures from the Embedded Node runtime, bridge, packaging output, security policy, or script behavior.

Examples:

- Native crash, tombstone, ANR, or service process death.
- Scoped fs escape allowed or an expected `ERR_AUTOJS6_*` denial missing.
- `child_process`, native addon, raw network, inspector, or dangerous process API unexpectedly allowed.
- Packaged APK script launches but produces the wrong smoke marker.
- Resource leak assertions fail for RSS, heap, thread count, fd count, or bridge resource registry.

## Retry Rules

Retries are allowed only for infrastructure flakes and ADB/device flakes, and the retry must be recorded in the report.

Allowed retry examples:

- Re-run an install step after `adb` reports the approved device went temporarily offline.
- Re-run a host pull step after the device export file exists but `adb pull` fails due to transport loss.
- Re-run a gate after correcting a missing `ANDROID_SERIAL` or `-Pautojs.embeddedNode.adbSerial`.

Retries are not allowed for:

- security assertions
- scoped fs denial assertions
- resolver policy assertions
- native addon, child process, raw network, inspector, or process API denial assertions
- packaged smoke script failure
- packaged manifest or native library verification failure
- resource leak assertions
- bridge capability enforcement failures
- native crashes, ANRs, or service process deaths

## Reporting Requirements

Every RC or GA gate report must classify failures as one of:

- `infra-flake`
- `adb-device-flake`
- `timing-threshold-flake`
- `runtime-failure`
- `security-failure`
- `packaged-output-failure`
- `resource-leak-failure`

If a retry is used, the report must include:

- original failing command
- retry command
- device serial and model
- original failure category
- reason retry was allowed
- final result

## Release Gate Rules

A release gate cannot be marked green when:

- a security assertion failed, even once
- a packaged smoke script failed
- a resource leak assertion failed
- a native crash, ANR, or service process death occurred
- the only passing result came from retrying a runtime assertion failure

A release gate may be marked green after an infrastructure or ADB/device retry only when:

- the retry reason is documented
- the final retry passes without runtime/security/resource failures
- the report links or embeds the original failure evidence

## Device Profiles

Heavy stress gates should use an explicit device profile. The current primary full-gate device is Sony XQ-AT72, serial `QV710AF65F`, with profile `sony`.

Current-branch Sony gates use the temporary Node package identity and should keep the serial explicit:

```powershell
$env:ANDROID_SERIAL="QV710AF65F"
adb -s QV710AF65F shell cmd deviceidle whitelist +org.autojs.autojs6.nodejs
adb -s QV710AF65F shell cmd deviceidle whitelist +org.autojs.autojs6.nodejs.test
adb -s QV710AF65F shell am start -n org.autojs.autojs6.nodejs/org.autojs.autojs.launcher.AdaptiveIconAlias
```

Classify Sony device failures by the actual failing layer. ADB disconnection, unauthorized state, or missing app install may be `adb-device-flake`; runtime/security/resource assertions are never hidden behind device preparation.

OnePlus OPD2413 is excluded from current unattended gates because APK installation requires manual confirmation on that device.
