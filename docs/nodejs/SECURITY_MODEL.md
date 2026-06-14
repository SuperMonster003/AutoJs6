# AutoJs6 Embedded Node.js Security Model

The embedded Node runtime is an Android automation runtime, not a desktop shell. The security model is based on process isolation, scoped filesystem access, builtin restrictions, packaged APK verification, and explicit feature gates.

## Trust Boundary

User scripts are untrusted relative to the AutoJs6 app process and Android privileged services.

The embedded runtime may execute user-provided JavaScript, npm-style CommonJS packages, and packaged APK assets. It must not receive raw access to:

- Android `Context` with broad privileges
- `AccessibilityService` instances
- `MediaProjection` instances
- `Activity` or view objects
- Binder objects
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

If a limited module exists for npm feature detection, operations that would cross the security boundary must fail with a stable policy error.

## ESM Policy

ESM is design-gated and disabled by default.

The build flag is `autojs.nodejs.esm.experimental=false`. With the flag off:

- `.mjs` routes to the Node backend but fails before execution
- static `import`/`export` syntax is rejected
- dynamic `import()` is rejected
- `import.meta` is rejected
- package `type=module` remains diagnostic-only for current CommonJS loading

Future ESM support must follow `docs/nodejs/design/ESM.md` and must not bypass scoped filesystem, package target, builtin, native addon, network, or bridge policies.

## Privacy-Safe Compatibility Modules

Some compatibility modules are present but intentionally redacted.

The `os` module must not expose raw device fingerprinting data. Network interfaces, CPU details, memory totals, load averages, hostname, and user information are synthetic or coarse values. This preserves compatibility for packages that feature-detect `os` without turning the embedded runtime into a host fingerprinting API.

The `crypto` module exposes bounded symmetric/hash helpers and a limited WebCrypto surface. It must not expose native Android keystore access, certificate inspection, asymmetric key APIs, native crypto handles, or unrestricted algorithm selection without a separate design gate.

## Network Policy

Network is not a default live I/O capability of the Safe Node Profile. The controlled fetch wrapper exists, but the explicit design-gate flag keeps the Android/OkHttp provider disabled in normal builds:

```properties
autojs.nodejs.network.experimental=false
```

Do not treat a present `http`, `https`, `net`, `tls`, `dns`, or fetch-shaped API as permission to perform I/O. The current `fetch` and `require("fetch")` APIs route through the AutoJs6 bridge and reject with `ERR_AUTOJS6_NETWORK_DISABLED` when the live provider is gated off. Raw network modules remain denied.

Network enablement requires:

- Android permission policy
- packaged APK manifest policy
- timeout and abort support
- lifecycle cancellation on service destroy
- TLS and CA policy
- privacy review
- tests for denied and allowed paths

The AutoJs6-controlled `fetch` wrapper is the preferred network path over unrestricted socket modules. It supports request timeout, abort, response-size budgeting, and provider-side cancellation. The Android provider tracks live OkHttp calls and cancels them on provider destroy. Raw `http`, `https`, `net`, `tls`, `dns`, and `dgram` authority must remain denied unless a later design gate explicitly changes that boundary.

See `docs/nodejs/design/NETWORK.md` for the current network design gate.

## Worker Threads Policy

`worker_threads` is not enabled in the default Safe Node Profile. The explicit design-gate flag is:

```properties
autojs.nodejs.worker_threads.experimental=false
```

With the flag off, `require("worker_threads")` and `require("node:worker_threads")` reject with the disabled-builtin error shape. A local `node_modules/worker_threads` package must not shadow that denial.

Future workers, if enabled experimentally, must be treated as pure JavaScript compute workers. They must not access:

- AutoJs bridge modules or `autojs6:bridge`
- Android `Context`
- accessibility, media projection, image, UI/dialog, shell, engines, clipboard, or device providers
- raw network modules
- native addons
- child process APIs
- `vm`
- inspector
- unrestricted filesystem APIs

Bridge ownership remains in the parent script. Workers may exchange pure data with the parent only after lifecycle, timeout, memory, and package-source policies are implemented.

See `docs/nodejs/design/WORKER_THREADS.md` for the current worker boundary.

## Native Code Policy

Native addons are disabled by default.

Reasons:

- they execute arbitrary native code in the embedded process
- they bypass JavaScript-level policy
- ABI distribution is complex
- packaged APK injection is complex
- they can bypass scoped filesystem and builtin restrictions

Future support, if any, should be developer-mode only and separately gated.

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
- malformed request errors with `ERR_AUTOJS6_BRIDGE_INVALID_REQUEST`
- a centralized module/method registry for provider routing and diagnostics
- main/UI/IO dispatcher separation
- callbacks into JS marshalled onto the Node event loop thread

Current `toast` and `app` support uses a private live transport in the main embedded service. The service injects per-execution bridge directories under its private cache, the JavaScript bootstrap writes JSON request files with an internal native filesystem helper, and the service writes JSON responses back for the pending Promise. User scripts are not given the bridge directory paths or raw Android objects.

When the live transport configuration is absent, such as in older packaged inrt templates, the bridge falls back to the queued host-effect envelope. In queued mode JavaScript only observes that the request was accepted; the Android dispatch result is diagnostic output after script completion.

The `app` bridge exposes JSON-only intent descriptors and read-only package metadata. It does not expose raw Android `Intent`, `Context`, `PackageManager`, or `Activity` objects to JavaScript. Invalid intent descriptors must fail with a stable bridge policy error.

The `engines` bridge exposes only serialized engine and execution snapshots. It does not expose raw `ScriptEngine`, `ScriptExecution`, `ScriptRuntime`, thread, or process control objects. `execScript()` and `execScriptFile()` are routed through a non-exported main-process dispatch service so script startup still goes through AutoJs6's script manager rather than the isolated Node service process. Spawning requires an explicit `timeoutMs`, scoped `cwd`/path validation, JSON-primitive arguments, and a bounded spawn depth. Nested embedded Node child scripts are rejected until multi-instance embedded lifecycle ownership is designed. `execAutoFile` remains unsupported in the Safe Node Profile.

The `dialogs` bridge currently exposes a stable JavaScript Promise API and protocol surface, but the Android provider rejects until a UI capability provider is connected. This avoids accidentally showing service-context or overlay dialogs from the embedded Node process without an explicit Activity/UI ownership design.

The `accessibility` bridge currently exposes a stable JavaScript Promise API and protocol surface, including selector v1 descriptors. Builders such as `text(value)`, `desc(value)`, `id(value)`, `className(value)`, `textContains(value)`, and `descContains(value)` return frozen plain JSON descriptors; `findOne(selector)`, `findAll(selector)`, and `click(selector)` send those descriptors through the bridge. The Android provider is still limited to the disabled path: `isEnabled()` and `ensureEnabled()` return `false`; actions and queries reject with `ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED` until a safe capability provider is connected. No raw `AccessibilityService`, `AccessibilityNodeInfo`, `UiObject`, or live selector object crosses into Node. Node results must remain frozen JSON snapshots or opaque handles with explicit lifecycle cleanup.

The `media_projection` bridge currently exposes the JavaScript Promise API and opaque screen-capturer handle shape, but the Android provider rejects until an Activity-owned MediaProjection capability provider is connected. It must not request screen-capture permission from the embedded Node service process directly.

The `image` and `images` bridge modules currently expose opaque image-handle API shapes and fake-provider tests only, including `captureScreen`, scoped `readImage`/`saveImage`, `clip`, `resize`, `findImage`, and idempotent JavaScript-side `recycle`. The Android provider rejects until screenshot capture, image decoding, template matching, and handle cleanup are owned by a dedicated capability provider. `captureScreen()` must use an already-authorized MediaProjection provider path and must not request screen-capture permission from the embedded Node service process. JavaScript must never receive raw `Bitmap`, `Mat`, `ImageWrapper`, native pointer, or filesystem path handles outside the scoped policy.

Image and node handles must be opaque ids with explicit lifecycle cleanup.

The `clipboard` bridge exposes only `getText()`, `setText(text)`, and `hasText()` Promise APIs. The Android live provider performs system clipboard access on the main thread and returns JSON strings or booleans through the bridge. Older packaged templates that lack live bridge metadata use a per-execution queued fallback string so packaged scripts can preserve Promise behavior without receiving background system clipboard access. The module must not expose raw `ClipboardManager`, `ClipData`, listeners, clipboard history, or background watchers to JavaScript.

The `device` bridge is privacy-safe by default. It exposes only coarse runtime/display properties (`sdkInt`, `width`, `height`, `density`) and Promise APIs for `isScreenOn()`, `wakeUp()`, and `vibrate(ms)`. The live provider uses Android `PowerManager` and `Vibrator` through the bridge and does not expose raw Android objects. Device identifiers and fingerprinting surfaces must remain absent, including IMEI, Android ID, serial, phone number, accounts, network MAC/IP, full hardware fingerprint, brand, and manufacturer. Packaged APKs using vibration must declare `android.permission.VIBRATE`.

The `shell` bridge is the controlled AutoJs6 replacement for Node `child_process`, not an unrestricted process-spawning API. It exposes only `exec(command, options?)` and returns serialized `{ code, stdout, stderr }` results. Commands run on a provider thread through a non-interactive shell; JavaScript never receives a `Process`, file descriptor, stream, signal handle, or raw Android shell object. Root mode is explicit through `options.root === true`, never default. `cwd` is scoped to the execution working directory, `env` is JSON string-only, command output is bounded, and timeouts are enforced. The queued packaged fallback must not execute shell commands; `shell.exec()` requires live bridge metadata so command results and denials are observable before the Promise settles.

The `fetch` bridge is the controlled AutoJs6 replacement for Node raw network modules. JavaScript receives only a fetch-compatible Promise wrapper and buffered response helpers. Request descriptors are JSON-serialized, method-limited to `GET` and `POST`, bounded by `timeoutMs` and `maxResponseBytes`, and may be cancelled through `AbortController.signal`. The live Android provider is gated by `BuildConfig.NODEJS_NETWORK_EXPERIMENTAL_ENABLED`; normal builds reject before network I/O. Provider destroy cancels tracked OkHttp calls and must not call back into a destroyed Node environment.

## Scheduled Background Task Policy

Node scripts do not currently have authority to register persistent timed, broadcast, boot, or package-event tasks.

Future support must follow `docs/nodejs/design/WORK_MANAGER.md`:

- timed task registration may be added only through a Promise bridge provider
- the main app or packaged app owns prompts, persistence, receivers, and scheduler calls
- direct access to WorkManager, AlarmManager, JobScheduler, BroadcastReceiver, PendingIntent, and IntentFilter remains denied
- task descriptors must store Node backend, entry, project root, encrypted entry/module source ids, timeout, and JSON arguments explicitly
- persistent registration must be visible in task UI and must be cancellable by the user
- packaged APKs must opt in before boot/package receivers or exact-alarm permissions are injected

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
- packaged APK verifier passes
- lifecycle timeout does not poison the next run
