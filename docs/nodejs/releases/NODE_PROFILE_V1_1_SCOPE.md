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
- Practical ESM-to-CommonJS interop while keeping CommonJS `require()` of ESM
  disabled with a stable error.
- Package `exports` and `imports` condition selection for both CommonJS and
  ESM importers.
- Controlled fetch and WebSocket remain on the v1.1 roadmap, but are not part
  of the first ESM promotion batch.
- NPM compatibility expansion continues to focus on pure JavaScript packages.
- AutoJs bridge expansion continues through capability-gated modules.
- Long-running script support remains an explicit mode, not accidental
  behavior from timers, sockets, workers, or bridge resources.

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
| Real npm corpus | partial | v1.1 expands the fixed offline corpus with ESM-only dependency, hybrid CJS/ESM, Buffer, fetch wrapper fallback, and assertion helper fixtures while retaining no-install/no-native safety checks. |
| Scoped fs | stable | No policy loosening. |
| ESM entry | partial | `.mjs` and `type=module` entries run by default in v1.1. |
| ESM builtin import | partial | Only allowed builtins and limited wrappers. |
| ESM/CJS interop | partial | ESM may import CJS; CJS `require()` of ESM remains denied. |
| Package ESM conditions | partial | ESM uses `import`, `node`, `default`; CJS keeps `require`, `node`, `default`. |
| Dynamic import | partial | Local-only dynamic import is enabled by default for relative modules, allowed builtins, and scoped packages. |
| Packaged ESM | partial | Packaged `.mjs` entry plus local `.mjs`/CommonJS graph smoke is required; dynamic import local graph smoke is covered separately. |
| Buffer module | partial | `require("buffer")`, `require("node:buffer")`, and ESM `node:buffer` use the same builtin wrapper and cannot be shadowed by `node_modules/buffer`. |
| Core builtin feature-detect modules | partial | `constants`, `util/types`, `events`, and `diagnostics_channel` support CJS and partial ESM import paths with shadowing denial and no `process.getBuiltinModule()` bypass. |
| Stream primitives | partial | Async iteration, callback/promise pipeline and finished helpers, Web Streams adapters, backpressure smoke, destroy propagation, and shadowing denial stay covered as pure-JS compatibility surface. |
| Web Streams | partial | `stream/web`, globals, Blob streams, Node/Web adapters, and bounded fetch `response.body.stream()` reads are supported without direct network streaming. |
| fs streams | partial | Scoped read/write streams support read, write, pipe, stable denial paths, active-stream diagnostics, timeout/service cleanup, and packaged smoke coverage. |
| zlib streams | partial | Bounded transform streams support gzip/gunzip and deflate/inflate pipelines, scoped fs pipeline smoke, invalid input errors, active-stream diagnostics, timeout/service cleanup, and packaged smoke coverage. |
| Controlled fetch | experimental | Global `fetch` and `require("fetch")` surface is hardened for v1.1 with fake-provider GET/POST, headers, string/binary bodies, response helpers, abort, timeout, budgets, redirects, and raw-network denial; live provider remains default-off. |
| Controlled axios | experimental | `require("axios")` exposes an AutoJs6-owned fetch-backed compatibility adapter for `get`/`post`/`request`, `baseURL`, params, headers, timeout, response types, and Axios-like errors while denying custom adapter/agent/proxy/raw transport paths. |
| WebSocket | experimental | Controlled bridge surface covers connect, send/receive text and binary messages, close/error events, handler properties, listener aliases, bounded queue/message policy, service-destroy cleanup, and raw socket denial; live provider remains default-off. |
| Image advanced operations | partial | `require("image")` and `require("images")` expand opaque-handle workflows with grayscale, threshold, template matching, color search, local size snapshots, and save format overloads while keeping Android provider ownership fail-closed. |
| OCR | partial | `require("ocr")` exposes `recognize`, `recognizeText`, and `detectTextBounds` over opaque image handles with `ocr` + `image` capability checks; Android provider remains fail-closed until real provider/model ownership is accepted. |
| Storage | partial | `require("storage")` and `require("storages")` expose JSON-only project/package-scoped key-value stores with explicit `storage` capability checks. |
| Notifications | partial | `require("notifications")` exposes post-only `notify`, `cancel`, and `cancelAll` with explicit `notifications` capability checks, Android `POST_NOTIFICATIONS` permission mapping, and owned-resource cleanup. |
| Sensors | partial | `require("sensors")` exposes `getAvailableSensors`, `once`, and bounded callback subscriptions for `accelerometer`, `gyroscope`, and `light` with explicit `sensors` capability checks. |
| Accessibility selector v2 | partial | `require("accessibility")` expands selector descriptors with regex, clickable/enabled, depth, and bounds fields plus selector actions for long-click, set-text, and scroll while keeping JSON-only snapshots and explicit `accessibility` capability checks. |
| Shell controls | partial | `require("shell")` remains the only child_process alternative and adds `execFile`, `checkAccess`, and `setDefaultTimeout` over the same controlled `exec` bridge while keeping Shizuku unsupported. |
| Work Manager metadata | partial | `require("work_manager")` registers controlled scheduled-task metadata for `scheduleOnce`, `schedulePeriodic`, `cancel`, and `list`, stores embedded backend plus `scriptPath`/project `entry` metadata, supports packaged entry descriptors, and still does not launch background Node executions. |
| Execution queue policy | partial | Embedded Node remains one active execution per `:nodejs_embedded` process; explicit caller-side enqueue supports one queued execution, queue status, cancellation, queue timeout, and stable busy/full/timeout/cancelled errors. |
| Long-running mode | blocked | Request protocol carries `executionMode`, but v1.1 currently supports only `one_shot`; explicit `long_running`/`service` requests are rejected before user code runs. |
| Worker threads | blocked | Disabled by default. Task 170 rechecked the pure-JS worker target, but the current Embedded Node build still lacks native `worker_threads`; no runnable v1.1 partial support is advertised. |
| Native addon | unsupported | Must remain disabled. |
| Child process | disabled | Use controlled `shell` bridge instead. |

## Success Criteria

- `.mjs` entry smoke passes without a per-test ESM opt-in.
- Fixed real npm corpus increases from the v1.0 CommonJS-only baseline and
  produces `build/reports/nodejs/npm-compat-v1.1.md`.
- Package `type=module` entry smoke passes.
- Local static import, named export, default export, and top-level await pass.
- ESM imports of allowed builtins pass.
- ESM import of disabled builtins fails with stable AutoJs6 error metadata.
- ESM package `exports`/`imports` choose the import condition.
- CJS package `exports`/`imports` still choose the require condition.
- CommonJS `require()` of `.mjs` fails with
  `ERR_AUTOJS6_REQUIRE_ESM_UNSUPPORTED`.
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
- Controlled fetch smoke passes for global `fetch`, `require("fetch")`,
  GET/POST, headers, string/Buffer/Uint8Array bodies, response
  text/json/arrayBuffer helpers, timeout, abort-before and abort-during
  dispatch, response budget, redirect modes, unsupported schemes, raw network
  denial, diagnostics, and default Android provider denial.
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
  denial, TypeScript declarations, and fail-closed Android provider behavior.
- Storage bridge smoke passes for `storage`/`storages` create/open,
  get/put/remove/clear/keys/contains, JSON value policy, manifest denial,
  Android provider persistence, working-directory namespace isolation, and
  TypeScript declarations.
- Notifications bridge smoke passes for `notify`/`cancel`/`cancelAll`,
  fake-provider request serialization, manifest denial, Android
  permission-denial or post/cancel behavior, owned-resource cleanup,
  `POST_NOTIFICATIONS` manifest mapping, and TypeScript declarations.
- Sensors bridge smoke passes for `getAvailableSensors`, `once`,
  `subscribe`, fake-provider event draining, manifest denial, Android sensor
  listing, live subscription cleanup when a supported sensor exists, and
  TypeScript declarations.
- Accessibility selector v2 smoke passes for regex, clickable/enabled, depth,
  bounds descriptors, `longClick`, `setText`, `scrollForward`,
  `scrollBackward`, timeout/cancellation cleanup, manifest denial, no raw node
  leak, and TypeScript declarations.
- Shell control smoke passes for `exec`, JS-layer `execFile`, `checkAccess`,
  default timeout changes, non-zero exit, timeout result shape, scoped
  cwd/path/env denial, root capability denial, unsupported Shizuku reporting,
  Android non-root execution, and `child_process` denial.
- Work Manager metadata smoke passes for schedule-once, schedule-periodic,
  cancel, list, `work_manager` capability tagging, embedded backend and
  `scriptPath`/project `entry` metadata, packaged entry descriptors, scoped
  path and missing-file denial, minimum periodic interval enforcement, and
  `tasks`/raw scheduler denial.
- Execution queue smoke passes for default `ERR_AUTOJS6_NODE_ENGINE_BUSY`
  rejection, explicit enqueue order, queued cancellation, queued timeout before
  user code runs, one-entry queue-full denial, queue status diagnostics, and
  recovery after the active execution stops.
- Long-running mode remains blocked until an async service lifecycle exists,
  but the explicit `executionMode` policy gate rejects `long_running`/`service`
  before user code runs while normal interval scripts still time out.
- Worker threads remain default-off and blocked for v1.1 runnable partial
  support until native `worker_threads` availability changes and the focused
  execution/stress suites pass on Sony.
- fs stream smoke passes for read/write/pipe, scoped escape denial,
  `/proc`/`/sys`/`/dev` absolute path denial, active stream timeout cleanup,
  and packaged APK markers.
- zlib stream smoke passes for gzip/gunzip, deflate/inflate, scoped fs
  read->gzip->write, invalid gzip error propagation, active stream timeout
  cleanup, and packaged APK markers.
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
