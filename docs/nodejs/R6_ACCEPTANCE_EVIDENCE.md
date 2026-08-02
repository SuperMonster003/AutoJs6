# R6 Acceptance Evidence

R6 revalidates production evidence after the move to a persistent
process-global Node runtime and a required external runtime plugin. Historical
Phase 11/13 capability closures remain valid, but they do not replace current
device, APK, JUnit, or soak evidence for this lifecycle.

## Current Checkpoint

The archived candidate at host commit
`ee97b7909eaf64daf1521e6f840c65c94569d2b5` and runtime-plugin commit
`a904ee2011e93031db1178a0b2ff624b970e460f` has a complete internally
consistent R6 payload:

- Sony `arm64-v8a`, Sony `armeabi-v7a`, Xiaomi `arm64-v8a`, and the x86_64
  emulator each passed all 13 fixed selectors with zero failure, error, or
  skip.
- The quick soak ran for 8.000056 hours with 979 cycles, 126 samples, and 49
  liveness/recoverability checks.
- The final soak ran for 24.000057 hours with 2890 cycles, 329 samples, and 145
  liveness/recoverability checks.
- Both soaks completed cleanup and wake-lock release, reused one process
  runtime with fresh execution state, derived zero suspend time, and reported
  no monotonic leak, orphan process, or unrecoverable runtime/slot.
- Before successor source edits, `verifyNodeR6ProductionEvidenceGate` reported
  `completed` with zero blockers and zero policy violations.

The 2026-08-02 final-integration audit corrected stale final-status producer
dependencies, bound long-running to the real Phase 13 JSON decision, reused the
canonical R6 contract for final-soak validation, and fixed live-provider
summary lookup and provenance. It also fixed a WebSocket provider-destroy race
and made OCR/Barcode health tests reflect the actual external-plugin
installation state. Every WebSocket close path now retains transport quota,
sends Close, and selectively cancels a non-ACK peer after a two-second grace
period; the regression includes explicit close followed by provider destroy.
The originally failing Sony
selectors and the enhanced non-ACK regression pass, followed by the complete
live-provider summary at 27 expected cases, zero failures/errors, two
policy-approved skips, and zero policy violations.

These corrections modify the host source after the archived candidate. The
current non-strict R6 status therefore deliberately reports
`deviceMatrixReady=true`, `soakReady=true`, and `zeroSkippedTests=true`, but
remains blocked by `current host worktree is not clean`. The strict final-soak
gate now propagates the same canonical source-identity blocker. Separately,
the live-provider summary records test-before/test-after commit and clean state,
then copies and hashes its single 27-case JUnit XML, the app/androidTest APKs
actually selected by UTP's `aapt` trace, the selection logs, and device bindings
in a unique snapshot with a frozen manifest and archived summary. A subsequent connected-test run overwrote
the producer XML without invalidating that snapshot. Final-provider therefore
correctly rejects the otherwise passing Sony run only because it was collected
from this dirty worktree. The final
integration summary therefore accepts 10 of 13 subreports and reports three
fail-closed views of the same uncommitted-source condition; the release gate
cannot pass transitively.

R6 and Core completion consequently remain pending for the successor source
identity. After these changes are committed, recollect all four device reports
and both production soaks, rerun the separate debug live-provider gate, and
then run the strict final/release command. The complete `ee97b790` archive is
valid historical candidate evidence, but it cannot authorize different source
bytes; a short smoke, old Phase 11/13 archive, or contract fixture cannot be
substituted either.

## Fixed Device Matrix

Run the production collector once for each target. Use an explicit adb serial
for the intended device or emulator:

```powershell
node tools/nodejs/r6/run-device-evidence.js --target sony-arm64 --serial QV710AF65F
node tools/nodejs/r6/run-device-evidence.js --target sony-armeabi-v7a --serial QV710AF65F
node tools/nodejs/r6/run-device-evidence.js --target second-oem-arm64 --serial SECOND_OEM_SERIAL
node tools/nodejs/r6/run-device-evidence.js --target emulator-x86_64 --serial EMULATOR_SERIAL
```

The four canonical reports are:

- `build/reports/nodejs/r6-evidence/device/sony-arm64.json`
- `build/reports/nodejs/r6-evidence/device/sony-armeabi-v7a.json`
- `build/reports/nodejs/r6-evidence/device/second-oem-arm64.json`
- `build/reports/nodejs/r6-evidence/device/emulator-x86_64.json`

The collector fixes the release flavor to `nodePlugin`, the backend to
`plugin`, the runtime slot to `node24_5`, and the lifecycle model to
`persistent_process_global_runtime`. It accepts only the four target IDs above
and refuses selector, profile, duration, status, force, and run-token overrides.

Each production run must start and end with clean, unchanged host and plugin
source commits. The collector builds a fresh signed ABI-specific plugin APK,
installs it, and pulls the installed APK back to prove byte-for-byte identity.
It also builds both a fresh slim `packageInrtDebug` APK for the target ABI and
`:plugins:apk-builder-template:packageRelease` from that same host commit. The
Builder build explicitly enables remote packaging and consumes the monorepo
Runtime Kit generated from the current host; a sibling Builder repository or a
preinstalled legacy package cannot satisfy the contract. The release-signed
Builder APK must share the host signer, expose the exact
`org.autojs.plugin.APK_BUILDER` service, match the fresh build after
installation, and remain byte-for-byte and signer-identical before and after
instrumentation.

“Slim” excludes prepackaged Node-owned JNI; it does not permit the Builder
Runtime Kit to omit its target-independent terminal baseline. Runtime Kit
generation copies `libjackpal-androidterm5.so` and
`libjackpal-termexec2.so` for `arm64-v8a`, `armeabi-v7a`, `x86_64`, and `x86`
from the version-controlled `libtermexec-release.aar`. It records those entries
in `native-libs.json` and declares the ABI/library policy in
`runtime-kit.json`. Builder packaging fails closed if any declared entry is
missing or if the template contains `libnode.so` or `libautojs6-node.so`.
Remote packaging may then satisfy required libraries from this verified
template or from authenticated host/plugin build inputs. This is required for
the Sony v7a target, whose evidence contract intentionally keeps the host
process arm64 while running the external Node service as v7a.

Generated Release packages remain non-debuggable. Packaged lifecycle
observation therefore cannot rely on `run-as`. The template exposes only an
explicit ordered status/Stop receiver guarded by the platform
`android.permission.DUMP` permission and returns status as Base64-encoded JSON;
ordinary applications cannot call it, while the R6 instrumentation's
`adb shell` identity can. The existing notification Stop receiver remains
`exported=false`. The source-contract gate checks the manifest permission,
receiver process, ordered-result encoding, test actions, and absence of
packaged `run-as` commands.

The collector verifies the slim template's package/signature, exact native ABI,
and absence of prepackaged Node JNI, archives its SHA-256, and pushes the exact
bytes to a unique packaged-smoke override path derived from the run token. It
binds that exact path and the target runtime ABI to instrumentation, verifies
the device SHA-256 both before and after testing, and removes the run-owned
device file before staging.
It also binds the installed host and Android test APKs to fresh build artifacts,
records their paths, sizes, and SHA-256 hashes, and attests a fresh JUnit XML
from that invocation. APK inspection requires the host to exclude Node-owned
JNI libraries while retaining its baseline `libjackpal-termexec2.so` and
`libc++_shared.so` dependencies for the reported host-process ABI. The
target-specific Inrt template must retain those baseline libraries for the
target runtime ABI, while the plugin must carry the complete Node runtime
triplet for that same target ABI. A generated 64-character run token binds the
run and the device-side smoke evidence. Archiving happens only after all
identity, device, APK, JUnit, lifecycle, resource, and report-contract checks
pass. Use `--no-archive` only when validating a staged run without replacing a
canonical report.

Every installed/build APK pair is verified with Android `apksigner`, must expose
exactly one SHA-256 signer, and must preserve signer identity. The plugin signer
is additionally bound to the same-run instrumentation preflight report, its
package and service class, its actual authorization state, and the fixed R5
trust plus R6 required-plugin selectors. The canonical verifier rejects missing
or forged `required`, `authenticated`, or `trusted` provenance even when the APK
hash fields are otherwise well formed.

All four collector Gradle invocations use `--no-daemon` and in-process Kotlin
compilation. This isolates the runtime-plugin build, target-ABI template build,
Runtime-Kit-backed APK Builder release build, and connected evidence gate from
reusable Gradle or Kotlin compiler daemons. The Debug template and Release
Builder graphs run sequentially in separate processes because their combined
4 GB compiler process exceeded its heap; each isolated full rebuild passes.
All four invocations use a daemon registry inside the unique run directory
while retaining the shared Gradle dependency cache, so an unrelated checkout's
global `gradle --stop` cannot terminate an in-flight evidence build.

The target constraints are fail-closed: the two Sony entries must use the
required 64-bit and 32-bit runtime ABIs, the second-OEM entry must be a
non-Sony physical arm64 device, and the x86_64 entry must be an emulator.
The packaged APK selectors use that same target ABI; an arm64-only packaged
fixture cannot satisfy the v7a or x86_64 matrix entries.

## Fixed 13-Selector Contract

Every device report must attest exactly these 13 unique selectors with zero
failures, errors, or skips:

1. `org.autojs.autojs.engine.NodeR6EngineSwitchInstrumentationTest#r6_01_requiredPluginNodeAndRhinoAlternateAcrossMultipleRounds`
2. `org.autojs.autojs.engine.NodeR6EngineSwitchInstrumentationTest#r6_02_pluginRhinoCompatibilityPluginSwitchExecutesAndRecovers`
3. `org.autojs.autojs.engine.NodeRuntimePluginR5InstrumentationTest#r5_01_livePluginPreflightNegotiatesContractAndReportsTrustIdentity`
4. `org.autojs.autojs.engine.NodeRuntimePluginR5InstrumentationTest#r5_02_candidateAndBinderCachesReusePersistentRuntimeWithFreshExecutionState`
5. `org.autojs.autojs.engine.NodeRuntimePluginR5InstrumentationTest#r5_04_concurrentPluginCallsApplySingleActiveBackpressureWithoutQueueing`
6. `org.autojs.autojs.engine.NodeRuntimePluginR5InstrumentationTest#r5_06_hostRejectsContractVersionAndMandatoryCapabilityMismatch`
7. `org.autojs.autojs.engine.NodeRuntimePluginR5InstrumentationTest#r5_07_binderDeathReconnectsAtomicallyAndPreservesCrashAttribution`
8. `org.autojs.autojs.engine.NodeRuntimePluginR5InstrumentationTest#r5_08_runtimeWarmSamplesMeetR2SloAndSeparateDiscoveryAndBindOverhead`
9. `org.autojs.autojs.engine.NodeR6RequiredPluginSecurityInstrumentationTest#requiredPluginPreservesPermanentSafeNodeDenials`
10. `org.autojs.autojs.engine.NodeR6PersistentPluginSoakInstrumentationTest#persistentRequiredPluginSoakProducesR6Evidence`
11. `org.autojs.autojs.apkbuilder.EmbeddedNodePackagedApkSmokeTest#installAndRunFullCompatibilityEmbeddedNodePackagedApk`
12. `org.autojs.autojs.apkbuilder.EmbeddedNodePackagedApkSmokeTest#installAndRunCrashEvidenceEmbeddedNodePackagedApk`
13. `org.autojs.autojs.apkbuilder.EmbeddedNodePackagedApkSmokeTest#installAndRunRuntimeCompatibilityDescriptorMismatchEmbeddedNodePackagedApk`

The fully qualified selector list is defined by
`nodeJsR6DeviceEvidenceGateInstrumentationClasses` in
`app/node-instrumentation-gates.gradle.kts` and independently checked by the R6
verifier. Direct Gradle execution is useful for diagnosis, but only the
collector creates the complete source/APK/JUnit attestation and canonical
device report.

## Fixed 8-Hour And 24-Hour Soaks

The production runner has no duration override and no force-archive option:

```powershell
node tools/nodejs/r6/run-production-soak.js --kind quick --serial QV710AF65F
node tools/nodejs/r6/run-production-soak.js --kind final --serial QV710AF65F
```

`quick` is exactly 8 hours and `final` is exactly 24 hours. Their canonical
reports are:

- `build/reports/nodejs/r6-evidence/soak/quick-soak-8h.json`
- `build/reports/nodejs/r6-evidence/soak/final-soak-24h.json`

Production soak acceptance binds a clean, unchanged host/plugin source
identity, a unique run token, one fresh passing JUnit testcase, wall-clock
start/end time, and the current required-plugin runtime identity. It requires
stable service PID, plugin session and process runtime generation, monotonic
execution sequence, fresh per-execution state, completed cleanup, and bounded
growth across RSS, Java heap, native heap, Node heap, threads, file
descriptors, libuv handles, and active bridge resources. Sampling may be no
slower than five minutes and must provide at least 97 samples for quick and 289
for final evidence.

The harness retains the 15-second per-cycle timeout and acquires one
non-reference-counted `PARTIAL_WAKE_LOCK` with a fixed 15-minute cleanup grace.
The report must prove the lock was acquired, remained held at the common
soak-end timestamp, covered matching elapsed/uptime durations with at most one
second of derived suspend, and was released in `finally`. Production builds use
a single-use Gradle process and in-process Kotlin compilation so unrelated
long-lived build/compiler daemons cannot own the instrumentation run. Both the
fresh plugin build and the connected 8/24-hour task use the soak run's private
daemon registry, preventing another checkout's global `gradle --stop` from
terminating the experiment while the shared dependency cache remains available.

Canonical RSS, thread, fd, and heap metrics come from the plugin process
itself. The additional cross-UID `/proc/<pid>/status` observation is only a
cross-check: it is retried five times, and an unavailable result is accepted
only with null external proc values, a bounded diagnostic, and same-cycle live
Binder proof matching the sample PID and host session. Plugin-self metrics and
PSS remain mandatory. Compact cycle records retain only the bridge counters
needed by the verifier so 24-hour evidence does not duplicate a large
bridge-health JSON payload on every iteration.

Android may legitimately report zero currently allocated native-heap bytes
after the allocator releases all tracked blocks. Therefore `nativeHeapKb` is a
required non-negative metric, while `nativeHeapCommittedKb` and `nativePssKb`
must both remain positive so a zero allocated value cannot hide unavailable
native-memory diagnostics.

For a development-only smoke:

```powershell
.\gradlew.bat --console=plain :app:verifyNodeR6PersistentPluginSoakSmoke `
  "-Pautojs.nodejs.r6.soak.serial=QV710AF65F"
```

A smoke always reports `kind=smoke` and is non-archivable. Passing it validates
the harness, not the 8-hour or 24-hour production requirement.

The production runner can exercise the same non-archivable path with explicit
duration and sampling controls:

```powershell
node tools/nodejs/r6/run-production-soak.js --smoke `
  --smoke-duration-ms 120000 --sample-interval-ms 5000 `
  --cycle-delay-ms 500 --serial QV710AF65F
```

## Status And Strict Gates

Contract fixtures and the non-strict current-status report can run without
claiming release readiness:

```powershell
.\gradlew.bat --console=plain :app:verifyNodeR6ProductionEvidenceContract
.\gradlew.bat --console=plain :app:generateNodeR6ProductionEvidenceStatus
```

The strict R6 gate fails closed on a missing, stale, dirty, skipped, mismatched,
or failed required report:

```powershell
.\gradlew.bat --console=plain :app:verifyNodeR6ProductionEvidenceGate
```

`generateNodeFinalIntegrationSummary` consumes the R6 status report.
`verifyNodeFinalIntegrationGate` is strict, and `verifyNodeReleaseGate` depends
on that strict final gate. Consequently a release cannot pass by generating a
blocked status report or by relying on historical capability closure.

The Phase 11 final soak status also reads the two canonical R6 soak reports
above. Its standalone checks cover duration, failures, wake-lock cleanup,
continuous runtime/fresh execution state, liveness/recoverability, suspend
time, and the complete resource-growth metric set; the R6 production-evidence
verifier remains the authoritative source/APK/JUnit/identity contract.
