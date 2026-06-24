# Compatibility Notes: Auto.js Pro 9 And AutoX.js v7

This document compares user-visible behavior only. It is not an implementation-equivalence claim and does not infer private internals of other projects.

References last reviewed: 2026-06-14.

- Auto.js Pro 9 public docs: <https://www.wuyunai.com/docs/v9/>
- AutoX.js v7 public docs: <https://autoxjs.dayudada.com/v7/docs/nodejs/intro/>

## Positioning

AutoJs6 exposes the AutoJs6 Safe Node Profile:

```text
Embedded Node/V8/libuv runtime
+ AutoJs6-controlled CommonJS loader
+ scoped filesystem
+ restricted builtin allowlist
+ asynchronous AutoJs bridge modules
+ packaged APK support
```

The goal is not unrestricted desktop Node.js compatibility. Scripts written for Auto.js Pro 9 or AutoX.js v7 may need adaptation.

## Entry Points

Auto.js Pro 9 public docs describe:

- Rhino remains the default engine for compatibility.
- Node can be selected with a `"nodejs";` file header.
- `.node.js` and `.mjs` files use the Node engine.
- `.mjs` enables ES Module behavior.

AutoX.js v7 public docs describe:

- normal `.js` files use the first-generation Rhino engine by default.
- `.mjs`, `.cjs`, and `.node.js` files use the Node engine.
- `.mjs` enables ESM and is described as the recommended Node route.

AutoJs6 behavior:

- `"nodejs";` and `"node";` route to the Node backend.
- `.node.js`, `.cjs`, and `.mjs` route to the Node backend.
- `.mjs` runs through the Node Profile v1.1 partial ESM path by default.
- CommonJS `require()` can load synchronous partial ESM graphs and rejects async
  ESM graphs with `ERR_REQUIRE_ASYNC_MODULE`.

Porting note: use `.cjs` or `"nodejs";` plus CommonJS `require()` when you need the widest AutoJs6 compatibility.

## Module Style

Auto.js Pro 9 public docs describe module usage through `require()` for Pro 9 modules and Node builtins.

AutoX.js v7 public docs describe second-generation APIs as import-first and state that `require()` and dynamic `import()` are not supported there at the time of the referenced docs.

AutoJs6 behavior:

- CommonJS `require()` is the primary supported module system.
- package `main`, `exports`, `imports`, self-reference, scoped packages, and JSON modules are supported within the Safe Node Profile.
- ESM static import is partially supported from `.mjs` and `type=module` entries.
- Local-only dynamic import is partially supported for relative modules,
  allowed builtins, and scoped packages.

Porting note:

```js
// Pro-style CommonJS is closest to AutoJs6 today.
const { showToast } = require("toast");
```

AutoX v7 import-first code usually needs to be rewritten to CommonJS for AutoJs6:

```js
// AutoX v7 style
import { showToast } from "toast";

// AutoJs6 Safe Node Profile style
const { showToast } = require("toast");
```

## npm And Builtins

Auto.js Pro 9 public docs present a broader Node/npm user experience, including npm installation guidance and examples using Node builtins such as `fs`, `http`, `https`, and `worker_threads`.

AutoJs6 behavior:

- pure JavaScript CommonJS packages are the primary compatibility target.
- checked-in `node_modules` package trees are supported.
- `npm install`, postinstall scripts, and native addon installation are not supported by the Safe Node Profile.
- raw Node network modules remain disabled by default.
- `worker_threads` remains disabled by default.
- native `.node` addons remain disabled by default.

P13-39 records native addon loading as partial. AutoJs6 now reports
`nativeAddonProfile` diagnostics, but this does not enable `.node` imports,
`process.dlopen`, native builds, or runtime download-exec. Future Desktop
support still requires signed packaged artifacts, ABI/hash allowlists,
dependency scan, and crash isolation.

Porting note: vendor pure-JS dependencies into the project, avoid install-time scripts, and check `docs/nodejs/COMPATIBILITY_PROFILE.md` for supported builtins.

## Filesystem

Auto.js Pro 9 public docs show Node `fs` examples that read Android absolute paths.

AutoJs6 behavior:

- `fs` and `fs/promises` are scoped to the working directory/project root.
- absolute paths are denied.
- `..` escape, NUL paths, `/proc`, `/sys`, `/dev`, symlink escape, and realpath escape are denied.
- file URL behavior is constrained by the scoped file URL policy.

Porting note:

```js
// Avoid this in AutoJs6 Node:
fs.readFile("/sdcard/scripts/test.txt", "utf8", callback);

// Prefer scoped project paths:
fs.readFile("data/test.txt", "utf8", callback);
```

## AutoJs API Bridge

Auto.js Pro 9 public docs describe migrated Pro modules such as `app`, `accessibility`, `image`, and `media_projection`, with many operations modeled as asynchronous Promise APIs.

AutoX.js v7 public docs also describe Promise and async-function direction for second-generation APIs.

AutoJs6 behavior:

- Android and AutoJs APIs are exposed through asynchronous bridge modules.
- Node scripts do not receive raw Android objects or unrestricted Java access by default.
- bridge calls are routed through capability providers and have timeout/destroy handling.
- returned UI nodes and image values are serialized snapshots or handles, not raw Java object references.

Current user-facing bridge modules include:

- `toast`
- `app`
- `dialogs`
- `engines`
- `accessibility`
- `media_projection`
- `image` / `images`
- `clipboard`
- `device`
- `ui`
- `ui.overlay`
- `shell`
- controlled `fetch` when the network experiment is explicitly enabled

Some modules are intentionally narrower than Pro/AutoX counterparts.

## Screenshot, Image, And OCR

P13-20 records screenshot/image/OCR as partial. AutoJs6 has the direct
`media_projection`, `image`/`images`, and `ocr` API shapes plus explicit
`autojs6:compat` wrappers for Rhino-style screenshot, image, and OCR code.
Scoped `image.readImage()`/`recycle()` and ML Kit OCR over provider-owned image
handles are live. Bridge-owned MediaProjection prompting, advanced image save
or transform operations, template/color search, packaged capture/OCR behavior,
Paddle/Rapid fallback, and raw Bitmap/OpenCV object exposure are still blocked.

## Accessibility And Selector

Auto.js Pro and Rhino scripts commonly rely on global selector builders such as
`text()`, `id()`, `desc()`, chained selector handles, global actions, and
gesture helpers.

AutoJs6 behavior:

- P13-19 records selector/accessibility as partial.
- `require("accessibility")` exposes JSON selector builders, `findOne`,
  `findAll`, selector `click`, `longClick`, `setText`, `scrollForward`,
  `scrollBackward`, `findByText`, `clickText`, `back`, `home`, and
  `recentApps`.
- `require("autojs6:compat")` exposes Promise-based Rhino-style selector
  handles, including `findOne(1000)`, `find()`, `findAll()`, `exists()`,
  selector actions, coordinate `click`, and Back/Home/Recents wrappers.
- Returned nodes are frozen JSON snapshots, not raw `AccessibilityNodeInfo` or
  Rhino/UiAutomator node objects.
- `swipe`, `gesture`, `powerDialog`, direct `waitFor`, packaged
  accessibility-service lifecycle, and root/Shizuku automation tiers remain
  unpromoted.

Porting note: use explicit `await` with direct `accessibility` or
`autojs6:compat` selector calls. Keep scripts that require synchronous Rhino
selector behavior, raw node objects, or gesture/root/Shizuku fallback on Rhino
until a later provider gate lands.

## UI

Auto.js Pro 9 public docs describe `"nodejs ui"` / `"ui-thread"` style entry
semantics.

AutoJs6 behavior:

- P13-11 records the entry directive metadata path, but it does not yet make
  Pro-style UI-thread execution equivalent.
- P13-17 records the live `require("ui")` provider promotion gate: JSON
  layout, update, event drain, close, and provider cleanup have live Activity
  evidence.
- `require("autojs6:compat").ui` maps to the same JSON-only provider for Rhino
  migration.
- live packaged UI, Back/Recents, orientation/configuration-change,
  relaunch/crash, and packaged disclosure evidence remain unpromoted.

Porting note: migrate Rhino UI scripts to explicit `require("ui")` or
`compat.ui` JSON descriptors instead of relying on raw `View`, `NativeView`,
`ui.run()`, `ui.post()`, or implicit current-Activity behavior.

## Floaty And Overlay

Auto.js Pro and AutoX scripts commonly use Rhino `floaty.window()` /
`floaty.rawWindow()` and overlay permission helpers.

AutoJs6 behavior:

- P13-18 records the partial `ui.overlay` provider promotion gate over the
  P12-14 Android live gate.
- `require("ui.overlay")` has JSON-only facade/types for `show`, `update`,
  `drainEvents`, `close`, `closeAll`, `hasPermission`, and
  `openPermissionSettings`.
- `hasPermission`, `openPermissionSettings`, and `closeAll` have Android
  permission/settings/cleanup live-gate evidence.
- live `show`, `update`, `drainEvents`, and per-handle `close` still fail
  closed until a real Android overlay provider, foreground disclosure,
  packaged install-run behavior, and leak/stress evidence land.
- `require("floaty")`, `compat.floaty`, global `floaty`, and exact Rhino raw
  `floaty.window()` / `floaty.rawWindow()` View parity remain unavailable.

Porting note: use `ui.overlay` only for JSON-only overlay migration
experiments. Production scripts should not depend on live overlay windows until
the provider is promoted.

## Tasks And User Task Database

P13-21 records task database provider promotion as partial. AutoJs6 Node has
live `work_manager` scheduling for Node-owned scoped entries and
`require("autojs6:compat").tasks` one-shot disposable-task migration over that
provider. It still does not expose direct `require("tasks")`, global `tasks`,
daily/weekly wall-clock task database operations, IntentTask/broadcast task
provider access, packaged task database promotion, or raw Rhino task object
mutation.

P13-22 records IntentTask/broadcast provider promotion as partial. The
Android/Rhino `IntentTask` database and receiver stack are present, and the
Node `compat.tasks` IntentTask helpers keep stable unsupported errors. AutoJs6
Node still cannot register Android receivers, receive boot/package/custom
broadcast triggers, mutate packaged manifests, inspect raw trigger payloads, or
access raw `Intent` / `BroadcastReceiver` objects.

P13-23 records app/intent/broadcast Pro parity as partial. AutoJs6 Node now has
JSON-safe `app.intent()` descriptors and stricter `app.startActivity()`
descriptor parsing for `category`/`categories`, numeric flag arrays, and
primitive extras. It still keeps `app.sendBroadcast()`, root/Shizuku app
options, dual-user helpers, URI/email/settings helpers, installed-app lookup
policy, packaged behavior, and raw Android app/intent objects blocked.

P13-24 records shell/root/Shizuku capability promotion as partial. AutoJs6 Node
now has explicit `shell`, `shell.root`, and `shell.shizuku` capability tokens,
`shell.execRoot()` / `compat.shell.execRoot()` facades for the existing
controlled root shell path, and stable pre-dispatch Shizuku rejection. Real
Shizuku service binding, authorization prompts, lifecycle cleanup, packaged
disclosure, and device matrix evidence remain blocked.

P13-25 records notifications/settings/power manager promotion as partial.
AutoJs6 Node now has JSON-safe notification permission/settings snapshots and
battery-optimization status/settings dry-run helpers through exact
`notifications.settings` and `device.power` boundaries. Keep Rhino/Pro scripts
that depend on notification actions/listeners, rich layouts, Do-Not-Disturb,
exact alarms, wakelocks, direct power-manager control, OEM power behavior, or
packaged disclosure flows on Rhino until a dedicated provider lands.

P13-26 records media/recorder/mediainfo provider promotion as partial. AutoJs6
Node now has read-only audio stream snapshots, scoped file metadata through
`mediainfo`, and recorder status behind exact `media.audio`, `media.metadata`,
and `media.recording` boundaries. Keep Rhino/Pro scripts that depend on audio
playback, real recording sessions, MediaStore, packaged media assets,
foreground microphone disclosure, or raw Android media objects on Rhino until a
dedicated provider lands.

P13-27 records utility-heavy Rhino modules promotion as partial. AutoJs6 Node
now exposes pure `jsox.mathx` helpers through `require("jsox")`,
`require("jsox.mathx")`, and `require("autojs6:compat").jsox`. Keep Rhino/Pro
scripts that depend on default global `Math`/prototype mutation, `zip`,
`canvas`, OpenCC/pinyin packaged dictionaries, `sysprops`, `Arrayx`, `Numberx`,
or raw Android/OpenCV objects on Rhino until separate gates land.

P13-29 records HTTP/HTTPS client parity as partial. AutoJs6 Node supports the
controlled `http`/`https` client facade for request/get, stream response,
timeout, redirect, and provider-owned TLS errors, but still rejects
server/listen, raw sockets, proxy/tunnel options, `CONNECT`, custom TLS/session
control, and per-request proxy configuration.

P13-30 records net/tls/dns staged compatibility as partial. AutoJs6 Node now
keeps `net`, `tls`, `dns`, and `dns/promises` reserved and typed for package
probing, `SocketAddress`, `BlockList`, TLS metadata, and deterministic
loopback/IP-literal DNS lookup. Keep Pro/AutoX scripts that depend on real TCP
or TLS sockets, server listeners, Android resolver state, custom DNS servers,
TLS sessions, fd handles, or live network DNS on Rhino/Android APIs until a
future Desktop-profile gate explicitly promotes those capabilities.

P13-31 records dgram UDP capability as stable-denied. AutoJs6 Node does not
provide `require("dgram")` or `require("node:dgram")` in the current v1.1
Desktop track. Keep Pro/AutoX scripts that depend on UDP send/receive, UDP
server bind/listen, multicast, broadcast, or raw datagram sockets on
Rhino/Android APIs until a future UDP provider and packaged permission policy
land.

P13-32 records filesystem profile expansion as partial. AutoJs6 Node still
keeps `fs` and `fs/promises` scoped to the script working directory by default;
`autojs6:profile.filesystemProfile` is diagnostic-only and reports no extra
roots. Keep Pro/AutoX scripts that depend on `/sdcard`, SAF/document trees,
user-selected directories, app-private shared roots, or broad Android storage
on Rhino/Android APIs until a future explicit filesystem root provider lands.

P13-33 records desktop fs advanced APIs as partial. AutoJs6 Node can already
exercise scoped streams, `FileHandle`, fd APIs, `opendir`, watchers, and
`realpath` in focused tests, and `filesystemProfile.advancedApis` reports that
state. Scripts should still treat these as scoped compatibility APIs: raw native
fd access, recursive watchers, expanded roots, and full packaged
watcher/fd/FileHandle lifecycle parity are not yet Pro/Desktop-promoted.

P13-34 records `worker_threads` opt-in promotion as partial. AutoJs6 Node keeps
`worker_threads` disabled by default, and `workerThreadsProfile` is diagnostic
only. Pro/AutoX scripts should not treat worker threads as a portable threading
replacement until native worker availability, packaged worker smoke, crash
evidence, and Android resource ceilings are promoted.

P13-35 records process-worker replacement as partial. AutoJs6 Node exposes
`processWorkerReplacementProfile` diagnostics to show that WorkerPool is the
current replacement path, but it does not add `process_worker`,
`compute_worker`, raw `child_process`, raw process handles, or packaged
process-worker behavior. Scripts that need old threading/process-worker
semantics should remain on Rhino or explicit bridge-backed alternatives until a
future profile promotes them.

P13-40 records child_process scoped runner as partial. AutoJs6 Node exposes
`childProcessProfile` diagnostics, but it does not enable `child_process`,
`node:child_process`, `spawn`, `fork`, streaming stdio, or process handles.
Continue using the controlled `shell` bridge for the supported migration path
until a future Desktop profile promotes a scoped runner with app-private binary
allowlists and cleanup evidence.

P13-41 records ESM loader parity as partial. AutoJs6 Node exposes
`esmLoaderProfile` diagnostics for the current AutoJs6-managed ESM subset:
`.mjs`, `type=module`, TLA, local static/dynamic import, package
`exports`/`imports`, JSON import attributes, scoped `import.meta`, and CJS
interop. It does not enable raw loader hooks, network/file/data URL imports,
import maps, custom condition profiles, or broad packaged encrypted ESM graphs.

P13-42 records Android package manager parity as partial. AutoJs6 Node exposes
`packageManagerProfile` diagnostics for the current host-managed package
manager plus Android local-store substrate. Pure JavaScript dependencies can be
prepared by host tooling and stored through the internal app-private Android
store, but Android npm CLI, registry download, lifecycle scripts, bin shims,
symlinks, native builds/native payloads, postinstall downloads, and
script-visible `package_manager`/`npm` remain unavailable.

P13-43 records Node stdlib conformance as partial. AutoJs6 Node exposes
`stdlibProfile` diagnostics for representative Safe stdlib coverage across
`crypto`, streams, zlib, Buffer, URL, OS, readline, TTY, `perf_hooks`, and
`async_hooks`. Desktop packages that need broader OS/terminal behavior,
`process.getBuiltinModule`, `zlib/promises`, unrestricted network/native
handles, or full Node stdlib parity still need later Desktop-profile work.

P13-44 records process/env/argv parity as partial. AutoJs6 Node exposes
`processParityProfile` diagnostics for controlled `process.env`, script-local
env writes, filtered sensitive env variables, scoped cwd/chdir, argv metadata,
`exitCode`, versions, timing/resource snapshots, permission/report facades, and
bounded stdio. Desktop packages that need raw `process.exit()`, signal
delivery, inherited host env, raw process handles, native bindings,
`process.dlopen`, or report file writes still need later Desktop-profile work.

P13-45 records packaged APK capability metadata as partial. AutoJs6 Node
exposes `packagedCapabilityProfile` and typed
`bridgePermissions.packagedMetadata` diagnostics for packaged profile,
permissions, builtins, native assets, filesystem roots, execution mode, and
network intent. These fields do not yet grant Pro/Desktop authority; they
prepare reviewed packaged synthesis while undeclared bridge capabilities still
reject before dispatch.

P13-46 records plugin system parity as partial. AutoJs6 Node exposes
`pluginSystemProfile` diagnostics for the JS-only local plugin manager,
install/load/lifecycle operations, host capability ceiling, packaged local-file
status, and debug-only reload. Android provider plugins, native plugins,
signed archives, remote marketplace install/update, packaged plugin
distribution, and plugin-granted host authority remain unavailable.

P13-47 records dependency compatibility corpus as partial. AutoJs6 Node now
classifies the fixed real npm corpus with `phase13Compatibility` package reason
categories for `missing_builtin`, `native_addon`, `postinstall`, `network`,
and `fs`; the differential report can show pass-rate rows by category when
AutoJs6 results are supplied. Unsafe registry packages, native payloads,
lifecycle scripts, and postinstall downloads remain blocked.

P13-48 records package lock and integrity policy as partial. AutoJs6 Node
exposes `packageIntegrityProfile` diagnostics for shared lockfile/hash/signature
policy across Android package manager, plugin packages, and native addon
artifacts. Android local-store rollback/prune/corruption recovery is covered,
but signed plugin archives, signed native artifacts, raw registry install,
package-manager JavaScript facades, and unverified cache reuse remain
unavailable.

P13-49 records profile selection UX and diagnostics as partial. AutoJs6 Node
exposes `profileSelection` diagnostics through `autojs6:profile` and Node
Doctor so Pro/AutoX migrations can see requested profiles, declared high-risk
capabilities, Android permission synthesis, and blocked promotions. Missing
bridge capability errors now report the required profile, Node capability, and
Android permissions, but effective authority remains `safe_default`.

P13-50 records relaxed-capability security corpus as partial. AutoJs6 Node now
tracks raw network, filesystem relaxation, Java interop, `child_process`,
native addon loading, inspector, and WASI in
`RELAXED_CAPABILITY_SECURITY_CORPUS.json`, with positive tests, negative tests,
abuse cases, CI evidence, and rollback posture. This helps Pro/AutoX migration
planning without enabling raw sockets, expanded roots, raw process/native
handles, raw WASI, inspector, native addons, or release Java interop.

P13-36 records `vm` module promotion as partial. AutoJs6 Node exposes
`vmProfile` diagnostics, but `vm`, `node:vm`, `vm.Script`, contexts,
`compileFunction`, and VM module APIs remain unavailable. Packages that require
Node's VM APIs still need a future Desktop/Debug profile with timeout, memory,
escape-denial, and packaged behavior evidence.

P13-37 records inspector/devtools debug profile as partial. AutoJs6 Node
exposes `inspectorProfile` diagnostics, but `inspector`, `node:inspector`,
DevTools attach, debugger URLs, CPU profiles, heap snapshots, and inspector
profiling exports remain unavailable. Scripts that depend on desktop debugging
workflows still need a future debug-only profile with explicit user action,
loopback ADB forwarding, artifact redaction, and packaged behavior evidence.

P13-38 records controlled WASI profile as partial. AutoJs6 Node exposes
`wasiProfile` diagnostics, and pure WebAssembly remains available, but raw
`wasi`, `node:wasi`, controlled preopens, WASI fd/env/args surfaces, and
packaged WASI behavior remain unavailable. Packages that require desktop WASI
still need a future controlled profile with scoped or user-authorized roots,
resource budgets, and escape-denial evidence.

Porting note: migrate one-shot disposable task cases to `work_manager` or
`compat.tasks`. Keep Rhino scripts that depend on daily/weekly user task rows,
IntentTask triggers, or raw task object mutation on Rhino until a dedicated
Pro-profile task provider lands.

## Java And Android Interop

Auto.js Pro 9 public docs describe `$autojs.java` and a `rhino` module for Java/Android interop.

AutoJs6 behavior:

- direct Java/Android object access is not part of the default Safe Node Profile.
- P13-12 provides `$autojs` global v1 as a read-only diagnostic and gated entry
  surface: `$autojs.androidContext` is not a raw `Context`, and `$autojs.java`
  rejects by default unless the Java interop experiment is enabled.
- P13-13 extends the experiment with allowlist-only `java.lang.Math`,
  `java.util.UUID`, and `android.graphics.Rect` handle construction, selected
  instance calls, field reads, and explicit release/dispose cleanup.
- P13-14 adds an explicit `rhino.install({ experimental: true })` Java proxy
  POC: `Packages`, `java`, and `android` can be installed only when the Java
  interop experiment is enabled, and all calls still route through the same
  allowlist. Default `rhino.install()` remains rejected.
- P13-15 keeps Rhino `importClass` / `importPackage` semantics unsupported in
  Node. Explicit `require("rhino").importClass()` / `.importPackage()` calls
  throw `ERR_AUTOJS6_RHINO_IMPORT_UNSUPPORTED`; assign local names from a
  P13-14 proxy target instead.
- P13-16 keeps Rhino `JavaAdapter` unsupported in Node. Explicit
  `require("rhino").JavaAdapter()` calls throw
  `ERR_AUTOJS6_RHINO_JAVA_ADAPTER_UNSUPPORTED`; `java.defineClass()` and
  `$autojs.java.defineClass()` are reserved replacement names but still throw
  `ERR_AUTOJS6_JAVA_METHOD_DENIED` until a bounded proxy-interface provider is
  implemented.
- AutoJs capabilities should be requested through bridge modules.
- raw Android services, activities, accessibility nodes, media projection objects, and bitmap pointers are not exposed directly.

Porting note: treat `$autojs global v1`, `java.*`, `android.*`,
`importClass`, `JavaAdapter`, `defineClass`, and Rhino-style Java access as gated migration work. Prefer
explicit bridge APIs where available.

## Threads

Auto.js Pro 9 public docs describe Node's event-loop model, state that old `threads` style is not available in Node scripts, and describe `worker_threads` with restrictions around Auto.js APIs.

AutoX.js v7 public docs also describe replacing Node-engine multithreading with Promise and asynchronous functions.

AutoJs6 behavior:

- Rhino `threads` APIs are not available in Node scripts.
- `worker_threads` is disabled by default.
- background work should use Promise-based APIs, bridge providers, or future design-gated task APIs.

Porting note: convert threaded Rhino code to `async` / `await` and bridge calls.

## Network

Auto.js Pro 9 public docs list `http` and `https` as usable Node builtins.

AutoJs6 behavior:

- raw Node `http`, `https`, `net`, `tls`, `dns`, and `dgram` remain denied by default.
- the controlled `fetch` direction is documented separately and remains feature-gated.
- packaged APK INTERNET permission is only expected when controlled network is explicitly enabled.

Porting note: do not assume Node socket or HTTP builtins are available in AutoJs6. Use the controlled network design when enabled.

## Packaged APKs

AutoJs6 packaged Node APK support is a first-class target, but it is tied to the Safe Node Profile:

- native libraries must be present for selected ABIs.
- `NativeNodeEmbeddedScriptService` must be declared.
- foreground service permissions and `specialUse` metadata are verified.
- module sources and encrypted Node entries are checked by packaged diagnostics.
- `nodeFull`, `nodeMini`, and `rhinoOnly` release flavor policy is enforced.

Porting note: a Pro/AutoX Node script that runs in its original environment may still fail packaged AutoJs6 verification if it depends on unsupported builtins, unrestricted fs, native addons, raw network, full desktop ESM loader behavior, or dynamic import.

## Quick Porting Matrix

| Behavior | Auto.js Pro 9 docs | AutoX.js v7 docs | AutoJs6 Safe Node Profile |
| --- | --- | --- | --- |
| Default `.js` engine | Rhino | Rhino | Rhino compatibility preserved |
| Node directive | `"nodejs";` | suffix-focused docs | `"nodejs";` / `"node";` |
| `.mjs` | Node + ESM | Node + ESM | Partial ESM |
| Main module style | `require()` | `import` | `require()` |
| Dynamic import | not the AutoJs6 target | documented as unsupported | disabled by default |
| npm install | documented user workflow | not the AutoJs6 target | unsupported |
| Pure-JS CJS packages | broad npm-oriented docs | not equivalent | supported target |
| Scoped fs | not described as AutoJs6 policy | not equivalent | enforced |
| Raw `http`/`https` | documented as usable | not equivalent | disabled by default |
| `worker_threads` | documented with Auto.js API restrictions | no old-style multithreading | disabled by default |
| `tasks` / user task database | timed task database APIs | compatibility varies | P13-21 partial: `work_manager` and one-shot `compat.tasks` only |
| Direct Java/Android access | `$autojs` / `rhino` docs | Java Promise direction | bridge modules only by default |
| Packaged APK Node runtime | product-specific | product-specific | verified Safe Node Profile |

## Recommended Adaptation Steps

1. Convert entry files to `.cjs` or add `"nodejs";`.
2. Convert ESM/import-first code to CommonJS `require()`.
3. Vendor pure-JS dependencies instead of relying on `npm install`.
4. Replace absolute filesystem paths with scoped project-relative paths.
5. Replace raw network modules with controlled network APIs only when explicitly enabled.
6. Replace direct Java/Android access with AutoJs6 bridge modules.
7. Replace threads with Promise-based control flow.
8. Run `NodeSecurityRegressionInstrumentationTest` when changing compatibility assumptions.
9. Run packaged APK diagnostics when packaging adapted scripts.
