# Node Runtime Release Checklist

Use this checklist for releases that use the external Node runtime plugin,
include Embedded Node, or support packaged Embedded Node apps.

## Build

- [ ] `:app:compileAppDebugKotlin`
- [ ] `:app:compileAppDebugAndroidTestKotlin`
- [ ] `:app:externalNativeBuildAppDebug`
- [ ] `:app:nativeNodeDependencyReportAppDebug`
- [ ] `:app:nativeNodeSymbolArchiveReport`
- [ ] `:app:embeddedNodeDoctorReport`
- [ ] `:app:verifyNodeResolverVisualizerFixtures`
- [ ] `:app:generateNodeProfileReleaseManifest`
- [ ] `:app:verifyNodePrGate`
- [ ] release/gate configuration rejects flavor/backend pairs outside `nodePlugin/plugin`, `nodeFull|nodeMini/embedded|pluginOptional`, and `rhinoOnly/rhino`
- [ ] `:app:verifyNodeR5RuntimePluginGate` on the live plugin acceptance device when the external runtime backend changes
- [ ] v1.1 release candidates use `:app:verifyNodeV11RcReadiness` with `autojs.nodejs.test.deviceProfile=v1_1_beta`
- [ ] release candidates use `:app:verifyNodeReleaseGate` with `autojs.nodejs.test.deviceProfile=sony` or `full`
- [ ] `:app:generateNodeNightlyReport` output attached when publishing nightly or release-candidate results
- [ ] fuzzer failures, if any, have seed JSON files under `build/reports/nodejs/fuzz-seeds/` and replay with `:app:replayNodeFuzzerSeed`
- [ ] rollback path reviewed against [ROLLBACK_PLAYBOOK.md](ROLLBACK_PLAYBOOK.md)

## Device Tests

- [ ] approved device serial selected with `ANDROID_SERIAL` or the matching Gradle property
- [ ] `autojs.nodejs.test.deviceProfile` selected according to [DEVICE_MATRIX.md](DEVICE_MATRIX.md)
- [ ] Sony XQ-AT72 gates use explicit serial `QV710AF65F`; OnePlus OPD2413 is not used for unattended current-branch gates
- [ ] `EmbeddedScriptMvpInstrumentationTest`
- [ ] `EmbeddedNodePackagedApkSmokeTest`
- [ ] `NodeEmbeddedLifecycleStressInstrumentationTest`
- [ ] `NodeAutoJsBridgeInstrumentationTest`
- [ ] `NodeBridgeConformanceInstrumentationTest`
- [ ] `NodeCompatCorpusInstrumentationTest`
- [ ] `NodeRealNpmCompatInstrumentationTest`
- [ ] `NodeSecurityRegressionInstrumentationTest`
- [ ] `NodeFsPromisesInstrumentationTest`
- [ ] `NodeStreamMvpInstrumentationTest`
- [ ] `NodeZlibBufferInstrumentationTest`
- [ ] `NodeCryptoExpansionInstrumentationTest`
- [ ] `NodeProcessSweepInstrumentationTest`
- [ ] `NodeOsPrivacyInstrumentationTest`
- [ ] `NodeNativeAddonPolicyInstrumentationTest`
- [ ] `NodeEnginesBridgeInstrumentationTest`
- [ ] `NodeAccessibilitySelectorBridgeInstrumentationTest`
- [ ] `NodeImageBridgeExpansionInstrumentationTest`
- [ ] `NodeClipboardBridgeInstrumentationTest`
- [ ] `NodeDeviceBridgeInstrumentationTest`
- [ ] `NodeShellBridgeInstrumentationTest`
- [ ] `NodeFetchBridgeInstrumentationTest`
- [ ] `NodeWorkerThreadsBootstrapInstrumentationTest`
- [ ] `NodeWorkerThreadsDesignGateInstrumentationTest`
- [ ] `NodeRawNetworkPolicyInstrumentationTest`
- [ ] `NodeWorkerThreadsExperimentalInstrumentationTest`
- [ ] `NodeJavaInteropBridgeInstrumentationTest`
- [ ] `NodeBridgeResourceRegistryInstrumentationTest`
- [ ] `NodeBridgeConcurrencyLimitsInstrumentationTest`
- [ ] `NodeRuntimeBudgetPolicyInstrumentationTest`
- [ ] `NodeBridgeRpcFuzzerInstrumentationTest`
- [ ] `NodeExampleProjectInstrumentationTest`
- [ ] `NodeRuntimePluginR5InstrumentationTest`
- [ ] timeline trace smoke verifies default-off behavior and required `embedded_script.timeline_json` events when opted in
- [ ] optional `NodeEmbeddedPerformanceInstrumentationTest` run recorded for lifecycle/native-load changes
- [ ] extended `NodeEmbeddedMemoryLeakStressInstrumentationTest` run recorded before release candidates that change lifecycle, bridge, fs, or image-handle behavior
- [ ] optional `NodeScreenCaptureRealDeviceSmokeInstrumentationTest` run recorded when MediaProjection, screen capture, or image-frame behavior changes
- [ ] live bridge smoke verifies JavaScript can observe provider results
- [ ] bridge smoke verifies `engines` snapshots, controlled `execScript`/`execScriptFile`, scoped path policy, spawn depth, and unsupported `execAutoFile`
- [ ] bridge smoke verifies `dialogs` fake results and stable Android provider rejection
- [ ] bridge smoke verifies `accessibility` fake results, shadowing protection, disabled status, and stable action rejection
- [ ] bridge smoke verifies `accessibility` selector descriptors, `findOne`/`findAll`, snapshot-only nodes, invalid descriptor rejection, and stable Android provider rejection
- [ ] bridge smoke verifies `media_projection`/`image` fake handles, shadowing protection, and stable Android provider rejection
- [ ] bridge smoke verifies `image`/`images` capture/read/save/clip/resize/find/recycle shape, scoped path rejection, idempotent recycle, and stable Android provider rejection
- [ ] bridge smoke verifies `clipboard` fake provider results, shadowing protection, permission tagging, and no `watch` API
- [ ] bridge smoke verifies `device` fake provider results, shadowing protection, sensitive field absence, permission tagging, and no `watch` API
- [ ] bridge smoke verifies `shell` fake provider results, shadowing protection, timeout/cwd/env policy, declared `shell`/`shell.root` capability behavior, explicit root permission tagging, and that `child_process` remains disabled
- [ ] bridge smoke verifies controlled `fetch` fake-provider GET/POST, timeout, abort, response budget, unsupported scheme, redirect limit, max concurrency, service-destroy cleanup, diagnostics counters, shadowing protection, and that raw `http`/`https`/`net`/`tls`/`dns` remain disabled
- [ ] bridge smoke verifies controlled `websocket` fake-provider connect/send/receive/close, timeout, message budget, max connection, service-destroy cleanup, diagnostics counters, shadowing protection, and that raw `net`/`tls` remain disabled
- [ ] bridge smoke verifies controlled `work_manager` schedule-once, schedule-periodic, cancel, list, scoped descriptor policy, packaged entry metadata serialization, Android quota diagnostics, WorkManager live scheduled-runner CJS/ESM execution, `lastRun`/`runHistory`, cancel paths, queued-fallback rejection, shadowing protection, and that `tasks` remains unavailable
- [ ] bridge conformance verifies stable module exports, Promise-returning bridge calls, provider error shape, capability rejection, handle methods, and zero pending calls after destroy
- [ ] bridge resource registry verifies leaked image/screen/websocket resource cleanup semantics, Java handle cleanup, `work_manager` registration cleanup, shell timeout cleanup, and `embedded_script.bridge_resource.*` diagnostics
- [ ] bridge concurrency limits verify pending-call flood rejection, image-handle quota, shell/network/WebSocket resource limits, accessibility request-rate limiting, safe project overrides, and ignored above-hard-max overrides
- [ ] runtime heartbeat/budget verifies stale bridge-call diagnostics, active registry/resource counts, safe runtime budget overrides, ignored above-hard-max overrides, provider quota rejection without leaks, and terminal status preservation
- [ ] bridge RPC fuzzer verifies malformed request parsing, unknown dispatch paths, fake-provider mode, malformed responses, service-destroy rejection, and zero pending calls after cleanup
- [ ] native addon policy verifies `.node` denial through relative require, `module.createRequire`, package `main`, package `exports`, package `imports`, and package subpath imports with `ERR_AUTOJS6_NATIVE_ADDON_DISABLED`
- [ ] worker gate verifies `worker_threads` stays disabled by default, cannot be shadowed, reports `experimentalFlags.workerThreads=false`, and records experimental native-availability preflight as either usable native Worker bootstrap or stable `ERR_AUTOJS6_WORKER_DISABLED`
- [ ] experimental worker smoke verifies pure-JS worker computation, `workerData`, `parentPort.postMessage`, `threadId`, `terminate()`, max-worker rejection, scoped path denial, and denied AutoJs bridge/raw network/fs/nested-worker access when native workers are available; otherwise the promotion gate is explicitly skipped, not passed as runnable support
- [ ] `:app:verifyNodeExamples` passes and generated example report matches `sample/nodejs/examples.json`
- [ ] `:app:verifyAutoJs6NodeTypeDeclarations` passes and `sample/nodejs/typescript-smoke` type-checks when `tsc` is available
- [ ] checked-in main-app examples `hello-node`, `cjs-package`, `fs-scoped`, `fs-promises`, `npm-pure-js-cjs`, `toast-app`, `accessibility-click-text`, `screenshot-find-image`, and `disabled-features-demo` still run from `sample/nodejs`
- [ ] R5 live preflight has no skip path: plugin is installed, enabled, authorized, host-version compatible, contract compatible, and ABI compatible for `arm64-v8a`, `armeabi-v7a`, and `x86_64`
- [ ] R5 gate covers required failure and exactly one optional embedded fallback after a proven pre-dispatch failure; post-dispatch loss is never replayed or routed to fallback
- [ ] R5 gate covers candidate/Binder cache reuse, in-flight Binder-death attribution as `ERR_AUTOJS6_NODE_PLUGIN_EXECUTION_LOST`, and one atomic reconnect shared by concurrent acquisitions
- [ ] R5 gate covers persistent process reuse with fresh per-execution state, single-active/zero-queue busy rejection, and active process-restart cancellation followed by recovery with a new PID
- [ ] R5 gate covers workspace archive transport v2: explicit inputs only, plugin-private mirror execution, output writeback, final deletion tombstone, and zero host-sandbox scans
- [ ] R5 gate records at least 30 runtime-warm samples for every supported ABI with p50 at most `250 ms`, p95 at most `400 ms`, candidate/session cache reuse, and warm bind time `0`

The completed 2026-07-18 R5 baseline is recorded in
[R5_ACCEPTANCE_SAMPLING.md](R5_ACCEPTANCE_SAMPLING.md): all three ABI runs
passed 8 / 8 cases with zero skipped tests. The 30-sample runtime-warm p50/p95
values were 231 / 254 ms for `arm64-v8a`, 240 / 265 ms for `armeabi-v7a`, and
182 / 212 ms for `x86_64`. A later release still reruns the unchecked items
above against its own immutable artifacts.

## External Runtime Plugin

- [ ] default main-app release records `releaseFlavor=nodePlugin` and `backend=plugin`
- [ ] `nodePlugin` APK contains no host `libnode.so` or `libautojs6-node.so`
- [ ] external service uses `org.autojs.permission.PLUGIN` and reports package, service, UID, signer SHA-256, authorization state, runtime slot, Node version, and process ABI
- [ ] negotiated runtime reports exact contract version, mandatory host capabilities including `scopedWorkspaceArchiveTransport`, dedicated persistent process, max concurrency `1`, queue capacity `0`, and cancellation mode `process_restart`
- [ ] required `plugin` mode fails closed when discovery, trust, bind, contract, or ABI validation fails
- [ ] `pluginOptional` is used only with `nodeFull` or `nodeMini` and falls back exactly once only when a pre-dispatch result has `fallback_safe=true`
- [ ] Binder response loss after dispatch returns `ERR_AUTOJS6_NODE_PLUGIN_EXECUTION_LOST` with `fallback_safe=false`, without automatic replay or embedded fallback
- [ ] concurrent admission rejection returns `ERR_AUTOJS6_NODE_PLUGIN_BUSY` without embedded fallback
- [ ] package add/remove/replace/change/data-clear and host enable/authorization/trust changes invalidate candidate and Binder caches
- [ ] Binder death retires the session; concurrent next acquisitions share one atomic reconnect and advance session generation once
- [ ] workspace archive v2 maps only explicit request inputs into the plugin-private mirror, commits outputs back with a final deletion tombstone, rejects partial process-restart snapshots, and never scans the host sandbox

## Packaged APK

- [ ] `:app:embeddedNodePackagingPreflightAppDebug`
- [ ] `:app:verifyNodeTemplateReproducibility`
- [ ] `:app:embeddedNodePackagedAppIntegrationDryRunAppDebug`
- [ ] `:app:embeddedNodePackagedSmokeHostVerify`
- [ ] `:app:embeddedNodePackagedApkOutputVerify` with `-Pautojs.embeddedNode.packagedApk=<apk>` when verifying a specific exported APK
- [ ] `:app:embeddedNodePackagedDiagnosticReport` with `-Pautojs.embeddedNode.packagedApk=<apk>` when gating a specific exported APK
- [ ] packaged smoke host aggregate includes success and diagnostics APK verifier reports
- [ ] packaged verifier reports `embedded.node.packaged.output.module_sources.ready=true` with count, total-byte, per-module-byte, and largest-module fields
- [ ] packaged `toast` bridge smoke passes and cannot be shadowed by `node_modules/toast`
- [ ] packaged `app` bridge smoke passes, reports the packaged app id, and cannot be shadowed by `node_modules/app`
- [ ] packaged `clipboard` bridge smoke passes and cannot be shadowed by `node_modules/clipboard`
- [ ] packaged `device` bridge smoke passes, keeps sensitive identifiers absent, declares `android.permission.VIBRATE`, and cannot be shadowed by `node_modules/device`
- [ ] packaged v1.1 bridge smoke passes for controlled `fetch`, OCR, `storage`/`storages`, UI, and `work_manager` fake transport with declared `node.permissions`, bridge-module shadowing protection, and zero pending bridge calls
- [ ] packaged `engines` bridge smoke passes after the inrt template carries live bridge metadata
- [ ] slim template APK refreshed when inrt bytecode or manifest changes
- [ ] template reproducibility report records `template.refresh.ready=true`, template APK SHA-256, normalized manifest SHA-256, and slim native-lib injection policy
- [ ] production `template.apk` contains no `libnode.so`, `libautojs6-node.so`, or `libc++_shared.so`
- [ ] full Embedded Node inrt test template generated through `:app:embeddedNodeRefreshFullInrtTemplateApk` when runtime-template testing needs native libraries
- [ ] `NativeNodeEmbeddedScriptService` exists in manifest
- [ ] `NativeNodeEmbeddedScriptService` exists in Dex
- [ ] service uses `android:process=":nodejs_embedded"`
- [ ] service uses `android:foregroundServiceType="specialUse"`
- [ ] specialUse subtype property exists
- [ ] `FOREGROUND_SERVICE` permission exists
- [ ] `FOREGROUND_SERVICE_SPECIAL_USE` permission exists
- [ ] packaged scheduled/background task receivers and permissions are absent unless the project explicitly opts in
- [ ] packaged `work_manager` registration requires live bridge metadata and is not accepted by queued fallback
- [ ] if packaged timed/boot/package tasks are enabled, manifest verifier reports the exact receivers, permissions, and foreground-service requirements
- [ ] if packaged controlled network or WebSocket is enabled, manifest verifier reports `android.permission.INTERNET`; if it is not enabled, normal builds keep `autojs.nodejs.network.experimental=false`
- [ ] if `node.permissions` / `autojs6.node.permissions` is present, missing bridge capabilities reject with `ERR_AUTOJS6_BRIDGE_CAPABILITY_NOT_DECLARED`
- [ ] Android bridge provider rejects supported module/method dispatch when the request omits required capabilities
- [ ] if `node.bridgeLimits` / `autojs6.node.bridgeLimits` is present, effective limits appear in `profile.bridgeLimits`, and unsafe overrides are ignored with warnings
- [ ] packaged diagnostic report lists declared bridge capabilities, required Android manifest permissions, missing permissions, and unused relevant permissions
- [ ] `network` capability maps to `android.permission.INTERNET`
- [ ] `screen_capture` capability maps to foreground-service media-projection manifest requirements

## Native Libraries

- [ ] selected release flavor is recorded: `nodePlugin`, `nodeFull`, `nodeMini`, or `rhinoOnly`
- [ ] `nodePlugin` uses no host Embedded Node ABI payload
- [ ] `nodeFull` uses `arm64-v8a,armeabi-v7a,x86_64`
- [ ] `nodeMini` records the exact `autojs.nodejs.releaseAbis` value
- [ ] `rhinoOnly` APK contains no `libnode.so` or `libautojs6-node.so`
- [ ] Node-marked scripts in Node-disabled builds fail clearly with `ERR_AUTOJS6_NODE_DISABLED` and are not silently treated as ordinary Rhino scripts
- [ ] any explicit legacy Rhino-backed Node compatibility diagnostic run is recorded in the release notes
- [ ] `nativeNodeAbiSizeReport` generated `app/build/reports/nodejs/abi-size-report.md` with APK size, packaged template size, per-ABI native payload, and `nodePlugin` / `nodeFull` / `nodeMini` / `rhinoOnly` comparison
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
- [ ] extended scoped fs fuzzer passes for path normalization, encoded-looking names, NUL, Windows-style separators, long paths, and symlink chains
- [ ] package exports/imports escape denial
- [ ] extended resolver fuzzer passes for package exports/imports maps and prints reproducible seeds on failure
- [ ] native addon denial
- [ ] static ESM policy error
- [ ] dynamic import policy error
- [ ] release build rejects release-forbidden experimental flags; ESM/dynamic import/network/worker/java interop experiments are validated only through debug experimental builds
- [ ] project/package metadata feature flag overrides are rejected, including unknown flags and `node.featureFlags` / `autojs6.node.featureFlags`
- [ ] dangerous process APIs denied
- [ ] canonical `error.autojs6Code` present for AutoJs6 policy denials
- [ ] network operations denied unless explicitly feature-gated
- [ ] controlled `fetch` live provider disabled in normal release builds unless explicitly testing a network experiment
- [ ] controlled `websocket` live provider disabled in normal release builds unless explicitly testing a network experiment
- [ ] raw Node `http`, `https`, `net`, `tls`, `dns`, and `dgram` authority remains denied even if controlled fetch or WebSocket is enabled, including `node:` aliases and `node_modules` shadow attempts
- [ ] `worker_threads` authority remains denied unless explicitly feature-gated and security-reviewed
- [ ] experimental workers, when explicitly tested, expose a smaller capability set than parent Node scripts and cannot access AutoJs bridge modules
- [ ] experimental worker resource stress, when explicitly tested, shows no active worker after completion cleanup and no unbounded RSS/thread growth
- [ ] direct WorkManager, AlarmManager, JobScheduler, and BroadcastReceiver authority remains denied to Node scripts
- [ ] `work_manager` registration accepts only embedded backend, scoped relative paths, bounded intervals, and boolean constraint descriptors; fired runs use only the WorkManager-owned `scheduled_runner` surface and cannot expose interactive UI or unrestricted background process authority
- [ ] direct Java/Android interop remains disabled unless `autojs.nodejs.javaInterop.experimental=true` is explicitly tested
- [ ] experimental Java interop, when explicitly tested, exposes only the documented allowlist and provider-owned opaque handles
- [ ] reflection, class loaders, raw `Context`, native library loading, and Java process execution remain unavailable to Node scripts
- [ ] inspector/debugger server remains disabled; future support requires `docs/nodejs/design/INSPECTOR.md` update and debug-build-only gate

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
- [ ] fixed real npm package corpus smoke
- [ ] real npm corpus has license notes, no install scripts, no native addons, and no ESM-only packages
- [ ] desktop Node differential report generated for fixed real npm corpus, including `build/reports/nodejs/v1.1-differential.md`
- [ ] modern core differential report generated at `build/reports/nodejs/differential/modern-core-differential.md`
- [ ] package manager policy remains vendored/build-time only; no runtime install, lifecycle scripts, native addons, or unchecked `.bin` execution
- [ ] `:app:bundleNodeDependencies` generated sanitized bundle manifest, SBOM-lite, size report, and rejection report
- [ ] `:app:verifyNodeDependencyBundlerFixtures` rejects unsafe native addon, node-gyp artifact, lifecycle script, `.bin`, npm artifact, executable, path-reference, and oversized fixtures
- [ ] dependency manifest records sanitized packaged APK `assets/project` layout

## Lifecycle

- [ ] normal script completes and service stops
- [ ] timeout returns stable error code
- [ ] timeout does not poison next run
- [ ] uncaught exception returns stable error shape
- [ ] unhandled rejection returns stable error shape
- [ ] stdout/stderr are captured and closed
- [ ] native handle diagnostics are available when debugging
- [ ] runtime timeline trace remains off by default and records service/native/bootstrap/stdout/pending/bridge/stop/drain/destroy events when opted in
- [ ] normal concurrent embedded Node starts queue and run after the active execution releases its process slot
- [ ] explicit `reject_when_busy` concurrent starts return `ERR_AUTOJS6_NODE_ENGINE_BUSY`, and a later run succeeds after the active execution finishes
- [ ] embedded execution queue supports queued cancellation and returns `ERR_AUTOJS6_NODE_QUEUE_FULL` when the queue is full
- [ ] interactive long-running mode remains explicit, foreground, user-stoppable, restart policy `never`, and unavailable to normal script/package launches unless their dedicated gates are implemented
- [ ] scheduled execution mode remains unavailable to direct script/package launches and is accepted only for WorkManager-owned scheduled runner executions
- [ ] embedded Node foreground-service start/type/timeout diagnostics are present, and Android timeout cleanup calls the immediate stop path
- [ ] external plugin cancellation after dispatch follows process-restart semantics and later execution reconnects with a new PID instead of reusing the cancelled process/session
- [ ] interactive status snapshots preserve runtime heartbeat/budget diagnostics, including stopped/timed-out terminal records
- [ ] interactive lifecycle checkpoints are JSON-only, size-limited, project-scoped, atomically persisted, explicitly restored, and never enable automatic restart
- [ ] long-running stress gate repeats interactive stop/checkpoint/recovery and provider destroy leaves no active resources, shell commands, WebSocket counters, or close errors

## Diagnostics

- [ ] `process.version` and `process.versions` captured in smoke
- [ ] native dependency report generated
- [ ] native symbol archive report generated
- [ ] release manifest generated at `app/build/reports/nodejs/release-manifest.json`
- [ ] release manifest Markdown generated at `app/build/reports/nodejs/release-manifest.md`
- [ ] release manifest records app version, git commit, release flavor, ABI list, native library hashes, template hash, compatibility profile hash, stable surface hash, and feature flag defaults
- [ ] Embedded Node doctor text and JSON reports generated under `app/build/reports/nodejs/doctor/`
- [ ] doctor report lists backend, expected Node version, feature flags, enabled/disabled modules, bridge/network capability status, native hashes, packaged status, last-error/lifecycle/resource-count availability, and dependency bundle status
- [ ] app UI exposes Developer options > Node.js > Node.js Doctor and can export text and JSON reports
- [ ] device Node Doctor report omits user script contents and environment values and redacts app-private paths
- [ ] runtime probe logs `libnode.so.sha256` and `libautojs6-node.so.sha256`
- [ ] logcat/tombstone or bugreport collection steps in `NATIVE_CRASH_DIAGNOSTICS.md` remain current
- [ ] optional performance report generated with `NodeEmbeddedPerformanceInstrumentationTest` and pulled by `:app:pullNodeEmbeddedPerformanceReports` for lifecycle/native-load changes
- [ ] memory leak stress report generated with `NodeEmbeddedMemoryLeakStressInstrumentationTest` for lifecycle, bridge, fs, or image-handle changes
- [ ] packaged verifier gives actionable failures
- [ ] `ERR_AUTOJS6_*` codes stable for documented negative paths
- [ ] `:app:verifyNodeErrorCatalog` passes and `build/reports/nodejs/error-catalog.json` has no missing, stale, or unregistered runtime codes
- [ ] scoped fs fuzzer failure diagnostics include base seed, path case, expected code, observed code, and summary JSON
- [ ] resolver trace remains off by default once implemented
- [ ] resolver visualizer fixture parses exports failure, imports failure, disabled builtin, scoped path escape, compact text, verbose text, and JSON diagnostics
- [ ] resolver fuzzer failure diagnostics include base seed, case seed, request, result record, and generated package.json
- [ ] runtime timeline trace is available only with `autojs.nodejs.timelineTrace=true` or `AUTOJS6_NODE_TIMELINE_TRACE=1`
- [ ] execution queue diagnostics report queue mode, queue status, wait time, and max queued executions
- [ ] plugin diagnostics report discovery/cache generation, bind/session reuse and generation, Binder death/reconnect counts, selected component identity, dispatch state, fallback safety, and admission state
- [ ] plugin workspace diagnostics report archive contract version, mapped/commit status, `legacy_directory_scan_ran=false`, input directory scan count `0`, plugin-private output scan count, and deleted-input tombstone count
- [ ] contract and ABI rejection use `ERR_AUTOJS6_NODE_PLUGIN_CONTRACT_MISMATCH`; unavailable, busy, and post-dispatch loss use their distinct cataloged codes
- [ ] bridge live diagnostics are present for main-app bridge smoke
- [ ] docs updated for any changed capability
- [ ] `docs/nodejs/FLAKE_POLICY.md` reviewed; only infrastructure/adb setup failures may be retried, and security, leak, packaged script, and runtime assertions are not hidden behind retries

## Documentation

- [ ] `docs/nodejs/QUICK_START.md`
- [ ] `docs/nodejs/INTEGRATION_PLAN.md`
- [ ] `docs/nodejs/COMPATIBILITY_PROFILE.md`
- [ ] `docs/nodejs/SECURITY_MODEL.md`
- [ ] `docs/nodejs/ERROR_CATALOG.md`
- [ ] `docs/nodejs/security/THREAT_MODEL_V1.md`
- [ ] `docs/nodejs/TESTING.md`
- [ ] `docs/nodejs/releases/NODE_PROFILE_V1_STABLE_SURFACE.md`
- [ ] `docs/nodejs/EXPERIMENTAL_PROMOTION_POLICY.md`
- [ ] `docs/nodejs/releases/NODE_PROFILE_V1_KNOWN_ISSUES.md`
- [ ] `docs/nodejs/releases/NODE_PROFILE_V1_GA.md`
- [ ] generated compatibility profile agrees with `NODE_PROFILE_V1_STABLE_SURFACE.md`
- [ ] no experimental feature is promoted without `EXPERIMENTAL_PROMOTION_POLICY.md` gates
- [ ] `docs/nodejs/MIGRATION_FROM_RHINO.md` when entry points, bridge modules, scoped fs, or packaged behavior changes in a user-visible way
- [ ] `docs/nodejs/COMPATIBILITY_NOTES_PRO_AUTOX.md` when AutoJs6 behavior diverges from or converges toward documented Pro/AutoX user-visible behavior
- [ ] `docs/nodejs/EXAMPLES.md` when examples or example smoke coverage changes
- [ ] `docs/nodejs/PERFORMANCE.md` when benchmark behavior or report schema changes
- [ ] `docs/nodejs/NATIVE_CRASH_DIAGNOSTICS.md` when native crash capture, symbol archive, or symbolication behavior changes
- [ ] `docs/nodejs/FLAKE_POLICY.md` when release-gate retry policy, flaky category definitions, or device-infrastructure handling changes
- [ ] `docs/nodejs/MAINTENANCE_PLAN.md` when GA cadence, versioning, deprecation, crash triage, or experimental review policy changes
- [ ] `docs/nodejs/design/WORK_MANAGER.md` when scheduled/background task behavior changes
- [ ] `docs/nodejs/design/BROADCAST_TASKS.md` when boot, package-event, startup, or custom broadcast task behavior changes
- [ ] `docs/nodejs/design/ESM.md` when ESM or dynamic import behavior changes
- [ ] `docs/nodejs/design/NETWORK.md` when network behavior, packaging permissions, or request policy changes
- [ ] `docs/nodejs/design/WORKER_THREADS.md` when worker behavior or worker policy changes
- [ ] `docs/nodejs/design/JAVA_INTEROP.md` when Java/Android interop, Rhino compatibility shims, or Java allowlist policy changes
- [ ] `docs/nodejs/design/RHINO_SHIM.md` when `require("rhino")`, old global API installation, or mixed Rhino/Node migration behavior changes
- [ ] `docs/nodejs/design/EXECUTION_QUEUE.md` when embedded execution queue behavior changes
- [ ] `docs/nodejs/design/RUNTIME_PLUGIN_BACKEND.md` when external runtime discovery, trust, contract, session, fallback, cancellation, or distribution behavior changes
- [ ] `docs/nodejs/design/LONG_RUNNING_SERVICE.md` when long-running service behavior, foreground service policy, checkpoint/restore behavior, or restart policy changes
- [ ] packaged APK user docs or examples if packaging behavior changed
