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

Host-side project wizard and validator gate:

```powershell
.\gradlew.bat --console=plain :app:verifyNodeProjectWizard
```

This gate runs `tools/nodejs/project/autojs6-node-project.js` against all
checked templates (`commonjs-app`, `esm-app`, `automation-script`,
`screenshot-ocr-script`, `network-fetch-script`) and invalid fixtures for
missing entries, invalid package names, unknown permissions, and unsafe
dependency bundles.

## Device Instrumentation

Device-specific gates should use `autojs.nodejs.test.deviceProfile`; see [DEVICE_MATRIX.md](DEVICE_MATRIX.md).

```powershell
.\gradlew.bat --console=plain :app:printNodeTestDeviceProfile "-Pautojs.nodejs.test.deviceProfile=sony"
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pautojs.nodejs.test.deviceProfile=sony"
```

When `android.testInstrumentationRunnerArguments.class` is passed explicitly, it overrides the device profile selection.

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
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeEmbeddedLifecycleStressInstrumentationTest#queuedExecutionTimeoutReturnsStableError"
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeEmbeddedLifecycleStressInstrumentationTest#queueLimitReachedReturnsStableError"
```

These tests verify that default concurrent starts still reject, explicit enqueue runs after the active script, queued executions can be cancelled by execution id, queue wait timeout returns `ERR_AUTOJS6_NODE_QUEUE_TIMEOUT` before user code runs, and the one-entry queue limit returns `ERR_AUTOJS6_NODE_QUEUE_FULL`.

Long-running service mode has a controlled interactive foreground MVP:

```text
docs/nodejs/design/LONG_RUNNING_SERVICE.md
```

`NodeInteractiveLongRunningInstrumentationTest` starts
`NativeNodeEmbeddedScriptService.startInteractiveLongRunning(...)`, waits for
the app-private status snapshot to report `running`, sends the app UI to the
background, verifies the script remains alive, then uses the stop path and
checks recovery after the isolated Node process exits. The status snapshot must
include `executionMode=interactive_long_running`,
`launchSurface=interactive_session`, runtime duration, active resource summary,
restart policy `never`, `automaticRestart=false`, and the Stop action name.
`NodeAndroidRuntimePolicyInstrumentationTest` verifies the embedded Node service
manifest/type/permission diagnostics, background foreground-service start
denial classification, missing or invalid foreground-service type diagnostics,
Android 16 WorkManager quota metadata, and a simulated Android 15
`Service.onTimeout(...)` path that marks the interactive runtime `timed_out`,
calls the cleanup path, and verifies a later one-shot script can recover.
`NodeRuntimeBudgetPolicyInstrumentationTest` verifies safe runtime budget
override parsing, ignored above-hard-max overrides, stale heartbeat
classification for pending bridge calls, provider quota rejection without
leaked resources, and interactive long-running status snapshots carrying
runtime heartbeat, runtime budget, and active resource summaries through stop.
The interactive long-running suite also verifies the S7-22 lifecycle protocol:
`onStop()` can write a JSON checkpoint during the stop grace window, terminal
status exposes only checkpoint metadata, a settled later service process can
explicitly `onRestore()` that checkpoint, and `automaticRestart` remains false.
`NodeLongRunningStressInstrumentationTest` extends the same gate with repeated
interactive stop/checkpoint/recovery cycles and provider-destroy cleanup for
owned notification, sensor, UI, shell, and WebSocket counters. Normal builds
still keep WebSocket live I/O feature-gated or denied, and packaged service
mode remains unavailable.

Lifecycle tests must still verify that normal scripts are one-shot executions
controlled by `timeoutMs`, including timeout recovery, uncleared interval
timeout behavior, and the explicit execution-mode policy gate that rejects
reserved or unsupported launch surfaces before user code runs.
`NodeExecutionModePolicyInstrumentationTest` freezes the Phase 7 state machine
for `one_shot`, `interactive_long_running`, `scheduled`, and
`worker_computation`; `NodeEmbeddedLifecycleStressInstrumentationTest#explicitLongRunningModeIsRejectedBeforeUserScriptRuns`
verifies that direct script `interactiveLongRunning`/legacy `long_running`
cannot be enabled by `timeoutMs=0` or pending timers.

Current packaged APK smoke instrumentation:

```powershell
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.apkbuilder.EmbeddedNodePackagedApkSmokeTest"
```

This suite covers packaged CommonJS/npm/fs/native-lib behavior, the packaged `toast`/`app`/`clipboard`/`device` bridge smoke, and the v1.1 fake-provider packaged bridge smoke for controlled `fetch`, `ocr`, `storage`/`storages`, `ui`, and `work_manager`, including declared `node.permissions`, bridge-module shadowing protection, and pending-call cleanup.

Focused packaged `toast` bridge smoke:

```powershell
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.apkbuilder.EmbeddedNodePackagedApkSmokeTest#installAndRunToastBridgeEmbeddedNodePackagedApk"
```

Checked-in example project smoke:

```powershell
.\gradlew.bat --console=plain :app:verifyNodeExamples
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeExampleProjectInstrumentationTest"
```

`verifyNodeExamples` validates `sample/nodejs/examples.json`, required files, metadata, Node directives, and PASS markers,
then writes `app/build/reports/nodejs/examples.json` and `.md`.

The instrumentation smoke runs `hello-node`, `cjs-package`, `fs-scoped`, `fs-promises`, `npm-pure-js-cjs`, `toast-app`,
`accessibility-click-text`, `screenshot-find-image`, and `disabled-features-demo` from the `androidTest` asset source set.
`packaged-node-apk` remains marked packaged-compatible and is covered by packaged APK smoke gates.

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

Focused packaged v1.1 bridge smoke:

```powershell
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.apkbuilder.EmbeddedNodePackagedApkSmokeTest#installAndRunV11BridgeEmbeddedNodePackagedApk" "-Pautojs.nodejs.test.deviceProfile=sony"
```

This smoke proves the v1.1 bridge modules are not main-app-only by packaging fake-provider coverage for controlled `fetch`, OCR opaque image handles, JSON storage, UI handles/events, and `work_manager` metadata serialization without granting live Android provider authority.

Current bridge protocol instrumentation:

```powershell
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeAutoJsBridgeInstrumentationTest"
```

This suite covers the Kotlin bridge protocol, strict malformed request shape checks, bridge module registry, unavailable module/method errors, malformed live request responses, duplicate provider callback suppression, provider dispatch exception conversion, JavaScript fake transport, timeout/destroy handling, the current private live file transport, and the `toast`/`app`/`engines`/`dialogs`/`accessibility`/`media_projection`/`image` bridge smoke. The `app` smoke verifies that JavaScript can observe a real provider result, including a missing application lookup returning `false`. The `engines` smoke verifies current engine snapshots, bridge-module shadowing protection, live `all`/`stopAll`/`stopSelf` responses, and the controlled `execScript`/`execScriptFile` method shape. The `dialogs` smoke verifies fake provider results and the stable Android rejection path while the UI provider is not connected. The `accessibility` smoke verifies fake provider results, shadowing protection, disabled status reporting, and stable action rejection until an accessibility provider is connected. The `media_projection` and `image` smoke verifies fake opaque handles, `image`/`images` shadowing protection, stable Android rejection for screenshot/transform operations that still lack provider ownership, and the focused OCR live gate covers the scoped `image.readImage()`/`image.recycle()` provider path. The `ocr`, `clipboard`, and `device` bridges have their own focused test classes.

Provider registry and Doctor health instrumentation:

```powershell
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeBridgeProviderRegistryInstrumentationTest,org.autojs.autojs.ui.settings.NodeDoctorReportGeneratorInstrumentationTest"
```

This suite verifies the Phase 7 bridge provider registry for `fetch`/`axios`, `websocket`, OCR, storage, notifications, sensors, accessibility, media projection, image, UI, `work_manager`, and shell. It freezes provider version metadata, required AutoJs capabilities, Android permissions, active resource counts, current `available`/`fake`/`unavailable`/`permission-required`/`degraded` health states, provider detail fields, Doctor JSON/text reporting, and bridge error categories for unavailable, permission-denied, capability-denied, and provider-failed paths while preserving legacy error codes.

Phase 7 S7-16 live-provider gate:

```powershell
$env:ANDROID_SERIAL="QV710AF65F"
.\gradlew.bat --console=plain :app:verifyNodeLiveProviderGate "-Pautojs.nodejs.test.deviceProfile=sony" "-Pautojs.nodejs.network.experimental=true"
```

This gate runs only live-provider or live fail-closed availability tests for `fetch`, `websocket`, OCR, storage, notifications, sensors, accessibility/image, UI, and `work_manager`; fake-provider regression tests are deliberately excluded. It writes `app/build/reports/nodejs/live-provider-summary.json` after connected instrumentation, including each expected test method, provider, interaction requirement, fake-provider policy, skip policy, XML status, skip/failure message, device profile, result XML path, and policy violations. Unexpected skips, missing expected tests, failures, and errors fail the gate. Allowed skips must report a reason; if the Android runner emits an empty `<skipped />`, the summary uses the gate's explicit `skipPolicy` as the recorded reason and marks `skipReasonSource` as `summary-policy`. Fetch and WebSocket are included in this aggregate gate, so it requires `autojs.nodejs.network.experimental=true`; normal release builds still keep live network providers default-off.

Stable bridge API conformance:

```powershell
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeBridgeConformanceInstrumentationTest"
```

This suite freezes the stable AutoJs bridge API shape for `toast`, `app`, `dialogs`, `clipboard`, `device`, `shell`, `engines`, `accessibility`, `media_projection`, `image`/`images`, `ocr`, `storage`/`storages`, `notifications`, `sensors`, `ui`, and `work_manager`. It verifies Kotlin registry drift, JavaScript module exports, Promise contracts, provider error shape, capability denial, handle method shape, and pending-call cleanup after bridge destroy.

Focused bridge RPC fuzzer:

```powershell
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeBridgeRpcFuzzerInstrumentationTest"
```

This suite fuzzes bridge request parsing, live file transport dispatch, Android provider unavailable/error paths, JavaScript `autojs6:bridge` malformed response handling, and pending-call cleanup. It covers missing or wrongly typed `id`/`module`/`method`, invalid JSON, non-array `args`, malformed `permissions`, invalid `timeoutMs`, huge arguments, too many arguments, binary-like payloads, unknown modules, unknown methods, same-id malformed responses, service destroy rejection, and fake-provider mode.

Fuzzer budgets are controlled by `autojs.nodejs.fuzzerBudget`:

```powershell
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeResolverFuzzerInstrumentationTest" "-Pautojs.nodejs.fuzzerBudget=quick"
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeResolverFuzzerInstrumentationTest" "-Pautojs.nodejs.fuzzerBudget=extended"
```

Supported values are `quick`, `normal`, and `extended`. `full` and nightly/release gates default to `extended`; emulator and smoke profiles default to `quick`; focused runs default to `normal` unless the property is set. Fuzzer failures print an `autojs6.fuzzer.seed=` JSON line, and `collectNodeFuzzerSeeds` copies parsed seed files to:

```text
build/reports/nodejs/fuzz-seeds/
```

Replay a saved seed with:

```powershell
.\gradlew.bat --console=plain :app:replayNodeFuzzerSeed "-Pautojs.nodejs.fuzzerSeedFile=build/reports/nodejs/fuzz-seeds/<seed>.json"
```

Current compatibility corpus instrumentation:

```powershell
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeCompatCorpusInstrumentationTest"
```

This suite copies `app/src/androidTest/assets/node_compat_corpus/` into a scoped working directory and verifies pure JavaScript CommonJS package patterns: basic CJS, deep dependencies, package `exports`, package `imports`, self-reference, JSON module caching, `util.promisify(fs)`, `Buffer`/`path`/`util`, and disabled-feature detection. It also runs a moduleSources/preload-shaped fixture as a packaged-preload proxy and verifies moduleSources budget behavior: within-budget execution passes, over-count and over-byte requests fail with `ERR_AUTOJS6_MODULE_SOURCE_BUDGET_EXCEEDED`.

Current fixed real npm corpus instrumentation:

```powershell
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeRealNpmCompatInstrumentationTest"
```

This suite copies `app/src/androidTest/assets/npm_compat_real/` into a scoped working directory and runs fixed, offline,
license-noted CommonJS package snapshots. It covers small utilities, nested dependencies, semver parsing,
querystring/URL utilities, glob/path matching, crypto hashing, stream utilities, zlib buffer utilities, and core builtin
feature detection for `constants`, `util/types`, `events`, and `diagnostics_channel`. The v1.1 corpus also covers an
ESM-only dependency, a hybrid CJS/ESM package, Buffer helpers, fetch-wrapper fallback behavior, and assertion/test helper
patterns. It also checks that the corpus contains no native addons, no install/postinstall scripts, and no ESM root
runner; ESM-only package metadata is allowed only below fixture `node_modules`.

Desktop Node differential harness:

```powershell
node tools/nodejs/differential/run-differential-tests.js
```

This host-side script runs the fixed real npm fixtures under desktop Node 24 and writes:

```text
build/reports/nodejs/differential/desktop-node-differential.json
build/reports/nodejs/differential/desktop-node-differential.md
build/reports/nodejs/npm-compat-v1.1.md
build/reports/nodejs/v1.1-differential.md
```

`NodeRealNpmCompatInstrumentationTest` emits `realnpm.differential.result=...` JSON lines for each fixture. The Android
Gradle plugin stores these lines in the connected test logcat output, for example under
`app/build/outputs/androidTest-results/connected/.../logcat-org.autojs.autojs.engine.NodeRealNpmCompatInstrumentationTest-realPureJavaScriptPackageCorpusPasses.txt`.
Pass that file to the harness with `--autojs-log <file>` to compare AutoJs6 against the desktop baseline. Differences are
classified as matched, expected AutoJs6 restriction, unsupported, flaky, or bug. The `v1.1-differential.md` report
also groups the fixed fixtures by the Task 188 capability areas: ESM packages, stream packages, zlib packages, fetch
packages, and buffer packages.

Modern core differential harness:

```powershell
node tools/nodejs/differential/run-modern-core-differential.js
```

This host-side script runs the fixed `node_modern_core_differential` corpus under desktop Node 24 and writes:

```text
build/reports/nodejs/differential/modern-core-differential.json
build/reports/nodejs/differential/modern-core-differential.md
```

`NodeModernCoreDifferentialInstrumentationTest` emits `moderncore.differential.result=...` JSON lines for the same
fixtures. Pass the connected-test logcat file to the harness with `--autojs-log <file>` to compare AutoJs6 with the
desktop baseline. The S7-36 categories are `exact match`, `intentional AutoJs6 restriction`, `partial`, and `bug`.
The corpus covers ESM, `process.permission`, `AsyncLocalStorage`, `perf_hooks`, `diagnostics_channel`, `node:test`,
`timers/promises`, `stream/promises`, WebCrypto, and the separately classified controlled database bridge.

Current dependency bundler verification:

```powershell
.\gradlew.bat --console=plain :app:bundleNodeDependencies
.\gradlew.bat --console=plain :app:verifyNodeDependencyBundlerFixtures
```

`bundleNodeDependencies` validates an existing project root with `package.json` and `node_modules`, then writes a
sanitized bundle plus `dependency-manifest.json`, `dependency-sbom-lite.json`, `dependency-size-report.txt`, and
`dependency-rejections.txt`.
`verifyNodeDependencyBundlerFixtures` confirms the checked-in sample project passes, the sanitized bundle can be used as
the packaged APK `assets/project` layout, and unsafe fixtures are rejected for native addon, node-gyp build artifacts,
lifecycle script, unchecked `.bin` entries, npm cache/postinstall artifacts, suspicious executables, absolute or
escaping package metadata references, and oversized files/packages/trees. Native addon binaries are reported as
`NATIVE_ADDON_DISABLED`; `binding.gyp` and `build/{Release,Debug}` files are reported as
`NODE_GYP_ARTIFACT_UNSUPPORTED`.

Current doctor diagnostics:

```powershell
.\gradlew.bat --console=plain :app:embeddedNodeDoctorReport
```

`embeddedNodeDoctorReport` writes attachable text and JSON reports under
`app/build/reports/nodejs/doctor/`. It aggregates the compatibility profile, backend and feature flags, enabled/disabled
modules, bridge capability manifest status including the controlled network capability, native library hashes from the
symbol archive report, optional packaged APK diagnostics, dependency bundler status, and differential harness status
when those reports are available. Host-only fields such as live
`workingDirectory`, last error, lifecycle state, and resource counts are marked as unavailable until a device/runtime
diagnostic is attached.

The app UI also exposes `Developer options > Node.js > Node.js Doctor`. It exports privacy-redacted text or JSON reports
through the system share sheet. Device reports include backend, expected and observed Node version when the native probe
is available, ABI, feature flags, native library hashes, capability profile, bridge capability manifest status, the
`network -> android.permission.INTERNET` mapping, the current Node runtime compatibility descriptor version vector,
packaged template presence, and last-error availability. They do not include user script contents or environment values.

Focused device report generator instrumentation:

```powershell
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.ui.settings.NodeDoctorReportGeneratorInstrumentationTest"
```

Current security regression instrumentation:

```powershell
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeSecurityRegressionInstrumentationTest"
```

This suite verifies dangerous process APIs, disabled builtins, network operation denial, scoped fs escape denial, native addon extension denial, and package exports/imports escape denial with stable error codes or documented current denial shape. `process.chdir` is treated as scoped and controlled; the security tests verify absolute and escaping chdir targets are rejected.

The Phase 5 security corpus in the same class also covers v1.1 expansion
boundaries: ESM static import escape denial, local dynamic import escape and
network URL denial, `fs.createReadStream()`/`fs.createWriteStream()` scoped
escape denial plus cleanup counters, unsupported `fetch`/`websocket` schemes,
UI handle cleanup without raw View exposure, JS-only plugin `.node` rejection,
worker-local bridge module denial when worker support is available or
worker_threads fail-closed when unavailable, shell usage while `child_process`
remains disabled, and zlib stream error cleanup.

Focused native addon policy instrumentation:

```powershell
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeNativeAddonPolicyInstrumentationTest"
```

This suite verifies that `.node` native addon targets reject with `ERR_AUTOJS6_NATIVE_ADDON_DISABLED` through direct relative `require()`, `module.createRequire()`, package `main`, package `exports`, package `imports`, and package subpath imports. It also verifies the profile reports `native_addon.enabled=false` and exposes the native-addon error code.

The network portion also asserts that normal builds keep `BuildConfig.NODEJS_NETWORK_EXPERIMENTAL_ENABLED=false`; raw network operations must stay denied until `docs/nodejs/design/NETWORK.md` is implemented by a later task.

Current resolver fuzzer extended instrumentation:

```powershell
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeResolverFuzzerInstrumentationTest"
```

This extended security suite creates a deterministic set of generated packages under a scoped `node_modules` tree and fuzzes package `exports` and `imports` resolver inputs: string targets, condition objects, unsupported arrays, `null` blocking, wildcard patterns, unsupported pattern forms, invalid targets, and package-root traversal attempts. It runs in one embedded Node execution, asserts that no escape marker outside the package is loaded, and requires all resolver failures to use stable codes such as `MODULE_NOT_FOUND`, `ERR_PACKAGE_PATH_NOT_EXPORTED`, `ERR_PACKAGE_IMPORT_NOT_DEFINED`, or `ERR_INVALID_PACKAGE_TARGET`. Assertion failures print the base seed, case seed, request, result record, and generated `package.json`.

Current scoped fs fuzzer extended instrumentation:

```powershell
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeScopedFsFuzzerInstrumentationTest"
```

This extended security suite creates safe files, an outside secret marker, inside symlink chains, and outside symlink chains, then fuzzes scoped fs path normalization with `.`, `..`, repeated slashes, encoded-looking names, NUL bytes, Unicode slash-like characters, Windows-style backslashes, long paths, `fs.realpathSync`, sync reads, sync `stat`/`access`, and `fs.promises.readFile`. Accepted paths must resolve to a real path inside `workingDirectory`, rejected policy paths must use stable `ERR_AUTOJS6_FS_*` codes, and the outside marker must never be read. Failure diagnostics include the base seed, path case, expected code, observed code, and summary JSON.

Current `fs.promises` focused instrumentation:

```powershell
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeFsPromisesInstrumentationTest"
```

This suite verifies scoped `fs.promises` and `fs/promises` aliases, FileHandle operations, `fs.createReadStream`, file URL helpers, builtin shadowing protection, promise drain behavior, and negative paths for absolute, escaping, NUL, and missing files.

Current stream focused instrumentation:

```powershell
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeStreamMvpInstrumentationTest"
```

This suite verifies `stream`/`node:stream` and `stream/promises`/`node:stream/promises` identity, shadowing protection, `Readable.from`, async iteration, `PassThrough`, `Transform`, callback and promise `pipeline`, callback and promise `finished`, Web Streams adapters, backpressure smoke, error propagation, and destroy propagation.

Current zlib focused instrumentation:

```powershell
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeZlibBufferInstrumentationTest"
```

This suite verifies `zlib`/`node:zlib` identity, shadowing protection, callback/promisified/buffer/stream roundtrips, scoped fs read->gzip->write pipeline behavior, invalid input rejection, frozen constants, pending callback/stream drain, active zlib stream timeout recovery, and stable denial for `zlib/promises`.

Current crypto focused instrumentation:

```powershell
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeCryptoExpansionInstrumentationTest"
```

This suite verifies `crypto`/`node:crypto` identity, shadowing protection, `randomUUID`, `createHmac`, `timingSafeEqual`, high-risk asymmetric API absence, and the current limited WebCrypto boundary.

Current process focused instrumentation:

```powershell
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeProcessSweepInstrumentationTest"
```

This suite verifies `process.nextTick` ordering, warning listeners, event-emitter helpers, inert `allowedNodeEnvironmentFlags`, writable `process.exitCode`, scoped `chdir` rejection, dangerous process API denial, and the S7-30 read-only `process.permission` facade. The permission facade coverage checks scoped-fs references, manifest-declared and manifest-denied bridge-style scopes, default-denied `network`/`worker` states, frozen query snapshots, `autojs6:profile.processPermission`, and that permission queries leave the bridge pending count at zero.

Current `node:module` safe subset focused instrumentation:

```powershell
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeModuleSafeSubsetInstrumentationTest"
```

This suite verifies the partial `module`/`node:module` surface, shadowing protection, filtered `builtinModules`, scoped `createRequire` loading for local and package dependencies, `child_process` denial through created require functions, and unsafe base or target rejection.

Current core builtin compatibility focused instrumentation:

```powershell
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeCoreBuiltinCompatibilityInstrumentationTest"
```

This suite verifies `constants`, `util/types`, `events`, `diagnostics_channel`, `perf_hooks`, `async_hooks`, and `node:test`
bare/`node:` aliases, shadowing protection, CJS and partial ESM import paths, frozen wrappers, the `events`/global
`EventTarget` boundary, `diagnostics_channel` publish/subscribe behavior, S7-31 `AsyncLocalStorage` propagation across
Promise/timer/fs/bridge/fetch boundaries plus cleanup, S7-32 frozen primitive diagnostics messages for bridge/fetch
runtime telemetry, safe `performance.eventLoopUtilization()` snapshots, S7-33 single-runtime `node:test` TAP reporting,
hooks/skip/todo/timeout/failure handling, late async activity detection, S7-34 `timers/promises`, `stream/promises`,
`readline/promises`, `AbortSignal`, `structuredClone`, and limited global/WebCrypto coverage including random values,
UUIDs, SHA digest, HMAC sign/verify/export, and negative cases, `process.getBuiltinModule()` denial, and unchanged denial
for disabled builtins such as `child_process`, `vm`, and `dgram`.

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

This suite verifies selector v2 builder shape, bridge registry entries for `findOne`/`findAll` and selector actions, fake provider request/response behavior, `require("accessibility")`, shadowing protection, frozen selector descriptors, regex/boolean/depth/bounds JSON descriptors, frozen node snapshots with no raw node leak, invalid descriptor pre-dispatch rejection, timeout/cancellation cleanup, permission propagation, and stable Android provider rejection when an accessibility provider is unavailable in the current process.

Optional real-device `accessibility` smoke:

```powershell
$env:ANDROID_SERIAL="QV710AF65F"
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pautojs.nodejs.test.deviceProfile=accessibility"
```

Enable the current branch's AutoJs6 accessibility service manually before running it. The profile is not part of normal PR, Sony, full, nightly, or release gates; it launches a controlled test UI and validates `isEnabled`, `ensureEnabled`, `findByText`, selector `findOne`, and selector `click` through the Android provider path. Add `-Pandroid.testInstrumentationRunnerArguments.autojs.nodejs.accessibilitySmoke.globalActions=true` only when it is safe for the test to trigger Back/Home.

Focused accessibility/image live gate:

```powershell
$env:ANDROID_SERIAL="QV710AF65F"
.\gradlew.bat --console=plain :app:verifyNodeLiveAccessibilityImageGate "-Pautojs.nodejs.test.deviceProfile=sony"
```

This focused gate runs selector timeout/cancellation, Android accessibility unavailable rejection, scoped `image.readImage()` to OCR to `image.recycle()` live behavior, OCR unknown-handle cleanup, and screenshot/advanced image fail-closed coverage. It also enables the controlled Activity accessibility smoke; that smoke validates real `isEnabled`, `ensureEnabled`, `findByText`, selector `findOne`, selector `click`, stale JSON snapshot behavior, and post-click lookup when the AutoJs6 accessibility service is already enabled, and otherwise records an explicit JUnit skip without changing device accessibility settings.

Optional real-device `screen_capture` smoke:

```powershell
$env:ANDROID_SERIAL="QV710AF65F"
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pautojs.nodejs.test.deviceProfile=screen_capture"
```

Approve the Android screen-capture prompt on the Sony test device when it appears. The profile is not part of normal PR, Sony, full, nightly, or release gates; it launches the target app `MainActivity`, requests MediaProjection permission through the app's real `ScreenCaptureRequester`, captures one frame through `ScreenCapturer`, verifies frame and decoded-bitmap dimensions, recycles the bitmap/frame, and stops the capturer/foreground service. Denying or canceling the prompt fails the optional test with an explicit permission-denied message.

Current `image` bridge expansion focused instrumentation:

```powershell
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeImageBridgeExpansionInstrumentationTest"
```

This suite verifies the `image`/`images` bridge registry, fake provider request/response behavior, `captureScreen`, scoped `readImage`/`saveImage` path rejection and format overloads, `clip`, `resize`, `grayscale`, `threshold`, `findImage`, `matchTemplate`, `findColor`, `findMultiColors`, local `getSize`, idempotent `recycle`, shadowing protection, opaque handle normalization/resource tracking, timeout and permission propagation, and stable Android provider rejection for screenshot, transform, template, color, and save paths that still lack provider ownership. The focused OCR live gate covers the limited scoped `readImage()`/`recycle()` provider path used by OCR.

Current `ocr` bridge v1.1 partial focused instrumentation:

```powershell
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeOcrBridgeInstrumentationTest"
```

Focused live OCR provider gate:

```powershell
$env:ANDROID_SERIAL="QV710AF65F"
.\gradlew.bat --console=plain :app:verifyNodeLiveOcrGate "-Pautojs.nodejs.test.deviceProfile=sony"
```

This suite verifies the `ocr` bridge registry, fake-provider request/response behavior, `require("ocr")`, shadowing protection, `recognize(image, options?)`, `recognizeText(image, options?)`, `detectTextBounds(image, options?)`, opaque image-handle validation before dispatch, frozen OCR result snapshots, timeout and `ocr`/`image` permission propagation, manifest capability denial before dispatch, and the live Android MLKit OCR provider over provider-owned image handles. The live gate creates a scoped image handle through `image.readImage()`, recognizes text with optional region handling, recycles the handle through `image.recycle()`, rejects recycled or unknown handles with `ERR_AUTOJS6_BRIDGE_INVALID_REQUEST`, and asserts zero active OCR/image bridge resources after cleanup. Screenshot capture, image transforms, template matching, and packaged OCR model ownership remain outside this live OCR gate.

Current `storage` bridge v1.1 partial focused instrumentation:

```powershell
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeStorageBridgeInstrumentationTest"
```

Focused live storage/notifications provider gate:

```powershell
$env:ANDROID_SERIAL="QV710AF65F"
.\gradlew.bat --console=plain :app:verifyNodeLiveStorageNotificationsGate "-Pautojs.nodejs.test.deviceProfile=sony"
```

This suite verifies the `storage`/`storages` bridge registry, fake-provider request/response behavior, shadowing protection, `create`/`open` frozen store handles, `get`/`put`/`remove`/`clear`/`keys`/`contains`, JSON-only values, manifest capability denial before dispatch, Android provider persistence, working-directory namespace isolation, package-identity isolation for packaged apps, and per-value/per-store quota denial.

Current `database`/`sqlite` bridge v1.1 partial focused instrumentation:

```powershell
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeDatabaseBridgeInstrumentationTest"
```

This suite verifies the `database`/`sqlite` bridge registry, fake-provider request/response behavior, shadowing protection, `open` frozen handles, `exec`/`run`/`get`/`all`/`transaction`/`close`, manifest capability denial before dispatch, `node:sqlite` denial, Android SQLite transaction and persistence behavior, scoped database-name denial for path escapes, `ATTACH`/`load_extension` denial, and owned database-handle resource cleanup through the bridge resource registry.

Current `notifications` bridge v1.1 partial focused instrumentation:

```powershell
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeNotificationsBridgeInstrumentationTest"
```

This suite verifies the `notifications` bridge registry, fake-provider request/response behavior, shadowing protection, `notify`/`cancel`/`cancelAll`, notification option serialization, manifest capability denial before dispatch, Android `POST_NOTIFICATIONS` manifest mapping, Android provider permission-denial or post/cancel smoke, stable channel IDs, ongoing notification ownership metadata, and owned notification resource cleanup on provider destroy.

Current `sensors` bridge v1.1 partial focused instrumentation:

```powershell
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeSensorsBridgeInstrumentationTest"
```

Focused live sensors provider gate:

```powershell
$env:ANDROID_SERIAL="QV710AF65F"
.\gradlew.bat --console=plain :app:verifyNodeLiveSensorsGate "-Pautojs.nodejs.test.deviceProfile=sony"
```

This suite verifies the `sensors` bridge registry, fake-provider request/response behavior, shadowing protection, `getAvailableSensors`, `once`, `subscribe`, subscription `ready`/`close`, event snapshot freezing, type/callback validation, manifest capability denial before dispatch, Android provider sensor listing, live one-shot events, live subscription drain/unsubscribe cleanup, sampling interval clamping, maximum subscription denial, missing-provider unavailable errors, and live subscription resource cleanup when a supported device sensor is available.

Current `ui` bridge live-provider focused instrumentation:

```powershell
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeUiBridgeInstrumentationTest"
.\gradlew.bat --console=plain :app:verifyNodeLiveUiGate "-Pautojs.nodejs.test.deviceProfile=sony"
```

This suite verifies the `ui` bridge registry, capability manifest mapping, fake-provider `showLayout`, `drainEvents`, `update`, and `close`, shadowing protection for `require("ui")`, opaque handle shape, fake-provider and Android-live `handle.on` callback delivery through drained JSON snapshots, timeout and permission propagation, JSON-only invalid descriptor rejection before dispatch, absence of raw View/Activity handle properties, live Android Activity show, Button/Input event delivery into Node, state snapshots, update, close, provider-destroy cleanup, unsupported descriptor denial, foreign-handle denial, and active UI session limit denial.

Current JS-only plugin prototype focused instrumentation:

```powershell
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodePluginSystemInstrumentationTest"
```

This suite verifies reserved `require("plugins")` and `require.resolve("plugins")`, local `node_modules/plugins` shadowing denial, project-local `./plugins/<id>` package loading, `autojs6.plugin.permissions` metadata, exact `plugin.<id>` opt-in, plugin-requested permission denial before code execution, `.node` rejection with `ERR_AUTOJS6_NATIVE_ADDON_DISABLED`, plugin entry path escape denial, plugin-root relative resolution, and TypeScript declaration coverage.

Current Node developer REPL focused instrumentation:

```powershell
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeDeveloperReplInstrumentationTest"
```

This suite verifies the debug-build-only developer REPL entry, simple eval
result capture, console output capture, allowed `path` builtin require,
continued `child_process` denial, deny-by-default bridge capability manifest
behavior, explicit bridge capability opt-in through `toast`, isolated temporary
working directory use, scoped filesystem writes inside that directory, and
cleanup after `close()`.

Current bridge permission manifest focused instrumentation:

```powershell
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeBridgePermissionManifestInstrumentationTest"
```

This suite verifies `project.json node.permissions`, `package.json autojs6.node.permissions`, capability normalization, Android manifest permission mapping including `POST_NOTIFICATIONS`, provider-side module/method capability coverage, declared `toast` dispatch, undeclared provider requests, enforced-manifest provider rejection, root-shell capability rejection before execution, packaged manifest permission mismatch detection, and the JavaScript capability report exposed through `autojs6:bridge` and `autojs6:profile`.

Current `shell` bridge focused instrumentation:

```powershell
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeShellBridgeInstrumentationTest"
```

This suite verifies the `shell` bridge registry, fake provider request/response behavior, `require("shell")`, shadowing protection, `execFile()` scoped path/argument wrapping, `checkAccess()` reports for root and unsupported Shizuku access, `setDefaultTimeout()` fallback behavior, timeout propagation, `cwd` and `env` normalization, explicit root permission tagging, manifest-declared `shell`/`shell.root` capability behavior, real Android non-root command execution, non-zero exit results, timeout result shape, policy denials, and that `child_process` remains disabled.

Current controlled fetch bridge focused instrumentation:

```powershell
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeFetchBridgeInstrumentationTest"
```

This suite verifies the `fetch` bridge registry, fake provider GET/POST behavior, `require("fetch")`, global `fetch`, shadowing protection, object and array headers, string/Buffer/Uint8Array bodies, `Response.text()`/`json()`/`arrayBuffer()`, `response.body.stream()` Web Stream reads, timeout rejection, abort-before-dispatch and abort-during-dispatch rejection, response-size budget rejection, unsupported URL scheme rejection, redirect limit plus `redirect: "manual"`/`"error"` behavior, max concurrent request rejection, service-destroy rejection cleanup, fetch diagnostics counters, raw `http`/`https`/`net` and `node:https` denial, pending bridge cleanup, and that normal Android providers reject with `ERR_AUTOJS6_NETWORK_DISABLED` while `autojs.nodejs.network.experimental=false`. It also verifies the Task 167 `require("axios")` compatibility layer: reserved module resolution, fake-provider `get`/`post`/`request`, `baseURL`, params serialization, headers, timeout, `responseType: json/text/arraybuffer`, Axios-like error shape, custom adapter/agent/proxy denial, and raw `node:https` denial.

Current controlled fetch/axios live-provider subgate:

```powershell
$env:ANDROID_SERIAL="QV710AF65F"
.\gradlew.bat --console=plain :app:verifyNodeLiveFetchGate "-Pautojs.nodejs.test.deviceProfile=sony" "-Pautojs.nodejs.network.experimental=true"
```

This explicit experimental subgate runs only the live Android provider methods and is also included by the S7-16 aggregate live-provider gate. It starts an on-device loopback HTTP server, verifies real OkHttp-backed `fetch` GET/POST, headers, query params, string and `Uint8Array` bodies, `json()`/`arrayBuffer()` responses, redirect follow, response-size rejection, timeout rejection, fetch-backed `axios.get`/`axios.post`, raw `http`/`net` denial, provider diagnostics, and provider destroy cancellation of an active request. Normal release builds still keep `autojs.nodejs.network.experimental=false`.

Current controlled WebSocket bridge focused instrumentation:

```powershell
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeWebSocketBridgeInstrumentationTest"
```

This suite verifies the registry/profile experimental state for `websocket`, fake-provider connect/send/receive/close behavior, connect headers/protocols/maxQueueSize descriptors, text and binary send/receive, `on`/`off`, `addEventListener`/`removeEventListener`, `onopen`/`onmessage`/`onerror`/`onclose` handler properties, close event shape, `require("websocket")`, shadowing protection, timeout rejection, unsupported scheme rejection, max connection rejection, message-size budget rejection, service-destroy rejection cleanup, WebSocket diagnostics counters, raw `net`/`tls` denial, pending bridge cleanup, and that normal Android providers reject with `ERR_AUTOJS6_NETWORK_DISABLED` while `autojs.nodejs.network.experimental=false`.

Current controlled WebSocket live-provider subgate:

```powershell
$env:ANDROID_SERIAL="QV710AF65F"
.\gradlew.bat --console=plain :app:verifyNodeLiveWebSocketGate "-Pautojs.nodejs.test.deviceProfile=sony" "-Pautojs.nodejs.network.experimental=true"
```

This explicit experimental subgate starts an on-device loopback WebSocket server and is also included by the S7-16 aggregate live-provider gate. It verifies real OkHttp-backed `websocket.connect()`, headers, protocol negotiation, text and binary roundtrip, close events, inbound message-size rejection, connect timeout rejection, raw `net`/`tls` denial, live diagnostics, and provider destroy cleanup of an active socket. Normal release builds still keep `autojs.nodejs.network.experimental=false`.

Current raw network policy focused instrumentation:

```powershell
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeRawNetworkPolicyInstrumentationTest"
```

This suite verifies that controlled `fetch` and `websocket` modules cannot be shadowed, raw `http`, `https`, `net`, `tls`, `dns`, `dns/promises`, and `dgram` stay denied, `node:` aliases stay denied or limited in the same way, and local `node_modules` fixtures cannot bypass the Safe Node Profile network policy.

Task 123 GA evaluation report: [reports/NETWORK_EVALUATION.md](reports/NETWORK_EVALUATION.md).

Task 145 v1.1 promotion evaluation: [releases/V1_1_NETWORK_PROMOTION.md](releases/V1_1_NETWORK_PROMOTION.md).

Current controlled work_manager bridge focused instrumentation:

```powershell
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeWorkManagerBridgeInstrumentationTest"
```

Focused live WorkManager gate:

```powershell
.\gradlew.bat --console=plain :app:verifyNodeLiveWorkManagerGate "-Pautojs.nodejs.test.deviceProfile=sony"
```

This suite verifies the `work_manager` bridge registry, fake-provider schedule-once and schedule-periodic descriptors, `require("work_manager")`, shadowing protection, ordinary `scriptPath` versus project `entry` serialization, packaged project-entry metadata serialization, scoped path and missing-file rejection, interval/backend policy rejection, explicit capability tagging, AndroidX WorkManager-backed task persistence, Android 16 JobScheduler quota diagnostics in task records, real scheduled-runner embedded Node execution with `executionMode=scheduled` and `launchSurface=scheduled_runner`, CommonJS and ESM scheduled entries, persisted `lastRun`/`runHistory` result and failure metadata, scheduled task environment variables, delayed-task cancellation before execution, `cancel()`, `list()`, and that `require("tasks")` remains unavailable. Packaged queued-fallback rejection remains a release/template gate because the normal instrumentation harness injects live bridge metadata.

Current Java interop allowlist focused instrumentation:

```powershell
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeJavaInteropBridgeInstrumentationTest"
```

This suite verifies `BuildConfig.NODEJS_JAVA_INTEROP_EXPERIMENTAL_ENABLED=false`, disabled default `require("java")`, shadowing protection against `node_modules/java`, request-local experimental enablement, fake transport serialization, `java.lang.Math`, `java.util.UUID`, opaque `android.graphics.Rect` handles, provider destroy cleanup, and denial of Runtime, ProcessBuilder, System native-library loading, ClassLoader, and reflection classes.

Task 124 GA evaluation report: [reports/JAVA_INTEROP_EVALUATION.md](reports/JAVA_INTEROP_EVALUATION.md).

Current bridge resource registry focused instrumentation:

```powershell
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeBridgeResourceRegistryInstrumentationTest"
```

This suite verifies direct registry cleanup for image-handle, screen-capturer, and WebSocket resource types; idempotent close behavior; leaked-resource diagnostics; Android provider cleanup for experimental Java object handles; `work_manager` registration cancellation and destroy cleanup; and shell timeout cleanup through the live provider resource registry.

Current Phase 5 bridge resource leak stress instrumentation:

```powershell
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeBridgeResourceLeakStressInstrumentationTest" "-Pandroid.testInstrumentationRunnerArguments.autojs.nodejs.bridgeStress.iterations=8"
```

This suite loops the v1.1 bridge module surface through JS fake transport and Android provider paths. It covers repeated
OCR calls, storage CRUD, notifications post/cancel or permission-denied cleanup, sensors subscribe/unsubscribe when a
supported sensor is available, accessibility selector queries, image operations and recycle calls, UI show/close,
shell timeout cleanup, and WebSocket open/close or default-disabled provider denial. It asserts JS pending calls drain to
zero and Android diagnostics report no active bridge resources, shell commands, WebSocket connections, leaked resources,
or resource close errors. Use `autojs.nodejs.bridgeStress.iterations` to scale the loop for local triage or nightly gates.

Current bridge concurrency and resource-limit focused instrumentation:

```powershell
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeBridgeConcurrencyLimitsInstrumentationTest"
```

This suite verifies bridge limit parsing from project/package metadata, safe override acceptance, above-hard-max rejection with warnings, JavaScript pending-call flood rejection, JavaScript image-handle quota and recycle/reuse behavior, Android provider accessibility request-rate limiting, shell command resource limiting, and the `require("autojs6:profile").bridgeLimits` report.

Current runtime heartbeat and resource-budget focused instrumentation:

```powershell
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeRuntimeBudgetPolicyInstrumentationTest" "-Pautojs.nodejs.test.deviceProfile=sony"
```

This suite verifies that runtime budget metadata cannot raise bridge-controlled
resource limits beyond the effective hard caps, heartbeat diagnostics classify
stale pending bridge work with actionable status fields, provider resource
quota denial rejects only the offending resource without leaking registry
entries, and the explicit interactive long-running runtime preserves
heartbeat/budget diagnostics in app-private running and stopped status records.

Current `worker_threads` bootstrap/design gate focused instrumentation:

```powershell
.\gradlew.bat --console=plain :app:verifyNodeWorkerGate "-Pautojs.nodejs.test.deviceProfile=sony"
```

This suite runs `NodeWorkerThreadsBootstrapInstrumentationTest` and
`NodeWorkerThreadsDesignGateInstrumentationTest` in normal builds. It verifies
request-local default denial, JavaScript profile flags, bare and `node:`
`worker_threads` denial, `require.resolve("worker_threads")` denial before
`node_modules/worker_threads` shadowing, frozen profile flags, and the
request-local experimental preflight. The preflight must report either a usable
native Worker bootstrap surface (`Worker`, `isMainThread`, `parentPort`,
`workerData`, `threadId`, `MessageChannel`, `MessagePort`, bounded messaging
policy, diagnostics) or stable
`ERR_AUTOJS6_WORKER_DISABLED` diagnostics with zero active workers.

Task 122 GA evaluation report: [reports/WORKER_EXPERIMENTAL_EVALUATION.md](reports/WORKER_EXPERIMENTAL_EVALUATION.md).

Task 146 v1.1 worker policy: [releases/V1_1_WORKER_POLICY.md](releases/V1_1_WORKER_POLICY.md).

Current experimental worker_threads focused instrumentation:

```powershell
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeWorkerThreadsExperimentalInstrumentationTest"
```

This suite verifies the default disabled path when the build-level flag is off,
then enables the request-local experimental flag to cover local CommonJS worker
scripts, local CJS/JSON dependencies, `workerData`,
`parentPort.postMessage`, `terminate()`, max-worker rejection, scoped path
escape rejection, shadowing protection, Worker diagnostics counters, and denial
of AutoJs bridge modules, `fs`, raw `net`, and nested workers inside worker
scope. S7-25 extends the same native-availability promotion gate with
`worker.cjs`, `worker.mjs`, `package.json` `type=module` worker entries, local
partial-ESM dependencies, allowed worker builtins, native addon denial, raw
network denial, bridge denial, and worker entry path escape denial. On the
current Sony S7-25 recheck, runnable worker creation is skipped with an explicit
preflight reason because native `worker_threads` is unavailable.
S7-26 extends the native-available promotion gate with limited
`MessageChannel`/`MessagePort`, `Worker.postMessage(value, transferList)`,
`MessagePort.postMessage(value, transferList)`, ArrayBuffer transfer detachment,
typed array delivery, structured-clone-safe value checks, maximum message size,
maximum queued messages, DataCloneError-compatible failures, and result-envelope
MessagePort cleanup counters. The current Sony build still skips this runnable
coverage through the same native-availability preflight.
S7-27 adds the worker capability-isolation gate: worker-local
`require("fs")` and `require("fs/promises")` expose only scoped read/write
helpers rooted at the worker working directory, while `toast`, `accessibility`,
`image`, `shell`, `java`, `inspector`, raw network, AutoJs bridge modules, and
nested workers remain denied. The same native-availability preflight skips this
coverage on the current Sony build.
S7-28 adds the controlled `WorkerPool` promotion gate: repeated CPU tasks must
reuse the bounded worker, queue overflow must reject, queued tasks can be
cancelled, running tasks can time out, resourceLimits are capped, and
`pool.close()` must leave zero active workers and zero active pools. This
coverage is also skipped on the current Sony build until native workers are
available.
S7-29 expands the same focused gate with worker stress and packaged smoke
coverage: 100 sequential worker jobs, WorkerPool failure recovery, timeout
recovery while a previous worker termination is still pending, ArrayBuffer
transfer through `WorkerPool.run`, parent-completion pool cleanup, and one
packaged APK `.mjs` worker entry smoke. The packaged worker smoke is selected
as a single `EmbeddedNodePackagedApkSmokeTest` method inside
`verifyNodeWorkerGate`; it skips before APK construction when the S7-24 native
worker preflight reports unavailable.

Current experimental worker_threads resource stress instrumentation:

```powershell
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeWorkerThreadsResourceStressInstrumentationTest"
```

This suite verifies 100 sequential worker create/terminate cycles, WorkerPool
stress across 100 jobs plus failure/cancel/timeout/transfer recovery,
parent-completion cleanup for pending worker messages and active pools, worker
cleanup diagnostics counters, and coarse RSS/thread-count snapshots around the
sequential and pool stress cases. It is skipped when the S7-24
native-availability preflight reports
`ERR_AUTOJS6_WORKER_DISABLED`, and it must pass on the Sony gate device before
workers can be advertised as a runnable experiment.

Current feature flag registry focused instrumentation:

```powershell
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeFeatureFlagRegistryInstrumentationTest"
```

This suite verifies the centralized `NodeRuntimeCapabilities.featureFlags` registry, safe defaults for high and critical security flags, build-only/debug-only/release-forbidden classification, denied project/package override policy, debug build-time override allowance, release build override rejection, unknown-flag rejection warnings, Node project/package metadata rejection, and JavaScript `require("autojs6:profile").featureFlags` diagnostics.

Current ESM partial focused instrumentation:

```powershell
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeEsmExperimentalInstrumentationTest"
```

This suite verifies that `.mjs` can still be rejected by an explicit ESM-off diagnostic request, then covers default partial `.mjs` entry execution, local static `.mjs` imports, top-level await, package `type=module` entry routing, scoped import escape denial, ESM default import from CommonJS, ESM import of supported builtins, disabled builtin rejection from ESM, CJS `require()` of ESM rejection with `ERR_AUTOJS6_REQUIRE_ESM_UNSUPPORTED`, package `exports`/`imports` condition selection with resolver trace for both ESM and CommonJS importers, `.mjs` package targets, nested package imports, self-reference, null blocking, unsafe target denial, explicit dynamic import off rejection, local dynamic import of `.mjs` and CommonJS modules, allowed builtin dynamic import, disabled builtin dynamic import denial, package dynamic import with ESM conditions, dynamic import scoped escape denial, and URL/absolute dynamic import rejection.

Packaged `.mjs`, `package.json` `type=module`, and dynamic import local smokes
run through the focused packaged ESM gate with the default production inrt
template:

```powershell
$env:ANDROID_SERIAL="QV710AF65F"
.\gradlew.bat --console=plain :app:verifyNodePackagedEsmGate "-Pautojs.nodejs.test.deviceProfile=sony" "-Pautojs.nodejs.identity=production" "-Pautojs.embeddedNode.expectedAbis=arm64-v8a"
```

With ESM partial enabled by default, the packaged ESM diagnostics smoke asserts
the CommonJS-to-ESM boundary with `ERR_AUTOJS6_REQUIRE_ESM_UNSUPPORTED`. The
focused packaged ESM gate covers a `.mjs` entry, package `type=module` `.js`
modules, local static imports, selected builtin imports, ESM-to-CJS interop,
top-level await, source-name stack shape, scoped escape denial, disabled builtin
denial, local dynamic import of `.mjs`, CommonJS, and type-module targets, and
runtime compatibility descriptor version-vector mismatch before script
execution. The gate also validates descriptor versions inside packaged APK
fixtures and prints packaged fixture cache hit/miss lines; cache hits still
export, install, launch, and assert logcat markers on device. Broader dynamic
import package graphs remain design-gated follow-ups.

Current GA promotion evaluation: `docs/nodejs/reports/ESM_EXPERIMENTAL_EVALUATION.md`.
Current v1.1 promotion evaluation: `docs/nodejs/releases/V1_1_ESM_PROMOTION.md`.

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

Sony XQ-AT72 test preparation for the current temporary Node package identity:

```powershell
$env:ANDROID_SERIAL="QV710AF65F"
adb -s QV710AF65F shell cmd deviceidle whitelist +org.autojs.autojs6.nodejs
adb -s QV710AF65F shell cmd deviceidle whitelist +org.autojs.autojs6.nodejs.test
adb -s QV710AF65F shell am start -n org.autojs.autojs6.nodejs/org.autojs.autojs.launcher.AdaptiveIconAlias
```

The current branch uses `org.autojs.autojs6.nodejs` so it can coexist with other AutoJs6 branches on the Sony device. Keep the serial explicit when more than one Android device is attached.

Direct reduced stress probe for Sony GA triage:

```powershell
adb -s QV710AF65F shell am instrument -w -r `
  -e class org.autojs.autojs.engine.NodeEmbeddedMemoryLeakStressInstrumentationTest `
  -e autojs.nodejs.leak.iterations 5 `
  -e autojs.nodejs.leak.imageIterations 3 `
  -e autojs.nodejs.leak.faultIterations 2 `
  org.autojs.autojs6.nodejs.test/androidx.test.runner.AndroidJUnitRunner
```

## Packaged APK Verification

Current host-side packaged checks:

```powershell
.\gradlew.bat --console=plain :app:embeddedNodePackagingPreflightAppDebug
.\gradlew.bat --console=plain :app:embeddedNodePackagedAppIntegrationDryRunAppDebug
.\gradlew.bat --console=plain :app:embeddedNodePackagedSmokeHostVerify
```

Current compatibility profile generation and registry/docs verification:

```powershell
.\gradlew.bat --console=plain :app:generateNodeCompatibilityProfile
.\gradlew.bat --console=plain :app:verifyNodeCompatibilityProfile
.\gradlew.bat --console=plain :app:verifyNodeErrorCatalog
```

This writes `build/reports/nodejs/compatibility-profile.json` and `build/reports/nodejs/compatibility-profile.md`, compares the Kotlin capability registry with the JavaScript bootstrap builtin allowlist/denylist, and fails when `docs/nodejs/COMPATIBILITY_PROFILE.md` omits registry entries.

`verifyNodeErrorCatalog` scans `NodeRuntimeErrorCodes.kt` and the Embedded Node runtime bootstrap sources for
`ERR_AUTOJS6_*` codes, verifies every runtime code has a matching `##` entry in
`docs/nodejs/ERROR_CATALOG.md`, verifies runtime codes are present in the Kotlin registry, and writes
`build/reports/nodejs/error-catalog.json`.

`embeddedNodePackagedApkOutputVerify` and `embeddedNodePackagedDiagnosticReport` inspect a concrete exported APK. Pass it with
`-Pautojs.embeddedNode.packagedApk=<apk>` and optionally constrain ABI expectations with
`-Pautojs.embeddedNode.expectedAbis=arm64-v8a,armeabi-v7a,x86_64`.
`verifyEmbeddedNodePackagedDebug` runs these standalone verifier tasks only when an explicit packaged APK property is present.
Without that property, the aggregate gate relies on `embeddedNodePackagedSmokeHostVerify`, which verifies the success and
diagnostics APKs pulled from the device and writes separate verifier reports for both artifacts.

For packaged Node outputs that request full smoke moduleSources, the verifier requires
`assets/project/preload-budget.expected.txt` and fails before runtime when any budget is exceeded or missing:

- module count must be at most `64`
- total moduleSources bytes must be at most `524288`
- every module source must be at most `262144` UTF-8 bytes
- the report must include largest moduleSources for triage

The entry script budget is tracked separately from injected moduleSources. Runtime rejects a single entry script over
`524288` UTF-8 bytes, while moduleSources keep the stricter per-module `262144` byte limit.

If `autojs.embeddedNode.expectedAbis` is omitted, the verifier derives the ABI policy from `autojs.nodejs.releaseFlavor`:

- `nodeFull`: `arm64-v8a,armeabi-v7a,x86_64`
- `nodeMini`: `autojs.nodejs.releaseAbis`, defaulting to `arm64-v8a`
- `rhinoOnly`: no Embedded Node runtime native libraries

See `docs/nodejs/design/RELEASE_FLAVORS.md`.

The packaged diagnostic report also prints whether the inspected manifest declares `android.permission.INTERNET`. Controlled fetch and controlled WebSocket remain disabled in normal builds unless a packaged project opts into network explicitly; raw Node network modules must stay denied in either state.

Template refresh and smoke artifact collection:

```powershell
.\gradlew.bat --console=plain :app:embeddedNodeRefreshInrtTemplateApk
.\gradlew.bat --console=plain :app:verifyNodeTemplateReproducibility
.\gradlew.bat --console=plain :app:embeddedNodePullPackagedSmokeApks
```

`verifyNodeTemplateReproducibility` writes `app/build/reports/embedded-node/template-reproducibility.txt` and fails before
packaged smoke when the checked-in template is stale, missing the embedded script service, missing the expected arm64
native runtime seed, carrying x86 Node runtime entries, or unable to produce a deterministic normalized manifest hash.

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

Rollback flavor verification:

```powershell
.\gradlew.bat --console=plain :app:verifyRhinoOnlyBuild "-Pautojs.nodejs.releaseFlavor=rhinoOnly"
```

ABI size and distribution report:

```powershell
.\gradlew.bat --console=plain :app:nativeNodeAbiSizeReport
```

This writes `app/build/reports/nodejs/abi-size-report.md` with APK artifact sizes, packaged template size, per-ABI native payload, APK native entry compression, and `nodeFull` / `nodeMini` / `rhinoOnly` payload comparison.

See `docs/nodejs/ROLLBACK_PLAYBOOK.md` for when to use this path and what release notes must record.

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
- `autojs.nodejs.esm.experimental=true` keeps `.mjs` enabled by default; explicit ESM-off requests still reject
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
node tools/nodejs/resolver/explain-node-resolve.js --mode compact --request trace-pkg/private --from app/src/main.js --trace-file path\to\resolve-stderr.log
node tools/nodejs/resolver/explain-node-resolve.js --mode json --request trace-pkg/private --from app/src/main.js --trace-file path\to\resolve-stderr.log
.\gradlew.bat --console=plain :app:explainNodeResolve "-Pautojs.nodejs.resolve.request=trace-pkg/private" "-Pautojs.nodejs.resolve.from=app/src/main.js" "-Pautojs.nodejs.resolve.traceFile=path\to\resolve-stderr.log"
.\gradlew.bat --console=plain :app:explainNodeResolve "-Pautojs.nodejs.resolve.mode=compact" "-Pautojs.nodejs.resolve.request=trace-pkg/private" "-Pautojs.nodejs.resolve.from=app/src/main.js" "-Pautojs.nodejs.resolve.traceFile=path\to\resolve-stderr.log"
.\gradlew.bat --console=plain :app:verifyNodeResolverVisualizerFixtures
```

The resolver visualizer does not run a second resolver. It formats the real Embedded Node `[resolve]` trace captured with
`AUTOJS6_NODE_RESOLVE_TRACE=1` into `app/build/reports/nodejs/resolver/resolve-explanation.txt` and `.json`. The report
lists the original request, parent module, `node_modules` search paths, package.json files read, selected
`exports`/`imports`/`main` branch, `target=null` blocking, the final error, a compact diagnosis, and suggested next
steps. `compact` mode is intended for user bug reports, `verbose` mode keeps raw trace lines, and `json` mode is intended
for scripts and issue attachments.

`verifyNodeResolverVisualizerFixtures` covers package exports failure, package imports alias failure, disabled builtin
failure, and scoped path escape failure. These examples correspond to the common user reports `MODULE_NOT_FOUND`,
`ERR_PACKAGE_PATH_NOT_EXPORTED`, `ERR_PACKAGE_IMPORT_NOT_DEFINED`, `ERR_AUTOJS6_BUILTIN_DISABLED`, and
`ERR_AUTOJS6_FS_PATH_ESCAPE`.

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
- interactive checkpoint diagnostics use `embedded_script.lifecycle_checkpoint.*`
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
- interactive stop writes a project-scoped checkpoint and a later explicit
  restore reads it without automatic restart
- repeated interactive stop drains timers, writes checkpoints, and leaves the
  settled service process able to run a later one-shot script
- provider destroy closes owned notification, sensor, UI, shell, and WebSocket
  counters without active resources or close errors

Planned lifecycle stress cases:

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
.\gradlew.bat --console=plain :app:verifyNodePrGate
.\gradlew.bat --console=plain :app:verifyNodeV11AlphaGate "-Pautojs.nodejs.test.deviceProfile=v1_1_alpha"
.\gradlew.bat --console=plain :app:verifyNodeV11BetaGate "-Pautojs.nodejs.test.deviceProfile=v1_1_beta"
.\gradlew.bat --console=plain :app:verifyNodeV11RcReadiness "-Pautojs.nodejs.test.deviceProfile=v1_1_beta"
.\gradlew.bat --console=plain :app:verifyNodeNightlyGate "-Pautojs.nodejs.test.deviceProfile=sony"
.\gradlew.bat --console=plain :app:verifyNodeReleaseGate "-Pautojs.nodejs.test.deviceProfile=sony"
.\gradlew.bat --console=plain :app:verifyEmbeddedNodeDebug
.\gradlew.bat --console=plain :app:verifyEmbeddedNodePackagedDebug
.\gradlew.bat --console=plain :app:verifyEmbeddedNodeReleaseCandidate
```

`verifyNodePrGate` is the fast host-side PR gate. `verifyNodeV11AlphaGate` is the v1.1 functional milestone gate and
requires `autojs.nodejs.test.deviceProfile=v1_1_alpha`; see [reports/v1.1-alpha/README.md](reports/v1.1-alpha/README.md).
`verifyNodeV11BetaGate` is the v1.1 stabilization gate and requires
`autojs.nodejs.test.deviceProfile=v1_1_beta`; see [reports/v1.1-beta/README.md](reports/v1.1-beta/README.md).
`verifyNodeV11RcReadiness` verifies v1.1 beta evidence is ready for release-candidate planning and also requires
`autojs.nodejs.test.deviceProfile=v1_1_beta`; see [reports/v1.1-rc/README.md](reports/v1.1-rc/README.md).
`verifyNodeNightlyGate` and `verifyNodeReleaseGate` require `autojs.nodejs.test.deviceProfile=sony` or `full`; see
[CI_GATES.md](CI_GATES.md).
`generateNodeNightlyReport` can also be run directly to summarize the currently available build/device reports:

```powershell
.\gradlew.bat --console=plain :app:generateNodeNightlyReport
```

`generateNodeV11AlphaSummary` writes `build/reports/nodejs/v1.1-alpha-summary.json` from the current build and device
reports. Running `verifyNodeV11AlphaGate` finalizes with this summary automatically.
`generateNodeV11BetaSummary` writes `build/reports/nodejs/v1.1-beta-summary.json` from the current build, packaged host,
tooling, and device reports. Running `verifyNodeV11BetaGate` finalizes with this summary automatically.
`verifyNodeV11BetaBlockerRegister` writes `app/build/reports/nodejs/v1.1-beta-blockers.json` and is part of the beta
gate so RC-blocking experimental, protocol-gated, blocked, and explicit-subgate surfaces stay recorded.
`verifyNodeV11RcReadiness` writes `app/build/reports/nodejs/v1.1-rc-readiness.json` and requires a clean beta summary,
ready packaged aggregate, and beta blocker statuses of `deferred` or `closed` before RC planning starts.

`verifyEmbeddedNodeDebug` covers build, native dependency reporting, native symbol archive reporting, and packaging preflight.
It also runs `verifyNodeCompatibilityProfile` and `embeddedNodeDoctorReport`, which generate compatibility and attachable
doctor reports.
`verifyEmbeddedNodePackagedDebug` covers template reproducibility and packaged host verification. With
`-Pautojs.embeddedNode.packagedApk=<apk>` it also runs the standalone output verifier and gated diagnostic report for that
APK; otherwise it uses the packaged smoke host aggregate reports produced from device-exported APKs.
Focused instrumentation classes still use the explicit `connectedAppDebugAndroidTest` commands above when a device gate is required.
