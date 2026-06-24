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
