# R6 Acceptance Evidence

R6 revalidates production evidence after the move to a persistent
process-global Node runtime and a required external runtime plugin. Historical
Phase 11/13 capability closures remain valid, but they do not replace current
device, APK, JUnit, or soak evidence for this lifecycle.

## Current Checkpoint

The focused Sony XQ-AT72 engine-switch gate passed both required tests on
2026-07-18 with zero failures, errors, or skips. It proves the multi-round
Rhino/Node alternating and plugin/Rhino-compatible/backend-switch regression,
but it is not a complete R6 device report.

The fresh R6 host/test/plugin installation repeated those selectors at `2/2`
and the permanent required-plugin Safe Node denial selector at `1/1` on
2026-07-19. An explicitly non-archivable 5-second persistent-plugin smoke also
passed with 6 cycles, 6 resource samples, and 3 liveness/recoverability checks.

After diagnosing later long-run harness failures, the repaired 2026-07-24
collector passed a screen-off 120.743-second Sony XQ-AT72 smoke with 100
continuous executions, 25 resource samples, zero failures, and clean JUnit.
The bounded partial wake lock remained held across the exact report interval
and was released during cleanup; elapsed/uptime evidence derived `0 ms` of
suspend. These focused results validate the implementation and collector path
only.

The first current-source Sony arm64 production attempt later on 2026-07-24
was correctly left unarchived: all ten main-app selectors passed, but the
three packaged-APK selectors were skipped because the collector had not
installed the new remote APK Builder plugin introduced by the 2026-07-23
packaging migration. The collector now treats that plugin as same-run
evidence rather than an external prerequisite. A fresh production matrix run
against the corrected collector is still required.

R6 and Core completion remain pending. The four current device reports, the
8-hour quick soak, the 24-hour final soak, and a clean-source final/release
decision still need to pass. A short smoke run, an old Phase 11/13 archive, or a
contract fixture cannot be substituted for any of those reports.

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

All collector Gradle invocations use `--no-daemon` and in-process Kotlin
compilation. This isolates the runtime-plugin build, template/Builder build, and
connected evidence gate from reusable Gradle or Kotlin compiler daemons. They
also use a daemon registry inside the unique run directory while retaining the
shared Gradle dependency cache, so an unrelated checkout's global
`gradle --stop` cannot terminate an in-flight evidence build.

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
