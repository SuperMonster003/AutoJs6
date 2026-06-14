# AutoJs6 Embedded Node Baseline After Task 49

Date: 2026-06-14

This report freezes the Safe Node Profile after the second implementation stage, Tasks 19-49. It records the release-candidate gate, capability surface, packaged APK state, performance and memory stress reports, compatibility and security corpus coverage, and native artifact hashes that should be kept with this baseline.

This is not a desktop Node.js compatibility claim. AutoJs6 still exposes a controlled Safe Node Profile with scoped filesystem access, restricted native/process/network capabilities, packaged APK diagnostics, and an asynchronous AutoJs bridge.

## Verification Gate

The post-49 release gate passed with an explicit packaged smoke APK:

```powershell
.\gradlew.bat --console=plain :app:verifyEmbeddedNodeReleaseCandidate "-Pautojs.embeddedNode.packagedApk=D:\idea-projects\AutoJs6\app\build\embedded-node-packaged-smoke\PackagedNodeSmoke_v1.0.0.apk" "-Pautojs.embeddedNode.expectedAbis=arm64-v8a"
```

Result:

- `BUILD SUCCESSFUL`
- `:app:verifyEmbeddedNodeDebug` passed.
- `:app:embeddedNodePackagedDiagnosticReport` passed and reported `Embedded Node.js packaged APK diagnostic ready: true`.
- `:app:embeddedNodePackagedSmokeHostVerify` passed and reported `embedded.node.packaged.smoke.aggregate.ready=true`.
- `:app:verifyEmbeddedNodePackagedDebug` passed.
- `:app:verifyEmbeddedNodeReleaseCandidate` passed.

Reports produced by this gate:

- `app/build/reports/embedded-node/packaging-preflight-appDebug.txt`
- `app/build/reports/embedded-node/packaged-diagnostic-report.txt`
- `app/build/reports/embedded-node/packaged-smoke-aggregate.txt`
- `app/build/reports/embedded-node/packaged-app-integration-dry-run-appDebug.txt`
- `app/build/reports/native-node/native-deps-appDebug.txt`
- `app/build/reports/native-node/native-symbol-archive-appDebug.txt`

The packaged diagnostic task intentionally requires `-Pautojs.embeddedNode.packagedApk=<apk>` for a real exported APK. A run without that property fails fast with an actionable report and should not be treated as a runtime regression.

## Runtime Identity

- Profile: `AutoJs6 Safe Node Profile`
- Expected embedded Node version: `v24.5.0`
- Engine version: `autojs6-node-profile-v1`
- Capability registry: `NodeRuntimeCapabilities`
- JavaScript profile module: `require("autojs6:profile")`
- Script process: `:nodejs_embedded`
- Backend switches preserved: `rhino`, `native`, `embedded`, `auto`
- Default backend: `embedded`
- Native one-shot backend: retained as a regression oracle
- Packaged APK target: first-class target, including encrypted Node entry scripts and template native library injection
- Release flavor property: `autojs.nodejs.releaseFlavor=nodeFull`
- Required Node ABIs for `nodeFull`: `arm64-v8a`, `armeabi-v7a`, `x86_64`
- Unsupported ABI: `x86` remains fail-fast for Embedded Node

## Stable Surface

Stable entry and project routing:

- `"nodejs";` and `"node";` directives
- `.node.js`
- `.cjs`
- `.mjs` as a Node backend route only
- Node project metadata through `project.json`
- package bin launch paths from Node project metadata
- encrypted script headers carrying the Node execution flag
- packaged APK Node project metadata and encrypted entry scripts

Stable CommonJS and npm subset:

- `require()`
- `module.require()`
- `module.exports` and `exports`
- JSON modules
- `node_modules` traversal
- scoped packages
- package `main`
- package `exports`
- package `imports`
- package self-reference
- dynamic CommonJS require within the documented profile
- `module.createRequire()` focused compatibility
- resolver trace through `AUTOJS6_NODE_RESOLVE_TRACE=1`

Stable filesystem policy:

- scoped `fs`
- scoped `fs/promises` limited surface
- callback, sync, and promise drain coverage
- scoped file URL helpers
- canonical path checks
- package exports/imports target containment
- denial for absolute path, NUL byte, `..` escape, `/proc`, `/sys`, `/dev`, symlink escape, and realpath escape

Stable low-risk and compatibility builtins include:

- `assert`, `assert/strict`
- `buffer`
- `console`
- `constants`
- `crypto` limited profile
- `events`
- `fs`, `fs/promises`
- `module` limited profile
- `os` redacted/privacy profile
- `path`, `path/posix`, `path/win32`
- `process` limited profile
- `punycode`
- `querystring`
- `stream` MVP, including selected `stream/promises`, `stream/consumers`, and `stream/web` behavior
- `string_decoder`
- `timers`, `timers/promises`
- `url`
- `util`, `util/types`
- `zlib` buffer and stream-focused limited profile

Stable bridge modules:

- `toast`
- `app`
- `engines`
- `dialogs`
- `accessibility` safe snapshot/selector surface
- `media_projection` MVP and denial paths
- `image`
- `images`
- `clipboard`
- `device`
- `shell` controlled command bridge; `child_process` remains disabled
- `fetch` controlled bridge wrapper; live network provider remains disabled by default

Stable packaging and release surfaces:

- packaged APK output verifier
- packaged diagnostic report
- packaged smoke host verifier
- packaged app integration dry run
- release flavor policy for `nodeFull`, `nodeMini`, and `rhinoOnly`
- native dependency report
- native symbol archive report
- checked-in example project suite
- Rhino migration guide
- Pro/AutoX compatibility notes

## Partial Surface

Partial means intentionally smaller than desktop Node.js, test-covered for targeted packages or denial semantics, and documented as not complete:

- `crypto`: `randomBytes`, hash, `randomUUID`, `createHmac`, `timingSafeEqual`, and limited WebCrypto boundary; high-risk asymmetric APIs remain absent.
- `fs/promises`: scoped and expanded, but still governed by AutoJs6 path policy and lifecycle drain rules.
- `stream`: MVP behavior for targeted pure-JS package compatibility, not complete desktop stream semantics.
- `zlib`: selected buffer, callback, promisified, and stream roundtrips; `zlib/promises` remains denied.
- `module`: enough for CommonJS compatibility and `createRequire()` tests, not unrestricted loader hooks.
- `os`: privacy-redacted device information.
- `process`: event helpers, warnings, `nextTick`, `exitCode`, scoped `chdir`; dangerous process APIs remain denied.
- `http`, `https`, `net`, `tls`, `dns`, `dns/promises`: recognized for stable denial and feature detection only; raw I/O remains disabled in normal builds.
- `readline`, `readline/promises`, `tty`, `v8`, `perf_hooks`, `diagnostics_channel`, and `async_hooks`: limited compatibility surfaces for targeted dependency behavior.

## Experimental Or Disabled Surface

Experimental flags are off in the post-49 baseline:

```properties
autojs.nodejs.esm.experimental=false
autojs.nodejs.network.experimental=false
autojs.nodejs.worker_threads.experimental=false
```

Disabled or design-gated by default:

- ESM execution
- dynamic import execution
- npm install and postinstall execution
- native `.node` addons
- unrestricted native addon loading
- unrestricted `child_process`
- unrestricted `worker_threads`
- unrestricted `http`, `https`, `net`, `tls`, `dns`, and `dgram`
- unrestricted `vm`
- inspector/debugger server
- raw Android or Java object access from Node scripts
- raw AccessibilityNodeInfo or native image pointer transfer across the bridge
- `process.binding`
- `process.dlopen`
- `process._linkedBinding`
- `process.getBuiltinModule`
- `process.exit`, `process.kill`, and related process termination or privilege APIs

The controlled `fetch` bridge is implemented as an AutoJs6 capability wrapper. Normal release builds keep the Android live network provider disabled and reject with stable `ERR_AUTOJS6_NETWORK_*` errors before live I/O.

## Packaged APK Status

Current packaged APK input:

- APK: `app/build/embedded-node-packaged-smoke/PackagedNodeSmoke_v1.0.0.apk`
- Package: `org.autojs6.packaged.node.smoke`
- Expected ABI property: `arm64-v8a`
- Release flavor: `nodeFull`
- Diagnostic ready: true
- Manifest inspected: true
- `INTERNET` permission declared: true
- `FOREGROUND_SERVICE` permission declared: true
- `FOREGROUND_SERVICE_SPECIAL_USE` permission declared: true
- `NativeNodeEmbeddedProbeService` declared in `:nodejs_embedded`: true
- `NativeNodeEmbeddedScriptService` declared in `:nodejs_embedded`: true
- Script service special-use FGS declaration and subtype property: true
- Main script is present and appears encrypted: true
- `moduleSources` budget ready: true
- Real npm corpus package and JavaScript entries present and encrypted: true

The inspected smoke APK is an arm64 packaged artifact. The appDebug and template preflight still verify the full `nodeFull` ABI policy: `arm64-v8a`, `armeabi-v7a`, and `x86_64`; `x86` remains absent by design.

Non-gating packaged marker note:

- The current inspected smoke marker set still reports the `packaged.node_modules_scope.*` markers as false in `packaged-diagnostic-report.txt`.
- The release gate still passes because the host smoke aggregate and required packaged readiness checks are true.
- Refresh the packaged smoke payload when node_modules scope markers are promoted to a hard packaged gate.

## Performance Baseline

Latest pulled performance report:

- Report: `app/build/reports/nodejs/perf/embedded-node-performance-1781429181600.json`
- Schema: `autojs6-node-performance-v1`
- Device: Sony XQ-AT72, Android 12, primary ABI `arm64-v8a`
- Service process cold run wall time: 1130 ms
- Warm run wall time: 1050 ms
- First script execution wall time: 132 ms
- Warm script execution wall time: 127 ms
- `libnode` load: 26 ms cold, 23 ms warm
- App RSS delta after first script: 9356 KB
- App RSS delta after destroy: 17540 KB
- App thread delta after destroy: -4
- App fd delta after destroy: 0

The benchmark is informational unless threshold arguments are passed as documented in `docs/nodejs/PERFORMANCE.md`.

## Memory Stress Baseline

Latest pulled memory leak stress report:

- Report: `app/build/reports/nodejs/perf/embedded-node-memory-leak-stress-1781431840758.json`
- Schema: `autojs6-node-memory-leak-stress-v1`
- Device: Sony XQ-AT72, Android 12, primary ABI `arm64-v8a`
- `stressIterations`: 100
- `imageIterations`: 50
- `faultIterations`: 10
- Scenario count: 7
- Scenarios: `simple:100`, `fsCallback:100`, `fsPromises:100`, `bridgeToastAppFake:100`, `imageFakeHandles:50`, `uncaughtException:10`, `timeout:10`
- Expected failures: 10 uncaught exception runs and 10 timeout runs
- Unexpected failures: 0
- Runaway failures: 0
- Report `failures`: empty

## Compatibility Corpus Baseline

The post-49 test matrix documented in `docs/nodejs/TESTING.md` covers:

- `EmbeddedScriptMvpInstrumentationTest`
- `NodeEmbeddedLifecycleStressInstrumentationTest`
- `NodeAutoJsBridgeInstrumentationTest`
- `NodeCompatCorpusInstrumentationTest`
- `NodeSecurityRegressionInstrumentationTest`
- `NodeFsPromisesInstrumentationTest`
- `NodeStreamMvpInstrumentationTest`
- `NodeZlibBufferInstrumentationTest`
- `NodeCryptoExpansionInstrumentationTest`
- `NodeProcessSweepInstrumentationTest`
- `NodeOsPrivacyInstrumentationTest`
- `NodeClipboardBridgeInstrumentationTest`
- `NodeDeviceBridgeInstrumentationTest`
- `NodeEnginesBridgeInstrumentationTest`
- `NodeAccessibilitySelectorBridgeInstrumentationTest`
- `NodeImageBridgeExpansionInstrumentationTest`
- `NodeShellBridgeInstrumentationTest`
- `NodeFetchBridgeInstrumentationTest`
- `NodeWorkerThreadsDesignGateInstrumentationTest`
- `NodeEmbeddedPerformanceInstrumentationTest`
- `NodeEmbeddedMemoryLeakStressInstrumentationTest`
- `NodeExampleProjectInstrumentationTest`
- `EmbeddedNodePackagedApkSmokeTest`

The compatibility corpus covers pure JavaScript CommonJS packages, deep dependencies, package `exports`, package `imports`, self-reference, JSON module caching, `util.promisify(fs)`, Buffer/path/util behavior, disabled-feature detection, and a moduleSources/preload-shaped fixture.

The checked-in example suite covers the `sample/nodejs/hello-node` and `sample/nodejs/toast-app` projects from androidTest assets.

## Security Corpus Baseline

The security regression matrix covers:

- dangerous process API denial
- disabled builtin denial
- network operation denial in normal builds
- scoped filesystem escape denial
- native addon extension denial
- package exports/imports escape denial
- canonical or documented stable AutoJs6 error codes
- `process.chdir` scoped absolute and escaping target rejection
- worker thread default denial and shadowing protection
- ESM and dynamic import default denial
- frozen `autojs6:profile` flags

The key security invariant remains: a builtin may be present for compatibility or stable denial, but high-risk I/O, process, worker, native addon, and unrestricted Android capabilities must stay behind explicit design gates.

## Native Artifact Hashes

Hashes are from `app/build/reports/native-node/native-symbol-archive-appDebug.txt` and `app/build/reports/native-node/native-deps-appDebug.txt`, generated during the successful Task 50 release-candidate run.

| ABI | Library | Source or merged sha256 | Stripped sha256 | Build ID |
| --- | --- | --- | --- | --- |
| `arm64-v8a` | `libnode.so` | `b1f9b6f95be74336e8b70801967c1dbbb5dac6b86053b9de7dd8c7b74dc81b85` | `edf81883fad5cb2e5756ac603cf2d1140e252599caf01eefaf218b5ad16aa34f` | missing |
| `arm64-v8a` | `libautojs6-node.so` | `d6bfe641647a1b188bf4b17a0b337fc122d9e13d162a7d505f5ee223d3626597` | `e9e1e2b4bed90363c38c3d99bbb660dba165af73388f6cb6a80e16b8e8474856` | `481202c117459d2e5deab4e2bdca77eb752d7e3d` |
| `arm64-v8a` | `libc++_shared.so` | `4e843755cda12ed65cd2b450be720b122d6657b24b690bf32de74fdc3f529447` | `69e517e645a6d38f7536d64108d245f5a8208a4a5cd43375b4906f60a60a86c4` | not recorded |
| `armeabi-v7a` | `libnode.so` | `ee50698e264ce78a12d450de6781bdd4b7df4e7a7546e93d1b96bac2b62cab63` | `137686e5381497e6682121c4a3739c06db638d6c493ccc82a84762fcada992cc` | missing |
| `armeabi-v7a` | `libautojs6-node.so` | `3693f7c464659cd6b3ee970f9a09bc2bde0608d3477e243431edb28122dd7d43` | `ff25eec7e96aa3d57d46ffdafbddf8174616f1b0e81d048c6ab27ede94b9cb59` | `a748dd1f171c8242e5fe0f7d099a83b8d067fd23` |
| `armeabi-v7a` | `libc++_shared.so` | `49b35da8db470f462802b526140fd95c4005408b8fb576878158fb890b26310c` | `588ad8b26a2583ef540920a267397ec434dc29be6c0221fa849387c0b3a168bd` | not recorded |
| `x86_64` | `libnode.so` | `a62031b38a467027dd5f98db8e91cdc8c31521380ee2dd8c70cd8afbaedc8e2b` | `20f73bb7d7814860bfc680d6b1e45141a0cb489324d77efbf43be34562f09fc3` | missing |
| `x86_64` | `libautojs6-node.so` | `585c370b0042951ed6701fe4b409cbbce7fe5174d5067fc23ae11ffd66b7ec0a` | `257b971dc2169c9e820b4f8038d8f03a7ca60602e4a8e54709046afb1a6961b5` | `09ce6715cc25a5b6a97532c8fe195c04cc008a3a` |
| `x86_64` | `libc++_shared.so` | `732a247cd490b36201892e98afc33abe726d5aff0498063e76954b4c39a54033` | `463414fe6451a91d05e10512c77d713a4beebb7d2641e48f209f6e3d2db5a73a` | not recorded |

The native dependency report confirms that appDebug packaging uses stripped native libraries in APK outputs. APK `libc++_shared.so` hashes are expected to match `stripped_native_libs`, not merged native libs, when stripping changes the file.

## Carry-Forward Rules

The next task group should preserve these invariants:

- Rhino compatibility stays intact.
- Backend switches stay available.
- The embedded backend remains default.
- The native one-shot backend stays available as a regression oracle.
- Packaged APK verification remains a release gate.
- Scoped filesystem enforcement remains mandatory.
- High-risk capabilities stay disabled unless a task explicitly opens them behind a documented design gate.
- All AutoJs bridge calls remain asynchronous from JavaScript perspective.
- Java/Kotlin/C++ callbacks into JavaScript remain marshalled onto the Node event loop thread.
- Every new feature needs documentation, positive coverage, negative/security coverage, and packaged impact review where relevant.
