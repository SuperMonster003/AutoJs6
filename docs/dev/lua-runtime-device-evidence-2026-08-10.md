# Lua Runtime R2 Device Evidence - 2026-08-10

This report records the first cross-package Android acceptance run for the Lua
runtime host proxy and hostile fixture. It is intentionally narrower than a
production or native-runtime claim.

## Scope And Preflight

- Device serial: `QV710AF65F`
- Model: Sony `XQ-AT72`
- Android API: 31
- Device ABI order: `arm64-v8a,armeabi-v7a,armeabi`
- Selected host artifact ABI: `arm64-v8a`
- Kernel page size: 4,096 bytes; this is not 16 KiB page-size evidence.
- Fingerprint:
  `Sony/XQ-AT72_CN/XQ-AT72:12/58.2.A.10.44A/058002A0100044A0891821322:user/release-keys`
- Before installation, `org.autojs.autojs6`,
  `org.autojs.autojs6.test`, and
  `org.autojs.plugin.lua.runtime.fake` were all absent. No instrumentation was
  active and no Lua runtime service was discoverable.
- Every Gradle invocation waited for the shared Gradle client slot. Every ADB
  mutation used the explicit serial and first rejected competing local
  install/uninstall/instrument clients.

## Reviewed Artifacts

All three APKs contained one signer with certificate SHA-256
`2e64822e13a6c80c12e1c4b47e8fb32d1e9334526289da75777b7a79145de4b8`.
Build Tools 37 `aapt` and `apksigner` verified the package identities, target,
runner, signatures, and final local artifacts. After installation, each
package exposed exactly one base APK; pulling it back from the device produced
the same digest.

| Role | Package / version | Bytes | SHA-256 |
| --- | --- | ---: | --- |
| Host arm64 | `org.autojs.autojs6`, 5273, `6.8.0 Alpha7` | 42,033,624 | `d0b420c2a2bd38c68e2a9b7221e8e1098fbef11c94852bbd7a51c62b2332034e` |
| Host androidTest | `org.autojs.autojs6.test`, 0 | 2,313,608 | `ad05178f3390a358533b216ea05e58d3b5da0229defbf8dfedd62d7d7a3777cc` |
| Hostile fixture | `org.autojs.plugin.lua.runtime.fake`, 1, `1.0.0` | 1,034,673 | `47eba9655c427ae6c9a445d0be8d32119b4fa8b9584dc67c28a41a191ba77aa7` |

The installed UIDs were respectively 14442, 14444, and 14443. The registered
instrumentation was exactly
`org.autojs.autojs6.test/androidx.test.runner.AndroidJUnitRunner` targeting
`org.autojs.autojs6`. The only enabled `org.autojs.plugin.lua.RUNTIME` service
was the selected fixture component.

## Instrumentation Results

Each method ran alone with `-e class Class#method`, opt-in enabled, and its exact
scenario. A pass required both `OK (1 test)` and `INSTRUMENTATION_CODE: -1`,
with no failure, crash, short-message, or assumption result. The eight marked
fresh cases also received `autojs.luaRuntime.conformance.freshProcess=true`.
All 19 methods passed against the same final host artifact.

| # | Method | Scenario | Fresh | Result |
| ---: | --- | --- | :---: | :---: |
| 1 | `normalExecutionUsesExplicitBinderAndBalancesRemoteResources` | `normal` | no | PASS |
| 2 | `malformedRuntimeInfoIsRejectedBeforeExecutionCreation` | `malformed-runtime-info` | no | PASS |
| 3 | `runtimeNullBindingFailsClosedWithoutCreatingSession` | `null-binding` | yes | PASS |
| 4 | `twoEligibleRuntimeComponentsAreRejectedAsAmbiguous` | `ambiguity` | no | PASS |
| 5 | `stalledPluginInfoTimesOutFailsFastWhileOccupiedAndRecoversAfterRelease` | `stall-plugin-info` | yes | PASS |
| 6 | `stalledRuntimeInfoTimesOutFailsFastWhileOccupiedAndRecoversAfterRelease` | `stall-runtime-info` | yes | PASS |
| 7 | `callbackBeforeCreateReturnContainsEitherObservedBinderOrdering` | `callback-before-start` | no | PASS |
| 8 | `duplicateTerminalKeepsFirstResultAndStillDisposesTheSession` | `duplicate-terminal` | no | PASS |
| 9 | `zeroCreditOutputRecordsObservedOnewayOrderingAndAlwaysCleansUp` | `zero-credit-output` | yes | PASS |
| 10 | `sequenceGapFailsClosedAfterOneInvalidOutput` | `sequence-gap` | no | PASS |
| 11 | `sequenceReplayFailsClosedAfterTheRepeatedOutput` | `sequence-replay` | no | PASS |
| 12 | `outputForAnotherRequestIdFailsClosed` | `wrong-request-id` | no | PASS |
| 13 | `truncatedRawSourcePfdFailsBeforeSessionCreation` | `raw-source-truncated` | no | PASS |
| 14 | `trailingRawSourcePfdFailsBeforeSessionCreation` | `raw-source-trailing` | no | PASS |
| 15 | `digestMismatchedRawSourcePfdFailsBeforeSessionCreation` | `raw-source-digest-mismatch` | no | PASS |
| 16 | `ignoredCancelStillStopsLocallyAndClosesTheRemoteSession` | `ignore-cancel` | yes | PASS |
| 17 | `secondCreateReceivesBusyWhileFirstSessionRemainsActive` | `busy-second-create` | yes | PASS |
| 18 | `interruptedStalledCreatePoisonsLaneAndClosesRetainedSourceAfterRelease` | `stall-create` | yes | PASS |
| 19 | `lateCreateSessionIsCancelledClosedAndNeverStartedAfterCallerAbandons` | `late-create-return-session` | yes | PASS |

The two create-abandonment cases ran last. Each proved its host lane remained
poisoned, released the fixture stall, contained or disposed the late result,
and then left no host or fixture process after explicit teardown.

## Failure And Correction Chain

The first null-binding attempt, against the earlier host SHA-256
`1806f111e9a07d803c08d9f99ddf0a9d2f4739c7e8da9dc9b7b2d82b832e74be`,
failed because discovery returned fourteen evaluations instead of the one
effectively enabled runtime. `MATCH_DISABLED_COMPONENTS` was correctly reading
component overrides, but dormant INFO/runtime services were not removed from
the candidate inventory.

The Android transport now filters INFO and runtime services through the
effective manifest-plus-override state before applying the sixteen-candidate
limit and building descriptors. It still checks the state again during
descriptor/preflight handling so a concurrent disable fails closed. The 66
focused App JVM tests passed, the final host APK above was rebuilt and pulled
back from the device, and the null-binding case then passed. All other methods
were executed or re-executed against that final host artifact.

One runner batch also stopped after method 16 because an audit regex matched
the word `ignored` inside the passing method name. Its actual result was already
`OK (1 test)` and code `-1`; the parser was narrowed and methods 16 and 17 were
run again successfully. This was a harness false negative, not a device-test
failure.

## Restore And Evidence Boundary

After the suite, the androidTest, fixture, and host packages were uninstalled
in that order because all three were absent in the preflight. A final `pm path`
and `pidof` check found none of them, and no fixture Lua runtime service remained
discoverable. The device therefore returned to the audited package/process
state.

This run establishes one API 31/arm64-v8a/4 KiB cross-package Binder, component,
UID, callback, cancellation, and tracked PFD-content/ownership checkpoint. It
does not establish:

- OS-level process FD counts or a leak-free kernel claim;
- oversized-source, provider-death, update-during-session, or uninstall-during-session behavior;
- wrong-caller-UID or denied/replayed host-capability attacks;
- truly concurrent double-create exclusion;
- API 24-36, x86_64, or 16 KiB page-size coverage;
- a production provider, vendored/compiled PUC Lua, JNI execution, native OOM,
  hook cancellation, ABI packaging, signing, or release readiness.
