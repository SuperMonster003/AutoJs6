# AutoJs6 Node.js Compatibility Profile

This profile describes the current Safe Node surface. It is intentionally smaller than desktop Node.js and should be updated whenever runtime behavior changes.

For the GA-facing v1 release contract, see
[releases/NODE_PROFILE_V1_STABLE_SURFACE.md](releases/NODE_PROFILE_V1_STABLE_SURFACE.md). For user-visible caveats and
workarounds, see [releases/NODE_PROFILE_V1_KNOWN_ISSUES.md](releases/NODE_PROFILE_V1_KNOWN_ISSUES.md).

## Runtime Identity

- Profile name: `AutoJs6 Safe Node Profile`
- Embedded Node baseline: Node.js `v24.5.0` artifacts are present in the current native tree.
- Execution model: embedded `libnode.so` plus AutoJs6 bootstrap and CommonJS loader.
- Process model: script service runs in `:nodejs_embedded`.
- Concurrency model: one active execution per embedded process; default concurrent starts reject, while explicit enqueue uses the bounded execution queue.
- Service model: Phase 7 defines an execution-mode state machine, but only `one_shot` is executable; normal scripts remain timeout-controlled.
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

`NodeRuntimeCapabilities.featureFlags` is the single registry for experimental, partial, disabled, and unsupported Node capability flags. Normal builds keep high and critical security features disabled by default. Project-level and packaged-APK overrides are audited and rejected unless a future design-gated task explicitly changes the registry. Release builds reject release-forbidden experimental Gradle flags; debug experimental builds remain the validation path.

| Flag id | Gradle property | Default | Build override | project.json override | Packaged APK override | Release forbidden | Security | Status |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `esm` | `autojs.nodejs.esm.experimental` | true | true | false | false | false | medium | partial |
| `dynamic_import` | `autojs.nodejs.dynamicImport.local` | true | true | false | false | false | medium | partial |
| `network` | `autojs.nodejs.network.experimental` | false | true | false | false | true | high | experimental |
| `raw_node_network_modules` | none | false | false | false | false | true | high | disabled |
| `websocket` | none | false | false | false | false | true | high | experimental |
| `worker_threads` | `autojs.nodejs.worker_threads.experimental` | false | true | false | false | true | high | experimental |
| `fs_streams` | none | true | false | false | false | false | medium | partial |
| `zlib_streams` | none | true | false | false | false | false | medium | partial |
| `vm` | none | false | false | false | false | true | high | disabled |
| `inspector` | none | false | false | false | false | true | high | disabled |
| `java_interop` | `autojs.nodejs.javaInterop.experimental` | false | true | false | false | true | critical | experimental |
| `native_addon` | none | false | false | false | false | true | critical | unsupported |
| `child_process` | none | false | false | false | false | true | high | disabled |

Runtime diagnostics expose the same state through `require("autojs6:profile").featureFlags`, `enabledExperimentalFlags`, and `deniedFeatureFlags`. The legacy `experimentalFlags` and `highRiskCapabilities` snapshots are retained for compatibility.

## Supported Entry Points

- `"nodejs";` directive
- `"node";` directive
- `.node.js`
- `.cjs`
- `.mjs` as a Node ESM entry
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
- resolver visualizer report through `node tools/nodejs/resolver/explain-node-resolve.js --mode compact|verbose|json --request <specifier> --from <file> --trace-file <stderr-log>` or `:app:explainNodeResolve` with `-Pautojs.nodejs.resolve.*`
- optional embedded runtime timeline trace through `autojs.nodejs.timelineTrace=true` or `AUTOJS6_NODE_TIMELINE_TRACE=1`, reported as `embedded_script.timeline_json` and disabled by default

## Developer REPL

`NodeDeveloperRepl` is a debug-build-only developer entry for inspecting the
Safe Node Profile without enabling inspector, `vm`, `child_process`, worker
threads, Java interop, or broader filesystem access. It is a programmatic
line-eval session, not a production script API or a long-running Node process.

Each session creates a unique temporary `workingDirectory` under app cache and
uses that directory as both `process.cwd()` and the source-name root for
relative `require()` calls. `close()` deletes the directory and any files
created during the session. The API reuses the normal embedded script service,
so stdout/stderr capture, result text, scoped filesystem policy, builtin
allowlist, native addon denial, and timeout behavior are identical to ordinary
one-shot scripts.

Bridge modules are deny-by-default in REPL sessions. The REPL always injects an
enforced bridge permission manifest; by default it declares no capabilities.
Callers must explicitly pass `bridgeCapabilities` to allow a bridge capability,
and the Android provider still performs the normal request audit.

## Project Wizard And Validator

`tools/nodejs/project/autojs6-node-project.js` is a host-side developer tool for
creating and validating Safe Node Profile projects. It can generate
`commonjs-app`, `esm-app`, `automation-script`, `screenshot-ocr-script`, and
`network-fetch-script` templates, then validate `project.json`, `package.json`,
entry files, bridge permission metadata, supported module type, and sanitized
dependency bundle shape.

Validator failures are actionable JSON issues with a stable `code`, `file`,
`message`, and `hint`. The tool rejects unsafe entry paths, unknown bridge
capabilities, native addon files, `binding.gyp`, lifecycle scripts, symlinks,
and generated `.bin` shims before users try to run or package the project. See
[PROJECT_WIZARD.md](PROJECT_WIZARD.md).

## JS-Only Plugin Prototype

`require("plugins")` is reserved by the Safe Node Profile and cannot be
shadowed by `node_modules/plugins`.

Prototype API:

```js
const plugins = require("plugins");
const info = plugins.manifest("org.example.demo");
const entryPath = plugins.resolve("org.example.demo");
const demo = plugins.load("org.example.demo");
```

The prototype loads only project-local directories under `./plugins/<id>`.
Each plugin must provide `package.json` metadata with `autojs6.plugin` and an
`autojs6.plugin.permissions` string array. `plugins.load(id)` requires an
enforced project/package capability manifest that declares the exact
`plugin.<id>` capability and every capability requested by the plugin manifest
before plugin code executes.

Only `.js` and `.cjs` plugin entries are accepted. Plugin-relative `require()`
uses the plugin directory as its resolver root, so plugin code cannot use
relative package resolution to escape into sibling project files. This is a
local JS-only prototype, not an installer, signed archive verifier, Android
plugin adapter, packaged APK plugin runtime, native provider model, or isolated
process/context.

## Execution Queue

Embedded Node is not multi-instance inside a single `:nodejs_embedded` process. The default behavior rejects concurrent starts with `ERR_AUTOJS6_NODE_ENGINE_BUSY`.

Callers that need ordered execution can opt in to the bounded caller-side queue through `NativeNodeEmbeddedScriptService.enqueueScript(...)`. The queue currently allows one waiting execution, supports cancellation by execution id, exposes queue status for queue participants, and separates queue timeout from script execution timeout. Each dequeued item starts a normal one-shot embedded service execution after the previous service process has settled.

Stable queue errors:

- `ERR_AUTOJS6_NODE_QUEUE_FULL`
- `ERR_AUTOJS6_NODE_QUEUE_TIMEOUT`
- `ERR_AUTOJS6_NODE_QUEUE_CANCELLED`

See [design/EXECUTION_QUEUE.md](design/EXECUTION_QUEUE.md).

## Long-Running Service Mode

Long-running Node service mode is only partially available through the explicit
interactive foreground runtime. Pending timers, WebSocket handles, network
requests, or open bridge resources do not convert a normal script into a
service.

Current behavior remains:

- normal embedded scripts require `timeoutMs`
- timeout produces the existing timeout diagnostic shape
- `EmbeddedScriptRequest.executionMode` is serialized through the service
  request spool and normalized through the Phase 7 mode state machine
- canonical modes are `one_shot`, `interactive_long_running`, `scheduled`, and
  `worker_computation`
- `one_shot` is the only supported executable value for normal script launches
- `interactive_long_running` is supported only by the explicit
  `interactive_session` launch surface used by
  `NativeNodeEmbeddedScriptService.startInteractiveLongRunning(...)`
- `scheduled` is supported only by the WorkManager-owned
  `scheduled_runner` launch surface used for fired `work_manager` tasks
- direct script `interactiveLongRunning`, legacy `long_running`/`service`,
  `scheduled`, and `workerComputation` requests still reject before user code
  runs with `ERR_AUTOJS6_EMBEDDED_NODE_UNSUPPORTED_OPTION`
- rejection native values include timeout policy, foreground requirement,
  notification requirement, allowed-provider list, restart policy, quota
  profile, stop semantics, launch surface, and supported launch surfaces
- interactive status snapshots include runtime duration, Stop action, active
  resource summary, restart policy `never`, and `automaticRestart=false`
- `require("autojs6:lifecycle")` is available only inside the explicit
  interactive long-running runtime and supports JSON-only
  `checkpoint()`/`readCheckpoint()`/`clearCheckpoint()`, `onCheckpoint()`,
  explicit `onRestore()`, and cooperative `onStop()`
- checkpoints are app-private, project-scoped by package plus canonical working
  directory, atomically written, limited to 64 KiB, and surfaced in status
  snapshots only as metadata; AutoJs6 does not automatically restart or replay a
  checkpoint
- Android runtime policy diagnostics cover foreground-service manifest/type
  validation, background-start denial, Android 15 `Service.onTimeout(...)`
  cleanup, and Android 16 WorkManager quota metadata
- runtime heartbeat/budget diagnostics report event-loop responsiveness, last
  bridge request/response progress, active resource registry counts, active
  image/network/WebSocket counts, and budget breach counts in status
  `nativeValues`/`activeResources`
- service process exit is expected after one-shot execution settles
- packaged Node entries remain one-shot unless a future explicit service
  descriptor is implemented

The future design gate is documented in [design/LONG_RUNNING_SERVICE.md](design/LONG_RUNNING_SERVICE.md).

Unsupported or rejected module source forms:

- native `.node` addon entries, rejected with `ERR_AUTOJS6_NATIVE_ADDON_DISABLED` from direct relative imports, `module.createRequire()`, package `main`, package `exports`, package `imports`, and package subpath imports
- `.node` files anywhere inside a JS-only plugin directory, rejected before the
  plugin entry executes
- `plugins.load(id)` without exact `plugin.<id>` project opt-in or without the
  plugin-requested capabilities
- plugin entry paths that are absolute, protocol-based, NUL-containing, or
  escape the plugin directory
- static ESM import execution only from ESM entries and ESM graph modules
- dynamic import from `eval`, `Function`, URL/protocol specifiers, absolute paths, or outside `workingDirectory`
- package export/import targets that escape the package root
- unsupported package export/import extensions

The reusable compatibility corpus lives under `app/src/androidTest/assets/node_compat_corpus/` and is executed by
`NodeCompatCorpusInstrumentationTest`. It covers pure JavaScript CommonJS package patterns that should remain stable across
future builtin and bridge work.

The fixed real package corpus lives under `app/src/androidTest/assets/npm_compat_real/` and is executed by
`NodeRealNpmCompatInstrumentationTest`. It vendors offline, license-noted CommonJS package snapshots that cover tested
patterns rather than broad npm compatibility claims:

- small utility and nested dependency packages
- semver parsing plus querystring/URL utility use
- glob/path matching plus crypto hash utility use
- stream utility plus zlib buffer utility use
- core builtin feature detection for `constants`, `util/types`, `events`, and `diagnostics_channel`
- ESM-only dependency import and hybrid CJS/ESM package condition selection
- Buffer utility, fetch wrapper fallback, and assertion helper patterns

These fixtures are intentionally checked in. Tests must not fetch packages from the network, run install/postinstall
scripts, or load native addons. Fixture runners remain CommonJS `test.cjs` files; v1.1 permits ESM-only dependency
packages below `node_modules` so the corpus can exercise partial ESM package import without converting the runner itself
to ESM.

`tools/nodejs/differential/run-differential-tests.js` runs the same fixed fixtures under desktop Node 24 and writes a
JSON/Markdown report under `build/reports/nodejs/differential/`, the v1.1 npm summary report
`build/reports/nodejs/npm-compat-v1.1.md`, and the Task 188 measurement report
`build/reports/nodejs/v1.1-differential.md`. When supplied with `realnpm.differential.result=...` lines emitted by
`NodeRealNpmCompatInstrumentationTest`, it compares normalized stdout, exit code, error code, and error message pattern,
then classifies differences as matched, expected AutoJs6 restriction, unsupported, flaky, or bug.

`:app:bundleNodeDependencies` is the host-side dependency bundling gate. It consumes a project that already has
`package.json` and `node_modules`, validates the tree without running package code, copies a sanitized bundle to
`app/build/nodejs/dependency-bundle/project/`, and writes a manifest plus size/rejection reports under
`app/build/reports/nodejs/dependencies/`. The report set includes `dependency-manifest.json`,
`dependency-sbom-lite.json`, `dependency-size-report.txt`, and `dependency-rejections.txt`.
Native addon binaries are rejected as `NATIVE_ADDON_DISABLED`; node-gyp build traces such as `binding.gyp` and
`build/{Release,Debug}` artifacts are rejected as `NODE_GYP_ARTIFACT_UNSUPPORTED`. The bundler also rejects lifecycle
scripts, unchecked `.bin` entries, npm install/cache artifacts, suspicious executable files, absolute or escaping
package metadata path references, and file/package/tree size limit violations. The sanitized bundle uses the same
`package.json` plus `node_modules` layout expected under packaged APK `assets/project/`.

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

The v1.1 core builtin compatibility gate treats `constants`, `util/types`, `events`, and `diagnostics_channel` as
low-risk feature-detection modules. Bare and `node:` specifiers route to the same frozen wrappers, `node_modules` shadows
are ignored, and `process.getBuiltinModule()` remains disabled so scripts cannot bypass the limited wrappers. The
`events` wrapper exposes `EventEmitter` and helper functions such as `once()`/`on()`, but does not expose
`EventEmitterAsyncResource` or `EventTarget`; Web-style `EventTarget` remains a global. `diagnostics_channel` exposes a
bounded publish/subscribe/tracing surface without raw subscriber internals.

The `stream` wrapper is a partial compatibility surface for pure-JS packages. It
exposes the standard constructors, `Readable.from()`, callback
`pipeline()`/`finished()`, `stream/promises` `pipeline()`/`finished()`, selected
state helpers, `stream/consumers`, and the current limited `stream/web` surface.
`Readable.toWeb()`/`Readable.fromWeb()` and
`Writable.toWeb()`/`Writable.fromWeb()` are allowed as adapters over already
permitted streams; they do not grant raw network, process, native addon, or
unscoped filesystem authority.

Web Streams v1.1 is documented in `docs/nodejs/design/WEB_STREAMS_V1_1.md`.
Controlled fetch response bodies remain buffer-bounded and Blob-backed; use
`response.body.stream()` for the Web Stream read path. Direct spec-style
`Response.body` as a `ReadableStream` is deferred.

The `buffer` wrapper is available through `require("buffer")`, `require("node:buffer")`,
bare ESM `import "buffer"`, and ESM `import "node:buffer"`. It exposes the
safe Node-managed surface already used by the runtime: `Buffer`, `Blob`, `File`,
`atob`, `btoa`, `constants` when present, and encoding helpers. `globalThis.Buffer`
and `require("buffer").Buffer` must stay identical, and builtin routing must
win over `node_modules/buffer` shadow packages.

The `fs` wrapper exposes a scoped stream MVP through `createReadStream()` and
`createWriteStream()`. These streams use the same scoped path policy as the rest
of `fs`: absolute paths, path escape, symlink escape, and Android/device roots
such as `/proc`, `/sys`, and `/dev` remain denied. Active scoped fs streams are
tracked in runtime diagnostics and are destroyed during runtime cleanup so a
timed-out or stopped service does not poison the next execution.

The `zlib` wrapper exposes bounded transform streams for `createGzip()`,
`createGunzip()`, `createDeflate()`, `createInflate()`, raw deflate/inflate,
and Brotli compression/decompression. Active zlib transform tracking starts
after the stream receives data or flushes, so feature-detection constructors do
not keep scripts alive. Active transforms are reported with
`pendingZlibStreams` and `pendingZlibStreamCount`, destroyed during runtime
cleanup, and keep the next execution clean after timeout or service stop.

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
- Rhino shim: `docs/nodejs/design/RHINO_SHIM.md`

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

Current GA evaluation: `docs/nodejs/reports/JAVA_INTEROP_EVALUATION.md`.

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
- Rhino compatibility shim and old global AutoJs API installation
- inspector/debugger server
- npm install and postinstall execution; dependency handling is vendored/build-time only, see `docs/nodejs/design/PACKAGE_MANAGER.md`

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

`inspector` is intentionally disabled, including debugger port binding and script-visible inspector APIs, until a future debug-build-only design gate explicitly proves user consent, loopback binding, lifecycle close, and packaged APK denial. See `docs/nodejs/design/INSPECTOR.md`.

## ESM Status

`.mjs` routes to Node and ESM execution is a default partial capability in Node Profile v1.1.

The legacy build switch remains:

```properties
autojs.nodejs.esm.experimental=true
```

Dynamic import has a separate local-only switch and is enabled by default in Node Profile v1.1:

```properties
autojs.nodejs.dynamicImport.local=true
```

The legacy broad experiment remains release-forbidden:

```properties
autojs.nodejs.dynamicImport.experimental=false
```

Expected ESM boundary errors:

- explicit ESM-off request path: `ERR_AUTOJS6_EMBEDDED_NODE_ESM_UNSUPPORTED`
- static `import` syntax in a CommonJS entry path: `ERR_AUTOJS6_EMBEDDED_NODE_ESM_UNSUPPORTED`
- disabled or unsupported dynamic import path: legacy `code` `ERR_AUTOJS6_EMBEDDED_NODE_DYNAMIC_IMPORT_UNSUPPORTED`, canonical `autojs6Code` `ERR_AUTOJS6_DYNAMIC_IMPORT_UNSUPPORTED`
- CJS `require()` of ESM: `ERR_AUTOJS6_REQUIRE_ESM_UNSUPPORTED`

Default `require("autojs6:profile")` reports `esm=true`, `dynamicImport=true`, `featureFlags.esm.status="partial"`, `featureFlags.dynamic_import.status="partial"`, `featureFlags.dynamic_import.defaultEnabled=true`, and `experimentalFlags.dynamicImport=true`. The legacy `experimentalFlags.esm` and `experimentalFlags.dynamicImport` snapshots remain for compatibility with older diagnostics.

Node Profile v1.1 partial ESM supports:

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

Partial ESM still rejects `import.meta`, CJS `require()` of ESM, ESM JSON import assertions, custom loaders, network/URL imports, disabled builtins, and any import outside `workingDirectory`.

Dynamic `import()` is local-only partial support. It allows local relative `.mjs` and CommonJS modules, allowed builtins, and packages resolved inside `workingDirectory` with ESM package condition order. It rejects disabled builtins, absolute paths, `file:`/`data:` URLs, network URLs, path escapes, loader hooks, and dynamic-code sources such as `eval` and `Function` with stable AutoJs6 errors.

Runtime diagnostics include `esmEnabled`, `esmEntry`, `esmModuleGraphRoot`, `esmModuleGraphSize`, `esmModuleGraphModules`, and `dynamicImportEnabled` in the embedded script result envelope.

See `docs/nodejs/design/ESM.md` for package `type`, CJS/ESM interop, top-level await, dynamic import, `import.meta.url`, package condition, moduleSources, encrypted graph, packaged APK, and stack trace decisions.

## moduleSources Budget

Injected `moduleSources` are bounded before native runtime startup. The v1 limits are:

- module count: `64`
- per-module UTF-8 source bytes: `262144`
- total UTF-8 source bytes: `524288`

The limits apply to CommonJS preload records and encrypted packaged/runtime module source records, including partial ESM `.mjs` records. Over-budget requests fail with `ERR_AUTOJS6_MODULE_SOURCE_BUDGET_EXCEEDED` before the embedded service starts native Node.

Packaged APKs carry `assets/project/preload-budget.expected.txt` when full smoke moduleSources are requested. The packaged verifier checks the budget marker, count, total bytes, per-module maximum, and largest-module report before runtime smoke execution.

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
- `require("axios")` exposes an AutoJs6-owned axios-shaped adapter backed by controlled fetch
- `http`, `https`, `net`, `tls`, `dns`, and `dns/promises` operations reject with `ERR_AUTOJS6_NETWORK_DISABLED`
- `dgram` remains a disabled builtin
- raw sockets, servers, DNS queries, TLS sessions, datagrams, and unrestricted HTTP requests remain unavailable

Use global `fetch()` or `require("fetch")` for controlled request experiments, use `require("axios")` for the AutoJs6-owned axios-shaped compatibility adapter, and use `require("websocket")` for the controlled WebSocket bridge. Userland HTTP clients cannot assume desktop Node `http`/`https` adapters. Raw Node networking is not part of the Safe Node Profile: bare names and `node:` aliases are intercepted, and local `node_modules/http`, `node_modules/https`, `node_modules/net`, `node_modules/tls`, `node_modules/dns`, `node_modules/dns/promises`, `node_modules/dgram`, or `node_modules/axios` fixtures cannot bypass the policy.

Current `require("autojs6:profile")` reports `network=false` and `experimentalFlags.network=false`.

Current controlled fetch support includes `GET`/`POST`, headers, string or binary bodies, `timeoutMs`, `AbortController.signal`, `maxResponseBytes`, `maxRedirects`, text/arrayBuffer/json response helpers, and Web Stream reads through `response.body.stream()`. The live provider is backed by Android/OkHttp behind `BuildConfig.NODEJS_NETWORK_EXPERIMENTAL_ENABLED`; normal release builds keep that flag false. The axios-shaped adapter supports `axios(config)`, `axios.request`, `axios.get`, `axios.post`, `baseURL`, params, headers, `timeout`, and `responseType: "json" | "text" | "arraybuffer"` while rejecting custom adapters, agents, proxy configuration, custom transport, and raw socket assumptions.

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

Current GA evaluation: `docs/nodejs/reports/NETWORK_EVALUATION.md`.

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
- `connection.addEventListener(...)` / `connection.removeEventListener(...)` aliases
- `connection.onopen`, `connection.onmessage`, `connection.onclose`, and `connection.onerror` handler properties
- `policy`
- `diagnostics()`

The MVP is provider-backed through the AutoJs6 bridge and uses bounded policy: max 2 active connections, `ws:`/`wss:` only, default timeout `10000` ms, default message budget `65536` bytes, bounded queued events, headers/protocol negotiation descriptors, and string or binary send payloads. The Android provider is backed by OkHttp only when `BuildConfig.NODEJS_NETWORK_EXPERIMENTAL_ENABLED=true`; normal builds reject with `ERR_AUTOJS6_NETWORK_DISABLED`. Fake-provider tests cover connect descriptor shape, send/receive text and binary messages, close/error event dispatch, timeout, max-connection denial, message-size denial, service-destroy rejection, diagnostics, and raw `net`/`tls` denial.

Raw `net`, `tls`, and HTTP upgrade authority remains denied, so userland packages cannot open sockets through desktop Node APIs. The module boundary is documented in `docs/nodejs/design/WEBSOCKET.md`.

Current GA evaluation: `docs/nodejs/reports/NETWORK_EVALUATION.md`.

## Worker Threads Status

`worker_threads` remains disabled by default. AutoJs6 has an experimental pure-JS worker design path, but Task 122 found
that worker execution is blocked in the current Embedded Node build because native `worker_threads` is unavailable even
when the experimental flag is enabled. S7-24 adds a focused bootstrap/native-availability preflight to the worker gate so
this blocked state is verified explicitly without advertising runnable worker support. S7-25 prepares the native-available
entry path for CJS and partial-ESM workers, but the current build still skips runnable worker smoke tests until native
`worker_threads` exists. S7-26 prepares the native-available messaging path for
limited `MessageChannel`/`MessagePort` and transfer lists, but it remains behind
the same experimental gate and native-availability preflight. S7-27 defines the
separate worker capability profile: workers get only scoped `fs`/`fs/promises`
read/write helpers rooted at their working directory and do not inherit main
script bridge, network, shell, Java, or inspector authority. S7-28 prepares a
controlled `WorkerPool` extension with bounded worker count, queueing,
cancellation, task timeout, idle timeout, capped resource limits, and shutdown
diagnostics. S7-29 extends that promotion path with 100-job stress, WorkerPool
failure, transfer, cancellation, timeout recovery, parent-completion cleanup,
and a single packaged APK `.mjs` worker smoke selected by `verifyNodeWorkerGate`.
Those runnable checks remain skipped on Sony while native workers are
unavailable. The explicit gate flag is:

```properties
autojs.nodejs.worker_threads.experimental=false
```

With the flag off:

- `require("worker_threads")` rejects with disabled-builtin error shape
- `require("node:worker_threads")` rejects with disabled-builtin error shape
- `require.resolve("worker_threads")` rejects before a local `node_modules/worker_threads` package can shadow the builtin
- no worker can access AutoJs bridge modules, Android Context, accessibility, media projection, image handles, shell, engines, raw network, child process, native addon, `vm`, inspector, or unrestricted filesystem APIs

Current `require("autojs6:profile")` reports `workerThreads=false`, `highRiskCapabilities.workerThreads=false`, and `experimentalFlags.workerThreads=false`.

Current `verifyNodeWorkerGate` normal builds also run
`NodeWorkerThreadsBootstrapInstrumentationTest`, which verifies request-local
default denial, shadowing protection, and request-local experimental preflight.
On the current Embedded Node build, that preflight reports
`ERR_AUTOJS6_WORKER_DISABLED`, `embedded_script.worker_active_count=0`, and no
same-thread fallback.

When the experimental request flag is enabled in a build that exposes native `worker_threads`, the intended limited module
for local worker scripts inside `workingDirectory` is:

- `new Worker("./worker.js" | "./worker.cjs" | "./worker.mjs", { workerData })`
- `new Worker(filename, { workerData, transferList })` for ArrayBuffer and MessagePort transfer entries
- `package.json` `type=module` `.js` worker entries
- local CJS/JSON worker dependencies
- local partial-ESM worker dependencies from `.mjs` or `type=module` `.js`
- `worker.on("message" | "error" | "exit" | "online", listener)`
- `worker.postMessage(value, transferList?)`
- `worker.terminate(): Promise<number>`
- `new MessageChannel()`
- `MessagePort`
- `MessagePort.postMessage(value, transferList?)`
- ArrayBuffer transfer detachment, typed array delivery, and structured-clone-safe values
- DataCloneError-compatible failures for unsupported clone/transfer values
- scoped worker `fs` and `fs/promises` helpers for read/write operations inside `workingDirectory`
- controlled `WorkerPool` with explicit task protocol, min/max worker count, idle timeout, task timeout, queue limit, cancellation, capped `resourceLimits`, and `close()`
- `isMainThread`
- `parentPort`
- `workerData`
- `threadId`
- `policy`
- `diagnostics()`

The intended MVP loads worker source through the parent scoped resolver, then executes it in a native Node Worker with a worker-local restricted loader. Worker-local `require("worker_threads")` exposes only `parentPort`, `workerData`, `isMainThread`, `threadId`, limited native-available `MessageChannel`/`MessagePort`, `workerProfile`, and a disabled nested `Worker`. Worker-local CJS and partial-ESM imports can load local worker dependencies and the safe worker builtin allowlist only. Worker-local `fs` and `fs/promises` are AutoJs6 wrappers, not native Node `fs`; they validate lexical paths and real paths against the worker root before read/write operations. Worker-local loading denies AutoJs bridge modules, `autojs6:bridge`, raw network, `shell`, `child_process`, native addons, `vm`, inspector, Java/Android interop, and nested workers. The first policy allows one active worker, a `5000` ms startup timeout, `65536` byte messages, `32` queued messages per parent/port sender, scoped worker fs read/write in `workingDirectory`, and safe pure-JS builtins only.

`WorkerPool` is an AutoJs6 experimental extension, not a Node standard export. Pool workers use an explicit message protocol: the parent sends `{ __autojs6PoolTaskId, value }`, and the worker replies with `{ __autojs6PoolTaskId, value }` or `{ __autojs6PoolTaskId, error }`. Worker and parent-side MessagePort instances are tracked per execution when the native module is available. Parent success/failure cleanup performs best-effort termination of any active worker and pool and closes tracked ports before writing the result envelope, and exposes pending-worker, worker-pool, message-port, rejected-message, and cleanup counters through native diagnostics. See `docs/nodejs/design/WORKER_THREADS.md` and `docs/nodejs/reports/WORKER_EXPERIMENTAL_EVALUATION.md` for worker count, timeout, memory, packaged APK, bridge-denial, lifecycle policy, and the current GA blocking status.
Packaged worker support is still limited to the S7-29 internal smoke: a
packaged CJS entry starts a local `.mjs` worker and verifies workerData,
parentPort, threadId, builtin import, source graph packaging, and exit cleanup.
It is not a user-visible packaged worker guarantee while the default profile
keeps `worker_threads` disabled.

## Process Profile

Supported or limited:

- `process.version`
- `process.versions`
- `process.argv`
- `process.env`
- `process.cwd()`
- controlled `process.chdir()` for project bootstrap use
- `process.nextTick`; tests lock its ordering before Promise/`queueMicrotask` callbacks and before timer callbacks
- read-only `process.permission.has(scope, reference?)` compatibility facade for `fs.read`, `fs.write`,
  `network`, `worker`, `shell`, `accessibility`, `screen_capture`, `notifications`, `sensors`, and `ocr`
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

`process.permission` is an AutoJs6 compatibility view, not an exact desktop
Node Permission Model implementation and not a sandbox boundary. `fs.read` and
`fs.write` reference checks reuse the scoped filesystem policy, so absolute
host paths, NUL paths, `..` escapes, sensitive roots, symlink escapes, and
realpath escapes return `false`. Bridge-style scopes reflect the current
capability manifest: an enforced manifest requires the matching declared
capability, while legacy non-enforced runs keep their historical bridge
compatibility behavior. `network` remains `false` in the default Safe Node
Profile because raw network modules and live controlled-network providers are
not enabled by default. `worker` reflects only the explicit worker_threads
feature flag. Querying never grants a capability, never contacts Android
providers, and `process.permission.query()` returns a frozen AutoJs6 snapshot;
`autojs6:profile.processPermission` exposes the same read-only view.

Interactive long-running scripts can use `require("autojs6:lifecycle")` for
cooperative checkpoint and restore. `onStop(handler)` receives a bounded stop
reason and can persist JSON checkpoint data before the service exits.
`onRestore(handler)` is an explicit application call in a later interactive
session; it never runs automatically during process startup and never grants a
restart policy beyond `never`.

## Embedded Execution Lifecycle

The `:nodejs_embedded` service process allows one active embedded Node execution at a time. A concurrent start in the same process is rejected, not queued, with:

- `errorCode=ERR_AUTOJS6_NODE_ENGINE_BUSY`
- `nativeValues.embedded_script.engine_busy=true`
- `nativeValues.embedded_script.concurrent_policy=single-active-execution-per-process`

After the active execution completes and the service settles, a later execution can run normally. Queueing remains a separate design gate; see `docs/nodejs/design/MULTI_INSTANCE.md`.

If Embedded Node script execution is disabled at build time, embedded execution fails before service startup or native entry with:

- `errorCode=ERR_AUTOJS6_NODE_DISABLED`
- `nativeValues.embedded_script.error_code=ERR_AUTOJS6_NODE_DISABLED`

The actionable message names `-Pautojs.nodejs.embedded.script.enabled=true`. This is a release fallback and diagnostic path, not a silent conversion of Node-marked scripts into ordinary Rhino scripts.

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
- Node project metadata and module source entries within the `64` module, `262144` byte per-module, and `524288` byte total budgets
- packaged smoke coverage for the current user-facing `toast`, `app`, `clipboard`, and `device` bridge modules, plus v1.1 fake-provider packaged coverage for controlled `fetch`, `ocr`, `storage`/`storages`, `ui`, and `work_manager`

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
- per-execution bridge resource registry: tracks provider-owned handles and closes leaked resources on destroy
- per-execution bridge limit policy: bounds pending calls, image handles, shell commands, controlled network/WebSocket resources, and accessibility request rate
- bridge requests require non-empty `id`, `module`, and `method`; `args` must be a JSON array, `permissions` must be a string array, and `timeoutMs` must be a positive number
- Node projects may declare bridge capabilities in `project.json` under `node.permissions`, or in `package.json` under `autojs6.node.permissions`
- when a bridge capability manifest is present, missing capabilities reject before dispatch with `ERR_AUTOJS6_BRIDGE_CAPABILITY_NOT_DECLARED`
- `require("autojs6:bridge").permissions()` and `require("autojs6:profile").bridgePermissions` expose the read-only capability report
- declaring `network` covers controlled `fetch`, `axios`, and `websocket`; packaged builds derive `android.permission.INTERNET` from that capability rather than from module presence
- Node doctor text/JSON reports expose the bridge capability manifest schema, `network` capability status, required Android permission mapping, and raw network denial state
- Node projects may declare bridge limits in `project.json` under `node.bridgeLimits`, or in `package.json` under `autojs6.node.bridgeLimits`
- `require("autojs6:profile").bridgeLimits` exposes the effective read-only limit report, including warnings for ignored overrides
- malformed live bridge requests reject with `ERR_AUTOJS6_BRIDGE_INVALID_REQUEST` when a response id can be recovered
- provider callbacks are accepted at most once per bridge request; duplicate callbacks are ignored
- synchronous provider dispatch exceptions are converted to bridge failure responses with stable module and method metadata
- JS pending bridge calls reject on timeout with `ERR_AUTOJS6_BRIDGE_TIMEOUT`
- JS pending bridge calls reject on `AbortSignal` cancellation with `ERR_AUTOJS6_BRIDGE_CANCELLED`
- JS pending bridge calls reject on destroy with `ERR_AUTOJS6_BRIDGE_PROCESS_DEAD`
- controlled fetch and WebSocket wrappers map bridge cancellation to their public network abort code `ERR_AUTOJS6_NETWORK_ABORTED`
- bridge flood/resource exhaustion rejects with `ERR_AUTOJS6_BRIDGE_RESOURCE_LIMIT`
- accessibility bridge request-rate exhaustion rejects with `ERR_AUTOJS6_BRIDGE_RATE_LIMITED`

Cancellation policy:

- service stop, user stop, or native bridge destruction rejects pending calls with `ERR_AUTOJS6_BRIDGE_PROCESS_DEAD`
- bridge timeouts reject with `ERR_AUTOJS6_BRIDGE_TIMEOUT`
- explicit JavaScript `AbortSignal` cancellation rejects pending bridge calls with `ERR_AUTOJS6_BRIDGE_CANCELLED`
- provider-owned permission UI dismissal and manual resource closure should use `ERR_AUTOJS6_BRIDGE_CANCELLED` when the provider can distinguish cancellation from policy denial
- unavailable Android authorities, undeclared capabilities, or policy denial continue to use `ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED` or `ERR_AUTOJS6_BRIDGE_CAPABILITY_NOT_DECLARED`

Current user-facing bridge modules:

v1.1 bridge expansion planning is tracked in
[BRIDGE_V1_1_MODULES.md](design/BRIDGE_V1_1_MODULES.md). Task 148 added the
`ocr` bridge protocol MVP, Task 172 expands it to the v1.1 partial JavaScript
surface, and Phase 7 adds a live Android MLKit OCR provider over provider-owned
image handles with a scoped `image.readImage()`/`image.recycle()` backend. OCR
packaged model policy and broader image-provider ownership remain future work.
Task 173 adds JSON-only `storage`/`storages`
provider storage with package/cwd/store isolation and quota checks, and Task
174 adds post-only `notifications` with Android permission gating, stable
channels, and owned-notification cleanup. Task 175 adds bounded
`sensors` events with live once/subscribe paths, sampling clamps, subscription
limits, and controlled cleanup. Task 182 reserves the `ui` protocol and S7-14
adds the Activity-owned live Android provider for declarative layouts, drained
events, updates, close, and provider-destroy cleanup while keeping raw
View/Activity/Context objects out of Node.

Declared capability names:

- `toast`
- `app`, `app.launch`, `app.settings`, `app.activity`
- `dialogs`
- `engines`, `engines.exec`
- `accessibility`
- `screen_capture` (`media_projection` is accepted as an alias)
- `image`
- `ocr`
- `storage`
- `notifications`
- `sensors`
- `ui`
- `clipboard`
- `device`
- `shell`, `shell.root`
- `network`
- `work_manager`
- `java_interop`

No manifest keeps the legacy compatibility mode and does not enforce bridge capability declarations. An explicit empty array such as `"node": { "permissions": [] }` enforces a deny-by-default bridge surface.

Default bridge limits:

- `maxPendingBridgeCalls`: `32`, hard max `128`
- `maxImageHandles`: `32`, hard max `128`
- `maxShellCommands`: `2`, hard max `4`
- `maxNetworkRequests`: `4`, hard max `8`
- `maxWebSocketConnections`: `2`, hard max `4`
- `accessibilityQueriesPerSecond`: `20`, hard max `60`

Overrides outside the documented safe range are ignored and reported in `profile.bridgeLimits.warnings`.

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
  - `textMatches(value): Selector`
  - `descMatches(value): Selector`
  - `idMatches(value): Selector`
  - `classNameMatches(value): Selector`
  - `clickable(value?): Selector`
  - `enabled(value?): Selector`
  - `depth(value): Selector`
  - `boundsInside(bounds): Selector`
  - `boundsContains(bounds): Selector`
  - `isEnabled(options?): Promise<boolean>`
  - `ensureEnabled(options?): Promise<boolean>`
  - `click(x, y, options?): Promise<boolean>`
  - `click(selector, options?): Promise<boolean>`
  - `longClick(selector, options?): Promise<boolean>`
  - `setText(selector, text, options?): Promise<boolean>`
  - `scrollForward(selector, options?): Promise<boolean>`
  - `scrollBackward(selector, options?): Promise<boolean>`
  - `back(options?): Promise<boolean>`
  - `home(options?): Promise<boolean>`
  - `recentApps(options?): Promise<boolean>`
  - `findByText(text, options?): Promise<UiNode | null>`
  - `clickText(text, options?): Promise<boolean>`
  - `findOne(selector, options?): Promise<UiNode | null>`
  - `findAll(selector, options?): Promise<readonly UiNode[]>`
  - selector descriptors are frozen plain JSON values such as `{ type: "autojs6.accessibility.selector", version: 1, text: "OK" }`
  - selector v2 fields remain JSON-only descriptors: regular expressions are serialized as `{ pattern, flags }`, bounds are `{ left, top, right, bottom }`, and no raw selector or node object crosses the bridge
  - returned `UiNode` values are frozen JSON snapshots only; no live `AccessibilityNodeInfo`, `UiObject`, or selector object crosses into Node
  - current JavaScript API, fake-provider tests, and bridge protocol are stable
  - when an operational AutoJs6 accessibility service is visible in the current process, the Android provider can validate `isEnabled`, `ensureEnabled`, `findByText`, selector `findOne`/`findAll`, selector `click`/`longClick`/`setText`/`scrollForward`/`scrollBackward`, and Back/Home/Recents global actions through controlled JSON snapshots and booleans
  - the focused accessibility/image gate launches a controlled Activity and exercises real selector `findOne`/`click` only when the service is already enabled; it does not modify device accessibility settings
  - when an accessibility provider is unavailable in the current process, including isolated embedded service processes that cannot see the main-process service instance, `isEnabled`/`ensureEnabled` return `false` and action/query methods reject with `ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED`
- `media_projection`
  - `requestScreenCapture(options?): Promise<ScreenCapturer>`
  - `ScreenCapturer.nextImage(options?): Promise<Image | null>`
  - `ScreenCapturer.stop(options?): Promise<void>`
  - current JavaScript API, fake-provider tests, and bridge protocol are stable
  - current Android provider rejects with `ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED` until a MediaProjection capability provider is connected
  - optional real-device `screen_capture` smoke validates the underlying Activity-owned Android MediaProjection and `ScreenCapturer` frame path without enabling the Node provider by default
- `image`
  - `captureScreen(options?): Promise<Image | null>`
  - `readImage(path, options?): Promise<Image | null>`
  - `saveImage(image, path, options?): Promise<void>`
  - `saveImage(image, path, format, options?): Promise<void>`
  - `clip(image, x, y, w, h, options?): Promise<Image | null>`
  - `resize(image, w, h, options?): Promise<Image | null>`
  - `grayscale(image, options?): Promise<Image | null>`
  - `threshold(image, options?): Promise<Image | null>`
  - `findImage(source, template, options?): Promise<Point | null>`
  - `matchTemplate(source, template, options?): Promise<readonly ImageMatch[]>`
  - `findColor(image, color, options?): Promise<Point | null>`
  - `findMultiColors(image, color, points, options?): Promise<Point | null>`
  - `getSize(image): { width, height }`
  - `recycle(image, options?): Promise<void>`
  - `images` is also reserved as an AutoJs-style alias with the same API shape
  - `captureScreen()` requests an already-granted screen-capture path through the future MediaProjection-backed image provider; it must not prompt directly from the Node service
  - `readImage()` and `saveImage()` accept scoped relative paths only; absolute paths, NUL bytes, and `..` escapes are rejected before bridge dispatch
  - `clip()`, `resize()`, `grayscale()`, and `threshold()` return new opaque image handles and count against the per-execution image-handle limit until recycled
  - `matchTemplate()` returns frozen JSON match snapshots with `{ x, y, score }`; `findColor()` and `findMultiColors()` return frozen `Point` snapshots or `null`
  - `getSize()` reads the frozen image-handle snapshot locally and does not dispatch to Android
  - repeated `recycle()` calls for the same normalized handle are safe from JavaScript and dispatch at most once per module instance
  - current JavaScript API, fake-provider tests, and bridge protocol are stable
  - current Android provider supports the scoped local-file `readImage()` and `recycle()` path used by the live OCR provider, returning provider-owned opaque handles and enforcing the per-execution image-handle limit
  - the focused accessibility/image gate keeps screenshot capture and advanced image operations fail-closed while validating the scoped `readImage()` to OCR to `recycle()` path
  - screenshot capture, save, transform, template-matching, and color-search methods still reject with `ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED` until full image capability provider ownership is connected
  - optional real-device `screen_capture` smoke validates frame decode and recycle behavior against the Android capture pipeline while JavaScript still receives only opaque future handles
- `ocr`
  - `recognize(image, options?): Promise<readonly OcrResult[]>`
  - `recognizeText(image, options?): Promise<string>`
  - `detectTextBounds(image, options?): Promise<readonly OcrResult[]>`
  - `OcrResult` is a frozen JSON snapshot with `text: string`, `confidence: number | null`, and optional frozen `bounds: { left: number, top: number, right: number, bottom: number }`
  - `recognizeText()` returns the recognized text values joined with newline characters
  - `detectTextBounds()` returns the same frozen result shape as `recognize()` and is reserved for providers that distinguish text detection from text extraction internally
  - input must be an existing image handle from `image`, `images`, or `media_projection.nextImage()`
  - requests require both `ocr` and `image` capabilities because OCR consumes provider-owned image handles
  - current JavaScript API, fake-provider tests, manifest-denial behavior, type declarations, and bridge protocol are stable
  - current Android provider uses MLKit Chinese text recognition over active provider-owned image handles, supports optional bounded `region` descriptors, and reports zero active OCR operations after success, timeout, failure, or destroy cleanup
  - unknown, recycled, or non-provider-owned image handles reject with `ERR_AUTOJS6_BRIDGE_INVALID_REQUEST`
  - no raw OCR engine, model path, native pointer, `Bitmap`, `Mat`, `ImageWrapper`, or Java object crosses into Node
- `storage` / `storages`
  - `create(name): StorageStore`
  - `open(name): StorageStore`
  - `StorageStore.get(key, defaultValue?, options?): Promise<JsonValue>`
  - `StorageStore.put(key, value, options?): Promise<void>`
  - `StorageStore.remove(key, options?): Promise<void>`
  - `StorageStore.clear(options?): Promise<void>`
  - `StorageStore.keys(options?): Promise<readonly string[]>`
  - `StorageStore.contains(key, options?): Promise<boolean>`
  - values must be JSON-serializable; `undefined`, functions, symbols, native objects, and circular structures are rejected before dispatch
  - requests require the `storage` capability
  - Android provider data is scoped by package identity, working directory, and storage name in app-private SharedPreferences
  - Android writes are synchronous and bounded by a 64 KiB per-value quota and 256 KiB per-store quota
  - JavaScript receives frozen store handles only; no raw `SharedPreferences`, database, file, or Android object crosses into Node
- `notifications`
  - `notify(options, callOptions?): Promise<number>`
  - `cancel(id, options?): Promise<void>`
  - `cancelAll(options?): Promise<void>`
  - options: `{ id?: number, title?: string, text?: string, channel?: string, channelName?: string, ongoing?: boolean, priority?: "min" | "low" | "default" | "high" | "max" | number }`
  - requests require the `notifications` capability
  - packaged capability manifests map `notifications` to `android.permission.POST_NOTIFICATIONS`
  - Android provider checks notification runtime permission/app notification state before posting
  - Android provider creates stable channel ids and records channel/ongoing metadata for owned notification resources
  - the provider tracks notifications owned by the current Node execution and cancels remaining owned notifications on provider destroy
  - JavaScript receives only numeric ids; no raw `Notification`, `NotificationChannel`, `PendingIntent`, `RemoteViews`, or Android object crosses into Node
- `sensors`
  - `getAvailableSensors(options?): Promise<readonly SensorInfo[]>`
  - `once(type, options?): Promise<SensorEvent>`
  - `subscribe(type, callback, options?): SensorSubscription`
  - supported types: `accelerometer`, `gyroscope`, `light`
  - options: `{ samplingIntervalMs?: number, intervalMs?: number, timeoutMs?: number, maxQueueSize?: number, drainIntervalMs?: number }`
  - subscriptions expose frozen local handles with `id`, `type`, `closed`, `ready`, and `close(options?)`
  - requests require the `sensors` capability
  - Android provider lists supported hardware, returns live one-shot events, drains live subscription events, clamps sampling intervals to policy, caps active subscriptions, and unregisters listeners on unsubscribe or destroy
  - missing Android sensor authority or unsupported hardware returns a stable bridge permission/unavailable error rather than exposing raw platform objects
  - JavaScript receives frozen JSON event snapshots only; no raw `SensorManager`, `Sensor`, `SensorEvent`, listener, `Context`, or Android object crosses into Node
- `ui`
  - `showLayout(layoutJson, options?): Promise<UiHandle>`
  - `UiHandle.id: string`
  - `UiHandle.on(event, callback, options?): () => void`
  - `UiHandle.update(patch, options?): Promise<void>`
  - `UiHandle.close(options?): Promise<void>`
  - layout descriptors and update patches must be JSON-only plain objects; functions, symbols, `bigint`, `undefined`, cycles, non-plain object instances, excessive depth, excessive object keys, excessive array length, and oversized descriptors are rejected before dispatch
  - event callbacks receive frozen JSON snapshots with `handleId`; events are delivered by bounded `drainEvents` bridge requests instead of direct Android-to-JavaScript callbacks
  - requests require the `ui` capability
  - current JavaScript API, fake-provider tests, type declarations, and bridge protocol are reserved
  - Android provider starts an internal Activity and supports `Column`, `Row`, `Text`, `Button`, `Input`, `Image`, and `ScrollView` descriptors, `update`, Button/Input/lifecycle events, state snapshots, `close`, provider-destroy cleanup, and a bounded active UI session count
  - JavaScript receives only an opaque handle; no raw `Context`, `Activity`, `View`, `Window`, `Fragment`, `Handler`, Rhino `NativeView`, overlay window, Binder, or Java object crosses into Node
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
  - `execFile(path, args?, options?): Promise<{ code: number, stdout: string, stderr: string }>`
  - `checkAccess(options?): { shell: boolean, root: boolean, shizuku: boolean, missingCapabilities: string[] }`
  - `setDefaultTimeout(ms): number`
  - options: `{ root?: boolean, timeoutMs?: number, cwd?: string, env?: Record<string, string> }`
  - runs through the AutoJs6 bridge provider, not Node `child_process`
  - migration mapping: `child_process.exec()` -> `require("shell").exec()` with declared `shell` capability; `child_process.spawn()`, `fork()`, streaming stdio, and interactive shells are not supported by the Safe Node Profile
  - non-root mode uses a controlled non-interactive shell process on a provider thread; root mode is explicit and attempts `su -c`
  - `execFile()` is a JS-layer convenience wrapper over `exec()` for scoped relative file paths and string arguments; it shell-quotes the target and args and does not add a second native dispatch method
  - `cwd` is relative to the scoped working directory and rejects absolute paths, NUL bytes, `..` escapes, and realpath escapes
  - `env` accepts string keys and values only
  - command timeout returns a result with `code: 124`; invalid policy inputs reject with `ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED`; `setDefaultTimeout()` changes only the per-module JS fallback timeout
  - `checkAccess({ root: true })` reports missing `shell.root` when a manifest is enforced and the capability is absent; `checkAccess({ shizuku: true })` always reports `shizuku: false` in v1.1 because Shizuku execution remains unsupported
  - queued host fallback does not execute shell commands; packaged templates need live bridge metadata before `shell.exec()` can observe real command results
  - no interactive shell, streaming stdio, process object, signal control, `spawn`, `fork`, or `open` API is exposed
- `fetch`
  - `fetch(url, options?): Promise<Response>` through global `fetch` and `require("fetch")`
  - options: `{ method?: "GET" | "POST", headers?: object | array, body?: string | Buffer | ArrayBuffer | TypedArray | Blob, timeoutMs?: number, maxResponseBytes?: number, maxRedirects?: number, signal?: AbortSignal }`
  - module diagnostics: `require("fetch").policy` and `require("fetch").diagnostics()`
  - response helpers: `text()`, `arrayBuffer()`, and `json()`
  - normal builds reject through the Android provider with `ERR_AUTOJS6_NETWORK_DISABLED`; fake-provider tests cover success, timeout, abort, response budget, unsupported scheme, redirect limit, concurrency limit, and service-destroy paths
  - raw `http`, `https`, `net`, `tls`, `dns`, and `dgram` modules remain unavailable for network I/O, including `node:` aliases and local `node_modules` shadow attempts
- `axios`
  - `axios(config)`, `axios.request(config)`, `axios.get(url, config?)`, and `axios.post(url, data?, config?)` through `require("axios")`
  - options: `{ url?, baseURL?, method?, headers?, params?, data?, timeout?, maxResponseBytes?, maxContentLength?, maxRedirects?, responseType?, signal?, validateStatus? }`
  - supported `responseType`: `json`, `text`, and `arraybuffer`
  - returns Axios-like responses with `data`, `status`, `statusText`, `headers`, `config`, and a provider-owned request descriptor
  - non-2xx status and provider failures reject with `AxiosError` shape while preserving AutoJs6 network codes
  - rejects `adapter`, `httpAgent`, `httpsAgent`, `agent`, `transport`, `socketPath`, custom `paramsSerializer`, and proxy configuration
  - backed by controlled fetch only; local `node_modules/axios` cannot shadow it, and raw network modules remain denied
- `websocket`
  - `connect(url, options?): Promise<WebSocketConnection>` through `require("websocket")`
  - options: `{ headers?: object | array, protocols?: string[] | string, timeoutMs?: number, maxMessageBytes?: number, maxQueueSize?: number, signal?: AbortSignal }`
  - connection methods: `send(data, options?)`, `close(code?, reason?, options?)`, `on(event, listener)`, `off(event, listener)`, `addEventListener(event, listener)`, and `removeEventListener(event, listener)`
  - handler properties: `onopen`, `onmessage`, `onclose`, and `onerror`; close listeners receive `{ type, code, reason }`
  - module diagnostics: `require("websocket").policy` and `require("websocket").diagnostics()`
  - accepts only `ws:` and `wss:` URLs; text and binary messages are buffered and budgeted
  - normal builds reject through the Android provider with `ERR_AUTOJS6_NETWORK_DISABLED`; fake-provider tests cover connect, send, receive, close, timeout, max connection, message budget, service destroy, diagnostics, and that raw `net`/`tls` plus their `node:` aliases remain disabled
  - live Android sessions are registered as bridge resources and are closed on execution destroy
- `work_manager`
  - `scheduleOnce(options): Promise<string>`
  - `schedulePeriodic(options): Promise<string>`
  - `cancel(taskId, options?): Promise<void>`
  - `list(options?): Promise<readonly TaskInfo[]>`
  - options: `{ scriptPath?: string, entry?: string, backend?: "embedded", delayMs?: number, intervalMs?: number, packaged?: boolean, constraints?: { charging?: boolean, network?: boolean, idle?: boolean }, taskTimeoutMs?: number, timeoutMs?: number }`; `taskTimeoutMs` controls the scheduled run, while `timeoutMs` remains the bridge call timeout
  - current MVP persists app-private WorkManager task records and can launch scoped embedded Node one-shot or bounded periodic work through AndroidX WorkManager
  - `backend` is restricted to `embedded`, task metadata preserves either `scriptPath` or project `entry` plus `entryKind`, paths must stay relative to the scoped working directory, non-packaged entries must exist as files, periodic intervals are bounded to at least `900000` ms, and workers never request implicit retry
  - packaged metadata can mark project entries with `packaged: true` and does not require a plaintext source file at registration time, but live execution still needs a readable scoped entry when the WorkManager worker fires
  - fired work runs through the dedicated `scheduled_runner` launch surface with `executionMode=scheduled`; direct scripts cannot request that mode
  - scheduled runner entries support CJS and ESM source names through the same scoped source resolver used by embedded Node project launches
  - task records expose `lastRun` plus bounded `runHistory` with trigger, runner version, attempt, execution id, execution mode, launch surface, stdout/stderr snippets, exit/error metadata, timeout state, Android quota policy, and selected embedded native values
  - provider-owned task registrations are tracked as bridge resources; explicit `cancel()` removes persisted records and cancels the unique WorkManager work
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
- `embedded_script.bridge_live_dispatch_pending_limit`
- `embedded_script.bridge_live_dispatch_resource_limited_count`
- `embedded_script.bridge_live_dispatch_status`
- `embedded_script.bridge_live_responses_json`
- `embedded_script.bridge_resource.active_count`
- `embedded_script.bridge_resource.max_active_count`
- `embedded_script.bridge_resource.created_count`
- `embedded_script.bridge_resource.closed_count`
- `embedded_script.bridge_resource.destroy_closed_count`
- `embedded_script.bridge_resource.leak_count`
- `embedded_script.bridge_resource.active_types`
- `embedded_script.bridge_resource.dump_json` in debug builds
- `embedded_script.bridge_limits.*`
- `embedded_script.bridge_resource_limit_rejection_count`
- `embedded_script.bridge_rate_limited_count`

The current Android provider registers live resources for controlled fetch calls, controlled WebSocket sessions, shell executions, WorkManager-backed `work_manager` task registrations, and experimental Java object handles. The registry also reserves resource types for future image handles, screen capturers, dialogs, UI sessions, and workers so those providers can share the same destroy path when they become live Android capabilities.

The packaged APK smoke requires `toast`, `app`, `clipboard`, and `device` successfully and verifies that project-local `node_modules/toast`, `node_modules/app`, `node_modules/clipboard`, and `node_modules/device` packages cannot shadow bridge modules. Packaged `app.packageName` is verified against the packaged application id. `versionName` and `versionCode` are type-checked in packaged smoke because older inrt templates do not inject host app metadata yet; refreshed templates should promote those to exact-value checks. Packaged `clipboard` and `device` smokes use the queued fallback when live bridge metadata is absent; this proves module routing, Promise behavior, packaged shadowing, and privacy-safe surface shape, while true system clipboard/device access is still provided by the live Android provider path.

The v1.1 packaged bridge smoke declares `network`, `ocr`, `image`, `storage`, `ui`, and `work_manager` capabilities in packaged project metadata, then runs a JavaScript fake-transport fixture for controlled `fetch`, OCR over an opaque image handle, JSON `storage`/`storages` operations, UI layout/event/update/close calls, and `work_manager` metadata schedule/list/cancel serialization. It verifies the v1.1 bridge modules cannot be shadowed by project-local `node_modules` packages and that pending bridge calls drain to zero, proving these surfaces are not main-app-only without granting live Android provider authority in older templates. The `shell` module is intentionally not executed by queued fallback; packaged `shell.exec()` should remain a live-bridge-only capability. Packaged `work_manager` persistent registration also remains live-bridge-only; the fake-transport packaged smoke covers JavaScript serialization and routing only.

Future bridge work must keep the protocol and fakeable capability providers stable before exposing real screenshot, image decoding, template matching, or handle lifecycle implementations.

Every bridge call must look asynchronous to JavaScript and must reject cleanly on timeout or service destroy.

## Scheduled And Background Task Status

Node scripts can register controlled scheduled Node work through `require("work_manager")`.

Current policy:

- `require("work_manager")` is exposed as a Promise bridge module for `scheduleOnce`, `schedulePeriodic`, `cancel`, and `list`.
- S7-15 persists scoped descriptors as app-private WorkManager task records and enqueues one-shot or bounded periodic AndroidX WorkManager requests.
- S7-21 executes fired tasks through the WorkManager-owned `scheduled_runner` launch surface with `executionMode=scheduled`; default script launches that request `scheduled` still reject before user code.
- `work_manager` is a live-bridge capability. Queued host fallback rejects it with `ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED` so older packaged templates cannot create persistent tasks as one-way effects.
- Registered descriptors accept only `backend: "embedded"`, scoped relative `scriptPath` or project `entry`, bounded delay/interval/timeout values, and boolean `charging`, `network`, and `idle` constraints. Non-packaged entries must resolve to files inside the working directory; packaged entries may serialize metadata without a plaintext file at registration time.
- When a WorkManager task fires, the worker revalidates the scoped entry path, runs the script through `NativeNodeEmbeddedScriptService` as a scheduled runner task, and stores `lastRun`, `runHistory`, `lastRunStatus`, `lastFailure`, stdout/stderr snippets, timeout state, exit/error metadata, execution id, attempt, launch surface, selected native values, and Android quota policy diagnostics.
- Scheduled runs receive `AUTOJS6_NODE_SCHEDULED_TASK_ID`, `AUTOJS6_NODE_SCHEDULED_TASK_TYPE`, and `AUTOJS6_NODE_SCHEDULED_TASK_ATTEMPT` environment values.
- Workers return success or failure only and never request WorkManager retry. Cancel removes the task record and cancels the unique WorkManager work.
- `require("tasks")` is not exposed in the Embedded Node Safe Node Profile.
- Node scripts must not access raw WorkManager, AlarmManager, JobScheduler, BroadcastReceiver, PendingIntent, or IntentFilter objects.
- Existing Rhino task APIs remain unchanged.
- Future migration into the user-visible AutoJs6 task database, packaged receivers, boot/broadcast triggers, restart policies, and arbitrary long-running scheduled Node processes remain separate gates.
- Future broadcast, startup, boot, and package-event task support is deferred behind `docs/nodejs/design/BROADCAST_TASKS.md` and a user-visible permission flow.

See `docs/nodejs/design/WORK_MANAGER.md` for the current design decision.

## Error Code Compatibility

Errors keep existing Node-style or legacy `code` values for compatibility. AutoJs6-specific canonical codes are exposed through `error.autojs6Code`. When a legacy AutoJs6 code is preserved, `error.legacyCode` repeats the legacy `code`.

Examples:

- disabled builtin: `code=ERR_AUTOJS6_EMBEDDED_NODE_BUILTIN_DISABLED`, `autojs6Code=ERR_AUTOJS6_BUILTIN_DISABLED`
- disabled process API: `code=ERR_AUTOJS6_PROCESS_API_DISABLED`, `autojs6Code=ERR_AUTOJS6_PROCESS_API_DISABLED`
- disabled fetch/network: `code=ERR_AUTOJS6_EMBEDDED_NODE_FETCH_DISABLED`, `autojs6Code=ERR_AUTOJS6_NETWORK_DISABLED`
- scoped fs escape: `code=EPERM`, `autojs6Code=ERR_AUTOJS6_FS_PATH_ESCAPE`
- undeclared bridge capability: `code=ERR_AUTOJS6_BRIDGE_CAPABILITY_NOT_DECLARED`, `autojs6Code=ERR_AUTOJS6_BRIDGE_CAPABILITY_NOT_DECLARED`
- cancelled bridge call: `code=ERR_AUTOJS6_BRIDGE_CANCELLED`, `autojs6Code=ERR_AUTOJS6_BRIDGE_CANCELLED`
- malformed bridge request: `code=ERR_AUTOJS6_BRIDGE_INVALID_REQUEST`, `autojs6Code=ERR_AUTOJS6_BRIDGE_INVALID_REQUEST`
- bridge resource limit: `code=ERR_AUTOJS6_BRIDGE_RESOURCE_LIMIT`, `autojs6Code=ERR_AUTOJS6_BRIDGE_RESOURCE_LIMIT`
- bridge rate limit: `code=ERR_AUTOJS6_BRIDGE_RATE_LIMITED`, `autojs6Code=ERR_AUTOJS6_BRIDGE_RATE_LIMITED`
- embedded Node disabled: `errorCode=ERR_AUTOJS6_NODE_DISABLED`
- concurrent embedded execution: `errorCode=ERR_AUTOJS6_NODE_ENGINE_BUSY`
