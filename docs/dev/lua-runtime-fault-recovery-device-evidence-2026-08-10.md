# Lua Runtime Fault-Recovery Device Evidence - 2026-08-10

## Scope

This checkpoint validates the provider-disabled debug fault harness in the
independent `AutoJs6-Plugin-Lua-Runtime` repository. It does not enable or bind
the production INFO/RUNTIME components.

Both methods cross the Android Binder boundary into a dedicated
`:lua_runtime` process. The instrumentation process sends a read-only regular
source PFD plus remote callback and broker Binders to an `ILuaRuntimeProvider`,
receives a remote `ILuaExecutionSession`, and calls `start()`. Execution then
uses the manager, session controller, zero-backlog worker, and process-watchdog
implementations shared with the production provider. Fault tokens enter a
debug-only runner shim; the recovery `return 7` enters the real native runner.

## Immutable Build And Artifact Gate

- Sibling revision:
  `e8ed2bdc4d72d06314354aefb1e1a5f36b8343d5`
- Commit count and `VERSION_BUILD`: `15`
- Canonical Gradle invocation: one clean, no-cache, rerun-tasks invocation
  after two idle Gradle-client samples; `100/100` actionable tasks executed.
- JVM result: `38/38`, zero failures, errors, or skips.
- Static/hostile verifier result: `23/23`.
- Generated flags: native enabled, debug fault harness enabled, production
  provider disabled.
- Debug artifact gate: PASS for arm64-v8a and x86_64, 16 KiB ELF LOAD and APK
  ZIP alignment, and exact fault-symbol admission.
- Unsigned release-variant exclusion gate: PASS. Its generated BuildConfig,
  merged manifest, fresh compiled classes/JARs, and both native ABIs contain no
  fault service/class/JNI symbol. This is not a signed release APK claim.

Reviewed APKs:

- arm64-v8a target:
  `1c1650b044842f5ef7cd198c6b87be8501fe25b6f9b175b09039001c1c136d03`
- Android-test APK:
  `5e29fdfe272fcb75769760179fa272fa0d42fa54d4535d72c33c20109d797acf`
- universal debug:
  `cdfc7bab0abbe54f08d4d783486092faf7f0444ecee022ef988500556fd31a83`
- x86_64 debug:
  `f4f9641fa352fce511a7de4eb4376f5794a6c0edb19cfea25a8f679f34d5eed2`
- single debug signer certificate SHA-256:
  `2e64822e13a6c80c12e1c4b47e8fb32d1e9334526289da75777b7a79145de4b8`

The installed target and test APKs were each pulled back from the device and
matched the reviewed local bytes exactly before instrumentation started.

## Device Preflight

- Serial: `QV710AF65F`
- Android API: `31`
- ABI list: `arm64-v8a,armeabi-v7a,armeabi`
- Fingerprint:
  `Sony/XQ-AT72_CN/XQ-AT72:12/58.2.A.10.44A/058002A0100044A0891821322:user/release-keys`
- Target package:
  `io.github.supermonster003.autojs6.plugin.lua.runtime`
- Test package:
  `io.github.supermonster003.autojs6.plugin.lua.runtime.test`
- Runner:
  `androidx.test.runner.AndroidJUnitRunner`

Android users `0` and `10` were enumerated. Both packages had zero current or
uninstalled package records for every user; their processes and runner were
also absent before the canonical installation. No other mutating or
instrumentation ADB client targeting this serial was active. A preliminary
install verification was deliberately aborted before instrumentation because a
vendor-specific `dumpsys package` text check did not enumerate the debug
service; both packages were immediately uninstalled and absence was reverified.
The canonical installation then repeated the all-user preflight and used APK
manifest, byte, signer, runner, and explicit-bind behavior instead of that
non-portable text format.

## Separately Selected Methods

Every method was selected explicitly with `-e class` and run in a separate
instrumentation invocation. The acceptance wrapper required both
`OK (1 test)` and `INSTRUMENTATION_CODE: -1`, and rejected failures, ignored or
assumption outcomes, and zero-test output.

1. `nativeCrashCausesBinderDeathAndRecoversInANewProcess`
   - Exact selector:
     `io.github.supermonster003.autojs6.plugin.lua.runtime.LuaRuntimeFaultRecoveryInstrumentationTest#nativeCrashCausesBinderDeathAndRecoversInANewProcess`.
   - Device log window: 2026-08-10 19:16:26.752 to 19:16:27.629 +08:00.
   - Fault process PID `27245` entered the dedicated `:lua_runtime` process and
     its `autojs-lua-exec` thread terminated with SIGABRT.
   - The test observed exactly one started callback and no terminal callback
     before Binder death.
   - Rebind started PID `27282`; the test required a different PID and process
     nonce, then completed `return 7` exactly once through the same remote
     provider/PFD/session path.
   - Result: PASS, one test.

2. `nativeWedgeIsKilledByWatchdogAndRecoversInANewProcess`
   - Exact selector:
     `io.github.supermonster003.autojs6.plugin.lua.runtime.LuaRuntimeFaultRecoveryInstrumentationTest#nativeWedgeIsKilledByWatchdogAndRecoversInANewProcess`.
   - Device log window: 2026-08-10 19:16:31.590 to 19:16:34.414 +08:00.
   - Wedged process PID `27430` ran in `:lua_runtime`; the same process-watchdog
     implementation used by the production provider sent SIGKILL after the
     execution deadline and stop grace.
   - The test observed exactly one started callback and no terminal callback
     before Binder death.
   - Rebind started PID `27459`; the test required a different PID and process
     nonce, then completed `return 7` exactly once.
   - Result: PASS, one test.

The two fault methods passed independently. Production provider discovery
remained disabled throughout.

## Restoration

After both methods, the test and target packages were force-stopped and
uninstalled in that order. At 2026-08-10 19:16:59 +08:00, per-user package
inventory for users `0` and `10`, `pm path`, `pidof`, and the instrumentation
inventory confirmed:

- target package absent;
- test package absent;
- no current or uninstalled record for either package in either Android user;
- target main process absent;
- `:lua_runtime` process absent;
- Lua runtime test runner absent;
- device state still `device`.

The device therefore returned to the audited package/process state.

## Evidence Boundary

This checkpoint establishes debug-only cross-process PFD/session transport,
native crash containment, watchdog termination of a wedged native call, Binder
death, rebind to a new process identity, and successful post-recovery execution
on one API 31 arm64-v8a device.

It does not establish production INFO/RUNTIME discovery or enablement,
concurrent BUSY/no-hidden-queue behavior on Android, cancellation or callback
peer-death recovery, blocking/hostile PFD recovery, Android process OOM,
additional API/ABI devices, signed release APK exclusion, or public release
readiness.
