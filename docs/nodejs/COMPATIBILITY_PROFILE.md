# AutoJs6 Node.js Compatibility Profile

This profile describes the current Safe Node surface. It is intentionally
smaller than desktop Node.js today, but Phase 13 defines a staged path toward
explicit Auto.js Pro and desktop Node.js compatibility profiles. This document
must be updated whenever runtime behavior changes.

For the GA-facing v1 release contract, see
[releases/NODE_PROFILE_V1_STABLE_SURFACE.md](releases/NODE_PROFILE_V1_STABLE_SURFACE.md). For user-visible caveats and
workarounds, see [releases/NODE_PROFILE_V1_KNOWN_ISSUES.md](releases/NODE_PROFILE_V1_KNOWN_ISSUES.md).

## Runtime Identity

- Profile name: `AutoJs6 Safe Node Profile`
- Embedded Node baseline: Node.js `v24.5.0` artifacts are present in the current native tree.
- Execution model: embedded `libnode.so` plus AutoJs6 bootstrap and CommonJS loader.
- Process model: normal scripts run through the primary `:nodejs_embedded`
  service process. S8-43 declares reserved process-pool slots, but the default
  runtime remains primary-only.
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

## Phase 13 Profile Model

The default runtime remains `AutoJs6 Safe Node Profile`. Phase 13 adds a
planning vocabulary for future opt-in profiles; these names are not runtime
promises until the matching surface manifest, capability gate, tests, packaged
smoke, and rollback evidence land.

| Profile | Purpose | Current default behavior |
| --- | --- | --- |
| `safe_default` | Existing Safe Node Profile for ordinary scripts and release defaults. | Enabled by default. |
| `pro_compat_opt_in` | Auto.js Pro-style compatibility for Java/Android interop, UI, floaty, tasks, plugins, and Rhino migration helpers. | Planned; metadata must fail closed until a task promotes each surface. |
| `desktop_compat_opt_in` | Broader desktop Node.js compatibility for raw network, wider fs, worker, WASI, inspector, child process, native addon, npm, and stdlib parity. | Planned; metadata must fail closed until a task promotes each surface. |
| `debug_unsafe_lab` | Debug-build validation profile for high-risk capabilities before release opt-in. | Debug-only planning lane; release builds must reject it. |
| `permanently_unsupported` | Capability reviewed and intentionally unavailable. | Stable denial with documentation and tests. |

Any move from denied or experimental behavior into `pro_compat_opt_in` or
`desktop_compat_opt_in` must follow
[CAPABILITY_RELAXATION_RUBRIC.md](security/CAPABILITY_RELAXATION_RUBRIC.md).
The Phase 13 source of truth is
[PHASE13_NODE_PRO_DESKTOP_PARITY_TASKS.md](reports/phase13/PHASE13_NODE_PRO_DESKTOP_PARITY_TASKS.md),
with surface ownership in
[phase13-surface-manifest.json](phase13/phase13-surface-manifest.json).

## Feature Flag Registry

`NodeRuntimeCapabilities.featureFlags` is the single registry for experimental, partial, disabled, and unsupported Node capability flags. Normal builds keep high and critical security features disabled by default except for the P13-36 native `vm` promotion needed for desktop npm compatibility. Project-level and packaged-APK overrides are audited and rejected unless a future design-gated task explicitly changes the registry. Release builds reject release-forbidden experimental Gradle flags; debug experimental builds remain the validation path.

| Flag id | Gradle property | Default | Build override | project.json override | Packaged APK override | Release forbidden | Security | Status |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `esm` | `autojs.nodejs.esm.experimental` | true | true | false | false | false | medium | partial |
| `dynamic_import` | `autojs.nodejs.dynamicImport.local` | true | true | false | false | false | medium | partial |
| `network` | `autojs.nodejs.network.experimental` | false | true | false | false | true | high | experimental |
| `raw_node_network_modules` | `autojs.nodejs.rawNetworkModules.experimental` | false | true | false | false | true | high | experimental |
| `websocket` | none | false | false | false | false | true | high | experimental |
| `worker_threads` | `autojs.nodejs.worker_threads.experimental` | false | true | false | false | true | high | experimental |
| `fs_streams` | none | true | false | false | false | false | medium | partial |
| `zlib_streams` | none | true | false | false | false | false | medium | partial |
| `vm` | none | true | false | false | false | true | high | partial |
| `inspector` | none | false | false | false | false | true | high | disabled |
| `java_interop` | `autojs.nodejs.javaInterop.experimental` | false | true | false | false | true | critical | experimental |
| `native_addon` | none | false | false | false | false | true | critical | unsupported |
| `child_process` | `autojs.nodejs.child_process.experimental` | false | true | false | false | true | high | experimental |

Runtime diagnostics expose the same state through `require("autojs6:profile").featureFlags`, `enabledExperimentalFlags`, and `deniedFeatureFlags`. The legacy `experimentalFlags` and `highRiskCapabilities` snapshots are retained for compatibility.

P13-12 also exposes a read-only `$autojs` global v1. In the Safe Node Profile,
`$autojs.version` and `$autojs.profile` are diagnostics, `$autojs.androidContext`
is a frozen unavailable descriptor, and `$autojs.java` is a gated facade over
the existing Java interop experiment. Normal builds keep `$autojs.java.enabled`
false and reject calls with `ERR_AUTOJS6_JAVA_INTEROP_DISABLED`.

P13-13 extends that experiment without changing the default profile. When
`autojs.nodejs.javaInterop.experimental=true` is enabled, `require("java")` and
`$autojs.java` support the allowlisted `java.lang.Math`, `java.util.UUID`, and
provider-owned `android.graphics.Rect` handle surface, including selected Rect
instance calls, field reads, and explicit `release` / `dispose` cleanup. Thread
dispatch and Pro profile promotion remain unpromoted.

P13-14 adds an explicit experimental `require("rhino").install({ experimental:
true })` Java proxy installer. It can install `Packages`, `java`, and `android`
proxies onto `globalThis` or a caller-provided target only when Java interop is
enabled. The default Safe Node Profile still rejects `rhino.install()` without
that explicit option, and `importClass`, `importPackage`, and `JavaAdapter`
remain unavailable.

P13-15 closes the `importClass` / `importPackage` decision by keeping Rhino
dynamic import semantics unsupported in Node. `require("rhino").importClass()`
and `.importPackage()` are explicit module helpers that throw
`ERR_AUTOJS6_RHINO_IMPORT_UNSUPPORTED`; scripts should migrate by assigning
local names from a `rhino.install({ experimental: true, target })` Java proxy
scope.

P13-16 records the `JavaAdapter` / `$java.defineClass` replacement decision.
`require("rhino").JavaAdapter()` is an explicit stable denial with
`ERR_AUTOJS6_RHINO_JAVA_ADAPTER_UNSUPPORTED`; no global `JavaAdapter` is
installed. `java.defineClass()` and `$autojs.java.defineClass()` are reserved
replacement names but currently throw `ERR_AUTOJS6_JAVA_METHOD_DENIED` without
bridge dispatch until a bounded proxy-interface provider, callback lifecycle,
thread dispatch, and packaged policy are proven.

## Supported Entry Points

- `"nodejs";` directive
- `"node";` directive
- `"nodejs ui";` directive metadata via `NodeEntryDirective` (P13-11 partial)
- `"nodejs ui-thread";` directive metadata via `NodeEntryDirective` (P13-11 partial)
- `.node.js`
- `.cjs`
- `.mjs` as a Node ESM entry
- `project.json` with `type: "node"` or `projectType: "node"`
- package bin launch paths resolved from Node project metadata
- encrypted script headers carrying the Node execution flag

P13-11 records Pro-style UI entry directives through Node project launch
metadata and diagnostics. Full Activity lifecycle launch, Back/Recents behavior,
and concrete UI-thread dispatch parity remain unpromoted.

P13-17 records the live `require("ui")` provider promotion gate. The direct
Activity-owned JSON provider has live device evidence for layout, events,
updates, close, and provider cleanup, while live packaged UI, Back/Recents,
orientation/configuration-change policy, relaunch/crash evidence, and packaged
capability/disclosure denial evidence remain unpromoted.

## Supported CommonJS Features

- `require()`
- `module.require()`
- `require("autojs6:profile")` for a read-only AutoJs6 profile descriptor
- global `$autojs` for the P13-12 read-only Pro-style diagnostics and gated
  Java entry facade
- `module.exports` and `exports`
- JSON modules
- local `.js`, `.cjs`, and `.json` modules
- `node_modules` traversal
- scoped packages
- package `main`
- package `exports`
- package `imports`
- package self-reference
- package condition matching currently uses active condition sets plus package key order: CommonJS activates `autojs6`, `require`, `node`, `default`; ESM activates `autojs6`, `import`, `node`, `default`
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
generated `.bin` shims, raw network/process requirements, and fs literal path
escapes before users try to run or package the project. The validator also
returns a dependency compatibility analyzer object with `compatible`,
`compatible with capability`, `experimental`, `unsupported`, or `unsafe`
status, required builtins, capability requirements, package export-condition
usage, and APK-builder preflight metadata. See [PROJECT_WIZARD.md](PROJECT_WIZARD.md).

## JS-Only Plugin Prototype

`require("plugins")` is reserved by the Safe Node Profile and cannot be
shadowed by `node_modules/plugins`.

Manager API:

```js
const plugins = require("plugins");
plugins.install("org.example.demo", {
  source: "./plugins/org.example.demo",
  config: { mode: "safe" }
});
const info = plugins.manifest("org.example.demo");
const entryPath = plugins.resolve("org.example.demo", "helper");
const demo = plugins.load("org.example.demo");
plugins.start("org.example.demo");
const status = plugins.status("org.example.demo");
plugins.stop("org.example.demo");
plugins.disable("org.example.demo");
plugins.enable("org.example.demo");
plugins.unload("org.example.demo");
plugins.reload("org.example.demo", { debug: true });
```

The manager registers only project-local directories under `./plugins/<id>`.
Each plugin must provide `package.json` metadata with `autojs6.plugin` and an
`autojs6.plugin.permissions` string array. `autojs6.plugin.dependencies`
declares plugin dependency ids for diagnostics only, and
`autojs6.plugin.exports` maps named exported Node modules to plugin-local
`.js` or `.cjs` files. `plugins.install`, `load`, `resolve`, `enable`,
`configure`, and `unload` require an enforced project/package capability
manifest that declares the exact `plugin.<id>` capability and every capability
requested by the plugin manifest before plugin code executes or cached modules
are exposed.

Only `.js` and `.cjs` plugin entries are accepted. Plugin-relative `require()`
uses the plugin directory as its resolver root, so plugin code cannot use
relative package resolution to escape into sibling project files.
Each plugin receives a path-rooted namespace such as
`plugin:org.example.demo`, an explicit effective capability subset, and
`load`/`start`/`stop`/`unload` state. `plugins.start(id)` calls
`exports.start(ctx)` when present; `plugins.stop(id)` calls
`exports.stop(ctx)` when present and then releases resources registered through
`ctx.resources`. The resource registry owns timers and explicitly tracked
`provider`, `ui`, `socket`, `image`, and `generic` handles for the current
plugin execution. `plugins.reload(id, { debug: true })` is an explicit
debug-only hot reload path; calls without `debug: true` reject with
`ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED`.
`install()` does not download, copy, or persist packages; it validates and
registers an already-present sanitized local package for the current execution.
`disable()` blocks future loads, stops the plugin if needed, releases owned
resources, and unloads cached plugin modules. This is a local JS-only manager,
not a signed archive verifier, Android plugin adapter, native provider model,
isolated process/context, download installer, update channel, or marketplace
runtime. The packaged APK smoke covers only already-packaged project-local
JS-only plugins with explicit `plugin.<id>` and declared bridge capabilities;
it does not enable remote installs, native plugin archives, or Android provider
plugin adapters.

## Execution Queue

Embedded Node is not multi-instance inside a single `:nodejs_embedded` process. Ordinary starts now use bounded queueing when the target process slot is busy, so rapid repeated runs wait for the active execution to finish instead of failing with `ERR_AUTOJS6_NODE_ENGINE_BUSY`.

The queue currently allows up to 16 waiting executions, supports cancellation by execution id for explicit enqueue participants, exposes queue status, and separates queue timeout from script execution timeout. Each dequeued item starts a normal one-shot embedded service execution after the previous service process has settled. Explicit `reject_when_busy` remains available for fail-fast diagnostics and still reports `ERR_AUTOJS6_NODE_ENGINE_BUSY`.

Stable queue errors:

- `ERR_AUTOJS6_NODE_QUEUE_FULL`
- `ERR_AUTOJS6_NODE_QUEUE_TIMEOUT`
- `ERR_AUTOJS6_NODE_QUEUE_CANCELLED`

See [design/EXECUTION_QUEUE.md](design/EXECUTION_QUEUE.md).

## Embedded Process Pool

Node Profile v1.1 includes the S8-43 process-pool policy gate, not a promoted
multi-runtime pool. The manifest declares three non-exported embedded script
service components:

- `primary`: `NativeNodeEmbeddedScriptService` in `:nodejs_embedded`
- `long_running`: `NativeNodeEmbeddedScriptServiceSlot1` in
  `:nodejs_embedded_1`
- `overflow_1`: `NativeNodeEmbeddedScriptServiceSlot2` in
  `:nodejs_embedded_2`

`NodeEmbeddedProcessPoolPolicy` keeps the default runtime mode `primary_only`,
with one active slot. Phase 11 adds an internal explicit one-shot diagnostic
route for `primary` and `overflow_1`; selecting `overflow_1` runs in
`:nodejs_embedded_2` and reports `explicit_two_slot` diagnostics. The device
memory profile is still diagnostic, `long_running` remains reserved, and
project metadata or packaged metadata cannot select additional slots.

Script results expose `embedded_script.process_pool.*` diagnostics for schema,
status, selected slot, declared slot count, effective limit, recommended limit,
long-running reservation, queue policy, crash-isolation policy, and memory
profile.

See [design/PROCESS_POOL.md](design/PROCESS_POOL.md).

## Long-Running Service Mode

Long-running Node service mode is only partially available through the explicit
interactive foreground runtime. Pending timers, WebSocket handles, network
requests, or open bridge resources do not convert a normal script into a
service.

Current behavior remains:

- normal embedded scripts default to a `300000` ms timeout and still accept an
  explicit `timeoutMs` override
- timeout produces the existing timeout diagnostic shape
- `EmbeddedScriptRequest.executionMode` is serialized through the service
  request spool and normalized through the Phase 7 mode state machine
- canonical modes are `one_shot`, `interactive_long_running`, `scheduled`,
  `worker_computation`, and the design-gated `node_sandboxed` POC
- `one_shot` is the only supported executable value for normal script launches
- `interactive_long_running` is supported only by the explicit
  `interactive_session` launch surface used by
  `NativeNodeEmbeddedScriptService.startInteractiveLongRunning(...)` or the
  packaged long-running opt-in surface used by packaged projects that declare
  `node.executionMode=interactive_long_running`
- `scheduled` is supported only by the WorkManager-owned
  `scheduled_runner` launch surface used for fired `work_manager` tasks
- direct script `interactiveLongRunning`, legacy `long_running`/`service`,
  `scheduled`, `workerComputation`, and `nodeSandboxed` requests still reject
  before user code runs with `ERR_AUTOJS6_EMBEDDED_NODE_UNSUPPORTED_OPTION`
- rejection native values include timeout policy, foreground requirement,
  notification requirement, allowed-provider list, restart policy, quota
  profile, stop semantics, launch surface, and supported launch surfaces
- interactive status snapshots include runtime duration, Stop action, active
  resource summary, restart policy `never`, and `automaticRestart=false`
- `require("autojs6:lifecycle")` is available only inside the explicit
  interactive or packaged long-running runtime and supports JSON-only
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
- packaged Node entries remain one-shot unless they explicitly opt in with
  `node.executionMode=interactive_long_running`; that packaged long-running
  path is partial until P13-07 device restore/cleanup and Android policy
  evidence land

The future design gate is documented in [design/LONG_RUNNING_SERVICE.md](design/LONG_RUNNING_SERVICE.md).

Unsupported or rejected module source forms:

- native `.node` addon entries, rejected with `ERR_AUTOJS6_NATIVE_ADDON_DISABLED` from direct relative imports, `module.createRequire()`, package `main`, package `exports`, package `imports`, and package subpath imports
- `.node` files anywhere inside a JS-only plugin directory, rejected before the
  plugin entry executes
- `plugins.load(id)` without exact `plugin.<id>` project opt-in or without the
  plugin-requested capabilities
- plugin entry paths that are absolute, protocol-based, NUL-containing, or
  escape the plugin directory
- `plugins.reload(id)` unless `options.debug === true`
- static ESM import execution only from ESM entries and ESM graph modules
- dynamic import from `eval`, `Function`, URL/protocol specifiers, absolute paths, or outside `workingDirectory`
- package export/import targets that escape the package root
- unsupported package export/import extensions

P13-39 records native addon loading as partial. `nativeAddonProfile` is
diagnostic metadata only: `.node` loading, addon resolution, `process.dlopen`,
`process.binding`, native builds, and runtime download-exec remain denied by
default. Any future Desktop-profile native addon support must be signed and
packaged-only, tied to ABI and hash allowlists, covered by dependency scan and
APK preflight, and isolated against native crashes before promotion.

The reusable compatibility corpus lives under `app/src/androidTest/assets/node_compat_corpus/` and is executed by
`NodeCompatCorpusInstrumentationTest`. It covers pure JavaScript CommonJS package patterns that should remain stable across
future builtin and bridge work. The S8-19 module conformance v2 corpus lives under
`app/src/androidTest/assets/node_compat_corpus_v2/` and is executed by the same test class. It freezes the v1.1 mixed-module
boundary for CJS `require(esm)`, async ESM graph rejection, JSON import attributes, ESM/CJS cycles, scoped
`import.meta.resolve()`, the `autojs6` package condition, `.mts`/`.cts` mixed graphs, and packaged moduleSources mixed
graphs.

The fixed real package corpus lives under `app/src/androidTest/assets/npm_compat_real/` and is executed by
`NodeRealNpmCompatInstrumentationTest`. It vendors offline, license-noted CommonJS package snapshots that cover tested
patterns rather than broad npm compatibility claims. The S7-48 expansion grew the measurable corpus from 7 to 13
fixtures, the S8-46 expansion grew it from 13 to 20 fixtures, and S9-46 grows it from 20 to 30 fixtures:

- small utility and nested dependency packages
- semver parsing plus querystring/URL utility use
- glob/path matching plus crypto hash utility use
- stream utility plus zlib buffer utility use
- core builtin feature detection for `constants`, `util/types`, `events`, and `diagnostics_channel`
- ESM-only dependency import and hybrid CJS/ESM package condition selection
- Buffer utility, fetch wrapper fallback, and assertion helper patterns
- `AsyncLocalStorage` propagation across promise and timer boundaries
- `stream/promises` pipeline through a transform package
- WebCrypto digest helper use through `crypto.webcrypto.subtle`
- worker CPU package behavior with graceful fallback while `worker_threads` stays default-disabled
- test-runner and database-abstraction package patterns
- native TypeScript source project shape with runtime-safe CommonJS fallback
- CommonJS `require()` of a synchronous ESM package namespace
- packages using default-denied `node:http`, Web Platform `FormData`/`fetch`,
  scoped `FileHandle`, `fs.watch()` lifecycle, and compile-cache-shaped module
  graph reuse
- WebAssembly compile/instantiate, Buffer-backed WASM bytes, policy-aware raw
  WASI detection, inspector-free diagnostics, promise-pool concurrency, wider
  compile-cache graphs, TypeScript+WASM metadata, worker+WASM fallback, larger
  ESM dependency fanout, and package-integrity hashing

These fixtures are intentionally checked in. Tests must not fetch packages from the network, run install/postinstall
scripts, or load native addons. Fixture runners remain CommonJS `test.cjs` files; v1.1 permits ESM-only dependency
packages below `node_modules` so the corpus can exercise partial ESM package import without converting the runner itself
to ESM.

The fixed HTTP compatibility corpus lives under
`app/src/androidTest/assets/http_compat_corpus/` and is executed by
`NodeHttpNpmCorpusInstrumentationTest`. It adds nine offline npm-shaped
fixtures for packages that use the controlled `node:http` / `node:https`
client facade: `http.get()`, `https.request()`, `IncomingMessage` stream
consumption, `application/x-www-form-urlencoded` POST bodies, request-level
`AbortSignal`, safe `http.Agent` / `https.Agent` options,
follow-redirects-style redirect handling, TLS provider failure handling, and
per-request proxy configuration denial. The corpus runs with a fake controlled
fetch bridge transport and an enforced `network` manifest; it does not install
packages, use live network I/O, load native addons, or expose raw sockets.

`tools/nodejs/differential/run-differential-tests.js` runs the same fixed fixtures under desktop Node 24 and writes a
JSON/Markdown report under `build/reports/nodejs/differential/`, the v1.1 npm summary report
`build/reports/nodejs/npm-compat-v1.1.md`, and the Task 188 measurement report
`build/reports/nodejs/v1.1-differential.md`. When supplied with `realnpm.differential.result=...` lines emitted by
`NodeRealNpmCompatInstrumentationTest` through `--autojs-log`, `--autojs-json`, or
`--autojs-connected-results`, it compares normalized stdout, exit code, error code, and error message pattern, then
classifies differences as matched, expected AutoJs6 restriction, unsupported, flaky, or bug.

`tools/nodejs/differential/run-modern-core-differential.js` runs the fixed S7-36 modern core corpus under desktop Node
24 and writes `build/reports/nodejs/differential/modern-core-differential.json` plus Markdown. When supplied with
`moderncore.differential.result=...` lines emitted by `NodeModernCoreDifferentialInstrumentationTest`, it classifies
ESM, `process.permission`, `AsyncLocalStorage`, `perf_hooks`, `diagnostics_channel`, `node:test`, `timers/promises`,
`stream/promises`, WebCrypto, and the database compatibility module as `exact match`, `intentional AutoJs6 restriction`,
`partial`, or `bug`.

`:app:bundleNodeDependencies` is the host-side dependency bundling gate. It consumes a project that already has
`package.json` and `node_modules`, validates the tree without running package code, copies a sanitized bundle to
`app/build/nodejs/dependency-bundle/project/`, and writes a manifest plus size/rejection reports under
`app/build/reports/nodejs/dependencies/`. The report set includes `dependency-manifest.json`,
`dependency-sbom-lite.json`, `dependency-size-report.txt`, and `dependency-rejections.txt`.
`:app:manageNodeDependencies` is the S8-45 integrated dependency manager action for lockfile projects. It runs
host-side `npm ci --ignore-scripts --no-bin-links --install-links --no-audit --fund=false` in a clean work directory,
requires `package-lock.json`, verifies `package.json` and the lockfile are not mutated, feeds the installed tree through
the same sanitizer, writes the manifest, SBOM-lite, compatibility, size, rejection, and
`dependency-manager-report.json` files under `app/build/reports/nodejs/dependency-manager/`, updates the
content-addressed dependency cache, and verifies the result can be packaged as `assets/project`. The manager report
schema is `autojs6-node-dependency-manager-v1` and records `packageLifecycleScriptsExecuted=false`.
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

- absolute paths are accepted only when they resolve inside the scoped
  `workingDirectory`/`sandboxRoot`; outside absolute paths remain denied
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

Node-only limited builtins currently include:

- `node:test`

`node:test` intentionally has no bare `test` alias; `require("test")` remains
ordinary package resolution.

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
are ignored, and controlled `process.getBuiltinModule()` returns the same limited wrappers without bypassing policy. The
`events` wrapper exposes `EventEmitter` and helper functions such as `once()`/`on()`, but does not expose
`EventEmitterAsyncResource` or `EventTarget`; Web-style `EventTarget` remains a global. `diagnostics_channel` exposes a
bounded publish/subscribe/tracing surface without raw subscriber internals.
Package-root requests with a trailing slash, such as `require("punycode/")`,
are treated as npm package requests and resolve through `node_modules`
instead of the exact builtin-name path. This matches common desktop Node/npm
compatibility patterns while keeping exact bare names such as
`require("punycode")` and `require("node:punycode")` on the controlled builtin
wrapper path.
On no-ICU embedded Node builds, `globalThis.TextDecoder` and `require("util").TextDecoder`
include an AutoJs6 Unicode fallback for `fatal: true` with `utf-8`,
`utf-16le`, and `utf-16be`. This keeps pure-JS npm packages from failing at
constructor time while still not claiming full ICU encoding coverage.
S7-32 adds execution-scoped runtime telemetry channels for bridge RPC and
controlled fetch:

- `autojs6.bridge.rpc.start`
- `autojs6.bridge.rpc.end`
- `autojs6.bridge.rpc.error`
- `autojs6.fetch.request.start`
- `autojs6.fetch.request.end`
- `autojs6.fetch.request.error`

Runtime-published diagnostics messages are frozen and contain only primitive
fields or primitive arrays, such as module, method, phase/status, timeout,
duration, policy counters, HTTP status, URL scheme, and error code. They do not
publish bridge args, headers, full URLs, request/response bodies, result
objects, Android objects, or provider-owned resources.

`perf_hooks` exposes `performance.now`, mark/measure entry APIs,
`PerformanceObserver` for `mark`/`measure`, and a safe
`performance.eventLoopUtilization()` wrapper when the embedded Node runtime
provides it. Event-loop utilization returns a frozen numeric snapshot. Histogram,
timerify, node timing, resource timing, and event-loop delay monitoring remain
hidden.

`async_hooks` exposes only `AsyncLocalStorage` and `AsyncResource`. It does not
expose `createHook`, async-id introspection, provider tables, or raw execution
resources. The S7-31 compatibility gate locks context propagation across
Promise chains, `process.nextTick`, `queueMicrotask`, timers,
`timers/promises`, scoped `fs` callbacks, `fs/promises`, AutoJs bridge RPC, and
controlled fetch's bridge transport path. Querying or using async context does
not grant bridge, network, filesystem, worker, or Android permissions. Stores
are expected to be cleared after `AsyncLocalStorage.run()` completes. Where
native workers are available, the worker-pool parent promise boundary preserves
the parent context; worker runtimes still use their separate restricted profile
and do not receive implicit AutoJs bridge authority.

`node:test` exposes an AutoJs6 embedded subset rather than Node's full desktop
test runner. It supports single-runtime `test`, `describe`/`suite`, `it`,
`before`/`after`, `beforeEach`/`afterEach`, `skip`, `todo`, per-test timeout
options, assertion failure reporting, and a TAP reporter. The subset does not
support process-isolated test files, file discovery, watch mode, coverage,
native runner `run()`, mocks, or snapshots. Tests run sequentially in the owning
Node runtime, and completed tests are checked for newly pending timer activity
so late async work is reported as a test failure. `node:test/reporters` remains
disabled.

S7-34 locks a promise and Web Platform sweep for the supported Node 24 subset.
`timers/promises` supports `setTimeout`, `setImmediate`, `setInterval`,
`scheduler.wait`, and `scheduler.yield`, including AbortSignal rejection
semantics. `stream/promises` supports `pipeline()` and `finished()` over
already permitted streams. `readline/promises` supports `Interface`,
`Readline`, and `createInterface()` over already permitted streams. The Web
Platform sweep covers `AbortSignal.abort()`, `AbortSignal.timeout()`,
`AbortSignal.any()`, `structuredClone()`, and the limited global `crypto`
surface. Controlled `process.getBuiltinModule()` keeps builtin shadows ignored,
and these APIs do not grant raw network, process, native addon,
Android, or unscoped filesystem authority.

S8-20 adds a focused Web Platform globals alignment gate for the constructors
used by controlled HTTP compatibility work: `Headers`, `Request`, `Response`,
`FormData`, `Blob`, `File`, `URLPattern`, `DOMException`, `EventTarget`,
`MessageEvent`, `CompressionStream`, and `DecompressionStream`. The gate covers
headers, request/response body consumption, form entries, URLPattern groups,
DOM/Event shapes, gzip compression/decompression roundtrip, and descriptor
locking. Global `fetch()` remains denied in the default controlled-network-off
profile, and these globals do not expose raw sockets, native addons, process
spawning, Java/Android objects, or unscoped filesystem authority.

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
win over `node_modules/buffer` shadow packages. The module export uses a frozen
ordinary object so legacy npm packages such as `safer-buffer` can call
`require("buffer").hasOwnProperty("Buffer")` without treating the wrapper as a
mutable authority boundary.

The `fs` wrapper exposes a scoped stream MVP through `createReadStream()` and
`createWriteStream()`. These streams use the same scoped path policy as the rest
of `fs`: absolute paths are allowed only inside the scoped working directory,
while path escape, symlink escape, and Android/device roots such as `/proc`,
`/sys`, and `/dev` remain denied. Active scoped fs streams are tracked in
runtime diagnostics and are destroyed during runtime cleanup so a timed-out or
stopped service does not poison the next execution.

The scoped `fs.promises.open()` surface returns AutoJs6-managed `FileHandle`
objects. The v1.1 S8-27 gate covers `read()`, `write()`, `readFile()`,
`writeFile()`, `appendFile()`, `truncate()`, `stat()`, `datasync()`, `sync()`,
and `close()`, plus promise-level `readFile(fileHandle)` and
`appendFile(fileHandle, data)`. FileHandle access stays execution-owned:
closed handles and non-owned numeric descriptors reject with `EBADF`, path
escapes keep scoped-fs denial codes, and timeout cleanup prevents unclosed
handles from poisoning the next embedded Node execution.

The callback and sync fd API subset is also scoped and execution-owned. S8-28
covers `open`/`openSync`, `close`/`closeSync`, `read`/`readSync`,
`write`/`writeSync`, `fstat`/`fstatSync`, and
`ftruncate`/`ftruncateSync`. Numeric fd values are compatibility tokens backed
by the scoped descriptor registry; invalid, closed, or foreign descriptors
reject with `EBADF` before raw native fd access.

The scoped directory iteration subset covers `opendir`, `opendirSync`, and
`fs.promises.opendir`. S8-29 keeps `Dir` handles execution-owned while
supporting callback, sync, promise, and async-iteration reads, `Dirent`
compatibility snapshots, buffer names, and bounded recursive walks. Closed
directory handles reject with `ERR_DIR_CLOSED`, while relative escapes,
absolute outside paths, file targets, missing directories, and NUL paths keep
their stable scoped fs or Node error codes.

The scoped watcher subset covers `fs.watch`, `fs.promises.watch`,
`fs.watchFile`, and `fs.unwatchFile`. S8-30 keeps watchers execution-owned and
subject to scoped path validation, a 16 active watcher quota, a 64 events per
1000 ms per-watcher rate limit, AbortSignal cleanup, and service-stop cleanup.
Recursive watch remains unsupported. Android watcher events may be coalesced
and may report `change`, `rename`, or a null filename; scripts should treat
watchers as notification hints and re-read scoped state when exact contents
matter.

The scoped safe file operations subset covers `fs.cp`/`cpSync`,
`fs.mkdtemp`/`mkdtempSync`, `fs.statfs`/`statfsSync`, and the constrained
`glob` surface. S8-31 verifies callback, sync, and promise forms for copy,
temporary-directory, filesystem-stat, and glob operations while preserving
scoped path validation for relative escapes, absolute outside paths, NUL paths,
external symlinks, and Android/device roots. `glob` stays limited to safe local
patterns and rejects parent-segment, absolute, protocol, invalid exclude, and
`followSymlinks` forms. `link`, `chmod`, `chown`, and `symlink` are not promoted
by S8-31; existing wrappers remain bounded by scoped path policy and platform
permission failures.

P13-32 records filesystem profile expansion as partial. The current Safe
profile remains `workingDirectory` scoped and exposes
`autojs6:profile.filesystemProfile` only as frozen diagnostics:
`mode="scoped_working_directory"` and `additionalRoots=[]`. Scoped absolute
paths that resolve under the current sandbox are accepted so npm packages can
read package resources via `__dirname`, while absolute paths outside the
sandbox still reject with the absolute-path policy. User-authorized roots,
`/sdcard`, SAF/document roots, app-private roots, grant persistence,
revocation, and packaged expanded-root evidence remain unpromoted.

P13-33 records desktop fs advanced APIs as partial. The same diagnostic object
now reports `filesystemProfile.advancedApis` for streams, `FileHandle`, fd
APIs, `opendir`, watchers, and `realpath`, plus watcher limits. These APIs stay
scoped and execution-owned: `rawFd=false`, recursive watch remains unsupported,
and broader roots still depend on the future P13-32 root authority provider.

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

`builtinModules` is a frozen list of AutoJs6 known safe or limited builtins, not a promise of unrestricted desktop Node behavior. Network-shaped limited modules can appear for feature detection and stable denial wrappers. Raw network I/O remains disabled in the Safe profile; debug/build-time runs may explicitly enable selected native Node network builtins with `autojs.nodejs.rawNetworkModules.experimental=true`.

The following Node loader internals are not exposed:

- `Module`
- `_load`
- `_resolveFilename`
- `_compile`
- `wrap`
- `globalPaths`
- `register`
- `registerHooks`
- `enableCompileCache`
- `flushCompileCache`
- `constants.compileCacheStatus`
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

## Module Compile Cache

Node Profile v1.1 enables Node's module compile cache internally for embedded
script executions. AutoJs6 calls the real internal
`node:module.enableCompileCache(cacheDir)` from the bootstrap before user code
starts, but this API is not exposed through the Safe Node `module` wrapper.

The cache directory is app-private only:

```text
<app code cache>/node-compile-cache/<runtime-key>/<project-key>
```

Runtime key material includes the Node/header/runtime slot identity, Android
ABI list, AutoJs6 runtime profile version, effective feature flags, and loader
versions. Content key diagnostics include the entry source hash plus sorted
`moduleSources` and runtime module source hashes after TypeScript stripping.
Incompatible runtime or loader upgrades produce a new runtime key, and old
directories are pruned by the app-private quota policy.

Cache failure is fail-soft. Directory preparation errors, unavailable Node API
state, or enable/flush errors are recorded in `embedded_script.compile_cache.*`
native values and must not change script correctness. The Doctor report exposes
the compile-cache schema, default status, app-private base directory, quota,
diagnostic prefix, and hit/miss status note.

`require("autojs6:profile").compileCache` is a frozen diagnostic snapshot with
`appPrivateOnly=true` and `userControlExposed=false`. User scripts cannot
redirect or disable the internal cache through `process.env`: AutoJs6 filters
`AUTOJS6_NODE_COMPILE_CACHE_*`, `NODE_COMPILE_CACHE`, and
`NODE_DISABLE_COMPILE_CACHE` from the controlled environment.

AutoJs6 still uses its scoped custom loader for user-managed CJS, ESM, and
moduleSources graphs. Node does not expose reliable per-module hit/miss counters
for that custom-loader path, so diagnostics report
`hitMissStatus=unavailable_custom_loader` instead of fabricated counts.

## Bootstrap Startup Snapshot

Startup snapshots are not enabled in Node Profile v1.1. The current embedded
runtime creates a fresh isolate, context, environment, and request-local
bootstrap source for each execution. Normal bootstrap is both the current path
and the required fallback path.

The snapshot design gate allows only immutable helper/schema code as a future
candidate. It must not snapshot V8 `Context` objects, Android `Context` or
Binder objects, providers, user paths, project roots, permissions, execution
ids, request env, `moduleSources`, packaged graph contents, open resources,
or project module cache state.

Future compatibility claims require CJS, ESM, TypeScript-after-stripping,
dynamic import, packaged graph, compile-cache interaction, and cross-execution
isolation tests with snapshot enabled. See
`docs/nodejs/design/BOOTSTRAP_STARTUP_SNAPSHOT.md`.

## Runtime Prewarm Pool

Node Profile v1.1 includes an explicit process-only prewarm MVP for the
embedded service process. `NativeNodeEmbeddedScriptService.prewarmRuntime()`
starts one bounded `:nodejs_embedded` foreground service prewarm, then the next
script consumes it once.

This is not a pooled Node runtime. Prewarm loads no V8 isolate, Node
environment, user source, module graph, runtime module source, bridge provider,
permission, cwd, env, or execution-owned resource. The consuming script still
uses the normal request preparation, validation, scoped fs, env filtering,
bridge setup, native bootstrap, and process-exit cleanup path.

Current diagnostics are under `embedded_script.prewarm_pool.*`. Reusable
V8/Node environment pooling remains deferred until isolation and cleanup tests
prove no cross-script leakage.

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
- unrestricted `process.getBuiltinModule` bypass
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

Network-related limited builtins may exist for feature detection and stable denial errors. That is not permission to perform network I/O unless the debug/build-time raw Node network module opt-in is explicitly enabled.

WebAssembly is available as a pure JavaScript/V8 capability. The Phase 9
`NodeWasmWasiInstrumentationTest` verifies `WebAssembly.compile`,
`WebAssembly.instantiate`, and scoped `.wasm` file loading. Raw `wasi` /
`node:wasi` remains registry-disabled and cannot be shadowed by
`node_modules/wasi`; a future `autojs6:wasi` facade must route preopens through
the scoped-fs broker before promotion. See
`docs/nodejs/reports/phase9/WASM_WASI.md`.

P13-38 records controlled WASI profile as partial. The runtime exposes
`autojs6:profile.wasiProfile` to document that pure WebAssembly remains
available while raw `wasi` / `node:wasi` stays denied. Any future controlled
WASI profile must prove scoped or user-authorized preopens, virtual fd and
bounded stdio behavior, explicit args, empty-by-default env, limited
clock/random, network/process-exit denial, resource budgets, worker
integration, and packaged `.wasm`/WASI behavior before promotion. For P13-38
v1.1, pure WebAssembly is the supported runtime path and raw/controlled WASI
APIs stay stable-denied.

`vm` is enabled as the native Node builtin for desktop npm compatibility, including `require("vm")`, `require("node:vm")`, `require.resolve("vm")`, and `require.resolve("node:vm")`. Local `node_modules/vm` packages cannot shadow the builtin. This is not a sandbox boundary; `autojs6:profile.vmProfile` records the native surface and remaining memory/packaged evidence. See `docs/nodejs/design/VM.md`.

`inspector` is intentionally disabled, including debugger port binding and script-visible inspector APIs. S8-37 adds a focused disabled-state gate that proves metadata cannot enable the feature flag, `node_modules/inspector` cannot shadow the policy, process debugging APIs remain inert, and `NODE_OPTIONS` / `NODE_INSPECT_RESUME_ON_START` are filtered from script-visible `process.env`. S8-40 extends the boundary by stripping Node startup/debugger/cache env keys before native startup, initializing embedded Node with `NODE_OPTIONS` parsing disabled, proving hostile `--inspect` host/port attempts leave no checked listener after stop or timeout, and proving profiler artifacts / sensitive marker data remain absent. A future positive implementation must be debug-build-only and must prove explicit user action, loopback binding, visible/revocable active-session state, lifecycle close, and packaged APK denial. See `docs/nodejs/design/INSPECTOR.md`, `docs/nodejs/reports/phase8/INSPECTOR_DESIGN_GATE.md`, and `docs/nodejs/reports/phase8/INSPECTOR_SECURITY_GATE.md`.

ADB forwarding, Chrome DevTools attach, CPU profiles, heap snapshots, allocation sampling, and inspector-owned profiling exports are also disabled. S8-38 and S8-39 are documented as deferred gates because the current runtime exposes no inspector endpoint or profiler channel. See `docs/nodejs/reports/phase8/DEVTOOLS_ADB_FORWARD_GATE.md` and `docs/nodejs/reports/phase8/INSPECTOR_PROFILING_GATE.md`.

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
- CJS `require()` of an asynchronous ESM graph: `ERR_REQUIRE_ASYNC_MODULE`

Default `require("autojs6:profile")` reports `esm=true`, `dynamicImport=true`, `featureFlags.esm.status="partial"`, `featureFlags.dynamic_import.status="partial"`, `featureFlags.dynamic_import.defaultEnabled=true`, and `experimentalFlags.dynamicImport=true`. The legacy `experimentalFlags.esm` and `experimentalFlags.dynamicImport` snapshots remain for compatibility with older diagnostics.

Node Profile v1.1 partial ESM supports:

- `.mjs` entry execution
- package `type=module` entry execution for `.js`
- `.js` entry execution through partial ESM when the entry source contains
  static ESM syntax or `import.meta`, including `$remote/...` editor sources
- local static imports of `.mjs` files inside `workingDirectory`, including
  common multiline and compact default/named import declarations
- default import from scoped CommonJS modules through the existing CommonJS loader
- supported builtin imports such as `node:path` and `node:events`
- bare package imports that resolve package `exports` with the ESM active condition set `autojs6`, `import`, `node`, `default`, preserving package key order, and then load a CommonJS target
- package `imports` aliases with the same ESM active condition set and package key order
- package `exports`/`imports` targets that point to `.mjs` files inside the package root
- nested package imports and package self-reference under ESM mode
- CommonJS `require()` of synchronous partial ESM targets, returning the ESM namespace object while preserving CommonJS package condition matching
- ESM modules may import `createRequire` from `module` and declare a local
  `require`, matching package entrypoints such as `css-tree`
- ESM JSON imports with `with { type: "json" }`, sharing the CommonJS JSON cache object
- scoped `import.meta.url`, `import.meta.filename`, `import.meta.dirname`, `import.meta.main`, and `import.meta.resolve(specifier)`
- simple named exports, including identifier declarations and object/array
  destructuring declaration exports used by package entrypoints such as `css-tree`
- default-exported object literals may contain keyword property names such as
  `import` and `export`, matching package entrypoints such as `css-tree`
- simple re-export syntax: `export { name as alias } from`, `export * from`, and `export * as namespace from`
- block and line comments inside named import/export/re-export specifier lists
- simple top-level await

CommonJS package condition matching uses active conditions `autojs6`, `require`, `node`, `default` while preserving package key order. `development` and `production` are recognized for diagnostics but are not active by default.

Partial ESM still rejects CJS `require()` of asynchronous ESM graphs containing `await` or dynamic `import()`, legacy ESM JSON import assertions, missing/unknown import attributes, import attributes on non-JSON modules, custom loaders, network/file/unrestricted URL imports, disabled builtins, any import outside `workingDirectory`, and Node's full `import.meta` loader surface beyond the scoped metadata and resolver subset. Controlled `data:` URL imports are partial for inline JavaScript and JSON modules only; JSON data modules require `with { type: "json" }`.

Dynamic `import()` is local-only partial support. It allows local relative `.mjs`, CommonJS, and JSON modules, allowed builtins, controlled JavaScript/JSON `data:` URL modules, and packages resolved inside `workingDirectory` with ESM package condition matching. Dynamic JSON imports require `{ with: { type: "json" } }`, including JSON data URLs. It rejects disabled builtins, absolute paths, `file:` URLs, network URLs, unsupported data URL MIME types, data-module relative/package imports, path escapes, loader hooks, and dynamic-code sources such as `eval` and `Function` with stable AutoJs6 errors. `import.meta.resolve()` follows the same scoped ESM resolver, returning `file:` URLs for local/package targets and `node:` URLs for allowed builtins while keeping disabled builtins denied.

Runtime diagnostics include `esmEnabled`, `esmEntry`, `esmModuleGraphRoot`, `esmModuleGraphSize`, `esmModuleGraphModules`, and `dynamicImportEnabled` in the embedded script result envelope.

See `docs/nodejs/design/ESM.md` for package `type`, CJS/ESM interop, top-level await, dynamic import, `import.meta.url`, package condition, moduleSources, encrypted graph, packaged APK, and stack trace decisions.

## moduleSources Budget

Injected `moduleSources` are bounded before native runtime startup. The v1 limits are:

- module count: `8192`
- per-module UTF-8 source bytes: `16777216`
- total UTF-8 source bytes: `67108864`

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
- `http.request()` / `http.get()` and `https.request()` / `https.get()` keep the existing synchronous disabled error unless the execution has an enforced bridge manifest declaring `network`
- with an enforced `network` manifest, `http` / `https` client calls are a controlled fetch facade; the default Android provider can still reject with `ERR_AUTOJS6_NETWORK_DISABLED` while live network is disabled
- `http.createServer()`, `https.createServer()`, `listen()`, raw `net`, raw `tls`, `dns`, and `dns/promises` operations remain unavailable for I/O
- `dgram` remains a disabled builtin
- raw sockets, servers, DNS queries, TLS sessions, datagrams, `CONNECT` tunneling, socket upgrades, and unrestricted HTTP requests remain unavailable

Use global `fetch()` or `require("fetch")` for controlled request experiments, use `require("axios")` for the AutoJs6-owned axios-shaped compatibility adapter, use `require("http")` / `require("https")` only for the controlled client facade, and use `require("websocket")` for the controlled WebSocket bridge. Userland HTTP clients cannot assume desktop Node socket adapters in the Safe profile. Raw Node networking is not part of the Safe Node Profile: bare names and `node:` aliases are intercepted, and local `node_modules/http`, `node_modules/https`, `node_modules/net`, `node_modules/tls`, `node_modules/dns`, `node_modules/dns/promises`, `node_modules/dgram`, or `node_modules/axios` fixtures cannot bypass the policy. Debug/build-time runs can explicitly enable native Node `dns`, `dns/promises`, `http`, `https`, `net`, and `tls` with `autojs.nodejs.rawNetworkModules.experimental=true`.

Current Safe-profile `require("autojs6:profile")` reports `network=false`, `rawNodeNetworkModules=false`, `experimentalFlags.network=false`, and `experimentalFlags.rawNodeNetworkModules=false`.

Current controlled fetch support includes `GET`/`POST`, headers, string or binary bodies, `timeoutMs`, `AbortController.signal`, `maxResponseBytes`, `maxRedirects`, text/arrayBuffer/json response helpers, and Web Stream reads through `response.body.stream()`. The live provider is backed by Android/OkHttp behind `BuildConfig.NODEJS_NETWORK_EXPERIMENTAL_ENABLED`; normal release builds keep that flag false. The axios-shaped adapter supports `axios(config)`, `axios.request`, `axios.get`, `axios.post`, `baseURL`, params, headers, `timeout`, and `responseType: "json" | "text" | "arraybuffer"` while rejecting custom adapters, agents, proxy configuration, custom transport, and raw socket assumptions. The S8-21 HTTP client facade supports `request()` / `get()`, a `ClientRequest` subset, an `IncomingMessage` subset, request and response headers, response status metadata, buffered request body chunks, response body `data` / `end` / `pipe()`, timeout, abort, and explicit redirect follow only when `redirect` or `maxRedirects` is configured. S8-23 adds focused stream semantics coverage for request-level `AbortSignal`, pre-aborted signal no-dispatch, `IncomingMessage` `close`, `pipe()` backpressure smoke, `maxResponseBytes`, service-destroy cancellation, and pending-call cleanup. S8-24 keeps `http.Agent` and `https.Agent` as limited shape-only facades with safe option fields while rejecting raw socket connector options. The S8-22 HTTPS layer keeps certificate validation provider-owned and rejects TLS customization such as `rejectUnauthorized=false`, custom secure contexts, client certificate fields, custom `createConnection`, and unsafe agent TLS options with `ERR_AUTOJS6_NETWORK_POLICY_DENIED`.

P13-29 records HTTP/HTTPS client parity as partial. The controlled
`http`/`https` facade now has a Phase 13 gate, TypeScript declarations, desktop
builtin matrix evidence, TLS/provider-error propagation coverage, and explicit
proxy/tunnel/`CONNECT`/server-listen denials. This does not promote raw sockets,
DNS/TLS session APIs, per-request proxy support, packaged INTERNET permission
evidence, or live provider HTTP smoke.

P13-30 records net/tls/dns staged compatibility as partial. By default, `require("net")`,
`require("node:net")`, `require("tls")`, `require("node:tls")`,
`require("dns")`, `require("node:dns")`, `require("dns/promises")`, and
`require("node:dns/promises")` resolve to reserved limited builtins that cannot
be shadowed. AutoJs6 Node exposes deterministic socket/server shape metadata,
`SocketAddress`, `BlockList`, TLS context/socket metadata, safe TLS constants,
and DNS lookup for `localhost`, loopback aliases, and IP literals only. Real
client sockets, server listeners, network DNS queries, Android resolver state,
TLS sessions, fd/native handles, packaged INTERNET proof, and live Android
network evidence remain unpromoted in Safe profile. The
`autojs.nodejs.rawNetworkModules.experimental=true` debug/build-time opt-in
routes CommonJS `dns`, `dns/promises`, `http`, `https`, `net`, and `tls` to
Node native builtins for Desktop parity experiments.

P13-31 records dgram UDP capability as stable-denied. `require("dgram")`,
`require("node:dgram")`, `require.resolve("dgram")`, transitive package
requires, and local `node_modules/dgram` shadow attempts all remain blocked by
the builtin-disabled policy. UDP client sockets, UDP server bind/listen,
multicast, broadcast, Android multicast state, fd/native handles, packaged
INTERNET proof, multicast permission proof, and live Android UDP evidence are
not part of the current v1.1 Desktop profile.

P13-32 records filesystem profile expansion as partial for the Desktop track.
It does not change the default scoped `fs` authority: scoped absolute paths are
accepted only while they remain under `workingDirectory`, and broader roots
require a future explicit provider, user/manifest authorization model,
per-root symlink/realpath/sensitive-path audit, and packaged evidence before
scripts can read or write outside their working directory.

P13-33 records desktop fs advanced APIs as partial for the Desktop track.
Existing S8 and P13 gates cover scoped `fs.watch`, `fs.promises.watch`,
`fs.watchFile`, `fs.opendir`, `fs.promises.opendir`, `FileHandle`, callback and
sync fd APIs, scoped streams, and `realpath`; packaged realpath and opendir
markers also exist. Full Desktop promotion still needs packaged watcher/fd/
FileHandle/stream lifecycle evidence, pending watcher diagnostics, expanded
root reruns, Android unsupported behavior notes, and final full `fs` semantics.

The controlled request policy is bounded even when fake or experimental live providers are used:

- max concurrent user fetches: `16`
- default timeout: `30000` ms, hard cap `120000` ms
- default response budget: `33554432` bytes, hard cap `67108864` bytes
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
- `require("node:worker_threads")` exposes a shape-only npm compatibility
  facade for feature detection and packages such as `jsdom`; `Worker`
  construction returns an inert disabled worker object, while `postMessage()`
  and heap snapshot operations reject with `ERR_AUTOJS6_WORKER_DISABLED`
- `require.resolve("worker_threads")` rejects before a local `node_modules/worker_threads` package can shadow the builtin
- no worker can access AutoJs bridge modules, Android Context, accessibility, media projection, image handles, shell, engines, raw network, child process, native addon, `vm`, inspector, or unrestricted filesystem APIs

Current `require("autojs6:profile")` reports `workerThreads=false`, `highRiskCapabilities.workerThreads=false`, and `experimentalFlags.workerThreads=false`.

Current `verifyNodeWorkerGate` normal builds also run
`NodeWorkerThreadsBootstrapInstrumentationTest`, which verifies request-local
default denial, shadowing protection, and request-local experimental preflight.
On the current Embedded Node build, that preflight reports
`ERR_AUTOJS6_WORKER_DISABLED`, `embedded_script.worker_active_count=0`, and no
same-thread fallback. If the experimental request flag is on but the native
`worker_threads` surface is unavailable, `require("worker_threads")` and
`require("node:worker_threads")` return the same shape-only facade with
`nativeAvailable=false` so npm packages can finish top-level feature detection;
actual worker execution and `postMessage()` still reject with
`ERR_AUTOJS6_WORKER_DISABLED`.

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

P13-34 records `worker_threads` opt-in promotion as partial. The runtime now
exposes `autojs6:profile.workerThreadsProfile` as diagnostics for default
denial, request-local experimental opt-in, native availability preflight,
worker policy, bridge denial, nested-worker denial, WorkerPool, transfer-list,
and packaged behavior state. It does not promote default, project, or packaged
worker authority; native runnable evidence, archived experimental worker gate
results, crash evidence, packaged `.mjs` worker smoke, and Android resource
ceilings remain open.

P13-35 records process-worker replacement as partial. The runtime exposes
`autojs6:profile.processWorkerReplacementProfile` to document that the current
replacement path is request-local `worker_threads.WorkerPool`, not a new
`process_worker` or `compute_worker` facade. `child_process` stays denied in
the Safe/default profile, `shell` remains a separate scoped command bridge,
`worker_computation` remains reserved, `second_execution_slot` is not closed by
this decision, and raw process handles, spawn/fork, stdio streams, signals,
bridge modules, nested workers, and packaged process-worker behavior remain
unpromoted. For P13-35 v1.1, there is no user-facing process-worker API or UX;
P13-40 remains the separate track for any future scoped `child_process` runner.

P13-40 records child_process scoped runner as partial. The runtime exposes
`autojs6:profile.childProcessProfile` to document that `child_process` and
`node:child_process` remain denied by default while the existing `shell` bridge
stays the Safe-profile command-execution migration path. A debug/build-time
Desktop compatibility experiment can enable the native Node builtin with
`autojs.nodejs.child_process.experimental=true`; this routes bare and
`node:`-prefixed CommonJS and ESM imports to Node's builtin before local
`node_modules` shadowing, and reports the flag through the profile registry.
The flag is release-forbidden and does not promote a stable scoped runner. Any
future release/Desktop scoped runner must use app-private allowlisted binaries,
reject arbitrary paths/downloaded binaries/shell operators/detached handles,
bound stdin/stdout/stderr, prove timeout/cancel/kill/cleanup behavior, and add
packaged plus hostile-corpus evidence before promotion.

P13-41 records ESM loader parity as partial. The runtime exposes
`autojs6:profile.esmLoaderProfile` to document that v1.1 supports the
AutoJs6-managed partial ESM loader for `.mjs`, `type=module`, TLA, local
static/dynamic import, controlled JavaScript/JSON `data:` URL imports, package `exports` / `imports`, JSON import attributes,
scoped `import.meta`, ESM/CJS interop, sync `require(esm)`, and focused
packaged ESM smokes. This is not full desktop loader parity: raw loader hooks,
raw `node:module` loader authority, network imports, `file:` URL imports,
unrestricted URL imports, arbitrary absolute paths, working-directory escapes, custom condition
sets, import maps, broad packaged encrypted graphs, and pending-evaluation
cleanup remain unpromoted.

P13-42 records Android package manager parity as partial. The runtime exposes
`autojs6:profile.packageManagerProfile` to document that package installation
is currently host-managed plus Android local-store substrate only. Already
unpacked package directories can flow through the internal app-private
`NodeAndroidPackageInstallManager` with transactions, locking, cancellation,
SHA-512 integrity, and verify/prune support. Android npm CLI execution,
registry download, lifecycle scripts, bin shims, symlink materialization,
native builds/native payloads, postinstall downloads, raw `node_modules`
mutation, unrestricted `package_manager` operations, and raw
registry/executable authority remain unpromoted. `package_manager` now exposes
status, policy, source policy, dry-run `planInstall`, `planUpdate`,
`planRemove`, policy-only `planRegistryInstall`/`planTarballInstall`, guarded
`install`/`update`/`remove`, `list`, `verify`, and `prune`. Remote source plan
calls return frozen `blocked_by_policy` diagnostics without bridge dispatch,
network/cache access, extraction, store mutation, or install authority;
registry plans can resolve caller-provided npm `metadata`/`packument` into
frozen dist-tag/semver/integrity diagnostics, and tarball plans can parse URL,
inferred package identity, and sha512 integrity shape locally. `npm` is
script-visible as a guarded package-manager alias facade; it does not run the
npm CLI or fetch from registries. Android registry downloader, cache
management, tarball extraction, and integrity-lock install authority stay
stable-denied as runtime user APIs for P13-42 v1.1; host-managed validation
remains the equivalent install path. Mutating calls are capability-gated by
`package_manager.mutate`, require
`allowMutation: true`, and use dry-run SHA-512 integrity.

P13-43 records Node stdlib conformance as partial. The runtime exposes
`autojs6:profile.stdlibProfile` to document the current Safe stdlib subset for
`crypto`, `stream`, `stream/promises`, `stream/consumers`, `stream/web`,
`zlib`, `buffer`, `url`, `os`, `readline`, `readline/promises`, `tty`,
`perf_hooks`, and `async_hooks`. `process.getBuiltinModule` is controlled
partial and returns the same limited wrappers as `require()`. `zlib/promises`,
raw desktop parity, raw native handles, unrestricted network behavior, and
broad terminal control remain unpromoted while broader Android difference
coverage and additional stdlib corpus expansion stay future work. The current
stdlib npm corpus marker is `real_npm_phase13_53_fixture_corpus`.

Large embedded result envelopes are bounded before Android Binder transport.
Dedicated result fields carry `resultText`, `stdout`, `stderr`, `errorMessage`,
and `errorStack`; duplicate large native payload fields such as
`embedded_script.result_json`, `embedded_script.error_stack`, `embedded_script.stdout`,
`embedded_script.stderr`, and fd capture text are omitted from
`result_native_payload`. Remaining native values are capped at 512 entries and
65536 characters with `embedded_script.result_transport.*` truncation
diagnostics.

P13-44 records process/env/argv parity as partial. The runtime exposes
`autojs6:profile.processParityProfile` to document the current Safe process
subset: controlled string-coercing mutable `process.env`, filtered sensitive env
variables, scoped cwd/chdir, `process.argv`, `exitCode`, versions, timing,
cpu/memory and `resourceUsage()` snapshots, `process.permission`,
`process.report.getReport()`, bounded stdio, and `process.exit(code)` mapped to
the embedded-script lifecycle.
Native/raw process termination, Android signal delivery, raw inherited env, raw
process handles, native bindings, `process.dlopen`, `process.binding`, and
`report.writeReport()` remain unpromoted.

P13-45 records packaged APK capability metadata as partial. The runtime exposes
`autojs6:profile.packagedCapabilityProfile` and
`bridgePermissions.packagedMetadata` to document packaged profile, builtin,
native asset, filesystem-root, execution-mode, network, and Android permission
metadata. The metadata is diagnostic-only: `node.permissions` remains the
bridge authority source, Android manifest synthesis currently maps bridge
capabilities to Android permissions, and profile/builtin/native/fs-root
metadata does not widen runtime authority. Invalid packaged metadata fields are
mirrored into `packagedMetadata.warnings` for JS diagnostics.

P13-46 records plugin system parity as partial. The runtime exposes
`autojs6:profile.pluginSystemProfile` to document the current JS-only local
plugin manager, tracked manager operations, host capability ceiling, packaged
local-file status, debug-only reload, and denied/deferred Android provider
plugin, native plugin, remote marketplace, signed archive, and installer
paths. Plugin manifests may request `plugin.<id>` and bridge capabilities, but
the host project/package permission manifest remains the authority ceiling and
missing capabilities reject before plugin code runs.

P13-47 records dependency compatibility corpus as partial. The fixed real npm
corpus now contains 53 offline fixtures, and its manifest records
`phase13Compatibility` package reason categories for `missing_builtin`,
`native_addon`, `postinstall`, `network`, and `fs` plus a stdlib
`targetedFixtureDeltas` +3 pass delta. The desktop differential report can
show pass-rate rows by category when AutoJs6 results are supplied, but unsafe
registry packages, native payloads, lifecycle scripts, and postinstall
downloads are still represented as metadata blockers rather than runnable
corpus content.

P13-48 records package lock and integrity policy as partial. The runtime
exposes `autojs6:profile.packageIntegrityProfile`, backed by
`NodePackageIntegrityPolicy`, to document the shared lockfile/hash/signature
policy for Android package manager, plugin packages, and native addon
artifacts. Android local store rollback, stale transaction prune, corruption
detection, and reinstall/update recovery are covered by focused
instrumentation. The profile exposes recovery action hints for integrity
verification, stale transaction pruning, reinstall from a verified source,
transaction snapshot restore, and disabling unsigned plugin/native subjects.
Unsigned plugin install, unsigned native addon load, raw registry install,
lifecycle scripts, postinstall downloads, and unverified cache reuse remain
fail-closed. Signed native addon artifact verification is delegated to the
P13-39 packaged native path; P13-48 keeps only the shared integrity policy and
fail-closed diagnostics until that path exists.

P13-49 records profile selection UX and diagnostics as partial. The runtime
exposes `autojs6:profile.profileSelection`, backed by
`NodeProfileSelectionDiagnostics`, to report requested Safe/Pro/Desktop/Debug
profiles, declared Node permissions, Android permission synthesis, high-risk
capability rows, blocked profile promotions, and failure hints. Missing bridge
capability errors now include `requiredProfiles`, `requiredNodePermissions`,
`requiredAndroidPermissions`, and `profileSelectionHint` with
`declarationExamples` snippets for `node.permissions`,
`project.json#node.permissions`, and
`package.json#autojs6.node.permissions`. The effective
profile remains `safe_default`; requested Pro/Desktop/Debug metadata is
diagnostic-only and does not grant authority.

P13-50 records relaxed-capability security corpus closeout. The corpus at
`docs/nodejs/security/RELAXED_CAPABILITY_SECURITY_CORPUS.json` covers raw
network, filesystem relaxation, Java interop, `child_process`, native addon
loading, inspector, and WASI with positive tests, negative tests, abuse cases,
CI evidence, runtime guards, promotion preconditions, and rollback posture. The host gate
`verifyNodePhase13RelaxedCapabilitySecurityCorpus` and focused
`NodeSecurityRegressionInstrumentationTest` additions prove the current
runtime remains fail-closed while future Pro/Desktop/Debug promotion evidence
is collected by P13-51/P13-52 and later promotion gates.

P13-51 records per-profile rollback playbook as partial. The runtime exposes
`autojs6:profile.profileRollbackPolicy`, backed by
`NodeProfileRollbackPolicy`, to report default-off Pro/Desktop/Debug profile
switches, relaxed-capability rollback switches, crash/stress/security/package
rollback triggers, and packaged Safe-profile downgrade guidance. Gradle
properties default to false for `autojs.nodejs.profile.pro.enabled`,
`autojs.nodejs.profile.desktop.enabled`,
`autojs.nodejs.profile.debugUnsafeLab.enabled`, and
`autojs.nodejs.relaxedCapabilities.enabled`; `raw_network` maps to
`autojs.nodejs.rawNetworkModules.experimental` so P13-50 corpus ids and P13-51
rollback ids line up. This is rollback metadata only: effective authority
remains `safe_default` until promotion gates close.

P13-52 records privacy and disclosure policy as partial. The runtime exposes
`autojs6:profile.privacyDisclosurePolicy`, backed by
`NodePrivacyDisclosurePolicy`, to report prompt channels, foreground
disclosure requirements, Android permissions/settings, packaged denial
behavior, and promotion blockers for screenshot, recording, overlay,
accessibility, network, raw network, filesystem relaxation, Java interop,
root/Shizuku shell, `child_process`, native addon, inspector, and WASI. The
`raw_network` disclosure surface aligns with P13-50/P13-51 raw Node network
builtin ids. The policy keeps
`implicitHighPrivilegeDenied` and `packagedImplicitHighPrivilegeDenied` true
and does not grant authority; actual prompt UI, notification ownership, and
packaged disclosure review remain future work.

P13-36 records `vm` module promotion as partial. The runtime exposes
`autojs6:profile.vmProfile` to document that `vm` and `node:vm` now resolve to
the native Node builtin for desktop npm compatibility. `vm.Script`, contexts,
`compileFunction`, VM module APIs, and native timeout options are available
through Node's own implementation. AutoJs6 does not inject bridge modules or
raw Android host objects into VM contexts, but the Node `vm` module is not a
security boundary. Follow-up evidence remains for memory/compiled-script
budgets and packaged APK smoke coverage.

P13-37 records inspector/devtools debug profile as partial. The runtime exposes
`autojs6:profile.inspectorProfile` to document that `inspector` and
`node:inspector` remain denied by default and in release builds while any future
Debug profile must prove explicit user action, loopback-only port binding, ADB
forward setup/cleanup, DevTools attach lifecycle, CPU/heap artifact redaction,
and packaged behavior. No callable inspector session, debugger endpoint,
profiler API, raw socket, bridge module, or script-visible inspector API is
promoted.

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
  `network`, `worker`, `shell`, `accessibility`, `screen_capture`, `notifications`, `notifications.settings`, `device.power`,
  `media`, `media.audio`, `media.metadata`, `media.recording`, `sensors`, and `ocr`
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

The `:nodejs_embedded` service process allows one active embedded Node execution at a time. A normal concurrent start in the same process waits in the bounded queue. An explicit fail-fast start is rejected with:

- `errorCode=ERR_AUTOJS6_NODE_ENGINE_BUSY`
- `nativeValues.embedded_script.engine_busy=true`
- `nativeValues.embedded_script.concurrent_policy=single-active-execution-per-process`

After the active execution completes and the service settles, queued or later executions can run normally. See `docs/nodejs/design/MULTI_INSTANCE.md`.

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
- limited global `crypto`, `crypto.webcrypto`, and `crypto.subtle` for
  `getRandomValues`, `randomUUID`, SHA-1/SHA-256/SHA-384/SHA-512 digest, and
  HMAC SHA-1/SHA-256/SHA-384/SHA-512 key import/export/sign/verify flows

Disabled or absent:

- asymmetric key creation/import/export APIs such as `createPrivateKey`, `createPublicKey`, and `generateKeyPair`
- signing and verification streams such as `createSign` and `createVerify`
- certificate and X.509 APIs
- native key-store or Android keystore access
- WebCrypto asymmetric generation/import, key derivation, unsupported digest or
  HMAC algorithms, invalid random-value typed arrays, oversized random-value
  buffers, empty HMAC usages, and non-extractable key export

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
- Node project metadata and module source entries within the `8192` module, `16777216` byte per-module, and `67108864` byte total budgets
- packaged smoke coverage for the current user-facing `toast`, `app`, `clipboard`, and `device` bridge modules, plus v1.1 fake-provider packaged coverage for controlled `fetch`, `ocr`, `storage`/`storages`, `notifications`, `ui`, `work_manager`, and project-local JS-only plugins using isolated capabilities and resources

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
- Node doctor text/JSON reports expose the bridge capability manifest schema, `network` capability status, required Android permission mapping, and raw network opt-in state
- Node projects may declare bridge limits in `project.json` under `node.bridgeLimits`, or in `package.json` under `autojs6.node.bridgeLimits`
- `require("autojs6:profile").bridgeLimits` exposes the effective read-only limit report, including warnings for ignored overrides
- malformed live bridge requests reject with `ERR_AUTOJS6_BRIDGE_INVALID_REQUEST` when a response id can be recovered
- provider callbacks are accepted at most once per bridge request; duplicate callbacks are ignored
- synchronous provider dispatch exceptions are converted to bridge failure responses with stable module and method metadata
- every Android provider request passes through capability broker v2
  declaration/preflight/audit before existing capability checks and dispatch
- broker v2 diagnostics expose schema, operation count, request-size limits,
  audit-row quota, total/rejected/failed/dropped counts, and last-row JSON
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
- `app`, `app.launch`, `app.settings`, `app.activity`, `app.query`
- `barcode`
- `dialogs`
- `engines`, `engines.exec`
- `accessibility`
- `input_observer`, `input_observer.keys`, `input_observer.touch`, `input_observer.intercept`
- `screen_capture` (`media_projection` is accepted as an alias)
- `image`
- `ocr`
- `storage`
- `database` (`sqlite` is accepted as an alias)
- `notifications`, `notifications.settings`
- `media`, `media.audio`, `media.metadata`, `media.recording`
- `sensors`
- `ui`
- `clipboard`
- `device`, `device.power`
- `shell`, `shell.root`, `shell.shizuku`
- `network`
- `work_manager`
- `java_interop`

No manifest keeps the legacy compatibility mode and does not enforce bridge capability declarations. An explicit empty array such as `"node": { "permissions": [] }` enforces a deny-by-default bridge surface.

Default bridge limits:

- `maxPendingBridgeCalls`: `32`, hard max `128`
- `maxImageHandles`: `32`, hard max `128`
- `maxShellCommands`: `2`, hard max `4`
- `maxNetworkRequests`: `16`, hard max `32`
- `maxWebSocketConnections`: `2`, hard max `4`
- `accessibilityQueriesPerSecond`: `20`, hard max `60`

Overrides outside the documented safe range are ignored and reported in `profile.bridgeLimits.warnings`.

Default capability broker v2 limits:

- retained per-execution audit rows: `64`
- max request JSON bytes: `65536`
- max argument JSON bytes per operation: `32768`

Broker v2 is currently provider-side policy/audit only. It does not expose a
public JavaScript API and does not enable the design-gated `node_sandboxed`
runtime.

- `toast`
  - `showToast(message, options?): Promise<void>`
  - `toast(message, options?): Promise<void>`
  - options: `{ duration?: "short" | "long", log?: boolean, timeoutMs?: number }`
- `app`
  - `packageName: string`
  - `versionName: string`
  - `versionCode: number`
  - `intent(intentOptions): IntentDescriptor`
  - `openUrl(url, options?): Promise<void>`
  - `sendEmail(request, options?): Promise<void>`
  - `uninstall(packageName, options?): Promise<void>`
  - `getPackageName(appName, options?): Promise<string | null>`
  - `getAppName(packageName, options?): Promise<string | null>`
  - `isInstalled(packageName, options?): Promise<boolean>`
  - `viewFile(path, options?): Promise<boolean>`
  - `editFile(path, options?): Promise<boolean>`
  - `launch(packageName, options?): Promise<boolean>`
  - `launchPackage(packageName, options?): Promise<boolean>`
  - `launchApp(name, options?): Promise<boolean>`
  - `openAppSetting(packageName?, options?): Promise<void>`
  - `startActivity(intentOptions, options?): Promise<void>`
  - `sendBroadcast(intentOptionsOrName, options?): Promise<never>`
  - P13-23 records app/intent/broadcast Pro parity as partial: `app.intent()`
    and `app.startActivity()` use JSON-safe descriptors with
    `category`/`categories`, numeric flag arrays, primitive extras, and scoped
    file view/edit Activity helpers. `app.parseUri()` returns JSON-safe URI
    descriptors. `app.getUriForFile()`, `app.kill()`, dual-user helpers, and
    broadcast sending stay stable-denied for P13-23 v1.1; future raw
    URI/FileProvider, root/Shizuku/dual-user, and broadcast authority belongs
    under the P13-22/P13-24 gates. Redacted installed-app list APIs, packaged
    app smoke, negative corpus, alias matrix, and migration examples remain
    open.
- `barcode` / `qrcode`
  - `detect(imageOrPath, options?): Promise<BarcodeSnapshot | null>`
  - `detectAll(imageOrPath, options?): Promise<readonly BarcodeSnapshot[]>`
  - `recognizeText(imageOrPath, options?): Promise<string | null>`
  - `recognizeTexts(imageOrPath, options?): Promise<readonly string[]>`
  - requests require `barcode` and `image` capabilities because the provider consumes image handles or scoped image paths
  - JavaScript receives frozen JSON barcode snapshots only; no raw ML Kit barcode, Bitmap, ImageWrapper, or Android object crosses into Node
  - current Android provider uses ML Kit barcode scanning when available and reports active operation cleanup through bridge diagnostics
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
  - Direct `waitFor`, `pickup`, selector-style `detect`, `existsAll`, and `existsOne` stay stable-denied for P13-19 v1.1 because they can imply live Rhino node handles or callback return values
  - P13-19 records the current accessibility selector promotion state as partial: direct `accessibility` and explicit `autojs6:compat` selector handles are covered by host/device/type evidence, while `swipe`, `gesture`, `powerDialog`, broader Rhino selector aliases, packaged service/settings lifecycle, expanded real-device evidence, and root/Shizuku gesture tiers remain blocked
- `input_observer`
  - `getAvailableSources(options?): Promise<readonly string[]>`
  - `observeKeys(descriptor, options?): Promise<InputObserverSubscription>`
  - `InputObserverSubscription.drainEvents(options?): Promise<readonly InputObserverEvent[]>`
  - `InputObserverSubscription.close(options?): Promise<boolean>`
  - requests require `input_observer`; key observation also requires `input_observer.keys`
  - current live source is bounded key observation through the accessibility-backed provider; touch observation and interception remain design-gated behind `input_observer.touch` and `input_observer.intercept`
  - event delivery uses bounded `drainEvents` JSON snapshots and provider-owned subscription handles that are closed on execution destroy
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
  - Paddle/Rapid/plugin OCR provider selection and raw Bitmap/OpenCV/MediaProjection object parity stay stable-denied for P13-20 v1.1
  - P13-20 records the current screenshot/image/OCR promotion state as partial: scoped `image.readImage()`/`recycle()` and ML Kit OCR over provider-owned image handles are live, while MediaProjection bridge prompting, advanced image processing, packaged capture/OCR behavior, and packaged disclosure evidence remain blocked
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
- `database` / `sqlite`
  - `open(name | { name }, options?): Promise<DatabaseHandle>`
  - `DatabaseHandle.exec(sql, params?, options?): Promise<void>`
  - `DatabaseHandle.run(sql, params?, options?): Promise<{ rowsAffected, insertId }>`
  - `DatabaseHandle.get(sql, params?, options?): Promise<Row | null>`
  - `DatabaseHandle.all(sql, params?, options?): Promise<readonly Row[]>`
  - `DatabaseHandle.transaction(operations, options?): Promise<readonly TransactionResult[]>`
  - `DatabaseHandle.close(options?): Promise<boolean>`
  - requests require the `database` capability; `sqlite` normalizes to the same capability
  - this is an AutoJs6 controlled bridge module, not Node's `node:sqlite`; `require("node:sqlite")` remains denied
  - database files live under app-private storage and are scoped by package identity, working directory, and database name
  - database names are single scoped names, not paths; absolute paths, separators, `..`, and NUL bytes are rejected before dispatch
  - SQL is bounded and rejects `load_extension`, `ATTACH`, and `DETACH`; native extension loading and cross-database path attachment remain unavailable
  - parameters must be JSON primitive arrays and are bound through prepared statements; query results are bounded JSON row snapshots
  - JavaScript receives frozen opaque handles only; no raw `SQLiteDatabase`, `Cursor`, file path authority, or Android object crosses into Node
- `notifications`
  - `notify(options, callOptions?): Promise<number>`
  - `cancel(id, options?): Promise<void>`
  - `cancelAll(options?): Promise<void>`
  - `getPermissionStatus(options?): Promise<NotificationPermissionStatus>`
  - `openSettings(options?): Promise<NotificationSettingsResult>`
  - `openNotificationSettings(options?): Promise<NotificationSettingsResult>`
  - options: `{ id?: number, title?: string, text?: string, channel?: string, channelName?: string, ongoing?: boolean, priority?: "min" | "low" | "default" | "high" | "max" | number }`
  - requests require the `notifications` capability; settings launch helpers additionally require exact `notifications.settings`
  - packaged capability manifests map `notifications` to `android.permission.POST_NOTIFICATIONS`
  - Android provider checks notification runtime permission/app notification state before posting
  - Android provider reports permission/settings state through schema `autojs6-node-notification-permission-status-v1` and supports `{ dryRun: true }` for settings-launch tests
  - Android provider creates stable channel ids and records channel/ongoing metadata for owned notification resources
  - the provider tracks notifications owned by the current Node execution and cancels remaining owned notifications on provider destroy
  - JavaScript receives only numeric ids; no raw `Notification`, `NotificationChannel`, `PendingIntent`, `RemoteViews`, or Android object crosses into Node
  - P13-25 records notifications/settings/power manager promotion as partial: actions, listeners, richer layouts, Do-Not-Disturb, exact-alarm, packaged disclosure, and migration examples remain open
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
  - `UiHandle.batchUpdate(patches, options?): Promise<void>`
  - `UiHandle.close(options?): Promise<void>`
  - `state(initialJson)` creates an observable JSON state object; `bind(state, key)` provides one-way binding into descriptor properties such as `text`, `checked`, `progress`, `enabled`, and `selectedIndex`
  - layout descriptors and update patches must be JSON-only plain objects; functions, symbols, `bigint`, `undefined`, cycles, non-plain object instances, excessive depth, excessive object keys, excessive array length, and oversized descriptors are rejected before dispatch
  - binding values are resolved to JSON before `showLayout` dispatch; UI process code never receives JavaScript functions, closures, or executable binding expressions
  - event callbacks receive frozen JSON snapshots with `handleId`; events are delivered by bounded `drainEvents` bridge requests instead of direct Android-to-JavaScript callbacks
  - requests require the `ui` capability
  - current JavaScript API, fake-provider tests, type declarations, and bridge protocol are reserved
  - Android provider starts an internal Activity and supports `Column`, `Row`, `Text`, `Button`, `Input`, `Image`, `ScrollView`, `List`, `Switch`, `Checkbox`, `Progress`, and `Tabs` descriptors, `update`, `batchUpdate`, Button/Input/List/Switch/Checkbox/Tabs/lifecycle events, state snapshots, `close`, provider-destroy cleanup, and a bounded active UI session count
  - P13-17 records this as a partial live-provider promotion gate; packaged support is provider-dependent until Back/Recents, orientation, relaunch/crash, and disclosure evidence lands
  - `WebView` descriptors reject with a stable invalid-request error until a separate WebView security gate is designed
  - JavaScript receives only an opaque handle; no raw `Context`, `Activity`, `View`, `Window`, `Fragment`, `Handler`, Rhino `NativeView`, overlay window, Binder, or Java object crosses into Node
- `ui.overlay`
  - `show(descriptor, options?): Promise<OverlayHandle>`
  - `OverlayHandle.update(patch, options?): Promise<OverlaySnapshot>`
  - `OverlayHandle.drainEvents(options?): Promise<readonly OverlayEvent[]>`
  - `OverlayHandle.close(options?): Promise<boolean>`
  - `closeAll(options?): Promise<OverlayCloseSummary>`
  - `hasPermission(options?): Promise<OverlayPermissionSnapshot>`
  - `openPermissionSettings(options?): Promise<OverlayPermissionSettingsResult>`
  - descriptors are JSON-only and require user-visible disclosure text; raw `WindowManager.LayoutParams`, hidden overlays, and fully transparent overlays are rejected before dispatch
  - requests require explicit `ui.overlay` or `ui.overlay.permission`; base `ui` never implies overlay authority
  - P13-18 records this as a partial overlay-provider promotion gate over the P12-14 Android permission/settings/closeAll live gate
  - live `show`, `update`, `drainEvents`, and per-handle `close` remain fail-closed until a controlled Android overlay provider, foreground disclosure ownership, packaged install-run evidence, and leak/stress coverage land
  - `require("floaty")`, `compat.floaty`, global `floaty`, exact Rhino `floaty.window()` / `floaty.rawWindow()` raw View parity, and raw `WindowManager`, `FloatyWindow`, `WindowBridge`, `View`, `NativeView`, Binder, or Java objects remain unavailable
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
  - `isIgnoringBatteryOptimizations(options?): Promise<boolean>`
  - `openBatteryOptimizationSettings(options?): Promise<DevicePowerSettingsResult>`
  - current Android provider uses `PowerManager` and `Vibrator` through the live bridge provider
  - battery optimization helpers require exact `device.power` and return schema `autojs6-node-battery-optimization-settings-v1`; settings launch supports `{ dryRun: true }`
  - `vibrate()` clamps duration to 10 seconds and packaged APKs that use it must declare `android.permission.VIBRATE`
  - privacy-sensitive identifiers are absent: IMEI, Android ID, serial, phone number, accounts, MAC/IP address, hardware fingerprint, brand, and manufacturer
  - no raw `Context`, `PowerManager`, `Vibrator`, display, or build objects are exposed to JavaScript
  - P13-25 records notifications/settings/power manager promotion as partial: wake locks, direct Doze mutation, request-ignore-battery-optimization flow, OEM power-manager matrices, packaged disclosure, and migration examples remain open
- `media`, `mediainfo`, `recorder`
  - `media.getAudioStreamVolume(stream?, options?): Promise<number>`
  - `media.getAudioStreamMaxVolume(stream?, options?): Promise<number>`
  - `media.getAudioStreamInfo(stream?, options?): Promise<MediaAudioStreamInfo>`
  - `mediainfo.read(path, options?): Promise<MediainfoSnapshot>`
  - `mediainfo.get(path, streamKind, parameter?, options?): Promise<string>`
  - `recorder.getStatus(options?): Promise<RecorderStatus>`
  - `recorder.start()` and `recorder.stop()` are present but fail closed until a privacy-reviewed recorder provider lands
  - `media.audio`, `media.metadata`, and `media.recording` are exact capabilities and are not implied by `media`
  - `mediainfo` accepts only relative paths inside the scoped Node working directory
  - no raw `MediaPlayer`, `AudioManager`, `MediaRecorder`, `MediaMetadataRetriever`, `MediaInfo`, `MediaStore`, `Uri`, file descriptor, or Android `Context` object is exposed to JavaScript
  - P13-26 records media/recorder/mediainfo provider promotion as partial: playback, real recorder sessions, foreground disclosure, MediaStore policy, packaged media asset lookup, corpus evidence, and migration examples remain open
- `jsox`, `jsox.mathx`, `autojs6:compat.jsox`
  - `jsox.mathx` exposes pure Rhino `Mathx` helpers for random values,
    statistics, distance, logarithms, powers, and min/max helpers
  - `jsox.arrayx` exposes pure Rhino `Arrayx` validation, set, sort, and
    shuffle helpers
  - `jsox.numberx` exposes pure Rhino `Numberx` validation, comparison,
    clamp, formatting, and parsing helpers
  - `jsox.extend(target, "Mathx" | "Arrayx" | "Numberx")` and
    `jsox.extendAll(target)` copy helpers to an explicit target object only
  - default global `Math` or prototype mutation is blocked with
    `ERR_AUTOJS6_RHINO_COMPAT_BLOCKED_BY_DESIGN`
  - `Mathx.dist()` accepts JSON-safe point arrays and `{ x, y }` objects only;
    raw Android/OpenCV point and rect objects are not exposed
  - Raw Android/OpenCV point and rect object parity is stable-denied for P13-27;
    future compatibility requires a separate opt-in profile gate
  - P13-27 records utility-heavy Rhino modules promotion as partial: `zip`,
    `canvas` JSON drawing provider/raw Canvas/Paint decision, OpenCC/pinyin
    packaged resources, Rhino `util` split batches, and `sysprops` remain open
- `shell`
  - `exec(command, options?): Promise<{ code: number, stdout: string, stderr: string }>`
  - `execRoot(command, options?): Promise<{ code: number, stdout: string, stderr: string }>`
  - `execShizuku(command, options?): Promise<never>`
  - `execFile(path, args?, options?): Promise<{ code: number, stdout: string, stderr: string }>`
  - `checkAccess(options?): { shell: boolean, root: boolean, shizuku: boolean, missingCapabilities: string[] }`
  - `setDefaultTimeout(ms): number`
  - options: `{ root?: boolean, timeoutMs?: number, cwd?: string, env?: Record<string, string> }`; `shizuku: true` is reserved and rejected in v1.1
  - runs through the AutoJs6 bridge provider, not Node `child_process`
  - migration mapping for Safe profile scripts: `child_process.exec()` -> `require("shell").exec()` with declared `shell` capability; `child_process.spawn()`, `fork()`, streaming stdio, and interactive shells are not supported by the Safe Node Profile
  - non-root mode uses a controlled non-interactive shell process on a provider thread; root mode is explicit and attempts `su -c`
  - `execRoot()` is a JavaScript facade over `exec(..., { root: true })` and requires the explicit `shell.root` capability when a manifest is enforced
  - `execShizuku()` and `exec(..., { shizuku: true })` reject before bridge dispatch with `ERR_AUTOJS6_SHELL_SHIZUKU_UNSUPPORTED`
  - `execFile()` is a JS-layer convenience wrapper over `exec()` for scoped relative file paths and string arguments; it shell-quotes the target and args and does not add a second native dispatch method
  - `cwd` is relative to the scoped working directory and rejects absolute paths, NUL bytes, `..` escapes, and realpath escapes
  - `env` accepts string keys and values only
  - command timeout returns a result with `code: 124`; invalid policy inputs reject with `ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED`; `setDefaultTimeout()` changes only the per-module JS fallback timeout
  - `checkAccess({ root: true })` reports missing `shell.root` when a manifest is enforced and the capability is absent; `checkAccess({ shizuku: true })` always reports `shizuku: false` in v1.1 because Shizuku execution remains unsupported
  - P13-24 records shell/root/Shizuku capability promotion as partial: `shell.shizuku` is a vocabulary-only token, root remains explicit, and Shizuku execution stays stable-denied for v1.1 until a dedicated provider owns authorization, lifecycle, cleanup, packaged disclosure, and rollback evidence
  - queued host fallback does not execute shell commands; packaged templates need live bridge metadata before `shell.exec()` can observe real command results
  - no interactive shell, streaming stdio, process object, signal control, `spawn`, `fork`, or `open` API is exposed
- `fetch`
  - `fetch(url, options?): Promise<Response>` through global `fetch` and `require("fetch")`
  - options: `{ method?: "GET" | "POST", headers?: object | array, body?: string | Buffer | ArrayBuffer | TypedArray | Blob, timeoutMs?: number, maxResponseBytes?: number, maxRedirects?: number, signal?: AbortSignal }`
  - module diagnostics: `require("fetch").policy` and `require("fetch").diagnostics()`
  - response helpers: `text()`, `arrayBuffer()`, and `json()`
  - normal builds reject through the Android provider with `ERR_AUTOJS6_NETWORK_DISABLED`; fake-provider tests cover success, timeout, abort, response budget, unsupported scheme, redirect limit, concurrency limit, and service-destroy paths
  - raw `net`, `tls`, `dns`, and `dgram` modules remain unavailable for network I/O; `http`, `https`, and `undici` expose only the controlled client facades described below
- `http` / `https`
  - `request(options|string|URL[, options][, callback])` and `get(...)` route through controlled fetch only when an enforced manifest declares `network`
  - exposes a `ClientRequest` subset: `write()`, `end()`, `setHeader()`, `getHeader()`, `getHeaders()`, `setTimeout()`, `abort()`, and `destroy()`
  - exposes an `IncomingMessage` subset: `statusCode`, `statusMessage`, `headers`, `rawHeaders`, `setEncoding()`, `data` / `end` / `close` events, `pause()`, `resume()`, and `pipe()`
  - request body chunks are buffered before controlled fetch dispatch; no socket stream, fd, or native handle is exposed
  - request-level `AbortSignal` maps to `ERR_AUTOJS6_NETWORK_ABORTED`, and pre-aborted signals reject without dispatching a bridge request
  - `maxResponseBytes` is forwarded to the controlled fetch response budget, and service destroy rejects pending facade requests with `ERR_AUTOJS6_BRIDGE_PROCESS_DEAD`
  - redirects are manual by default; explicit `redirect` or `maxRedirects` options are required to follow redirects
  - `http.Agent` and `https.Agent` expose only a safe compatibility shape for options such as `keepAlive`, `maxSockets`, `maxFreeSockets`, `defaultPort`, `protocol`, and `getName()`; `destroy()` marks the facade destroyed without managing sockets
  - request-level `createConnection` and `socketPath`, plus custom agent objects exposing `createConnection()` or `addRequest()`, reject with `ERR_AUTOJS6_NETWORK_POLICY_DENIED`
  - HTTPS rejects `rejectUnauthorized=false`, custom secure contexts, custom OpenSSL/TLS options, client certificate fields, request-level `createConnection`, and unsafe agent TLS options with `ERR_AUTOJS6_NETWORK_POLICY_DENIED`
  - without an enforced `network` manifest, `request()` and `get()` keep the existing synchronous disabled error
  - `createServer()`, `listen()`, `CONNECT`, upgrade, agent socket pooling, raw sockets, DNS lookup, TLS session control, and local `node_modules/http` or `node_modules/https` shadow bypass remain unavailable
- `undici`
  - `fetch(input, init?): Promise<Response>` and `request(input, init?): Promise<{ statusCode: number, status: number, statusText: string, headers: object, body: ReadableStream | null, trailers: object, opaque: null, context: null }>` through reserved `require("undici")`
  - reuses runtime Web Platform constructors for `Headers`, `Request`, `Response`, `FormData`, and `File`
  - `Dispatcher`, `Agent`, and `MockAgent` expose only safe package-compatibility shapes; `MockAgent` helpers such as `disableNetConnect()`, `get()`, `intercept()`, `reply()`, `times()`, `persist()`, and `delay()` do not intercept real network traffic
  - `DecoratorHandler` exposes a shape-only, subclassable handler delegate for npm packages such as `jsdom`; it forwards new handler callbacks and legacy `onHeaders` / `onData` / `onComplete` methods without opening raw Undici dispatch
  - `setGlobalDispatcher()` accepts only AutoJs6 limited dispatcher instances, and `getGlobalDispatcher()` returns that limited facade object
  - `connect()`, `upgrade()`, `stream()`, `pipeline()`, direct `dispatch()`, raw dispatcher injection, custom `connect` / `factory`, `socketPath`, proxy, and tunnel options reject with `ERR_AUTOJS6_NETWORK_POLICY_DENIED`
  - backed by controlled fetch only; local `node_modules/undici` cannot shadow it, and raw network modules remain denied
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
  - P13-21 records the current daily/weekly/user task database provider state as partial: `work_manager` and `compat.tasks` one-shot migration are live evidence, while direct `require("tasks")`, global `tasks`, daily/weekly add/query/update/remove, packaged task-database promotion, and raw `TimedTask` / `IntentTask` mutation remain blocked
  - P13-22 records the current IntentTask/broadcast task provider state as partial: Android/Rhino receiver substrate and `compat.tasks` IntentTask rejection stubs are evidence, while receiver registration, boot/package/startup/custom broadcast triggers, manifest mutation, packaged trigger/remove behavior, and raw `Intent` / `BroadcastReceiver` objects remain blocked
  - `compat.tasks.addIntentTask()`, `queryIntentTasks()`, `getIntentTask(id)`, and `removeIntentTask(id)` stay Promise-returning stable rejection stubs until a dedicated JSON-safe broadcast provider owns the future surface

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

The current Android provider registers live resources for controlled fetch calls, controlled WebSocket sessions, database handles, shell executions, WorkManager-backed `work_manager` task registrations, and experimental Java object handles. The registry also reserves resource types for future image handles, screen capturers, dialogs, UI sessions, and workers so those providers can share the same destroy path when they become live Android capabilities.

S7-47 adds a focused Phase 7 resource stress gate. `verifyNodePhase7ResourceStressGate` records RSS, native heap,
Java heap, thread count, fd count, test-only libuv handle counts, and bridge diagnostics while combining long-running
stress, bridge resource loops, live provider cleanup, database transaction/close stress, WorkManager scheduled execution
cleanup, JS-only plugin lifecycle cleanup, and worker resource stress when native workers are available. The gate must end
with zero active bridge resources, database handles, WorkManager registrations, shell commands, WebSocket connections,
leaks, and close errors.

P13-08 adds `verifyNodePhase13FullHeavyResourceStress`, a host status gate for
the required `quick-soak-8h.json` and `final-soak-24h.json` reports. It keeps
the final blocker open until both reports contain comparable RSS, Java heap,
native heap, thread, fd, and bridge diagnostics and no leak/orphan/runtime
failure flags. The verifier also has contract fixtures for accepted
duration/timestamp report shapes and rejected under-duration, missing-metric,
leak/orphan, and unrecoverable-runtime shapes; those fixtures validate the gate
only and do not count as soak evidence. A companion archive helper validates
real completed soak JSON before copying it into the canonical final-soak path.

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
- P13-21 keeps daily/weekly/user task database parity in a partial Pro-profile evidence state: `compat.tasks.addDisposableTask()` can migrate Node-owned one-shot descriptors through `work_manager`, but wall-clock daily/weekly rows, user task database schema migration, packaged metadata, and raw task object compatibility remain separate gates.
- P13-22 keeps IntentTask/broadcast parity in a partial Pro-profile evidence state: Rhino/Android receiver infrastructure is documented, but Node cannot create broadcast receiver rows, trigger boot/package/custom actions, mutate manifests, inspect raw trigger payloads, or turn `compat.tasks` IntentTask names into raw Rhino row wrappers.
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
- scoped fs absolute outside path: canonical `autojs6Code=ERR_AUTOJS6_FS_ABSOLUTE_PATH_DENIED`
- closed or non-owned scoped FileHandle descriptor: `code=EBADF`
- undeclared bridge capability: `code=ERR_AUTOJS6_BRIDGE_CAPABILITY_NOT_DECLARED`, `autojs6Code=ERR_AUTOJS6_BRIDGE_CAPABILITY_NOT_DECLARED`
  with P13-49 `requiredProfiles`, `requiredNodePermissions`,
  `requiredAndroidPermissions`, and `profileSelectionHint.declarationExamples`
  diagnostics.
- cancelled bridge call: `code=ERR_AUTOJS6_BRIDGE_CANCELLED`, `autojs6Code=ERR_AUTOJS6_BRIDGE_CANCELLED`
- malformed bridge request: `code=ERR_AUTOJS6_BRIDGE_INVALID_REQUEST`, `autojs6Code=ERR_AUTOJS6_BRIDGE_INVALID_REQUEST`
- bridge resource limit: `code=ERR_AUTOJS6_BRIDGE_RESOURCE_LIMIT`, `autojs6Code=ERR_AUTOJS6_BRIDGE_RESOURCE_LIMIT`
- bridge rate limit: `code=ERR_AUTOJS6_BRIDGE_RATE_LIMITED`, `autojs6Code=ERR_AUTOJS6_BRIDGE_RATE_LIMITED`
- embedded Node disabled: `errorCode=ERR_AUTOJS6_NODE_DISABLED`
- explicit fail-fast concurrent embedded execution: `errorCode=ERR_AUTOJS6_NODE_ENGINE_BUSY`
