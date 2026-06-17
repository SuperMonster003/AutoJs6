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

`module` and `node:module` are limited compatibility builtins, not loader authority. The safe subset exposes `createRequire`, `isBuiltin`, `builtinModules`, and a no-op `syncBuiltinESMExports` only. `createRequire` must stay scoped to a file inside the current working directory and must keep using the AutoJs6 resolver and builtin policy. Loader internals such as `Module`, `_load`, `_resolveFilename`, `_compile`, `wrap`, `globalPaths`, `register`, and `registerHooks` remain absent.

Runtime package installation is not a Safe Node Profile capability. Projects may vendor pure JavaScript CommonJS dependencies, and future tooling may sanitize them at build time, but AutoJs6 must not run package lifecycle scripts, native addon builds, arbitrary `.bin` commands, or registry downloads inside the Android runtime. See `docs/nodejs/design/PACKAGE_MANAGER.md`.
The host dependency bundler rejects unsafe dependency trees before packaging. `.node` files are reported as `NATIVE_ADDON_DISABLED`; `binding.gyp` and `build/{Release,Debug}` artifacts are reported as `NODE_GYP_ARTIFACT_UNSUPPORTED`. It also rejects lifecycle scripts, unchecked `.bin` entries, npm cache/log artifacts, suspicious executable/native binaries, oversized files/packages/trees, and package metadata path references that are absolute, protocol-based, NUL-containing, or escape through `..`. A sanitized bundle is copied only after the scan reports zero issues, and the dependency report includes an SBOM-lite file for packaged APK review.

`vm` remains disabled because a second JavaScript context is not a security boundary in this embedder. A limited `vm.Script` subset is deferred until timeout, global leakage, bridge leakage, and process object exposure can be proven safe.

Inspector remains disabled because it is a live debugger control channel, not a passive diagnostic API. Any future support must be debug-build-only, loopback-only, explicitly user-started, and closed through the embedded service lifecycle. See `docs/nodejs/design/INSPECTOR.md`.

Embedded Node execution is single-active per `:nodejs_embedded` process. Normal concurrent starts reject with `ERR_AUTOJS6_NODE_ENGINE_BUSY` instead of overlapping V8/libuv lifecycle ownership or silently queuing. A bounded execution queue exists only for explicit enqueue requests; queue full, timeout, and cancellation paths use stable `ERR_AUTOJS6_NODE_QUEUE_*` errors. See `docs/nodejs/design/MULTI_INSTANCE.md` and `docs/nodejs/design/EXECUTION_QUEUE.md`.

Long-running Node service mode remains a design-only future capability. It must not be inferred from pending timers, open network/WebSocket handles, or package metadata. Normal scripts remain one-shot and timeout-controlled until an explicit foreground-service descriptor, user stop controls, resource quotas, packaged APK checks, and crash restart policy are implemented. See `docs/nodejs/design/LONG_RUNNING_SERVICE.md`.

## Feature Flag Policy

`NodeRuntimeCapabilities.featureFlags` is the source of truth for build-only, debug-only, project-allowed, packaged-allowed, and release-forbidden Node feature flags. Current experimental flags are build-time only and debug-only; `project.json` and `package.json` metadata must not enable them. Explicit `node.featureFlags`, `node.experimentalFlags`, `autojs6.node.featureFlags`, and `autojs6.node.experimentalFlags` entries are audited and rejected when they name unknown flags or flags whose project/package override policy is false.

Release builds reject release-forbidden experimental flags even when a Gradle property is supplied. Use debug experimental builds for validation, then return release builds to the Safe Node Profile defaults.

## ESM Policy

ESM is partial and enabled by default in Node Profile v1.1.

The legacy ESM build switch is `autojs.nodejs.esm.experimental=true`. Dynamic import has its own local-only switch, `autojs.nodejs.dynamicImport.local=true`; the legacy broad experiment `autojs.nodejs.dynamicImport.experimental=false` remains release-forbidden. With ESM and local dynamic import enabled:

- `.mjs` routes to the Node backend and runs as partial ESM
- static `import`/`export` syntax is accepted only in ESM entries and ESM graph modules
- dynamic `import()` supports local relative modules, allowed builtins, and packages resolved inside `workingDirectory`
- `import.meta` is rejected
- package `type=module` can route `.js` entries to ESM

ESM v1.1 is limited to scoped static imports, simple exports, top-level await, CommonJS default import through the existing CommonJS loader, supported builtin imports through the Safe Node allowlist, and package `exports`/`imports` condition selection with the ESM condition set. ESM package targets may use `.mjs` only when they remain inside the package root and working directory. It must not bypass scoped filesystem, package target, builtin, native addon, network, or bridge policies. Disabled builtins remain disabled when imported from ESM or dynamic import. Dynamic import remains local-only: URL imports, network imports, absolute paths, path escapes, disabled builtins, and dynamic-code sources such as `eval` and `Function` remain denied. Loader hooks, raw Node loader authority from `node:module`, and CJS `require()` of ESM remain disabled; the CJS-to-ESM boundary fails with `ERR_AUTOJS6_REQUIRE_ESM_UNSUPPORTED`.

## Privacy-Safe Compatibility Modules

Some compatibility modules are present but intentionally redacted.

The `os` module must not expose raw device fingerprinting data. Network interfaces, CPU details, memory totals, load averages, hostname, and user information are synthetic or coarse values. This preserves compatibility for packages that feature-detect `os` without turning the embedded runtime into a host fingerprinting API.

The `crypto` module exposes bounded symmetric/hash helpers and a limited WebCrypto surface. It must not expose native Android keystore access, certificate inspection, asymmetric key APIs, native crypto handles, or unrestricted algorithm selection without a separate design gate.

## Network Policy

Network is not a default live I/O capability of the Safe Node Profile. The controlled fetch wrapper exists, but the explicit design-gate flag keeps the Android/OkHttp provider disabled in normal builds:

```properties
autojs.nodejs.network.experimental=false
```

Do not treat a present `http`, `https`, `net`, `tls`, `dns`, or fetch-shaped API as permission to perform I/O. The current `fetch` and `require("fetch")` APIs route through the AutoJs6 bridge and reject with `ERR_AUTOJS6_NETWORK_DISABLED` when the live provider is gated off. Raw network modules remain denied. The denial applies to bare imports, `node:` aliases, and `node_modules` shadow attempts.

Network enablement requires:

- Android permission policy
- packaged APK manifest policy
- timeout and abort support
- lifecycle cancellation on service destroy
- TLS and CA policy
- privacy review
- tests for denied and allowed paths

The AutoJs6-controlled `fetch` wrapper is the preferred network path over unrestricted socket modules. Userland HTTP clients must use this controlled path or another explicit AutoJs6 bridge adapter; desktop Node adapters based on `http`, `https`, `net`, `tls`, `dns`, or `dgram` are outside the Safe Node Profile. The wrapper supports request timeout, abort, response-size budgeting, redirect budgeting, concurrent-request limits, unsupported-scheme rejection, and provider-side cancellation. The Android provider tracks live OkHttp calls and cancels them on provider destroy. Raw network authority must remain denied unless a later design gate explicitly changes that boundary.

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

With the flag off, `require("worker_threads")` and `require("node:worker_threads")` reject with the disabled-builtin error shape. A local `node_modules/worker_threads` package must not shadow that denial.

With the experimental request flag on, workers are pure JavaScript compute workers with a smaller capability set than the parent script. Worker source is resolved and read by the parent scoped resolver, then executed through a worker-local restricted `require`. Worker scripts must not access:

- AutoJs bridge modules or `autojs6:bridge`
- Android `Context`
- accessibility, media projection, image, OCR, UI/dialog, shell, engines, clipboard, or device providers
- raw network modules
- native addons
- child process APIs
- `vm`
- inspector
- unrestricted filesystem APIs

Bridge ownership remains in the parent script. Workers may exchange pure data with the parent through `parentPort.postMessage`, `worker.postMessage`, and `workerData`; the worker itself may not call bridge modules. The MVP allows one active worker, uses a startup timeout, denies nested workers, tracks active workers per execution, and terminates active workers during parent success/failure cleanup. Packaged worker source graphs remain follow-up work.

See `docs/nodejs/design/WORKER_THREADS.md` for the current worker boundary.

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

## Java Interop Policy

Direct Java and Android interop is disabled in the default Safe Node Profile. Node scripts must use AutoJs bridge modules instead of raw `java.*`, `android.*`, Rhino `importClass`, `Packages`, `$autojs.java`, reflection, class loaders, or Android object references.

The `java_interop` feature flag is critical, disabled by default, build-time experimental only, and not overrideable by `project.json` or packaged APK metadata. The current experiment exposes only `require("java")` with a tiny allowlist for `java.lang.Math`, `java.util.UUID`, and opaque `android.graphics.Rect` handles. It must not expose `Context`, `Activity`, `Service`, `AccessibilityService`, `MediaProjection`, `Intent`, Binder, `ClassLoader`, reflection, filesystem, process, native-library, or raw UI objects.

See `docs/nodejs/design/JAVA_INTEROP.md` for the mode comparison, allowlist candidates, Rhino compatibility boundary, and future test gate.

Rhino compatibility shims are a separate migration concern. Node must not install Rhino globals, `importClass`, `Packages`, or `JavaAdapter` by default. See `docs/nodejs/design/RHINO_SHIM.md`.

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
- main/UI/IO dispatcher separation
- callbacks into JS marshalled onto the Node event loop thread

Bridge capability manifests are optional for legacy compatibility. When a Node project declares `node.permissions` in `project.json` or `autojs6.node.permissions` in `package.json`, the bridge becomes deny-by-default for that execution. Missing declarations reject in JavaScript before the request reaches the native or Android provider. The Android provider also audits every supported bridge request before dispatch: the request must carry the required capability names, and an enforced project manifest must declare them. Failures use `ERR_AUTOJS6_BRIDGE_CAPABILITY_NOT_DECLARED`.

Canonical capability names are `toast`, `app.launch`, `app.settings`, `app.activity`, `dialogs`, `engines`, `engines.exec`, `accessibility`, `screen_capture`, `image`, `ocr`, `clipboard`, `device`, `shell`, `shell.root`, `network`, `work_manager`, and `java_interop`. Declaring a parent such as `app` or `engines` grants its non-root child methods; `shell.root` must be declared explicitly. `media_projection` is accepted as an alias for `screen_capture`.

Declaring `network` is required for controlled `fetch`, `axios`, and `websocket` bridge dispatch when a capability manifest is enforced. Packaged builds map that capability to `android.permission.INTERNET`; doctor reports expose the mapping and raw Node network modules remain disabled.

Bridge limit policy is optional and safe by default. Projects may lower or safely raise limits through `node.bridgeLimits` in `project.json` or `autojs6.node.bridgeLimits` in `package.json`; values above the hard maximum are ignored and reported through `profile.bridgeLimits.warnings`. The current policy bounds pending bridge calls, JS image handles, live shell commands, controlled fetch calls, controlled WebSocket sessions, and accessibility requests per second. Resource exhaustion uses `ERR_AUTOJS6_BRIDGE_RESOURCE_LIMIT`; request-rate exhaustion uses `ERR_AUTOJS6_BRIDGE_RATE_LIMITED`.

Current `toast` and `app` support uses a private live transport in the main embedded service. The service injects per-execution bridge directories under its private cache, the JavaScript bootstrap writes JSON request files with an internal native filesystem helper, and the service writes JSON responses back for the pending Promise. User scripts are not given the bridge directory paths or raw Android objects.

When the live transport configuration is absent, such as in older packaged inrt templates, the bridge falls back to the queued host-effect envelope. In queued mode JavaScript only observes that the request was accepted; the Android dispatch result is diagnostic output after script completion.

Any provider-owned resource created through the bridge must be registered with the current execution id before JavaScript receives or depends on its handle. Registry records include an id, type, creation timestamp, owner execution id, metadata, and an idempotent close/recycle callback. On execution stop or service destroy, the provider closes all remaining registry entries, clears stale handle maps, and logs a leaked-resource summary through `embedded_script.bridge_resource.*` diagnostics. Current live registrations cover controlled fetch calls, controlled WebSocket sessions, shell executions, `work_manager` metadata tasks, and experimental Java object handles; image handles, screen capturers, dialogs, and workers must use the same registry and limit policy when their Android providers become live.

The `app` bridge exposes JSON-only intent descriptors and read-only package metadata. It does not expose raw Android `Intent`, `Context`, `PackageManager`, or `Activity` objects to JavaScript. Invalid intent descriptors must fail with a stable bridge policy error.

The `engines` bridge exposes only serialized engine and execution snapshots. It does not expose raw `ScriptEngine`, `ScriptExecution`, `ScriptRuntime`, thread, or process control objects. `execScript()` and `execScriptFile()` are routed through a non-exported main-process dispatch service so script startup still goes through AutoJs6's script manager rather than the isolated Node service process. Spawning requires an explicit `timeoutMs`, scoped `cwd`/path validation, JSON-primitive arguments, and a bounded spawn depth. Nested embedded Node child scripts are rejected until multi-instance embedded lifecycle ownership is designed. `execAutoFile` remains unsupported in the Safe Node Profile.

The `dialogs` bridge currently exposes a stable JavaScript Promise API and protocol surface, but the Android provider rejects until a UI capability provider is connected. This avoids accidentally showing service-context or overlay dialogs from the embedded Node process without an explicit Activity/UI ownership design.

The `accessibility` bridge currently exposes a stable JavaScript Promise API and protocol surface, including selector v1 descriptors. Builders such as `text(value)`, `desc(value)`, `id(value)`, `className(value)`, `textContains(value)`, and `descContains(value)` return frozen plain JSON descriptors; `findOne(selector)`, `findAll(selector)`, and `click(selector)` send those descriptors through the bridge. When an operational AutoJs6 accessibility service is visible in the current process, the Android provider returns booleans or frozen JSON node snapshots for controlled actions and queries. When the provider is unavailable in the current process, including isolated embedded service processes that cannot see the main-process service instance, `isEnabled()` and `ensureEnabled()` return `false` and actions/queries reject with `ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED`. No raw `AccessibilityService`, `AccessibilityNodeInfo`, `UiObject`, or live selector object crosses into Node. Node results must remain frozen JSON snapshots or opaque handles with explicit lifecycle cleanup.

The `media_projection` bridge currently exposes the JavaScript Promise API and opaque screen-capturer handle shape, but the Android provider rejects until an Activity-owned MediaProjection capability provider is connected. It must not request screen-capture permission from the embedded Node service process directly.

The `image` and `images` bridge modules currently expose opaque image-handle API shapes and fake-provider tests only, including `captureScreen`, scoped `readImage`/`saveImage`, `clip`, `resize`, `findImage`, and idempotent JavaScript-side `recycle`. The Android provider rejects until screenshot capture, image decoding, template matching, and handle cleanup are owned by a dedicated capability provider. `captureScreen()` must use an already-authorized MediaProjection provider path and must not request screen-capture permission from the embedded Node service process. JavaScript must never receive raw `Bitmap`, `Mat`, `ImageWrapper`, native pointer, or filesystem path handles outside the scoped policy.

The `ocr` bridge module exposes only `recognize(image, options?)` over existing opaque image handles. It requires both `ocr` and `image` capabilities, validates the image handle before dispatch, returns frozen JSON text-result snapshots, and currently rejects through the Android provider until a dedicated OCR provider is connected. JavaScript must not receive raw OCR engines, model paths, native pointers, `Bitmap`, `Mat`, `ImageWrapper`, or Java objects.

Image and node handles must be opaque ids with explicit lifecycle cleanup.

The `clipboard` bridge exposes only `getText()`, `setText(text)`, and `hasText()` Promise APIs. The Android live provider performs system clipboard access on the main thread and returns JSON strings or booleans through the bridge. Older packaged templates that lack live bridge metadata use a per-execution queued fallback string so packaged scripts can preserve Promise behavior without receiving background system clipboard access. The module must not expose raw `ClipboardManager`, `ClipData`, listeners, clipboard history, or background watchers to JavaScript.

The `device` bridge is privacy-safe by default. It exposes only coarse runtime/display properties (`sdkInt`, `width`, `height`, `density`) and Promise APIs for `isScreenOn()`, `wakeUp()`, and `vibrate(ms)`. The live provider uses Android `PowerManager` and `Vibrator` through the bridge and does not expose raw Android objects. Device identifiers and fingerprinting surfaces must remain absent, including IMEI, Android ID, serial, phone number, accounts, network MAC/IP, full hardware fingerprint, brand, and manufacturer. Packaged APKs using vibration must declare `android.permission.VIBRATE`.

The `shell` bridge is the controlled AutoJs6 replacement for Node `child_process`, not an unrestricted process-spawning API. The migration path is intentionally narrow: `child_process.exec()` maps to `require("shell").exec()` when the project declares the `shell` bridge capability; `spawn()`, `fork()`, streaming stdio, and interactive shells remain unsupported. It exposes only `exec(command, options?)` and returns serialized `{ code, stdout, stderr }` results. Commands run on a provider thread through a non-interactive shell; JavaScript never receives a `Process`, file descriptor, stream, signal handle, or raw Android shell object. Root mode is explicit through `options.root === true`, requires the separate `shell.root` capability when a manifest is enforced, and is never default. `cwd` is scoped to the execution working directory, `env` is JSON string-only, command output is bounded, and timeouts are enforced. The queued packaged fallback must not execute shell commands; `shell.exec()` requires live bridge metadata so command results and denials are observable before the Promise settles.

The `fetch` bridge is the controlled AutoJs6 replacement for Node raw network modules. JavaScript receives only a fetch-compatible Promise wrapper and buffered response helpers. Request descriptors are JSON-serialized, method-limited to `GET` and `POST`, bounded by `timeoutMs`, `maxResponseBytes`, `maxRedirects`, and max active request count, and may be cancelled through `AbortController.signal`. Only `http:` and `https:` URLs are accepted. The live Android provider is gated by `BuildConfig.NODEJS_NETWORK_EXPERIMENTAL_ENABLED`; normal builds reject before network I/O. Provider destroy cancels tracked OkHttp calls and must not call back into a destroyed Node environment.

The `websocket` bridge is the controlled AutoJs6 replacement for raw WebSocket sockets. JavaScript receives a Promise-based `connect()` API plus connection `send()`, `close()`, `on()`, `off()`, `addEventListener()`, and `removeEventListener()` methods with `onopen`/`onmessage`/`onclose`/`onerror` handler properties. Request descriptors are JSON-serialized, limited to `ws:` and `wss:`, bounded by `timeoutMs`, `maxMessageBytes`, `maxQueueSize`, and max active connection count, and may be cancelled through `AbortController.signal`. Binary frames cross the bridge as Base64. Event callbacks are drained through the bridge and must not call into JavaScript directly from OkHttp callbacks. The live Android provider is gated by `BuildConfig.NODEJS_NETWORK_EXPERIMENTAL_ENABLED`; normal builds reject before network I/O. Provider destroy closes tracked WebSocket sessions and must not call back into a destroyed Node environment.

The `work_manager` bridge is a controlled scheduled-task metadata surface, not direct Android scheduler access. JavaScript can call `scheduleOnce()`, `schedulePeriodic()`, `cancel()`, and `list()` through `require("work_manager")`; descriptors are JSON-only, `backend` is restricted to `embedded`, entries must be scoped relative paths, and periodic intervals are bounded. The current MVP stores provider-owned metadata and does not launch background Node executions. Queued host fallback rejects work-manager requests so packaged templates without live bridge metadata cannot create persistent tasks as one-way effects. Raw WorkManager, AlarmManager, JobScheduler, BroadcastReceiver, PendingIntent, IntentFilter, exact-alarm, boot, package-event, and custom broadcast authority remain denied.

## Scheduled Background Task Policy

Node scripts have only the Task 65 `work_manager` metadata-registration MVP. They do not yet have authority to execute persistent timed, broadcast, boot, or package-event tasks.

Future support must follow `docs/nodejs/design/WORK_MANAGER.md`:

- timed task execution may be added only through a Promise bridge provider
- the main app or packaged app owns prompts, persistence, receivers, and scheduler calls
- direct access to WorkManager, AlarmManager, JobScheduler, BroadcastReceiver, PendingIntent, and IntentFilter remains denied
- task descriptors must store Node backend, entry, project root, encrypted entry/module source ids, timeout, and JSON arguments explicitly
- persistent registration must be visible in task UI and must be cancellable by the user
- packaged APKs must opt in before boot/package receivers or exact-alarm permissions are injected

Boot, package-event, startup, and custom broadcast task behavior is design-gated separately in `docs/nodejs/design/BROADCAST_TASKS.md`.

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

## Release Security Gate

Every Node release candidate must verify:

- dangerous process APIs denied
- dangerous builtins denied or limited as documented
- scoped fs escape tests pass
- native addon loading denied
- ESM and dynamic import paths return stable policy errors
- network operations are denied unless explicitly feature-gated
- direct Java/Android interop, reflection, class loaders, native library loading, and raw Android objects remain unavailable
- packaged APK verifier passes
- lifecycle timeout does not poison the next run
