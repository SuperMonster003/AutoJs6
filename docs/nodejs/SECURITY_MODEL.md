# AutoJs6 Embedded Node.js Security Model

The embedded Node runtime is an Android automation runtime, not a desktop shell. The security model is based on process isolation, scoped filesystem access, builtin restrictions, packaged APK verification, and explicit feature gates.

The release threat model for Node Profile v1 is tracked in [security/THREAT_MODEL_V1.md](security/THREAT_MODEL_V1.md). Keep that document updated when adding or promoting any resolver, filesystem, bridge, bundler, packaged APK, or experimental feature surface.

## Trust Boundary

User scripts are untrusted relative to the AutoJs6 app process and Android privileged services.

The embedded runtime may execute user-provided JavaScript, npm-style CommonJS packages, and packaged APK assets. It must not receive raw access to:

- Android `Context` with broad privileges
- `AccessibilityService` instances
- `MediaProjection` instances
- `Activity` or view objects
- Binder objects
- Java or Android class objects
- Java reflection or class-loader objects
- native pointers or raw image handles
- unrestricted process, network, or native addon APIs

Privileged actions must go through AutoJs6 capability providers.

## Process Isolation

Embedded scripts run in `:nodejs_embedded`. This protects the main app process from common runtime failure modes:

- V8 crash
- libuv lifecycle bug
- fatal Node native error
- script timeout
- native bridge assertion failure

The service currently uses a normal named process, not `android:isolatedProcess="true"`. Do not switch to `isolatedProcess` without redesigning all permission and Binder capability flows.
S8-43 statically declares reserved embedded script service slots in
`:nodejs_embedded_1` and `:nodejs_embedded_2`, but the process-pool policy gate
keeps the effective runtime `primary_only`. Reserved slots are non-exported and
cannot be selected by scripts, project metadata, or packaged metadata until a
future pool coordinator and lifecycle gate are promoted.

## Filesystem Policy

User filesystem access is scoped to the script working directory or project root.

Denied:

- absolute paths
- paths containing NUL
- `..` traversal outside the scope
- `/proc`
- `/sys`
- `/dev`
- symlink escape
- realpath escape
- package export/import target escape
- native addon `.node` loading

Required checks:

- normalize lexical paths
- canonicalize before opening
- re-check after symlink resolution
- keep packaged module source extraction inside the package scope

The policy applies equally to callback, sync, and promise filesystem APIs.

Source maps are diagnostics only and must not weaken scoped filesystem policy. Inline `sourceMappingURL=data:` maps are self-contained. External source maps must resolve inside the current working directory or come from already injected moduleSources; path escapes, NUL bytes, sensitive paths, and symlink/realpath escapes are denied and recorded as source-map diagnostics without replacing the original script error.

## Builtin Policy

Builtins fall into four categories:

- allowed: safe direct modules such as `path`, `util`, and `events`
- limited: compatibility modules with restricted behavior
- denied: modules that throw stable `ERR_AUTOJS6_*` errors
- design-gated: modules that may be opened only after a design review and feature flag

Always deny by default for dangerous APIs.

`NodeRuntimeCapabilities` is the Kotlin-side registry for this policy. `require("autojs6:profile")` exposes a frozen JavaScript snapshot for diagnostics; it is not an authority to grant capabilities at runtime.

High-risk modules and APIs:

- `child_process`
- `worker_threads`
- `process.binding`
- `process._linkedBinding`
- `process.dlopen`
- native addons
- unrestricted `net`, `tls`, `http`, `https`, `dns`, and `dgram`
- unrestricted `vm`
- inspector
- direct Java or Android interop

If a limited module exists for npm feature detection, operations that would cross the security boundary must fail with a stable policy error.

Inspector remains disabled in the default Safe Node Profile. `inspector` /
`node:inspector`, `process._debugProcess`, debugger feature flags, metadata
overrides, and local package shadowing must all fail closed. Script-visible
`process.env` filters `NODE_OPTIONS` and `NODE_INSPECT_RESUME_ON_START` so
user-provided startup/debugger env values cannot become an implied inspector
contract. The embedded request boundary also strips Node startup/debugger/cache
env keys before native startup, and native Node initialization disables
`NODE_OPTIONS` and global debug-variable parsing. Release, project, and
packaged metadata cannot enable inspector. Future inspector support must be
debug-build-only and prove loopback-only binding, explicit user action,
visible and revocable session state, deterministic shutdown, and packaged
release denial. ADB forwarding, DevTools attach, CPU profiles, heap snapshots,
allocation sampling, and profiler artifact export remain unavailable until
that inspector channel exists and stores raw artifacts under app-private
diagnostics before explicit export.

`module` and `node:module` are limited compatibility builtins, not loader authority. The safe subset exposes `createRequire`, `isBuiltin`, `builtinModules`, and a no-op `syncBuiltinESMExports` only. `createRequire` must stay scoped to a file inside the current working directory and must keep using the AutoJs6 resolver and builtin policy. Loader internals such as `Module`, `_load`, `_resolveFilename`, `_compile`, `wrap`, `globalPaths`, `register`, `registerHooks`, `enableCompileCache`, `flushCompileCache`, and compile-cache constants remain absent. The module compile cache is enabled only by the internal bootstrap against an app-private cache directory; `AUTOJS6_NODE_COMPILE_CACHE_*`, `NODE_COMPILE_CACHE`, and `NODE_DISABLE_COMPILE_CACHE` are filtered from script-visible `process.env`, so user code cannot redirect or disable the internal cache path.

Startup snapshots remain design-gated. A future snapshot may contain immutable
Safe Node helper/schema code only; it must never contain V8 `Context` objects,
Android `Context` or Binder objects, providers, permissions, user paths,
project roots, execution ids, request env, `moduleSources`, packaged graph
contents, open resources, or project module cache entries. Snapshot load
failure must fall back to normal bootstrap without skipping scoped fs, builtin,
bridge capability, env filtering, module policy, or resource cleanup checks.

Runtime prewarm is process-only in v1.1. It may keep the
`:nodejs_embedded` foreground service process alive briefly, but it must not
create or reuse a V8 isolate, Node environment, bridge provider, capability
grant, cwd, env, module graph, module cache, execution id, timer, watcher, fd,
or other script-owned resource. The next script consumes the prewarm once and
then runs normal bootstrap and cleanup. Unused prewarm expires on TTL or
low-memory signals.

Runtime package installation is not a Safe Node Profile user capability. Projects may vendor pure JavaScript CommonJS dependencies, and host tooling may sanitize them at build time. Phase 13 adds `NodeAndroidPackageInstallManager` as an internal app-private local store for already-unpacked packages; it records `local_unpacked_host_managed` as the only allowed source substrate and still rejects lifecycle scripts, package `bin`, native payloads, symlinks, path escapes, raw registry sources, remote tarballs, unverified cache reuse, and registry/npm execution. AutoJs6 must not run package lifecycle scripts, native addon builds, arbitrary `.bin` commands, or registry downloads inside the Android runtime. See `docs/nodejs/design/PACKAGE_MANAGER.md`.
P13-48 records package lock and integrity policy as partial. `NodePackageIntegrityPolicy` and `autojs6:profile.packageIntegrityProfile` define the shared lockfile, SHA-512 integrity, signature, rollback, cache cleanup, corruption recovery requirements, and machine-readable recovery action hints for Android package manager, plugin packages, and native addon artifacts. The policy does not promote unsigned plugin installs, unsigned native addon loads, lifecycle scripts, postinstall downloads, raw registry installs, or unverified cache reuse. Signed native addon artifact verification is delegated to the P13-39 packaged native path; P13-48 keeps shared integrity policy diagnostics fail-closed until that path exists.
P13-49 records profile selection UX and diagnostics as partial. `NodeProfileSelectionDiagnostics` and `autojs6:profile.profileSelection` expose requested Safe/Pro/Desktop/Debug metadata, declared high-risk capabilities, synthesized Android permissions, blocked profile promotions, and failure hints without changing authority. Missing bridge capability errors identify the required profile, Node capability declaration, Android permission set, and `declarationExamples` snippets for project/package manifests, while the effective profile remains `safe_default` and user-selectable profile promotion stays disabled. Effective Pro/Desktop/Debug profile switching stays stable-denied for P13-49 v1.1.
P13-51 records per-profile rollback playbook as partial. `NodeProfileRollbackPolicy` and `autojs6:profile.profileRollbackPolicy` expose default-off Pro/Desktop/Debug profile switches, default-off relaxed-capability aggregation, individual capability rollback actions, Safe-profile crash/stress fallback, and packaged Safe-profile downgrade guidance. The `raw_network` rollback id maps to `autojs.nodejs.rawNetworkModules.experimental` so the P13-50 corpus and P13-51 playbook use the same relaxed-capability name. The playbook is metadata-only and does not grant authority; runtime profile promotion rollback wiring and automatic crash circuit breaker implementation are deferred until effective profile switching is promoted, while settings UI and privacy prompts remain future work.
The host dependency bundler rejects unsafe dependency trees before packaging. `.node` files are reported as `NATIVE_ADDON_DISABLED`; `binding.gyp` and `build/{Release,Debug}` artifacts are reported as `NODE_GYP_ARTIFACT_UNSUPPORTED`. It also rejects every blocked lifecycle script variant (`preinstall`, `install`, `postinstall`, `prepare`), unchecked `.bin` entries, npm cache/log artifacts, symlink/path escapes, suspicious executable/native binaries, unsupported package-manager layouts, oversized files/packages/trees, and package metadata path references that are absolute, protocol-based, NUL-containing, or escape through `..`. A sanitized bundle is copied only after the scan reports zero issues, and the dependency report includes an SBOM-lite file, dependency compatibility analyzer status, deterministic bundle hash, and content-addressed cache key for packaged APK review. APK-builder preflight gates reject analyzer `unsafe` and `unsupported` statuses.

The `require("plugins")` surface is a JS-only local manager, not a package
download installer or a native/plugin-center bridge. It can register/load only
`./plugins/<id>/package.json` packages that declare `autojs6.plugin` metadata.
`plugins.install(id, options?)` validates an already-present sanitized local
package for the current execution only; it does not copy files, run lifecycle
scripts, fetch registries, or persist global state. `plugins.install`, `load`,
`resolve`, `enable`, `configure`, and `unload` require exact project opt-in
through `plugin.<id>` and the plugin's requested capabilities before any plugin
code or cached module is exposed. `.node` files below the plugin directory,
common native library/object files, escaping entry paths, and symlinks are
rejected. The manager supports in-execution enable/disable, configuration,
named JS module exports, dependency declarations, and cache-clearing unload,
but it does not grant Java interop, Android plugin APK access, native provider
loading, update authority, package download authority, or packaged APK manifest
injection.

`vm` is enabled as a native Node builtin for desktop npm compatibility, but a second JavaScript context is not a security boundary in this embedder. AutoJs6 does not inject raw Android or bridge host objects into VM contexts, and disabled builtins remain governed by the loader policy. P13-36 focused VM stress now covers repeated compiled scripts, native timeout interruption, bridge/process/global non-injection probes, and packaged fixture smoke coverage.

Inspector remains disabled because it is a live debugger control channel, not a passive diagnostic API. Any future support must be debug-build-only, loopback-only, explicitly user-started, and closed through the embedded service lifecycle. See `docs/nodejs/design/INSPECTOR.md`.

The Node developer REPL is debug-build-only and reuses the normal one-shot
embedded script service instead of opening a debugger socket or a second
JavaScript context. Every session runs inside a unique temporary app-cache
`workingDirectory`, deletes that directory on close, forces worker threads and
Java interop off, and injects an enforced bridge permission manifest. The
default REPL manifest declares no bridge capabilities; callers must explicitly
opt in to each bridge capability and the provider audit still applies.

Embedded Node execution is single-active per `:nodejs_embedded` process. Normal concurrent starts wait in a bounded queue instead of overlapping V8/libuv lifecycle ownership; explicit fail-fast requests still reject with `ERR_AUTOJS6_NODE_ENGINE_BUSY`. Queue full, timeout, and cancellation paths use stable `ERR_AUTOJS6_NODE_QUEUE_*` errors. See `docs/nodejs/design/MULTI_INSTANCE.md` and `docs/nodejs/design/EXECUTION_QUEUE.md`.
Process-pool diagnostics are reported under
`embedded_script.process_pool.*`, but S8-43 does not enable same-process
concurrency, cross-process slot routing, or reusable Node environments. Future
multi-process promotion must keep one active Node/V8/libuv environment per
process slot and prove crash isolation, queue-when-full behavior, and
cross-slot resource cleanup before enabling reserved slots.

Long-running Node service mode is limited to explicit foreground launch
surfaces: the S7-18 interactive runtime and the P13-07 packaged long-running
opt-in path. It must not be inferred from pending timers, open
network/WebSocket handles, `timeoutMs: 0`, or undeclared package metadata.
Normal scripts remain one-shot and timeout-controlled; direct script
`interactiveLongRunning`/legacy `long_running` requests reject before user code.
The request protocol recognizes `executionMode` through the Phase 7 state
machine: `one_shot` is executable for normal launches,
`interactive_long_running` is executable only through the explicit
`interactive_session` launch surface or a packaged project that declares
`node.executionMode=interactive_long_running`, with a persistent notification
and Stop action, `scheduled` is executable only through the WorkManager-owned
`scheduled_runner` launch surface, `worker_computation` remains reserved, and
`node_sandboxed` is a Phase 8 manifest/design-gated POC that requires a Binder
capability broker before execution. Direct script requests for non-`one_shot`
modes still reject before user code.
Interactive
long-running status records restart policy `never`, `automaticRestart=false`,
duration, active resources, and the Stop action; packaged long-running includes
checkpoint/read/clear smoke evidence but remains partial until Stop action,
task-removal cleanup, permission-denial, background-start, and stress evidence
land. Automatic restart, broader quotas, and script-level
cooperative stop beyond the interactive lifecycle module remain gated. S7-22
adds `require("autojs6:lifecycle")` for interactive and packaged long-running
sessions:
checkpoints are JSON-only, limited to 64 KiB, app-private, project-scoped by
package plus canonical working directory, atomically persisted, and restored
only by an explicit script call. Status snapshots expose checkpoint metadata but
not the full payload, and the restart policy remains `never`. S7-19 adds Android
foreground-service
diagnostics for background-start denial, manifest type validation, and
`Service.onTimeout(...)` cleanup; those diagnostics do not grant background
launch authority or restart permission. S7-23 stress coverage requires
interactive stop/checkpoint/recovery to remain repeatable and provider destroy
to close owned notification, sensor, UI, shell, and WebSocket counters without
active resources or close errors. See
`docs/nodejs/design/LONG_RUNNING_SERVICE.md`.
S7-20 adds runtime heartbeat and budget diagnostics for the explicit
interactive runtime. These diagnostics report event-loop responsiveness, last
bridge progress, active registry/resource counts, and budget breaches, but they
do not grant new bridge capabilities, raw Android objects, raw network,
child-process authority, automatic restart, or hidden background execution.

S8-41 declares `NativeNodeSandboxedScriptService` as a non-exported Android
`isolatedProcess` service under `:nodejs_sandboxed`, but no script route uses it
yet. The canonical `node_sandboxed` execution mode exposes no launch surface and
no bridge providers. Any future executable sandboxed runtime must route source,
module, scoped filesystem, network, and bridge operations through a Binder
broker with explicit capability checks, size limits, cancellation, audit
metadata, and execution-owned cleanup; it must not inherit Java interop,
shell/root, native addon, raw process, raw network, or unrestricted filesystem
authority.

## Feature Flag Policy

`NodeRuntimeCapabilities.featureFlags` is the source of truth for build-only, debug-only, project-allowed, packaged-allowed, and release-forbidden Node feature flags. Current experimental flags are build-time only and debug-only; `project.json` and `package.json` metadata must not enable them. Explicit `node.featureFlags`, `node.experimentalFlags`, `autojs6.node.featureFlags`, and `autojs6.node.experimentalFlags` entries are audited and rejected when they name unknown flags or flags whose project/package override policy is false.

Release builds reject release-forbidden experimental flags even when a Gradle property is supplied. Use debug experimental builds for validation, then return release builds to the Safe Node Profile defaults.

## ESM Policy

ESM is partial and enabled by default in Node Profile v1.1.

The legacy ESM build switch is `autojs.nodejs.esm.experimental=true`. Dynamic import has its own local-only switch, `autojs.nodejs.dynamicImport.local=true`; the legacy broad experiment `autojs.nodejs.dynamicImport.experimental=false` remains release-forbidden. With ESM and local dynamic import enabled:

- `.mjs` routes to the Node backend and runs as partial ESM
- static `import`/`export` syntax is accepted only in ESM entries and ESM graph modules
- dynamic `import()` supports local relative modules, allowed builtins, and packages resolved inside `workingDirectory`
- `import.meta` exposes only scoped metadata and scoped `resolve()`
- package `type=module` can route `.js` entries to ESM

ESM v1.1 is limited to scoped static imports, simple exports, top-level await, CommonJS default import through the existing CommonJS loader, CommonJS `require()` of synchronous partial ESM graphs, supported builtin imports through the Safe Node allowlist, scoped `import.meta.url`/`filename`/`dirname`/`main`/`resolve()`, and package `exports`/`imports` condition selection with importer-appropriate active condition sets. Condition objects preserve package key order; `autojs6` is active for both CommonJS and ESM, while `development` and `production` remain inactive unless a future explicit profile enables them. ESM package targets may use `.mjs` only when they remain inside the package root and working directory. It must not bypass scoped filesystem, package target, builtin, native addon, network, or bridge policies. Disabled builtins remain disabled when imported from ESM, resolved by `import.meta.resolve()`, dynamic imported, or CJS `require()` of ESM. Dynamic import remains local-only: URL imports, network imports, absolute paths, path escapes, disabled builtins, and dynamic-code sources such as `eval` and `Function` remain denied. Loader hooks and raw Node loader authority from `node:module` remain disabled; CJS `require()` of ESM graphs containing `await` or dynamic `import()` fails with `ERR_REQUIRE_ASYNC_MODULE`.

## Privacy-Safe Compatibility Modules

Some compatibility modules are present but intentionally redacted.

The `os` module must not expose raw device fingerprinting data. Network interfaces, CPU details, memory totals, load averages, hostname, and user information are synthetic or coarse values. This preserves compatibility for packages that feature-detect `os` without turning the embedded runtime into a host fingerprinting API.

The `crypto` module exposes bounded symmetric/hash helpers and a limited WebCrypto surface. It must not expose native Android keystore access, certificate inspection, asymmetric key APIs, native crypto handles, or unrestricted algorithm selection without a separate design gate.

## Network Policy

Network is not a default live I/O capability of the Safe Node Profile. The controlled fetch wrapper exists, but the explicit design-gate flag keeps the Android/OkHttp provider disabled in normal builds:

```properties
autojs.nodejs.network.experimental=false
```

Do not treat a present `http`, `https`, `net`, `tls`, `dns`, or fetch-shaped API as permission to perform I/O. The current `fetch` and `require("fetch")` APIs route through the AutoJs6 bridge and reject with `ERR_AUTOJS6_NETWORK_DISABLED` when the live provider is gated off. Raw network modules remain denied in the Safe profile. The denial applies to bare imports, `node:` aliases, and `node_modules` shadow attempts. Debug/build-time experiments may explicitly enable native Node `dns`, `dns/promises`, `http`, `https`, `net`, and `tls` with `autojs.nodejs.rawNetworkModules.experimental=true`.

Network enablement requires:

- Android permission policy
- packaged APK manifest policy
- timeout and abort support
- lifecycle cancellation on service destroy
- TLS and CA policy
- privacy review
- tests for denied and allowed paths

The AutoJs6-controlled `fetch` wrapper is the preferred Safe-profile network path over unrestricted socket modules. Userland HTTP clients must use this controlled path or another explicit AutoJs6 bridge adapter unless a debug/build-time raw network experiment is intentionally enabled. The wrapper supports request timeout, abort, response-size budgeting, redirect budgeting, concurrent-request limits, unsupported-scheme rejection, and provider-side cancellation. The Android provider tracks live OkHttp calls and cancels them on provider destroy. Raw network authority must stay default-off and release-forbidden until policy, privacy, and rollback gates are complete.

See `docs/nodejs/design/NETWORK.md` for the current network design gate.

Controlled WebSocket has an experimental bridge-backed MVP. `require("websocket")` is an AutoJs6-controlled module, not a desktop `ws` clone and not raw socket authority. The live Android/OkHttp provider remains disabled in normal builds by `autojs.nodejs.network.experimental=false`; normal provider calls reject with `ERR_AUTOJS6_NETWORK_DISABLED`.

The WebSocket wrapper must keep:

- max connection limits
- bounded message size
- bounded queued events
- connect timeout and bridge timeout behavior
- service-destroy cancellation
- `ws:`/`wss:` scheme validation
- raw `net`, `tls`, and HTTP upgrade denial
- explicit packaged APK INTERNET opt-in before live network use

See `docs/nodejs/design/WEBSOCKET.md`.

## Worker Threads Policy

`worker_threads` is not enabled in the default Safe Node Profile. The explicit experimental flag is:

```properties
autojs.nodejs.worker_threads.experimental=false
```

With the flag off, `require("worker_threads")` rejects with the disabled-builtin error shape. `require("node:worker_threads")` exposes only a shape-only npm compatibility facade: `MessageChannel` / `MessagePort` are local compatibility objects, `Worker` construction creates an inert disabled worker, and worker execution or `postMessage()` rejects with `ERR_AUTOJS6_WORKER_DISABLED`. A local `node_modules/worker_threads` package must not shadow the bare-module denial.

With the experimental request flag on, the bootstrap first checks whether the
Embedded Node build exposes a usable native `worker_threads` module. If it does
not, `require("worker_threads")` and `require("node:worker_threads")` fall back
to the same shape-only facade with `nativeAvailable=false`; `Worker`
construction remains inert, `postMessage()` rejects with
`ERR_AUTOJS6_WORKER_DISABLED`, active-worker diagnostics stay at zero, and there
is no same-thread fallback. When native workers are available, workers are pure
JavaScript compute workers with a smaller capability set than the parent script.
Worker source is resolved and read by the parent scoped resolver, then executed
through a worker-local restricted `require`. Worker scripts must not access:

- AutoJs bridge modules or `autojs6:bridge`
- Android `Context`
- accessibility, media projection, image, OCR, UI/dialog, shell, engines, clipboard, or device providers
- raw network modules
- native addons
- child process APIs
- `vm`
- inspector
- unrestricted filesystem APIs

Bridge ownership remains in the parent script. Workers may exchange pure data
with the parent through `parentPort.postMessage`, `worker.postMessage`, and
`workerData`; the worker itself may not call bridge modules. The MVP allows one
active worker, exposes `threadId`, uses a startup timeout, denies nested workers,
tracks active workers per execution, and terminates active workers during parent
success/failure cleanup. Packaged worker source graphs remain follow-up work.

See `docs/nodejs/design/WORKER_THREADS.md` for the current worker boundary.

P13-34 records `worker_threads` opt-in promotion as partial. The new
`workerThreadsProfile` diagnostics are not an authority grant: default
`worker_threads` remains disabled, project/package opt-in remains unavailable,
native workers must pass preflight before runnable claims, worker bridge modules
remain denied, nested workers remain denied, and raw native handles do not cross
into JavaScript. Crash evidence, packaged worker evidence, and Android resource
ceilings remain required before this boundary can relax.

S11-25 records the Rhino `threads` migration policy separately from the
internal worker policy. Rhino `threads.start()`, `threads.pool()`,
`threads.interrupt()`, `threads.disposable()`, and global `sync(fn, lock)` are
`blocked_by_design` for Node because they depend on shared Rhino
`ScriptRuntime` objects, raw Java `Thread` / `ThreadPoolExecutor` handles,
Rhino `Synchronizer`, Java locks, and blocking `VolatileDispose` waits. The
Safe Node Profile must not add `require("threads")`, `compat.threads`, a
global `threads`, a global `sync`, or default `worker_threads` as a migration
shortcut. Supported migration directions are `Promise`, `async` / `await`,
timers, `AbortController`, bridge `timeoutMs`, explicit
`engines.execScriptFile()` / `engines.execScript()` execution slots, and
`work_manager` scheduled work. See
`docs/nodejs/design/RHINO_THREADS_MIGRATION_POLICY.md`.

## Native Code Policy

Native addons are disabled by default. Any CommonJS path that resolves to a `.node` binary must fail with
`ERR_AUTOJS6_NATIVE_ADDON_DISABLED`, including direct relative imports, `module.createRequire()`, package `main`,
package `exports`, package `imports`, and package subpath imports.

Reasons:

- they execute arbitrary native code in the embedded process
- they bypass JavaScript-level policy
- ABI distribution is complex
- packaged APK injection is complex
- they can bypass scoped filesystem and builtin restrictions

Future support, if any, should be developer-mode only, separately gated, and based on an explicit plugin model with ABI,
signature, packaging, and review policy. Runtime npm/native build tooling must not be used as the enablement path.

P13-39 records native addon loading as partial. `nativeAddonProfile` reports
default denial for `.node`, addon resolution, and `process.dlopen` while
making signed packaged-only artifacts, ABI and hash allowlists, dependency
scan, no download-exec, and crash isolation explicit promotion blockers. The
current runtime still rejects arbitrary native addon files and private process
native APIs before user code can turn them into embedded-process native code.

## Java Interop Policy

Direct Java and Android interop is disabled in the default Safe Node Profile. Node scripts must use AutoJs bridge modules instead of raw `java.*`, `android.*`, Rhino `importClass`, `Packages`, reflection, class loaders, or Android object references. P13-12 exposes `$autojs.androidContext` only as a frozen unavailable descriptor and `$autojs.java` only as a gated facade; normal builds still reject Java calls with `ERR_AUTOJS6_JAVA_INTEROP_DISABLED`.

The `java_interop` feature flag is critical, disabled by default, build-time experimental only, and not overrideable by `project.json` or packaged APK metadata. The current experiment exposes only `require("java")` and gated `$autojs.java` with a tiny allowlist for `java.lang.Math`, `java.util.UUID`, and opaque `android.graphics.Rect` handles. P13-13 permits selected Rect instance methods, field reads, and explicit release/dispose cleanup through provider-owned handles. It must not expose `Context`, `Activity`, `Service`, `AccessibilityService`, `MediaProjection`, `Intent`, Binder, `ClassLoader`, reflection, filesystem, process, native-library, or raw UI objects.

See `docs/nodejs/design/JAVA_INTEROP.md` for the mode comparison, allowlist candidates, Rhino compatibility boundary, and future test gate.

Rhino compatibility shims are a separate migration concern. Node must not install Rhino globals, `importClass`, `Packages`, or `JavaAdapter` by default. S11-26 fixes the explicit `rhino.run({ source, timeoutMs, args })` / scoped `path` design. P12-24 reserves `require("rhino")` as a disabled facade in the current Safe Node Profile: `rhino.run()` without `experimental: true` rejects with `ERR_AUTOJS6_RHINO_SHIM_DISABLED`, `rhino.install()` throws `ERR_AUTOJS6_RHINO_INSTALL_UNSUPPORTED`, `require("node:rhino")` remains unavailable, local `node_modules/rhino` cannot shadow the controlled module, and `compat.rhino` remains absent. P12-23 establishes a default-disabled POC gate, and P12-25 implements `rhino.run({ experimental: true, source|path, timeoutMs, args })` through `NodeBridgeRhinoExecutionService`, which executes outside the V8 isolate in an Android Rhino engine, accepts and returns only JSON-safe snapshots, enforces required timeouts, bounds console capture, destroys the Rhino engine after execution, and rejects Java objects, Rhino `Scriptable`, Android objects, functions, handles, threads, locks, native pointers, process/file descriptors, cycles, and prototype-bearing objects at the boundary. P13-14 adds a separate explicit experimental `rhino.install({ experimental: true })` Java proxy POC: it may install `Packages`, `java`, and `android` proxies only when Java interop is enabled, and those proxies route through the same allowlist bridge instead of exposing raw Rhino or Java objects. P13-15 keeps Rhino `importClass` and `importPackage` semantics unsupported; explicit `require("rhino").importClass()` / `.importPackage()` calls throw `ERR_AUTOJS6_RHINO_IMPORT_UNSUPPORTED` and the migration path is local assignment from a P13-14 proxy target. P13-16 keeps `JavaAdapter` unsupported with `ERR_AUTOJS6_RHINO_JAVA_ADAPTER_UNSUPPORTED` and reserves `java.defineClass()` / `$autojs.java.defineClass()` as stable-denied replacement names until a bounded proxy-interface provider proves callback cleanup, thread dispatch, packaged policy, and leak diagnostics. Classloader, reflection, native library, and process authority remain denied. P12-26 records packaged metadata behavior as fail-closed. No packaged metadata shape is accepted in Phase 12; future metadata may authorize only JSON-only run or a similarly explicit proxy surface, never unrestricted globals, and packaged APK execution remains rejected until packaged timeout, crash, and destroy evidence exists. See `docs/nodejs/design/RHINO_SHIM.md`, `docs/nodejs/design/RHINO_JSON_ONLY_SHIM.md`, `docs/nodejs/reports/phase12/RHINO_JSON_ONLY_SHIM_POC_GATE.md`, `docs/nodejs/reports/phase12/RHINO_JSON_ONLY_EXECUTION_ADAPTER_POC.md`, and `docs/nodejs/reports/phase12/RHINO_SHIM_PACKAGED_METADATA_GATE.md`.

P12-32 promotes `s13n.bytes` only as a pure JavaScript compatibility helper
through `require("s13n")` and `require("autojs6:compat").s13n.bytes`. It has no
Android provider authority and must not receive or return raw Java, Android, or
Rhino objects. `s13n.color`, `s13n.point`, `s13n.throwable`, and `s13n.time`
remain stable unsupported with `ERR_AUTOJS6_RHINO_COMPAT_UNSUPPORTED` until
each helper has a separate JSON-safe contract. P12-34 keeps that guidance in
the formal migration, Pro 9 comparison, testing, matrix, and type declaration
docs.

P12-33 dual-engine device evidence is a verification artifact, not a runtime
permission. The fake device records carry only `engine`, `compatStatus`,
`providerMode`, `resourceCleanupStatus`, and `skipReason`, and the
`RhinoNodeDualEngineCompatInstrumentationTest` hook validates that shape before
printing evidence. This does not promote new live providers, Java interop,
global Rhino installation, or raw object access.

## AutoJs API Bridge Policy

Node scripts must not hold raw Android or AutoJs privileged objects.

Allowed shape:

```text
JS Promise API
  -> bridge request
  -> NativeNodeEmbeddedScriptService
  -> main process capability provider
  -> async result or policy error
```

Required bridge properties:

- JSON-serializable request and response payloads
- request ids
- strict request shape: non-empty `id`/`module`/`method`, array `args`, string-array `permissions`, positive numeric `timeoutMs`
- timeouts
- cancellation or rejection on service destroy
- at-most-once completion for each provider request callback
- synchronous provider exceptions converted to bridge error responses
- fake providers for tests
- permission error codes
- project-declared bridge capability gate with `ERR_AUTOJS6_BRIDGE_CAPABILITY_NOT_DECLARED`
- provider-side pre-dispatch capability audit for every supported bridge module/method
- malformed request errors with `ERR_AUTOJS6_BRIDGE_INVALID_REQUEST`
- a centralized module/method registry for provider routing and diagnostics
- a per-execution resource registry for provider-owned handles
- a per-execution concurrency and resource limit policy
- a capability broker v2 declaration/preflight/audit layer
- main/UI/IO dispatcher separation
- callbacks into JS marshalled onto the Node event loop thread

Bridge capability manifests are optional for legacy compatibility. When a Node project declares `node.permissions` in `project.json` or `autojs6.node.permissions` in `package.json`, the bridge becomes deny-by-default for that execution. Missing declarations reject in JavaScript before the request reaches the native or Android provider. The Android provider also audits every supported bridge request before dispatch: the request must carry the required capability names, and an enforced project manifest must declare them. Failures use `ERR_AUTOJS6_BRIDGE_CAPABILITY_NOT_DECLARED`.

Canonical capability names are `toast`, `app.launch`, `app.settings`, `app.activity`, `app.query`, `dialogs`, `engines`, `engines.exec`, `accessibility`, `screen_capture`, `image`, `ocr`, `storage`, `notifications`, `notifications.settings`, `media`, `media.audio`, `media.metadata`, `media.recording`, `sensors`, `ui`, `clipboard`, `device`, `device.power`, `shell`, `shell.root`, `shell.shizuku`, `network`, `work_manager`, and `java_interop`. Declaring a parent such as `app` or `engines` grants its ordinary child methods; `app.query`, `notifications.settings`, `media.audio`, `media.metadata`, `media.recording`, `device.power`, `shell.root`, and `shell.shizuku` must be declared explicitly. `media_projection` is accepted as an alias for `screen_capture`, and `storages` is accepted as an alias for `storage`.

Declaring `network` is required for controlled `fetch`, `axios`, and `websocket` bridge dispatch when a capability manifest is enforced. Packaged builds map that capability to `android.permission.INTERNET`; doctor reports expose the mapping and the raw Node network module opt-in state.

Bridge limit policy is optional and safe by default. Projects may lower or safely raise limits through `node.bridgeLimits` in `project.json` or `autojs6.node.bridgeLimits` in `package.json`; values above the hard maximum are ignored and reported through `profile.bridgeLimits.warnings`. The current policy bounds pending bridge calls, JS image handles, live shell commands, controlled fetch calls, controlled WebSocket sessions, and accessibility requests per second. Resource exhaustion uses `ERR_AUTOJS6_BRIDGE_RESOURCE_LIMIT`; request-rate exhaustion uses `ERR_AUTOJS6_BRIDGE_RATE_LIMITED`.

Capability broker v2 is the common provider-side security envelope for bridge
dispatch. Every Android provider request must match an explicit operation
declaration, carry JSON-array arguments, stay inside broker payload limits,
record cancellation/execution ownership metadata, and write a quota-bound
per-call audit row. The broker runs before provider dispatch and before any
future isolated-process Binder broker can be promoted. It does not grant new
capabilities or make `node_sandboxed` executable.

Runtime budget policy is a diagnostic and alignment layer over those provider
limits. Safe `node.runtimeBudget`/`autojs6.node.runtimeBudget` metadata and the
internal `autojs6:runtime-budget` source may tune reported budgets only within
the effective bridge hard caps for bridge-owned resources. Above-cap overrides
are ignored and surfaced as warnings. Provider quota enforcement remains the
authority that rejects or cleans up the offending resource.

Current `toast` and `app` support uses a private live transport in the main embedded service. The service injects per-execution bridge directories under its private cache, the JavaScript bootstrap writes JSON request files with an internal native filesystem helper, and the service writes JSON responses back for the pending Promise. User scripts are not given the bridge directory paths or raw Android objects.

When the live transport configuration is absent, such as in older packaged inrt templates, the bridge falls back to the queued host-effect envelope. In queued mode JavaScript only observes that the request was accepted; the Android dispatch result is diagnostic output after script completion.

Any provider-owned resource created through the bridge must be registered with the current execution id before JavaScript receives or depends on its handle. Registry records include an id, type, creation timestamp, owner execution id, metadata, and an idempotent close/recycle callback. On execution stop or service destroy, the provider closes all remaining registry entries, clears stale handle maps, and logs a leaked-resource summary through `embedded_script.bridge_resource.*` diagnostics. Current live registrations cover controlled fetch calls, controlled WebSocket sessions, shell executions, WorkManager-backed `work_manager` task registrations, notifications owned by the current Node execution, bounded sensor subscriptions, and experimental Java object handles; image handles, screen capturers, dialogs, UI sessions, and workers must use the same registry and limit policy when their Android providers become live.

The JS-only plugin manager has a separate per-plugin owner registry for
resources that plugin lifecycle code explicitly registers through
`ctx.resources`. It tracks timers plus provider/UI/socket/image/generic handles
inside the current Node execution and releases them on `plugins.stop()`,
`plugins.unload()`, and `plugins.disable()`. This registry is not a substitute
for Android provider-owned bridge resources: real provider handles must still be
registered and audited by the bridge provider before crossing into JavaScript.
Plugin lifecycle contexts expose only the effective capability subset
(`plugin.<id>` plus plugin-requested permissions), frozen JSON config, namespace
metadata, and registry helpers; they do not expose raw `Context`, `Activity`,
`View`, Binder, filesystem authority, Java objects, native pointers, sockets,
or unrestricted Node builtins. Debug hot reload remains explicit through
`plugins.reload(id, { debug: true })` and cannot be invoked as the default
release lifecycle path.

The `app` bridge exposes JSON-only intent descriptors and read-only package metadata. It does not expose raw Android `Intent`, `Context`, `PackageManager`, or `Activity` objects to JavaScript. Invalid intent descriptors must fail with a stable bridge policy error.

The `engines` bridge exposes only serialized engine and execution snapshots. It does not expose raw `ScriptEngine`, `ScriptExecution`, `ScriptRuntime`, thread, or process control objects. `execScript()` and `execScriptFile()` are routed through a non-exported main-process dispatch service so script startup still goes through AutoJs6's script manager rather than the isolated Node service process. Spawning requires an explicit `timeoutMs`, scoped `cwd`/path validation, JSON-primitive arguments, and a bounded spawn depth. Nested embedded Node child scripts are rejected until multi-instance embedded lifecycle ownership is designed. `execAutoFile` remains unsupported in the Safe Node Profile.

The `dialogs` bridge currently exposes a stable JavaScript Promise API and protocol surface, but the Android provider rejects until a UI capability provider is connected. This avoids accidentally showing service-context or overlay dialogs from the embedded Node process without an explicit Activity/UI ownership design.

The `accessibility` bridge currently exposes a stable JavaScript Promise API and protocol surface, including selector v2 descriptors. Builders such as `text(value)`, `desc(value)`, `id(value)`, `className(value)`, `textContains(value)`, `descContains(value)`, regex selector builders, clickable/enabled flags, depth, and bounds filters return frozen plain JSON descriptors; `findOne(selector)`, `findAll(selector)`, `click(selector)`, `longClick(selector)`, `setText(selector, text)`, `scrollForward(selector)`, and `scrollBackward(selector)` send those descriptors through the bridge. Regex values are serialized as `{ pattern, flags }`, bounds are serialized as `{ left, top, right, bottom }`, and all calls remain subject to the accessibility request-rate limit. When an operational AutoJs6 accessibility service is visible in the current process, the Android provider returns booleans or frozen JSON node snapshots for controlled actions and queries; the focused live gate may launch a controlled Activity to exercise selector `findOne` and selector `click`, but it must not enable the service or alter accessibility settings. When the provider is unavailable in the current process, including isolated embedded service processes that cannot see the main-process service instance, `isEnabled()` and `ensureEnabled()` return `false` and actions/queries reject with `ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED`. No raw `AccessibilityService`, `AccessibilityNodeInfo`, `UiObject`, or live selector object crosses into Node. Node results must remain frozen JSON snapshots or opaque handles with explicit lifecycle cleanup.

P13-19 records accessibility/selector promotion as partial. The current gate
allows direct JSON selector APIs and explicit Rhino compat selector handles to
remain documented and tested, but it must not imply `swipe`, `gesture`,
`powerDialog`, raw node handles, root input automation, or Shizuku authority.
Direct `waitFor`, `pickup`, selector-style `detect`, `existsAll`, and
`existsOne` stay stable-denied for P13-19 v1.1 because they can imply live
Rhino node handles or callback return values. Gesture/root/Shizuku paths need
separate capability, lifecycle, packaged, and disclosure evidence before
promotion.

The `media_projection` bridge currently exposes the JavaScript Promise API and opaque screen-capturer handle shape, but the Android provider rejects until an Activity-owned MediaProjection capability provider is connected. S11-19 `require("autojs6:compat").requestScreenCapture()` and `requestScreenCaptureAsync()` are only Rhino-style wrappers over that controlled provider. They cache an opaque capturer handle for later stop/cleanup, return `true` only after the provider grants a capturer, and preserve provider denial as a stable bridge error. They must not request screen-capture permission from the embedded Node service process directly, expose a raw `MediaProjection`, or create a packaged fallback prompt path.

The `image` and `images` bridge modules currently expose opaque image-handle API shapes, fake-provider tests, and a limited live Android provider path for scoped local-file `readImage()` plus `recycle()` so OCR can consume provider-owned handles. S11-18 `require("autojs6:compat").images` is only a Rhino-name compatibility facade over the same controlled `images` module: it normalizes `read`, `save`, region/size overloads, threshold arguments, matching, color search, and `recycle()` without exposing raw provider objects. S11-19 `compat.captureScreen()` and `compat.images.captureScreen()` force the existing-permission capture path; `captureScreen(path, options)` saves through `images.saveImage()` and recycles the temporary handle before resolving. Transform operations return new opaque image handles and must count against the per-execution image-handle limit until recycled; matching and color operations return frozen JSON point/match snapshots only. The Android provider still rejects screenshot capture, save, transform, template-matching, and color-search methods until those operations are owned by a dedicated capability provider. `captureScreen()` must use an already-authorized MediaProjection provider path and must not request screen-capture permission from the embedded Node service process. JavaScript must never receive raw `Bitmap`, `Mat`, `ImageWrapper`, native pointer, or filesystem path handles outside the scoped policy.

The `ocr` bridge module exposes `recognize(image, options?)`, `recognizeText(image, options?)`, and `detectTextBounds(image, options?)` over existing opaque image handles. It requires both `ocr` and `image` capabilities, validates that the handle is active and provider-owned before dispatch, returns frozen JSON text-result snapshots for result-list methods, and uses the current live Android MLKit provider for Chinese text recognition over those handles. Optional OCR regions are bounded to the source image. Unknown or recycled handles reject with `ERR_AUTOJS6_BRIDGE_INVALID_REQUEST`. S11-20 `require("autojs6:compat").ocr` is only a Rhino-name facade over the same controlled provider: path inputs are loaded through scoped `images.readImage()` and recycled after OCR settles, missing image inputs capture through the existing-permission screenshot path, `recognizeText()` returns string arrays for Rhino parity, and Paddle/Rapid mode switching is blocked by design. P12-28 exposes the direct `barcode` bridge provider over ML Kit barcode scanning. It requires `barcode` and `image`, accepts only provider-owned image handles on the Android bridge, routes path/capture inputs through temporary `images` handles in the JS facade, validates unknown/recycled handles before scanner dispatch, enforces QR-only and format filters, and reports cleanup through barcode operation diagnostics. P12-29 `compat.barcode` and `compat.qrcode` are Rhino-name facades over that same controlled provider; `compat.qrcode` forces QR_CODE filtering. JavaScript must not receive raw OCR engines, barcode scanners, raw ML Kit `Barcode`, model paths, native pointers, `Bitmap`, `Mat`, `ImageWrapper`, or Java objects.

P13-20 records screenshot/image/OCR promotion as partial. The current security
boundary allows scoped image handles, explicit recycle, and ML Kit OCR over
provider-owned handles, but it keeps MediaProjection bridge prompting, advanced
image processing, and packaged capture/OCR behavior behind later gates.
Paddle/Rapid/plugin OCR provider selection and raw Bitmap/OpenCV/MediaProjection
object parity stay stable-denied for P13-20 v1.1.

S11-21 records Rhino `events`/`events.keys`/input observer compatibility as design-gated. P12-03 promotes only `events.keys` constants through `autojs6:compat`; `require("autojs6:compat")` must not expose Rhino `events.__asEmitter__()`, Java `EventEmitter`, Rhino `ScriptableObject`, `Thread`, `MainThreadProxy`, raw `KeyEvent`, raw touch events, or Android listener objects. P12-05 adds a direct `input_observer` fake-source provider POC with bounded queues, `drainEvents`, explicit `close()`, ResourceRegistry-owned subscription cleanup, and capability checks for `input_observer` and `input_observer.keys`. P12-06 adds the accessibility live-source gate: `source: "accessibility"` registers a controlled `OnKeyListener` only when an operational AutoJs6 accessibility service is visible in the current process, queues JSON snapshots, and removes the listener through the same resource close path or accessibility-service disconnect callback. P12-07 adds cleanup/security diagnostics and scheduled/background rejection: active subscriptions, pending events, dropped events, and live listener count are reported; scheduled/background launch surfaces reject live observation before listener registration; fake queue overflow is bounded and reports `droppedCount`. Live touch observation, `input_observer.touch`, and `input_observer.intercept` remain separate rejected provider gates. Pure JavaScript code can use Node's built-in `node:events`. Packaged or background support must reject without a valid foreground/user-visible provider state; it must not create silent service-process key/touch listeners, shell/root/overlay fallback capture, or hidden key interception.

The `storage` and `storages` bridge modules expose JSON-only key-value stores through frozen JavaScript handles. They require the `storage` capability, scope Android provider data by package identity, working directory, and store name, store values in app-private provider-owned SharedPreferences, and enforce bounded synchronous writes with per-value and per-store quotas. JavaScript must not receive raw `SharedPreferences`, database, file handles, `ContentResolver`, Android `Context`, or direct storage paths.

The `notifications` bridge module exposes owned posting/cancel APIs plus JSON-safe permission/settings snapshots. It exposes `notify()`, `cancel()`, `cancelAll()`, `getPermissionStatus()`, `openSettings()`, and `openNotificationSettings()`, maps packaged capability metadata to `android.permission.POST_NOTIFICATIONS`, checks Android notification runtime state before posting, and creates stable provider-owned channel ids. Settings helpers require exact `notifications.settings` when a manifest is enforced and support `dryRun` for tests. The provider registers notification ids owned by the current Node execution, records channel/ongoing metadata for diagnostics, and cancels remaining owned notifications on destroy. `cancel(id)` must not cancel unrelated app notifications. JavaScript must not receive raw `Notification`, `NotificationChannel`, `PendingIntent`, `RemoteViews`, `NotificationManager`, `Context`, `Intent`, or action/listener objects.

The `sensors` bridge module is read-only and bounded. It exposes available sensor metadata, one-shot reads, and event subscriptions only for `accelerometer`, `gyroscope`, and `light` under the `sensors` capability. Sampling intervals are clamped, active subscriptions and event queues are capped, missing provider or hardware paths return stable bridge errors, and unsubscribe/provider destroy unregister Android listeners. JavaScript receives frozen JSON event snapshots only and must not receive raw `SensorManager`, `Sensor`, `SensorEvent`, listener handles, Android `Context`, or high-risk sensor classes without a separate privacy review.

The `ui` bridge module exposes an Activity-owned live MVP under the `ui` capability. It accepts JSON-only `showLayout(layoutJson, options?)`, returns an opaque `UiHandle`, supports `UiHandle.on(event, callback)`, `UiHandle.update(patch)`, `UiHandle.batchUpdate(patches)`, `UiHandle.close()`, one-way `state`/`bind` helpers, and provider-drained event/state snapshots through bounded `drainEvents` calls. Bindings are resolved to JSON in the Node process before dispatch; the UI process never receives JavaScript functions, closures, or executable binding expressions. The Android provider owns a non-exported internal Activity and currently supports declarative `Column`, `Row`, `Text`, `Button`, `Input`, `Image`, `ScrollView`, `List`, `Switch`, `Checkbox`, `Progress`, and `Tabs` components. `WebView` remains rejected until a separate security gate exists. UI sessions are ResourceRegistry-owned, active session count is bounded per execution, and owned sessions close on provider destroy. JavaScript must never receive raw `Context`, `Activity`, `View`, `Window`, `Fragment`, `Handler`, Rhino `NativeView`, Android resource object, overlay window, Binder, or Java object through this module.

P13-17 records the live UI provider promotion state without widening this
boundary. The main-app Activity provider has device evidence for JSON layout,
event drain, update, close, and provider cleanup, but packaged live UI remains
provider-dependent until Back/Recents, orientation/configuration-change,
relaunch/crash, and packaged capability/disclosure denial evidence lands.

S11-23 records Rhino `ui` compatibility as design-gated. `ui.layout()` and `ui.layoutFile()` may migrate only through constrained JSON descriptors passed to `require("ui").showLayout()`, and updates must use `UiHandle.update()` / `UiHandle.batchUpdate()`. `ui.run()` and `ui.post()` are blocked by design for Node because they imply JavaScript execution on Android's main thread; Node callbacks run on the Node event loop after bounded `drainEvents` snapshots. `ui.inflate()`, `setContentView(view)`, `findView()`, `root`, widget registries, `R`, and Activity emitters must not expose `NativeView` or Android `View` objects to Node.

P12-10 constrains `compat.ui` smoke coverage to the same JSON-only provider boundary. The packaged Rhino compat smoke uses fake bridge transport markers to prove `compat.ui` routing and zero pending bridge calls in packaged form, while live packaged/background UI remains unpromoted. Scheduled/background execution policy must not list `ui` as an allowed provider until a separate packaged Activity lifecycle, Back/Recents, and disclosure gate exists.

S11-24 keeps Rhino `floaty` and overlay compatibility design-gated. There is no `require("floaty")`, `compat.floaty`, global `floaty`, packaged `SYSTEM_ALERT_WINDOW` injection, or foreground notification/disclosure model in the current Node profile. `floaty.window()` and `floaty.rawWindow()` are blocked as exact compatibility targets because they depend on XML inflation, `FloatyService`, `WindowManager`, `LayoutParams`, live `JsWindow` wrappers, and raw `View`/`NativeView` lookup. Future overlay support must use explicit overlay capability declaration, Android permission checks, user-visible foreground disclosure where required, provider-owned opaque handles, bounded `drainEvents`, and auditable close paths for handle close, script stop, service destroy, notification stop, permission revocation, packaged task removal, and process death. `floaty.getClip()` must migrate to the controlled `clipboard` bridge or `autojs6:compat.getClip()` rather than creating a hidden temporary overlay window.

P12-11 selects the Provider path for a controlled JSON-only overlay provider. That decision still keeps exact Rhino `floaty.window()` and `floaty.rawWindow()` raw overlay wrappers as a permanent non-goal for Node. P12-12A locks the explicit `ui.overlay` capability, permission checks, foreground disclosure, packaged manifest, opaque handle, and cleanup contract in [OVERLAY_PROVIDER_V1.md](design/OVERLAY_PROVIDER_V1.md). P12-13 exposes `require("ui.overlay")` only through fake transport for JS facade, capability, handle lifecycle, and denial-path evidence. P12-14 adds the Android live gate for permission snapshots, explicit settings dry-run/launch evidence, `SYSTEM_ALERT_WINDOW` packaged metadata, foreground disclosure requirements, and closeAll cleanup summaries; scheduled/background silent overlays remain rejected and live overlay creation remains fail-closed.

After P12-14, `require("floaty")`, `compat.floaty`, and global `floaty` remain absent; `require("ui.overlay")` is a fake transport POC plus live gate and not live overlay authority. Raw Rhino-window examples may use `compat.blockedByDesign()` with `ERR_AUTOJS6_RHINO_COMPAT_BLOCKED_BY_DESIGN`, but provider work must move through the JSON-only overlay contract instead of dispatching raw Rhino window bridge requests. No Node execution mode may create a hidden overlay, silently request overlay permission, inject `SYSTEM_ALERT_WINDOW` without explicit package metadata, or register overlay resources outside the provider lifecycle.

P13-18 records the overlay provider promotion state as partial. Permission
snapshots, settings dry-run/launch evidence, explicit overlay capabilities,
packaged `SYSTEM_ALERT_WINDOW` metadata records, type coverage, and cleanup
summaries are checked, but live overlay creation remains fail-closed. The
surface cannot move to `pro_compat_opt_in` until foreground disclosure
ownership, packaged install-run denial/positive cases, task-removal cleanup,
crash/relaunch behavior, and real overlay leak/stress evidence are available.

S11-25 keeps Rhino `threads` compatibility blocked by design. There is no `require("threads")`, `compat.threads`, global `threads`, global `sync`, default `worker_threads`, raw `Thread`, raw `ThreadPoolExecutor`, Rhino `Synchronizer`, Java lock, or `VolatileDispose` object exposed to Node. `threads.start()` and `threads.pool()` must migrate to `async` / `await`, timeout-bounded bridge calls, explicit `engines` execution slots, or `work_manager` scheduled work. `threads.interrupt()` must migrate to `AbortController`, bridge `timeoutMs`, script stop, and provider cleanup. `threads.disposable()` and `sync(fn, lock)` must migrate to Promise, `EventEmitter` / `MessageChannel`, immutable JSON snapshots, or an async queue with a single state owner. Future compute workers remain policy-gated and must not claim same-runtime Rhino thread semantics.

Image and node handles must be opaque ids with explicit lifecycle cleanup.

The `clipboard` bridge exposes only `getText()`, `setText(text)`, and `hasText()` Promise APIs. The Android live provider performs system clipboard access on the main thread and returns JSON strings or booleans through the bridge. Older packaged templates that lack live bridge metadata use a per-execution queued fallback string so packaged scripts can preserve Promise behavior without receiving background system clipboard access. The module must not expose raw `ClipboardManager`, `ClipData`, listeners, clipboard history, or background watchers to JavaScript.

The `notifications` bridge exposes owned notification posting/cancel APIs plus
P13-25 permission/settings snapshots. `notifications.openSettings()` and
`openNotificationSettings()` require exact `notifications.settings` when a
manifest is enforced and support `dryRun` for tests. JavaScript must not
receive raw `Notification`, `NotificationChannel`, `NotificationManager`,
`PendingIntent`, `RemoteViews`, `Intent`, or Android settings objects.

The `device` bridge is privacy-safe by default. It exposes only coarse runtime/display properties (`sdkInt`, `width`, `height`, `density`) and Promise APIs for `isScreenOn()`, `wakeUp()`, `vibrate(ms)`, `isIgnoringBatteryOptimizations()`, and `openBatteryOptimizationSettings()`. The live provider uses Android `PowerManager` and `Vibrator` through the bridge and does not expose raw Android objects. Battery optimization helpers require exact `device.power` and settings launch supports `dryRun`. Device identifiers and fingerprinting surfaces must remain absent, including IMEI, Android ID, serial, phone number, accounts, network MAC/IP, full hardware fingerprint, brand, and manufacturer. Packaged APKs using vibration must declare `android.permission.VIBRATE`.

P13-25 records notifications/settings/power manager promotion as partial.
Notification actions/listeners/rich layouts, Do-Not-Disturb/exact-alarm
policy, direct wakelock/power-manager control, request-ignore-battery-
optimization flows, OEM power matrices, packaged disclosure, and migration
examples remain blocked.

The `media`, `mediainfo`, and `recorder` bridge modules expose only a partial
P13-26 provider. `media` returns read-only Android audio-stream volume/status
snapshots behind exact `media.audio`. `mediainfo` reads scoped relative files
inside the Node execution working directory and returns frozen JSON metadata
behind exact `media.metadata`. `recorder.getStatus()` returns provider status
behind exact `media.recording`, while `recorder.start()` and `recorder.stop()`
fail closed until a foreground recording provider and disclosure model exist.
JavaScript must not receive raw `MediaPlayer`, `MediaRecorder`, `AudioManager`,
`MediaInfo`, `MediaStore`, `Uri`, `Context`, native pointers, file descriptors,
or Android media object handles.

P13-26 records media/recorder/mediainfo provider promotion as partial.
Playback, real recorder sessions, foreground-service microphone disclosure,
MediaStore access, packaged media asset lookup, corpus/device evidence, and
migration examples remain blocked.

P13-27 records utility-heavy Rhino modules promotion as partial. The promoted
`jsox.mathx`, `jsox.arrayx`, and `jsox.numberx` subsets are pure JavaScript and
do not require an Android bridge provider. `jsox.extend()` and `extendAll()`
copy helpers only to an explicit target object; default global `Math` or
prototype mutation is blocked by design. `Mathx.dist()` accepts JSON-safe point
arrays and `{ x, y }` objects only. Raw Android/OpenCV point or rect object
parity is stable-denied for P13-27; future compatibility requires a separate
opt-in profile gate. `zip`, `canvas` JSON drawing provider/raw Canvas/Paint
decision, OpenCC/pinyin packaged resources, and `sysprops` remain blocked until
separate provider/profile gates exist.

The `shell` bridge is the controlled AutoJs6 replacement for Node `child_process`, not an unrestricted process-spawning API. The migration path is intentionally narrow: `child_process.exec()` maps to `require("shell").exec()` when the project declares the `shell` bridge capability; S11-17 `require("autojs6:compat").shell(command, optionsOrRoot)` is only a callable compatibility wrapper over the same controlled facade. `spawn()`, `fork()`, streaming stdio, and interactive shells remain unsupported. It exposes `exec(command, options?)`, JS-layer `execFile(path, args?, options?)`, `checkAccess(options?)`, and `setDefaultTimeout(ms)`, and returns serialized `{ code, stdout, stderr }` results for command execution. `execFile()` validates a scoped relative path and string arguments, shell-quotes them, and then calls the same underlying `exec` bridge method. Commands run on a provider thread through a non-interactive shell; JavaScript never receives a `Process`, file descriptor, stream, signal handle, or raw Android shell object. Root mode is explicit through `options.root === true` or the migration shorthand `compat.shell(command, true)`, requires the separate `shell.root` capability when a manifest is enforced, and is never default. Shizuku execution remains unsupported in v1.1; `checkAccess({ shizuku: true })` reports it as unavailable instead of creating a fallback path. `cwd` is scoped to the execution working directory, `env` is JSON string-only, command output is bounded, and timeouts are enforced. The queued packaged fallback must not execute shell commands; `shell.exec()`, `shell.execFile()`, and `compat.shell()` require live bridge metadata so command results and denials are observable before the Promise settles.

P13-24 records shell/root/Shizuku capability promotion as partial. The
capability vocabulary now distinguishes `shell`, `shell.root`, and
`shell.shizuku`; `shell.execRoot()` is an explicit root facade over the
controlled shell bridge, while `shell.execShizuku()` and
`shell.exec(..., { shizuku: true })` reject with
`ERR_AUTOJS6_SHELL_SHIZUKU_UNSUPPORTED` before bridge dispatch. For v1.1,
Shizuku execution is stable-denied: `shell.shizuku` is auditable vocabulary
only until a dedicated provider owns authorization, lifecycle, cleanup,
packaged disclosure, and rollback evidence. Shizuku service handles, Binder
access, root shell handles, and raw process/file-descriptor/stream/signal
objects remain denied.

The `fetch` bridge is the controlled AutoJs6 Safe-profile replacement for Node raw network modules. JavaScript receives only a fetch-compatible Promise wrapper and buffered response helpers. Request descriptors are JSON-serialized, method-limited to `GET` and `POST`, bounded by `timeoutMs`, `maxResponseBytes`, `maxRedirects`, and max active request count, and may be cancelled through `AbortController.signal`. Only `http:` and `https:` URLs are accepted. The live Android provider is gated by `BuildConfig.NODEJS_NETWORK_EXPERIMENTAL_ENABLED`; normal builds reject before network I/O. Provider destroy cancels tracked OkHttp calls and must not call back into a destroyed Node environment.

P13-29 closes the controlled HTTP/HTTPS client subset for v1.1. The
`http`/`https` modules remain controlled-fetch facades in the Safe profile and
raw Node network modules remain disabled by default: server/listen, raw sockets,
DNS/TLS session control, proxy/tunnel options, `CONNECT`, upgrade, and custom
connector hooks reject before raw transport authority can be acquired.

P13-30 records net/tls/dns staged compatibility as partial. By default, the `net`, `tls`,
`dns`, and `dns/promises` modules are reserved limited builtins for compatibility
probing and deterministic local behavior only. `net.connect()`,
`net.createConnection()`, `net.Server.listen()`, `tls.connect()`,
`tls.createConnection()`, `tls.Server.listen()`, DNS `resolve*()`/`reverse()`,
TLS renegotiation, and keying-material export reject with the canonical AutoJs6
network-disabled code. DNS lookup remains loopback/IP-literal-only and
`dns.getServers()` returns an empty list so Android resolver servers, VPN/private
network state, search domains, and local topology are not exposed.
The release-forbidden `autojs.nodejs.rawNetworkModules.experimental=true`
build/debug opt-in routes CommonJS `dns`, `dns/promises`, `http`, `https`,
`net`, and `tls` to Node native builtins for Desktop parity experiments.

P13-31 records dgram UDP capability as stable-denied. `dgram` / `node:dgram`
is not a shape facade and cannot be loaded through local package shadowing or
transitive package requires. UDP bind, send, recv, connect, multicast,
broadcast, membership, TTL, and interface control remain denied as a whole; no
UDP socket or fd is opened by the Node runtime in this profile. A future UDP
provider must add explicit capability tiers, Android permission mapping, packet
budgets, background behavior, and fd leak evidence before this boundary can
change.

P13-32 records filesystem profile expansion as partial. The runtime now exposes
a frozen diagnostic `autojs6:profile.filesystemProfile` object, but it reports
only `mode="scoped_working_directory"` and `additionalRoots=[]`. This is not an
authority grant: user-authorized roots, `/sdcard`, SAF/document roots,
app-private roots, and Android shared storage remain denied until a future root
provider supplies explicit grant, revocation, symlink/realpath audit, packaged
evidence, and rollback behavior.

P13-33 records desktop fs advanced APIs as partial. `filesystemProfile`
diagnostics now describe the current advanced fs envelope, but they do not grant
new authority: `rawFd` stays false, numeric fd values remain execution-owned
compatibility tokens, watchers and streams are cleaned up with the runtime, and
recursive watch remains unsupported. Packaged watcher/fd/FileHandle/stream
close-on-destroy evidence and expanded-root reruns are still required before
the Desktop profile can relax this boundary.

The `websocket` bridge is the controlled AutoJs6 replacement for raw WebSocket sockets. JavaScript receives a Promise-based `connect()` API plus connection `send()`, `close()`, `on()`, `off()`, `addEventListener()`, and `removeEventListener()` methods with `onopen`/`onmessage`/`onclose`/`onerror` handler properties. Request descriptors are JSON-serialized, limited to `ws:` and `wss:`, bounded by `timeoutMs`, `maxMessageBytes`, `maxQueueSize`, and max active connection count, and may be cancelled through `AbortController.signal`. Binary frames cross the bridge as Base64. Event callbacks are drained through the bridge and must not call into JavaScript directly from OkHttp callbacks. The live Android provider is gated by `BuildConfig.NODEJS_NETWORK_EXPERIMENTAL_ENABLED`; normal builds reject before network I/O. Provider destroy closes tracked WebSocket sessions and must not call back into a destroyed Node environment.

The `work_manager` bridge is a controlled scheduled-task surface, not direct Android scheduler access. JavaScript can call `scheduleOnce()`, `schedulePeriodic()`, `cancel()`, and `list()` through `require("work_manager")`; descriptors are JSON-only, `backend` is restricted to `embedded`, entries must be scoped relative paths, non-packaged entries must resolve to files inside the working directory, packaged entries may serialize metadata without a plaintext file at registration time, and periodic intervals are bounded. The S7-15 live provider persists task records under app-private storage and enqueues AndroidX WorkManager one-shot or periodic requests. S7-21 revalidates the scoped entry when the worker fires, launches embedded Node only through `executionMode=scheduled` plus `launchSurface=scheduled_runner`, supports CJS/ESM entries, stores stable `lastRun`/`runHistory` metadata, and still never returns WorkManager retry. The scheduled runner cannot expose interactive UI or unrestricted long-running periodic Node process authority. S7-19 records Android 16 JobScheduler quota diagnostics for WorkManager-backed Node tasks, but still does not expose raw quota controls or scheduler objects. S11-22 records Rhino `tasks` migration as a design gate: `tasks.addDisposableTask()` may later map to `work_manager.scheduleOnce()` for Node-owned scoped entries, but `tasks.addDailyTask()`, `tasks.addWeeklyTask()`, `tasks.addIntentTask()`, raw `TimedTask`/`IntentTask` objects, task update mutation, and full user task database parity are not provided by `work_manager`. P12-15 locks the `compat.tasks` v1 scope to disposable one-shot wrappers only, P12-16 implements `addDisposableTask` over `work_manager.scheduleOnce`, `removeTask(id)` over `work_manager.cancel`, and `queryTasks(options?)` over `work_manager.list` with JSON-safe ids and snapshots only, and P12-17 adds Rhino-name aliases `queryTimedTasks()`, `getTimedTask(id)`, and `removeTimedTask(id)` for Node-owned one-shot snapshots only. P12-17 keeps daily/weekly user-task database provider access rejected with `ERR_AUTOJS6_RHINO_COMPAT_UNSUPPORTED` and keeps update mutation blocked with `ERR_AUTOJS6_RHINO_COMPAT_BLOCKED_BY_DESIGN`. P12-18 keeps `IntentTask` and broadcast-triggered task provider access rejected with `ERR_AUTOJS6_RHINO_COMPAT_UNSUPPORTED`; `addIntentTask()`, `queryIntentTasks()`, `getIntentTask(id)`, and `removeIntentTask(id)` fail before bridge dispatch. Queued host fallback rejects work-manager requests so packaged templates without live bridge metadata cannot create persistent tasks as one-way effects. Raw WorkManager, AlarmManager, JobScheduler, BroadcastReceiver, PendingIntent, Intent, Bundle, Uri, IntentFilter, exact-alarm, boot, package-event, custom broadcast authority, raw `TimedTask`, and raw `IntentTask` remain denied.

## Scheduled Background Task Policy

Node scripts have only the Task 65/180/S7-15/S7-21 `work_manager` controlled scheduling MVP plus the P12-16/P12-17/P12-18 `compat.tasks` wrapper over that MVP. `require("tasks")`, global `tasks`, and raw Rhino task objects remain unavailable. P12-17 rejection stubs for daily/weekly tasks and `updateTask()` must fail before bridge dispatch; P12-18 rejection stubs for IntentTask/broadcast tasks must also fail before bridge dispatch. For P13-22 v1.1, `compat.tasks.addIntentTask()`, `queryIntentTasks()`, `getIntentTask(id)`, and `removeIntentTask(id)` remain Promise-returning stable rejection stubs until a dedicated JSON-safe broadcast provider owns the future surface. Node scripts do not have authority to register broadcast, boot, package-event, exact-alarm, raw Android scheduler, interactive background UI, or arbitrary long-running background tasks.

P13-21 records daily/weekly/user task database promotion as partial. The existing Android/Rhino `TimedTask` and `IntentTask` database substrate is evidence only; Node still has no `tasks` capability, no direct task database provider, no packaged task database promotion, no wall-clock daily/weekly scheduler parity, and no raw `TimedTask`, `IntentTask`, WorkManager, PendingIntent, AlarmManager, JobScheduler, BroadcastReceiver, Intent, or cursor object access.

P13-22 records IntentTask/broadcast task provider promotion as partial. The existing Android/Rhino receiver stack remains substrate evidence only; Node still cannot register static or dynamic receivers, mutate packaged manifests, accept external broadcast wakeups, inject boot/package receivers, observe raw trigger payloads, or access raw `BroadcastReceiver`, `Intent`, `IntentFilter`, `PendingIntent`, `Bundle`, `Uri`, `PackageManager`, signature, icon, package-list, or receiver-handle objects.

P13-23 records bounded app/intent/broadcast Pro parity as ready. Node
`app.intent()` and `app.startActivity()` accept only JSON-safe descriptors,
exact installed-app lookups require `app.query`, and scoped file
`app.viewFile()` / `app.editFile()` Activity routing is covered in the focused
packaged app bridge smoke. Privileged Rhino options such as `root`, `shizuku`,
`dual`, `user`, and `userId` are rejected before bridge dispatch.
`app.sendBroadcast()`, raw Android `Uri`/`FileProvider` handles, raw installed
app lists, dual-user helpers, and root/Shizuku app tiers stay stable-denied for
P13-23 v1.1; future promotion belongs under the P13-22 broadcast provider or
P13-24 shell/root/Shizuku capability gates.

Future support must follow `docs/nodejs/design/WORK_MANAGER.md`:

- timed task execution may be added only through a Promise bridge provider
- the main app or packaged app owns prompts, persistence, receivers, and scheduler calls
- direct access to WorkManager, AlarmManager, JobScheduler, BroadcastReceiver, PendingIntent, and IntentFilter remains denied
- task descriptors must store Node backend, entry, project root, encrypted entry/module source ids, timeout, and JSON arguments explicitly
- persistent registration must be visible in task UI and must be cancellable by the user
- packaged APKs must opt in before boot/package receivers or exact-alarm permissions are injected

Boot, package-event, startup, and custom broadcast task behavior is design-gated separately in `docs/nodejs/design/BROADCAST_TASKS.md`.

Rhino `tasks` migration details, the daily/weekly/user-task-database boundary, and the IntentTask/broadcast boundary are tracked in `docs/nodejs/design/RHINO_TASKS_WORK_MANAGER_MIGRATION.md`, `docs/nodejs/design/TASKS_DAILY_WEEKLY_USER_DATABASE_DECISION.md`, and `docs/nodejs/design/TASKS_INTENT_BROADCAST_COMPATIBILITY_DECISION.md`.

Node scheduled runs must launch through the same lifecycle and scoped filesystem controls as manual Embedded Node runs.

## Packaged APK Policy

Packaged APKs are first-class targets. A feature is not release-ready if it works only inside the main AutoJs6 app.

Verification must cover:

- native libs per ABI
- x86 fail-fast behavior
- `NativeNodeEmbeddedScriptService` manifest entry
- service process name
- foreground service type
- foreground service permissions
- specialUse subtype property
- Dex class definitions
- encrypted script entry
- module source counts and byte budgets

## Error Policy

Security denials must be explicit and stable.

Required shape:

```js
{
  name: "Error",
  message: "...",
  code: "ERR_AUTOJS6_...",
  path: "...",
  syscall: "...",
  moduleId: "...",
  requireStack: ["..."]
}
```

Use the most specific code available. Avoid generic `Error` messages for security boundaries.

During the compatibility migration, `error.code` may remain a Node-style code such as `EPERM` or an existing legacy AutoJs6 code such as `ERR_AUTOJS6_EMBEDDED_NODE_BUILTIN_DISABLED`. New consumers should prefer `error.autojs6Code` when present. If a legacy code is preserved, `error.legacyCode` is also set.

P13-35 records process-worker replacement as partial. The only runtime change is
diagnostic metadata in `autojs6:profile.processWorkerReplacementProfile`; it
does not create a process-worker facade. The current replacement direction is
request-local `worker_threads.WorkerPool` after native preflight succeeds.
`child_process` remains denied, `shell` stays a separately permissioned scoped
command bridge, `worker_computation` stays reserved, and no raw process handle,
spawn/fork, stdio stream, signal, bridge-module, nested-worker, or packaged
process-worker authority is promoted. For P13-35 v1.1, there is no user-facing
process-worker API or UX; P13-40 remains the separate child_process
scoped-runner track.

P13-40 records child_process scoped runner as partial. `childProcessProfile` is
diagnostic metadata only: `child_process` and `node:child_process` remain
disabled builtins, and the existing `shell` bridge remains the separate
capability-scoped command path. A future scoped runner must prove app-private
allowlisted binaries, integrity checks, arbitrary-path denial, bounded
stdin/stdout/stderr, timeout, cancellation, kill, cleanup, packaged disclosure,
hostile corpus coverage, and rollback controls before any child-process-like
Desktop profile authority can be promoted.

P13-41 records ESM loader parity as partial. `esmLoaderProfile` is diagnostic
metadata for the existing AutoJs6-managed partial ESM loader; it does not grant
raw Node loader authority. `.mjs`, `type=module`, TLA, local static/dynamic
import, controlled JavaScript/JSON `data:` URL imports, package `exports` / `imports`, JSON import attributes, scoped
`import.meta`, ESM/CJS interop, and sync `require(esm)` remain constrained by
scoped filesystem, builtin allowlist, native-addon denial, and package target
policy. Raw loader hooks, raw `node:module` loader authority, network imports,
`file:` URL imports, unrestricted URL imports, absolute paths, working-directory escapes,
custom condition sets, import maps, and raw loader handles remain denied until
separate Desktop-profile evidence proves them safe.

P13-42 records Android package manager parity as partial.
`packageManagerProfile` is diagnostic metadata for the existing host-managed
package validation path and the Android app-private local store substrate; it
does not grant npm or registry authority to scripts. Android runtime package
operations are limited to already-unpacked package directories handled by
`NodeAndroidPackageInstallManager`, with SHA-512 integrity, transactions,
locking, cancellation, and verify/prune support. npm CLI execution, registry
download, lifecycle scripts, bin shim creation, symlink materialization, native
builds/native payloads, postinstall downloads, raw executable launch,
unrestricted `package_manager` operations, and raw `node_modules` mutation
remain denied until separate Desktop-profile evidence proves them safe.
`package_manager` itself is limited to guarded local mutation plus
read-maintenance and policy-only source planning: `status`/`policy`/
`sourcePolicy`, `planInstall`/`planUpdate`/`planRemove`,
`planRegistryInstall`/`planTarballInstall`, `install`/`update`/`remove`, and
`list`/`verify`/`prune`. Remote source plan calls return `blocked_by_policy`
without bridge dispatch, network/cache access, extraction, store mutation, or
install authority; registry plans can parse caller-provided npm
`metadata`/`packument` only as frozen dist-tag/semver/integrity diagnostics,
and tarball plans can parse URL/name/version/sha512 shape locally. `npm` is
script-visible only as a guarded package-manager alias facade; its CLI,
lifecycle, registry install, publish, and pack entry points reject with stable
policy errors. Android registry downloader, cache management, tarball
extraction, and integrity-lock install authority stay stable-denied as runtime
user APIs for P13-42 v1.1; host-managed validation remains the equivalent
install path.
Mutating calls require
`package_manager.mutate`, `allowMutation: true`, a project-scoped local
unpacked source where relevant, and expected SHA-512 integrity from a dry-run
plan.

P13-43 records Node stdlib conformance as partial. `stdlibProfile` is
diagnostic metadata only and does not grant raw desktop stdlib authority.
Representative Safe subsets are available for `crypto`, streams, zlib, Buffer,
URL, OS, readline, TTY, `perf_hooks`, and `async_hooks`. Controlled
`process.getBuiltinModule` returns the same limited wrappers as `require()`,
but raw `process.getBuiltinModule` bypass behavior, `zlib/promises`, raw native
handles, unrestricted network/OS surfaces, raw terminal control, and full
desktop parity remain denied until separate Desktop-profile evidence proves
them safe. The current stdlib npm corpus marker is
`real_npm_phase13_55_fixture_corpus`.

P13-44 records process/env/argv parity as partial. `processParityProfile` is
diagnostic metadata only and does not grant raw process authority. The Safe
subset keeps env script-local, string-coercing, and filtered, cwd scoped, maps
`process.exit(code)` to an embedded-script lifecycle result without native
process termination, denies signals, keeps raw process handles unavailable,
denies native bindings, and blocks `report.writeReport()` until separate
Desktop-profile and packaged lifecycle evidence proves them safe.

P13-45 records packaged APK capability metadata as partial.
`packagedCapabilityProfile` and `bridgePermissions.packagedMetadata` are
diagnostic metadata only. They must not grant profile selection, builtin
promotion, native asset loading, expanded filesystem roots, or unreviewed
Android permissions. Packaged bridge authority remains tied to declared
`node.permissions`, and undeclared capabilities reject before provider dispatch.

P13-46 records plugin system parity as partial. `pluginSystemProfile` is
diagnostic metadata only and does not grant install, update, native, Android
provider, marketplace, or packaged distribution authority. JS-only plugin
manifests remain constrained by the host project/package permission manifest:
exact `plugin.<id>` and every plugin-requested bridge capability must be
declared by the host, and missing capabilities reject before plugin code runs.

P13-47 records dependency compatibility corpus as partial. The real npm corpus
taxonomy records `missing_builtin`, `native_addon`, `postinstall`, `network`,
and `fs` package blocker reasons plus a stdlib `targetedFixtureDeltas` +5
pass delta without adding unsafe runnable payloads. Native addons, lifecycle
scripts, postinstall downloads, registry drift, and executable extraction
remain denied by dependency scanning and runtime policy; P13-47 only improves
reporting and pass-rate visibility by reason kind.

P13-36 records `vm` module promotion as partial. `vmProfile` is diagnostic
metadata and does not make a second JavaScript context a security boundary.
`vm` and `node:vm` resolve to Node's native builtin; `vm.Script`, context APIs,
`compileFunction`, VM module APIs, dynamic import hooks, and native timeout
options follow Node behavior. AutoJs6 does not inject bridge or raw Android
host objects into VM contexts, and disabled builtins remain governed by the
loader policy. P13-36 focused VM stress now covers repeated compiled scripts,
a 32 MiB heap-delta budget, native timeout interruption, VM
bridge/process/global non-injection probes, scoped filesystem preservation, and
packaged fixture smoke coverage; future work can broaden duration and corpus
size without treating VM as a security boundary.

P13-37 records inspector/devtools debug profile as partial.
`inspectorProfile` is diagnostic metadata only and does not open a debugger
transport. `inspector` and `node:inspector` remain denied; release, project,
and packaged metadata cannot enable them. Any future debug-only opt-in must
prove explicit user action, visible/revocable session state, loopback-only
binding, deterministic ADB forward cleanup, DevTools attach lifecycle,
CPU/heap artifact redaction, app-private export, and packaged release denial.

P13-38 records controlled WASI profile as partial. `wasiProfile` is diagnostic
metadata only and does not expose raw WASI imports or a controlled facade.
Pure WebAssembly remains available, but raw `wasi` and `node:wasi` remain
denied. For P13-38 v1.1, pure WebAssembly is the supported runtime path and
raw/controlled WASI APIs stay stable-denied. Any future controlled WASI opt-in
must prove scoped or
user-authorized preopens, realpath/symlink escape denial, virtual fd tables,
bounded stdio, explicit args, empty-by-default env, limited clock/random,
network and process-exit denial, resource budgets, hostile corpus coverage,
and packaged behavior.

P13-50 records relaxed-capability security corpus closeout. The structured
corpus covers raw network, filesystem relaxation, Java interop,
`child_process`, native addon loading, inspector, and WASI with positive
evidence, negative abuse probes, CI evidence, runtime guards, promotion
preconditions, and rollback posture. The focused
security regression keeps Safe-profile raw network modules, expanded filesystem roots,
raw `child_process`, native addons, inspector, raw WASI, and release Java
interop fail-closed while later rollback/privacy/disclosure gates are designed.

P13-51 records per-profile rollback playbook as partial. Profile switches
(`autojs.nodejs.profile.pro.enabled`,
`autojs.nodejs.profile.desktop.enabled`,
`autojs.nodejs.profile.debugUnsafeLab.enabled`) and the aggregate
`autojs.nodejs.relaxedCapabilities.enabled` switch default to false. Crash,
stress, security-corpus, and packaged regressions fall back to `safe_default`;
published packaged APK downgrade is documented as a Safe-profile patch or
metadata removal, not as a silent authority change. `raw_network` is listed as
the corpus-facing rollback id for raw Node network builtins.

P13-52 records privacy and disclosure policy as partial. The policy requires
user prompts before sensitive screenshot, recording, overlay, accessibility,
network, raw network, filesystem relaxation, Java interop, shell/root/Shizuku,
`child_process`, native addon, inspector, or WASI promotion; `raw_network`
uses the same id as the P13-50 corpus and P13-51 rollback policy. Screenshot,
recording, overlay, root shell, and inspector also require foreground
disclosure/notification ownership. Packaged APKs keep
`packagedImplicitHighPrivilegeDenied=true` and must not synthesize reviewed
high-privilege authority without a future disclosure manifest review path.

## Release Security Gate

Every Node release candidate must verify:

- dangerous process APIs denied
- dangerous builtins denied or limited as documented
- scoped fs escape tests pass
- fs stream escape tests pass and leave no active stream resources
- native addon loading denied
- ESM and dynamic import paths return stable policy errors
- network operations are denied unless explicitly feature-gated
- controlled fetch/WebSocket unsupported schemes reject before provider I/O
- UI/plugin/worker/shell v1.1 boundaries keep raw Android objects, native
  addons, worker bridge access or unavailable-worker fallback, and
  `child_process` unavailable
- zlib stream failures do not leave active stream resources
- direct Java/Android interop, reflection, class loaders, native library loading, and raw Android objects remain unavailable
- packaged APK verifier passes
- lifecycle timeout does not poison the next run
- lifecycle checkpoints cannot trigger automatic restart or escape their
  project-scoped app-private store
