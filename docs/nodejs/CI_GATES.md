# Embedded Node CI Gates

This document separates fast host checks from heavy device and release checks for Node Profile v1.

## PR Gate

Use `verifyNodePrGate` for ordinary pull requests and local pre-submit checks:

```powershell
.\gradlew.bat --console=plain :app:verifyNodePrGate
```

Scope:

- app and androidTest Kotlin compilation
- compatibility profile generation and docs/registry verification
- resolver visualizer fixtures
- checked-in Node example manifest validation
- dependency bundler safe/unsafe fixtures
- main APK packaging preflight
- template reproducibility
- packaged app integration dry run

Non-goals:

- no connected-device instrumentation
- no packaged APK install/launch smoke
- no memory/lifecycle stress
- no desktop Node differential comparison

## Device PR Gate

When a device is available, use the shortest matching device profile from [DEVICE_MATRIX.md](DEVICE_MATRIX.md).

```powershell
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pautojs.nodejs.test.deviceProfile=smoke"
```

Scope:

- embedded smoke
- bridge smoke
- packaged toast bridge smoke

Use explicit single-class or single-method commands for focused fixes. Passing `android.testInstrumentationRunnerArguments.class` overrides device profile class selection.

## Nightly Gate

Use `verifyNodeNightlyGate` only with an explicit heavy device profile:

```powershell
$env:ANDROID_SERIAL="QV710AF65F"
.\gradlew.bat --console=plain :app:verifyNodeNightlyGate "-Pautojs.nodejs.test.deviceProfile=sony"
```

Scope:

- PR gate
- debug host gate
- packaged host gate
- desktop Node differential harnesses for real npm and modern core probes
- connected device instrumentation selected by `sony` or `full`
- lifecycle and memory stress through the selected device profile
- fuzzers and real npm corpus through the selected device profile

The task fails immediately if `autojs.nodejs.test.deviceProfile` is not `sony` or `full`.
When the requested task is `verifyNodeNightlyGate` or `verifyNodeReleaseGate`, fuzzer instrumentation defaults to
`autojs.nodejs.fuzzerBudget=extended` unless the property is explicitly overridden.

After nightly or release gates finish, `generateNodeNightlyReport` writes:

```text
build/reports/nodejs/nightly-report.json
build/reports/nodejs/nightly-report.md
```

The report summarizes commit state, device/profile, latest connected-test XML counts and failures, perf and memory reports, fuzzer seeds, packaged smoke readiness, native hashes, and experimental feature flags.
Fuzzer failure seeds are collected from connected-test XML into `build/reports/nodejs/fuzz-seeds/` and can be replayed with `:app:replayNodeFuzzerSeed`.

## v1.1 Alpha Gate

Use `verifyNodeV11AlphaGate` for the v1.1 functional milestone. It measures feature progress without creating a release tag:

```powershell
$env:ANDROID_SERIAL="QV710AF65F"
.\gradlew.bat --console=plain :app:verifyNodeV11AlphaGate "-Pautojs.nodejs.test.deviceProfile=v1_1_alpha"
```

Scope:

- PR host gate and debug host gate
- desktop Node differential harnesses for real npm and modern core probes
- v1.1 ESM, npm, controlled network, WebSocket, expanded bridge, security, resource stress, memory stress, worker design-gate, and packaged v1.1 smoke instrumentation
- JSON summary at `build/reports/nodejs/v1.1-alpha-summary.json`

The `v1_1_alpha` profile uses shorter default stress budgets than nightly: `autojs.nodejs.leak.iterations=12`,
`autojs.nodejs.leak.imageIterations=6`, `autojs.nodejs.leak.faultIterations=3`, and
`autojs.nodejs.bridgeStress.iterations=8`. Override those Gradle properties for local probes.

The task fails unless `autojs.nodejs.test.deviceProfile=v1_1_alpha` is explicit. Keep `ANDROID_SERIAL` pointed at the Sony unattended device when multiple devices are attached.

## v1.1 Beta Gate

Use `verifyNodeV11BetaGate` after the alpha milestone when stabilizing v1.1 for
release-candidate planning:

```powershell
$env:ANDROID_SERIAL="QV710AF65F"
.\gradlew.bat --console=plain :app:verifyNodeV11BetaGate "-Pautojs.nodejs.test.deviceProfile=v1_1_beta"
```

Scope:

- all v1.1 alpha capability coverage
- lifecycle, fs promises, native-addon denial, resolver fuzzer, and scoped-fs
  fuzzer regressions
- packaged host verification
- error catalog, project wizard, and TypeScript declaration checks
- beta blocker register verification
- JSON summary at `build/reports/nodejs/v1.1-beta-summary.json`

Packaged `.mjs` and local dynamic import install-and-run smokes now have a
focused gate that uses the default production packaged template:

```powershell
$env:ANDROID_SERIAL="QV710AF65F"
.\gradlew.bat --console=plain :app:verifyNodePackagedEsmGate "-Pautojs.nodejs.test.deviceProfile=sony" "-Pautojs.nodejs.identity=production" "-Pautojs.embeddedNode.expectedAbis=arm64-v8a"
```

The focused gate validates the packaged runtime compatibility descriptor version
vector and reports packaged fixture cache hit/miss lines while still installing
and launching the generated APKs on device.

The beta gate fails unless `autojs.nodejs.test.deviceProfile=v1_1_beta` is explicit.
Keep `ANDROID_SERIAL` pointed at the Sony unattended device when multiple
devices are attached.

The beta profile keeps the default normal fuzzer budget. Use
`-Pautojs.nodejs.fuzzerBudget=extended` only as a separate stress subgate.

## v1.1 RC Readiness Gate

Use `verifyNodeV11RcReadiness` after a passing v1.1 beta gate and before
production release-candidate gate planning:

```powershell
$env:ANDROID_SERIAL="QV710AF65F"
.\gradlew.bat --console=plain :app:verifyNodeV11RcReadiness "-Pautojs.nodejs.test.deviceProfile=v1_1_beta"
```

Scope:

- regenerates the v1.1 beta summary from current host reports,
- verifies the beta summary schema, clean git state, and `v1_1_beta` profile,
- verifies the latest beta connected-test XML has zero failures, errors, and
  skips,
- verifies required beta reports and packaged smoke aggregate readiness,
- verifies all beta blocker statuses are `deferred` or `closed`,
- writes `app/build/reports/nodejs/v1.1-rc-readiness.json`.

This gate does not create a tag and does not replace `verifyNodeReleaseGate`.
See [reports/v1.1-rc/README.md](reports/v1.1-rc/README.md) and
[releases/NODE_PROFILE_V1_1_RC_PLAN.md](releases/NODE_PROFILE_V1_1_RC_PLAN.md).

## Phase 8 Node 24.17 Upgrade Gates

Phase 8 starts with a build-only Node 24.17 candidate slot. These gates do not
change the default runtime; `node24_5` remains the rollback/default slot until
all reports are ready:

```powershell
.\gradlew.bat --console=plain :app:generateNode2417NativeDiffReport
.\gradlew.bat --console=plain :app:generateNode2417SecurityProbeReport
.\gradlew.bat --console=plain :app:generateNode2417UpgradeGateReport
```

The generated reports are:

- `build/reports/nodejs/node-24.17-native-diff.json`
- `build/reports/nodejs/node-24.17-security-probes.json`
- `build/reports/nodejs/node-24.17-upgrade-gate.json`

Candidate validation must pass explicit artifacts and run on Sony:

```powershell
$env:ANDROID_SERIAL="QV710AF65F"
.\gradlew.bat --console=plain :app:verifyNode2417UpgradeGate `
  "-Pautojs.nodejs.runtimeSlot=node24_current" `
  "-Pautojs.nodejs.patchCandidateJniLibs=D:\path\to\jniLibs" `
  "-Pautojs.nodejs.patchCandidateHeaders=D:\path\to\include\node" `
  "-Pautojs.nodejs.test.deviceProfile=sony" `
  "-Pautojs.nodejs.network.experimental=true"
```

`verifyNode2417UpgradeGate` fails fast unless `node24_current` is selected. It
combines S8-01/S8-02 native artifact intake and ABI/export diff, S8-03
dual-runtime A/B evidence, S8-04 security probes, packaged smoke, main-app
embedded/native one-shot coverage, long-running, workers, live providers,
startup/RSS, and the existing compatibility/npm/modern-core corpus. See
[reports/phase8/README.md](reports/phase8/README.md).

## Phase 8 Final Gates

Use the Phase 8 focused gates to close the capability expansion stage without
running the full release gate:

```powershell
$env:ANDROID_SERIAL="QV710AF65F"
.\gradlew.bat --console=plain :app:verifyNodePhase8SecurityStressGate "-Pautojs.nodejs.test.deviceProfile=sony"
.\gradlew.bat --console=plain :app:verifyNodePhase8Gate "-Pautojs.nodejs.test.deviceProfile=sony"
```

`verifyNodePhase8SecurityStressGate` selects the Phase 8 security/stress
instrumentation subset: Node 24.17 security probes, TypeScript, module
conformance, controlled HTTP/HTTPS/undici, advanced scoped fs, compile-cache /
prewarm / performance, inspector security, hardened sandbox, broker/process
pool policy, and bridge RPC fuzzing.

`verifyNodePhase8Gate` selects the full Phase 8 capability set, runs host
tooling/report gates, and writes
`app/build/reports/nodejs/phase8-summary.json` through
`generateNodePhase8Summary`. The summary fails on missing or failed expected
instrumentation selectors and records known design-gated inputs separately.
See [reports/phase8/README.md](reports/phase8/README.md).

## Phase 9 Native Modernization Gates

Phase 9 Batch A starts with host-side native modernization preflights:

```powershell
.\gradlew.bat --console=plain :app:generateNodeNativeSymbolUsageReport
.\gradlew.bat --console=plain :app:verifyNodeRuntimeAdapterAbi
.\gradlew.bat --console=plain :app:verifyNodeRuntimeAdapterExport
.\gradlew.bat --console=plain :app:verifyNodeRuntimeBuildPlan
.\gradlew.bat --console=plain :app:verifyNode16KbNativeAlignment
.\gradlew.bat --console=plain :app:generateNodeRuntimeArtifactManifest
.\gradlew.bat --console=plain :app:verifyNodeRuntimeArtifactIntegrity
.\gradlew.bat --console=plain :app:verifyNodeRuntimeDistributionPolicy
.\gradlew.bat --console=plain :app:verifyNodeRuntimeArtifactGate
.\gradlew.bat --console=plain :app:verifyNodePhase9PerformancePolicy
.\gradlew.bat --console=plain :app:verifyNodePhase9InspectorPolicy
.\gradlew.bat --console=plain :app:verifyNodePhase9IsolationPolicy
.\gradlew.bat --console=plain :app:verifyNodePhase9PluginPolicy
.\gradlew.bat --console=plain :app:verifyNodePhase9WasmWasiPolicy
.\gradlew.bat --console=plain :app:verifyNodePhase9NativeCrashPipeline
.\gradlew.bat --console=plain :app:verifyNodePhase9ChaosSecurityPolicy
.\gradlew.bat --console=plain :app:verifyNodePhase9NativeModernizationPlan
.\gradlew.bat --console=plain :app:verifyNodeWasmWasiGate "-Pautojs.nodejs.test.deviceProfile=sony"
.\gradlew.bat --console=plain :app:generateNodePhase9Summary "-Pautojs.nodejs.test.deviceProfile=sony"
.\gradlew.bat --console=plain :app:verifyNodePhase9Gate "-Pautojs.nodejs.test.deviceProfile=sony"
```

These tasks generate the native symbol usage map, verify the versioned C
runtime adapter ABI header, verify the adapter export and artifact metadata
across built app debug ABIs, validate the reproducible Android runtime build
plan skeleton, and check current-runtime 16 KB alignment/artifact manifests.
They also verify the runtime distribution policy and aggregate runtime artifact
gate. The performance policy gate records the current S9-15/S9-20 state:
compile cache is production-evidenced, startup snapshots remain deferred, and
process-only prewarm/performance matrix coverage remains partial until reusable
runtime pooling, packaged metrics, and numeric SLO thresholds exist. The
inspector policy gate records S9-21/S9-26 with the disabled inspector boundary
preserved, DevTools/profiling/heap support deferred, diagnostic reports partial,
and the disabled-state security gate ready. The isolation policy gate records
S9-27/S9-33 with hardened isolated runtime, capability broker, and process-pool
evidence partial, crash/resource/multi-instance work deferred, and worker
partial promotion blocked by native availability. The WASM/WASI focused gate
records the first S9-34/S9-40 device evidence: pure WebAssembly works with
scoped `.wasm` bytes while raw `wasi` / `node:wasi` remains disabled. The
WASM/WASI policy gate records the remaining compile-cache, facade,
resource-limit, worker, build-workflow, and security-corpus gaps. The plugin
policy gate records S9-41/S9-43 with WASM plugin packages and native service
plugins deferred, JS-only manager ABI/lifecycle partial, and native
addon/plugin runtime denial preserved. The native crash pipeline gate records
S9-44 with runtime artifact evidence, symbol archive/dependency reports,
logcat/tombstone capture, execution descriptor, and offline symbolication.
The chaos/security policy gate records S9-45/S9-47 with artifact failure
coverage, process/provider failure gaps, cache/snapshot/worker/WASM chaos
status, native artifact security boundaries, broker/WASI/plugin denial posture,
and inspector release-bypass evidence. `generateNodePhase9Summary` combines the
host-side reports, connected WASM/WASI and 30-fixture real npm corpus XML,
examples report, runtime comparison, page-size validation, startup/prewarm
performance delta, capability matrix, and remaining blockers into
`build/reports/nodejs/phase9-summary.json` and `.md`. `verifyNodePhase9Gate`
runs this summary as the aggregate close-out on the Sony/full device profile.
These gates do not promote Node 24.17 and do not change the default runtime
slot. See
[reports/phase9/README.md](reports/phase9/README.md),
[native/RUNTIME_ADAPTER_ABI.md](native/RUNTIME_ADAPTER_ABI.md), and
[native/ANDROID_NATIVE_COMPAT.md](native/ANDROID_NATIVE_COMPAT.md).

## Phase 10 Real-World Activation Gates

Phase 10 starts by freezing the runtime upgrade lane and promoting existing
packaged APK evidence into focused first-wave gates:

```powershell
.\gradlew.bat --console=plain :app:verifyNodePhase10RuntimeFreeze
.\gradlew.bat --console=plain :app:generateNodePhase10CapabilityTruth
.\gradlew.bat --console=plain :app:verifyNodePhase10ProviderRegistryV2
.\gradlew.bat --console=plain :app:generateNodePhase10SkipAudit
.\gradlew.bat --console=plain :app:verifyNodePhase10PackagedParityHost
.\gradlew.bat --console=plain :app:verifyNodePhase10FocusedGateMatrix
.\gradlew.bat --console=plain :app:verifyNodePhase10Autojs6PmFixture
.\gradlew.bat --console=plain :app:verifyNodePackageManagerGate
.\gradlew.bat --console=plain :app:verifyNodePhase13AndroidPackageInstallManager
.\gradlew.bat --console=plain :app:verifyNodePhase10LongRunningActivation
.\gradlew.bat --console=plain :app:verifyNodeLongRunningGate "-Pautojs.nodejs.test.deviceProfile=sony"
.\gradlew.bat --console=plain :app:verifyNodePhase10WorkerWasiBoundary
.\gradlew.bat --console=plain :app:verifyNodeWorkerWasiGate "-Pautojs.nodejs.test.deviceProfile=sony"
.\gradlew.bat --console=plain :app:verifyNodePhase10DeveloperExperience
.\gradlew.bat --console=plain :app:verifyNodeDeveloperExperienceGate "-Pautojs.nodejs.test.deviceProfile=sony"
.\gradlew.bat --console=plain :app:verifyNodePhase10CrashAdapterCutover
.\gradlew.bat --console=plain :app:verifyNodeCrashAdapterCutoverGate
.\gradlew.bat --console=plain :app:verifyNodePhase10SecurityCorpus
.\gradlew.bat --console=plain :app:verifyNodeRealNpmGate "-Pautojs.nodejs.test.deviceProfile=sony"
.\gradlew.bat --console=plain :app:verifyNodeExamples
.\gradlew.bat --console=plain :app:generateNodePhase10Summary
.\gradlew.bat --console=plain :app:verifyNodePackagedParityGate "-Pautojs.nodejs.test.deviceProfile=sony"
.\gradlew.bat --console=plain :app:verifyNodePhase10Gate "-Pautojs.nodejs.test.deviceProfile=sony"
```

`verifyNodePhase10RuntimeFreeze` fails if the recorded `libnode.so` hashes,
default `node24_5` slot, Node 24.5 capability constants, or moduleSources
budgets drift. `generateNodePhase10CapabilityTruth` records implemented,
live-provider, packaged-supported, experimental, blocked, and disabled
capabilities without promoting fake-provider-only results.
`verifyNodePhase10ProviderRegistryV2` verifies the S10-11 provider registry
metadata, Doctor report wiring, Phase 10 `statusCode` vocabulary,
`healthCheck`, `activeResources`, `lastError`, and packaged-support fields. The
packaged parity host report records descriptor/version-vector coverage,
stale-template checks, and fixture cache-key invalidators;
`verifyNodePackagedParityGate` still runs the Sony install-and-launch APK
smokes. `verifyNodePhase10Autojs6PmFixture` covers the S10-21 through S10-27
host-managed `autojs6-pm` boundary with offline registry metadata, semver,
tarball integrity, safe extraction, `autojs6-lock.json`, install/list/update/
remove/verify/prune, and package compatibility analyzer negative tests. It does
not enable full npm CLI behavior. `verifyNodePhase13AndroidPackageInstallManager`
covers the partial Android local package store with transaction staging, store
locking, cancellation, SHA-512 integrity, lifecycle/bin/native/path denial, and
install/update/remove/list/verify/prune. It does not enable registry download or
a JavaScript package-manager API.
`verifyNodePhase10LongRunningActivation` records the S10-29 through S10-36
long-running, scheduled, queue, checkpoint, and multi-instance boundary state.
Interactive foreground long-running and the scheduled runner are active through
focused gates; multi-instance remains primary-only and packaged long-running
service mode remains blocked. `verifyNodePhase10WorkerWasiBoundary` records
S10-37 through S10-42: pure WebAssembly stays usable, while `worker_threads`,
raw WASI, controlled WASI, worker+WASM, and packaged WASM cache/asset promotion
remain blocked or deferred. `verifyNodePhase10DeveloperExperience` records
S10-43 through S10-46 with a host/build-stage TypeScript transform fixture,
incremental cache evidence, source-map evidence, embedded `node:test` coverage,
and debug-only hot reload policy. `verifyNodeDeveloperExperienceGate` runs the
focused Sony TypeScript/source-map/node:test/debug-reload selector. Packaged and
release hot reload remain disabled. `verifyNodePhase10CrashAdapterCutover`
records S10-47 through S10-52: crash evidence bundle schema, resource/timeline
snapshot requirements, adapter operation inventory, legacy-vs-adapter A/B
blockers, and default-switch blockers. `verifyNodeCrashAdapterCutoverGate`
pairs that report with the Phase 9 native crash pipeline and adapter ABI/export
checks. The Node 24.5 adapter remains `surface_only`, so `legacy_jni` remains
the default execution path. `verifyNodePhase10SecurityCorpus` records S10-53
attack-surface evidence and fails if any corpus entry loses its code or gate
evidence. `generateNodePhase10Summary` records S10-56 and requires the final
Phase 10 50-fixture real npm corpus and 55-example catalog before it can report
`completed_with_recorded_blockers`. See
[reports/phase10/README.md](reports/phase10/README.md).

## Phase 11 Final Graduation Gates

Phase 11 introduces the final graduation contract. Generation tasks are safe to
run while required internal blockers are still open:

```powershell
.\gradlew.bat --console=plain :app:generateNodeFinalCapabilityTruth
.\gradlew.bat --console=plain :app:verifyNodeFinalFocusedGateTopology
.\gradlew.bat --console=plain :app:generateNodeFinalAdapterStatus
.\gradlew.bat --console=plain :app:generateNodeFinalCrashStatus
.\gradlew.bat --console=plain :app:generateNodeFinalIntegrationSummary
```

Strict final gates intentionally fail until the required internal blockers in
`docs/nodejs/final/FINAL_BLOCKER_REGISTRY.json` are closed:

```powershell
.\gradlew.bat --console=plain :app:verifyNodeFinalCapabilityTruth
.\gradlew.bat --console=plain :app:verifyNodeFinalAdapterGate
.\gradlew.bat --console=plain :app:verifyNodeFinalCrashGate
.\gradlew.bat --console=plain :app:verifyNodeFinalIntegrationGate
```

The final status vocabulary is only `completed` or `blocked`;
`completed_with_recorded_blockers` is not valid for required internal
capabilities. Node 24.17 remains an external runtime maintenance lane, and
`node24_5` remains the default runtime while Phase 11 work proceeds. See
[final/README.md](final/README.md).

## Phase 7 Focused Gates

Phase 7 uses focused feature gates instead of expanding every iteration to the
full release gate:

```powershell
$env:ANDROID_SERIAL="QV710AF65F"
.\gradlew.bat --console=plain :app:verifyNodePackagedEsmGate "-Pautojs.nodejs.test.deviceProfile=sony"
.\gradlew.bat --console=plain :app:verifyNodeLiveProviderGate "-Pautojs.nodejs.test.deviceProfile=sony" "-Pautojs.nodejs.network.experimental=true"
.\gradlew.bat --console=plain :app:verifyNodeLiveFetchGate "-Pautojs.nodejs.test.deviceProfile=sony" "-Pautojs.nodejs.network.experimental=true"
.\gradlew.bat --console=plain :app:verifyNodeLiveWebSocketGate "-Pautojs.nodejs.test.deviceProfile=sony" "-Pautojs.nodejs.network.experimental=true"
.\gradlew.bat --console=plain :app:verifyNodeLiveOcrGate "-Pautojs.nodejs.test.deviceProfile=sony"
.\gradlew.bat --console=plain :app:verifyNodeLiveStorageNotificationsGate "-Pautojs.nodejs.test.deviceProfile=sony"
.\gradlew.bat --console=plain :app:verifyNodeLiveSensorsGate "-Pautojs.nodejs.test.deviceProfile=sony"
.\gradlew.bat --console=plain :app:verifyNodeLiveAccessibilityImageGate "-Pautojs.nodejs.test.deviceProfile=sony"
.\gradlew.bat --console=plain :app:verifyNodeLiveUiGate "-Pautojs.nodejs.test.deviceProfile=sony"
.\gradlew.bat --console=plain :app:verifyNodeLiveWorkManagerGate "-Pautojs.nodejs.test.deviceProfile=sony"
.\gradlew.bat --console=plain :app:verifyNodeLongRunningGate "-Pautojs.nodejs.test.deviceProfile=sony"
.\gradlew.bat --console=plain :app:verifyNodeWorkerGate "-Pautojs.nodejs.test.deviceProfile=sony"
.\gradlew.bat --console=plain :app:verifyNodeModernCoreGate "-Pautojs.nodejs.test.deviceProfile=sony"
.\gradlew.bat --console=plain :app:verifyNodePhase7ResourceStressGate "-Pautojs.nodejs.test.deviceProfile=sony"
.\gradlew.bat --console=plain :app:verifyNodePhase7Gate "-Pautojs.nodejs.test.deviceProfile=sony" "-Pautojs.nodejs.network.experimental=true"
```

`verifyNodePhase7Gate` combines the current focused gate set, the dependency
sanitizer host gate, desktop differential reports, the expanded real npm corpus,
the Phase 7 security corpus, and the plugin/UI gate. It writes
`build/reports/nodejs/phase7-summary.json`; see
[reports/phase7/README.md](reports/phase7/README.md). Feature-specific live
subgates above remain explicit so their opt-in requirements and provider
assumptions stay visible. These gates select existing focused instrumentation
classes automatically unless
`android.testInstrumentationRunnerArguments.class` is supplied explicitly.
The S7-16 live-provider gate runs only live-provider or fail-closed availability
tests for fetch, WebSocket, OCR, storage, notifications, sensors,
accessibility/image, UI, and `work_manager`; fake-provider tests are excluded.
It requires `autojs.nodejs.network.experimental=true` so fetch and WebSocket are
real Android I/O instead of skipped, and writes
`app/build/reports/nodejs/live-provider-summary.json` with per-test interaction,
skip, and status metadata.
`verifyNodeLiveFetchGate` is an explicit experimental subgate: it fails unless
`autojs.nodejs.network.experimental=true` is set, then runs loopback
controlled-fetch/axios Android I/O plus provider destroy cancellation coverage.
`verifyNodeLiveWebSocketGate` follows the same opt-in rule for controlled
WebSocket Android I/O, including loopback text/binary roundtrip, close,
timeout/size-limit rejection, raw socket denial, and destroy cleanup.
`verifyNodeLiveOcrGate` validates the live MLKit OCR provider over
provider-owned image handles created by the scoped `image.readImage()` path,
including region recognition, `image.recycle()`, unknown-handle denial, and
zero active OCR/image resources after cleanup.
`verifyNodeLiveStorageNotificationsGate` validates Android-backed JSON storage
persistence, working-directory and package-identity isolation, storage quota
denial, notification post/cancel or runtime-permission denial, stable channel
creation, ongoing notification ownership metadata, and owned cleanup.
`NodeDatabaseBridgeInstrumentationTest` is the S7-35 focused database bridge
gate for controlled `database`/`sqlite` SQLite open/exec/run/get/all/transaction
behavior, scoped path denial, `ATTACH`/`load_extension` denial, persistence,
manifest denial, and resource-registry cleanup.
`verifyNodeLiveSensorsGate` validates Android-backed sensor listing, live
one-shot events, live subscribe/drain/unsubscribe behavior, sampling interval
clamping, maximum subscription denial, missing-provider unavailable errors, and
destroy cleanup for accelerometer/gyroscope/light providers available on the
device.
`verifyNodeLiveAccessibilityImageGate` validates selector timeout/cancellation,
accessibility provider unavailable errors, scoped `image.readImage()` to OCR to
`image.recycle()` live behavior, and screenshot/advanced image fail-closed
paths. If the AutoJs6 accessibility service is already enabled on the device,
it also launches a controlled Activity and validates real selector `findOne`
and selector `click`; otherwise that single real-accessibility smoke is an
explicit JUnit skip.
`verifyNodeLiveUiGate` validates the Activity-owned UI provider, JavaScript
event delivery, provider cleanup, descriptor denial, and foreign-handle denial.
`verifyNodeLiveWorkManagerGate` validates AndroidX WorkManager-backed
`work_manager` scheduling, real scheduled-runner embedded Node execution for
CommonJS and ESM entries, delayed cancel prevention, path/capability
enforcement, persisted `lastRun`/`runHistory` metadata, scheduled task
environment values, and Android 16 JobScheduler quota diagnostics in task
records.
`verifyNodeLongRunningGate` keeps normal long-running activation fail-closed
while covering the S7-18 interactive foreground MVP. It freezes the Phase 7
execution-mode state machine, verifies ordinary script launches still execute
only `one_shot`, rejects direct `interactiveLongRunning`/legacy `long_running`
before user code, and runs the explicit interactive session status/foreground
survival/Stop-action recovery test. S7-19 adds embedded foreground-service
manifest/type validation, foreground-service start-denial diagnostics, simulated
Android 15 `Service.onTimeout(...)` cleanup, and Android 16 WorkManager quota
metadata checks. S7-20 adds runtime budget parsing, heartbeat stale diagnostics,
provider quota rejection without leaked resources, and interactive status
heartbeat/budget summaries. S7-22 adds cooperative `autojs6:lifecycle`
checkpoint/restore coverage: an interactive stop hook writes a JSON-only
project-scoped checkpoint, stopped status exposes metadata, a settled later
interactive session restores explicitly, and automatic restart remains disabled.
S7-23 adds long-running stress coverage for repeated interactive stop,
checkpoint, one-shot recovery, and provider-destroy cleanup of owned
notification, sensor, UI, shell, and WebSocket counters. It also continues
lifecycle/resource stress coverage for timeouts, queueing, cleanup, and
reserved-mode rejection before user code.
`verifyNodeWorkerGate` covers the S7-24 worker bootstrap boundary. In normal
builds it runs `NodeWorkerThreadsBootstrapInstrumentationTest` and
`NodeWorkerThreadsDesignGateInstrumentationTest`: default `worker_threads`
requires and resolves stay denied before local shadow packages, profile flags
remain false, and a request-local experimental preflight reports either a usable
native Worker surface or stable `ERR_AUTOJS6_WORKER_DISABLED` diagnostics. When
`autojs.nodejs.worker_threads.experimental=true` is supplied, runnable worker
creation and resource stress classes remain promotion gates; they are skipped
with an explicit native-availability reason on the current Embedded Node build
instead of weakening the contract with a same-thread fallback.
`verifyNodeModernCoreGate` covers the S7-31 through S7-36 modern core surface:
`AsyncLocalStorage` propagation, diagnostics channel runtime telemetry,
`perf_hooks`, single-runtime `node:test`, promise-based timers/streams/readline,
`AbortSignal` helpers, `structuredClone`, and the limited global/WebCrypto
surface with negative security cases. It also runs the modern core differential
corpus for ESM, `process.permission`, `AsyncLocalStorage`, `perf_hooks`,
`diagnostics_channel`, `node:test`, `timers/promises`, `stream/promises`,
WebCrypto, and the separately classified controlled database bridge.
`verifyNodePhase7ResourceStressGate` is the S7-47 stress subset. It records RSS,
native heap, Java heap, thread count, fd count, test-only libuv handle counts,
and bridge resource diagnostics while combining long-running stress, bridge
resource leak loops, live fetch/WebSocket destroy cleanup, OCR/sensor/UI/
WorkManager cleanup, database open/transaction/close stress, WorkManager
scheduled execution cleanup, plugin load/unload lifecycle cleanup, and worker
resource stress when native workers are available. Live network entries still
require `autojs.nodejs.network.experimental=true`; otherwise those tests skip
with the same explicit opt-in reason as the live provider gates.

`verifyNodePhase13FullHeavyResourceStress` is the Phase 13 status gate for the
final 8 hour quick soak and 24 hour final soak. It consumes
`build/reports/nodejs/final-soak/quick-soak-8h.json` and
`build/reports/nodejs/final-soak/final-soak-24h.json`, checks comparable RSS,
Java heap, native heap, thread count, fd count, and bridge diagnostics, and
keeps the blocker open while either real device soak is missing or dirty.

## Release Gate

Use `verifyNodeReleaseGate` for release candidates:

```powershell
$env:ANDROID_SERIAL="QV710AF65F"
.\gradlew.bat --console=plain :app:verifyNodeReleaseGate "-Pautojs.nodejs.test.deviceProfile=sony"
```

Scope:

- release manifest generation
- desktop Node differential harnesses for real npm and modern core probes
- `verifyEmbeddedNodeReleaseCandidate`
- connected device instrumentation selected by `sony` or `full`

The release gate still relies on packaged smoke APK artifacts being current when packaged host verification is part of the selected path. See [TESTING.md](TESTING.md) for packaged smoke artifact collection.

## Manual Heavy Checks

Some checks remain explicit because they need a prepared device, a recently exported APK, or a failure-specific budget:

- reduced/full `NodeEmbeddedMemoryLeakStressInstrumentationTest`
- focused `verifyNodePhase7ResourceStressGate` with increased iteration
  arguments
- `verifyNodePhase13FullHeavyResourceStress` after archiving 8h/24h soak
  reports under `build/reports/nodejs/final-soak/`
- worker resource stress
- packaged APK output verifier with `-Pautojs.embeddedNode.packagedApk=<apk>`
- `verifyRhinoOnlyBuild` rollback verification
- template refresh through `embeddedNodeRefreshInrtTemplateApk`

Do not add retries around security assertions, resource leak assertions, or packaged smoke script failures. Use [FLAKE_POLICY.md](FLAKE_POLICY.md) for classification.
