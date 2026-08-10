# Lua Runtime Native Device Evidence - 2026-08-10

Evidence level: **PASS, DEVICE PARTIAL / PROVIDER DISABLED**.

This checkpoint executes the pinned PUC Lua JNI core directly from an
instrumentation process. It does not enable, discover, or bind the production
INFO/RUNTIME services and is not cross-process Binder/PFD acceptance.

## Frozen Inputs

- sibling revision: `52076d7a5b97630acdc86652f39703eba058a46a`
- sibling `VERSION_BUILD`: `14`, equal to its 14-commit history
- host protocol revision: `ae422391759810ce9dbbf0bc119bb47834c66b85`
- native build flags: `LUA_NATIVE_ENABLED=true`,
  `LUA_PROVIDER_ENABLED=false`
- JVM gate: 9 suites, 38/38, zero failure/error/skip
- static/hostile Python gate: 21/21
- debug signer SHA-256:
  `2e64822e13a6c80c12e1c4b47e8fb32d1e9334526289da75777b7a79145de4b8`
- installed arm64-v8a target APK SHA-256:
  `f35bef79e9c151a9bfd7edc6ea6afdf496fa1ac26a6d57496e4a33dee0c4bf22`
- installed test APK SHA-256:
  `f3ac6980f17441c25f350843cce9fa2b8af4ad82f7b1bb0a7cf9b4b1aa8056e0`

The APKs pulled back from the device matched those two local hashes byte for
byte. The artifact gate also passed exact split/universal ABI inventory, one
common signer, all ELF LOAD segments at `0x4000`, and APK ZIP 16 KiB alignment.

## Device

- serial: `QV710AF65F`
- Android API: `31`
- ABI list: `arm64-v8a,armeabi-v7a,armeabi`
- page size: `4096`
- fingerprint:
  `Sony/XQ-AT72_CN/XQ-AT72:12/58.2.A.10.44A/058002A0100044A0891821322:user/release-keys`
- evidence recorded through: `2026-08-10T16:44:27+08:00`

Before installation, both the target and test packages were absent and no
instrumentation was active. Every method was selected explicitly and executed
in a separately started instrumentation run.

## Results

| Method | Result | Device time |
| --- | --- | ---: |
| `providerRemainsDisabledDuringNativeTests` | PASS | 0.012 s |
| `nativeCoreAndRunnerReturnV1Scalars` | PASS | 0.016 s |
| `syntaxAndRuntimeErrorsAreClassified` | PASS | 0.019 s |
| `unsupportedResultsAndArgumentsFailClosed` | PASS | 0.020 s |
| `infiniteLoopIsCancelledByHook` | PASS | 0.048 s |
| `infiniteLoopHonoursDeadline` | PASS | 0.073 s |
| `allocatorLimitFailsClosedAndTheProcessRemainsReusable` | PASS | 0.052 s |

Each run reported `OK (1 test)` and `INSTRUMENTATION_CODE: -1`; there were zero
failure, assumption, ignored, crash, or timeout results. The allocator case
provoked a Lua-owned memory-limit failure and then returned `7` in the same
process. It is not an Android process-OOM test.

## Containment And Restoration

- Both generated and packaged provider gates remained false.
- PackageManager discovery returned no enabled INFO or RUNTIME service.
- The instrumentation did not call `bindService()` for either production
  service.
- Gradle invocations used a 15-second shared-client poll with a 9-minute upper
  bound; detected work in other Codex worktrees was allowed to finish before
  this sibling started Gradle.
- After the final method, the test and target packages were force-stopped and
  uninstalled successfully.
- Final state: both packages absent, no target PID, and no active
  instrumentation.

## Remaining Gates

This evidence does not establish:

- actionless INFO/RUNTIME binding or production session execution;
- source-PFD ownership through the real provider;
- watchdog termination of a native/PFD wedge, Binder death, rebind, a new PID,
  or a successful post-recovery execution;
- native crash containment, Android process OOM, release signing, API 24-36,
  x86_64 device execution, or a 16 KiB-page device.

Provider discovery therefore remains disabled.
