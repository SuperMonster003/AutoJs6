# AutoJs6 Node.js Compatibility Profile

This profile describes the current Safe Node surface. It is intentionally smaller than desktop Node.js and should be updated whenever runtime behavior changes.

## Runtime Identity

- Profile name: `AutoJs6 Safe Node Profile`
- Embedded Node baseline: Node.js `v24.5.0` artifacts are present in the current native tree.
- Execution model: embedded `libnode.so` plus AutoJs6 bootstrap and CommonJS loader.
- Process model: script service runs in `:nodejs_embedded`.
- Project model: file entry, Node project entry, and packaged APK entry are all supported paths.
- Kotlin capability registry: `NodeRuntimeCapabilities`
- JavaScript profile module: `require("autojs6:profile")`

Rhino migration guidance is documented in [MIGRATION_FROM_RHINO.md](MIGRATION_FROM_RHINO.md). Behavior-oriented notes for Auto.js Pro 9 and AutoX.js v7 users are documented in [COMPATIBILITY_NOTES_PRO_AUTOX.md](COMPATIBILITY_NOTES_PRO_AUTOX.md).

## Generated Profile Reports

The machine-readable and human-readable compatibility profile is generated from the Kotlin runtime capability registry, the bridge module registry, the JavaScript bootstrap allowlist/denylist, and the Gradle release ABI policy.

```powershell
.\gradlew.bat --console=plain :app:generateNodeCompatibilityProfile
```

Generated outputs:

- `build/reports/nodejs/compatibility-profile.json`
- `build/reports/nodejs/compatibility-profile.md`

The release/debug gate also runs:

```powershell
.\gradlew.bat --console=plain :app:verifyNodeCompatibilityProfile
```

`verifyNodeCompatibilityProfile` fails if this document omits registry entries from `NodeRuntimeCapabilities` or `NodeBridgeModules`.

## Feature Flag Registry

`NodeRuntimeCapabilities.featureFlags` is the single registry for experimental, partial, disabled, and unsupported Node capability flags. Normal builds keep high and critical security features disabled by default. Project-level and packaged-APK overrides are not allowed unless a future design-gated task explicitly changes the registry.

| Flag id | Gradle property | Default | Build override | project.json override | Packaged APK override | Security | Status |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `esm` | `autojs.nodejs.esm.experimental` | false | true | false | false | medium | experimental |
| `dynamic_import` | `autojs.nodejs.dynamicImport.experimental` | false | true | false | false | medium | experimental |
| `network` | `autojs.nodejs.network.experimental` | false | true | false | false | high | experimental |
| `raw_node_network_modules` | none | false | false | false | false | high | disabled |
| `websocket` | none | false | false | false | false | high | experimental |
| `worker_threads` | `autojs.nodejs.worker_threads.experimental` | false | true | false | false | high | experimental |
| `fs_streams` | none | true | false | false | false | medium | partial |
| `zlib_streams` | none | true | false | false | false | medium | partial |
| `vm` | none | false | false | false | false | high | disabled |
| `inspector` | none | false | false | false | false | high | disabled |
| `java_interop` | `autojs.nodejs.javaInterop.experimental` | false | true | false | false | critical | experimental |
| `native_addon` | none | false | false | false | false | critical | unsupported |
| `child_process` | none | false | false | false | false | high | disabled |

Runtime diagnostics expose the same state through `require("autojs6:profile").featureFlags`, `enabledExperimentalFlags`, and `deniedFeatureFlags`. The legacy `experimentalFlags` and `highRiskCapabilities` snapshots are retained for compatibility.

## Supported Entry Points

- `"nodejs";` directive
- `"node";` directive
- `.node.js`
- `.cjs`
- `.mjs` as a Node route only
- `project.json` with `type: "node"` or `projectType: "node"`
- package bin launch paths resolved from Node project metadata
- encrypted script headers carrying the Node execution flag

## Supported CommonJS Features

- `require()`
- `module.require()`
- `require("autojs6:profile")` for a read-only AutoJs6 profile descriptor
- `module.exports` and `exports`
- JSON modules
- local `.js`, `.cjs`, and `.json` modules
- `node_modules` traversal
- scoped packages
- package `main`
- package `exports`
- package `imports`
- package self-reference
- package condition order currently oriented around CommonJS: `require`, `node`, `default`
- module source budget checks
- optional resolver trace through `AUTOJS6_NODE_RESOLVE_TRACE=1`, written to stderr and disabled by default

Unsupported or rejected module source forms:

- native `.node` addon entries
- static ESM import execution
- dynamic import execution unless both `autojs.nodejs.esm.experimental` and `autojs.nodejs.dynamicImport.experimental` are enabled for an experimental run
- package export/import targets that escape the package root
- unsupported package export/import extensions

The reusable compatibility corpus lives under `app/src/androidTest/assets/node_compat_corpus/` and is executed by
`NodeCompatCorpusInstrumentationTest`. It covers pure JavaScript CommonJS package patterns that should remain stable across
future builtin and bridge work.

## Filesystem Profile

Supported:

- scoped `fs`
- scoped `fs/promises` limited surface; see `docs/nodejs/design/FS_PROMISES.md`
- sync and callback file APIs covered by the embedded test corpus
- path canonicalization checks
- scoped `url.fileURLToPath()` and `url.pathToFileURL()` wrappers; see `docs/nodejs/design/FILE_URL_POLICY.md`
- module source loading from the working directory and project root

Policy:

- no absolute path access from user scripts
- no NUL byte paths
- no `..` escape
- no `/proc`, `/sys`, or `/dev` access
- no realpath escape
- no symlink escape
- package exports/imports targets must remain inside their package root

## Supported and Limited Builtins

The bootstrap currently distinguishes fully allowed direct builtins from limited builtins. Both bare names and `node:` names should be treated consistently unless tests document a narrower behavior.

Direct allowlist currently includes:

- `path`
- `path/posix`
- `path/win32`
- `util`
- `util/types`
- `events`
- `async_hooks`
- `diagnostics_channel`
- `perf_hooks`

Limited builtin map currently includes:

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

Limited means API-compatible enough for the current tests and targeted pure-JS npm packages, not full desktop Node.js compatibility.

## `node:module` Safe Subset

`require("module")` and `require("node:module")` are partial limited builtins for CommonJS/npm compatibility. They expose only:

- `createRequire(filename)`
- `isBuiltin(name)`
- `builtinModules`
- `syncBuiltinESMExports()`

`createRequire(filename)` creates a CommonJS require function scoped to a file path inside the current working directory. The base path must be a file path, must not be absolute outside the scope, must not contain NUL bytes, must not be a `file:` URL, must not resolve through a symlink escape, and must not point to a directory. The created require function uses the existing AutoJs6 resolver, builtin allowlist/limited map, module cache, package `exports`/`imports` policy, and scoped filesystem checks.

`builtinModules` is a frozen list of AutoJs6 known safe or limited builtins, not a promise of unrestricted desktop Node behavior. Network-shaped limited modules can appear for feature detection and stable denial wrappers, while raw network I/O remains disabled unless a future design-gated task changes that boundary.

The following Node loader internals are not exposed:

- `Module`
- `_load`
- `_resolveFilename`
- `_compile`
- `wrap`
- `globalPaths`
- `register`
- `registerHooks`
- custom loader hooks

Design notes for the broadest limited builtins:

- `fs/promises`: `docs/nodejs/design/FS_PROMISES.md`
- file URL helpers: `docs/nodejs/design/FILE_URL_POLICY.md`
- source maps and stack traces: `docs/nodejs/design/SOURCE_MAPS.md`
- `stream`: `docs/nodejs/design/STREAM.md`
- `vm`: `docs/nodejs/design/VM.md`
- `zlib`: `docs/nodejs/design/ZLIB.md`
- scheduled/background tasks: `docs/nodejs/design/WORK_MANAGER.md`
- ESM: `docs/nodejs/design/ESM.md`
- network: `docs/nodejs/design/NETWORK.md`
- WebSocket: `docs/nodejs/design/WEBSOCKET.md`
- worker threads: `docs/nodejs/design/WORKER_THREADS.md`
- Java interop: `docs/nodejs/design/JAVA_INTEROP.md`

## Java Interop Experimental Allowlist

Direct Java and Android interop is disabled by default. Normal builds keep:

```properties
autojs.nodejs.javaInterop.experimental=false
```

When explicitly enabled for an experimental build or focused test run, `require("java")` exposes only an allowlist bridge:

- `java.type(className)`
- `java.callStatic(className, method, args)`
- `java.new(className, args)`

The MVP allowlist includes:

- `java.lang.Math` selected static numeric helpers
- `java.util.UUID` selected static UUID helpers
- `android.graphics.Rect` construction as an opaque provider-owned handle

Runtime, ProcessBuilder, System, ClassLoader, reflection, raw Android `Context`, filesystem, network, native library loading, and raw object graph traversal remain denied. `require("java")` and `require.resolve("java")` are intercepted before `node_modules` lookup, so a local `node_modules/java` package cannot shadow the disabled policy or the experimental bridge module.

## Disabled by Policy

These surfaces must stay disabled unless a future task explicitly opens them behind a design gate:

- unrestricted native addon loading
- unrestricted `child_process`
- unrestricted `worker_threads`
- unrestricted network sockets and servers
- unrestricted WebSocket sockets or HTTP upgrade authority
- unrestricted `http`, `https`, `net`, `tls`, `dns`, and `dgram`
- unrestricted `process.binding`
- unrestricted `process.dlopen`
- unrestricted `process._linkedBinding`
- unrestricted `process.getBuiltinModule`
- unrestricted `vm`
- unrestricted Java/Android interop
- inspector/debugger server
- npm install and postinstall execution

Registry-disabled builtin module names:

- `child_process`
- `cluster`
- `dgram`
- `domain`
- `inspector`
- `repl`
- `vm`
- `wasi`
- `worker_threads`
- `zlib/promises`

Network-related limited builtins may exist for feature detection and stable denial errors. That is not permission to perform network I/O.

`vm` is intentionally disabled, including local `node_modules/vm` shadowing, until a future design gate explicitly proves a safe subset. See `docs/nodejs/design/VM.md`.

## ESM Status

`.mjs` routes to Node so user intent is preserved. ESM execution is still not a default supported capability, but an experimental v1 path exists behind the explicit flag.

The explicit gate flag is:

```properties
autojs.nodejs.esm.experimental=false
```

Dynamic import has a separate gate and remains off when only ESM is enabled:

```properties
autojs.nodejs.dynamicImport.experimental=false
```

With the flag off, `.mjs` entry and resolution errors include:

```text
.mjs routes to Node backend, but ESM is not enabled in AutoJs6 Safe Node Profile.
```

Expected errors:

- static ESM path: `ERR_AUTOJS6_EMBEDDED_NODE_ESM_UNSUPPORTED`
- dynamic import path: legacy `code` `ERR_AUTOJS6_EMBEDDED_NODE_DYNAMIC_IMPORT_UNSUPPORTED`, canonical `autojs6Code` `ERR_AUTOJS6_DYNAMIC_IMPORT_UNSUPPORTED`
- CJS `require()` of ESM while the experimental flag is enabled: `ERR_AUTOJS6_REQUIRE_ESM_UNSUPPORTED`

Default `require("autojs6:profile")` reports `esm=false`, `dynamicImport=false`, `experimentalFlags.esm=false`, and `experimentalFlags.dynamicImport=false`.

With the flag enabled for an embedded script request, experimental ESM v1 supports:

- `.mjs` entry execution
- package `type=module` entry execution for `.js`
- local static imports of `.mjs` files inside `workingDirectory`
- default import from scoped CommonJS modules through the existing CommonJS loader
- supported builtin imports such as `node:path` and `node:events`
- bare package imports that resolve package `exports` with ESM condition order `import`, `node`, `default` and then load a CommonJS target
- package `imports` aliases with ESM condition order `import`, `node`, `default`
- package `exports`/`imports` targets that point to `.mjs` files inside the package root
- nested package imports and package self-reference under ESM mode
- simple named exports
- simple top-level await

CommonJS package condition order remains `require`, `node`, `default`.

Experimental ESM v1 still rejects `import.meta`, CJS `require()` of ESM, ESM JSON import assertions, custom loaders, network/URL imports, disabled builtins, and any import outside `workingDirectory`.

With both experimental flags enabled, dynamic `import()` is limited to local relative specifiers inside `workingDirectory`. It rejects package/builtin specifiers, absolute paths, `file:` URLs, network URLs, and path escapes with stable AutoJs6 errors.

Runtime diagnostics include `esmEnabled`, `esmEntry`, `esmModuleGraphRoot`, `esmModuleGraphSize`, `esmModuleGraphModules`, and `dynamicImportEnabled` in the embedded script result envelope.

See `docs/nodejs/design/ESM.md` for package `type`, CJS/ESM interop, top-level await, dynamic import, `import.meta.url`, package condition, moduleSources, encrypted graph, packaged APK, and stack trace decisions.

## Source Map Status

Embedded Node result diagnostics apply source map v1 mappings to uncaught script `errorStack` output when a script or module contains a `sourceMappingURL` comment. Supported map sources are inline `data:` URLs and external `.map` files inside the scoped working directory or injected moduleSources. External map paths use the same scoped filesystem boundary: no NUL, no path escape, no sensitive path, no symlink/realpath escape.

The mapper supports version 3 `sources` and `mappings` for line, column, and source-name remapping. It does not enable loader hooks, inspector, coverage, source-map consuming APIs, or automatic access to files outside the working directory.

## Network Status

Network has a controlled fetch MVP, but live Android network I/O is still disabled by default. The explicit gate flag is:

```properties
autojs.nodejs.network.experimental=false
```

With the flag off:

- global `fetch()` is an AutoJs6 controlled bridge wrapper, but the default Android provider rejects before performing I/O
- `require("fetch")` exposes the same controlled wrapper for CommonJS scripts
- `http`, `https`, `net`, `tls`, `dns`, and `dns/promises` operations reject with `ERR_AUTOJS6_NETWORK_DISABLED`
- `dgram` remains a disabled builtin
- raw sockets, servers, DNS queries, TLS sessions, datagrams, and unrestricted HTTP requests remain unavailable

Current `require("autojs6:profile")` reports `network=false` and `experimentalFlags.network=false`.

Current controlled fetch support includes `GET`/`POST`, headers, string or binary bodies, `timeoutMs`, `AbortController.signal`, `maxResponseBytes`, `maxRedirects`, and text/arrayBuffer/json response helpers. The live provider is backed by Android/OkHttp behind `BuildConfig.NODEJS_NETWORK_EXPERIMENTAL_ENABLED`; normal release builds keep that flag false. No `axios` builtin adapter is exposed.

The controlled request policy is bounded even when fake or experimental live providers are used:

- max concurrent user fetches: `4`
- default timeout: `30000` ms, hard cap `120000` ms
- default response budget: `1048576` bytes, hard cap `16777216` bytes
- default redirect budget: `5`, hard cap `10`
- allowed URL schemes: `http:` and `https:` only
- `file:`, `content:`, `about:`, `data:`, malformed URLs, and redirect targets outside `http:`/`https:` are rejected before raw network authority is reached

Network-specific errors include:

- `ERR_AUTOJS6_NETWORK_DISABLED`
- `ERR_AUTOJS6_NETWORK_ABORTED`
- `ERR_AUTOJS6_NETWORK_TIMEOUT`
- `ERR_AUTOJS6_NETWORK_RESPONSE_TOO_LARGE`
- `ERR_AUTOJS6_NETWORK_INVALID_URL`
- `ERR_AUTOJS6_NETWORK_PERMISSION_DENIED`
- `ERR_AUTOJS6_NETWORK_POLICY_DENIED`

See `docs/nodejs/design/NETWORK.md` for INTERNET permission, packaged APK manifest diagnostics, timeout, abort, body budget, TLS, proxy, background behavior, and service-destroy cancellation policy.

## WebSocket Status

Controlled WebSocket support has an experimental MVP, but live Android network I/O remains disabled in normal builds. Current `require("autojs6:profile")` reports:

- `featureFlags.websocket.enabled=false`
- `featureFlags.websocket.status="experimental"`
- `highRiskCapabilities.webSocket=false`

`require("websocket")` is reserved as a controlled AutoJs6 module and cannot be shadowed by `node_modules/websocket`. It exposes:

- `connect(url, options?): Promise<WebSocketConnection>`
- `connection.send(data, options?): Promise<void>`
- `connection.close(code?, reason?, options?): Promise<void>`
- `connection.on("open" | "message" | "close" | "error", listener)`
- `connection.off("open" | "message" | "close" | "error", listener)`
- `policy`
- `diagnostics()`

The MVP is provider-backed through the AutoJs6 bridge and uses bounded policy: max 2 active connections, `ws:`/`wss:` only, default timeout `10000` ms, default message budget `65536` bytes, and bounded queued events. The Android provider is backed by OkHttp only when `BuildConfig.NODEJS_NETWORK_EXPERIMENTAL_ENABLED=true`; normal builds reject with `ERR_AUTOJS6_NETWORK_DISABLED`. Fake-provider tests cover connect, send, receive, close, timeout, max-connection denial, message-size denial, service-destroy rejection, diagnostics, and raw `net`/`tls` denial.

Raw `net`, `tls`, and HTTP upgrade authority remains denied, so userland packages cannot open sockets through desktop Node APIs. The module boundary is documented in `docs/nodejs/design/WEBSOCKET.md`.

## Worker Threads Status

`worker_threads` has an experimental pure-JS MVP and remains disabled by default. The explicit gate flag is:

```properties
autojs.nodejs.worker_threads.experimental=false
```

With the flag off:

- `require("worker_threads")` rejects with disabled-builtin error shape
- `require("node:worker_threads")` rejects with disabled-builtin error shape
- `require.resolve("worker_threads")` rejects before a local `node_modules/worker_threads` package can shadow the builtin
- no worker can access AutoJs bridge modules, Android Context, accessibility, media projection, image handles, shell, engines, raw network, child process, native addon, `vm`, inspector, or unrestricted filesystem APIs

Current `require("autojs6:profile")` reports `workerThreads=false`, `highRiskCapabilities.workerThreads=false`, and `experimentalFlags.workerThreads=false`.

When the experimental request flag is enabled, `require("worker_threads")` and `require("node:worker_threads")` expose a limited module for local CommonJS worker scripts inside `workingDirectory`:

- `new Worker("./worker.js", { workerData })`
- `worker.on("message" | "error" | "exit" | "online", listener)`
- `worker.postMessage(value)`
- `worker.terminate(): Promise<number>`
- `isMainThread`
- `parentPort`
- `workerData`
- `policy`
- `diagnostics()`

The MVP loads worker source through the parent scoped resolver, then executes it in a native Node Worker with a worker-local restricted `require`. Worker-local `require("worker_threads")` exposes only `parentPort`, `workerData`, `isMainThread`, `threadId`, and a disabled nested `Worker`. Worker-local `require` denies AutoJs bridge modules, `autojs6:bridge`, `fs`, raw network, `shell`, `child_process`, native addons, `vm`, inspector, and nested workers. The first policy allows one active worker, a `5000` ms startup timeout, and safe pure-JS builtins only.

Worker instances are tracked per execution. Parent success/failure cleanup performs best-effort termination of any active worker before writing the result envelope, and exposes pending-worker and cleanup counters through native diagnostics. See `docs/nodejs/design/WORKER_THREADS.md` for worker count, timeout, memory, packaged APK, bridge-denial, and lifecycle policy.

## Process Profile

Supported or limited:

- `process.version`
- `process.versions`
- `process.argv`
- `process.env`
- `process.cwd()`
- controlled `process.chdir()` for project bootstrap use
- `process.nextTick`; tests lock its ordering before Promise/`queueMicrotask` callbacks and before timer callbacks
- warning and event-emitter style process APIs where covered by tests

Disabled or controlled:

- `process.exit`
- `process.abort`
- `process.kill`
- `process.reallyExit`
- `process.binding`
- `process._linkedBinding`
- `process.dlopen`
- privilege-changing POSIX APIs
- report writing

Scripts should use AutoJs6 lifecycle controls instead of forcing process exit.

## Crypto Profile

Supported or limited:

- `crypto.randomBytes`
- `crypto.randomFill` / `crypto.randomFillSync`
- `crypto.randomInt`
- `crypto.randomUUID`
- `crypto.createHash` for the documented hash allowlist
- `crypto.createHmac`
- `crypto.createSecretKey` and secret `KeyObject`
- `crypto.timingSafeEqual`
- bounded PBKDF2, scrypt, and HKDF helpers where covered by tests
- limited `crypto.webcrypto` / `crypto.subtle` for random values, digest, and HMAC key/sign/verify flows

Disabled or absent:

- asymmetric key creation/import/export APIs such as `createPrivateKey`, `createPublicKey`, and `generateKeyPair`
- signing and verification streams such as `createSign` and `createVerify`
- certificate and X.509 APIs
- native key-store or Android keystore access

The original Task 29 text expected `webcrypto` to stay absent, but the checked-in runtime already exposes a limited WebCrypto surface with regression coverage. Future work should narrow or expand that surface only with an explicit compatibility and security review.

## OS Privacy Profile

The `os` module is present for npm compatibility but must not expose real device fingerprinting data. Privacy-sensitive APIs return stable redacted values instead of raw host values:

- `os.networkInterfaces()` returns a frozen empty object.
- `os.userInfo()` returns the synthetic `autojs6` user and scoped cwd-backed home directory.
- `os.cpus()` returns a single synthetic CPU entry.
- `os.freemem()` and `os.totalmem()` return fixed coarse values.
- `os.loadavg()` returns `[0, 0, 0]`.
- `os.hostname()` returns `localhost`.
- `os.tmpdir()` and `os.homedir()` return the scoped working directory.

`os.availableParallelism()` may return an approximate positive integer. `os.constants` exposes frozen safe constant groups. `os.setPriority()` remains disabled.

## Packaged APK Profile

Packaged Node APKs require:

- native libraries for each selected ABI: `libnode.so`, `libautojs6-node.so`, `libc++_shared.so`
- `NativeNodeEmbeddedScriptService` class and manifest entry
- `android:process=":nodejs_embedded"`
- `android:foregroundServiceType="specialUse"` for the embedded Node service
- `android.permission.FOREGROUND_SERVICE`
- `android.permission.FOREGROUND_SERVICE_SPECIAL_USE`
- `android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE`
- Node project metadata and module source entries within current budgets
- packaged smoke coverage for the current user-facing `toast`, `app`, `clipboard`, and `device` bridge modules

Release packaging profile:

- `nodeFull`: default Embedded Node profile; requires `arm64-v8a`, `armeabi-v7a`, and `x86_64`
- `nodeMini`: Embedded Node profile with an explicit smaller ABI set, defaulting to `arm64-v8a`
- `rhinoOnly`: no Embedded Node runtime native libraries are packaged

See [RELEASE_FLAVORS.md](design/RELEASE_FLAVORS.md) for Gradle properties and validation rules.

Checked-in examples for the current profile live under `sample/nodejs/`; see [EXAMPLES.md](EXAMPLES.md).

Supported ABI policy:

- `arm64-v8a`: primary release target
- `armeabi-v7a`: supported when native libs and smoke tests pass
- `x86_64`: debug/emulator target
- `x86`: fail-fast until `libnode.so` exists for x86

## AutoJs API Bridge Status

The current safe profile does not expose unrestricted Android or AutoJs host objects to Node scripts.

Current foundation:

- Kotlin protocol data classes: `NodeBridgeRequest`, `NodeBridgeResponse`, `NodeBridgeError`
- Kotlin module registry: `NodeBridgeModules`
- fakeable provider contract: `NodeBridgeCapabilityProvider`
- internal JavaScript client: `require("autojs6:bridge")`
- private file-backed live transport in the current embedded service
- queued host transport fallback for older packaged templates and one-way compatibility paths
- bridge requests require non-empty `id`, `module`, and `method`; `args` must be a JSON array, `permissions` must be a string array, and `timeoutMs` must be a positive number
- malformed live bridge requests reject with `ERR_AUTOJS6_BRIDGE_INVALID_REQUEST` when a response id can be recovered
- provider callbacks are accepted at most once per bridge request; duplicate callbacks are ignored
- synchronous provider dispatch exceptions are converted to bridge failure responses with stable module and method metadata
- JS pending bridge calls reject on timeout with `ERR_AUTOJS6_BRIDGE_TIMEOUT`
- JS pending bridge calls reject on destroy with `ERR_AUTOJS6_BRIDGE_PROCESS_DEAD`

Current user-facing bridge modules:

- `toast`
  - `showToast(message, options?): Promise<void>`
  - `toast(message, options?): Promise<void>`
  - options: `{ duration?: "short" | "long", log?: boolean, timeoutMs?: number }`
- `app`
  - `packageName: string`
  - `versionName: string`
  - `versionCode: number`
  - `launchPackage(packageName, options?): Promise<boolean>`
  - `launchApp(name, options?): Promise<boolean>`
  - `openAppSetting(packageName?, options?): Promise<void>`
  - `startActivity(intentOptions, options?): Promise<void>`
- `engines`
  - `myEngine(): EngineInfo`
  - `all(options?): Promise<EngineInfo[]>`
  - `stopAll(options?): Promise<number>`
  - `stopSelf(options?): Promise<boolean>`
  - `execScript(name, script, options): Promise<ExecutionInfo>`
  - `execScriptFile(path, options): Promise<ExecutionInfo>`
  - current `all()` result is limited to the current embedded Node execution snapshot
  - current `stopAll()` returns `0` until a main-process engine provider is connected
  - current `stopSelf()` acknowledges the request; forceful embedded cancellation remains lifecycle-controller work
  - `execScript()` and `execScriptFile()` dispatch through a main-process bridge service and return a serialized execution snapshot, not a raw `ScriptExecution`
  - `options.timeoutMs` is required for script spawning
  - `cwd` and `execScriptFile(path)` are scoped to the current working directory and reject absolute paths, NUL bytes, `..`, and realpath escapes
  - `options.arguments` accepts JSON primitive values only
  - recursive spawning is limited with `spawnDepth`; nested embedded Node child scripts are rejected until multi-instance embedded lifecycle control is available
- `dialogs`
  - `alert(title, message?, options?): Promise<void>`
  - `confirm(title, message?, options?): Promise<boolean>`
  - `input(title, prefill?, options?): Promise<string | null>`
  - `prompt(title, prefill?, options?): Promise<string | null>`
  - `select(title, items, options?): Promise<number>`
  - current JavaScript API and bridge protocol are stable
  - current Android provider rejects with `ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED` until a UI capability provider is connected
- `accessibility`
  - `text(value): Selector`
  - `desc(value): Selector`
  - `id(value): Selector`
  - `className(value): Selector`
  - `textContains(value): Selector`
  - `descContains(value): Selector`
  - `isEnabled(options?): Promise<boolean>`
  - `ensureEnabled(options?): Promise<boolean>`
  - `click(x, y, options?): Promise<boolean>`
  - `click(selector, options?): Promise<boolean>`
  - `back(options?): Promise<boolean>`
  - `home(options?): Promise<boolean>`
  - `recentApps(options?): Promise<boolean>`
  - `findByText(text, options?): Promise<UiNode | null>`
  - `clickText(text, options?): Promise<boolean>`
  - `findOne(selector, options?): Promise<UiNode | null>`
  - `findAll(selector, options?): Promise<readonly UiNode[]>`
  - selector descriptors are frozen plain JSON values such as `{ type: "autojs6.accessibility.selector", version: 1, text: "OK" }`
  - returned `UiNode` values are frozen JSON snapshots only; no live `AccessibilityNodeInfo`, `UiObject`, or selector object crosses into Node
  - current JavaScript API, fake-provider tests, and bridge protocol are stable
  - current Android provider returns `false` for `isEnabled` and `ensureEnabled`
  - current Android provider rejects action and query methods with `ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED` until a safe accessibility capability provider is connected
- `media_projection`
  - `requestScreenCapture(options?): Promise<ScreenCapturer>`
  - `ScreenCapturer.nextImage(options?): Promise<Image | null>`
  - `ScreenCapturer.stop(options?): Promise<void>`
  - current JavaScript API, fake-provider tests, and bridge protocol are stable
  - current Android provider rejects with `ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED` until a MediaProjection capability provider is connected
- `image`
  - `captureScreen(options?): Promise<Image | null>`
  - `readImage(path, options?): Promise<Image | null>`
  - `saveImage(image, path, options?): Promise<void>`
  - `clip(image, x, y, w, h, options?): Promise<Image | null>`
  - `resize(image, w, h, options?): Promise<Image | null>`
  - `findImage(source, template, options?): Promise<Point | null>`
  - `recycle(image, options?): Promise<void>`
  - `images` is also reserved as an AutoJs-style alias with the same API shape
  - `captureScreen()` requests an already-granted screen-capture path through the future MediaProjection-backed image provider; it must not prompt directly from the Node service
  - `readImage()` and `saveImage()` accept scoped relative paths only; absolute paths, NUL bytes, and `..` escapes are rejected before bridge dispatch
  - `clip()` and `resize()` return new opaque image handles
  - repeated `recycle()` calls for the same normalized handle are safe from JavaScript and dispatch at most once per module instance
  - current JavaScript API, fake-provider tests, and bridge protocol are stable
  - current Android provider rejects with `ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED` until an image capability provider is connected
- `clipboard`
  - `getText(options?): Promise<string>`
  - `setText(text, options?): Promise<void>`
  - `hasText(options?): Promise<boolean>`
  - current Android provider accesses `ClipboardManager` on the main thread through the live bridge provider
  - queued host fallback keeps a per-execution clipboard string for packaged templates that do not yet inject live bridge metadata
  - no raw `ClipboardManager` object and no clipboard-change listener are exposed to JavaScript
- `device`
  - `sdkInt: number`
  - `width: number`
  - `height: number`
  - `density: number`
  - `isScreenOn(options?): Promise<boolean>`
  - `wakeUp(options?): Promise<void>`
  - `vibrate(ms, options?): Promise<void>`
  - current Android provider uses `PowerManager` and `Vibrator` through the live bridge provider
  - `vibrate()` clamps duration to 10 seconds and packaged APKs that use it must declare `android.permission.VIBRATE`
  - privacy-sensitive identifiers are absent: IMEI, Android ID, serial, phone number, accounts, MAC/IP address, hardware fingerprint, brand, and manufacturer
  - no raw `Context`, `PowerManager`, `Vibrator`, display, or build objects are exposed to JavaScript
- `shell`
  - `exec(command, options?): Promise<{ code: number, stdout: string, stderr: string }>`
  - options: `{ root?: boolean, timeoutMs?: number, cwd?: string, env?: Record<string, string> }`
  - runs through the AutoJs6 bridge provider, not Node `child_process`
  - non-root mode uses a controlled non-interactive shell process on a provider thread; root mode is explicit and attempts `su -c`
  - `cwd` is relative to the scoped working directory and rejects absolute paths, NUL bytes, `..` escapes, and realpath escapes
  - `env` accepts string keys and values only
  - command timeout returns a result with `code: 124`; invalid policy inputs reject with `ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED`
  - queued host fallback does not execute shell commands; packaged templates need live bridge metadata before `shell.exec()` can observe real command results
  - no interactive shell, streaming stdio, process object, signal control, `spawn`, `fork`, or `open` API is exposed
- `fetch`
  - `fetch(url, options?): Promise<Response>` through global `fetch` and `require("fetch")`
  - options: `{ method?: "GET" | "POST", headers?: object | array, body?: string | Buffer | ArrayBuffer | TypedArray | Blob, timeoutMs?: number, maxResponseBytes?: number, maxRedirects?: number, signal?: AbortSignal }`
  - module diagnostics: `require("fetch").policy` and `require("fetch").diagnostics()`
  - response helpers: `text()`, `arrayBuffer()`, and `json()`
  - normal builds reject through the Android provider with `ERR_AUTOJS6_NETWORK_DISABLED`; fake-provider tests cover success, timeout, abort, response budget, unsupported scheme, redirect limit, concurrency limit, and service-destroy paths
  - raw `http`, `https`, `net`, `tls`, `dns`, and `dgram` modules remain unavailable for network I/O
- `websocket`
  - `connect(url, options?): Promise<WebSocketConnection>` through `require("websocket")`
  - options: `{ headers?: object | array, protocols?: string[] | string, timeoutMs?: number, maxMessageBytes?: number, maxQueueSize?: number, signal?: AbortSignal }`
  - connection methods: `send(data, options?)`, `close(code?, reason?, options?)`, `on(event, listener)`, and `off(event, listener)`
  - module diagnostics: `require("websocket").policy` and `require("websocket").diagnostics()`
  - accepts only `ws:` and `wss:` URLs; text and binary messages are buffered and budgeted
  - normal builds reject through the Android provider with `ERR_AUTOJS6_NETWORK_DISABLED`; fake-provider tests cover connect, send, receive, close, timeout, max connection, message budget, service destroy, diagnostics, and that raw `net`/`tls` remain disabled
- `work_manager`
  - `scheduleOnce(options): Promise<string>`
  - `schedulePeriodic(options): Promise<string>`
  - `cancel(taskId, options?): Promise<void>`
  - `list(options?): Promise<readonly TaskInfo[]>`
  - options: `{ scriptPath?: string, entry?: string, backend?: "embedded", delayMs?: number, intervalMs?: number, packaged?: boolean, constraints?: { charging?: boolean, network?: boolean, idle?: boolean }, timeoutMs?: number }`
  - current MVP registers provider-owned task metadata only; it does not launch background Node executions yet
  - `backend` is restricted to `embedded`, paths must stay relative to the scoped working directory, and periodic intervals are bounded to at least `900000` ms
  - packaged metadata can mark project entries with `packaged: true`, but older packaged templates without live bridge metadata do not queue persistent registration
  - raw WorkManager, AlarmManager, JobScheduler, BroadcastReceiver, PendingIntent, and IntentFilter objects remain unavailable to JavaScript

Example:

```js
"nodejs";

const clipboard = require("clipboard");
const device = require("device");
const accessibility = require("accessibility");
const shell = require("shell");
const fetch = require("fetch");
const workManager = require("work_manager");

await clipboard.setText("hello from AutoJs6 Node");
console.log(await clipboard.getText());
console.log(await clipboard.hasText());

console.log(device.sdkInt, device.width, device.height, device.density);
console.log(await device.isScreenOn());
await device.vibrate(50);

const okSelector = accessibility.text("OK");
console.log(JSON.stringify(okSelector));

const result = await shell.exec("echo ok", {
  timeoutMs: 1000,
  cwd: ".",
  env: { AUTOJS6_EXAMPLE: "1" },
});
console.log(result.code, result.stdout.trim());

try {
  await fetch("https://example.com", { timeoutMs: 1000 });
} catch (error) {
  console.log(error.autojs6Code || error.code);
}

const taskId = await workManager.scheduleOnce({
  scriptPath: "./main.cjs",
  delayMs: 1000,
});
console.log(taskId);
```

Explicitly unsupported `engines` APIs:

- `execAutoFile`
- raw `ScriptEngine` or `ScriptExecution` object access

In the main AutoJs6 app, the embedded service injects a per-execution live bridge configuration. The JavaScript bridge writes request files into service-private cache directories, the service dispatches them through `NodeBridgeCapabilityProvider`, and JavaScript observes the provider response before the Promise resolves.

If the live bridge configuration is absent, the JavaScript bridge falls back to the queued host transport. In that mode, the Promise means the request was accepted into the embedded result envelope, and the Android dispatch response is recorded after script completion as `embedded_script.bridge_responses_json`. This fallback keeps older packaged inrt templates and one-way host-effect paths working while templates are refreshed.

Live bridge diagnostics are recorded in result `nativeValues`:

- `embedded_script.bridge_live_enabled`
- `embedded_script.bridge_live_dispatch_count`
- `embedded_script.bridge_live_dispatch_failed_count`
- `embedded_script.bridge_live_dispatch_pending_count`
- `embedded_script.bridge_live_dispatch_status`
- `embedded_script.bridge_live_responses_json`

The packaged APK smoke requires `toast`, `app`, `clipboard`, and `device` successfully and verifies that project-local `node_modules/toast`, `node_modules/app`, `node_modules/clipboard`, and `node_modules/device` packages cannot shadow bridge modules. Packaged `app.packageName` is verified against the packaged application id. `versionName` and `versionCode` are type-checked in packaged smoke because older inrt templates do not inject host app metadata yet; refreshed templates should promote those to exact-value checks. Packaged `clipboard` and `device` smokes use the queued fallback when live bridge metadata is absent; this proves module routing, Promise behavior, packaged shadowing, and privacy-safe surface shape, while true system clipboard/device access is still provided by the live Android provider path. The `shell` and `work_manager` modules are intentionally not executed by queued fallback; packaged `shell.exec()` and `work_manager.schedule*()` should remain live-bridge-only capabilities.

Future bridge work must keep the protocol and fakeable capability providers stable before exposing real screenshot, image decoding, template matching, or handle lifecycle implementations.

Every bridge call must look asynchronous to JavaScript and must reject cleanly on timeout or service destroy.

## Scheduled And Background Task Status

Node scripts can register controlled scheduled-task metadata through `require("work_manager")`.

Current policy:

- `require("work_manager")` is exposed as a Promise bridge module for `scheduleOnce`, `schedulePeriodic`, `cancel`, and `list`.
- Task 65 stores provider-owned metadata only; it does not execute background Node scripts yet.
- `work_manager` is a live-bridge capability. Queued host fallback rejects it with `ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED` so older packaged templates cannot create persistent tasks as one-way effects.
- Registered descriptors accept only `backend: "embedded"`, scoped relative `scriptPath` or packaged project `entry`, bounded delay/interval values, and boolean `charging`, `network`, and `idle` constraints.
- `require("tasks")` is not exposed in the Embedded Node Safe Node Profile.
- Node scripts must not access raw WorkManager, AlarmManager, JobScheduler, BroadcastReceiver, PendingIntent, or IntentFilter objects.
- Existing Rhino task APIs remain unchanged.
- Future execution of scheduled Node tasks must go through an AutoJs6 bridge provider and the existing task manager/scheduler ownership model.
- Future broadcast, startup, boot, and package-event task support is deferred behind `docs/nodejs/design/BROADCAST_TASKS.md` and a user-visible permission flow.

See `docs/nodejs/design/WORK_MANAGER.md` for the current design decision.

## Error Code Compatibility

Errors keep existing Node-style or legacy `code` values for compatibility. AutoJs6-specific canonical codes are exposed through `error.autojs6Code`. When a legacy AutoJs6 code is preserved, `error.legacyCode` repeats the legacy `code`.

Examples:

- disabled builtin: `code=ERR_AUTOJS6_EMBEDDED_NODE_BUILTIN_DISABLED`, `autojs6Code=ERR_AUTOJS6_BUILTIN_DISABLED`
- disabled process API: `code=ERR_AUTOJS6_PROCESS_API_DISABLED`, `autojs6Code=ERR_AUTOJS6_PROCESS_API_DISABLED`
- disabled fetch/network: `code=ERR_AUTOJS6_EMBEDDED_NODE_FETCH_DISABLED`, `autojs6Code=ERR_AUTOJS6_NETWORK_DISABLED`
- scoped fs escape: `code=EPERM`, `autojs6Code=ERR_AUTOJS6_FS_PATH_ESCAPE`
- malformed bridge request: `code=ERR_AUTOJS6_BRIDGE_INVALID_REQUEST`, `autojs6Code=ERR_AUTOJS6_BRIDGE_INVALID_REQUEST`
