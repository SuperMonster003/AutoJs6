# AutoJs6 Embedded Node.js Testing

This document records the current test commands and the expected gates for future Embedded Node work.

## Baseline Reports

- Post-18 baseline: `docs/nodejs/reports/BASELINE_AFTER_TASK_18.md`
- Post-49 baseline: `docs/nodejs/reports/BASELINE_AFTER_TASK_49.md`

## Build Smoke

Run these before device tests:

```powershell
.\gradlew.bat --console=plain :app:compileAppDebugKotlin
.\gradlew.bat --console=plain :app:compileAppDebugAndroidTestKotlin
.\gradlew.bat --console=plain :app:externalNativeBuildAppDebug
.\gradlew.bat --console=plain :app:nativeNodeDependencyReportAppDebug
.\gradlew.bat --console=plain :app:nativeNodeSymbolArchiveReport
```

## Device Instrumentation

Current embedded runtime regression suite:

```powershell
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.EmbeddedScriptMvpInstrumentationTest"
```

Current lifecycle stress instrumentation:

```powershell
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeEmbeddedLifecycleStressInstrumentationTest"
```

Focused embedded execution queue coverage:

```powershell
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeEmbeddedLifecycleStressInstrumentationTest#queuedExecutionRunsAfterActiveExecution"
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeEmbeddedLifecycleStressInstrumentationTest#queuedExecutionCanBeCancelled"
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeEmbeddedLifecycleStressInstrumentationTest#queueLimitReachedReturnsStableError"
```

These tests verify that default concurrent starts still reject, explicit enqueue runs after the active script, queued executions can be cancelled by execution id, and the one-entry queue limit returns `ERR_AUTOJS6_NODE_QUEUE_FULL`.

Current packaged APK smoke instrumentation:

```powershell
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.apkbuilder.EmbeddedNodePackagedApkSmokeTest"
```

This suite covers packaged CommonJS/npm/fs/native-lib behavior and the packaged `toast`/`app`/`clipboard`/`device` bridge smoke, including bridge-module shadowing protection.

Focused packaged `toast` bridge smoke:

```powershell
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.apkbuilder.EmbeddedNodePackagedApkSmokeTest#installAndRunToastBridgeEmbeddedNodePackagedApk"
```

Checked-in example project smoke:

```powershell
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeExampleProjectInstrumentationTest"
```

This runs `sample/nodejs/hello-node` and `sample/nodejs/toast-app` from the `androidTest` asset source set.

Focused packaged `app` bridge smoke:

```powershell
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.apkbuilder.EmbeddedNodePackagedApkSmokeTest#installAndRunAppBridgeEmbeddedNodePackagedApk"
```

Focused packaged `clipboard` bridge smoke:

```powershell
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.apkbuilder.EmbeddedNodePackagedApkSmokeTest#installAndRunClipboardBridgeEmbeddedNodePackagedApk"
```

Focused packaged `device` bridge smoke:

```powershell
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.apkbuilder.EmbeddedNodePackagedApkSmokeTest#installAndRunDeviceBridgeEmbeddedNodePackagedApk"
```

Current bridge protocol instrumentation:

```powershell
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeAutoJsBridgeInstrumentationTest"
```

This suite covers the Kotlin bridge protocol, strict malformed request shape checks, bridge module registry, unavailable module/method errors, malformed live request responses, duplicate provider callback suppression, provider dispatch exception conversion, JavaScript fake transport, timeout/destroy handling, the current private live file transport, and the `toast`/`app`/`engines`/`dialogs`/`accessibility`/`media_projection`/`image` bridge smoke. The `app` smoke verifies that JavaScript can observe a real provider result, including a missing application lookup returning `false`. The `engines` smoke verifies current engine snapshots, bridge-module shadowing protection, live `all`/`stopAll`/`stopSelf` responses, and the controlled `execScript`/`execScriptFile` method shape. The `dialogs` smoke verifies fake provider results and the stable Android rejection path while the UI provider is not connected. The `accessibility` smoke verifies fake provider results, shadowing protection, disabled status reporting, and stable action rejection until an accessibility provider is connected. The `media_projection` and `image` smoke verifies fake opaque handles, `image`/`images` shadowing protection, and stable Android provider rejection until screenshot and image providers are connected. The `clipboard` and `device` bridges have their own focused test classes.

Current compatibility corpus instrumentation:

```powershell
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeCompatCorpusInstrumentationTest"
```

This suite copies `app/src/androidTest/assets/node_compat_corpus/` into a scoped working directory and verifies pure JavaScript CommonJS package patterns: basic CJS, deep dependencies, package `exports`, package `imports`, self-reference, JSON module caching, `util.promisify(fs)`, `Buffer`/`path`/`util`, and disabled-feature detection. It also runs a moduleSources/preload-shaped fixture as a packaged-preload proxy; a true packaged APK corpus smoke remains a follow-up.

Current fixed real npm corpus instrumentation:

```powershell
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeRealNpmCompatInstrumentationTest"
```

This suite copies `app/src/androidTest/assets/npm_compat_real/` into a scoped working directory and runs fixed, offline,
license-noted CommonJS package snapshots. It covers small utilities, nested dependencies, semver parsing,
querystring/URL utilities, glob/path matching, crypto hashing, stream utilities, and zlib buffer utilities. It also
checks that the corpus contains no native addons, no install/postinstall scripts, and no ESM-only package metadata.

Desktop Node differential harness:

```powershell
node tools/nodejs/differential/run-differential-tests.js
```

This host-side script runs the fixed real npm fixtures under desktop Node 24 and writes:

```text
build/reports/nodejs/differential/desktop-node-differential.json
build/reports/nodejs/differential/desktop-node-differential.md
```

`NodeRealNpmCompatInstrumentationTest` emits `realnpm.differential.result=...` JSON lines for each fixture. The Android
Gradle plugin stores these lines in the connected test logcat output, for example under
`app/build/outputs/androidTest-results/connected/.../logcat-org.autojs.autojs.engine.NodeRealNpmCompatInstrumentationTest-realPureJavaScriptPackageCorpusPasses.txt`.
Pass that file to the harness with `--autojs-log <file>` to compare AutoJs6 against the desktop baseline. Differences are
classified as matched, expected AutoJs6 restriction, unsupported, flaky, or bug.

Current dependency bundler verification:

```powershell
.\gradlew.bat --console=plain :app:bundleNodeDependencies
.\gradlew.bat --console=plain :app:verifyNodeDependencyBundlerFixtures
```

`bundleNodeDependencies` validates an existing project root with `package.json` and `node_modules`, then writes a
sanitized bundle plus `dependency-manifest.json`, `dependency-size-report.txt`, and `dependency-rejections.txt`.
`verifyNodeDependencyBundlerFixtures` confirms the checked-in sample project passes and an unsafe fixture is rejected for
native addon, lifecycle script, and unchecked `.bin` entries.

Current doctor diagnostics:

```powershell
.\gradlew.bat --console=plain :app:embeddedNodeDoctorReport
```

`embeddedNodeDoctorReport` writes attachable text and JSON reports under
`app/build/reports/nodejs/doctor/`. It aggregates the compatibility profile, backend and feature flags, enabled/disabled
modules, native library hashes from the symbol archive report, optional packaged APK diagnostics, dependency bundler
status, and differential harness status when those reports are available. Host-only fields such as live
`workingDirectory`, last error, lifecycle state, and resource counts are marked as unavailable until a device/runtime
diagnostic is attached.

Current security regression instrumentation:

```powershell
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeSecurityRegressionInstrumentationTest"
```

This suite verifies dangerous process APIs, disabled builtins, network operation denial, scoped fs escape denial, native addon extension denial, and package exports/imports escape denial with stable error codes or documented current denial shape. `process.chdir` is treated as scoped and controlled; the security tests verify absolute and escaping chdir targets are rejected.

The network portion also asserts that normal builds keep `BuildConfig.NODEJS_NETWORK_EXPERIMENTAL_ENABLED=false`; raw network operations must stay denied until `docs/nodejs/design/NETWORK.md` is implemented by a later task.

Current `fs.promises` focused instrumentation:

```powershell
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeFsPromisesInstrumentationTest"
```

This suite verifies scoped `fs.promises` and `fs/promises` aliases, FileHandle operations, `fs.createReadStream`, file URL helpers, builtin shadowing protection, promise drain behavior, and negative paths for absolute, escaping, NUL, and missing files.

Current stream focused instrumentation:

```powershell
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeStreamMvpInstrumentationTest"
```

This suite verifies `stream`/`node:stream` identity, shadowing protection, `Readable.from`, `PassThrough`, `Transform`, `stream.finished`, and `stream/promises` `pipeline`/`finished` success and error behavior.

Current zlib focused instrumentation:

```powershell
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeZlibBufferInstrumentationTest"
```

This suite verifies `zlib`/`node:zlib` identity, shadowing protection, callback/promisified/buffer/stream roundtrips, invalid input rejection, frozen constants, pending callback drain, and stable denial for `zlib/promises`.

Current crypto focused instrumentation:

```powershell
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeCryptoExpansionInstrumentationTest"
```

This suite verifies `crypto`/`node:crypto` identity, shadowing protection, `randomUUID`, `createHmac`, `timingSafeEqual`, high-risk asymmetric API absence, and the current limited WebCrypto boundary.

Current process focused instrumentation:

```powershell
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeProcessSweepInstrumentationTest"
```

This suite verifies `process.nextTick` ordering, warning listeners, event-emitter helpers, inert `allowedNodeEnvironmentFlags`, writable `process.exitCode`, scoped `chdir` rejection, and dangerous process API denial.

Current `node:module` safe subset focused instrumentation:

```powershell
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeModuleSafeSubsetInstrumentationTest"
```

This suite verifies the partial `module`/`node:module` surface, shadowing protection, filtered `builtinModules`, scoped `createRequire` loading for local and package dependencies, `child_process` denial through created require functions, and unsafe base or target rejection.

Current `vm` design gate focused instrumentation:

```powershell
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeVmDesignGateInstrumentationTest"
```

This suite verifies the registry/profile disabled state for `vm`, denial for `require("vm")`, `require("node:vm")`, `require.resolve("vm")`, `require.resolve("node:vm")`, and shadowing protection against `node_modules/vm`.

Current `os` privacy focused instrumentation:

```powershell
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeOsPrivacyInstrumentationTest"
```

This suite verifies `os`/`node:os` identity, shadowing protection, redacted `networkInterfaces`, `userInfo`, `cpus`, memory, load, hostname, cwd-backed directories, safe constants, and disabled `setPriority`.

Current `clipboard` bridge focused instrumentation:

```powershell
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeClipboardBridgeInstrumentationTest"
```

This suite verifies the `clipboard` bridge registry, fake provider request/response behavior, `require("clipboard")`, shadowing protection, Promise return values, timeout propagation, permission tagging, and that no `watch` API is exposed.

Current `device` bridge focused instrumentation:

```powershell
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeDeviceBridgeInstrumentationTest"
```

This suite verifies the `device` bridge registry, fake provider request/response behavior, `require("device")`, shadowing protection, sync metric property shape, Promise return values, timeout propagation, permission tagging, vibration argument normalization, sensitive field absence, and that no `watch` API is exposed.

Current `engines` bridge focused instrumentation:

```powershell
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeEnginesBridgeInstrumentationTest"
```

This suite verifies the `engines` bridge registry, fake provider request/response behavior, `require("engines")`, shadowing protection, `execScript`/`execScriptFile` payload shape, timeout requirement, scoped `cwd`/path rejection, permission tagging, recursive spawn denial through `spawnDepth`, main-process dispatch of scoped Rhino child scripts, and stable rejection of nested embedded Node child scripts.

Current `accessibility` selector bridge focused instrumentation:

```powershell
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeAccessibilitySelectorBridgeInstrumentationTest"
```

This suite verifies selector v1 builder shape, bridge registry entries for `findOne`/`findAll`, fake provider request/response behavior, `require("accessibility")`, shadowing protection, frozen selector descriptors, frozen node snapshots, invalid descriptor pre-dispatch rejection, timeout and permission propagation, and stable Android provider rejection until a safe accessibility provider is connected.

Current `image` bridge expansion focused instrumentation:

```powershell
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeImageBridgeExpansionInstrumentationTest"
```

This suite verifies the `image`/`images` bridge registry, fake provider request/response behavior, `captureScreen`, scoped `readImage`/`saveImage` path rejection, `clip`, `resize`, `findImage`, idempotent `recycle`, shadowing protection, opaque handle normalization, timeout and permission propagation, and stable Android provider rejection until a real image capability provider is connected.

Current bridge permission manifest focused instrumentation:

```powershell
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeBridgePermissionManifestInstrumentationTest"
```

This suite verifies `project.json node.permissions`, `package.json autojs6.node.permissions`, capability normalization, Android manifest permission mapping, declared `toast` dispatch, undeclared `accessibility` pre-dispatch rejection with `ERR_AUTOJS6_BRIDGE_CAPABILITY_NOT_DECLARED`, and the JavaScript capability report exposed through `autojs6:bridge` and `autojs6:profile`.

Current `shell` bridge focused instrumentation:

```powershell
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeShellBridgeInstrumentationTest"
```

This suite verifies the `shell` bridge registry, fake provider request/response behavior, `require("shell")`, shadowing protection, timeout propagation, `cwd` and `env` normalization, explicit root permission tagging, real Android non-root command execution, non-zero exit results, timeout result shape, policy denials, and that `child_process` remains disabled.

Current controlled fetch bridge focused instrumentation:

```powershell
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeFetchBridgeInstrumentationTest"
```

This suite verifies the `fetch` bridge registry, fake provider GET/POST behavior, `require("fetch")`, global `fetch`, shadowing protection, timeout rejection, abort rejection, response-size budget rejection, unsupported URL scheme rejection, redirect limit rejection, max concurrent request rejection, service-destroy rejection cleanup, fetch diagnostics counters, raw `http`/`https`/`net` denial, pending bridge cleanup, and that normal Android providers reject with `ERR_AUTOJS6_NETWORK_DISABLED` while `autojs.nodejs.network.experimental=false`.

Current controlled WebSocket bridge focused instrumentation:

```powershell
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeWebSocketBridgeInstrumentationTest"
```

This suite verifies the registry/profile experimental state for `websocket`, fake-provider connect/send/receive/close behavior, `require("websocket")`, shadowing protection, timeout rejection, unsupported scheme rejection, max connection rejection, message-size budget rejection, service-destroy rejection cleanup, WebSocket diagnostics counters, raw `net`/`tls` denial, pending bridge cleanup, and that normal Android providers reject with `ERR_AUTOJS6_NETWORK_DISABLED` while `autojs.nodejs.network.experimental=false`.

Current controlled work_manager bridge focused instrumentation:

```powershell
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeWorkManagerBridgeInstrumentationTest"
```

This suite verifies the `work_manager` bridge registry, fake-provider schedule-once and schedule-periodic descriptors, `require("work_manager")`, shadowing protection, packaged project-entry metadata serialization, scoped path rejection, interval/backend policy rejection, permission tagging, Android provider metadata storage, `cancel()`, `list()`, and that `require("tasks")` remains unavailable. Packaged queued-fallback rejection remains a release/template gate because the normal instrumentation harness injects live bridge metadata.

Current Java interop allowlist focused instrumentation:

```powershell
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeJavaInteropBridgeInstrumentationTest"
```

This suite verifies `BuildConfig.NODEJS_JAVA_INTEROP_EXPERIMENTAL_ENABLED=false`, disabled default `require("java")`, shadowing protection against `node_modules/java`, request-local experimental enablement, fake transport serialization, `java.lang.Math`, `java.util.UUID`, opaque `android.graphics.Rect` handles, provider destroy cleanup, and denial of Runtime, ClassLoader, and reflection classes.

Current bridge resource registry focused instrumentation:

```powershell
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeBridgeResourceRegistryInstrumentationTest"
```

This suite verifies direct registry cleanup for image-handle, screen-capturer, and WebSocket resource types; idempotent close behavior; leaked-resource diagnostics; Android provider cleanup for experimental Java object handles; `work_manager` metadata cancellation and destroy cleanup; and shell timeout cleanup through the live provider resource registry.

Current bridge concurrency and resource-limit focused instrumentation:

```powershell
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeBridgeConcurrencyLimitsInstrumentationTest"
```

This suite verifies bridge limit parsing from project/package metadata, safe override acceptance, above-hard-max rejection with warnings, JavaScript pending-call flood rejection, JavaScript image-handle quota and recycle/reuse behavior, Android provider accessibility request-rate limiting, shell command resource limiting, and the `require("autojs6:profile").bridgeLimits` report.

Current `worker_threads` design gate focused instrumentation:

```powershell
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeWorkerThreadsDesignGateInstrumentationTest"
```

This suite verifies `BuildConfig.NODEJS_WORKER_THREADS_EXPERIMENTAL_ENABLED=false`, the Kotlin capability registry, JavaScript profile flags, bare and `node:` `worker_threads` denial, `require.resolve("worker_threads")` denial, frozen profile flags, and shadowing protection against `node_modules/worker_threads`.

Current experimental worker_threads focused instrumentation:

```powershell
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeWorkerThreadsExperimentalInstrumentationTest"
```

This suite verifies the default disabled path, then enables the request-local experimental flag to cover local CommonJS worker scripts, `workerData`, `parentPort.postMessage`, `terminate()`, max-worker rejection, scoped path escape rejection, shadowing protection, Worker diagnostics counters, and denial of AutoJs bridge modules, `fs`, raw `net`, and nested workers inside worker scope.

Current experimental worker_threads resource stress instrumentation:

```powershell
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeWorkerThreadsResourceStressInstrumentationTest"
```

This suite verifies 50 sequential worker create/terminate cycles, worker exception recovery, infinite-loop worker timeout recovery, parent-completion cleanup for pending worker messages, worker cleanup diagnostics counters, and coarse RSS/thread-count snapshots around the sequential stress case.

Current feature flag registry focused instrumentation:

```powershell
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeFeatureFlagRegistryInstrumentationTest"
```

This suite verifies the centralized `NodeRuntimeCapabilities.featureFlags` registry, safe defaults for high and critical security flags, denied project/package override policy, expected build-time override allowlist, and JavaScript `require("autojs6:profile").featureFlags` diagnostics.

Current ESM experimental focused instrumentation:

```powershell
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeEsmExperimentalInstrumentationTest"
```

This suite keeps `.mjs` rejected with the default flag, then enables the internal experimental request flag to cover `.mjs` entry execution, local static `.mjs` imports, top-level await, package `type=module` entry routing, scoped import escape denial, ESM default import from CommonJS, ESM import of supported builtins, disabled builtin rejection from ESM, CJS `require()` of ESM rejection with `ERR_AUTOJS6_REQUIRE_ESM_UNSUPPORTED`, package `exports`/`imports` condition selection with resolver trace for both ESM and CommonJS importers, `.mjs` package targets, nested package imports, self-reference, null blocking, unsafe target denial, dynamic import default denial when only ESM is enabled, experimental local relative dynamic import, dynamic import scoped escape denial, and package/URL dynamic import rejection.

Experimental packaged `.mjs` smoke is deliberately gated behind the Gradle flag:

```powershell
.\gradlew.bat --console=plain :app:assembleInrtDebug :app:assembleAppDebug :app:assembleAppDebugAndroidTest "-Pautojs.nodejs.esm.experimental=true"
adb push app\build\outputs\apk\inrt\debug\inrt-v6.7.1-alpha4-universal.apk /sdcard/Download/AutoJs6/embedded-node-template/inrt-debug-template.apk
adb shell am instrument -w -r -e class org.autojs.autojs.apkbuilder.EmbeddedNodePackagedApkSmokeTest#installAndRunExperimentalEsmEmbeddedNodePackagedApk -e autojs.embeddedNode.packagedSmokeTemplateOverride true org.autojs.autojs6.test/androidx.test.runner.AndroidJUnitRunner
```

With the default flags off, the packaged ESM diagnostics smoke continues to assert `ERR_AUTOJS6_EMBEDDED_NODE_ESM_UNSUPPORTED`, and the packaged dynamic import diagnostics smoke continues to assert `ERR_AUTOJS6_EMBEDDED_NODE_DYNAMIC_IMPORT_UNSUPPORTED`. The experimental packaged smoke intentionally requires a refreshed inrt template override because release/default templates must keep ESM and dynamic import disabled. The current packaged ESM smoke is a single-file `.mjs` entry check; encrypted packaged ESM and dynamic import module graphs are still design-gated follow-ups.

Current source map stack trace focused instrumentation:

```powershell
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeSourceMapStackTraceInstrumentationTest"
```

This suite verifies inline `sourceMappingURL=data:` stack mapping, external `.map` stack mapping inside `workingDirectory`, and scoped denial diagnostics for external source maps outside `workingDirectory`.

Current Embedded Node performance benchmark:

```powershell
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeEmbeddedPerformanceInstrumentationTest"
.\gradlew.bat --console=plain :app:pullNodeEmbeddedPerformanceReports
```

This benchmark writes machine-readable JSON reports to `app/build/reports/nodejs/perf/` after the pull task. It records device/ABI metadata, cold and warm script wall time, runtime `elapsedMs`, native load duration when exposed, RSS/PSS/thread/fd snapshots, and Node `process.memoryUsage()`/`process.resourceUsage()`. It is optional and does not fail release builds by default; pass `autojs.nodejs.perf.failOnThreshold=true` plus threshold instrumentation arguments for controlled severe-regression checks. See `docs/nodejs/PERFORMANCE.md`.

Current Embedded Node memory leak stress gate:

```powershell
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeEmbeddedMemoryLeakStressInstrumentationTest"
.\gradlew.bat --console=plain :app:pullNodeEmbeddedPerformanceReports
```

This extended gate repeatedly runs simple scripts, scoped fs callbacks, `fs.promises`, fake `toast`/`app` bridge calls, fake image-handle create/recycle cycles, timeout scripts, and uncaught exception scripts. It writes `autojs6-node-memory-leak-stress-v1` JSON reports and fails only on conservative monotonic runaway detection for RSS, Java heap, native heap, thread count, or fd count. Use `autojs.nodejs.leak.iterations`, `autojs.nodejs.leak.imageIterations`, and `autojs.nodejs.leak.faultIterations` for shorter local probes.

## Packaged APK Verification

Current host-side packaged checks:

```powershell
.\gradlew.bat --console=plain :app:embeddedNodePackagingPreflightAppDebug
.\gradlew.bat --console=plain :app:embeddedNodePackagedAppIntegrationDryRunAppDebug
.\gradlew.bat --console=plain :app:embeddedNodePackagedApkOutputVerify
.\gradlew.bat --console=plain :app:embeddedNodePackagedDiagnosticReport
.\gradlew.bat --console=plain :app:embeddedNodePackagedSmokeHostVerify
```

Current compatibility profile generation and registry/docs verification:

```powershell
.\gradlew.bat --console=plain :app:generateNodeCompatibilityProfile
.\gradlew.bat --console=plain :app:verifyNodeCompatibilityProfile
```

This writes `build/reports/nodejs/compatibility-profile.json` and `build/reports/nodejs/compatibility-profile.md`, compares the Kotlin capability registry with the JavaScript bootstrap builtin allowlist/denylist, and fails when `docs/nodejs/COMPATIBILITY_PROFILE.md` omits registry entries.

`embeddedNodePackagedApkOutputVerify` and `embeddedNodePackagedDiagnosticReport` inspect a concrete exported APK. Pass it with
`-Pautojs.embeddedNode.packagedApk=<apk>` and optionally constrain ABI expectations with
`-Pautojs.embeddedNode.expectedAbis=arm64-v8a,armeabi-v7a,x86_64`.

If `autojs.embeddedNode.expectedAbis` is omitted, the verifier derives the ABI policy from `autojs.nodejs.releaseFlavor`:

- `nodeFull`: `arm64-v8a,armeabi-v7a,x86_64`
- `nodeMini`: `autojs.nodejs.releaseAbis`, defaulting to `arm64-v8a`
- `rhinoOnly`: no Embedded Node runtime native libraries

See `docs/nodejs/design/RELEASE_FLAVORS.md`.

The packaged diagnostic report also prints whether the inspected manifest declares `android.permission.INTERNET`. Controlled fetch and controlled WebSocket remain disabled in normal builds unless a packaged project opts into network explicitly; raw Node network modules must stay denied in either state.

Template refresh and smoke artifact collection:

```powershell
.\gradlew.bat --console=plain :app:embeddedNodeRefreshInrtTemplateApk
.\gradlew.bat --console=plain :app:embeddedNodePullPackagedSmokeApks
```

## ABI Matrix

Required source native libs for Node packaging:

- `arm64-v8a`
- `armeabi-v7a`
- `x86_64`

Known ABI list:

- `arm64-v8a`
- `armeabi-v7a`
- `x86_64`
- `x86`

Expected policy:

- `arm64-v8a`: full instrumentation and packaged smoke
- `armeabi-v7a`: smoke and lifecycle subset
- `x86_64`: emulator smoke and resolver/fs tests
- `x86`: explicit fail-fast until `libnode.so`, `libautojs6-node.so`, and smoke coverage are all added deliberately

Release packaging profiles:

- `nodeFull`: default, requires all supported Node ABIs
- `nodeMini`: supports a smaller explicit ABI set
- `rhinoOnly`: must not package `libnode.so` or `libautojs6-node.so`

## Security Negative Tests

Every release candidate should cover these classes of denial:

- `require("autojs6:profile")` stays frozen and reports disabled high-risk capabilities
- legacy `error.code` values remain compatible while canonical `error.autojs6Code` is present
- `process.binding`
- `process._linkedBinding`
- `process.dlopen`
- `process.exit`
- `child_process`
- `worker_threads`
- native addon `.node`
- network I/O
- `worker_threads` default denial and design-gate flag
- static ESM import
- dynamic import
- `autojs.nodejs.esm.experimental=false` keeps `.mjs` rejected by default
- `autojs.nodejs.worker_threads.experimental=false` keeps worker threads rejected by default
- absolute filesystem paths
- `..` filesystem escape
- NUL byte paths
- `/proc`, `/sys`, and `/dev`
- symlink and realpath escape
- package export/import escape

## Resolver Trace

CommonJS resolution trace is available for targeted debugging and is off by default. Enable it for one run with:

```js
process.env.AUTOJS6_NODE_RESOLVE_TRACE = "1";
require("some-package");
```

Project runner callers may pass `AUTOJS6_NODE_RESOLVE_TRACE=1` in the request environment before the entry script runs.

When enabled, trace lines are written to stderr with a deterministic `[resolve]` prefix. The trace includes request/referrer, local and `node_modules` finals, package scope discovery, package `exports`/`imports` matches, and resolver errors. Do not rely on this output in production scripts.

Resolver visualizer:

```powershell
node tools/nodejs/resolver/explain-node-resolve.js --request trace-pkg/private --from app/src/main.js --trace-file path\to\resolve-stderr.log
.\gradlew.bat --console=plain :app:explainNodeResolve "-Pautojs.nodejs.resolve.request=trace-pkg/private" "-Pautojs.nodejs.resolve.from=app/src/main.js" "-Pautojs.nodejs.resolve.traceFile=path\to\resolve-stderr.log"
.\gradlew.bat --console=plain :app:verifyNodeResolverVisualizerFixtures
```

The resolver visualizer does not run a second resolver. It formats the real Embedded Node `[resolve]` trace captured with
`AUTOJS6_NODE_RESOLVE_TRACE=1` into `app/build/reports/nodejs/resolver/resolve-explanation.txt` and `.json`. The report
lists the original request, parent module, `node_modules` search paths, package.json files read, selected
`exports`/`imports`/`main` branch, `target=null` blocking, and the final error.

## Runtime Timeline Trace

Embedded runtime timeline tracing is available for targeted lifecycle diagnostics and is off by default. It can be enabled
for a build with:

```powershell
.\gradlew.bat --console=plain :app:assembleAppDebug "-Pautojs.nodejs.timelineTrace=true"
```

Project runner and instrumentation callers may enable it for one request by passing `AUTOJS6_NODE_TIMELINE_TRACE=1` in the
embedded script environment.

When enabled, the embedded result `nativeValues` include:

- `embedded_script.timeline_trace_enabled=true`
- `embedded_script.timeline_event_count`
- `embedded_script.timeline_json`

The JSON timeline records compact events such as service start, native library load, inferred V8 and Node environment
creation, bootstrap load, user script entry, first stdout, pending callback and timer counts, bridge call counts, stop
request, drain completion, and destroy completion. It is intended for diagnosing slow startup and false completion only;
normal production scripts should leave it disabled.

## Lifecycle Tests

The embedded service must be verified beyond one successful script.

Current lifecycle diagnostics:

- every embedded script service execution gets `embedded_script.execution_id`
- result `nativeValues` include `embedded_script.runtime_state`
- result `nativeValues` include `embedded_script.lifecycle_transitions`
- opt-in timeline trace uses `embedded_script.timeline_json`
- bridge live transport diagnostics use `embedded_script.bridge_live_*`
- logcat uses `node.runtime.state executionId=<id> FROM -> TO`
- normal completion should include `CREATED -> STARTING -> RUNNING -> DRAINING -> STOPPED`
- service destruction should add `DESTROYED` in logcat
- timeout fallback should include `RUNNING -> TIMED_OUT -> STOPPING`

Current lifecycle stress cases:

- run simple script repeatedly
- timeout does not poison next run
- uncaught exception does not poison next run
- unhandled rejection does not poison next run
- uncleared interval times out and next run succeeds
- nested callbacks drain before success
- stdout and stderr close after run
- concurrent embedded execution rejects with `ERR_AUTOJS6_NODE_ENGINE_BUSY` and a later run succeeds

Planned lifecycle stress cases:

- run and stop repeatedly with an explicit user stop path
- service stops after script exit
- packaged app lifecycle smoke

## Documentation Gate

Any Node-facing runtime behavior change must update:

- `docs/nodejs/COMPATIBILITY_PROFILE.md`
- `docs/nodejs/SECURITY_MODEL.md` when a boundary changes
- `docs/nodejs/TESTING.md` when commands or suites change
- `docs/nodejs/RELEASE_CHECKLIST.md` when a release gate changes
- `docs/nodejs/EXAMPLES.md` when adding or changing checked-in examples
- `docs/nodejs/PERFORMANCE.md` when benchmark metrics, report schema, or threshold arguments change
- `docs/nodejs/design/` design notes when changing broad surfaces such as fs, file URL, stream, zlib, scheduled/background tasks, ESM, network, workers, Java interop, multi-instance lifecycle, Rhino shims, or native addons

## Aggregate Tasks

Use these aggregate tasks for local gates:

```powershell
.\gradlew.bat --console=plain :app:verifyEmbeddedNodeDebug
.\gradlew.bat --console=plain :app:verifyEmbeddedNodePackagedDebug
.\gradlew.bat --console=plain :app:verifyEmbeddedNodeReleaseCandidate
```

`verifyEmbeddedNodeDebug` covers build, native dependency reporting, native symbol archive reporting, and packaging preflight.
It also runs `verifyNodeCompatibilityProfile` and `embeddedNodeDoctorReport`, which generate compatibility and attachable
doctor reports.
`verifyEmbeddedNodePackagedDebug` covers packaged host verification, including the diagnostic report and smoke host verifier.
Focused instrumentation classes still use the explicit `connectedAppDebugAndroidTest` commands above when a device gate is required.
