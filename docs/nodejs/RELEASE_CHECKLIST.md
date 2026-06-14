# Embedded Node Release Checklist

Use this checklist for any release that includes the Embedded Node runtime or packaged Embedded Node support.

## Build

- [ ] `:app:compileAppDebugKotlin`
- [ ] `:app:compileAppDebugAndroidTestKotlin`
- [ ] `:app:externalNativeBuildAppDebug`
- [ ] `:app:nativeNodeDependencyReportAppDebug`
- [ ] `:app:nativeNodeSymbolArchiveReport`

## Device Tests

- [ ] `EmbeddedScriptMvpInstrumentationTest`
- [ ] `EmbeddedNodePackagedApkSmokeTest`
- [ ] `NodeEmbeddedLifecycleStressInstrumentationTest`
- [ ] `NodeAutoJsBridgeInstrumentationTest`
- [ ] `NodeCompatCorpusInstrumentationTest`
- [ ] `NodeSecurityRegressionInstrumentationTest`
- [ ] `NodeFsPromisesInstrumentationTest`
- [ ] `NodeStreamMvpInstrumentationTest`
- [ ] `NodeZlibBufferInstrumentationTest`
- [ ] `NodeCryptoExpansionInstrumentationTest`
- [ ] `NodeProcessSweepInstrumentationTest`
- [ ] `NodeOsPrivacyInstrumentationTest`
- [ ] `NodeEnginesBridgeInstrumentationTest`
- [ ] `NodeAccessibilitySelectorBridgeInstrumentationTest`
- [ ] `NodeImageBridgeExpansionInstrumentationTest`
- [ ] `NodeClipboardBridgeInstrumentationTest`
- [ ] `NodeDeviceBridgeInstrumentationTest`
- [ ] `NodeShellBridgeInstrumentationTest`
- [ ] `NodeFetchBridgeInstrumentationTest`
- [ ] `NodeWorkerThreadsDesignGateInstrumentationTest`
- [ ] `NodeExampleProjectInstrumentationTest`
- [ ] optional `NodeEmbeddedPerformanceInstrumentationTest` run recorded for lifecycle/native-load changes
- [ ] extended `NodeEmbeddedMemoryLeakStressInstrumentationTest` run recorded before release candidates that change lifecycle, bridge, fs, or image-handle behavior
- [ ] live bridge smoke verifies JavaScript can observe provider results
- [ ] bridge smoke verifies `engines` snapshots, controlled `execScript`/`execScriptFile`, scoped path policy, spawn depth, and unsupported `execAutoFile`
- [ ] bridge smoke verifies `dialogs` fake results and stable Android provider rejection
- [ ] bridge smoke verifies `accessibility` fake results, shadowing protection, disabled status, and stable action rejection
- [ ] bridge smoke verifies `accessibility` selector descriptors, `findOne`/`findAll`, snapshot-only nodes, invalid descriptor rejection, and stable Android provider rejection
- [ ] bridge smoke verifies `media_projection`/`image` fake handles, shadowing protection, and stable Android provider rejection
- [ ] bridge smoke verifies `image`/`images` capture/read/save/clip/resize/find/recycle shape, scoped path rejection, idempotent recycle, and stable Android provider rejection
- [ ] bridge smoke verifies `clipboard` fake provider results, shadowing protection, permission tagging, and no `watch` API
- [ ] bridge smoke verifies `device` fake provider results, shadowing protection, sensitive field absence, permission tagging, and no `watch` API
- [ ] bridge smoke verifies `shell` fake provider results, shadowing protection, timeout/cwd/env policy, explicit root permission tagging, and that `child_process` remains disabled
- [ ] bridge smoke verifies controlled `fetch` fake-provider GET/POST, timeout, abort, response budget, shadowing protection, and that raw `http`/`https`/`net`/`tls`/`dns` remain disabled
- [ ] worker gate verifies `worker_threads` stays disabled, cannot be shadowed, and reports `experimentalFlags.workerThreads=false`
- [ ] any future `tasks` bridge smoke verifies user-visible registration, scoped Node descriptors, and denied broadcast/boot/package paths
- [ ] checked-in examples `hello-node` and `toast-app` still run from `sample/nodejs`

## Packaged APK

- [ ] `:app:embeddedNodePackagingPreflightAppDebug`
- [ ] `:app:embeddedNodePackagedAppIntegrationDryRunAppDebug`
- [ ] `:app:embeddedNodePackagedApkOutputVerify`
- [ ] `:app:embeddedNodePackagedDiagnosticReport`
- [ ] `:app:embeddedNodePackagedSmokeHostVerify`
- [ ] packaged `toast` bridge smoke passes and cannot be shadowed by `node_modules/toast`
- [ ] packaged `app` bridge smoke passes, reports the packaged app id, and cannot be shadowed by `node_modules/app`
- [ ] packaged `clipboard` bridge smoke passes and cannot be shadowed by `node_modules/clipboard`
- [ ] packaged `device` bridge smoke passes, keeps sensitive identifiers absent, declares `android.permission.VIBRATE`, and cannot be shadowed by `node_modules/device`
- [ ] packaged `engines` bridge smoke passes after the inrt template carries live bridge metadata
- [ ] template APK refreshed when inrt bytecode or manifest changes
- [ ] `NativeNodeEmbeddedScriptService` exists in manifest
- [ ] `NativeNodeEmbeddedScriptService` exists in Dex
- [ ] service uses `android:process=":nodejs_embedded"`
- [ ] service uses `android:foregroundServiceType="specialUse"`
- [ ] specialUse subtype property exists
- [ ] `FOREGROUND_SERVICE` permission exists
- [ ] `FOREGROUND_SERVICE_SPECIAL_USE` permission exists
- [ ] packaged scheduled/background task receivers and permissions are absent unless the project explicitly opts in
- [ ] if packaged timed/boot/package tasks are enabled, manifest verifier reports the exact receivers, permissions, and foreground-service requirements
- [ ] if packaged controlled network is enabled, manifest verifier reports `android.permission.INTERNET`; if it is not enabled, normal builds keep `autojs.nodejs.network.experimental=false`

## Native Libraries

- [ ] selected release flavor is recorded: `nodeFull`, `nodeMini`, or `rhinoOnly`
- [ ] `nodeFull` uses `arm64-v8a,armeabi-v7a,x86_64`
- [ ] `nodeMini` records the exact `autojs.nodejs.releaseAbis` value
- [ ] `rhinoOnly` APK contains no `libnode.so` or `libautojs6-node.so`
- [ ] `arm64-v8a/libnode.so`
- [ ] `arm64-v8a/libautojs6-node.so`
- [ ] `arm64-v8a/libc++_shared.so`
- [ ] `armeabi-v7a/libnode.so`
- [ ] `armeabi-v7a/libautojs6-node.so`
- [ ] `armeabi-v7a/libc++_shared.so`
- [ ] `x86_64/libnode.so`
- [ ] `x86_64/libautojs6-node.so`
- [ ] `x86_64/libc++_shared.so`
- [ ] x86 remains fail-fast unless `libnode.so`, `libautojs6-node.so`, and smoke coverage are deliberately added
- [ ] `embeddedNodePackagingPreflightAppDebug` reports release flavor policy ready
- [ ] `nativeNodeSymbolArchiveReport` records per-ABI native library SHA-256 and Build ID values
- [ ] symbol archive report is stored with the APK, git commit, release flavor, and ABI split metadata

## Runtime Security

- [ ] scoped fs absolute path denial
- [ ] scoped fs `..` denial
- [ ] scoped fs NUL denial
- [ ] scoped fs `/proc`, `/sys`, and `/dev` denial
- [ ] realpath escape denial
- [ ] symlink escape denial
- [ ] package exports/imports escape denial
- [ ] native addon denial
- [ ] static ESM policy error
- [ ] dynamic import policy error
- [ ] `autojs.nodejs.esm.experimental=false` for normal release builds unless an explicit ESM experiment is being tested
- [ ] `autojs.nodejs.network.experimental=false` for normal release builds unless an explicit network experiment is being tested
- [ ] `autojs.nodejs.worker_threads.experimental=false` for normal release builds unless an explicit worker experiment is being tested
- [ ] dangerous process APIs denied
- [ ] canonical `error.autojs6Code` present for AutoJs6 policy denials
- [ ] network operations denied unless explicitly feature-gated
- [ ] controlled `fetch` live provider disabled in normal release builds unless explicitly testing a network experiment
- [ ] raw Node `http`, `https`, `net`, `tls`, `dns`, and `dgram` authority remains denied even if controlled fetch is enabled
- [ ] `worker_threads` authority remains denied unless explicitly feature-gated and security-reviewed
- [ ] direct WorkManager, AlarmManager, JobScheduler, and BroadcastReceiver authority remains denied to Node scripts

## Compatibility

- [ ] CommonJS local module loading
- [ ] JSON module loading
- [ ] `node_modules` traversal
- [ ] scoped package loading
- [ ] package `main`
- [ ] package `exports`
- [ ] package `imports`
- [ ] package self-reference
- [ ] builtin shadowing protection
- [ ] pure-JS npm corpus smoke

## Lifecycle

- [ ] normal script completes and service stops
- [ ] timeout returns stable error code
- [ ] timeout does not poison next run
- [ ] uncaught exception returns stable error shape
- [ ] unhandled rejection returns stable error shape
- [ ] stdout/stderr are captured and closed
- [ ] native handle diagnostics are available when debugging

## Diagnostics

- [ ] `process.version` and `process.versions` captured in smoke
- [ ] native dependency report generated
- [ ] native symbol archive report generated
- [ ] runtime probe logs `libnode.so.sha256` and `libautojs6-node.so.sha256`
- [ ] logcat/tombstone or bugreport collection steps in `NATIVE_CRASH_DIAGNOSTICS.md` remain current
- [ ] optional performance report generated with `NodeEmbeddedPerformanceInstrumentationTest` and pulled by `:app:pullNodeEmbeddedPerformanceReports` for lifecycle/native-load changes
- [ ] memory leak stress report generated with `NodeEmbeddedMemoryLeakStressInstrumentationTest` for lifecycle, bridge, fs, or image-handle changes
- [ ] packaged verifier gives actionable failures
- [ ] `ERR_AUTOJS6_*` codes stable for documented negative paths
- [ ] resolver trace remains off by default once implemented
- [ ] bridge live diagnostics are present for main-app bridge smoke
- [ ] docs updated for any changed capability

## Documentation

- [ ] `docs/nodejs/INTEGRATION_PLAN.md`
- [ ] `docs/nodejs/COMPATIBILITY_PROFILE.md`
- [ ] `docs/nodejs/SECURITY_MODEL.md`
- [ ] `docs/nodejs/TESTING.md`
- [ ] `docs/nodejs/MIGRATION_FROM_RHINO.md` when entry points, bridge modules, scoped fs, or packaged behavior changes in a user-visible way
- [ ] `docs/nodejs/EXAMPLES.md` when examples or example smoke coverage changes
- [ ] `docs/nodejs/PERFORMANCE.md` when benchmark behavior or report schema changes
- [ ] `docs/nodejs/NATIVE_CRASH_DIAGNOSTICS.md` when native crash capture, symbol archive, or symbolication behavior changes
- [ ] `docs/nodejs/design/WORK_MANAGER.md` when scheduled/background task behavior changes
- [ ] `docs/nodejs/design/ESM.md` when ESM or dynamic import behavior changes
- [ ] `docs/nodejs/design/NETWORK.md` when network behavior, packaging permissions, or request policy changes
- [ ] `docs/nodejs/design/WORKER_THREADS.md` when worker behavior or worker policy changes
- [ ] packaged APK user docs or examples if packaging behavior changed
