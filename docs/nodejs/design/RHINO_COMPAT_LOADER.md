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

S11-08 installed the loader stub. S11-09 extends it with the first low-risk async facade, S11-10 adds the scoped synchronous `files` facade, S11-11 adds pure utility facades for APIs that do not require Android providers, and S11-12 adds text utility facades with stable dependency-missing degradation. It does not expose raw Java objects, Rhino `Scriptable`, Android `View`, `AccessibilityNodeInfo`, or Pro 9 `require("rhino").install()` behavior.

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
compat.unsupported("files.open");
compat.blockedByDesign("importClass");
compat.assertSupported("toast");
```

`compat.install()` is intentionally explicit. Calling it with no options does not install globals. `compat.install({ globals: true })` installs the implemented low-risk names: `toast`, `toastLog`, `sleep`, `setClip`, `getClip`, `files`, `base64`, `colors`, `fmt`, `cvt`, `mime`, `nanoid`, `util`, `opencc`, `pinyin`, and `pinyin4j`.

The S11-09 facade is async. Rhino `sleep()` blocks the Rhino script thread; Node compat `sleep()` returns a Promise and must be awaited. Toast and clipboard helpers also return Promises because they cross the Android bridge.

The S11-10 `files` facade is synchronous and scoped to the script `workingDirectory`. It is also available as `require("files")`. `files.path()` resolves to a scoped absolute path, while file operations reject paths that escape the Safe Node Profile sandbox and append a migration hint. `files.open()` remains unsupported until a Node-safe file-handle wrapper is designed. `files.toFile()` is blocked by design because returning `java.io.File` would expose a raw Java object.

The S11-11 pure utility facade is synchronous and host-testable. It adds bare CommonJS modules `base64`, `colors`, `formatter`/`fmt`, `converter`/`cvt`, `mime`, and `nanoid`, plus the existing Node `util` module as the migration target for Rhino `util.inspect`. `colors.toColorStateList`, `colors.setPaintColor`, and `colors.themeColor` stay blocked by design because they would expose Android objects. The byte formatter/converter subset covers the Rhino `bytes` constants and common strict/loose calls; uncommon argument combinations remain a compatibility refinement area rather than Android bridge work.

The S11-12 text utility facade is also synchronous and host-testable. It adds bare CommonJS modules `opencc`, `pinyin`, and `pinyin4j`. `pinyin` and `pinyin4j` include a small built-in dictionary for smoke-testable common Chinese text and style options. Resource-backed paths such as OpenCC dictionary conversion, Jieba segmentation, full phrase dictionaries, and place-name dictionaries return stable `ERR_AUTOJS6_RHINO_COMPAT_UNSUPPORTED` errors until a Node-safe dictionary/resource provider is designed.

Future tasks should extend the module in small commits:

- S11-09: `toast`, `toastLog`, `sleep`, `setClip`, `getClip`, and basic globals.
- S11-10: scoped `files` facade with `read`, `write`, `exists`, `listDir`, `join`, `path`, `remove`, `copy`, and `move`.
- S11-11: pure utility facades for `base64`, `colors`, `fmt.bytes`, `cvt.bytes`, `mime`, `nanoid`, and `util.inspect` migration.
- S11-12: text utility facades for `opencc`, `pinyin`, and `pinyin4j`, with stable unsupported errors for missing dictionaries/resources.
- S11-13 to S11-14: storage/sqlite and console/timers/error compatibility.

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

The checked-in type declaration is `docs/nodejs/types/autojs6-node/rhino_compat.d.ts`. It declares only the loader and error helpers. Real facade methods should be added with the Task that implements them.
