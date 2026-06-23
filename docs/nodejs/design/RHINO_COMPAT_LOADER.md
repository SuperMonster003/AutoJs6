# Rhino Compatibility Loader

## Decision

Phase 11 Rhino compatibility uses one explicit Node entry:

```js
const compat = require("autojs6:compat");
```

The loader is an AutoJs6 controlled module, not an npm package name and not a script directive. It is resolved by the embedded Safe Node Profile loader in the same family as `autojs6:bridge` and `autojs6:lifecycle`.

## Rejected Entrypoints

| Entrypoint | Decision | Reason |
| --- | --- | --- |
| `"nodejs compat"` directive | Rejected for Phase 11A | Directives are too implicit and make source scanning/package behavior harder to audit. |
| `require("rhino-compat")` | Reserved | The bare name can collide with user dependencies and suggests ordinary npm resolution. |
| default Rhino globals | Rejected | It would silently change Node global state and make migration failures harder to diagnose. |
| `require("autojs6:compat")` | Selected | The `autojs6:` namespace is already reserved for controlled runtime modules and makes opt-in explicit. |

## Runtime Contract

S11-08 installed the loader stub. S11-09 extends it with the first low-risk async facade, S11-10 adds the scoped synchronous `files` facade, S11-11 adds pure utility facades for APIs that do not require Android providers, S11-12 adds text utility facades with stable dependency-missing degradation, S11-13 adds async-compatible storage/sqlite facades over the controlled bridge, S11-14 stabilizes console/timer/error debugging migration, S11-16 adds the first selector/accessibility facade over the controlled `accessibility` bridge, S11-17 exposes app/device/shell compatibility wrappers, S11-18 adds the first `images` compatibility facade over opaque image handles, S11-19 adds screenshot permission compatibility wrappers over `media_projection` and `images`, S11-20 adds an ML Kit-only OCR compatibility facade over `ocr`/`images`, S11-21 records the events/keys/input observer design gate without adding runtime observer APIs, S11-22 records the Rhino `tasks` to `work_manager` migration design, P12-16 adds `compat.tasks` as a module-only disposable facade over `work_manager`, P12-17 adds stable daily/weekly rejection stubs and Rhino-name aliases for Node-owned timed task snapshots, P12-18 adds stable IntentTask/broadcast rejection stubs, S11-23 records the Rhino `ui` compatibility design without adding `compat.ui`, S11-24 records the Rhino `floaty` overlay compatibility design without adding a floaty facade, S11-25 records the Rhino `threads` migration policy without adding a threads facade, S11-26 records the explicit Rhino JSON-only shim design, P12-23 establishes its POC gate, P12-24 reserves `require("rhino")` as a disabled default-denial facade, and P12-25 adds the explicit `experimental: true` Rhino JSON-only execution adapter POC outside `autojs6:compat`. It does not expose raw Java objects, Rhino `Scriptable`, Android `View`, `AccessibilityNodeInfo`, shell `Process` handles, raw `MediaProjection`, raw OCR engines, raw `Bitmap`/`Mat`/`ImageWrapper` instances, Rhino `events.__asEmitter__()`, Java `EventEmitter`, `Thread`, `MainThreadProxy`, raw key/touch listeners, raw `TimedTask`/`IntentTask` objects, Rhino `NativeView`, Android `Activity`/`Context`/`Window` objects, widget registries, main-thread `ui.run()` / `ui.post()` execution, raw `WindowManager`/`LayoutParams`/`FloatyService`/`FloatyWindow` handles, hidden overlay windows, shared Rhino `ScriptRuntime` thread scopes, raw `ThreadPoolExecutor`, Rhino `Synchronizer`, Java locks, `VolatileDispose` blocking waits, raw `BroadcastReceiver`/`Intent`/`IntentFilter`/`PendingIntent` objects, default `worker_threads`, `require("tasks")`, global `tasks`, default executable `rhino.run()` without the explicit gate, supported `rhino.install()`, `compat.rhino`, or Pro 9 `require("rhino").install()` behavior.

The first runtime shape is:

```js
const compat = require("autojs6:compat");

compat.status();
compat.install({ globals: false });
await compat.toast("hello");
await compat.toastLog("hello");
await compat.sleep(100);
await compat.setClip("text");
const text = await compat.getClip();
compat.files.write("logs/a.txt", "hello");
const body = compat.files.read("logs/a.txt");
const encoded = compat.base64.encode(body);
const color = compat.colors.rgb(1, 2, 3);
const size = compat.fmt.bytes(1536);
const bytes = compat.cvt.bytes("1 KB", "B");
const parsed = compat.mime("text/plain; charset=utf-8");
const id = compat.nanoid();
const inspected = compat.util.inspect({ parsed, id });
const py = compat.pinyin.simple("\u4f60\u597d");
const py4j = compat.pinyin4j.of("\u4e2d\u56fd", { separator: " " });
compat.opencc.s2t("\u6c49\u5b57"); // stable unsupported until OpenCC dictionaries are available.
const store = compat.storages.create("settings");
await store.put("enabled", true);
const db = await compat.sqlite("settings_db");
await db.transaction([{ type: "exec", sql: "create table if not exists kv (k text)" }]);
await db.close();
compat.console.show().setTitle("Debug");
compat.console.log("debug", { ok: true });
const timeout = compat.setTimeout(() => compat.console.info("tick"), 10);
compat.clearTimeout(timeout);
const ok = compat.text("OK").descContains("Confirm").clickable();
const node = await ok.findOne(3000);
const nodes = await ok.find({ timeoutMs: 3000 });
const clicked = await ok.click({ timeoutMs: 3000 });
await compat.click(1, 2, { timeoutMs: 3000 });
await compat.back();
await compat.home();
const launched = await compat.app.launchPackage("org.example.app", { timeoutMs: 5000 });
const screenOn = await compat.device.isScreenOn({ timeoutMs: 1000 });
await compat.device.vibrate(50, { timeoutMs: 1000 });
const shellResult = await compat.shell("echo ok", { timeoutMs: 1000 });
const rootResult = await compat.shell("id", true);
const template = await compat.images.read("template.png", { timeoutMs: 1000 });
const clipped = await compat.images.clip(template, [0, 0, 20, 20], { timeoutMs: 1000 });
const binary = await compat.images.threshold(clipped, 128, 255, "BINARY");
await compat.images.save(binary, "out/template-binary.png", "png", 90);
await compat.images.recycle(binary);
await compat.images.recycle(clipped);
await compat.images.recycle(template);
await compat.requestScreenCapture(true);
const screen = await compat.captureScreen({ timeoutMs: 5000 });
const words = await compat.ocr.recognizeText(screen, { region: [0, 0, 300, 120] });
await compat.images.recycle(screen);
compat.unsupported("files.open");
compat.blockedByDesign("importClass");
compat.assertSupported("toast");
```

`compat.install()` is intentionally explicit. Calling it with no options does not install globals. `compat.install({ globals: true })` installs the implemented names: `toast`, `toastLog`, `sleep`, `setClip`, `getClip`, `files`, `base64`, `colors`, `fmt`, `cvt`, `mime`, `nanoid`, `util`, `opencc`, `pinyin`, `pinyin4j`, `storages`, `sqlite`, `console`, `setTimeout`, `clearTimeout`, `setInterval`, `clearInterval`, `selector`, selector builders such as `text`, `desc`, `id`, accessibility action wrappers such as `click`, `back`, and `home`, plus `app`, `device`, callable `shell`, `images`, `requestScreenCapture`, `requestScreenCaptureAsync`, `captureScreen`, and callable `ocr`. Existing Node globals are not overwritten unless `overwrite: true` is passed.

The S11-09 facade is async. Rhino `sleep()` blocks the Rhino script thread; Node compat `sleep()` returns a Promise and must be awaited. Toast and clipboard helpers also return Promises because they cross the Android bridge.

The S11-10 `files` facade is synchronous and scoped to the script `workingDirectory`. It is also available as `require("files")`. `files.path()` resolves to a scoped absolute path, while file operations reject paths that escape the Safe Node Profile sandbox and append a migration hint. `files.open()` remains unsupported until a Node-safe file-handle wrapper is designed. `files.toFile()` is blocked by design because returning `java.io.File` would expose a raw Java object.

The S11-11 pure utility facade is synchronous and host-testable. It adds bare CommonJS modules `base64`, `colors`, `formatter`/`fmt`, `converter`/`cvt`, `mime`, and `nanoid`, plus the existing Node `util` module as the migration target for Rhino `util.inspect`. `colors.toColorStateList`, `colors.setPaintColor`, and `colors.themeColor` stay blocked by design because they would expose Android objects. The byte formatter/converter subset covers the Rhino `bytes` constants and common strict/loose calls; uncommon argument combinations remain a compatibility refinement area rather than Android bridge work.

The S11-12 text utility facade is also synchronous and host-testable. It adds bare CommonJS modules `opencc`, `pinyin`, and `pinyin4j`. `pinyin` and `pinyin4j` include a small built-in dictionary for smoke-testable common Chinese text and style options. Resource-backed paths such as OpenCC dictionary conversion, Jieba segmentation, full phrase dictionaries, and place-name dictionaries return stable `ERR_AUTOJS6_RHINO_COMPAT_UNSUPPORTED` errors until a Node-safe dictionary/resource provider is designed.

The S11-13 storage/sqlite facade is async-compatible. `storage` and `storages` remain scoped by package and working directory through the existing Node storage bridge; store names are part of every bridge call so independent stores stay isolated. Store-level `putSync`, `removeSync`, `clearSync`, and module-level `storages.removeSync()` aliases are available but return Promises in Node and must be awaited. `storages.names()` and `storages.all()` return stable `ERR_AUTOJS6_RHINO_COMPAT_UNSUPPORTED` errors because the controlled bridge does not expose namespace enumeration. `sqlite` is callable as an alias for `sqlite.open()` and returns the same controlled database handle as `database.open()`. Database paths stay scoped names, handles must be closed, and transaction callback form is rejected; transactions use explicit operation arrays.

The S11-14 console/timers/error facade is synchronous for console output and callback-based for timers. `require("console")`, `require("node:console")`, `compat.console`, and the installed `console` global expose Node-compatible `log`, `info`, `warn`, `error`, `assert`, `time`, `timeEnd`, `trace`, plus Rhino aliases such as `print`, `verbose`, `err`, and `clearConsole`. Floating-console window controls such as `show`, `hide`, `setSize`, and `setTitle` are no-op compatibility shims that return the console object so common debugging scripts continue to write readable output; they do not open or configure an Android floating console in Node. `console.input`, `console.rawInput`, and `console.build` remain stable unsupported paths. Standard `setTimeout`, `setInterval`, `clearTimeout`, and `clearInterval` come from the controlled Node timer implementation; Auto.js-specific `keepAlive`, `loop`, and `setIntervalExt` remain unsupported until a Node lifecycle design exists. Uncaught error envelopes continue to expose source-map-aware `errorStack` output through the existing source map diagnostics pipeline.

The S11-16 selector DSL facade is synchronous only while building a selector handle. `compat.text("OK").descContains("Confirm").clickable()` returns a frozen JavaScript handle with a frozen JSON descriptor; it is not a Rhino `UiSelector`, `UiObject`, Java object, or `AccessibilityNodeInfo`. Any call that queries or acts on Android accessibility returns a Promise: `findOne(timeout)`, `find()`, selector `click()`, `longClick()`, `setText()`, `scrollForward()`, `scrollBackward()`, coordinate `compat.click(x, y)`, `compat.back()`, and `compat.home()` must be awaited. `findOne(number)` is a Rhino timeout compatibility overload that maps to `{ timeoutMs: number }`; `find()` maps to `accessibility.findAll()`. Raw selector helpers such as `pickup()`, `detect()`, `existsAll()`, and `existsOne()` remain design-gated because they can imply live Rhino node handles or callback return values.

The S11-17 app/device/shell facade is also explicit and Promise-based for bridge calls. `compat.app` and `compat.device` are the existing controlled bridge modules, exposed so migrated scripts can keep the Rhino object names after opting into the compat loader. `compat.shell(command, optionsOrRoot)` is a callable wrapper around `require("shell").exec()`, with `true` normalized to `{ root: true }` for common Rhino scripts. Root shell execution is never the default and still requires the shell root capability when manifest enforcement is active. Shizuku remains unavailable in v1.1; `compat.shell.checkAccess({ shizuku: true })` reports it as missing rather than executing through a fallback.

The S11-18 images facade is Promise-based and handle-oriented. `compat.images.read()` maps to `images.readImage()`, `save()` maps to `saveImage()` and resolves `true` after the underlying save completes, region overloads such as `clip(image, [x, y, w, h])`, size overloads such as `resize(image, [w, h])`, numeric `threshold(image, threshold, maxValue, type)`, and Rhino region overloads for `findImage()`/`findColor()` are normalized before dispatch. Image values remain opaque bridge handles owned by the current execution; transforms create new handles that count against the image-handle limit until `compat.images.recycle()` is awaited.

The S11-19 screenshot facade is also Promise-based. `compat.requestScreenCapture(true)` maps Rhino's landscape boolean to `{ orientation: "landscape" }` and requests a controlled `media_projection` capturer; `false` maps to portrait, strings can pass `"portrait"`, `"landscape"`, or `"auto"`, and object options are forwarded. `compat.captureScreen()` and `compat.images.captureScreen()` capture through the controlled `images.captureScreen()` path with `requireExistingPermission: true`; they do not request permission from the embedded Node service process. `captureScreen(path, options)` saves the captured handle through `images.saveImage()` and recycles the temporary handle before resolving `true`. Permission denial, missing Activity-owned provider, and packaged fallback denial preserve the stable bridge error instead of returning a fake `false`.

The S11-20 OCR facade is Promise-based and ML Kit-only. `compat.ocr.recognizeText(imageOrPath, options)` returns a frozen string array to match Rhino's list-style `ocr.recognizeText()`, while `compat.ocr.detect()` and `detectTextBounds()` return frozen JSON-safe OCR result snapshots with optional bounds. Image input may be an opaque image handle, a project-relative path loaded through `images.readImage()` and recycled after the OCR call settles, or omitted to capture the screen through the existing-permission image path and recycle the temporary handle. `compat.ocr.tap("mlkit")` is accepted; Paddle/Rapid mode switching and raw mode modules remain blocked until safe Node providers exist. P12-27 locks the barcode provider v1 contract in `BARCODE_PROVIDER_V1.md` and `barcode_provider_v1.schema.json`; P12-28 exposes the direct `require("barcode")` provider with JSON-safe snapshots and provider-owned image handles. P12-29 adds `compat.barcode` and QR-only `compat.qrcode` wrappers over that provider.

S11-21 did not add `compat.events`, `compat.keys`, `events` globals, key observers, touch observers, or key interception wrappers. P12-03 promotes only the pure constant subset: `compat.keys` and `compat.events.keys` expose frozen JSON-safe Rhino `events.keys` aliases such as `home`, `back`, and `volume_down`. Explicit global installation may expose `keys`, but it still does not install an `events` global, override Node core `require("events")`, add key/touch observers, or promote `input_observer` through `autojs6:compat`. P12-04 locks the `input_observer` provider v1 contract, recorded as the input_observer provider v1 contract, but does not add `require("input_observer")`; P12-05 adds direct `require("input_observer")` fake-source POC with opaque subscription handles, bounded `drainEvents`, and explicit `close()`. P12-06 adds the direct `source: "accessibility"` live-source gate for operational AutoJs6 accessibility service instances. Rhino `events.__asEmitter__()` is blocked by design because it depends on Rhino `ScriptableObject`, Java `EventEmitter`, `Thread`, and `MainThreadProxy` behavior. Scripts that only need in-process JavaScript events should use `require("node:events").EventEmitter`. Touch observation and key interception remain gated behind later provider, foreground/user-visible, and cleanup gates.

S11-22 records the tasks migration design and P12-16 adds `compat.tasks` as a module-only disposable facade. `compat.tasks.addDisposableTask({ path, date|time|delayMs, ... })` maps to `work_manager.scheduleOnce()`, `compat.tasks.removeTask(id)` maps to `work_manager.cancel()`, and `compat.tasks.queryTasks(options?)` maps to `work_manager.list()` one-shot snapshots. It still does not add `require("tasks")` or a `tasks` global. P12-17 adds `queryTimedTasks()`, `getTimedTask(id)`, and `removeTimedTask(id)` aliases for Node-owned one-shot JSON snapshots only, plus `addDailyTask()` / `addWeeklyTask()` `ERR_AUTOJS6_RHINO_COMPAT_UNSUPPORTED` stubs and an `updateTask()` `ERR_AUTOJS6_RHINO_COMPAT_BLOCKED_BY_DESIGN` stub. P12-18 adds `addIntentTask()`, `queryIntentTasks()`, `getIntentTask(id)`, and `removeIntentTask(id)` `ERR_AUTOJS6_RHINO_COMPAT_UNSUPPORTED` stubs. The facade must not expose raw `TimedTask`/`IntentTask` objects, map daily/weekly wall-clock user tasks to ordinary WorkManager periodic cadence, dispatch rejected provider calls, register receivers, mutate manifests, grant custom broadcast authority, or create broadcast/startup/boot/package triggers without the separate task database and broadcast gates.

S11-23 did not add `compat.ui`, `require("rhino-ui")`, or a global `ui`. Current Node UI scripts can use `require("ui")` directly with `showLayout`, `UiHandle.on`, `update`, `batchUpdate`, `close`, and provider-drained JSON event snapshots. P12-08 locks the JSON-only descriptor subset, schema, and fixtures. P12-09 adds `require("autojs6:compat").ui` as a constrained wrapper over that same provider and allows `globalThis.ui` only after explicit `compat.install({ globals: true })`. The wrapper must not expose `NativeView`, Android `View`, Activity emitters, widget registries, `ui.run()`, or `ui.post()` as main-thread JavaScript execution.

S11-24 does not add `compat.floaty`, `require("floaty")`, `require("ui.overlay")`, or a `floaty` global. Current Node scripts that used Rhino `floaty.getClip()` should use `clipboard.getText()` or `compat.getClip()`. Overlay windows remain behind a future provider with explicit overlay permission checks, foreground/user-visible disclosure, packaged manifest policy, opaque handles, bounded event drain, and audited close paths.

S11-25 does not add `compat.threads`, `require("threads")`, a global `threads`, or a global `sync`. Current Node scripts should migrate Rhino `threads.start()` / `threads.pool()` to `async` / `await`, timeout-bounded bridge calls, explicit `engines` execution slots, or `work_manager` scheduled work. `threads.disposable()` and `sync(fn, lock)` remain blocked because they imply blocking waits, raw `VolatileDispose`, Rhino `Synchronizer`, Java locks, and shared mutable Rhino/Java objects on the Node event loop. `worker_threads` remains default-disabled and is not a Rhino thread replacement.

S11-26 records the JSON-only shim candidate as `rhino.run({ source, timeoutMs, args })` plus a scoped `path` variant, owned by the script manager or a dedicated compatibility process. It must accept and return only JSON-safe snapshots, reject Java objects, Rhino `Scriptable`, Android objects, functions, handles, native pointers, threads, locks, file descriptors, cycles, `undefined`, `bigint`, symbols, and prototype-bearing objects, and remain timeout-bounded. P12-23 establishes the default-disabled POC gate; execution requires an explicit experimental flag or test metadata. P12-24 reserves `require("rhino")` as a frozen disabled facade: `rhino.run()` without the gate rejects with `ERR_AUTOJS6_RHINO_SHIM_DISABLED`, `rhino.install()` throws `ERR_AUTOJS6_RHINO_INSTALL_UNSUPPORTED`, `require("node:rhino")` remains unavailable, local `node_modules/rhino` cannot shadow it, and no `compat.rhino` or Rhino globals are exposed. P12-25 implements `rhino.run({ experimental: true, source|path, timeoutMs, args })` as a JSON-only execution adapter POC and still exposes no `compat.rhino`. P12-26 records packaged metadata behavior as fail-closed: no packaged metadata shape is accepted in Phase 12, future metadata may authorize only JSON-only run, and packaged APK execution remains rejected.

Future tasks should extend the module in small commits:

- S11-09: `toast`, `toastLog`, `sleep`, `setClip`, `getClip`, and basic globals.
- S11-10: scoped `files` facade with `read`, `write`, `exists`, `listDir`, `join`, `path`, `remove`, `copy`, and `move`.
- S11-11: pure utility facades for `base64`, `colors`, `fmt.bytes`, `cvt.bytes`, `mime`, `nanoid`, and `util.inspect` migration.
- S11-12: text utility facades for `opencc`, `pinyin`, and `pinyin4j`, with stable unsupported errors for missing dictionaries/resources.
- S11-13: async-compatible `storages` and callable `sqlite` facades over the controlled storage/database bridge.
- S11-14: console/timers/error debugging compatibility with readable Node output and source-map-aware stack diagnostics.
- S11-16: selector DSL handle facade and core accessibility action wrappers over `accessibility`.
- S11-17: app/device/shell compatibility wrappers, including callable `shell(command, optionsOrRoot)`.
- S11-18: images compatibility for read/save/transform/search methods over opaque handles.
- S11-19: screenshot permission wrappers over `media_projection` and `images.captureScreen`.
- S11-20/P12-29: ML Kit-only OCR compatibility over `ocr` and `images`; P12-27 locks the JSON-safe barcode provider schema, P12-28 exposes direct `require("barcode")`, and P12-29 exposes `compat.barcode` plus QR-only `compat.qrcode`.
- S11-21/P12-03/P12-04/P12-05/P12-06: events/keys/input observer design gate, with only `compat.keys` and `compat.events.keys` pure constants promoted through `autojs6:compat`; the direct `input_observer` fake-source provider POC and accessibility live-source gate are available, while touch observation and key interception remain gated.
- S11-22/P12-15/P12-16/P12-17/P12-18: tasks to WorkManager/user task database migration design, `compat.tasks` v1 scope lock, disposable module-only facade, daily/weekly stable rejection stubs, Rhino-name aliases for Node-owned timed task snapshots, and IntentTask/broadcast stable rejection stubs; no `require("tasks")`, global `tasks`, daily/weekly provider, intent/broadcast provider, receiver registration, custom broadcast authority, or raw task object parity.
- S11-23/P12-08/P12-09: UI compatibility design gate, JSON layout subset, and constrained `compat.ui` wrapper; no raw Rhino UI globals, `NativeView`, `ui.run()`, or `ui.post()`.
- S11-24: floaty/overlay compatibility design gate; no runtime `compat.floaty`, `require("floaty")`, or `require("ui.overlay")` until overlay provider boundaries exist.
- S11-25: threads migration policy; no runtime `compat.threads`, `require("threads")`, global `threads`, global `sync`, or default `worker_threads`.
- S11-26/P12-23/P12-24/P12-25/P12-26: explicit Rhino JSON-only shim design and POC gate; P12-24 adds a disabled `require("rhino")` facade with stable default `rhino.run()` / `rhino.install()` denial, P12-25 adds the explicit `experimental: true` JSON-only execution adapter POC, and P12-26 records packaged metadata behavior as fail-closed. `compat.rhino`, Rhino globals, and packaged APK execution remain absent.

## Error Codes

The loader owns a stable Rhino compat error-code namespace:

| Code | Use |
| --- | --- |
| `ERR_AUTOJS6_RHINO_COMPAT_INVALID_OPTION` | Loader or facade options are malformed. |
| `ERR_AUTOJS6_RHINO_COMPAT_UNSUPPORTED` | A Rhino-style API is not implemented yet in Node compat mode. |
| `ERR_AUTOJS6_RHINO_COMPAT_BLOCKED_BY_DESIGN` | The requested API violates the Safe Node Profile boundary. |

All errors should include `module: "autojs6:compat"`. API-specific errors should also include `api`.

## Global Policy

Loading the module must not mutate `globalThis`. A script must make a second explicit call before any Rhino-style global can be installed. S11-08 verifies this by scanning the runtime stub for default global writes.

When later tasks add globals, they must keep these rules:

- no global install during `require("autojs6:compat")`;
- no raw Java/Rhino/Android object exposure;
- no aliases for `importClass`, `importPackage`, `JavaAdapter`, or unrestricted Java interop;
- unsupported or blocked APIs return stable `ERR_AUTOJS6_RHINO_COMPAT_*` errors with migration hints.

## Type Surface

The checked-in type declaration is `docs/nodejs/types/autojs6-node/rhino_compat.d.ts`. It declares the loader, stable error helpers, and the implemented Phase 11 facade modules through S11-20. P12-03 adds readonly `compat.keys` and `compat.events.keys` constants for the Rhino `events.keys` pure subset; S11-21 still adds no observer or `events` global type surface through `autojs6:compat`. P12-16 adds `compat.tasks` types for disposable WorkManager-backed add/remove/query only; it intentionally adds no `require("tasks")` type, global `tasks`, daily/weekly provider, intent/broadcast provider, raw task-object, or time-flag type surface. P12-17 extends the `compat.tasks` type with stable rejected `addDailyTask()` / `addWeeklyTask()` / `updateTask()` methods and Node-owned `queryTimedTasks()` / `getTimedTask()` / `removeTimedTask()` aliases. P12-18 extends it with stable rejected `addIntentTask()` / `queryIntentTasks()` / `getIntentTask()` / `removeIntentTask()` methods. S11-23 is also design-only and intentionally adds no `ui` compat type surface. S11-24 is design-only and intentionally adds no `floaty` or `ui.overlay` compat type surface. S11-25 is design-only and intentionally adds no `threads`, `sync`, or `worker_threads` compat type surface. P12-24 adds `docs/nodejs/types/autojs6-node/rhino.d.ts` for the disabled `require("rhino")` facade; P12-25 extends it with explicit `experimental: true` `RunRequest` / `RunResult` POC types. It still intentionally adds no supported `rhino.install` or `compat.rhino` type surface.
