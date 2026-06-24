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
`screenshot-ocr-script`, `network-fetch-script`, `typescript-cjs`,
`typescript-esm`, `typescript-automation`, `typescript-ocr`,
`typescript-long-running-task`, `compiled-tsx`) and invalid fixtures for
missing entries, invalid package names, unknown permissions, unsupported TSX
entries, and unsafe dependency bundles. The JSON output includes a
compatibility analyzer status (`compatible`, `compatible with capability`,
`experimental`, `unsupported`, or `unsafe`) plus required builtins, capability
requirements, dependency graph size, package export-condition usage, entry
format, and APK-builder preflight metadata.

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
P13-07 adds a packaged long-running dispatch gate for projects that declare
`node.executionMode=interactive_long_running`; packaged status snapshots should
use `launchSurface=packaged_long_running`, while final install/run,
restore/cleanup, permission-denial, and background-start evidence remains open.
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
still keep WebSocket live I/O feature-gated or denied. Packaged service mode is
partial through P13-07 and must not be treated as final until the packaged APK
smoke and Android policy matrix pass.

Lifecycle tests must still verify that normal scripts are one-shot executions
controlled by `timeoutMs`, including timeout recovery, uncleared interval
timeout behavior, and the explicit execution-mode policy gate that rejects
reserved or unsupported launch surfaces before user code runs.
`NodeExecutionModePolicyInstrumentationTest` freezes the Phase 7 state machine
for `one_shot`, `interactive_long_running`, `scheduled`,
`worker_computation`, and the S8-41 design-gated `node_sandboxed` POC;
`NodeEmbeddedLifecycleStressInstrumentationTest#explicitLongRunningModeIsRejectedBeforeUserScriptRuns`
verifies that direct script `interactiveLongRunning`/legacy `long_running`
cannot be enabled by `timeoutMs=0` or pending timers.

Current hardened isolated runtime POC instrumentation:

```powershell
$env:ANDROID_SERIAL='QV710AF65F'
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeHardenedIsolatedRuntimePocInstrumentationTest" "-Pautojs.nodejs.test.deviceProfile=sony"
```

This suite verifies the S8-41 manifest and policy POC: `nodeSandboxed`
normalizes to `node_sandboxed` but remains `design_gated`; the dedicated
`NativeNodeSandboxedScriptService` manifest entry is non-exported and
`android:isolatedProcess=true`; and direct script requests reject before user
code until the S8-42 Binder capability broker exists. See
`docs/nodejs/reports/phase8/HARDENED_ISOLATED_RUNTIME_POC.md`.

Current capability broker v2 focused instrumentation:

```powershell
$env:ANDROID_SERIAL='QV710AF65F'
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeCapabilityBrokerV2InstrumentationTest" "-Pautojs.nodejs.test.deviceProfile=sony"
```

This suite verifies the S8-42 provider-side broker contract: explicit
module/method declarations, required capability metadata, JSON-array argument
validation, broker payload size limits, quota-bound per-call audit rows,
execution-owned diagnostics, and Android provider native diagnostics. This is
the policy/audit layer for a future Binder broker; it does not make
`node_sandboxed` executable. See
`docs/nodejs/reports/phase8/CAPABILITY_BROKER_V2.md`.

Current embedded process-pool policy focused instrumentation:

```powershell
$env:ANDROID_SERIAL='QV710AF65F'
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeEmbeddedProcessPoolPolicyInstrumentationTest" "-Pautojs.nodejs.test.deviceProfile=sony"
```

This suite verifies the S8-43 process-pool policy gate: three non-exported
embedded script service slots are declared, reserved slots remain disabled by
default, the device-memory-aware recommendation stays separate from the
effective one-slot runtime limit, long-running slot routing remains reserved,
and normal script results publish `embedded_script.process_pool.*` primary-only
diagnostics. See `docs/nodejs/reports/phase8/PROCESS_POOL_POLICY_GATE.md`.

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

`verifyNodeExamples` validates `sample/nodejs/examples.json`, required files, metadata, Node directives, PASS markers,
and Phase 7 example capability/provider/security declarations, then writes `app/build/reports/nodejs/examples.json`
and `.md`.
The Phase 8 catalog adds `require-esm`, `http-client-compat`, `filehandle-advanced`, `fs-watch`, `compile-cache`,
`inspector-debug`, `cpu-profile`, and `hardened-sandbox`; these are static-verified examples and are not added to the
default instrumentation smoke list.

The instrumentation smoke runs `hello-node`, `cjs-package`, `fs-scoped`, `fs-promises`, `npm-pure-js-cjs`, `toast-app`,
`accessibility-click-text`, `screenshot-find-image`, and `disabled-features-demo` from the `androidTest` asset source set.
`packaged-node-apk` remains marked packaged-compatible and is covered by packaged APK smoke gates.

Rhino dual-engine compatibility fixtures:

```powershell
.\gradlew.bat --console=plain :app:verifyNodeRhinoDualEngineCompatTests
.\gradlew.bat --console=plain :app:verifyNodeRhinoCompatDualEngineDeviceEvidence
.\gradlew.bat --console=plain :app:verifyNodeRhinoCompatDeveloperDocsSync
```

This S11-28 host gate validates `docs/nodejs/dual-engine-compat`, writes the
`rhino-dual-engine-compat-tests-verification` report, and keeps
`supported_async` cases separate from `blocked_by_design` cases. P12-33 adds
fake device instrumentation evidence for the selected high-value cases and
verifies the required `engine`, `compatStatus`, `providerMode`,
`resourceCleanupStatus`, and `skipReason` result fields. P12-34 verifies that
the formal migration guide, Pro 9 comparison, matrix, security model, testing
guide, and type declaration README no longer mark completed P12 surfaces as
provider gaps and keep stable rejection guidance visible.

Focused dual-engine fake device evidence hook:

```powershell
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.RhinoNodeDualEngineCompatInstrumentationTest"
```

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

This smoke proves the v1.1 bridge modules are not main-app-only by packaging fake-provider coverage for controlled `fetch`, OCR opaque image handles, JSON storage, notifications, UI handles/events/update/batch update, `work_manager` metadata serialization, and a project-local JS-only plugin that uses storage, notifications, UI, and controlled fetch. It also verifies declared `node.permissions` including `plugin.<id>`, bridge-module shadowing denial, plugin resource cleanup/unload isolation, and zero pending bridge calls without granting live Android provider authority.

Focused packaged Rhino compat smoke:

```powershell
.\gradlew.bat --console=plain :app:verifyNodeRhinoCompatPackagedSmokeHost
.\gradlew.bat --console=plain :app:verifyNodeRhinoCompatPackagedSmoke "-Pautojs.nodejs.test.deviceProfile=sony"
```

The S11-29 host task writes the Rhino compat packaged smoke contract report.
The device gate selects
`org.autojs.autojs.apkbuilder.EmbeddedNodePackagedApkSmokeTest#installAndRunRhinoCompatEmbeddedNodePackagedApk`
and verifies packaged `autojs6:compat` loader, bridge permission markers,
resource cleanup, and the toast/files/storage/selector/image baseline paths.
See `docs/nodejs/reports/phase11/RHINO_COMPAT_PACKAGED_SMOKE.md`.

Focused Rhino compat resource cleanup/security gate:

```powershell
.\gradlew.bat --console=plain :app:verifyNodeRhinoCompatResourceCleanupSecurityHost
.\gradlew.bat --console=plain :app:verifyNodeRhinoCompatResourceCleanupSecurity "-Pautojs.nodejs.test.deviceProfile=sony"
```

The S11-30 host task writes
`build/reports/nodejs/rhino-compat-resource-cleanup-security-verification.json`
and `build/reports/nodejs/rhino-compat-resource-cleanup-security-verification.md`.
The connected-device gate selects focused cleanup/security instrumentation for
Rhino compat `image`, `selector`, `ui`, `db`, and `websocket/fetch` resources,
plus the design-gated no-resource boundaries for `event`, `floaty`, and Rhino
`ui.run`. The acceptance rule is active resource zero after stop, timeout,
close, or destroy, or a documented design-gated residual policy. See
`docs/nodejs/reports/phase11/RHINO_COMPAT_RESOURCE_CLEANUP_SECURITY.md`.

Phase 11A Rhino compat summary:

```powershell
.\gradlew.bat --console=plain :app:generateNodeRhinoCompatPhase11Summary
```

The S11-31 summary task refreshes the Phase 11A host reports and writes
`build/reports/nodejs/phase11a-summary.json` plus
`build/reports/nodejs/phase11a-summary.md`. The static planning summary lives at
`docs/nodejs/reports/phase11/PHASE11A_SUMMARY.md` and records supported
surfaces, blocked decisions, migration paths, and next-phase task splits.

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

Phase 10 S10-11 upgrades this same registry to Provider Registry v2 without
breaking legacy report consumers. `status` and `activeResourceCount` remain
present, while `statusCode` uses the Phase 10 vocabulary
`unavailable`/`fake`/`available`/`permission_required`/`degraded`/`failed`.
The v2 JSON also exposes `healthCheck`, `activeResources`, `lastError`, and
`packagedSupport` for every provider. The host verifier is:

```powershell
.\gradlew.bat --console=plain :app:verifyNodePhase10ProviderRegistryV2
```

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

The same test class also runs the S8-19 module conformance v2 corpus from `app/src/androidTest/assets/node_compat_corpus_v2/`. That corpus covers CommonJS `require()` of synchronous ESM graphs, async/TLA ESM rejection with `ERR_REQUIRE_ASYNC_MODULE`, JSON import attributes with CommonJS JSON cache sharing, ESM/CommonJS cycles, scoped `import.meta.resolve()`, the `autojs6` package condition, `.mts`/`.cts` mixed graphs, and a packaged moduleSources mixed graph with ESM, CommonJS, JSON, dynamic import, and lightweight TypeScript sources.

Focused Web Platform globals alignment instrumentation:

```powershell
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeWebPlatformGlobalsInstrumentationTest" "-Pautojs.nodejs.test.deviceProfile=sony"
```

This S8-20 gate verifies `Headers`, `Request`, `Response`, `FormData`, `Blob`,
`File`, `URLPattern`, `DOMException`, `EventTarget`, `MessageEvent`,
`CompressionStream`, and `DecompressionStream`, including descriptor locking,
gzip roundtrip behavior, and default controlled-network-off `fetch()` denial.
It is the compatibility prelude for later `node:http`/`node:https` client
facade work and does not enable raw socket modules.

Focused Phase 9 WebAssembly/WASI instrumentation:

```powershell
.\gradlew.bat --console=plain :app:verifyNodeWasmWasiGate "-Pautojs.nodejs.test.deviceProfile=sony"
```

This gate verifies basic `WebAssembly.compile` / `instantiate` support and
scoped `.wasm` file loading while keeping raw `wasi` / `node:wasi` disabled and
unshadowable.

Focused Phase 10 developer-experience instrumentation:

```powershell
.\gradlew.bat --console=plain :app:verifyNodePhase10DeveloperExperience
.\gradlew.bat --console=plain :app:verifyNodeDeveloperExperienceGate "-Pautojs.nodejs.test.deviceProfile=sony"
```

`verifyNodePhase10DeveloperExperience` writes the S10-43 through S10-46
developer-experience report. The host verifier builds a temporary `.ts` /
`.mts` / `.cts` project, emits CJS/ESM output and source maps, proves
incremental cache hit and invalidation behavior, runs the generated output with
host Node, and rejects lifecycle scripts, compiler plugins, runtime `.tsx`,
native addons, and transform-required TypeScript syntax. The Sony gate runs
`NodeTypeScriptInstrumentationTest`, `NodeSourceMapStackTraceInstrumentationTest`,
the focused embedded `node:test` subset methods from
`NodeCoreBuiltinCompatibilityInstrumentationTest`, and the debug-only plugin
reload method from `NodePluginSystemInstrumentationTest`. Hot reload remains a
debug-only host workflow; packaged and release hot reload stay disabled.

Focused controlled HTTP client facade instrumentation:

```powershell
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeHttpClientFacadeInstrumentationTest" "-Pautojs.nodejs.test.deviceProfile=sony"
```

This S8-21 gate verifies the controlled `http` / `node:http` and `https` /
`node:https` client facade when an enforced manifest declares `network`. It
covers `request()` / `get()`, request headers, response headers/status metadata,
buffered request body chunks, response `data` / `end`, `pipe()`, timeout,
abort, default manual redirect handling, explicit `maxRedirects` follow, bridge
permission tagging, and pending-call cleanup. Without the enforced `network`
manifest, the existing synchronous disabled error path remains covered by the
raw network and security regression tests.

Focused controlled HTTPS client facade instrumentation:

```powershell
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeHttpsClientFacadeInstrumentationTest" "-Pautojs.nodejs.test.deviceProfile=sony"
```

This S8-22 gate verifies the HTTPS-specific policy layer over the same
controlled client facade. It covers `https` / `node:https` identity, `https:`
request dispatch through controlled fetch, safe `https.Agent` shape options,
request header/body serialization, response metadata/body delivery, bridge
permission tagging, pending-call cleanup, and fail-closed TLS customization:
`rejectUnauthorized=false`, custom secure contexts, client certificate fields,
request-level `createConnection`, and unsafe agent TLS options all reject with
`ERR_AUTOJS6_NETWORK_POLICY_DENIED`.

Focused HTTP stream semantics instrumentation:

```powershell
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeHttpStreamSemanticsInstrumentationTest" "-Pautojs.nodejs.test.deviceProfile=sony"
```

This S8-23 gate verifies the `ClientRequest` / `IncomingMessage` stream subset
over the controlled client facade. It covers writable request body chunks,
readable response `data` / `end` / `close`, `pipe()` with a backpressure-style
destination, request-level `AbortSignal`, pre-aborted signal no-dispatch,
`maxResponseBytes` rejection, service-destroy cancellation, and pending-call
cleanup.

Focused HTTP Agent facade instrumentation:

```powershell
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeHttpAgentFacadeInstrumentationTest" "-Pautojs.nodejs.test.deviceProfile=sony"
```

This S8-24 gate verifies the limited `http.Agent` / `https.Agent` facade. It
covers safe shape fields (`keepAlive`, `maxSockets`, `maxFreeSockets`,
`defaultPort`, `protocol`, `getName()`, and `destroy()`), `ClientRequest.agent`
binding, empty compatibility pool objects, request permission tagging, pending
cleanup, and fail-closed denial for request-level `createConnection`,
`socketPath`, and custom agent connector methods.

Focused controlled Undici facade instrumentation:

```powershell
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeUndiciFacadeInstrumentationTest" "-Pautojs.nodejs.test.deviceProfile=sony"
```

This S8-25 gate verifies the reserved `require("undici")` compatibility
facade. It covers controlled `fetch()` / `request()` dispatch, Web Platform
constructor reuse, `Dispatcher` / `Agent` / `MockAgent` safe shapes,
`setGlobalDispatcher()` / `getGlobalDispatcher()` validation, local
`node_modules/undici` shadow denial, bridge permission tagging, pending
cleanup, and fail-closed denial for raw connectors, dispatcher injection,
`socketPath`, proxy/tunnel options, `connect()`, `stream()`, and direct
dispatcher dispatch.

Focused HTTP npm compatibility corpus instrumentation:

```powershell
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeHttpNpmCorpusInstrumentationTest" "-Pautojs.nodejs.test.deviceProfile=sony"
```

This S8-26 gate copies `app/src/androidTest/assets/http_compat_corpus/` into a
scoped working directory and runs six offline npm-shaped fixtures through the
controlled `node:http` / `node:https` client facade. It covers packages using
`http.get()`, `https.request()`, `IncomingMessage` `data` / `end`
consumption, form-style POST bodies, request-level `AbortSignal`, safe Agent
options, fixture metadata completeness, native-addon absence, lifecycle-script
absence, bridge permission tagging, and pending cleanup.

Current fixed real npm corpus instrumentation:

```powershell
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeRealNpmCompatInstrumentationTest"
```

This suite copies `app/src/androidTest/assets/npm_compat_real/` into a scoped working directory and runs fixed, offline,
license-noted CommonJS package snapshots. It covers small utilities, nested dependencies, semver parsing,
querystring/URL utilities, glob/path matching, crypto hashing, stream utilities, zlib buffer utilities, and core builtin
feature detection for `constants`, `util/types`, `events`, and `diagnostics_channel`. The v1.1/S7-48 corpus grew from
7 to 13 fixtures and added an ESM-only dependency, a hybrid CJS/ESM package, Buffer helpers, fetch-wrapper fallback
behavior, assertion/test helper patterns, `AsyncLocalStorage`, `stream/promises`, WebCrypto digest helpers, worker CPU
package fallback behavior while `worker_threads` remains default-disabled, a tiny test-runner package, and a database
abstraction package. S8-46 grows the corpus from 13 to 20 fixtures while keeping the pass rate at 100%: it adds native
TypeScript source project shape, CJS `require(esm)`, default-denied `node:http`, `FormData`/`fetch`, scoped
`FileHandle`, `fs.watch()` lifecycle, and compile-cache-shaped package coverage. S9-46 grows the corpus from 20 to 30
fixtures with WebAssembly, WASI-policy detection, inspector-free diagnostics, promise-pool concurrency, a wider
compile-cache graph, TypeScript+WASM metadata, worker+WASM fallback, large ESM graph, and package-integrity coverage.
The Android connected runner executes the corpus in fixed 6-fixture batches so corpus growth does not require loosening
the existing 64-moduleSources runtime budget. It also checks that the corpus contains no native addons, no
install/postinstall scripts, and no ESM root runner; ESM-only package metadata is allowed only below fixture
`node_modules`.

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

`NodeRealNpmCompatInstrumentationTest` emits `realnpm.differential.result=...` JSON lines for each fixture through
connected-test diagnostics/logcat. The Android Gradle plugin stores these lines in the connected test logcat output, for example under
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

Phase 7 functional gate:

```powershell
$env:ANDROID_SERIAL="QV710AF65F"
.\gradlew.bat --console=plain :app:verifyNodePhase7Gate "-Pautojs.nodejs.test.deviceProfile=sony" "-Pautojs.nodejs.network.experimental=true"
```

This gate selects the Phase 7 focused instrumentation set and writes
`build/reports/nodejs/phase7-summary.json`. It covers packaged ESM/dynamic import,
live providers, long-running runtime, worker bootstrap/default-denial behavior,
modern core probes, plugin/UI, security corpus, resource stress subset, real npm
corpus, desktop differential reports, and dependency sanitizer fixtures. The
summary records expected, missing, failed, and skipped selectors by coverage
group. See [reports/phase7/README.md](reports/phase7/README.md).

Current dependency bundler verification:

```powershell
.\gradlew.bat --console=plain :app:bundleNodeDependencies
.\gradlew.bat --console=plain :app:prepareNodeNpmCiDependencies
.\gradlew.bat --console=plain :app:verifyNodeNpmCiFixtures
.\gradlew.bat --console=plain :app:manageNodeDependencies
.\gradlew.bat --console=plain :app:verifyNodeDependencyManagerFixture
.\gradlew.bat --console=plain :app:verifyNodeDependencyBundlerFixtures
.\gradlew.bat --console=plain :app:verifyNodePhase10Autojs6PmFixture
.\gradlew.bat --console=plain :app:verifyNodePackageManagerGate
.\gradlew.bat --console=plain :app:verifyNodePhase10LongRunningActivation
.\gradlew.bat --console=plain :app:verifyNodePhase10WorkerWasiBoundary
```

`bundleNodeDependencies` validates an existing project root with `package.json` and `node_modules`, then writes a
sanitized bundle plus `dependency-manifest.json`, `dependency-sbom-lite.json`, `dependency-compatibility.json`,
`dependency-size-report.txt`, and `dependency-rejections.txt`. Reports include a deterministic normalized bundle hash,
content-addressed cache key, package/version, integrity, license, file count, total bytes, unsupported capability usage,
AutoJs6 compatibility score, required builtins, capability requirements, ESM/CJS format, package export-condition usage,
and expected packaged graph size. Successful bundles are cached under
`app/build/nodejs/dependency-cache/<bundle-hash>/project/`.
`prepareNodeNpmCiDependencies` validates a host-side project with `package.json` and required `package-lock.json`, copies
it to a temporary working directory, runs `npm ci --ignore-scripts --no-bin-links --install-links --no-audit
--fund=false`, then feeds the installed `node_modules` through the same sanitizer. The default fixture is
`sample/nodejs/npm-ci-pure-js`, which uses local `vendor/` file dependencies so the verification path does not require
registry access. The sanitized output is written to `app/build/nodejs/npm-ci-bundle/project/`; reports are written to
`app/build/reports/nodejs/npm-ci/` and record npm version, lockfile hash, install-options hash, command flags, safety
controls, bundle hash, cache entry, and dependency compatibility status.
`manageNodeDependencies` is the S8-45 integrated project action. It requires `package-lock.json`, installs in a clean
host work directory with the same lifecycle-script-disabled npm flags, validates and sanitizes the installed tree,
writes `dependency-manifest.json`, `dependency-sbom-lite.json`, `dependency-compatibility.json`,
`dependency-size-report.txt`, `dependency-rejections.txt`, and `dependency-manager-report.json`, updates the
content-addressed cache, and verifies the resulting `project/` directory can be consumed as packaged APK
`assets/project`. `verifyNodeDependencyManagerFixture` checks the manager report schema, action chain, report index,
required npm flags, cache metadata, and `packageLifecycleScriptsExecuted=false`.
`verifyNodeDependencyBundlerFixtures` confirms the checked-in sample project passes, the sanitized bundle can be used as
the packaged APK `assets/project` layout, the desktop `npm ci` fixture installs without lifecycle scripts, `.bin` links,
or native addons, and unsafe fixtures are rejected for native addon, node-gyp build artifacts, every blocked lifecycle
script variant (`preinstall`, `install`, `postinstall`, `prepare`), unchecked `.bin` entries, npm cache/postinstall
artifacts, suspicious executables, symlink/path escapes, absolute/NUL/escaping package metadata references, oversized
files/packages/trees, and unsupported package-manager layouts such as PnP or pnpm-style nested stores. The
packaged-layout verifier rejects analyzer `unsafe` and `unsupported` statuses before APK builder gates run.
Native addon binaries are reported as
`NATIVE_ADDON_DISABLED`; `binding.gyp` and `build/{Release,Debug}` files are reported as
`NODE_GYP_ARTIFACT_UNSUPPORTED`.

`verifyNodePhase10Autojs6PmFixture` is the S10-21 through S10-27
host-managed `autojs6-pm` gate. It uses `sample/nodejs/autojs6-pm-fixture` to
generate local registry tarballs, resolve exact/caret/tilde/comparator/dist-tag
ranges, install a nested pure JavaScript graph, verify `sha512` integrity,
extract `.tgz` files with Tar Slip/link denial, write `autojs6-lock.json`, and
exercise `install`, `installFromTarball`, `update`, `remove`, `list`, `verify`,
and `prune`. Its negative tests cover integrity mismatch, tar path escape,
disabled lifecycle scripts, native addon files, and non-HTTPS registry protocol.
The gate is host-managed and experimental; Android runtime package installation,
full npm CLI behavior, hoisting, package-manager hooks, and peer installation
remain deferred.

Phase 13 Android-side package install manager:

```powershell
.\gradlew.bat --console=plain :app:verifyNodePhase13AndroidPackageInstallManager
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeAndroidPackageInstallManagerInstrumentationTest"
```

`NodeAndroidPackageInstallManagerInstrumentationTest` covers the app-private
local package store: install/update/remove/list/verify/prune, transaction
staging, store locking, cancellation, SHA-512 integrity mismatch rollback,
and lifecycle/bin/native payload denial. This does not promote Android npm CLI,
registry download, or a JavaScript package-manager API.

Phase 13 Pro-style Node entry directives:

```powershell
.\gradlew.bat --console=plain :app:verifyNodePhase13ProEntryDirectives
```

`EmbeddedScriptMvpInstrumentationTest#proStyleNodeEntryDirectivesParseToStableMetadata`
and the existing Node project launch alignment coverage verify that
`"nodejs ui"` and `"nodejs ui-thread"` parse into stable `NodeEntryDirective`
metadata and flow through descriptor, source, project request, debug descriptor,
native values, and failure markers. The gate stays partial until Activity
lifecycle, Back/Recents behavior, and concrete UI-thread dispatch evidence land.

Phase 13 `$autojs` global v1:

```powershell
.\gradlew.bat --console=plain :app:verifyNodePhase13AutoJsGlobalV1
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.EmbeddedScriptMvpInstrumentationTest#autojsGlobalV1ExposesFrozenDiagnosticsAndGatedJavaFacade"
```

`EmbeddedScriptMvpInstrumentationTest#autojsGlobalV1ExposesFrozenDiagnosticsAndGatedJavaFacade`
verifies that `globalThis.$autojs` is installed before user code as a frozen
read-only global, that `$autojs.profile` matches `require("autojs6:profile")`,
that `$autojs.androidContext` is diagnostic-only, and that default
`$autojs.java` calls reject with `ERR_AUTOJS6_JAVA_INTEROP_DISABLED`.

`verifyNodePhase10LongRunningActivation` writes the S10-29 through S10-36
machine-readable report for execution mode, interactive foreground
long-running, runtime budget/heartbeat, queueing, scheduled runner,
checkpoint/restore, stress coverage, and multi-instance blockers. It does not
replace device testing; run the focused S10 core selector on Sony for routine
activation evidence, and keep full `verifyNodeLongRunningGate` for
release/nightly stress because it includes the heavier embedded memory leak
stress case. The report keeps
multi-instance as `blocked_primary_only` because reserved process slots exist
but `effectiveMaxActiveSlots` remains `1`, and packaged long-running service
mode is still not promoted.

`verifyNodePhase10WorkerWasiBoundary` writes the S10-37 through S10-42
machine-readable report. It records default-disabled `worker_threads`, native
worker availability blockers, blocked MessageChannel/transfer and WorkerPool
promotion, pure WebAssembly compile/instantiate support, raw `wasi`/`node:wasi`
denial, and deferred controlled WASI plus packaged WASM cache/asset gates.
Run `verifyNodeWorkerWasiGate` on Sony for the focused device proof.

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
module compile-cache status/quota/app-private location policy, packaged template presence, and last-error availability.
They do not include user script contents or environment values.

Focused device report generator instrumentation:

```powershell
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.ui.settings.NodeDoctorReportGeneratorInstrumentationTest"
```

Current security regression instrumentation:

```powershell
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeSecurityRegressionInstrumentationTest"
```

This suite verifies dangerous process APIs, disabled builtins, network operation denial, scoped fs escape denial, native addon extension denial, and package exports/imports escape denial with stable error codes or documented current denial shape. `process.chdir` is treated as scoped and controlled; the security tests verify absolute and escaping chdir targets are rejected.

The Phase 5/7 security corpus in the same class also covers v1.1 expansion
boundaries: ESM static import escape denial, local dynamic import escape and
network URL denial, `fs.createReadStream()`/`fs.createWriteStream()` scoped
escape denial plus cleanup counters, unsupported `fetch`/`websocket` schemes,
bridge capability bypass denial before provider dispatch, WorkManager arbitrary
path denial before dispatch, database `ATTACH`/`DETACH`/`load_extension` SQL
denial before dispatch, UI handle cleanup without raw View exposure, JS-only
plugin `.node`, path, and requested-capability rejection before plugin code
executes, worker-local bridge module denial when worker support is available or
worker_threads fail-closed when unavailable, long-running timer exhaustion
timeout diagnostics, shell usage while `child_process` remains disabled, and
zlib stream error cleanup. Packaged ESM escape, packaged dynamic import escape,
and stale runtime compatibility descriptor mismatch stay covered by the focused
packaged ESM/dynamic-import and launch-failure gates.

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

Current FileHandle advanced focused instrumentation:

```powershell
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeFileHandleAdvancedInstrumentationTest"
```

For Sony unattended gates, pin the device and profile:

```powershell
$env:ANDROID_SERIAL='QV710AF65F'
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeFileHandleAdvancedInstrumentationTest" "-Pautojs.nodejs.test.deviceProfile=sony"
```

This S8-27 suite verifies the advanced managed `FileHandle` surface from `fs.promises.open()`: read/write, `readFile`, `writeFile`, `appendFile`, `truncate`, `stat`, `datasync`, `sync`, `close`, promise-level `readFile(fileHandle)` / `appendFile(fileHandle, data)`, scoped path denial, closed-handle `EBADF`, non-owned fd denial, and timeout recovery after an unclosed handle. Pair it with `NodeFsPromisesInstrumentationTest` when validating scoped fs regressions.

Current callback/sync fd API focused instrumentation:

```powershell
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeFdApiInstrumentationTest"
```

For Sony unattended gates, pin the device and profile:

```powershell
$env:ANDROID_SERIAL='QV710AF65F'
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeFdApiInstrumentationTest" "-Pautojs.nodejs.test.deviceProfile=sony"
```

This S8-28 suite verifies `fs.open`/`openSync`, `close`/`closeSync`, `read`/`readSync`, `write`/`writeSync`, `fstat`/`fstatSync`, and `ftruncate`/`ftruncateSync` over AutoJs6-managed scoped descriptors. It checks callback and sync success paths, options-object reads, Buffer/string writes, scoped path denial, closed/non-owned fd `EBADF`, and timeout recovery after an unclosed sync fd. Pair it with `NodeFsPromisesInstrumentationTest` and `NodeFileHandleAdvancedInstrumentationTest` for the advanced scoped fs regression set.

Current `opendir` / `Dir` / `Dirent` focused instrumentation:

```powershell
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeOpendirInstrumentationTest"
```

For Sony unattended gates, pin the device and profile:

```powershell
$env:ANDROID_SERIAL='QV710AF65F'
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeOpendirInstrumentationTest" "-Pautojs.nodejs.test.deviceProfile=sony"
```

This S8-29 suite verifies scoped `fs.opendir`, `fs.opendirSync`, and `fs.promises.opendir` behavior. It covers `Dir.read`/`readSync` callback and promise flows, `Dir.close`/`closeSync`, async iteration, `Dirent` shape and type helpers, buffer names, small recursive walks, recursive entry budget coverage, scoped path denial, file/missing/NUL path errors, missing callback denial, and closed `Dir` `ERR_DIR_CLOSED`. Pair it with `NodeFsPromisesInstrumentationTest`, `NodeFileHandleAdvancedInstrumentationTest`, and `NodeFdApiInstrumentationTest` for the advanced scoped fs regression set.

Current `fs.watch` / `watchFile` focused instrumentation:

```powershell
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeFsWatchInstrumentationTest"
```

For Sony unattended gates, pin the device and profile:

```powershell
$env:ANDROID_SERIAL='QV710AF65F'
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeFsWatchInstrumentationTest" "-Pautojs.nodejs.test.deviceProfile=sony"
```

This S8-30 suite verifies scoped `fs.watch`, `fs.promises.watch`, `fs.watchFile`, and `fs.unwatchFile` behavior. It covers callback, event-emitter, async-iterator, AbortSignal, close/ref/unref, buffer filename, scoped file URL and Buffer path inputs, polling stats, listener-specific unwatch, watcher quota, event-rate limiting, path denial, unsupported recursive watch, and timeout cleanup after a leaked watcher. Android file events may be coalesced, so the gate accepts `change` or `rename` and nullable filenames while still enforcing the AutoJs6 watcher wrapper and resource boundary. Pair it with the other Batch E classes for the advanced scoped fs regression set.

Current safe file operations focused instrumentation:

```powershell
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeSafeFileOperationsInstrumentationTest"
```

For Sony unattended gates, pin the device and profile:

```powershell
$env:ANDROID_SERIAL='QV710AF65F'
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeSafeFileOperationsInstrumentationTest" "-Pautojs.nodejs.test.deviceProfile=sony"
```

This S8-31 suite verifies scoped `fs.cp`, `fs.cpSync`, `fs.promises.cp`,
`fs.mkdtemp`, `fs.mkdtempSync`, `fs.promises.mkdtemp`, disposable temporary
directories, `fs.statfs`, `fs.statfsSync`, `fs.promises.statfs`, `fs.glob`,
`fs.globSync`, and `fs.promises.glob`. It covers callback, sync, promise,
path-like inputs, URL paths, Buffer paths, safe `cp` and `glob` option shapes,
statfs bigint output, scoped denial paths, `glob` `followSymlinks` denial, and
bounded policy checks for `link`, `chmod`, `chown`, `symlink`, and
Android/device roots. Pair it with the other Batch E classes for the advanced
scoped fs regression set.

Current FileHandle / watcher lifecycle fuzzer:

```powershell
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeFileHandleWatcherFuzzerInstrumentationTest" "-Pautojs.nodejs.fuzzerBudget=quick"
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeFileHandleWatcherFuzzerInstrumentationTest" "-Pautojs.nodejs.fuzzerBudget=normal"
```

For Sony unattended gates, pin the device and profile:

```powershell
$env:ANDROID_SERIAL='QV710AF65F'
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeFileHandleWatcherFuzzerInstrumentationTest" "-Pautojs.nodejs.test.deviceProfile=sony" "-Pautojs.nodejs.fuzzerBudget=normal"
```

This S8-32 fuzzer verifies lifecycle edge cases across managed FileHandle,
numeric fd, and watcher resources: close races, cancellation, service timeout
cleanup, path rename during watch, symlink replacement, fd reuse, long paths,
Unicode paths, and watcher quota exhaustion. It prints replay seed metadata on
failure and is part of the v1.1 beta instrumentation profile. Pair it with
`NodeResolverFuzzerInstrumentationTest` and `NodeScopedFsFuzzerInstrumentationTest`
for the current fuzzer regression set.

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

Current module compile-cache focused instrumentation:

```powershell
$env:ANDROID_SERIAL='QV710AF65F'
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeModuleCompileCacheInstrumentationTest,org.autojs.autojs.ui.settings.NodeDoctorReportGeneratorInstrumentationTest" "-Pautojs.nodejs.test.deviceProfile=sony"
```

This suite verifies internal Node module compile-cache enablement for CJS, ESM,
and TypeScript-after-stripping module graphs, app-private project-scoped cache
directories, runtime/content/loader diagnostic keys, custom-loader hit/miss
status reporting, safe-subset denial for script-visible
`node:module.enableCompileCache` / `flushCompileCache` controls, filtering of
compile-cache env variables from `process.env`, user-env override denial, and
Doctor report JSON/text coverage.

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

This suite verifies the `ui` bridge registry, capability manifest mapping, fake-provider `showLayout`, `drainEvents`, `update`, `batchUpdate`, and `close`, shadowing protection for `require("ui")`, `state`/`bind` one-way binding, batched binding updates, opaque handle shape, fake-provider and Android-live `handle.on` callback delivery through drained JSON snapshots, timeout and permission propagation, JSON-only invalid descriptor rejection before dispatch, absence of raw View/Activity handle properties, live Android Activity show, Button/Input/List/Switch/Checkbox/Tabs event delivery into Node, Progress updates through `batchUpdate`, state snapshots, update, close, provider-destroy cleanup, unsupported descriptor denial, WebView security-gate denial, foreign-handle denial, and active UI session limit denial.

Current JS-only plugin manager focused instrumentation:

```powershell
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodePluginSystemInstrumentationTest"
```

This suite verifies reserved `require("plugins")` and
`require.resolve("plugins")`, local `node_modules/plugins` shadowing denial,
project-local `./plugins/<id>` package loading, `plugins.install`, `enable`,
`disable`, `isEnabled`, `configure`, `config`, `version`, `dependencies`,
named `exports`, `resolve`, `load`, `status`, `capabilities`, `start`, `stop`,
`reload`, `unload`, and `list`, `autojs6.plugin.permissions` /
`dependencies` / `exports` metadata, exact `plugin.<id>` opt-in,
plugin-requested permission denial before code execution, disabled-load denial,
path-rooted namespace metadata, lifecycle context capability subset,
timer/provider/UI/socket/image/generic resource cleanup on stop, debug-only
reload denial and allow path, module-cache clearing on unload/reload, `.node`
rejection with `ERR_AUTOJS6_NATIVE_ADDON_DISABLED`, plugin entry path escape
denial, plugin-root relative resolution, and TypeScript declaration coverage.
Packaged plugin/UI smoke is covered by
`EmbeddedNodePackagedApkSmokeTest#installAndRunV11BridgeEmbeddedNodePackagedApk`
with an already-packaged local JS-only plugin, explicit `plugin.<id>` and
bridge capabilities, declarative UI, notifications, storage, controlled fetch,
resource cleanup, unload isolation, and `node_modules/plugins` shadow denial.

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

S8-21 keeps this default-denial policy intact. The controlled `http`/`https`
client facade runs only with an enforced manifest declaring `network`, and still
does not expose servers, raw sockets, DNS, TLS sessions, `CONNECT` tunneling, or
socket upgrades.

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

Current Phase 7 bridge/resource stress instrumentation:

```powershell
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeBridgeResourceLeakStressInstrumentationTest" "-Pandroid.testInstrumentationRunnerArguments.autojs.nodejs.bridgeStress.iterations=8"
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodePhase7ResourceStressInstrumentationTest" "-Pandroid.testInstrumentationRunnerArguments.autojs.nodejs.phase7Resource.providerIterations=3" "-Pandroid.testInstrumentationRunnerArguments.autojs.nodejs.phase7Resource.workManagerIterations=1" "-Pandroid.testInstrumentationRunnerArguments.autojs.nodejs.phase7Resource.pluginIterations=8"
.\gradlew.bat --console=plain :app:verifyNodePhase7ResourceStressGate "-Pautojs.nodejs.test.deviceProfile=sony"
```

This suite loops the v1.1 bridge module surface through JS fake transport and Android provider paths. It covers repeated
OCR calls, storage CRUD, notifications post/cancel or permission-denied cleanup, sensors subscribe/unsubscribe when a
supported sensor is available, accessibility selector queries, image operations and recycle calls, UI show/close,
shell timeout cleanup, and WebSocket open/close or default-disabled provider denial. S7-47 adds
`NodePhase7ResourceStressInstrumentationTest`, which records RSS, Java heap, native heap, thread count, fd count,
test-only libuv handle counts, and bridge diagnostics while stressing database open/transaction/close, real
WorkManager scheduled task execution/cancel cleanup, and JS-only plugin lifecycle load/start/stop/unload cleanup.
It asserts JS pending calls drain to zero and Android diagnostics report no active bridge resources, shell commands,
database handles, WorkManager registrations, WebSocket connections, leaked resources, or resource close errors.
Use `autojs.nodejs.bridgeStress.iterations`, `autojs.nodejs.phase7Resource.providerIterations`,
`autojs.nodejs.phase7Resource.workManagerIterations`, and `autojs.nodejs.phase7Resource.pluginIterations` to scale the
loop for local triage or nightly gates. `verifyNodePhase7ResourceStressGate` also selects the existing long-running
stress, live fetch/WebSocket destroy cleanup, live OCR/sensor/UI/WorkManager resource cleanup, and worker resource
stress classes; live network cases still require `autojs.nodejs.network.experimental=true` to run instead of skip.

Phase 13 full heavy resource stress status gate:

```powershell
.\gradlew.bat --console=plain :app:verifyNodePhase13FullHeavyResourceStress
```

This host gate does not synthesize soak success. It reads the required final
soak reports from `build/reports/nodejs/final-soak/quick-soak-8h.json` and
`build/reports/nodejs/final-soak/final-soak-24h.json`, then writes
`phase13-full-heavy-resource-stress.json` and `.md` under
`build/reports/nodejs`. The reports must expose duration plus comparable RSS,
Java heap, native heap, thread count, fd count, and bridge diagnostics, and
must not report monotonic leaks, orphan processes, or unrecoverable runtime
failures. Until both real device soaks are present and clean, P13-08 remains
partial and `full_heavy_resource_stress` remains open.

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

S8-44 partial-promotion recheck: [reports/phase8/WORKER_PARTIAL_PROMOTION_RECHECK.md](reports/phase8/WORKER_PARTIAL_PROMOTION_RECHECK.md).

Current experimental worker_threads focused instrumentation:

```powershell
.\gradlew.bat --console=plain :app:verifyNodeWorkerGate "-Pautojs.nodejs.worker_threads.experimental=true" "-Pautojs.nodejs.test.deviceProfile=sony"
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

This suite verifies that `.mjs` can still be rejected by an explicit ESM-off diagnostic request, then covers default partial `.mjs` entry execution, local static `.mjs` imports, top-level await, package `type=module` entry routing, scoped import escape denial, ESM default import from CommonJS, ESM JSON import attributes with CommonJS cache sharing, missing/unknown JSON attribute denial, scoped `import.meta.url`/`filename`/`dirname`/`main`/`resolve()` metadata, `import.meta.resolve()` for local/package/allowed-builtin specifiers, disabled builtin denial from `import.meta.resolve()`, ESM import of supported builtins, disabled builtin rejection from ESM, CJS `require()` of synchronous ESM graphs, CJS `require()` rejection for async ESM graphs with `ERR_REQUIRE_ASYNC_MODULE`, package `exports`/`imports` condition selection with resolver trace for both ESM and CommonJS importers, `autojs6` custom condition selection, package key order preservation, inactive `development`/`production` diagnostics, package-condition native addon denial, `.mjs` package targets, nested package imports, self-reference, null blocking, unsafe target denial, explicit dynamic import off rejection, local dynamic import of `.mjs`, CommonJS, and JSON modules, allowed builtin dynamic import, disabled builtin dynamic import denial, package dynamic import with ESM conditions, dynamic import scoped escape denial, and URL/absolute dynamic import rejection.

Packaged `.mjs`, `package.json` `type=module`, and dynamic import local smokes
run through the focused packaged ESM gate with the default production inrt
template:

```powershell
$env:ANDROID_SERIAL="QV710AF65F"
.\gradlew.bat --console=plain :app:verifyNodePackagedEsmGate "-Pautojs.nodejs.test.deviceProfile=sony" "-Pautojs.nodejs.identity=production" "-Pautojs.embeddedNode.expectedAbis=arm64-v8a"
```

With ESM partial enabled by default, the packaged ESM diagnostics smoke asserts
that CommonJS can synchronously `require()` a packaged `.mjs` module. The broader
packaged diagnostics smoke also verifies that `require()` rejects async ESM
graphs with `ERR_REQUIRE_ASYNC_MODULE`. The focused packaged ESM gate covers a
`.mjs` entry, package `type=module` `.js` modules, local static imports,
selected builtin imports, ESM-to-CJS interop, top-level await, source-name stack
shape, scoped escape denial, disabled builtin denial, local dynamic import of
`.mjs`, CommonJS, and type-module targets, and runtime compatibility descriptor
version-vector mismatch before script execution. The gate also validates
descriptor versions inside packaged APK fixtures and prints packaged fixture
cache hit/miss lines; cache hits still export, install, launch, and assert
logcat markers on device. Broader dynamic import package graphs remain
design-gated follow-ups.

Current GA promotion evaluation: `docs/nodejs/reports/ESM_EXPERIMENTAL_EVALUATION.md`.
Current v1.1 promotion evaluation: `docs/nodejs/releases/V1_1_ESM_PROMOTION.md`.

Phase 10 packages the same evidence as a first-class parity gate:

```powershell
$env:ANDROID_SERIAL="QV710AF65F"
.\gradlew.bat --console=plain :app:verifyNodePhase10RuntimeFreeze
.\gradlew.bat --console=plain :app:verifyNodePhase10PackagedParityHost
.\gradlew.bat --console=plain :app:verifyNodePackagedParityGate "-Pautojs.nodejs.test.deviceProfile=sony"
```

The host parity report checks the runtime descriptor version vector,
template-staleness fail-fast markers, packaged ESM/dynamic-import markers,
module graph/source-name evidence, and the APK fixture cache-key invalidators.
The device gate remains responsible for proving real APK export, install,
launch, logcat marker collection, and stale descriptor failure before user
script execution. The broad packaged full-smoke matrix treats mobile-runtime
shape probes such as `perf_hooks`, Web Performance globals, and WebCrypto HMAC
as diagnostic markers rather than hard parity requirements; focused gates cover
their promoted compatibility surface.

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

S8-34 startup snapshot support is a design gate only. There is no focused
device test because no snapshot generation or load path is enabled. The current
test expectation is that normal bootstrap remains the active and fallback path.
Future snapshot promotion must add fallback, stale/corrupt manifest, isolation,
packaged graph, compile-cache interaction, and startup/RSS matrix coverage
before any runtime behavior changes.

Current runtime prewarm process-only focused instrumentation:

```powershell
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeRuntimePrewarmPoolInstrumentationTest"
```

This suite verifies the S8-35 explicit process-only prewarm path. It checks that
prewarm starts the `:nodejs_embedded` service process without loading Node/V8,
without granting capabilities, and without loading user modules; that the next
one-shot script consumes the prewarmed process while still receiving a clean
cwd/env/bootstrap; and that an expired prewarm does not contaminate a later
execution.

Current Phase 8 performance matrix instrumentation:

```powershell
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodePerformanceMatrixInstrumentationTest"
.\gradlew.bat --console=plain :app:pullNodeEmbeddedPerformanceReports
```

This suite writes `autojs6-node-phase8-performance-matrix-v1` JSON reports. It
measures the supported `compile-cache-only` mode and the separate
`compile-cache-plus-process-prewarm` axis, and records the S8-36
`no-cache-no-snapshot`, `snapshot-only`, and `compile-cache-plus-snapshot`
modes as unavailable with policy reasons instead of adding temporary runtime
switches.

Current inspector design-gate focused instrumentation:

```powershell
$env:ANDROID_SERIAL='QV710AF65F'
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeInspectorDesignGateInstrumentationTest" "-Pautojs.nodejs.test.deviceProfile=sony"
```

This suite verifies the S8-37 disabled inspector boundary: the `inspector`
feature flag is high-risk, release-forbidden, and has no build, project, or
packaged APK override path; `require("inspector")`, `node:inspector`,
`require.resolve`, `process.getBuiltinModule`, and local
`node_modules/inspector` shadowing all remain denied; `process._debugProcess`,
`process.execArgv`, `process.allowedNodeEnvironmentFlags`, and
`process.features.inspector` stay inert; and `NODE_OPTIONS` /
`NODE_INSPECT_RESUME_ON_START` are filtered from script-visible `process.env`.
No endpoint, port binding, ADB forwarding target, or active-session
notification exists while the design gate remains disabled. See
`docs/nodejs/reports/phase8/INSPECTOR_DESIGN_GATE.md`.

Current inspector security-gate focused instrumentation:

```powershell
$env:ANDROID_SERIAL='QV710AF65F'
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeInspectorSecurityGateInstrumentationTest" "-Pautojs.nodejs.test.deviceProfile=sony"
```

This suite verifies the S8-40 disabled-state hardening: release, project, and
packaged metadata cannot enable inspector; Node startup/debugger env keys are
stripped before native startup and from script-visible `process.env`; hostile
`NODE_OPTIONS=--inspect` host/port attempts remain inert; checked ports are not
left listening after normal script stop or timeout; and no inspector profile
artifacts or project-sensitive marker data leak through captured results. See
`docs/nodejs/reports/phase8/INSPECTOR_SECURITY_GATE.md`.

S8-38 ADB forwarding and Chrome DevTools attach are currently deferred by the
disabled inspector gate. There is no `start inspector`, `retrieve port/url`,
`adb forward`, DevTools attach, or `stop inspector` test command because no
runtime endpoint exists. Future promotion must add positive debug-build attach
coverage for breakpoints, pause/resume, console, async stacks, source maps, ESM
source names, and lightweight TypeScript source names. See
`docs/nodejs/reports/phase8/DEVTOOLS_ADB_FORWARD_GATE.md`.

S8-39 CPU and heap profiling are currently deferred by the disabled inspector
gate. The runtime does not emit `.cpuprofile`, `.heapsnapshot`, allocation
sampling, or inspector-owned profiling exports. Future promotion must verify
app-private artifact storage, explicit export/pull, service-stop cleanup,
metadata redaction, and packaged release denial. See
`docs/nodejs/reports/phase8/INSPECTOR_PROFILING_GATE.md`.

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

Node 24.x patch upgrade feasibility report:

```powershell
.\gradlew.bat --console=plain :app:generateNode24PatchUpgradeFeasibilityReport
.\gradlew.bat --console=plain :app:generateNode24PatchUpgradeFeasibilityReport "-Pautojs.nodejs.patchTargetVersion=24.17.0" "-Pautojs.nodejs.patchCandidateJniLibs=D:\path\to\jniLibs" "-Pautojs.nodejs.patchCandidateHeaders=D:\path\to\include\node"
```

This writes `build/reports/nodejs/patch-upgrade/node24-patch-upgrade-feasibility.json` and `.md`. Without candidate
Android `libnode.so` artifacts and headers, the task succeeds as an informational report but records a `no-go` decision.
The default runtime must remain unchanged until the same fork/toolchain provides candidate artifacts for `arm64-v8a`,
`armeabi-v7a`, and `x86_64`, plus candidate compatibility, performance, CJS/ESM differential, and packaged APK evidence.
Optional evidence properties are:

- `autojs.nodejs.patchCandidateCompatibilityProfile`
- `autojs.nodejs.patchCandidatePerformanceReport`
- `autojs.nodejs.patchCandidateDifferentialReport`
- `autojs.nodejs.patchCandidatePackagedReport`
- `autojs.nodejs.patchFailOnNoGo=true`

Node 24.x dual-runtime A/B report and gate:

```powershell
.\gradlew.bat --console=plain :app:generateNodeDualRuntimeABReport
.\gradlew.bat --console=plain :app:verifyNodeDualRuntimeABGate "-Pautojs.nodejs.test.deviceProfile=sony"
```

The build-only selector is `autojs.nodejs.runtimeSlot` and accepts only `node24_5` and `node24_current`. User projects,
`project.json`, and packaged APK metadata cannot select arbitrary native runtimes. `node24_5` is the default. Selecting
`node24_current` requires explicit candidate `jniLibs` and headers through the patch-candidate properties above; otherwise
Gradle fails during configuration instead of silently packaging the default runtime. The A/B report compares the required
evidence slots for compatibility corpus, security corpus, packaged smoke, memory stress, and performance benchmark. The
report task is informational; `verifyNodeDualRuntimeABGate` fails until both runtime slots have complete matching evidence.

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
.\gradlew.bat --console=plain :app:verifyNodePhase8SecurityStressGate "-Pautojs.nodejs.test.deviceProfile=sony"
.\gradlew.bat --console=plain :app:verifyNodePhase8Gate "-Pautojs.nodejs.test.deviceProfile=sony"
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
.\gradlew.bat --console=plain :app:generateNodeFinalCapabilityTruth
.\gradlew.bat --console=plain :app:verifyNodeFinalFocusedGateTopology
.\gradlew.bat --console=plain :app:generateNodeFinalAdapterStatus
.\gradlew.bat --console=plain :app:generateNodeFinalCrashStatus
.\gradlew.bat --console=plain :app:generateNodeFinalIntegrationSummary
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
`verifyNodePhase8SecurityStressGate` runs the Phase 8 security/stress selector set on Sony/full profiles.
`verifyNodePhase8Gate` runs the Phase 8 aggregate capability gate, host tooling/report checks, and
`generateNodePhase8Summary`, which writes `app/build/reports/nodejs/phase8-summary.json`; see
[reports/phase8/README.md](reports/phase8/README.md).
`verifyNodePhase9NativeModernizationPlan` runs the Phase 9 Batch A host-side
native boundary, runtime adapter ABI/export, runtime build-plan, 16 KB ELF
alignment, runtime artifact manifest/integrity, distribution policy, and
runtime artifact aggregate checks. It also includes
`verifyNodePhase9PerformancePolicy`, which records compile-cache, startup
snapshot, process-prewarm, memory-pressure, and performance matrix policy
evidence for S9-15 through S9-20. `verifyNodePhase9InspectorPolicy` records the
S9-21 through S9-26 inspector, DevTools, profiling, diagnostic-report, and
security policy state while keeping the runtime inspector disabled.
`verifyNodePhase9IsolationPolicy` records the S9-27 through S9-33 isolation,
broker, process-pool, crash, resource-limit, multi-instance, and worker
promotion state. `verifyNodePhase9WasmWasiPolicy` records the S9-34 through
S9-40 WASM/WASI policy split between ready, partial, and deferred work.
`verifyNodePhase9PluginPolicy` records the S9-41 through S9-43 plugin package,
native service plugin, and plugin ABI/lifecycle policy split.
`verifyNodePhase9NativeCrashPipeline` records the S9-44 crash evidence and
offline symbolication policy. `verifyNodePhase9ChaosSecurityPolicy` records
the S9-45/S9-47 chaos-test and security-corpus policy split; see
[reports/phase9/README.md](reports/phase9/README.md).
`generateNodePhase9Summary` writes `build/reports/nodejs/phase9-summary.json`
and `.md` from the Phase 9 host reports, focused Sony device XML, example
catalog, real npm corpus, runtime comparison, page-size validation,
startup/prewarm performance delta, capability matrix, and remaining blockers.
Running `verifyNodePhase9Gate` finalizes with this summary automatically.
`verifyNodePhase10RuntimeFreeze` records the Phase 10 Node 24.5 freeze and
fails on `libnode.so`, default runtime slot, capability constant, or
moduleSources budget drift. `generateNodePhase10CapabilityTruth` writes
`build/reports/nodejs/phase10-capability-truth.json` and `.md` with
implemented, live-provider, packaged-supported, experimental, blocked, and
disabled labels. `verifyNodePhase10ProviderRegistryV2` records S10-11 Provider
Registry v2 metadata and Doctor wiring. `generateNodePhase10SkipAudit` records
unresolved skip/assume/ignore sites for packaged, live provider, worker,
long-running, WASI, Inspector, and device-requirement categories.
`verifyNodePhase10Gate` aggregates the first-wave host evidence, Provider
Registry v2, packaged parity, package-manager boundary fixtures, multi-instance
policy evidence, developer-experience evidence, crash/adapter cutover evidence,
and adapter v1 ABI/export checks without promoting Node 24.17 or blocked
capabilities. `verifyNodePhase10CrashAdapterCutover` writes
`phase10-crash-adapter-cutover.json` and a synthetic
`phase10-crash-evidence-bundle/manifest.json` for S10-47 through S10-52. It
records crash bundle fields, resource/timeline snapshot requirements,
`AutoJsNodeRuntimeApiV1` operation inventory, and adapter cutover blockers.
`verifyNodeCrashAdapterCutoverGate` pairs that report with
`verifyNodePhase9NativeCrashPipeline`, `verifyNodeRuntimeAdapterAbi`, and
`verifyNodeRuntimeAdapterExport`. Current Node 24.5 adapter lifecycle remains
`surface_only`, so the gate keeps `legacy_jni` as the default path.
`verifyNodePhase10SecurityCorpus` writes `phase10-security-corpus.json` and
`.md` for S10-53. `generateNodePhase10Summary` writes
`phase10-summary.json` and `.md` for S10-56, combining Phase 10 host reports,
the example catalog, real npm corpus manifest, and available focused device
XML. The summary requires at least 55 examples and 50 real npm fixtures.
Phase 11 final generation tasks write `final-capability-truth.json`,
`final-focused-gate-topology.json`, `final-adapter-gate.json`,
`final-crash-gate.json`, and `final-integration-summary.json` without failing
on open blockers. Strict final tasks such as `verifyNodeFinalCapabilityTruth`,
`verifyNodeFinalAdapterGate`, `verifyNodeFinalCrashGate`, and
`verifyNodeFinalIntegrationGate` are expected to fail until the required
internal blockers in `docs/nodejs/final/FINAL_BLOCKER_REGISTRY.json` are
closed. The final status vocabulary is only `completed` or `blocked`;
`completed_with_recorded_blockers` is not valid for required internal Phase 11
capabilities.
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
