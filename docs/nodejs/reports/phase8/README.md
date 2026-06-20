# Phase 8 Reports

Phase 8 begins with Node.js `v24.17.0` candidate validation while keeping
`v24.5.0` as the default runtime. The candidate is build-only through
`-Pautojs.nodejs.runtimeSlot=node24_current`; users and packaged projects cannot
select arbitrary native runtimes.

## Batch A Gates

Current Batch A report tasks:

```powershell
.\gradlew.bat --console=plain :app:generateNode2417NativeDiffReport
.\gradlew.bat --console=plain :app:generateNode2417SecurityProbeReport
.\gradlew.bat --console=plain :app:generateNode2417UpgradeGateReport
```

Generated outputs:

- `build/reports/nodejs/node-24.17-native-diff.json`
- `build/reports/nodejs/node-24.17-native-diff.md`
- `build/reports/nodejs/node-24.17-security-probes.json`
- `build/reports/nodejs/node-24.17-security-probes.md`
- `build/reports/nodejs/node-24.17-upgrade-gate.json`
- `build/reports/nodejs/node-24.17-upgrade-gate.md`

The upgrade gate also consumes:

- `build/reports/nodejs/patch-upgrade/node24-patch-upgrade-feasibility.json`
- `build/reports/nodejs/patch-upgrade/node-dual-runtime-ab-gate.json`

## Candidate Inputs

Candidate runs must provide:

- `-Pautojs.nodejs.patchCandidateJniLibs=<dir>` containing
  `arm64-v8a/libnode.so`, `armeabi-v7a/libnode.so`, and `x86_64/libnode.so`
- `-Pautojs.nodejs.patchCandidateHeaders=<dir>` containing Node headers
- matching compatibility, security, packaged, memory, and performance reports
  for the `node24_current` slot

`verifyNode2417UpgradeGate` additionally requires:

- `-Pautojs.nodejs.runtimeSlot=node24_current`
- `-Pautojs.nodejs.test.deviceProfile=sony`
- `-Pautojs.nodejs.network.experimental=true`
- `ANDROID_SERIAL=QV710AF65F`

## Current Status

As of the first Phase 8 Batch A implementation pass, the gate is expected to be
`blocked` because no Node `v24.17.0` Android `libnode.so` artifacts or candidate
device evidence are checked in. This is intentional: the reports define the
intake and promotion boundary without switching the default runtime.

The security probe source plan is present in
`NodeSecurityRegressionInstrumentationTest#phase8Node2417SecurityFixProbesStaySafe`.
It covers NUL hostname rejection, raw TLS/HTTP2 denial, credential redaction on
denied HTTPS URLs, WebCrypto quota rejection, and scoped FileHandle behavior.

## Batch B TypeScript

Current Batch B implementation status:

- `.ts`, `.mts`, and `.cts` route to Embedded Node with host-side lightweight
  type stripping before native execution.
- `.mts` is ESM, `.cts` is CommonJS, and `.ts` follows package `type`.
- `.tsx` is explicitly rejected with
  `ERR_AUTOJS6_TYPESCRIPT_UNSUPPORTED_EXTENSION`.
- Transform-required TypeScript syntax is rejected before launch with
  `ERR_UNSUPPORTED_TYPESCRIPT_SYNTAX` and diagnostics for source, syntax kind,
  line, and column.
- Injected `moduleSources` and runtime module sources are stripped before the
  request reaches native Node.
- Project wizard templates now cover CommonJS TypeScript, ESM TypeScript,
  automation, OCR, long-running task, and build-time-only TSX.
- Checked examples now include `typescript-cjs`, `typescript-esm`, and
  `packaged-typescript`.

Focused gates:

```powershell
.\gradlew.bat --console=plain :app:verifyNodeTypeScriptGate "-Pautojs.nodejs.test.deviceProfile=sony"
.\gradlew.bat --console=plain :app:verifyNodeProjectWizard
.\gradlew.bat --console=plain :app:verifyNodeExamples
```

The current packaged TypeScript evidence is project/moduleSources compatible.
Full exported APK installation smoke remains part of the broader packaged gate
before TypeScript can be treated as a release-complete Phase 8 capability.

## Batch C CJS require(ESM)

Current Batch C implementation status:

- CommonJS `require()` can synchronously load partial ESM graphs from `.mjs`,
  `.mts`, and `type=module` `.js` targets.
- The returned value is the ESM namespace object.
- Package condition selection preserves package key order. CommonJS activates
  `autojs6`, `require`, `node`, `default`; ESM activates `autojs6`, `import`,
  `node`, `default`; `development` and `production` remain inactive by
  default.
- Sync-required ESM graphs containing `await` or dynamic `import()` reject with
  `ERR_REQUIRE_ASYNC_MODULE`.
- Native addons, raw loader hooks, raw network, disabled builtins, scoped fs
  escapes, and unrestricted `node:module` authority remain denied.
- Static JSON ESM imports require `with { type: "json" }`.
- Dynamic JSON imports require `{ with: { type: "json" } }`.
- JSON ESM imports reuse the existing CommonJS JSON parser/cache; missing
  attributes, unknown attributes, and legacy `assert { type: "json" }` syntax
  reject with the partial ESM unsupported error.
- `import.meta.url`, `filename`, `dirname`, `main`, and `resolve()` are
  available for AutoJs6-managed ESM modules.
- `import.meta.resolve()` reuses the scoped ESM resolver, returns `file:` URLs
  for local/package targets and `node:` URLs for allowed builtins, and keeps
  disabled builtins denied.
- The S8-19 module conformance v2 corpus is checked in under
  `app/src/androidTest/assets/node_compat_corpus_v2/` and runs through
  `NodeCompatCorpusInstrumentationTest`. It covers `require(esm)`, async/TLA
  rejection, JSON import attributes, ESM/CJS cycles, `import.meta.resolve`, the
  `autojs6` package condition, `.mts`/`.cts` mixed graphs, and packaged
  moduleSources mixed graphs.

Focused gates:

```powershell
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeEsmExperimentalInstrumentationTest" "-Pautojs.nodejs.test.deviceProfile=sony"
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeCompatCorpusInstrumentationTest" "-Pautojs.nodejs.test.deviceProfile=sony"
.\gradlew.bat --console=plain :app:compileAppDebugKotlin :app:compileAppDebugAndroidTestKotlin
```

Packaged smoke expectations now cover both the positive packaged `.mjs`
`require()` path and the async-graph denial path.

Detailed corpus classification is recorded in
`docs/nodejs/reports/phase8/MODULE_CONFORMANCE_V2.md`.

## Batch D Web Platform And HTTP Client Prelude

Current Batch D implementation status:

- S8-20 is complete for the Web Platform globals needed by later controlled
  `node:http` and `node:https` client compatibility work.
- `Headers`, `Request`, `Response`, `FormData`, `Blob`, `File`, `URLPattern`,
  `DOMException`, `EventTarget`, `MessageEvent`, `CompressionStream`, and
  `DecompressionStream` are covered by a focused Sony instrumentation gate.
- The gate also verifies that default global `fetch()` remains denied when the
  controlled network capability is not enabled.
- S8-21 is complete for the controlled `node:http` / `node:https` client
  facade. `request()` and `get()` can dispatch through controlled fetch only
  when an enforced bridge manifest declares `network`.
- The HTTP facade covers request/response headers, status metadata, buffered
  request body chunks, response `data` / `end`, `pipe()`, timeout, abort, and
  explicit redirect follow through `redirect` or `maxRedirects`.
- S8-22 is complete for HTTPS-specific policy hardening. Normal certificate
  validation remains provider-owned, `rejectUnauthorized=false` and custom TLS
  context/client-certificate fields reject with
  `ERR_AUTOJS6_NETWORK_POLICY_DENIED`, and certificate pinning remains
  design-gated.
- S8-23 is complete for the focused `ClientRequest` / `IncomingMessage` stream
  semantics gate. Writable request body chunks, readable response
  `data`/`end`/`close`, `pipe()` backpressure smoke, request-level
  `AbortSignal`, `maxResponseBytes`, service-destroy cancellation, and pending
  cleanup now have a dedicated Sony regression test.
- S8-24 is complete for the limited Agent compatibility facade. `http.Agent`
  and `https.Agent` preserve safe shape fields such as `keepAlive`,
  `maxSockets`, `maxFreeSockets`, `defaultPort`, `protocol`, `getName()`, and
  `destroy()`, while request-level raw socket options and custom connector
  agents reject fail-closed.
- S8-25 is complete for the controlled Undici compatibility facade.
  `require("undici")` is reserved, `fetch()` and `request()` route through the
  controlled fetch bridge, Web Platform constructors are reused, and
  `Dispatcher` / `Agent` / `MockAgent` expose safe package-compatibility shapes
  without raw connector, socket, proxy, upgrade, or pipeline authority.
- S8-26 is complete for the offline HTTP npm compatibility corpus. Six
  npm-shaped fixtures cover packages using `http.get()`, `https.request()`,
  `IncomingMessage` stream consumption, form-style POST bodies, request-level
  `AbortSignal`, and safe Agent options through the controlled client facade.
- Default scripts still get the existing synchronous disabled error for
  `http.request()` / `https.request()`, and raw `net`/`tls`/`dns`/`dgram`,
  servers, `CONNECT`, and socket upgrades remain unavailable.

Focused gates:

```powershell
$env:ANDROID_SERIAL='QV710AF65F'
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeWebPlatformGlobalsInstrumentationTest" "-Pautojs.nodejs.test.deviceProfile=sony"
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeHttpClientFacadeInstrumentationTest" "-Pautojs.nodejs.test.deviceProfile=sony"
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeHttpsClientFacadeInstrumentationTest" "-Pautojs.nodejs.test.deviceProfile=sony"
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeHttpStreamSemanticsInstrumentationTest" "-Pautojs.nodejs.test.deviceProfile=sony"
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeHttpAgentFacadeInstrumentationTest" "-Pautojs.nodejs.test.deviceProfile=sony"
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeUndiciFacadeInstrumentationTest" "-Pautojs.nodejs.test.deviceProfile=sony"
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeHttpNpmCorpusInstrumentationTest" "-Pautojs.nodejs.test.deviceProfile=sony"
```

Detailed coverage is recorded in:

- `docs/nodejs/reports/phase8/WEB_PLATFORM_GLOBALS.md`
- `docs/nodejs/reports/phase8/HTTP_CLIENT_FACADE.md`
- `docs/nodejs/reports/phase8/HTTPS_CLIENT_FACADE.md`
- `docs/nodejs/reports/phase8/HTTP_STREAM_SEMANTICS.md`
- `docs/nodejs/reports/phase8/HTTP_AGENT_FACADE.md`
- `docs/nodejs/reports/phase8/UNDICI_FACADE.md`
- `docs/nodejs/reports/phase8/HTTP_NPM_CORPUS.md`

## Batch E Advanced Scoped FS

Current Batch E implementation status:

- S8-27 is complete for advanced scoped `FileHandle` behavior.
  `fs.promises.open()` returns execution-owned managed handles supporting
  read/write, `readFile()`, `writeFile()`, `appendFile()`, `truncate()`,
  `stat()`, `datasync()`, `sync()`, `close()`, and promise-level
  `readFile(fileHandle)` / `appendFile(fileHandle, data)` helpers.
- S8-28 is complete for callback and sync fd APIs. `fs.open()` /
  `openSync()`, `close()` / `closeSync()`, `read()` / `readSync()`,
  `write()` / `writeSync()`, `fstat()` / `fstatSync()`, and
  `ftruncate()` / `ftruncateSync()` are covered as scoped, execution-owned
  descriptor operations.
- S8-29 is complete for scoped `opendir()` / `Dir` / `Dirent` compatibility.
  `fs.opendir()`, `fs.opendirSync()`, and `fs.promises.opendir()` support
  managed directory reads, close paths, async iteration, buffer names, small
  recursive walks, closed-handle `ERR_DIR_CLOSED`, and scoped path denial.
- S8-30 is complete for scoped watcher compatibility. `fs.watch()`,
  `fs.promises.watch()`, `fs.watchFile()`, and `fs.unwatchFile()` support
  scoped path-like inputs, AbortSignal, close/ref/unref paths, polling stats,
  listener-specific unwatch, watcher quota, event-rate limiting, timeout
  cleanup, and documented Android event coalescing behavior.
- S8-31 is complete for additional safe scoped file operations. `fs.cp()` /
  `cpSync()`, `fs.mkdtemp()` / `mkdtempSync()`, `fs.statfs()` /
  `statfsSync()`, and the constrained `glob` subset cover callback, sync, and
  promise forms, path-like inputs, safe option shapes, scoped denial paths, and
  bounded policy checks for `link`, `chmod`, `chown`, `symlink`, and
  Android/device roots.
- S8-32 is complete for FileHandle / watcher lifecycle fuzzing. The new beta
  fuzzer covers close races, cancellation, service timeout cleanup, watched path
  rename, symlink replacement, fd reuse, long paths, Unicode paths, and watcher
  quota exhaustion.
- Relative escapes and absolute outside paths keep stable scoped-fs denial
  codes, while closed handles and non-owned numeric descriptors reject with
  `EBADF`.
- Timeout cleanup with an unclosed file handle, fd, or watcher is covered so a
  failed execution does not poison the next embedded Node run.
- Recursive directory walks are capped by the scoped fs recursive entry budget
  before they can become unbounded traversal.
- Recursive watch remains disabled until Android/provider behavior is reliable.

Focused gates:

```powershell
$env:ANDROID_SERIAL='QV710AF65F'
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeFileHandleAdvancedInstrumentationTest" "-Pautojs.nodejs.test.deviceProfile=sony"
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeFsPromisesInstrumentationTest,org.autojs.autojs.engine.NodeFileHandleAdvancedInstrumentationTest" "-Pautojs.nodejs.test.deviceProfile=sony"
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeFdApiInstrumentationTest" "-Pautojs.nodejs.test.deviceProfile=sony"
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeFsPromisesInstrumentationTest,org.autojs.autojs.engine.NodeFileHandleAdvancedInstrumentationTest,org.autojs.autojs.engine.NodeFdApiInstrumentationTest" "-Pautojs.nodejs.test.deviceProfile=sony"
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeOpendirInstrumentationTest" "-Pautojs.nodejs.test.deviceProfile=sony"
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeFsPromisesInstrumentationTest,org.autojs.autojs.engine.NodeFileHandleAdvancedInstrumentationTest,org.autojs.autojs.engine.NodeFdApiInstrumentationTest,org.autojs.autojs.engine.NodeOpendirInstrumentationTest" "-Pautojs.nodejs.test.deviceProfile=sony"
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeFsWatchInstrumentationTest" "-Pautojs.nodejs.test.deviceProfile=sony"
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeFsPromisesInstrumentationTest,org.autojs.autojs.engine.NodeFileHandleAdvancedInstrumentationTest,org.autojs.autojs.engine.NodeFdApiInstrumentationTest,org.autojs.autojs.engine.NodeOpendirInstrumentationTest,org.autojs.autojs.engine.NodeFsWatchInstrumentationTest" "-Pautojs.nodejs.test.deviceProfile=sony"
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeSafeFileOperationsInstrumentationTest" "-Pautojs.nodejs.test.deviceProfile=sony"
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeFsPromisesInstrumentationTest,org.autojs.autojs.engine.NodeFileHandleAdvancedInstrumentationTest,org.autojs.autojs.engine.NodeFdApiInstrumentationTest,org.autojs.autojs.engine.NodeOpendirInstrumentationTest,org.autojs.autojs.engine.NodeFsWatchInstrumentationTest,org.autojs.autojs.engine.NodeSafeFileOperationsInstrumentationTest" "-Pautojs.nodejs.test.deviceProfile=sony"
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeFileHandleWatcherFuzzerInstrumentationTest" "-Pautojs.nodejs.test.deviceProfile=sony" "-Pautojs.nodejs.fuzzerBudget=quick"
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeFileHandleWatcherFuzzerInstrumentationTest" "-Pautojs.nodejs.test.deviceProfile=sony" "-Pautojs.nodejs.fuzzerBudget=normal"
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeResolverFuzzerInstrumentationTest,org.autojs.autojs.engine.NodeScopedFsFuzzerInstrumentationTest,org.autojs.autojs.engine.NodeFileHandleWatcherFuzzerInstrumentationTest" "-Pautojs.nodejs.test.deviceProfile=sony" "-Pautojs.nodejs.fuzzerBudget=quick"
```

Detailed coverage is recorded in:

- `docs/nodejs/reports/phase8/FILEHANDLE_ADVANCED.md`
- `docs/nodejs/reports/phase8/FD_API.md`
- `docs/nodejs/reports/phase8/OPENDIR_DIR.md`
- `docs/nodejs/reports/phase8/FS_WATCH.md`
- `docs/nodejs/reports/phase8/SAFE_FILE_OPERATIONS.md`
- `docs/nodejs/reports/phase8/FILEHANDLE_WATCHER_FUZZER.md`

## Batch F Debugging And Performance

Current Batch F implementation status:

- S8-33 is complete for internal module compile-cache enablement and
  diagnostics.
- The embedded bootstrap enables Node's compile cache through the internal
  `node:module.enableCompileCache(cacheDir)` API before user code starts.
- CJS, ESM, and TypeScript-after-stripping executions all receive the same
  app-private, project-scoped compile-cache policy.
- The cache directory is under app-private code cache only:
  `<app code cache>/node-compile-cache/<runtime-key>/<project-key>`.
- Runtime, ABI, profile, feature-flag, loader-version, and module-content
  hashes are included in the diagnostics key material.
- Incompatible runtime or loader changes create a new runtime key; old cache
  directories are pruned by the app-private quota policy.
- Cache failures are fail-soft and are reported through
  `embedded_script.compile_cache.*` native values.
- `require("module")` / `require("node:module")` still expose only the safe
  subset; compile-cache enable/flush controls remain internal.
- User-visible env filters hide `AUTOJS6_NODE_COMPILE_CACHE_*`,
  `NODE_COMPILE_CACHE`, and `NODE_DISABLE_COMPILE_CACHE`.
- Doctor reports include compile-cache status, app-private location, quota, and
  hit/miss status notes.
- Custom AutoJs6 loader paths do not expose reliable Node hit/miss counters, so
  diagnostics report `hitMissStatus=unavailable_custom_loader` instead of
  fabricating counts.
- S8-34 is design-gated for startup snapshots. The current native lifecycle
  still passes `snapshot_data=nullptr` to Node isolate/isolate-data creation and
  loads a request-specific generated bootstrap source. No snapshot blob is
  generated, packaged, loaded, or requested.
- Startup snapshot promotion requires a future bootstrap split between an
  immutable snapshot prelude and a request install layer. The current fallback
  and active v1.1 behavior remains normal bootstrap.
- S8-35 is complete as a process-only runtime prewarm MVP. Callers may
  explicitly start one bounded `:nodejs_embedded` foreground service prewarm;
  no Node/V8 environment, bridge provider, user module, env, cwd, permission,
  or open resource is created during prewarm. The next script consumes the
  prewarm once and still runs through normal bootstrap.
- S8-36 is complete for the current performance matrix evidence. The focused
  matrix report measures `compile-cache-only` and the separate
  `compile-cache-plus-process-prewarm` axis, while recording
  `no-cache-no-snapshot`, `snapshot-only`, and `compile-cache-plus-snapshot` as
  unavailable with policy reasons. No runtime cache-disable switch or startup
  snapshot path is added.

Focused gates:

```powershell
$env:ANDROID_SERIAL='QV710AF65F'
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeModuleCompileCacheInstrumentationTest,org.autojs.autojs.ui.settings.NodeDoctorReportGeneratorInstrumentationTest" "-Pautojs.nodejs.test.deviceProfile=sony"
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeRuntimePrewarmPoolInstrumentationTest" "-Pautojs.nodejs.test.deviceProfile=sony"
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodePerformanceMatrixInstrumentationTest" "-Pautojs.nodejs.test.deviceProfile=sony"
.\gradlew.bat --console=plain :app:verifyNodeCompatibilityProfile
```

Detailed coverage is recorded in:

- `docs/nodejs/reports/phase8/MODULE_COMPILE_CACHE.md`
- `docs/nodejs/reports/phase8/BOOTSTRAP_STARTUP_SNAPSHOT.md`
- `docs/nodejs/reports/phase8/RUNTIME_PREWARM_POOL.md`
- `docs/nodejs/reports/phase8/PERFORMANCE_MATRIX.md`

## Batch G Inspector DevTools And Profiling

Current Batch G implementation status:

- S8-37 is complete as a debug-only inspector design gate in the current
  disabled state. No inspector endpoint is opened, no port is bound, and no
  ADB forwarding target exists. The focused gate proves that metadata cannot
  enable the `inspector` feature flag, script-visible inspector APIs remain
  disabled, `node_modules/inspector` cannot shadow the builtin policy,
  process debugging surfaces stay inert, and `NODE_OPTIONS` /
  `NODE_INSPECT_RESUME_ON_START` are filtered from script-visible
  `process.env`.
- S8-38 is deferred by the disabled inspector gate. There is no current
  `start inspector`, `retrieve port/url`, `adb forward`, DevTools attach, or
  `stop inspector` command because no runtime endpoint exists.
- S8-39 is deferred by the disabled inspector gate. The runtime does not
  generate `.cpuprofile`, `.heapsnapshot`, allocation sampling, or
  inspector-owned profiling exports.
- S8-40 is complete as a disabled-state inspector security gate. Release,
  project, and packaged metadata cannot enable inspector; request startup env
  strips Node debugger/cache keys before native initialization; hostile
  non-loopback and loopback `NODE_OPTIONS=--inspect` attempts remain inert and
  leave no checked listener after normal stop or timeout; and profile artifact
  / sensitive-metadata checks stay absent. The focused gate settles the current
  one-shot `:nodejs_embedded` process between attempts because the service does
  not guarantee repeated libnode/V8/libuv startups in one process.

Focused gates:

```powershell
$env:ANDROID_SERIAL='QV710AF65F'
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeInspectorDesignGateInstrumentationTest" "-Pautojs.nodejs.test.deviceProfile=sony"
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeInspectorSecurityGateInstrumentationTest" "-Pautojs.nodejs.test.deviceProfile=sony"
.\gradlew.bat --console=plain :app:verifyNodeCompatibilityProfile
```

Detailed coverage is recorded in:

- `docs/nodejs/reports/phase8/INSPECTOR_DESIGN_GATE.md`
- `docs/nodejs/reports/phase8/DEVTOOLS_ADB_FORWARD_GATE.md`
- `docs/nodejs/reports/phase8/INSPECTOR_PROFILING_GATE.md`
- `docs/nodejs/reports/phase8/INSPECTOR_SECURITY_GATE.md`

## Batch H Hardened Sandbox And Concurrency

Current Batch H implementation status:

- S8-41 is complete as a manifest and policy POC. `nodeSandboxed` now
  canonicalizes to `node_sandboxed`, but the mode is `design_gated`, has no
  supported launch surfaces, exposes no bridge providers, and rejects before
  user code. `NativeNodeSandboxedScriptService` is declared as a non-exported
  Android `isolatedProcess` service under the `:nodejs_sandboxed` process
  suffix, but no script route targets it until the broker exists.
- S8-42 is complete as a provider-side capability broker v2 policy/audit MVP.
  Every Android bridge provider request now passes through a versioned
  operation declaration and preflight layer before existing capability audit
  and dispatch. The broker records quota-bound per-call security audit rows
  and native diagnostics, but it is not the future cross-process Binder broker
  yet.
- S8-43 is complete as a default-disabled process-pool policy gate.
  `NativeNodeEmbeddedScriptServiceSlot1` and
  `NativeNodeEmbeddedScriptServiceSlot2` are now statically declared as
  reserved, non-exported embedded process slots, while
  `NodeEmbeddedProcessPoolPolicy` keeps the runtime `primary_only` with one
  effective active slot and publishes `embedded_script.process_pool.*`
  diagnostics. No default concurrent execution or reserved-slot routing is
  enabled yet.
- S8-44 worker partial promotion was rechecked and remains blocked by native
  `worker_threads` availability. The normal Sony worker gate passed 3/3; the
  experimental gate selected 30 worker-gate tests with 0 failures and 11
  expected native-availability skips, so runnable worker, stress, and packaged
  worker support cannot be promoted.

Focused gates:

```powershell
$env:ANDROID_SERIAL='QV710AF65F'
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeHardenedIsolatedRuntimePocInstrumentationTest" "-Pautojs.nodejs.test.deviceProfile=sony"
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeCapabilityBrokerV2InstrumentationTest" "-Pautojs.nodejs.test.deviceProfile=sony"
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeEmbeddedProcessPoolPolicyInstrumentationTest" "-Pautojs.nodejs.test.deviceProfile=sony"
.\gradlew.bat --console=plain :app:verifyNodeWorkerGate "-Pautojs.nodejs.test.deviceProfile=sony"
.\gradlew.bat --console=plain :app:verifyNodeWorkerGate "-Pautojs.nodejs.worker_threads.experimental=true" "-Pautojs.nodejs.test.deviceProfile=sony"
```

Detailed coverage is recorded in:

- `docs/nodejs/design/HARDENED_ISOLATED_RUNTIME.md`
- `docs/nodejs/reports/phase8/HARDENED_ISOLATED_RUNTIME_POC.md`
- `docs/nodejs/design/CAPABILITY_BROKER_V2.md`
- `docs/nodejs/reports/phase8/CAPABILITY_BROKER_V2.md`
- `docs/nodejs/design/PROCESS_POOL.md`
- `docs/nodejs/reports/phase8/PROCESS_POOL_POLICY_GATE.md`
- `docs/nodejs/reports/phase8/WORKER_PARTIAL_PROMOTION_RECHECK.md`

## Batch I npm Project Experience

Current Batch I implementation status:

- S8-45 is complete for the integrated dependency manager action.
  `:app:manageNodeDependencies` now consumes a lockfile project, runs
  host-side `npm ci` with lifecycle scripts disabled, validates and sanitizes
  the installed dependency tree, writes manifest/SBOM-lite/compatibility/size
  and rejection reports, updates the content-addressed cache, emits
  `dependency-manager-report.json`, and verifies the sanitized output is ready
  for packaged APK `assets/project`.
- Android runtime install remains unsupported. The manager is host-side only
  and records `packageLifecycleScriptsExecuted=false`.

Focused gates:

```powershell
.\gradlew.bat --console=plain :app:manageNodeDependencies
.\gradlew.bat --console=plain :app:verifyNodeDependencyManagerFixture
.\gradlew.bat --console=plain :app:verifyNodeDependencyBundlerFixtures
```

Detailed coverage is recorded in:

- `docs/nodejs/design/PACKAGE_MANAGER.md`
- `docs/nodejs/reports/phase8/DEPENDENCY_MANAGER.md`
