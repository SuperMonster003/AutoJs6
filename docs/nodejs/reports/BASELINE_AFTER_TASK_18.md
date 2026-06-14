# AutoJs6 Embedded Node Baseline After Task 18

Date: 2026-06-14

This report freezes the checked-in Safe Node Profile after Tasks 1-18 and records the Task 19-45 audit, corpus, design-gate, focused-test, bridge-expansion, performance, memory-stress, and release-flavor gates added immediately afterward. It is an engineering baseline for the next task group, not a claim that AutoJs6 exposes unrestricted desktop Node.js.

## Scope

Tasks 1-18 established:

- documentation skeleton and release checklist
- Kotlin capability registry
- stable AutoJs6 error codes and resolver diagnostics
- explicit embedded lifecycle state tracking and stress coverage
- low-risk CommonJS builtin expansion
- AutoJs bridge protocol and MVP bridge modules
- packaged APK verification and release aggregate task names

Runtime behavior remains scoped to AutoJs6 Safe Node Profile.

## Runtime Identity

- Profile: `AutoJs6 Safe Node Profile`
- Capability registry: `NodeRuntimeCapabilities`
- Engine version: `autojs6-node-profile-v1`
- Expected Node version prefix: `v24.5.0`
- Embedded service process: `:nodejs_embedded`
- Node route suffixes: `.node.js`, `.cjs`, `.mjs`
- ESM experimental flag: `autojs.nodejs.esm.experimental=false`
- Network experimental flag: `autojs.nodejs.network.experimental=false`
- Worker threads experimental flag: `autojs.nodejs.worker_threads.experimental=false`
- Release packaging flavor: `autojs.nodejs.releaseFlavor=nodeFull`
- Optional performance benchmark: `NodeEmbeddedPerformanceInstrumentationTest`
- Extended memory leak stress gate: `NodeEmbeddedMemoryLeakStressInstrumentationTest`
- Backend switches preserved: `rhino`, `native`, `embedded`, `auto`
- Native one-shot backend remains present as a regression oracle.

## Entry Points

Supported Node routing inputs:

- `"nodejs";` directive
- `"node";` directive
- `.node.js`
- `.cjs`
- `.mjs` as a Node route only; ESM execution remains disabled by default
- `project.json` with Node project metadata
- encrypted script headers carrying the Node execution flag
- packaged APK Node project metadata and encrypted entry scripts

## Builtin Surface

Direct builtin aliases:

- `path`, `node:path`
- `path/posix`, `node:path/posix`
- `path/win32`, `node:path/win32`
- `util`, `node:util`
- `util/types`, `node:util/types`
- `events`, `node:events`
- `async_hooks`, `node:async_hooks`
- `diagnostics_channel`, `node:diagnostics_channel`
- `perf_hooks`, `node:perf_hooks`

Limited builtin names registered for the profile:

- `assert`
- `assert/strict`
- `async_hooks`
- `buffer`
- `console`
- `constants`
- `crypto`
- `diagnostics_channel`
- `dns`
- `dns/promises`
- `events`
- `fs`
- `fs/promises`
- `http`
- `https`
- `module`
- `net`
- `os`
- `path`
- `path/posix`
- `path/win32`
- `perf_hooks`
- `process`
- `punycode`
- `querystring`
- `readline`
- `readline/promises`
- `stream`
- `stream/consumers`
- `stream/promises`
- `stream/web`
- `string_decoder`
- `timers`
- `timers/promises`
- `tls`
- `tty`
- `url`
- `util`
- `util/types`
- `v8`
- `zlib`

Limited registration is not the same as full desktop Node compatibility. Some modules exist for partial pure-JS compatibility, feature detection, or stable denial behavior.

## Disabled Surface

Disabled builtin module names:

- `child_process`
- `cluster`
- `dgram`
- `domain`
- `inspector`
- `repl`
- `vm`
- `wasi`
- `worker_threads`

Network-related builtin names are recognized but unrestricted network I/O remains disabled by policy:

- `dns`
- `dns/promises`
- `http`
- `https`
- `net`
- `tls`

Disabled process APIs include:

- `abort`
- `binding`
- `_linkedBinding`
- `dlopen`
- `exit`
- `kill`
- `reallyExit`
- `_debugProcess`
- `setuid`
- `setgid`
- `seteuid`
- `setegid`
- `setgroups`
- `initgroups`

Other disabled or gated surfaces:

- ESM execution
- dynamic import
- npm install and postinstall execution
- native `.node` addons
- unrestricted Android or Java object access
- unrestricted filesystem access outside the scoped working directory
- inspector/debugger server

## Filesystem Boundary

The filesystem profile is scoped. It rejects:

- absolute user paths
- NUL byte paths
- `..` escapes
- `/proc`, `/sys`, and `/dev`
- realpath escapes
- symlink escapes
- package exports/imports targets escaping their package root

`fs/promises` is present as a limited scoped surface in the current compatibility profile. It must continue to reuse the scoped path policy.

## AutoJs Bridge Modules

Internal modules:

- `autojs6:profile`
- `autojs6:bridge`

User-facing modules:

- `toast`: `showToast`, `toast`
- `app`: `launchPackage`, `launchApp`, `openAppSetting`, `startActivity`, plus host info fields
- `engines`: `myEngine`, `all`, `stopAll`, `stopSelf`, `execScript`, `execScriptFile`
- `dialogs`: `alert`, `confirm`, `input`, `select`
- `accessibility`: `text`, `desc`, `id`, `className`, `textContains`, `descContains`, `isEnabled`, `ensureEnabled`, `click`, `back`, `home`, `recentApps`, `findByText`, `clickText`, `findOne`, `findAll`
- `media_projection`: `requestScreenCapture`, `nextImage`, `stop`
- `image` and `images`: `captureScreen`, `readImage`, `saveImage`, `clip`, `resize`, `findImage`, `recycle`
- `clipboard`: `getText`, `setText`, `hasText`
- `device`: `sdkInt`, `width`, `height`, `density`, `isScreenOn`, `wakeUp`, `vibrate`
- `shell`: `exec`
- `fetch`: global `fetch` and `require("fetch")` controlled bridge wrapper

Scheduled/background task modules are not exposed yet. Task 38 records the design gate in `docs/nodejs/design/WORK_MANAGER.md`; timed task registration is the only approved MVP direction, and broadcast/startup/boot/package-event registration remains deferred.

Current bridge maturity:

- `toast`, `app`, `engines`, `clipboard`, `device`, and `shell` have bridge smoke coverage.
- `dialogs`, `accessibility`, `media_projection`, and `image` have stable JavaScript API shape, fake-provider coverage, shadowing protection, and stable Android-provider rejection paths.
- `accessibility` selector v1 descriptors are JSON-only and node results are snapshot-only; the real Android provider still rejects selector actions and queries until a safe accessibility provider is connected.
- `image`/`images` expose screenshot/image-operation protocol shape with opaque handles, scoped path validation, and idempotent JavaScript-side recycle; the real Android provider still rejects until a safe image capability provider is connected.
- `clipboard` uses a main-thread Android provider for system clipboard access, has a queued fallback for packaged templates without live bridge metadata, and does not expose raw `ClipboardManager` or clipboard listeners.
- `device` uses a privacy-safe Android provider for screen state, wake, and vibration, has a queued fallback for packaged templates without live bridge metadata, and keeps raw device identifiers absent.
- `shell` uses a controlled non-interactive provider-thread shell implementation, requires explicit root opt-in, scopes `cwd` to the working directory, bounds output, enforces timeout, and does not enable Node `child_process`.
- `fetch` uses a controlled bridge request path for `GET` and `POST`, supports headers, string/binary bodies, timeout, abort, response-size budgets, and response text/arrayBuffer/json helpers. The default Android provider rejects before network I/O while `autojs.nodejs.network.experimental=false`; fake-provider tests cover allowed paths and raw Node network modules remain denied.
- `engines.execScript` and `engines.execScriptFile` use a non-exported main-process dispatch service, require `timeoutMs`, return serialized execution snapshots, reject scoped path escapes, and currently reject nested embedded Node child scripts.
- Real UI, accessibility, MediaProjection, and image providers are still follow-up completeness work.

All bridge APIs must remain asynchronous from JavaScript and reject cleanly on timeout or service destroy.

## Packaged APK Verification

Current packaged host gates:

- `:app:embeddedNodePackagingPreflightAppDebug`
- `:app:embeddedNodePackagedAppIntegrationDryRunAppDebug`
- `:app:embeddedNodePackagedApkOutputVerify`
- `:app:embeddedNodePackagedDiagnosticReport`
- `:app:embeddedNodePackagedSmokeHostVerify`

Current release aggregate task names:

- `:app:verifyEmbeddedNodeDebug`
- `:app:verifyEmbeddedNodePackagedDebug`
- `:app:verifyEmbeddedNodeReleaseCandidate`

The packaged diagnostic verifier checks:

- APK existence and SHA-256 of the inspected APK
- manifest service and foreground service requirements
- `NativeNodeEmbeddedScriptService` presence in DEX
- packaged project metadata and encrypted main script
- full compatibility smoke markers
- module source budget markers
- packaged real npm corpus markers
- CommonJS resolver corpus markers
- dependency resolution markers
- diagnostics markers
- native library entries per ABI
- per-entry native library size and SHA-256
- complete ABI set and x86 fail-fast state
- release flavor native policy for `nodeFull`, `nodeMini`, or `rhinoOnly`
- whether the manifest declares `android.permission.INTERNET`

The diagnostic task requires a concrete APK:

```powershell
.\gradlew.bat --console=plain :app:embeddedNodePackagedDiagnosticReport -Pautojs.embeddedNode.packagedApk=<apk> -Pautojs.embeddedNode.expectedAbis=arm64-v8a,armeabi-v7a,x86_64
```

## Test Gate Size

Primary Node instrumentation classes currently contain:

- `EmbeddedScriptMvpInstrumentationTest`: 239 `@Test` methods
- `NodeEmbeddedLifecycleStressInstrumentationTest`: 7 `@Test` methods
- `NodeAutoJsBridgeInstrumentationTest`: 22 `@Test` methods
- `NodeCompatCorpusInstrumentationTest`: 2 `@Test` methods
- `NodeSecurityRegressionInstrumentationTest`: 2 `@Test` methods
- `NodeFsPromisesInstrumentationTest`: 2 `@Test` methods
- `NodeStreamMvpInstrumentationTest`: 1 `@Test` method
- `NodeZlibBufferInstrumentationTest`: 1 `@Test` method
- `NodeCryptoExpansionInstrumentationTest`: 1 `@Test` method
- `NodeProcessSweepInstrumentationTest`: 1 `@Test` method
- `NodeOsPrivacyInstrumentationTest`: 1 `@Test` method
- `NodeEnginesBridgeInstrumentationTest`: 4 `@Test` methods
- `NodeAccessibilitySelectorBridgeInstrumentationTest`: 4 `@Test` methods
- `NodeImageBridgeExpansionInstrumentationTest`: 4 `@Test` methods
- `NodeClipboardBridgeInstrumentationTest`: 2 `@Test` methods
- `NodeDeviceBridgeInstrumentationTest`: 2 `@Test` methods
- `NodeShellBridgeInstrumentationTest`: 3 `@Test` methods
- `NodeFetchBridgeInstrumentationTest`: 3 `@Test` methods
- `NodeWorkerThreadsDesignGateInstrumentationTest`: 1 `@Test` method
- `NodeEmbeddedPerformanceInstrumentationTest`: 1 `@Test` method
- `NodeEmbeddedMemoryLeakStressInstrumentationTest`: 1 `@Test` method
- `EmbeddedNodePackagedApkSmokeTest`: 27 `@Test` methods

Total primary Node instrumentation test methods: 331.

The release aggregate tasks are Gradle host/build/package gates. Focused device instrumentation classes are still invoked explicitly with `connectedAppDebugAndroidTest` class filters.

## Lifecycle Stress Coverage

Current lifecycle stress class covers:

- simple embedded script execution repeated 100 times
- timeout recovery
- uncaught exception recovery
- unhandled rejection recovery
- uncleared interval timeout followed by successful recovery
- nested fs callback drain before success
- stdout/stderr isolation across runs

Expected lifecycle diagnostics include:

- `embedded_script.execution_id`
- `embedded_script.runtime_state`
- `embedded_script.lifecycle_transitions`
- `node.runtime.state executionId=<id> FROM -> TO` in logcat
- normal completion path ending in `STOPPED`
- timeout path reaching `TIMED_OUT` and service destruction

## Compatibility And Security Corpus Coverage

Task 20 adds `NodeCompatCorpusInstrumentationTest`, which copies `app/src/androidTest/assets/node_compat_corpus/` into a scoped working directory and verifies pure JavaScript CommonJS package patterns:

- basic CommonJS export/import
- deep nested `node_modules`
- package `exports`
- package `imports`
- package self-reference
- JSON module caching
- `util.promisify(fs)` over scoped callback fs
- `Buffer`/`path`/`util`
- disabled-feature detection for profile, process, builtin, worker, native addon, and network capability state
- moduleSources/preload-shaped fixture coverage as a packaged-preload proxy

Task 21 adds `NodeSecurityRegressionInstrumentationTest`, which verifies:

- dangerous process APIs stay denied
- high-risk builtins stay denied
- network operations stay denied
- absolute, `..`, NUL, `/proc`, `/sys`, `/dev`, realpath, and symlink filesystem escapes stay denied
- `fs/promises` reuses scoped path denial
- scoped `process.chdir` rejects absolute and escaping targets
- native `.node` addons stay denied by the current message-level extension rejection
- package exports/imports targets escaping the package root fail with `ERR_INVALID_PACKAGE_TARGET`

## Design Gate Docs Added After Task 21

The original Task 22-28 text assumed several surfaces were still unimplemented. The current runtime already has broader scoped implementations for fs promises, file URL helpers, stream, and zlib, so the design gate docs freeze the current reality instead of rolling behavior back:

- Task 22: `docs/nodejs/design/FS_PROMISES.md`
- Task 24: `docs/nodejs/design/FILE_URL_POLICY.md`
- Task 25: `docs/nodejs/design/STREAM.md`
- Task 27: `docs/nodejs/design/ZLIB.md`
- Task 38: `docs/nodejs/design/WORK_MANAGER.md`
- Task 39: `docs/nodejs/design/ESM.md`
- Task 40: `docs/nodejs/design/NETWORK.md`
- Task 42: `docs/nodejs/design/WORKER_THREADS.md`
- Task 43: `docs/nodejs/PERFORMANCE.md`

Task 22/24/25/27/38 were documentation-only updates. Task 39 also adds an explicit disabled ESM flag and clearer `.mjs` rejection assertions. Task 40 adds an explicit disabled network flag and design gate. Task 42 adds an explicit disabled worker_threads flag and design gate. None of these tasks opens ESM, dynamic import, child process, worker threads, native addons, unrestricted network, unrestricted fs, or persistent background task registration.

## Focused Regression Tests Added After Design Gates

Task 23 adds `NodeFsPromisesInstrumentationTest`, which verifies:

- scoped `fs.promises`, `fs/promises`, and `node:fs/promises` aliases
- FileHandle open/write/read/stat/close behavior
- `fs.createReadStream` over scoped paths
- scoped file URL helper behavior
- builtin shadowing protection for `fs/promises`
- promise drain behavior
- absolute, escaping, NUL, and missing-file negative paths

Task 26 adds `NodeStreamMvpInstrumentationTest`, which verifies:

- `stream` and `node:stream` identity
- builtin shadowing protection for `stream`
- `Readable.from`, `PassThrough`, and `Transform`
- callback `stream.finished`
- `stream/promises.pipeline` and `stream/promises.finished`
- rejected pipeline behavior

Task 28 adds `NodeZlibBufferInstrumentationTest`, which verifies:

- `zlib` and `node:zlib` identity
- builtin shadowing protection for `zlib`
- stable denial for `zlib/promises`
- gzip/deflate/raw/brotli sync, callback, promisified, and stream roundtrips
- invalid input rejection
- frozen constants and drained pending zlib callbacks

## Compatibility Sweep Tests Added After Task 28

Task 29 adds `NodeCryptoExpansionInstrumentationTest`, which verifies:

- `crypto` and `node:crypto` identity
- builtin shadowing protection for `crypto`
- `randomUUID` shape and uniqueness smoke
- `createHmac` with a known SHA-256 fixture
- `timingSafeEqual` true, false, and length-mismatch behavior
- high-risk asymmetric API absence
- current limited WebCrypto boundary

Task 30 adds `NodeProcessSweepInstrumentationTest`, which verifies:

- `process.nextTick` ordering before Promise and `queueMicrotask`
- `process.emitWarning` and warning listeners
- `process.on`, `once`, `off`, and `emit`
- inert `process.allowedNodeEnvironmentFlags`
- writable `process.exitCode` without bypassing the normal success lifecycle
- dangerous process API denial and scoped `chdir` rejection

Task 31 adds `NodeOsPrivacyInstrumentationTest`, which verifies:

- `os` and `node:os` identity
- builtin shadowing protection for `os`
- redacted `networkInterfaces`, `userInfo`, `cpus`, memory, load, and hostname values
- cwd-backed `tmpdir` and `homedir`
- safe constants and disabled `setPriority`

## Bridge Expansion Tests Added After Task 31

Task 32 adds `NodeClipboardBridgeInstrumentationTest`, which verifies:

- `clipboard` module registry entries for `getText`, `setText`, and `hasText`
- fake provider request/response behavior
- `require("clipboard")` and `require.resolve("clipboard")`
- bridge-module shadowing protection
- Promise return values for get/set/has text
- bridge `clipboard` permission tagging and timeout propagation
- no `watch` API in the MVP

Task 32 also extends `EmbeddedNodePackagedApkSmokeTest` with packaged clipboard bridge build and install-run smokes. The packaged smoke verifies:

- encrypted packaged `main.js`
- `node_modules/clipboard` shadowing protection
- `require("clipboard")` and `require.resolve("clipboard")`
- Promise behavior for `setText`, `getText`, and `hasText`
- queued host fallback behavior when packaged templates do not inject live bridge metadata

Task 33 adds `NodeDeviceBridgeInstrumentationTest`, which verifies:

- `device` module registry entries for `isScreenOn`, `wakeUp`, and `vibrate`
- fake provider request/response behavior
- `require("device")` and `require.resolve("device")`
- bridge-module shadowing protection
- synchronous privacy-safe metric property shape
- sensitive field absence for IMEI, Android ID, serial, phone number, accounts, network addresses, fingerprint, brand, and manufacturer
- Promise return values for screen state, wake, and vibration
- bridge `device` permission tagging and timeout propagation
- no `watch` API in the MVP

Task 33 also extends `EmbeddedNodePackagedApkSmokeTest` with packaged device bridge build and install-run smokes. The packaged smoke verifies:

- encrypted packaged `main.js`
- `node_modules/device` shadowing protection
- `require("device")` and `require.resolve("device")`
- sync metric property shape and sensitive field absence
- Promise behavior for `isScreenOn`, `wakeUp`, and `vibrate`
- queued host fallback behavior when packaged templates do not inject live bridge metadata
- `android.permission.VIBRATE` is declared for packaged vibration support

Task 34 adds `NodeShellBridgeInstrumentationTest`, which verifies:

- `shell` module registry entry for `exec`
- fake provider request/response behavior
- `require("shell")` and `require.resolve("shell")`
- bridge-module shadowing protection
- timeout propagation and bridge timeout padding
- scoped relative `cwd` normalization and `env` string-only normalization
- explicit root permission tagging through `shell:root`
- real Android non-root command execution through the provider
- non-zero exit result preservation
- timeout result shape with exit code `124`
- scoped `cwd` policy denial with `ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED`
- `child_process` remains disabled
- no `spawn`, `fork`, `open`, interactive shell, stream, or process object API in the MVP

Task 35 adds `NodeEnginesBridgeInstrumentationTest`, which verifies:

- `engines` module registry entries for `execScript` and `execScriptFile`
- fake provider request/response behavior
- `require("engines")` and `require.resolve("engines")`
- bridge-module shadowing protection
- `execScript` and `execScriptFile` payload shape
- required `timeoutMs` and bridge timeout padding
- scoped `cwd` and path denial with `ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED`
- `engines:exec` permission tagging
- recursive spawn denial through `spawnDepth`
- main-process dispatch of scoped Rhino child scripts
- stable rejection of nested embedded Node child scripts
- `execAutoFile` remains unsupported

Task 36 adds `NodeAccessibilitySelectorBridgeInstrumentationTest`, which verifies:

- selector v1 module registry entries for `findOne` and `findAll`
- fake provider request/response behavior for `findOne`, `findAll`, and selector-based `click`
- `require("accessibility")` and `require.resolve("accessibility")`
- bridge-module shadowing protection
- frozen selector descriptors from `text`, `desc`, `id`, `className`, `textContains`, and `descContains`
- frozen snapshot-only `UiNode` results and `findAll` result arrays
- invalid selector descriptors rejected before bridge dispatch
- bridge `accessibility` permission tagging and timeout propagation
- stable Android-provider rejection for selector queries and actions until a real accessibility provider is connected

Task 37 adds `NodeImageBridgeExpansionInstrumentationTest`, which verifies:

- `image` and `images` registry entries for `captureScreen`, `saveImage`, `clip`, and `resize`
- fake provider request/response behavior for capture, scoped read/save, clip, resize, find, and recycle
- `require("image")`, `require("images")`, and `require.resolve()` bridge-module shadowing protection
- opaque image handle normalization for new and derived handles
- scoped `readImage`/`saveImage` path rejection before bridge dispatch
- invalid handle and invalid size rejection before bridge dispatch
- JavaScript-side idempotent `recycle()` dispatch for repeated calls on the same handle
- stable Android-provider rejection for expanded image operations until a real image provider is connected

## Scheduled Task Design Gate Added After Task 37

Task 38 adds `docs/nodejs/design/WORK_MANAGER.md`, which defines the scheduled/background task design gate:

- current `require("tasks")` remains unavailable to Embedded Node scripts
- direct WorkManager, AlarmManager, JobScheduler, BroadcastReceiver, PendingIntent, and IntentFilter authority remains denied
- future timed tasks are the only MVP registration target
- future broadcast, startup, boot, and package-event tasks are deferred
- Node task descriptors must store backend, entry, project root, encrypted entry/module source ids, timeout, and JSON arguments explicitly instead of overloading `scriptPath`
- packaged APK receivers and permissions must be injected only when a project opts in

## ESM Design Gate Added After Task 38

Task 39 adds `docs/nodejs/design/ESM.md`, `autojs.nodejs.esm.experimental=false`, and `BuildConfig.NODEJS_ESM_EXPERIMENTAL_ENABLED`.

Current default remains:

- `.mjs` routes to Embedded Node but rejects before execution
- `.mjs` rejection messages include `.mjs routes to Node backend, but ESM is not enabled in AutoJs6 Safe Node Profile.`
- static `import`/`export`, dynamic `import()`, and `import.meta` remain disabled
- `package.json` `type=module` remains diagnostic-only for current CommonJS loading
- `require("autojs6:profile").experimentalFlags.esm` reports `false`

## Network Design Gate Added After Task 39

Task 40 adds `docs/nodejs/design/NETWORK.md`, `autojs.nodejs.network.experimental=false`, and `BuildConfig.NODEJS_NETWORK_EXPERIMENTAL_ENABLED`.

Current default remains:

- global `fetch()` rejects before network I/O
- raw `http`, `https`, `net`, `tls`, `dns`, `dns/promises`, and `dgram` authority remains unavailable
- network module recognition remains a compatibility and stable-denial surface, not permission to perform I/O
- live network enablement should continue through the controlled fetch provider with timeout, abort, body budget, TLS, proxy, packaged manifest, and service-destroy cancellation policy
- `require("autojs6:profile").experimentalFlags.network` reports `false`

## Controlled Fetch MVP Added After Task 40

Task 41 adds the controlled fetch MVP without enabling raw Node networking:

- global `fetch` is now an AutoJs6 bridge wrapper
- `require("fetch")` and `require.resolve("fetch")` are available for CommonJS scripts
- supported request shape includes `GET`, `POST`, headers, string/binary bodies, `timeoutMs`, `AbortController.signal`, and `maxResponseBytes`
- response helpers include `text()`, `arrayBuffer()`, and `json()`
- fake-provider tests cover GET success, POST body, timeout, abort, response-too-large, module shadowing protection, pending cleanup, and raw `http` denial
- the Android provider is backed by OkHttp but rejects before I/O in normal builds because `BuildConfig.NODEJS_NETWORK_EXPERIMENTAL_ENABLED=false`
- provider destroy cancels tracked OkHttp calls
- packaged diagnostic reporting now prints manifest `android.permission.INTERNET` presence
- raw `http`, `https`, `net`, `tls`, `dns`, `dns/promises`, and `dgram` authority remains unavailable

## Worker Threads Design Gate Added After Task 41

Task 42 adds `docs/nodejs/design/WORKER_THREADS.md`, `autojs.nodejs.worker_threads.experimental=false`, and `BuildConfig.NODEJS_WORKER_THREADS_EXPERIMENTAL_ENABLED`.

Current default remains:

- `require("worker_threads")` rejects with disabled-builtin error shape
- `require("node:worker_threads")` rejects with disabled-builtin error shape
- `require.resolve("worker_threads")` rejects before local package shadowing
- `require("autojs6:profile").workerThreads` reports `false`
- `require("autojs6:profile").highRiskCapabilities.workerThreads` reports `false`
- `require("autojs6:profile").experimentalFlags.workerThreads` reports `false`
- future workers must not access AutoJs bridge modules, Android Context, accessibility, media projection, image, UI/dialog, shell, engines, raw network, child process, native addon, `vm`, inspector, or unrestricted filesystem APIs

## Performance Benchmark Added After Task 42

Task 43 adds `NodeEmbeddedPerformanceInstrumentationTest`, `docs/nodejs/PERFORMANCE.md`, and `:app:pullNodeEmbeddedPerformanceReports`.

The benchmark is optional and does not fail release builds by default. It records:

- device and ABI metadata
- cold and warm `runScript` wall time
- embedded result `elapsedMs`
- `load.duration.ms` when exposed by the runtime
- app RSS/PSS/thread/fd snapshots before and after script execution and after destroy
- embedded service RSS/thread/fd snapshots when `/proc/<pid>` remains readable
- Node `process.memoryUsage()`, `process.resourceUsage()`, `process.version`, and `process.versions`

Reports use schema `autojs6-node-performance-v1` and are pulled to `app/build/reports/nodejs/perf/`. Threshold mode is available through `autojs.nodejs.perf.failOnThreshold=true` and explicit max-value instrumentation arguments, but remains off by default because timing and memory results are device-sensitive.

## Memory Leak Stress Added After Task 43

Task 44 adds `NodeEmbeddedMemoryLeakStressInstrumentationTest`.

The stress gate is intended for extended release-candidate or lifecycle-change validation. It records `autojs6-node-memory-leak-stress-v1` JSON reports and covers:

- 100 simple scripts
- 100 scoped fs callback scripts
- 100 `fs.promises` scripts when available
- 100 fake `toast`/`app` bridge scripts
- 50 fake image-handle create/use/recycle cycles
- timeout scripts
- uncaught exception scripts

The gate samples RSS, Java heap, native heap, PSS, thread count, and fd count. It does not require exact stability; it fails only when a metric keeps rising across samples and exceeds conservative runaway thresholds. The same `:app:pullNodeEmbeddedPerformanceReports` task collects memory-stress JSON reports.

## Release Flavor Gate Added After Task 44

Task 45 adds `docs/nodejs/design/RELEASE_FLAVORS.md` and wires `autojs.nodejs.releaseFlavor` into the main APK and packaged APK verification flow.

Current release flavor policy:

- `nodeFull`: default; requires `arm64-v8a`, `armeabi-v7a`, and `x86_64` Embedded Node native runtime entries.
- `nodeMini`: keeps Embedded Node enabled but narrows the ABI set with `autojs.nodejs.releaseAbis`, defaulting to `arm64-v8a`.
- `rhinoOnly`: forces the effective runtime backend to Rhino and verifies the final APK does not package `libnode.so` or `libautojs6-node.so`.
- `x86`: remains fail-fast when selected or when `app/src/main/jniLibs/x86/libnode.so` appears.

The `embeddedNodePackagingPreflightAppDebug` report now prints release flavor, expected ABIs, complete source/merged/APK ABI sets, x86 runtime presence, and `release flavor policy ready`.

## Current Verification Status

Verification performed while creating and extending this baseline:

- `:app:tasks --group verification`: passed; the Task 17/18 verification tasks are registered.
- `:app:embeddedNodePackagedDiagnosticReport` with `PackagedNodeSmoke_v1.0.0.apk` and `arm64-v8a` expected ABI: passed.
- `:app:embeddedNodePackagedDiagnosticReport` with the same APK and `arm64-v8a,armeabi-v7a,x86_64` expected ABIs: failed as expected for an arm64-only packaged smoke APK, with an actionable report.
- `:app:verifyEmbeddedNodeReleaseCandidate --dry-run`: passed; aggregate dependency graph is intact.
- `NodeAutoJsBridgeInstrumentationTest`: 22/22 passed on a connected Android 12 device.
- `:app:compileAppDebugAndroidTestKotlin`: passed after adding the Task 20/21 corpus classes.
- `NodeCompatCorpusInstrumentationTest`: 2/2 passed on a connected Android 12 device.
- `NodeSecurityRegressionInstrumentationTest`: 2/2 passed on a connected Android 12 device.
- Task 22/24/25/27 design gate docs: added as documentation-only updates; no runtime build was required for those doc-only changes.
- `:app:compileAppDebugAndroidTestKotlin`: passed after adding Task 23/26/28 focused test classes.
- `NodeFsPromisesInstrumentationTest`: 2/2 passed on a connected Android 12 device.
- `NodeStreamMvpInstrumentationTest`: 1/1 passed on a connected Android 12 device.
- `NodeZlibBufferInstrumentationTest`: 1/1 passed on a connected Android 12 device.
- `:app:compileAppDebugAndroidTestKotlin`: passed after adding Task 29/30/31 focused test classes.
- `NodeCryptoExpansionInstrumentationTest`: 1/1 passed on a connected Android 12 device.
- `NodeProcessSweepInstrumentationTest`: 1/1 passed on a connected Android 12 device.
- `NodeOsPrivacyInstrumentationTest`: 1/1 passed on a connected Android 12 device.
- `:app:compileAppDebugAndroidTestKotlin`: passed after adding Task 32 clipboard bridge runtime and focused tests.
- `NodeClipboardBridgeInstrumentationTest`: 2/2 passed on a connected Android 12 device.
- `EmbeddedNodePackagedApkSmokeTest#buildClipboardBridgeEmbeddedNodePackagedApk`: 1/1 passed on a connected Android 12 device.
- `EmbeddedNodePackagedApkSmokeTest#installAndRunClipboardBridgeEmbeddedNodePackagedApk`: initially exposed missing queued clipboard fallback, then 1/1 passed after adding the fallback.
- `:app:compileAppDebugAndroidTestKotlin`: passed after adding Task 33 device bridge runtime, packaged smoke, and focused tests.
- `NodeDeviceBridgeInstrumentationTest`: 2/2 passed on a connected Android 12 device.
- `EmbeddedNodePackagedApkSmokeTest#buildDeviceBridgeEmbeddedNodePackagedApk`: 1/1 passed on a connected Android 12 device.
- `EmbeddedNodePackagedApkSmokeTest#installAndRunDeviceBridgeEmbeddedNodePackagedApk`: 1/1 passed on a connected Android 12 device.
- `NodeAutoJsBridgeInstrumentationTest`: rerun after Task 33 and passed 22/22 on a connected Android 12 device.
- `:app:compileAppDebugAndroidTestKotlin`: passed after adding Task 34 shell bridge runtime and focused tests.
- `NodeShellBridgeInstrumentationTest`: 3/3 passed on a connected Android 12 device.
- `NodeAutoJsBridgeInstrumentationTest`: rerun after Task 34 and passed 22/22 on a connected Android 12 device.
- `:app:compileAppDebugAndroidTestKotlin`: passed after adding Task 35 engines exec runtime, main-process dispatch service, and focused tests.
- `NodeEnginesBridgeInstrumentationTest`: 4/4 passed on a connected Android 12 device.
- `NodeAutoJsBridgeInstrumentationTest`: rerun after Task 35 and passed 22/22 on a connected Android 12 device.
- `NodeShellBridgeInstrumentationTest`: rerun after Task 35 shared bridge changes and passed 3/3 on a connected Android 12 device.
- `:app:compileAppDebugAndroidTestKotlin`: passed after adding Task 36 accessibility selector v1 runtime and focused tests.
- `NodeAccessibilitySelectorBridgeInstrumentationTest`: 4/4 passed on a connected Android 12 device.
- `NodeAutoJsBridgeInstrumentationTest`: rerun after Task 36 and passed 22/22 on a connected Android 12 device.
- `NodeAccessibilitySelectorBridgeInstrumentationTest`: rerun after selector options normalization and passed 4/4 on a connected Android 12 device.
- `:app:compileAppDebugAndroidTestKotlin`: passed after adding Task 37 image bridge expansion runtime and focused tests.
- `NodeImageBridgeExpansionInstrumentationTest`: 4/4 passed on a connected Android 12 device.
- `NodeAutoJsBridgeInstrumentationTest`: rerun after Task 37 and passed 22/22 on a connected Android 12 device.
- Task 38 Work Manager design gate: added as a documentation-only update; no runtime build was required.
- `:app:compileAppDebugKotlin`: passed after adding Task 39 ESM flag and BuildConfig wiring.
- `:app:compileAppDebugAndroidTestKotlin`: passed after updating Task 39 `.mjs` assertions.
- `:app:externalNativeBuildAppDebug`: passed after updating the Embedded Node `.mjs` rejection message.
- `EmbeddedScriptMvpInstrumentationTest#esmUnsupportedGateRejectsMjsEntrypointsAndCommonJsResolutionTargets`: 1/1 passed on a connected Android 12 device after Task 39.
- `:app:compileAppDebugKotlin`: passed after adding Task 40 network flag and BuildConfig wiring.
- `:app:compileAppDebugAndroidTestKotlin`: passed after adding Task 40 profile and security assertions.
- `:app:externalNativeBuildAppDebug`: passed after updating the Safe Node profile experimental flag snapshot.
- `EmbeddedScriptMvpInstrumentationTest#autojs6ProfileModuleExposesReadOnlyCapabilityRegistry`: 1/1 passed on a connected Android 12 device after Task 40.
- `NodeSecurityRegressionInstrumentationTest#dangerousApisAndNetworkOperationsStayDenied`: 1/1 passed on a connected Android 12 device after Task 40.
- `:app:compileAppDebugKotlin`: passed after adding Task 41 controlled fetch provider, bridge routing, error codes, and packaged INTERNET diagnostic reporting.
- `:app:compileAppDebugAndroidTestKotlin`: passed after adding Task 41 focused fetch tests.
- `:app:externalNativeBuildAppDebug`: passed after adding the JavaScript controlled fetch wrapper.
- `NodeFetchBridgeInstrumentationTest`: 3/3 passed on a connected Android 12 device after Task 41.
- `EmbeddedScriptMvpInstrumentationTest#webDataGlobalsSupportBlobFileFormDataRequestResponseAndDisableFetch`: 1/1 passed on a connected Android 12 device after Task 41.
- `NodeSecurityRegressionInstrumentationTest#dangerousApisAndNetworkOperationsStayDenied`: 1/1 passed on a connected Android 12 device after Task 41.
- `:app:compileAppDebugKotlin`: passed after adding Task 42 worker_threads flag and BuildConfig wiring.
- `:app:compileAppDebugAndroidTestKotlin`: passed after adding Task 42 focused worker_threads design-gate test.
- `:app:externalNativeBuildAppDebug`: passed after adding the JavaScript profile `experimentalFlags.workerThreads` snapshot.
- `NodeWorkerThreadsDesignGateInstrumentationTest`: 1/1 passed on a connected Android 12 device after Task 42.
- `EmbeddedScriptMvpInstrumentationTest#autojs6ProfileModuleExposesReadOnlyCapabilityRegistry`: 1/1 passed on a connected Android 12 device after Task 42.
- `NodeSecurityRegressionInstrumentationTest#dangerousApisAndNetworkOperationsStayDenied`: 1/1 passed on a connected Android 12 device after Task 42.
- `:app:compileAppDebugKotlin`: passed after adding Task 43 performance report pull task.
- `:app:compileAppDebugAndroidTestKotlin`: passed after adding Task 43 performance benchmark instrumentation.
- `NodeEmbeddedPerformanceInstrumentationTest`: 1/1 passed on a connected Android 12 device after Task 43.
- `:app:pullNodeEmbeddedPerformanceReports`: passed after Task 43 and pulled JSON reports to `app/build/reports/nodejs/perf/`.
- `:app:compileAppDebugAndroidTestKotlin`: passed after adding Task 44 memory leak stress instrumentation.
- `NodeEmbeddedMemoryLeakStressInstrumentationTest`: 1/1 passed on a connected Android 12 device after Task 44.
- `:app:pullNodeEmbeddedPerformanceReports`: passed after Task 44 and pulled performance plus memory-stress JSON reports to `app/build/reports/nodejs/perf/`.
- `:app:compileAppDebugKotlin`: passed after adding Task 45 release flavor BuildConfig and ABI selection wiring.
- `:app:embeddedNodePackagingPreflightAppDebug`: passed for default `nodeFull`; report showed complete `arm64-v8a`, `armeabi-v7a`, and `x86_64` runtime entries and no x86 Node runtime.
- `:app:embeddedNodePackagingPreflightAppDebug -Pautojs.nodejs.releaseFlavor=rhinoOnly`: passed after tightening JNI packaging excludes; report verified no `libnode.so` or `libautojs6-node.so` in the APK.
- `:app:tasks -Pautojs.nodejs.releaseFlavor=nodeMini -Pautojs.nodejs.releaseAbis=x86`: failed as expected with an actionable x86 unsupported message.
- `:app:embeddedNodePackagedDiagnosticReport` with `PackagedNodeSmoke_v1.0.0.apk` and `arm64-v8a` expected ABI: passed after Task 45 flavor-aware packaged verifier updates.
- `:app:compileAppDebugKotlin`: passed after adding Task 46 runtime native library hash logging.
- `:app:nativeNodeSymbolArchiveReport`: passed after Task 46 and generated `app/build/reports/native-node/native-symbol-archive-appDebug.txt` with `embedded.node.native.symbol_archive.ready=true`.
- `:app:verifyEmbeddedNodeReleaseCandidate --dry-run`: passed after Task 46 and confirmed `nativeNodeSymbolArchiveReport` is part of the release-candidate task graph.
- Task 47 added checked-in examples under `sample/nodejs/` and `NodeExampleProjectInstrumentationTest` smoke coverage for `hello-node` and `toast-app`.
- `:app:compileAppDebugAndroidTestKotlin`: passed after wiring `sample/nodejs` into the `androidTest` asset source set.
- `NodeExampleProjectInstrumentationTest`: 2/2 passed on a connected Android 12 device after Task 47.

Instrumentation tests executed while creating and extending this baseline: 186 successful tests.

A full device and packaged release-candidate run still requires:

- a connected Android device
- focused instrumentation class execution
- a concrete packaged APK passed to `embeddedNodePackagedDiagnosticReport`
- packaged smoke APK artifacts available to `embeddedNodePackagedSmokeHostVerify`

Do not treat this report as a substitute for running the release checklist before shipping.

## Follow-up Completeness Work

Recommended next tasks:

- fold focused device tests into a more ergonomic release-candidate workflow without recursive Gradle invocation
- connect real providers for `dialogs`, `accessibility`, `media_projection`, and `image`
- add a true packaged APK compatibility corpus smoke in addition to the current moduleSources/preload proxy
- add packaged project opt-in gating for controlled network and make INTERNET permission verification actionable when that opt-in is used
- compare Task 43 performance JSON medians when changing native load, service lifecycle, or destroy behavior
- run Task 44 memory leak stress before merging lifecycle, bridge transport, fs, or image-handle ownership changes
- run Task 45 release flavor preflight for `nodeFull`, `nodeMini`, and `rhinoOnly` before changing native packaging rules
- keep the Task 46 symbol archive report with APKs, logcat, and tombstones when triaging native crashes
- keep Task 47 examples aligned with the documented Safe Node Profile and run `NodeExampleProjectInstrumentationTest` after changing example-facing modules
- keep high-risk features behind design gates
