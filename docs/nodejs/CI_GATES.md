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
.\gradlew.bat --console=plain :app:verifyNodePhase13ProEntryDirectives
.\gradlew.bat --console=plain :app:verifyNodePhase13AutoJsGlobalV1
.\gradlew.bat --console=plain :app:verifyNodePhase13JavaInteropAllowlist
.\gradlew.bat --console=plain :app:verifyNodePhase13RhinoInstallJavaProxy
.\gradlew.bat --console=plain :app:verifyNodePhase13RhinoImportHelpersDecision
.\gradlew.bat --console=plain :app:verifyNodePhase13RhinoJavaAdapterDecision
.\gradlew.bat --console=plain :app:verifyNodePhase13LiveUiProviderPromotion
.\gradlew.bat --console=plain :app:verifyNodePhase13ShellRootShizukuCapability
.\gradlew.bat --console=plain :app:verifyNodePhase13NotificationsSettingsPowerManager
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
covers the closed v1.1 Android local-store substrate with transaction staging, store
locking, cancellation, SHA-512 integrity, lifecycle/bin/native/path denial, and
install/update/remove/list/verify/prune. It does not enable registry download,
remote tarball install, unverified cache reuse, or unrestricted JavaScript
package-manager APIs; the script-visible facade is guarded local mutation plus
read-maintenance and policy-only source planning:
status/policy/sourcePolicy/planInstall/planUpdate/planRemove/
planRegistryInstall/planTarballInstall/install/update/remove/list/verify/prune.
`verifyNodePhase13ProEntryDirectives` records the P13-11 Pro-style Node entry
directive state for `"nodejs ui"` and `"nodejs ui-thread"`, including
`NodeEntryDirective`, descriptor/project config metadata, debug/native values,
and focused instrumentation. The P13-11 metadata directive gate is ready;
direct UI provider evidence remains P13-17 work, while full packaged task
relaunch, crash/disclosure, and UI-thread dispatch equivalence remain future
packaged UI promotion work. The UI-thread policy is provider-owned asynchronous
main-looper dispatch, not V8 execution on the Android main thread.
`verifyNodePhase13AutoJsGlobalV1` records the P13-12 read-only `$autojs`
global v1 state: frozen global installation, `$autojs.version`,
`$autojs.profile`, diagnostic-only `$autojs.androidContext`, gated
`$autojs.java`, TypeScript declarations, and focused instrumentation. It does
not promote full Java/Rhino parity.
`verifyNodePhase13JavaInteropAllowlist` records the P13-13 ready Java interop
allowlist state: `require("java")` and `$autojs.java` expose explicit
findClass/type, constructor, static call, Rect instance call, Rect field read,
and release/dispose helpers only when the build-time experiment is enabled.
P13-13 also records the Java method budget policy for future allowlist
expansion. Classloader, reflection, native-library, process authority,
thread-dispatch / `$autojs.java.setThreadMode` APIs, and expanded-budget
enforcement remain fail-closed. Pro profile promotion is delegated to the
aggregate Java/Rhino surface gate rather than counted as a separate P13-13
allowlist blocker.
`verifyNodePhase13RhinoInstallJavaProxy` records the P13-14 ready
`rhino.install` state: default install rejection remains, while explicit
experimental installs can add `Packages`, `java`, and `android` Java proxies
that route through the same allowlist bridge. The P13-16 `JavaAdapter` /
`defineClass` replacement decision is satisfied as a stable-denial gate;
packaged metadata remains fail-closed for v1.1. Real Pro profile selector is
delegated to the aggregate Java/Rhino surface gate rather than counted as a
separate P13-14 blocker.
`verifyNodePhase13RhinoImportHelpersDecision` records the P13-15 decision:
`require("rhino").importClass()` and `.importPackage()` are stable
`ERR_AUTOJS6_RHINO_IMPORT_UNSUPPORTED` helpers, no global helpers are installed,
and migration uses local assignment from a P13-14 Java proxy target.
`verifyNodePhase13RhinoJavaAdapterDecision` records the P13-16 ready v1.1
stable-denial decision: `require("rhino").JavaAdapter()` is a stable
`ERR_AUTOJS6_RHINO_JAVA_ADAPTER_UNSUPPORTED` helper, no global `JavaAdapter` is
installed, and `java.defineClass()` / `$autojs.java.defineClass()` stay
`ERR_AUTOJS6_JAVA_METHOD_DENIED` until the bounded proxy-interface provider and
callback cleanup evidence land.
`verifyNodePhase13LiveUiProviderPromotion` records the P13-17 partial live UI
provider state: direct `require("ui")` Activity evidence is present for JSON
layout, event drain, updates, close, provider cleanup, and Back/Recents
lifecycle. The same gate records explicit configuration-change
destroy/recreate policy, packaged missing-`ui` capability denial, and packaged
entry/update/close smoke without a full live packaged UI survival claim.
Packaged task relaunch, crash recovery, and user-visible packaged disclosure
still block opt-in promotion.
`verifyNodePhase13OverlayProviderPromotion` records the P13-18 partial
`ui.overlay` provider state: JS facade/types, explicit capabilities, Android
permission/settings/closeAll live gate, packaged `SYSTEM_ALERT_WINDOW` metadata
records, packaged missing-`ui.overlay`/missing-`SYSTEM_ALERT_WINDOW` denial,
and raw Window/View denial are present, while live overlay
`show`/`update`/`drainEvents`/per-handle `close`, foreground disclosure,
positive packaged install-run behavior, and real overlay leak/stress evidence
still block opt-in promotion.
`verifyNodePhase13AccessibilitySelectorPromotion` records the P13-19 partial
`accessibility` selector state: selector v2 JSON descriptors, direct
find/action APIs, explicit Rhino compat selector handles, unavailable-provider
rejection, and raw node denial are present, while `swipe`, `gesture`,
and `powerDialog` stay stable-denied for P13-19 v1.1. Packaged accessibility
service/settings lifecycle and expanded real-device evidence still block
opt-in promotion. Reserved future labels
`input.gesture`, `input.gesture.root`, and `input.gesture.shizuku` split
gesture/root/Shizuku authority away from `accessibility`. Direct `waitFor`, `pickup`,
selector-style `detect`, `existsAll`, and `existsOne` stay stable-denied for
P13-19 v1.1.
`verifyNodePhase13ScreenshotImageOcrPromotion` records the P13-20 partial
`media_projection`, `image`/`images`, and `ocr` promotion state: reserved
screen-capture API shape, scoped image read/recycle, ML Kit OCR over
provider-owned image handles, Rhino compat wrappers, cleanup, and raw object
denial are present, while bridge-owned MediaProjection prompting, advanced
image processing, packaged capture/OCR behavior, and packaged disclosure
evidence still block opt-in promotion. Paddle/Rapid/plugin OCR provider
selection and raw Bitmap/OpenCV/MediaProjection object parity stay
stable-denied for P13-20 v1.1.
`verifyNodePhase13TaskDatabaseProvider` records the P13-21 partial
daily/weekly/user task database provider state: live `work_manager` scheduled
work, `compat.tasks` one-shot migration, rejection stubs, Android/Rhino task
database substrate evidence, raw task object denial, and descriptor/id mapping
policy are present, while direct `tasks`, daily/weekly JSON provider
operations, packaged task database promotion, and raw Rhino task mutation remain
blocked.
`verifyNodePhase13IntentBroadcastProvider` records the P13-22 partial
IntentTask/broadcast provider state: Android/Rhino `IntentTask` persistence,
receiver substrate, `compat.tasks` IntentTask rejection stubs, and raw broadcast
object denial are present; packaged denial/metadata-boundary smoke verifies
future `tasks.*` modules and task/broadcast capabilities stay unavailable.
Receiver registration, manifest mutation, capability split, positive packaged
install/trigger/remove behavior, and external broadcast wakeups remain blocked.
`verifyNodePhase13AppIntentBroadcastParity` records the P13-23 bounded
app/intent/broadcast Pro parity state as ready: JSON-safe `app.intent()` descriptors,
`app.startActivity()` descriptor normalization, TypeScript/conformance evidence,
JSON-safe `app.parseUri()` descriptors, and stable `app.sendBroadcast()`,
raw-URI, kill, dual-user, plus privileged app-option rejection are present,
with broadcast/raw URI/root/Shizuku/dual-user authority stable-denied for
P13-23 v1.1. The descriptor alias and Rhino app gap matrices classify the
supported short forms, stable denials, and future-provider work. `app_query`
privacy disclosure and the future redacted installed-app list schema are
documented; packaged scoped-file Activity smoke now covers packaged
`app.activity` metadata and view/edit routing.
`verifyNodePhase13ShellRootShizukuCapability` records the P13-24 bounded
shell/root/Shizuku capability closure: `shell`, `shell.root`, and
`shell.shizuku` are split capability tokens, `execRoot()` routes through the
existing controlled shell provider, and Shizuku execution rejects before bridge
dispatch while authorization, lifecycle, packaged disclosure, and device matrix
evidence remain future promotion work.
`verifyNodePhase13NotificationsSettingsPowerManager` records the P13-25 partial
notifications/settings/power manager state: `notifications.settings` and
`device.power` are exact capability tokens, notification permission/settings
snapshots and battery-optimization settings dry-run helpers are covered, while
notification actions/listeners, Do-Not-Disturb/exact-alarm authority, direct
wakelock/power-manager control, and raw Android notification/power objects are
stable-denied for v1.1. Packaged metadata, install/run, notification
action-or-permission-denial, settings/power dry-run, exact-denial smoke,
rollback diagnostics, and the `notifications-power` Pro migration example are
covered by
`EmbeddedNodePackagedApkSmokeTest#installAndRunNotificationsSettingsPowerBridgeEmbeddedNodePackagedApks`.
OEM matrices, background-launch behavior, and foreground disclosure UX remain
future evidence.
`verifyNodePhase13MediaRecorderMediainfoProvider` records the P13-26 partial
media/recorder/mediainfo provider state: `media.audio`, `media.metadata`, and
`media.recording` are exact capability tokens, read-only media audio snapshots,
scoped mediainfo reads, recorder status, and stable recorder start/stop
fail-closed behavior are covered. Playback, MediaStore access, screen/internal
audio capture, and raw Android media objects are stable-denied for v1.1, while
packaged bundled-asset lookup, recorder status, recorder start denial,
exact-denial smoke, recorder privacy copy, and the `media-recorder` Pro
migration example are covered. Real recorder sessions, foreground microphone
disclosure, cleanup/resource handles, and broader corpus evidence remain open.
`verifyNodePhase13UtilityHeavyRhinoModules` records the P13-27 partial
utility-heavy Rhino modules state: pure `jsox.mathx`, `jsox.arrayx`, and
`jsox.numberx` helpers are covered through `jsox`, direct submodule requires,
and `compat.jsox`, while `zip`, `canvas`, OpenCC/pinyin packaged resources,
`sysprops`, raw Canvas/Paint, and raw Android/OpenCV object parity remain
blocked.
`verifyNodePhase13HttpHttpsClientParity` records the P13-29 controlled
HTTP/HTTPS client subset as ready: controlled `http`/`https` request/get,
stream-response, timeout, redirect, TLS/provider-error, proxy/tunnel denial,
server/listen denial, shadow denial, TypeScript declarations, desktop builtin
matrix evidence, live Android/OkHttp loopback fetch/axios/`http.get()` evidence,
and packaged `network` to `android.permission.INTERNET` metadata proof are
covered. The offline HTTP npm corpus also covers follow-redirects-style
handling, TLS provider failure handling, and proxy configuration denial. Raw
socket authority, DNS/TLS session APIs, broader packaged install/run network
behavior, and proxy support remain blocked or scoped to later gates.
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
P13-17 additionally uses `verifyNodePhase13LiveUiProviderPromotion` as the host
promotion-status gate for this same provider and keeps packaged live UI
provider-dependent until packaged task relaunch, crash, and disclosure evidence
lands.
P13-18 additionally uses `verifyNodePhase13OverlayProviderPromotion` as the host
promotion-status gate for `ui.overlay` and keeps the Android provider degraded:
permission/settings/closeAll are live-gate methods, but live overlay creation
methods remain fail-closed until a reviewed provider and positive
packaged/leak evidence land. Packaged missing-overlay capability/metadata
denial is covered by the focused APK smoke.
P13-19 additionally uses `verifyNodePhase13AccessibilitySelectorPromotion` as
the host promotion-status gate for `accessibility` selector parity and keeps
advanced automation blocked: the existing live provider remains selector-only
and service-dependent, while gesture APIs, packaged service lifecycle, and
root/Shizuku tiers require separate evidence.
P13-20 additionally uses `verifyNodePhase13ScreenshotImageOcrPromotion` as the
host promotion-status gate for screenshot/image/OCR parity and keeps the
Android providers partial: `image.readImage()`/`recycle()` and ML Kit OCR over
provider-owned handles are live, while MediaProjection prompting, capture,
save/transform/match/color providers, packaged behavior, and disclosure evidence
require separate evidence. Paddle/Rapid/plugin OCR provider selection and raw
Bitmap/OpenCV/MediaProjection object parity stay stable-denied for P13-20 v1.1.
P13-21 additionally uses `verifyNodePhase13TaskDatabaseProvider` as the host
promotion-status gate for daily/weekly/user task database parity and keeps the
provider partial: `work_manager` and `compat.tasks` one-shot scheduling are
covered, and JSON-safe legacy `TimedTask` query/get snapshots are recorded,
while direct `require("tasks")`, daily/weekly database
add/update/enable-disable/remove operations, packaged behavior, and raw task
objects require separate evidence.
P13-22 additionally uses `verifyNodePhase13IntentBroadcastProvider` as the host
promotion-status gate for IntentTask/broadcast parity and keeps the provider
partial: Android/Rhino receiver substrate and stable rejection stubs are
covered, while direct receiver registration, boot/package/custom triggers,
manifest synthesis, positive packaged install/trigger/remove behavior, and raw
broadcast objects require separate evidence. Packaged denial/metadata-boundary
smoke covers stable rejection stubs, future `tasks.*` module absence, and
future task/broadcast capability denial.
P13-23 additionally uses `verifyNodePhase13AppIntentBroadcastParity` as the
host promotion-status gate for bounded app/intent/broadcast parity:
JSON-safe `app.intent()`, normalized `app.startActivity()`, scoped
file view/edit helpers, `app.launch()` alias parity, JSON-safe
`app.parseUri()`, and stable raw-URI/kill/dual-user rejections are covered,
while `sendBroadcast`, raw URI/FileProvider handles, and root/Shizuku app
options stay stable-denied for P13-23 v1.1. `app_query` privacy disclosure and
the future redacted installed-app list schema are documented; packaged scoped
file Activity behavior is covered by the focused packaged app bridge smoke.
P13-24 additionally uses `verifyNodePhase13ShellRootShizukuCapability` as the
host promotion-status gate for shell/root/Shizuku capability parity and keeps
the surface partial: ordinary shell and explicit root facades are covered by
the controlled `shell.exec` provider, while Shizuku execution stays
stable-denied for v1.1 and root-device matrix closure, packaged disclosure,
cancellation evidence, and raw process/root/Shizuku handles require separate
evidence.
P13-25 additionally uses `verifyNodePhase13NotificationsSettingsPowerManager`
as the host promotion-status gate for notifications/settings/power manager
parity and keeps the surface partial: permission/settings snapshots and
battery-optimization status/settings dry-run helpers are covered, while
notification actions/listeners, direct power-manager APIs, wakelocks,
Do-Not-Disturb/exact-alarm authority, and raw Android notification/power objects
are stable-denied for v1.1. Packaged metadata/install-run, exact-denial smoke,
rollback diagnostics, and the `notifications-power` Pro migration example are
covered, while OEM matrices, background-launch behavior, and foreground
disclosure UX require separate evidence.
P13-26 additionally uses `verifyNodePhase13MediaRecorderMediainfoProvider` as
the host promotion-status gate for media/recorder/mediainfo parity and keeps
the surface partial: read-only audio stream snapshots, scoped mediainfo
metadata, recorder status, and stable recorder provider-missing denials are
covered. Playback, MediaStore access, and raw Android media objects are
stable-denied for v1.1. Packaged media/recorder asset-status-denial smoke,
recorder privacy copy, and the `media-recorder` Pro migration example are
covered, while real recording, foreground microphone disclosure,
cleanup/resource handles, and broader corpus evidence require separate
evidence.
P13-27 additionally uses `verifyNodePhase13UtilityHeavyRhinoModules` as the
host promotion-status gate for utility-heavy Rhino module parity and keeps the
surface partial: pure `jsox.mathx`, `jsox.arrayx`, and `jsox.numberx` helpers,
explicit-target `jsox.extend()`, and `compat.jsox` are covered, while `zip`,
`canvas`, OpenCC/pinyin packaged resources, Rhino `util` split batches,
`sysprops`, and raw Android/Canvas object parity require separate evidence.
P13-29 additionally uses `verifyNodePhase13HttpHttpsClientParity` as the host
promotion-status gate for controlled HTTP/HTTPS client parity and keeps the
desktop network/fs surface partial: client request, stream response, timeout,
redirect, TLS/provider error, proxy/tunnel denial, and fetch/axios boundary are
covered. The offline HTTP npm corpus also covers follow-redirects-style
handling, TLS provider failure handling, and proxy configuration denial, while
packaged INTERNET evidence, live provider HTTP smoke, raw sockets,
server/listen, DNS/TLS session control, and proxy support require separate
evidence.
P13-30 additionally uses `verifyNodePhase13NetTlsDnsStagedCompatibility` as
the host promotion-status gate for staged `net`/`tls`/`dns` compatibility and
keeps the desktop network/fs surface partial: reserved builtin identity,
shadow denial, socket/server shape metadata, `SocketAddress`, `BlockList`,
loopback/IP-literal DNS lookup, DNS privacy guardrails, TLS metadata,
fail-closed default connect/listen/resolve/session APIs, and the
release-forbidden `autojs.nodejs.rawNetworkModules.experimental=true` native
module opt-in path are covered, while release/default client sockets, server
sockets, Android resolver/search-domain/VPN/topology exposure, and release raw
network promotion are explicitly rejected for v1.1. TLS sessions, fd leak proof,
packaged INTERNET evidence, and live Android network evidence require separate
promotion evidence.
The raw-network opt-in external DNS sample is opportunistic evidence rather
than a deterministic CI prerequisite: native DNS errors are accepted as long as
AutoJs6 raw-network policy denial is not produced while the opt-in is enabled.
P13-31 additionally uses `verifyNodePhase13DgramUdpDecision` as the host
decision gate for UDP and keeps the desktop network/fs surface partial:
`dgram`/`node:dgram`, `require.resolve()`, transitive package require, and
local shadow attempts remain disabled, while UDP client sockets, server
bind/listen, multicast, broadcast, packaged INTERNET/multicast proof, fd leak
instrumentation, and live Android UDP evidence require separate evidence.
P13-32 additionally uses `verifyNodePhase13FilesystemProfileDecision` as the
host decision gate for filesystem profile expansion and keeps the desktop
network/fs surface partial: Safe `workingDirectory` scoped fs, frozen
`filesystemProfile` diagnostics, `/sdcard`, `/proc`, `/sys`, `/dev`, NUL,
`..`, symlink, and realpath denial evidence are covered, while
user-authorized roots, SAF/document roots, app-private roots, Android shared
storage expansion, packaged expanded-root smoke, grant revocation diagnostics,
and P13-33 advanced desktop fs API decisions require separate evidence.
P13-33 additionally uses `verifyNodePhase13FilesystemAdvancedApis` as the host
decision gate for advanced filesystem APIs. It requires profile diagnostics,
focused scoped stream/FileHandle/fd/opendir/watch/realpath evidence, existing
S8 lifecycle/fuzzer suites, packaged realpath/opendir markers, and documentation
updates while keeping raw fd exposure, expanded roots, packaged advanced-fs
lifecycle smoke, pending watcher diagnostics, Android unsupported behavior
notes, and full desktop `fs` semantics unpromoted.
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
`verifyNodePhase13WorkerThreadsOptInPromotion` records P13-34 as the Phase 13
worker decision gate. It requires `workerThreadsProfile` diagnostics, default
denial/shadow protection, request-local experimental native preflight, existing
worker/MessageChannel/WorkerPool/resource/packaged evidence anchors, and docs
updates while keeping default worker authority, project/package opt-in, native
runnable claims, native-available packaged worker promotion, nested-worker
policy, and Android resource ceilings unpromoted. Current resource/crash
evidence references are recorded, but native-available resource/RSS reruns
remain promotion work.
`verifyNodePhase13ProcessWorkerReplacementDecision` records P13-35 as the
process-worker replacement decision gate. It requires
`processWorkerReplacementProfile` diagnostics, no callable
`process_worker`/`compute_worker` facade, WorkerPool as the current replacement
path, continued `child_process` denial, `shell` as a separate scoped command
bridge, reserved `worker_computation`, P13-06-closed `second_execution_slot`
diagnostics, and docs updates while keeping native WorkerPool proof, packaged behavior, slot
accounting, cleanup stress, and future launch-surface evidence open. P13-35
v1.1 has no user-facing process-worker API or UX; P13-40 is the separate track
for any future scoped `child_process` runner.
`verifyNodePhase13ChildProcessScopedRunnerDecision` records P13-40 as the
child_process scoped runner decision gate. It requires `childProcessProfile`
diagnostics, stable `child_process` / `node:child_process` require/resolve
denial, local shadow-package protection, separate `shell` bridge evidence,
TypeScript declarations, desktop builtin matrix evidence, surface manifest
evidence, and docs updates while keeping any callable child_process module,
scoped runner API, app-private binary allowlist, bounded stdio,
cancel/kill/cleanup semantics, packaged behavior, and hostile corpus coverage
unpromoted. P13-40 records child_process scoped runner as partial.
`verifyNodePhase13EsmLoaderParityDecision` records P13-41 as the ESM loader
parity decision gate. It requires `esmLoaderProfile` diagnostics, focused
local ESM loader evidence, existing ESM/package/source-map/security evidence,
TypeScript declarations, surface manifest evidence, and docs updates while
keeping raw loader hooks, raw `node:module` loader authority,
network/file/unrestricted URL imports, custom conditions/import maps, broad
packaged encrypted graphs, pending-evaluation cleanup, and full desktop
source-map parity unpromoted. Controlled JavaScript/JSON `data:` URL imports
are partial evidence, not raw loader authority. P13-41 records ESM loader parity
as partial.
`verifyNodePhase13PackageManagerAndroidDecision` records P13-42 as the Android
package manager decision gate. It requires `packageManagerProfile`
diagnostics, the P13-09 Android local package store, the Phase 10 host-managed
package manager boundary, focused instrumentation, TypeScript declarations,
surface manifest evidence, and docs updates while keeping Android npm CLI,
registry download, lifecycle execution, bin shims, symlink materialization,
native builds/native payloads, postinstall downloads, unrestricted install
authority, raw executable authority, and raw registry access unpromoted. The
`package_manager` guarded mutation facade is limited to
status/policy/sourcePolicy, dry-run planInstall/planUpdate/planRemove,
policy-only planRegistryInstall/planTarballInstall, guarded install/update/
remove requiring `package_manager.mutate` plus `allowMutation: true`, and
list/verify/prune. Registry plan diagnostics may parse caller-provided npm
`metadata`/`packument` for dist-tag/semver/integrity verdicts, and tarball
plan diagnostics may parse URL/name/version/sha512 shape locally, but the gate
must still keep Android registry/tarball download and install authority denied.
The `npm` facade is allowed only as a guarded package-manager alias and must
keep CLI/registry/lifecycle entry points denied.
P13-42 records Android package manager parity as partial.
`verifyNodePhase13StdlibConformanceDecision` records P13-43 as the stdlib
conformance decision gate. It requires `stdlibProfile` diagnostics,
representative focused instrumentation, existing core builtin compatibility
evidence, real npm corpus baseline evidence, modern-core differential
evidence, TypeScript declarations, surface manifest evidence, and docs updates
while keeping full desktop stdlib parity, raw native handles, unrestricted
network behavior, broad terminal control, raw `process.getBuiltinModule`
bypass behavior, and `zlib/promises` unpromoted. Controlled
`process.getBuiltinModule` is partial and returns the same limited wrappers as
`require()`. P13-43 records Node stdlib conformance as partial.
`verifyNodePhase13ProcessParityDecision` records P13-44 as the process/env/argv
parity decision gate. It requires `processParityProfile` diagnostics, focused
process parity instrumentation, existing process sweep evidence, embedded
project-runner argv/env/cwd evidence, string-coercing env mutation and env
filtering evidence, modern-core process permission evidence, TypeScript
declarations, surface manifest evidence, and docs updates while keeping
native/raw process exit, Android signal delivery, raw inherited env, raw process
handles, native bindings, and full desktop process parity unpromoted. P13-44
records process/env/argv parity as partial.
`verifyNodePhase13PackagedCapabilityMetadataDecision` records P13-45 as the
packaged APK capability metadata decision gate. It requires
`packagedCapabilityProfile`, structured `bridgePermissions.packagedMetadata`,
focused packaged metadata instrumentation, existing bridge permission manifest
evidence, APK builder Android permission synthesis evidence, packaged
runtime/launch descriptor evidence, TypeScript declarations, surface manifest
evidence, metadata warning diagnostics, and docs updates while keeping profile
selection, builtin promotion, native asset loading, expanded filesystem roots,
and unreviewed Android permission synthesis unpromoted. P13-45 records packaged
APK capability metadata as partial.
`verifyNodePhase13PluginSystemParityDecision` records P13-46 as the plugin
system parity decision gate. It requires `pluginSystemProfile`, focused plugin
system parity instrumentation, existing JS-only plugin manager evidence, plugin
design/policy docs, TypeScript declarations, surface manifest evidence, and
docs updates while keeping Android provider plugins, native plugins, remote
marketplace install/update, signed archives, packaged plugin distribution, and
plugin-granted host authority unpromoted. P13-46 records plugin system parity
as partial.
`verifyNodePhase13DependencyCompatibilityCorpus` records P13-47 as the
dependency compatibility corpus gate. It requires the fixed 60-fixture real npm
manifest, `phase13Compatibility` taxonomy, targeted fixture deltas,
package/reason rows for `missing_builtin`, `native_addon`, `postinstall`,
`network`, and `fs`, metadata instrumentation, differential pass-rate
reporting, surface manifest evidence, and docs updates while keeping unsafe
package execution, live registry drift, native payloads, lifecycle scripts, and
postinstall downloads unpromoted.
P13-47 records dependency compatibility corpus as partial.
`verifyNodePhase13PackageLockIntegrityPolicy` records P13-48 as the package
lock and integrity policy gate. It requires `NodePackageIntegrityPolicy`,
`packageIntegrityProfile`, Android package manager `nativeValues()` policy
diagnostics, focused rollback/prune/corruption recovery instrumentation,
plugin and native addon fail-closed evidence, TypeScript declarations, surface
manifest evidence, and docs updates while keeping unsigned plugin install,
unsigned native addon load, raw registry install, lifecycle scripts,
postinstall downloads, and unverified cache reuse unpromoted. P13-48 records
package lock and integrity policy as partial.
`verifyNodePhase13ProfileSelectionDiagnostics` records P13-49 as the profile
selection UX and diagnostics gate. It requires
`NodeProfileSelectionDiagnostics`, `autojs6:profile.profileSelection`,
capability-not-declared `requiredProfiles` / `requiredNodePermissions` /
`requiredAndroidPermissions` / `profileSelectionHint`, Node Doctor Profile
Selection JSON/text, TypeScript declarations, surface manifest evidence, and
docs updates while keeping effective profile selection at `safe_default` and
user-selectable Pro/Desktop/Debug promotion disabled. P13-49 records profile
selection UX and diagnostics as partial.
`verifyNodePhase13RelaxedCapabilitySecurityCorpus` records P13-50 as the
relaxed-capability security corpus gate. It requires
`RELAXED_CAPABILITY_SECURITY_CORPUS.json`, focused
`NodeSecurityRegressionInstrumentationTest` coverage, companion raw
network/filesystem/Java/child_process/native addon/inspector/WASI evidence,
per-capability runtime guards and promotion preconditions, surface manifest
evidence, and docs updates while keeping raw sockets,
expanded filesystem roots, raw process/native handles, raw WASI, inspector,
native addons, and release Java interop unpromoted. P13-50 records
relaxed-capability security corpus as partial.
`verifyNodePhase13ProfileRollbackPlaybook` records P13-51 as the per-profile
rollback playbook gate. It requires default-off Pro/Desktop/Debug profile and
relaxed-capability Gradle switches, `NodeProfileRollbackPolicy`,
`autojs6:profile.profileRollbackPolicy`, Node Doctor rollback diagnostics,
focused instrumentation, TypeScript declarations, surface manifest evidence,
and docs updates while keeping effective authority at `safe_default` and
automatic crash circuit breaker, remote packaged kill switch, settings UI,
packaged builder UI, and privacy prompts unpromoted. P13-51 records
per-profile rollback playbook as partial.
`verifyNodePhase13PrivacyDisclosurePolicy` records P13-52 as the privacy and
disclosure policy gate. It requires `NodePrivacyDisclosurePolicy`,
`autojs6:profile.privacyDisclosurePolicy`, Node Doctor Privacy Disclosure
Policy diagnostics, focused instrumentation, TypeScript declarations, surface
manifest evidence, and docs updates while keeping implicit high privilege and
packaged implicit high privilege denied. P13-52 records privacy and disclosure
policy as partial; prompt UI, foreground notification ownership, packaged
review UI, and runtime enforcement remain unpromoted.
`verifyNodePhase13ProParityExamples` records P13-53 as the Pro parity examples
gate. It requires the `sample/nodejs/pro-parity-suite` catalog, per-snippet
required capabilities, expected outputs, packaged behavior, skip reasons,
`verifyNodeExamples` registration,
`NodeExampleProjectInstrumentationTest#proParitySafeSubsetExamplesRun`
focused runtime smoke for Activity-owned UI layout and WorkManager one-shot
schedule/cancel behavior, surface manifest evidence, release matrix updates,
and docs updates while keeping high-risk snippets out of default runtime smoke.
P13-53 records Pro parity examples as partial; live packaged Pro execution,
prompt/disclosure UI, provider promotion, and device permission matrices remain
unpromoted.
`verifyNodePhase13DesktopParityExamples` records P13-54 as the desktop Node
parity examples gate. It requires the `sample/nodejs/desktop-parity-suite`
catalog, per-snippet `desktop_compat_opt_in` profile metadata, required
capabilities, expected outputs, Safe-profile denial markers, packaged behavior,
skip reasons, `verifyNodeExamples` registration,
`NodeExampleProjectInstrumentationTest#desktopParitySafeSubsetExamplesRun`
focused runtime smoke for managed local ESM, controlled HTTP/HTTPS facade shape
without live network I/O, checked-in pure JavaScript dependency behavior, and
scoped advanced fs FileHandle/watch behavior, surface manifest evidence, release
matrix updates, and docs updates while keeping high-risk desktop snippets out of
default runtime smoke. P13-54 records desktop Node parity examples as partial;
packaged desktop parity, raw worker/raw-network/expanded-root/WASI/inspector
authority, and Android
package-manager parity remain unpromoted.
`verifyNodePhase13TypeDeclarationProfiles` records P13-55 as the
profile-aware TypeScript declaration gate. It requires
`profile_capabilities.d.ts`, `index.d.ts` wiring, declaration README updates,
`typescript-smoke` Safe/Pro/Desktop/Debug conditional type coverage,
P13-49 profile selection, P13-51 rollback, and P13-52 privacy/disclosure type
alignment, `verifyAutoJs6NodeTypeDeclarations`, `typeCheckNodeTypescriptSmoke`,
surface manifest evidence, release matrix updates, and docs updates while
keeping the matrix metadata-only. P13-55 records TypeScript profile
declarations as partial; runtime profile promotion and generated declaration
package release checks remain unpromoted.
`verifyNodePhase13MigrationPro9Comparison` records P13-56 as the migration and
Pro9 comparison docs gate. It requires `MIGRATION_FROM_RHINO.md`,
`INTEGRATION_STATUS_PRO9_COMPARISON.md`,
`COMPATIBILITY_NOTES_PRO_AUTOX.md`, surface manifest evidence, release matrix
updates, and docs updates while classifying surfaces as Pro-compatible,
Desktop-compatible, or gated/diagnostic-only. P13-56 records migration and Pro9
comparison docs as partial; debug-only/provider POC is not formal availability,
and examples, the `raw_network` desktop marker, or profile declarations remain
evidence only. The gate also links P13-56 to the P13-57
completed/open/deferred/rejected summary output without closing runtime or
packaged migration blockers.
`generateNodePhase13Summary` records P13-57 as the Phase 13 summary and v1.1
closeout gate. It requires the task checklist, surface manifest, final blocker
registry, `phase13-capability-truth`, generated conformance reports, Testing/CI
docs, release matrix updates, and `docs/nodejs/reports/phase13/PHASE13_SUMMARY.md`.
The gate lists completed/open/deferred/rejected categories, requires evidence
for every promoted surface, and keeps open or decision-required blockers out of
the completed bucket. It keeps `raw_network` rejected for v1.1 while P13-50
through P13-56 preserve a future relaxed/Desktop evidence trail without
granting release authority.
`verifyNodePhase13VmModuleDecision` records P13-36 as the `vm` module decision
gate. It requires `vmProfile` diagnostics, native `vm`/`node:vm`
require/resolve behavior, local shadow-package protection, the updated VM
design gate, TypeScript declarations, surface manifest evidence, and docs
updates plus focused repeated-script budget, timeout, boundary-probe, and
packaged fixture smoke evidence.
`verifyNodePhase13InspectorDebugProfileDecision` records P13-37 as the
inspector/devtools debug profile decision gate. It requires `inspectorProfile`
diagnostics, stable `inspector`/`node:inspector` require/resolve denial, local
shadow-package protection, the existing inspector design/security gates,
TypeScript declarations, surface manifest evidence, and docs updates while
keeping callable inspector sessions, DevTools attach, loopback ADB forwarding,
CPU/heap profiler artifacts, redaction proof, and packaged debug behavior
unpromoted. P13-37 records inspector/devtools debug profile as partial.
`verifyNodePhase13ControlledWasiProfileDecision` records P13-38 as the
controlled WASI profile decision gate. It requires `wasiProfile` diagnostics,
pure WebAssembly evidence, stable `wasi`/`node:wasi` require/resolve denial,
local shadow-package protection, existing Phase 9/10 WASM/WASI evidence,
TypeScript declarations, surface manifest evidence, and docs updates while
keeping callable WASI facades, scoped preopen broker, virtual fd/env/args
policy, resource budgets, worker integration, and packaged `.wasm`/WASI
behavior unpromoted. For P13-38 v1.1, pure WebAssembly is the supported runtime
path and raw/controlled WASI APIs stay stable-denied. P13-38 records controlled
WASI profile as partial.
`verifyNodePhase13NativeAddonLoadingDecision` records P13-39 as the native
addon loading decision gate. It requires `nativeAddonProfile` diagnostics,
stable `.node` require/resolve denial, private process native API denial,
existing native-addon policy evidence, dependency/package native-payload
rejection evidence, TypeScript declarations, surface manifest evidence, and
docs updates while keeping signed packaged artifacts, ABI/hash allowlists,
dependency scanning, crash isolation, download-exec policy evidence, and
packaged positive/hostile corpus unpromoted. P13-39 records native addon
loading as partial.
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
It also runs fixture-based contract checks from
`tools/nodejs/phase13/fixtures/full-heavy-resource-stress/` to prove accepted
report shapes and fail-closed under-duration, missing-metric, leak/orphan, and
unrecoverable-runtime shapes. Those fixtures are CI contract tests only and do
not satisfy the 8h/24h soak requirement.
Use `tools/nodejs/phase13/run-full-heavy-resource-soak.js` for the real device
soaks:

```powershell
node tools/nodejs/phase13/run-full-heavy-resource-soak.js --kind quick --serial QV710AF65F --device-profile sony --archive
node tools/nodejs/phase13/run-full-heavy-resource-soak.js --kind final --serial QV710AF65F --device-profile sony --archive
```

For short smoke validation, pass `--duration-ms` and leave `--archive` off.
Use `tools/nodejs/phase13/archive-full-heavy-resource-soak.js` to validate and
copy completed soak JSON into the canonical final-soak paths; the helper writes
an archive audit report and refuses dirty or under-duration inputs.

`verifyNodePhase14Baseline` and `verifyNodePhase14SurfaceManifest` are the
Phase 14 v1.2 foundation gates:

```powershell
.\gradlew.bat --console=plain :app:verifyNodePhase14Baseline
.\gradlew.bat --console=plain :app:verifyNodePhase14SurfaceManifest
.\gradlew.bat --console=plain :app:verifyNodePhase14PromotionContracts
.\gradlew.bat --console=plain :app:verifyNodePhase14LiveUiProviderPackaged
```

The baseline gate depends on `generateNodePhase13Summary`, keeps the Phase 13
v1.1 closeout state as the starting contract, imports the nine Phase 13
future-promotion tasks, and writes `phase14-baseline-verification.json` plus
`.md`. The surface manifest gate verifies the Phase 14 manifest, schema/docs,
Gradle wiring, and exact P14-00 through P14-39 task ownership. The promotion
contracts gate verifies shared v1.2 promotion states, report conventions,
release-blocker classification, packaged high-privilege disclosure, rollback
and crash circuit breaker fields, and Android 12 / Android 15+ device matrix
requirements. The packaged live UI provider gate writes
`phase14-live-ui-provider-packaged.json` plus `.md`, imports the `P13-17`
packaged UI capability-denial and entry/update/close smoke evidence, records
`pro.ui_automation_media` as `provider_poc`, and keeps packaged task relaunch,
crash recovery, and user-visible disclosure as future-promotion blockers.

`collectNodePhase13CrashEvidence` is the Phase 13 status gate for P13-05 crash
evidence automation. It verifies the runtime marker store, next-launch
collector, embedded/native process marker writers, required crash marker
metadata, libnode hashes, Phase 11 symbolization tooling, simulated native fatal
running-marker collection, real fatal native SIGKILL chaos collection through
`:nodejs_embedded`, packaged launch-surface marker contract, and signal-safe
marker boundary. It intentionally keeps `crash_evidence_automation` open until
real packaged APK crash smoke and signal-safe fixed-size native marker proof are
available.

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
