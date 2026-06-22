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

S11-08 installed the loader stub. S11-09 extends it with the first low-risk async facade, S11-10 adds the scoped synchronous `files` facade, S11-11 adds pure utility facades for APIs that do not require Android providers, S11-12 adds text utility facades with stable dependency-missing degradation, S11-13 adds async-compatible storage/sqlite facades over the controlled bridge, S11-14 stabilizes console/timer/error debugging migration, S11-16 adds the first selector/accessibility facade over the controlled `accessibility` bridge, S11-17 exposes app/device/shell compatibility wrappers, and S11-18 adds the first `images` compatibility facade over opaque image handles. It does not expose raw Java objects, Rhino `Scriptable`, Android `View`, `AccessibilityNodeInfo`, shell `Process` handles, raw `Bitmap`/`Mat`/`ImageWrapper` instances, or Pro 9 `require("rhino").install()` behavior.

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
compat.unsupported("files.open");
compat.blockedByDesign("importClass");
compat.assertSupported("toast");
```

`compat.install()` is intentionally explicit. Calling it with no options does not install globals. `compat.install({ globals: true })` installs the implemented names: `toast`, `toastLog`, `sleep`, `setClip`, `getClip`, `files`, `base64`, `colors`, `fmt`, `cvt`, `mime`, `nanoid`, `util`, `opencc`, `pinyin`, `pinyin4j`, `storages`, `sqlite`, `console`, `setTimeout`, `clearTimeout`, `setInterval`, `clearInterval`, `selector`, selector builders such as `text`, `desc`, `id`, accessibility action wrappers such as `click`, `back`, and `home`, plus `app`, `device`, callable `shell`, and `images`. Existing Node globals are not overwritten unless `overwrite: true` is passed.

The S11-09 facade is async. Rhino `sleep()` blocks the Rhino script thread; Node compat `sleep()` returns a Promise and must be awaited. Toast and clipboard helpers also return Promises because they cross the Android bridge.

The S11-10 `files` facade is synchronous and scoped to the script `workingDirectory`. It is also available as `require("files")`. `files.path()` resolves to a scoped absolute path, while file operations reject paths that escape the Safe Node Profile sandbox and append a migration hint. `files.open()` remains unsupported until a Node-safe file-handle wrapper is designed. `files.toFile()` is blocked by design because returning `java.io.File` would expose a raw Java object.

The S11-11 pure utility facade is synchronous and host-testable. It adds bare CommonJS modules `base64`, `colors`, `formatter`/`fmt`, `converter`/`cvt`, `mime`, and `nanoid`, plus the existing Node `util` module as the migration target for Rhino `util.inspect`. `colors.toColorStateList`, `colors.setPaintColor`, and `colors.themeColor` stay blocked by design because they would expose Android objects. The byte formatter/converter subset covers the Rhino `bytes` constants and common strict/loose calls; uncommon argument combinations remain a compatibility refinement area rather than Android bridge work.

The S11-12 text utility facade is also synchronous and host-testable. It adds bare CommonJS modules `opencc`, `pinyin`, and `pinyin4j`. `pinyin` and `pinyin4j` include a small built-in dictionary for smoke-testable common Chinese text and style options. Resource-backed paths such as OpenCC dictionary conversion, Jieba segmentation, full phrase dictionaries, and place-name dictionaries return stable `ERR_AUTOJS6_RHINO_COMPAT_UNSUPPORTED` errors until a Node-safe dictionary/resource provider is designed.

The S11-13 storage/sqlite facade is async-compatible. `storage` and `storages` remain scoped by package and working directory through the existing Node storage bridge; store names are part of every bridge call so independent stores stay isolated. Store-level `putSync`, `removeSync`, `clearSync`, and module-level `storages.removeSync()` aliases are available but return Promises in Node and must be awaited. `storages.names()` and `storages.all()` return stable `ERR_AUTOJS6_RHINO_COMPAT_UNSUPPORTED` errors because the controlled bridge does not expose namespace enumeration. `sqlite` is callable as an alias for `sqlite.open()` and returns the same controlled database handle as `database.open()`. Database paths stay scoped names, handles must be closed, and transaction callback form is rejected; transactions use explicit operation arrays.

The S11-14 console/timers/error facade is synchronous for console output and callback-based for timers. `require("console")`, `require("node:console")`, `compat.console`, and the installed `console` global expose Node-compatible `log`, `info`, `warn`, `error`, `assert`, `time`, `timeEnd`, `trace`, plus Rhino aliases such as `print`, `verbose`, `err`, and `clearConsole`. Floating-console window controls such as `show`, `hide`, `setSize`, and `setTitle` are no-op compatibility shims that return the console object so common debugging scripts continue to write readable output; they do not open or configure an Android floating console in Node. `console.input`, `console.rawInput`, and `console.build` remain stable unsupported paths. Standard `setTimeout`, `setInterval`, `clearTimeout`, and `clearInterval` come from the controlled Node timer implementation; Auto.js-specific `keepAlive`, `loop`, and `setIntervalExt` remain unsupported until a Node lifecycle design exists. Uncaught error envelopes continue to expose source-map-aware `errorStack` output through the existing source map diagnostics pipeline.

The S11-16 selector DSL facade is synchronous only while building a selector handle. `compat.text("OK").descContains("Confirm").clickable()` returns a frozen JavaScript handle with a frozen JSON descriptor; it is not a Rhino `UiSelector`, `UiObject`, Java object, or `AccessibilityNodeInfo`. Any call that queries or acts on Android accessibility returns a Promise: `findOne(timeout)`, `find()`, selector `click()`, `longClick()`, `setText()`, `scrollForward()`, `scrollBackward()`, coordinate `compat.click(x, y)`, `compat.back()`, and `compat.home()` must be awaited. `findOne(number)` is a Rhino timeout compatibility overload that maps to `{ timeoutMs: number }`; `find()` maps to `accessibility.findAll()`. Raw selector helpers such as `pickup()`, `detect()`, `existsAll()`, and `existsOne()` remain design-gated because they can imply live Rhino node handles or callback return values.

The S11-17 app/device/shell facade is also explicit and Promise-based for bridge calls. `compat.app` and `compat.device` are the existing controlled bridge modules, exposed so migrated scripts can keep the Rhino object names after opting into the compat loader. `compat.shell(command, optionsOrRoot)` is a callable wrapper around `require("shell").exec()`, with `true` normalized to `{ root: true }` for common Rhino scripts. Root shell execution is never the default and still requires the shell root capability when manifest enforcement is active. Shizuku remains unavailable in v1.1; `compat.shell.checkAccess({ shizuku: true })` reports it as missing rather than executing through a fallback.

The S11-18 images facade is Promise-based and handle-oriented. `compat.images.read()` maps to `images.readImage()`, `save()` maps to `saveImage()` and resolves `true` after the underlying save completes, region overloads such as `clip(image, [x, y, w, h])`, size overloads such as `resize(image, [w, h])`, numeric `threshold(image, threshold, maxValue, type)`, and Rhino region overloads for `findImage()`/`findColor()` are normalized before dispatch. Image values remain opaque bridge handles owned by the current execution; transforms create new handles that count against the image-handle limit until `compat.images.recycle()` is awaited. S11-18 does not add screenshot permission prompts: `requestScreenCapture()` and the Rhino global screenshot wrappers remain S11-19 work.

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

The checked-in type declaration is `docs/nodejs/types/autojs6-node/rhino_compat.d.ts`. It declares the loader, stable error helpers, and the implemented Phase 11 facade modules through S11-18.
