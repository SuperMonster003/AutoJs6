# Node Profile v1.1 Scope

Target branch: `feature/nodejs-runtime-v1.1`

Node Profile v1.1 is a capability expansion over the v1.0 Safe Node
Profile. It keeps the same sandbox, builtin allowlist, bridge capability
manifest, backend switches, and packaged APK requirements while promoting a
small set of already-gated features toward practical use.

## Goals

- ESM partial support for `.mjs` entries and `package.json` `type=module`
  entry routing.
- Safe ESM builtin imports through the existing builtin allowlist.
- Practical ESM/CommonJS interop, including CommonJS `require()` of synchronous
  partial ESM graphs while keeping asynchronous ESM graphs rejected with a
  stable error.
- Package `exports` and `imports` condition selection for both CommonJS and
  ESM importers.
- Controlled fetch and WebSocket remain on the v1.1 roadmap, but are not part
  of the first ESM promotion batch.
- NPM compatibility expansion continues to focus on pure JavaScript packages.
- AutoJs bridge expansion continues through capability-gated modules.
- Long-running script support remains an explicit interactive foreground mode,
  not accidental behavior from timers, sockets, workers, or bridge resources.

## Non-goals

- Full desktop Node.js compatibility.
- Native addon `.node` loading.
- Unrestricted `child_process`.
- Raw `net`, `tls`, `dns`, `dgram`, `http`, or `https` I/O.
- Unrestricted Java or Android object interop.
- Full inspector/debugger server support.
- Raw Node loader hooks, custom ESM loaders, or unrestricted `node:module`
  authority.
- Relaxing scoped filesystem policy.
- Removing Rhino fallback or the `rhino`, `native`, `embedded`, and `auto`
  backend switches.
- Removing the native one-shot oracle.

## Feature Status Targets

| Feature | v1.1 target | Notes |
| --- | --- | --- |
| CommonJS/npm subset | stable | Existing v1.0 contract remains first-class. |
| Real npm corpus | partial | v1.1 expands the fixed offline corpus from 7 to 13 fixtures with ESM-only dependency, hybrid CJS/ESM, Buffer, fetch wrapper fallback, assertion helper, `AsyncLocalStorage`, `stream/promises`, WebCrypto, worker CPU fallback, test-runner, and database-abstraction coverage while retaining no-install/no-native safety checks. |
| Scoped fs | stable | No policy loosening. |
| ESM entry | partial | `.mjs` and `type=module` entries run by default in v1.1. |
| ESM builtin import | partial | Only allowed builtins and limited wrappers. |
| ESM/CJS interop | partial | ESM may import CJS; CJS `require()` may load synchronous ESM graphs and rejects async graphs with `ERR_REQUIRE_ASYNC_MODULE`. |
| ESM JSON import attributes | partial | Static `import data from "./data.json" with { type: "json" }` and dynamic `import("./data.json", { with: { type: "json" } })` load scoped JSON through the existing CommonJS JSON cache; missing/unknown attributes and legacy assertions are denied. |
| ESM import.meta | partial | `import.meta.url`, `filename`, `dirname`, `main`, and `resolve()` are available for AutoJs6-managed ESM modules. `resolve()` reuses the scoped ESM resolver, returns `file:` URLs for local/package targets and `node:` URLs for allowed builtins, and denies disabled builtins. |
| Package ESM conditions | partial | Condition objects preserve package key order. ESM activates `autojs6`, `import`, `node`, `default`; CJS activates `autojs6`, `require`, `node`, `default`; `development` and `production` remain inactive unless explicitly enabled by a future profile. |
| Dynamic import | partial | Local-only dynamic import is enabled by default for relative modules, allowed builtins, and scoped packages. |
| Packaged ESM | partial | Packaged `.mjs` entry plus local `.mjs`/CommonJS graph smoke is required; dynamic import local graph smoke is covered separately. |
| Buffer module | partial | `require("buffer")`, `require("node:buffer")`, and ESM `node:buffer` use the same builtin wrapper and cannot be shadowed by `node_modules/buffer`. |
| Core builtin feature-detect modules | partial | `constants`, `util/types`, `events`, and `diagnostics_channel` support CJS and partial ESM import paths with shadowing denial and no `process.getBuiltinModule()` bypass. |
| Stream primitives | partial | Async iteration, callback/promise pipeline and finished helpers, Web Streams adapters, backpressure smoke, destroy propagation, and shadowing denial stay covered as pure-JS compatibility surface. |
| Web Streams | partial | `stream/web`, globals, Blob streams, Node/Web adapters, and bounded fetch `response.body.stream()` reads are supported without direct network streaming. |
| Web Platform globals | partial | `Headers`, `Request`, `Response`, `FormData`, `Blob`, `File`, `URLPattern`, `DOMException`, `EventTarget`, `MessageEvent`, `CompressionStream`, and `DecompressionStream` are aligned by a focused S8-20 gate as a compatibility prelude to controlled HTTP client facades. |
| fs streams | partial | Scoped read/write streams support read, write, pipe, stable denial paths, active-stream diagnostics, timeout/service cleanup, and packaged smoke coverage. |
| FileHandle advanced scoped fs | partial | `fs.promises.open()` returns execution-owned scoped handles with read/write, `readFile`, `writeFile`, `appendFile`, `truncate`, `stat`, `datasync`, `sync`, `close`, promise-level handle helpers, closed-handle `EBADF`, scoped path denial, and timeout recovery. |
| Callback/sync fd APIs | partial | `fs.open`/`openSync`, `close`/`closeSync`, `read`/`readSync`, `write`/`writeSync`, `fstat`/`fstatSync`, and `ftruncate`/`ftruncateSync` operate only on AutoJs6-managed scoped descriptors, with path denial, closed/non-owned fd `EBADF`, and timeout recovery. |
| opendir / Dir / Dirent | partial | `fs.opendir`, `fs.opendirSync`, and `fs.promises.opendir` operate only on scoped directories, with callback/sync/promise reads, async iteration, `Dirent` snapshots, buffer names, bounded recursive walks, closed `Dir` `ERR_DIR_CLOSED`, and path denial. |
| fs watchers | partial | `fs.watch`, `fs.promises.watch`, `fs.watchFile`, and `fs.unwatchFile` operate only on scoped paths, with AbortSignal, async iterator, polling stats, watcher quota, event-rate limiting, timeout cleanup, recursive watch denial, and documented Android event coalescing. |
| Safe file operations | partial | `fs.cp`/`cpSync`, `fs.mkdtemp`/`mkdtempSync`, `fs.statfs`/`statfsSync`, and constrained `glob` callback/sync/promise forms operate only inside the scoped filesystem; escapes, absolute outside paths, external symlinks, device roots, async `cp` filters, and unsafe glob forms remain denied. |
| zlib streams | partial | Bounded transform streams support gzip/gunzip and deflate/inflate pipelines, scoped fs pipeline smoke, invalid input errors, active-stream diagnostics, timeout/service cleanup, and packaged smoke coverage. |
| Controlled fetch | experimental | Global `fetch` and `require("fetch")` surface is hardened for v1.1 with fake-provider GET/POST, headers, string/binary bodies, response helpers, abort, timeout, budgets, redirects, and raw-network denial; live provider remains default-off. |
| HTTP client facade | experimental controlled client | `http.request()`, `http.get()`, `https.request()`, and `https.get()` expose a controlled fetch-backed client facade only when an enforced manifest declares `network`; default sync denial, no server/listen/raw sockets/CONNECT/upgrades, and explicit-only redirect follow remain enforced. |
| HTTPS client facade policy | experimental controlled client | HTTPS uses the same controlled client facade while keeping certificate validation provider-owned; `rejectUnauthorized=false`, custom TLS/OpenSSL context, client certificate fields, request-level `createConnection`, and unsafe agent TLS options are rejected, with pinning/client certificates left design-gated. |
| HTTP stream semantics | experimental controlled client | `ClientRequest` and `IncomingMessage` stream compatibility now has focused coverage for writable request chunks, readable `data`/`end`/`close`, `pipe()` backpressure smoke, request-level `AbortSignal`, `maxResponseBytes`, service-destroy cancellation, and pending cleanup without exposing raw sockets. |
| HTTP Agent facade | experimental controlled client | `http.Agent` and `https.Agent` expose only a limited compatibility shape for safe option fields, `getName()`, and `destroy()`; custom `createConnection`, `addRequest`, and `socketPath` paths reject without exposing raw socket pools. |
| Undici facade | experimental controlled client | `require("undici")` is reserved and exposes controlled-fetch-backed `fetch()` / `request()`, Web Platform constructors, and shape-only `Dispatcher` / `Agent` / `MockAgent` helpers while denying raw connectors, dispatcher injection, socket/proxy/tunnel options, upgrades, streams, and local shadowing. |
| HTTP npm corpus | compatibility evidence | Six offline npm-shaped fixtures cover packages using `http.get()`, `https.request()`, `IncomingMessage` stream consumption, form-style POST bodies, request-level `AbortSignal`, and safe Agent options through the controlled client facade. |
| Controlled axios | experimental | `require("axios")` exposes an AutoJs6-owned fetch-backed compatibility adapter for `get`/`post`/`request`, `baseURL`, params, headers, timeout, response types, and Axios-like errors while denying custom adapter/agent/proxy/raw transport paths. |
| WebSocket | experimental | Controlled bridge surface covers connect, send/receive text and binary messages, close/error events, handler properties, listener aliases, bounded queue/message policy, service-destroy cleanup, and raw socket denial; live provider remains default-off. |
| Image advanced operations | partial | `require("image")` and `require("images")` expand opaque-handle workflows with grayscale, threshold, template matching, color search, local size snapshots, and save format overloads while keeping Android provider ownership fail-closed except for scoped `readImage()`/`recycle()` used by OCR. |
| OCR | partial | `require("ocr")` exposes `recognize`, `recognizeText`, and `detectTextBounds` over opaque image handles with `ocr` + `image` capability checks; Android now has a live MLKit provider over scoped `image.readImage()` handles, while packaged model policy and broader image-provider ownership remain gated. |
| Storage | partial | `require("storage")` and `require("storages")` expose JSON-only package/cwd-scoped key-value stores with explicit `storage` capability checks, per-value and per-store quotas, and packaged-app isolation. |
| Database / SQLite bridge | partial | `require("database")` and `require("sqlite")` expose an AutoJs6-owned SQLite bridge for `open`, `exec`, `run`, `get`, `all`, `transaction`, and `close` with explicit `database` capability checks. Files are app-private and scoped by package identity, working directory, and database name; `node:sqlite`, extension loading, path escapes, and `ATTACH`/`DETACH` remain denied. |
| Notifications | partial | `require("notifications")` exposes post-only `notify`, `cancel`, and `cancelAll` with explicit `notifications` capability checks, Android `POST_NOTIFICATIONS` permission mapping, stable channel IDs, ongoing ownership metadata, and owned-resource cleanup. |
| Sensors | partial | `require("sensors")` exposes Android-backed `getAvailableSensors`, `once`, and bounded callback subscriptions for `accelerometer`, `gyroscope`, and `light` with explicit `sensors` capability checks, sampling clamps, max subscription limits, and destroy cleanup. |
| UI bridge | Activity-owned live MVP | `require("ui")` exposes JSON-only `showLayout`, opaque `UiHandle.on/update/batchUpdate/close`, `state`/`bind` one-way binding, drained event snapshots, Button/Input/List/Switch/Checkbox/Tabs event delivery, Progress batch updates, WebView gate denial, and provider-destroy cleanup with explicit `ui` capability checks; Android views remain Activity-owned and are never exposed to Node. |
| Accessibility selector v2 | partial | `require("accessibility")` expands selector descriptors with regex, clickable/enabled, depth, and bounds fields plus selector actions for click, long-click, set-text, and scroll while keeping JSON-only snapshots, explicit `accessibility` capability checks, and an optional live controlled-Activity smoke when the service is pre-enabled. |
| Shell controls | partial | `require("shell")` remains the only child_process alternative and adds `execFile`, `checkAccess`, and `setDefaultTimeout` over the same controlled `exec` bridge while keeping Shizuku unsupported. |
| Work Manager scheduling | partial | `require("work_manager")` registers controlled one-shot and bounded periodic scheduled work for `scheduleOnce`, `schedulePeriodic`, `cancel`, and `list`, stores embedded backend plus `scriptPath`/project `entry` metadata, supports packaged entry descriptors, records Android 16 quota diagnostics, and launches scoped CJS/ESM embedded Node work through AndroidX WorkManager's dedicated `scheduled_runner` surface without exposing raw scheduler authority. Task records expose `lastRun` and bounded `runHistory`. |
| Plugin system | JS-only lifecycle manager | `require("plugins")` registers and loads project-local `./plugins/<id>` CommonJS plugins with `autojs6.plugin.permissions`, dependency declarations, named JS module exports, in-execution enable/disable/config/version/status/capabilities/start/stop/unload/list/reload, exact `plugin.<id>` project opt-in, path-rooted namespace metadata, plugin-owned timer/provider/UI/socket/image resource cleanup on stop, debug-only reload, `.node` rejection, and plugin entry path escape denial; packaged APK support is limited to already-packaged project-local JS-only plugins, while download installers, signatures, native plugins, Android plugin adapters, update channels, and marketplace/runtime distribution stay gated. |
| Developer REPL | developer-only partial | `NodeDeveloperRepl` provides a debug-build-only programmatic line-eval session with an isolated temporary `workingDirectory`, normal Safe Node builtin policy, default bridge deny-by-default manifest, and cleanup on close. |
| Project wizard / validator | developer tooling partial | `tools/nodejs/project/autojs6-node-project.js` generates CommonJS, ESM, automation, screenshot/OCR, and network starter projects, then validates project metadata, entry files, permissions, module type, and sanitized dependency bundles with actionable error codes. |
| Execution queue policy | partial | Embedded Node remains one active execution per `:nodejs_embedded` process; explicit caller-side enqueue supports one queued execution, queue status, cancellation, queue timeout, and stable busy/full/timeout/cancelled errors. |
| Embedded process pool | policy gate, primary-only | S8-43 declares three non-exported embedded script service slots and publishes `embedded_script.process_pool.*` diagnostics, but the effective runtime remains `primary_only` with one active slot. Reserved long-running and overflow slots are not selectable until a future pool coordinator and lifecycle stress gate are promoted. |
| Execution mode state machine | partial | Request protocol carries `executionMode`; Phase 7 canonicalizes `one_shot`, `interactive_long_running`, `scheduled`, and `worker_computation`, and S8-41 adds design-gated `node_sandboxed`. Normal script launches execute only `one_shot`. `interactive_long_running` executes only through the explicit interactive session launch surface with persistent notification, Stop action, status/duration/resource summary, runtime heartbeat/budget diagnostics, cooperative `autojs6:lifecycle` checkpoint/explicit-restore hooks, Android foreground-service timeout cleanup, and restart policy `never`; `scheduled` executes only through the WorkManager-owned `scheduled_runner` launch surface. Direct script requests for non-`one_shot` modes, `worker_computation`, and `node_sandboxed` reject before user code with policy metadata. |
| Hardened isolated runtime | design-gated POC | `NativeNodeSandboxedScriptService` is declared as a non-exported Android `isolatedProcess` service and `nodeSandboxed` normalizes to `node_sandboxed`, but no launch surface executes it until the Binder capability broker gate exists. |
| Worker threads | blocked | Disabled by default. Task 170 rechecked the pure-JS worker target, but the current Embedded Node build still lacks native `worker_threads`; no runnable v1.1 partial support is advertised. S7-24 adds a bootstrap/native-availability preflight so the focused worker gate verifies default denial and stable `ERR_AUTOJS6_WORKER_DISABLED` without a same-thread fallback. |
| Native addon | unsupported | Must remain disabled. |
| Child process | disabled | Use controlled `shell` bridge instead. |

## Success Criteria

- `.mjs` entry smoke passes without a per-test ESM opt-in.
- Fixed real npm corpus increases from the v1.0 CommonJS-only baseline to 13 fixtures and
  produces `build/reports/nodejs/npm-compat-v1.1.md` and `build/reports/nodejs/v1.1-differential.md`.
- Package `type=module` entry smoke passes.
- Local static import, named export, default export, and top-level await pass.
- ESM imports of allowed builtins pass.
- ESM import of disabled builtins fails with stable AutoJs6 error metadata.
- ESM package `exports`/`imports` use active conditions
  `autojs6`/`import`/`node`/`default` while preserving package key order.
- CJS package `exports`/`imports` use active conditions
  `autojs6`/`require`/`node`/`default` while preserving package key order.
- CommonJS `require()` of synchronous `.mjs`/`.mts`/`type=module` targets returns
  an ESM namespace object while preserving CommonJS package condition matching.
- CommonJS `require()` of ESM graphs containing `await` or dynamic `import()`
  fails with `ERR_REQUIRE_ASYNC_MODULE`.
- ESM JSON imports require `with { type: "json" }`, share the CommonJS JSON
  cache object, and reject legacy assertions or unknown attributes.
- ESM `import.meta` exposes scoped metadata, resolves local/package/allowed
  builtin specifiers, and rejects disabled builtins with stable AutoJs6 error
  metadata.
- Module conformance v2 corpus passes for `require(esm)`, async ESM rejection,
  JSON import attributes, ESM/CJS cycles, `import.meta.resolve`, custom package
  conditions, TypeScript mixed graphs, and packaged moduleSources mixed graphs.
- Packaged `.mjs` graph smoke passes with refreshed template support.
- Dynamic import local-only smoke passes for `.mjs`, CommonJS, allowed builtins,
  and packages, while URL, absolute path, escape, and disabled builtin paths
  remain denied.
- Buffer smoke passes for CJS and ESM import forms, global consistency, common
  construction APIs, and `node_modules/buffer` shadow denial.
- Core builtin compatibility smoke passes for `constants`, `util/types`,
  `events`, and `diagnostics_channel` aliases, CJS and partial ESM import
  forms, shadow denial, `process.getBuiltinModule()` denial, and disabled
  builtin regressions.
- Stream smoke passes for async iteration, callback and promise pipeline,
  callback and promise finished, Web Streams adapters, slow-writable
  backpressure, destroy propagation, and `node_modules/stream` shadow denial.
- Web Streams smoke passes for `stream/web`, global constructors, Blob streams,
  Node/Web adapters, controlled fetch `response.body.stream()` reads, and
  packaged APK markers.
- Web Platform globals smoke passes for `Headers`, `Request`, `Response`,
  `FormData`, `Blob`, `File`, `URLPattern`, `DOMException`, `EventTarget`,
  `MessageEvent`, `CompressionStream`, and `DecompressionStream`, including
  descriptor locking, gzip roundtrip, URLPattern groups, DOM/Event shapes, and
  default controlled-network-off `fetch()` denial.
- Controlled fetch smoke passes for global `fetch`, `require("fetch")`,
  GET/POST, headers, string/Buffer/Uint8Array bodies, response
  text/json/arrayBuffer helpers, timeout, abort-before and abort-during
  dispatch, response budget, redirect modes, unsupported schemes, raw network
  denial, diagnostics, and default Android provider denial.
- Controlled HTTP client facade smoke passes for `http` / `node:http` and
  `https` / `node:https` identity, `request()` / `get()`, request and response
  headers, status metadata, buffered request body chunks, response `data` /
  `end` / `pipe()`, timeout, abort, default manual redirects, explicit
  `maxRedirects` follow, enforced `network` manifest tagging, pending cleanup,
  and unchanged default sync denial.
- Controlled HTTPS client facade smoke passes for `https` / `node:https`
  identity, `https:` request dispatch, safe `https.Agent` shape, request
  header/body serialization, response metadata/body delivery, enforced
  `network` manifest tagging, pending cleanup, and fail-closed TLS
  customization including `rejectUnauthorized=false`, custom secure contexts,
  client certificate fields, request-level `createConnection`, and unsafe agent
  TLS options.
- Controlled HTTP stream semantics smoke passes for writable request body
  chunks, readable response `data` / `end` / `close`, `pipe()` backpressure
  smoke, request-level `AbortSignal`, pre-aborted signal no-dispatch,
  `maxResponseBytes`, service-destroy cancellation, and pending cleanup.
- Controlled HTTP Agent facade smoke passes for `http.Agent` / `https.Agent`
  safe shape fields, `ClientRequest.agent` binding, empty compatibility pool
  objects, `destroy()`, request permission tagging, pending cleanup, and
  fail-closed denial for custom connector and `socketPath` options.
- Controlled Undici facade smoke passes for reserved `require("undici")`,
  controlled `fetch()` / `request()`, Web Platform constructor reuse,
  `Dispatcher` / `Agent` / `MockAgent` safe shapes, global dispatcher
  validation, local shadow denial, request permission tagging, pending cleanup,
  and fail-closed denial for raw connectors, dispatcher injection,
  socket/proxy/tunnel options, upgrades, streams, and direct dispatch.
- HTTP npm corpus smoke passes for offline fixtures using `http.get()`,
  `https.request()`, `IncomingMessage` `data` / `end`, form-style POST
  bodies, request-level `AbortSignal`, safe Agent options, native-addon and
  lifecycle-script absence checks, bridge permission tagging, and pending
  cleanup.
- Controlled axios smoke passes for reserved `require("axios")`, fake-provider
  get/post/request, `baseURL`, params serialization, headers, timeout,
  json/text/arraybuffer response types, Axios-like error shape, custom
  adapter/agent/proxy denial, `node_modules/axios` shadow denial, packaged
  default-provider denial, and raw network denial.
- Controlled WebSocket smoke passes for reserved `require("websocket")`,
  fake-provider connect/send/receive/close, headers/protocols/maxQueueSize
  descriptors, text and binary payloads, handler properties, listener aliases,
  close/error event shape, timeout, message budget, max connection, service
  destroy cleanup, default-provider denial, and raw socket denial.
- Network capability manifest v2 gates controlled `fetch`, `axios`, and
  `websocket` with the `network` bridge capability, maps that capability to
  `android.permission.INTERNET` for packaged APKs, and reports the mapping in
  Node doctor text/JSON output.
- Image advanced operation smoke passes for `grayscale`, `threshold`,
  `matchTemplate`, `findColor`, `findMultiColors`, local `getSize`, scoped
  `saveImage` format overloads, generated image-handle tracking, provider
  unavailable denial, and TypeScript declarations.
- OCR bridge smoke passes for fake-provider `recognize`, `recognizeText`, and
  `detectTextBounds`, opaque image handle validation, manifest capability
  denial, TypeScript declarations, and live MLKit Android provider behavior
  over scoped provider-owned image handles with cleanup diagnostics.
- Storage bridge smoke passes for `storage`/`storages` create/open,
  get/put/remove/clear/keys/contains, JSON value policy, manifest denial,
  Android provider persistence, working-directory namespace isolation,
  package-identity isolation, quota denial, and TypeScript declarations.
- Notifications bridge smoke passes for `notify`/`cancel`/`cancelAll`,
  fake-provider request serialization, manifest denial, Android
  permission-denial or post/cancel behavior, owned-resource cleanup,
  stable channel IDs, ongoing ownership metadata, `POST_NOTIFICATIONS`
  manifest mapping, and TypeScript declarations.
- Sensors bridge smoke passes for `getAvailableSensors`, `once`,
  `subscribe`, fake-provider event draining, manifest denial, Android sensor
  listing, live one-shot events, live subscription drain/unsubscribe,
  sampling interval clamp, max subscription denial, missing-provider
  unavailable errors, cleanup when a supported sensor exists, and TypeScript
  declarations.
- UI bridge smoke passes for `require("ui")`, fake-provider `showLayout`,
  button/click event drain, `handle.update()`, `handle.batchUpdate()`,
  `handle.close()`, `ui.state()`/`ui.bind()` one-way binding, JSON-only
  invalid descriptor denial before dispatch, shadowing protection, no raw
  View/Activity property on the handle, Activity-owned Android live provider
  Button/Input/List/Switch/Checkbox/Tabs event delivery, Progress batch update,
  WebView security-gate denial, provider-destroy cleanup, and TypeScript
  declarations.
- Accessibility selector v2 smoke passes for regex, clickable/enabled, depth,
  bounds descriptors, `longClick`, `setText`, `scrollForward`,
  `scrollBackward`, timeout/cancellation cleanup, manifest denial, no raw node
  leak, Android unavailable rejection, optional controlled-Activity selector
  `findOne`/`click` when the service is pre-enabled, and TypeScript
  declarations.
- Shell control smoke passes for `exec`, JS-layer `execFile`, `checkAccess`,
  default timeout changes, non-zero exit, timeout result shape, scoped
  cwd/path/env denial, root capability denial, unsupported Shizuku reporting,
  Android non-root execution, and `child_process` denial.
- Work Manager live smoke passes for schedule-once, schedule-periodic, cancel,
  list, `work_manager` capability tagging, embedded backend and
  `scriptPath`/project `entry` metadata, packaged entry descriptors, AndroidX
  WorkManager persistence, scoped scheduled-runner CJS/ESM embedded Node
  execution, persisted `lastRun`/`runHistory`, delayed cancel prevention,
  scoped path and missing-file denial, minimum periodic interval enforcement,
  no implicit retry, and `tasks`/raw scheduler denial.
- Plugin system smoke passes for reserved `require("plugins")`,
  project-local safe plugin loading, manager install/enable/disable/config/
  version/dependencies/exports/status/capabilities/start/stop/unload/list/
  reload, exact `plugin.<id>` opt-in, plugin requested-permission denial before
  code execution, namespace metadata, plugin-owned resource cleanup on stop,
  debug-only reload denial/allow path, `.node` rejection, plugin entry path
  escape denial, `node_modules/plugins` shadow denial, packaged local JS-only
  plugin/UI smoke, and TypeScript declarations.
- Developer REPL smoke passes for debug-build availability, expression eval,
  console capture, allowed `path` builtin require, `child_process` denial,
  default bridge deny-by-default behavior, explicit bridge capability opt-in,
  isolated temporary `workingDirectory`, and cleanup after close.
- Project wizard smoke passes for every checked template, JSON validator output,
  missing-entry diagnostics, invalid package name diagnostics, unknown
  permission diagnostics, and unsafe dependency bundle diagnostics.
- Phase 7 security corpus passes for packaged ESM/dynamic-import escape gates,
  stale runtime descriptor mismatch, ESM/static import escape denial, local
  dynamic import escape and network URL denial, fs stream escape cleanup,
  live bridge capability bypass denial before dispatch, WorkManager arbitrary
  path denial, database `ATTACH`/`DETACH`/`load_extension` denial, UI handle
  cleanup without raw View exposure, plugin `.node`, path, and capability
  rejection before code execution, worker bridge access denial or
  unavailable-worker fail-closed behavior, long-running timer exhaustion
  timeout diagnostics, dependency sanitizer bypass rejection, shell
  `child_process` denial, and zlib stream error cleanup.
- Bridge resource leak stress passes for repeated OCR, storage,
  notifications, sensors, accessibility selector, image, UI, shell timeout,
  and WebSocket loops with zero active provider resources, shell commands,
  WebSocket connections, leaked resources, or close errors after cleanup.
- Execution queue smoke passes for default `ERR_AUTOJS6_NODE_ENGINE_BUSY`
  rejection, explicit enqueue order, queued cancellation, queued timeout before
  user code runs, one-entry queue-full denial, queue status diagnostics, and
  recovery after the active execution stops.
- Interactive long-running foreground mode passes status, foreground survival,
  Stop action, runtime heartbeat/budget diagnostics, Android
  foreground-service timeout cleanup, cooperative checkpoint-on-stop,
  explicit restore after service settle, repeated stop/checkpoint/recovery
  stress, and provider-destroy resource cleanup while direct script
  long-running requests still reject before user code and normal interval
  scripts still time out.
- Worker threads remain default-off and blocked for v1.1 runnable partial
  support until native `worker_threads` availability changes and the focused
  execution/stress suites pass on Sony. The S7-24 worker bootstrap gate must
  keep proving default denial, shadowing protection, native availability
  preflight, and zero active workers when native support is unavailable.
- fs stream smoke passes for read/write/pipe, scoped escape denial,
  `/proc`/`/sys`/`/dev` absolute path denial, active stream timeout cleanup,
  and packaged APK markers.
- FileHandle advanced smoke passes for managed `fs.promises.open()` handles,
  read/write helpers, promise-level handle helpers, scoped path denial, closed
  handle/non-owned descriptor `EBADF`, and timeout recovery after an unclosed
  handle.
- Callback/sync fd API smoke passes for managed `fs.open`/`openSync`,
  `close`/`closeSync`, `read`/`readSync`, `write`/`writeSync`,
  `fstat`/`fstatSync`, `ftruncate`/`ftruncateSync`, scoped path denial,
  closed/non-owned descriptor `EBADF`, and timeout recovery after an unclosed
  sync fd.
- `opendir` smoke passes for managed `fs.opendir`, `fs.opendirSync`, and
  `fs.promises.opendir`, including callback/sync/promise reads, close paths,
  async iteration, `Dirent` shape helpers, buffer names, bounded recursive
  walks, scoped path denial, and closed `Dir` `ERR_DIR_CLOSED`.
- `fs.watch` smoke passes for managed `fs.watch`, `fs.promises.watch`,
  `fs.watchFile`, and `fs.unwatchFile`, including AbortSignal, async iterator,
  polling stats, path-like inputs, watcher quota, event-rate limiting,
  unsupported recursive watch, Android event coalescing tolerance, and timeout
  recovery after a leaked watcher.
- zlib stream smoke passes for gzip/gunzip, deflate/inflate, scoped fs
  read->gzip->write, invalid gzip error propagation, active stream timeout
  cleanup, and packaged APK markers.
- Packaged APK v1.1 bridge smoke passes for declared `node.permissions`,
  controlled `fetch` fake transport, OCR opaque image handle requests,
  JSON `storage`/`storages` CRUD, notifications, UI fake
  layout/event/update/batch update/close, JS-only plugin load/run/stop/unload
  with isolated capabilities/resources, `work_manager` fake-transport
  schedule/list/cancel serialization, bridge-module shadowing protection, and
  zero pending bridge calls.
- Compatibility profile marks ESM and dynamic import as `partial`.

## Safety Invariants

- ESM resolution must use the same scoped fs and package target checks as
  CommonJS.
- Builtin imports must route through the existing allowlist, limited wrappers,
  and disabled-builtin policy.
- ESM must not enable native addons, process binding, raw network, raw Android
  interop, or loader hooks.
- Packaged APK support must remain a first-class target; features not covered
  by packaged smoke stay experimental or disabled.
