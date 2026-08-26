# DEX Compiler R5 Diagnostic Device Evidence - 2026-08-26

This report closes the authorized-device evidence requirement for Roadmap R5.1.
It records one focused real-provider failure/recovery scenario on each target,
plus current-build cancellation and BUSY coverage on the preferred physical
device. It is not the R5.3 V1.1 classpath matrix or a release-candidate gate.

## Scope And Baselines

- Host production baseline: `4e58791238427c309ae5d4bbad07b9bc9d2a23d4`.
- Plugin production baseline: `4d08a612ae1d87059c28de98a66b8d3020763ac9`.
- Date: 2026-08-26, Asia/Shanghai.
- Preferred physical target: Sony XQ-AT72, serial `QV710AF65F`.
- AVD targets: `AVD_API_24` and `AVD_API_25`.
- All ADB operations named an explicit target with `adb -s <serial>`.
- Packages were cleanly uninstalled before each run and removed again after
  each run. No device user was created, removed, or switched.

The host repository contained unrelated in-progress Explorer/Archive changes
while this report was prepared. They were not included in the APKs, test
changes, staging set, or evidence commits described here.

## Paired Artifacts

| Artifact | Bytes | SHA-256 |
| --- | ---: | --- |
| `app/build/outputs/apk/app/debug/autojs6-v6.8.0-x86.apk` | 46,177,867 | `a07e69e20f9a14fafe1c35c1db03604b3103f5a5f4b9894c2b88f5cf666dba69` |
| `app/build/outputs/apk/app/debug/autojs6-v6.8.0-arm64-v8a.apk` | 46,219,429 | `fca09ff81ba528d7f1b45803a0e1ee38a484ef779c6b008ae20790aa70a05b2c` |
| `app/build/outputs/apk/androidTest/app/debug/app-app-debug-androidTest.apk` | 1,852,928 | `327cc8c5c97f90fde92c2c7a8188e67c4e45d63114785f59aed13f371b47af57` |
| `app/build/outputs/apk/debug/autojs6-plugin-dex-compiler-v1.0.0.apk` | 11,009,748 | `1cf2b74de1ab9ae387171a31073830872c0ac4c693f349a40e403d4d7d1c782b` |

The last path is relative to the sibling DEX Compiler plugin repository; the
other paths are relative to AutoJs6. `apksigner verify --print-certs` reported
the same V2 signer certificate SHA-256 for all four artifacts:
`31a681fcfffb3e428420cae280ded89292b12a3b0f59e19b7a73e32a8ae4c213`.

The installed package identities were:

- Host: `org.autojs.autojs6`
- Instrumentation: `org.autojs.autojs6.test`
- Provider: `io.github.supermonster003.autojs6.plugin.dexcompiler`
- Runner: `org.autojs.autojs6.test/androidx.test.runner.AndroidJUnitRunner`

## R5 Failure And Recovery Gate

The focused method was:

```text
org.autojs.autojs.core.plugin.dex.DexCompilerRealProviderAndroidTest#realProviderFailureCarriesR5DiagnosticsAndNextProductionLoadRecordsRoute
```

Each invocation passed
`-e autojs.dexCompiler.realProvider.enabled true`. The test uses the real
production coordinator and real paired provider. It submits a JAR containing a
malformed class, validates the structured remote failure, then loads a valid
fixture through `AndroidClassLoader.loadJarWithExperimentalCompiler` and repeats
the coordinator request to verify the exact cache hit.

The assertions cover all of the following:

- The failure is `COMPILATION_FAILED/COMPILATION`, contains an ERROR diagnostic,
  and passes `DexCompilerValidation.validateDiagnostics`.
- Retained diagnostic bytes include code, message, optional origin/entry, and
  optional numeric position fields and stay within the 64 KiB protocol ceiling.
- The diagnostic exposes logical origin `program` and canonical entry
  `org/autojs/fixture/dexcompiler/Broken.class`.
- Diagnostic messages contain neither the host input path nor an Android
  private absolute path or ISO control character.
- A failed provider attempt does not create a successful adopted-route record.
  Its bounded process-local failure details retain only stable failure tokens.
- The following valid production load resolves the fixture and returns `42`,
  records route `PLUGIN` without a fallback hint, and does not overwrite the
  earlier provider-failure snapshot.
- The repeated valid coordinator request is an exact-generation cache hit and
  its generation is invalidated during cleanup.

## Target Results

| Target | Serial during run | API | ABI | Result | Sanitized evidence |
| --- | --- | ---: | --- | --- | --- |
| `AVD_API_24` | `emulator-5554` | 24 | x86 | PASS, `OK (1 test)`, 0 skipped | `PASS sdk=24 abi=x86 diagnostics=1 bytes=117 origins=1 entries=1 positions=0 route=PLUGIN` |
| `AVD_API_25` | `emulator-5554` | 25 | x86 | PASS, `OK (1 test)`, 0 skipped | `PASS sdk=25 abi=x86 diagnostics=1 bytes=117 origins=1 entries=1 positions=0 route=PLUGIN` |
| Sony XQ-AT72 | `QV710AF65F` | 31 | arm64-v8a | PASS, `OK (1 test)`, 0 skipped | `PASS sdk=31 abi=arm64-v8a diagnostics=1 bytes=117 origins=1 entries=1 positions=0 route=PLUGIN` |

All three instrumentation processes ended with
`INSTRUMENTATION_STATUS_CODE: 0` and `INSTRUMENTATION_CODE: -1`. API 24 and API
25 exercise the provider's CLI-compatible D8 callback path; API 31 exercises
the `D8Command` path. The malformed fixture did not cause D8 to emit source
position metadata, so `positions=0` is expected. Optional position encoding and
validation remain covered by the paired JVM protocol/provider suites; this
focused device gate does not claim a position-bearing D8 callback.

## Physical-Device Cancellation And BUSY Gates

Two additional methods were run independently on `QV710AF65F` with the same
paired APK hashes:

| Method | Result | Acceptance covered |
| --- | --- | --- |
| `DexCompilerRealProviderLifecycleAndroidTest#blockedRealSessionRejectsBusyAndOwnsEveryDescriptorAndTerminal` | PASS, `OK (1 test)`, 0 skipped, 2.940 s | A blocked real session produces one structured retryable `BUSY/QUEUE` terminal for the second caller; repeated cancellation produces one cancellation terminal, closes output at EOF, releases the process gate, and permits a new session. |
| `DexCompilerProductionRoutingAndroidTest#cancelledProductionDecisionNeverStartsLocalCompiler` | PASS, `OK (1 test)`, 0 skipped, 0.038 s | A cancelled production decision surfaces `ScriptInterruptedException`, reaches the decision boundary once, and starts no built-in compiler fallback. |

The lifecycle invocation explicitly passed both
`autojs.dexCompiler.realProviderLifecycle.enabled=true` and
`autojs.dexCompiler.realProviderLifecycle.serial=QV710AF65F`. The production
cancellation gate was rerun alone so its record contains no skipped companion
test.

Two setup-only attempts are deliberately excluded from the PASS totals. The
first omitted the lifecycle opt-in and was skipped with status `-4`; the second
supplied the opt-in but omitted the required declared serial and failed the
test's pre-dispatch safety assertion with status `-2`. Neither entered the real
lifecycle scenario. The corrected invocation above is the acceptance result.

## Cleanup And Boundaries

- After every accepted run, package enumeration confirmed all three package
  identities absent (`PACKAGES_ABSENT=3/3`).
- Both AVD processes were stopped with `adb -s emulator-5554 emu kill` after
  their respective runs. No AVD snapshot was saved.
- The physical device remained on user 0. User IDs 0 and 10 were present before
  and after every physical-device run; the topology was unchanged.
- This report closes the R5.1 minimum device evidence and, together with the
  R5 JVM localization tests, closes the diagnostic-coverage prerequisite in
  the R5.2 default-enable checklist.
- It does not close the performance/resource, V1.1 classpath matrix, full
  release fallback-drill, usability, or publishing prerequisites. The compiler
  route remains default-off and explicitly selected.
