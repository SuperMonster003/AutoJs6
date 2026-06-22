# Migrating Rhino Scripts To Embedded Node

This guide explains how existing AutoJs6 Rhino scripts should move to the Embedded Node runtime. Node support is a second engine with a different compatibility profile; it is not a drop-in replacement for Rhino globals.

If you are adapting scripts from Auto.js Pro 9 or AutoX.js v7, also read [COMPATIBILITY_NOTES_PRO_AUTOX.md](COMPATIBILITY_NOTES_PRO_AUTOX.md).
For the current v1.1 Node feature status, read [NODE_PROFILE_V1_1_USER_DRAFT.md](NODE_PROFILE_V1_1_USER_DRAFT.md).

## Entry Points

Use a Node directive at the top of a script:

```js
"nodejs";

console.log(process.version);
```

Or use a Node-oriented file suffix:

```text
main.node.js
main.cjs
main.mjs
```

`.mjs` currently routes to the Node backend and runs through the Node Profile v1.1 partial ESM path. Use CommonJS (`require`) when you need the widest compatibility; partial ESM supports local static imports, selected builtin imports, and ESM-to-CJS interop, but not the full desktop Node loader.

For projects, prefer:

```json
{
  "type": "node",
  "main": "main.cjs",
  "node": {
    "backend": "embedded",
    "timeoutMs": 30000
  }
}
```

Rhino projects should keep their Rhino project type and should not add the Node directive accidentally.

## Mental Model Changes

Rhino scripts historically rely on many global AutoJs APIs. Node scripts should import capability modules:

```js
"nodejs";

const { showToast } = require("toast");
const app = require("app");
```

Android and AutoJs APIs are asynchronous from JavaScript. Use `async` / `await`:

```js
"nodejs";

(async function main() {
  const { showToast } = require("toast");
  await showToast("done");
})();
```

The Node thread must not block waiting for Android UI, accessibility, media projection, shell, or image work. Those calls go through bridge providers and return Promises.

## Globals And Java Access

Common Node globals such as `console`, `process`, `Buffer`, timers, and CommonJS `require` are available within the Safe Node Profile.

Do not assume Rhino globals are present:

- no direct global `toast()`
- no direct global `files`
- no direct global selector DSL unless `require("autojs6:compat").install({ globals: true })` is called explicitly
- no direct global `images`
- no direct Java object access by default
- no `importClass`, `importPackage`, or Rhino Java bridge globals

If a script needs Android or AutoJs functionality, use an AutoJs bridge module.

## Rhino Compatibility Shim

There is no `require("rhino")` compatibility module in the default Safe Node Profile, and Node does not support `rhino.install()` or automatic installation of old AutoJs globals. The explicit migration helper is `require("autojs6:compat")`; it does not install globals unless the script calls `compat.install({ globals: true })`.

Do not migrate scripts by expecting these Rhino-era surfaces to appear inside Node:

- `importClass`
- `importPackage`
- `Packages`
- `JavaAdapter`
- direct `java.*`
- direct `android.*`
- old global AutoJs APIs such as `toast`, `files`, `images`, selector globals, or `threads`

The recommended migration path is to rewrite those calls to explicit Node bridge modules. For low-risk helpers, `autojs6:compat` currently covers toast/sleep/clipboard, scoped files, pure/text utilities, storage/sqlite, readable `console`/timer debugging migration, the first selector/accessibility compatibility facade, and common app/device/shell wrappers. `console.show()` and related floating-console controls are no-op output-capture shims in Node; logs still go to captured stdout/stderr, and uncaught errors keep source-map-aware `errorStack` output. If a legacy Rhino script must remain Rhino, keep it on the Rhino engine or dispatch it explicitly through the script manager rather than mixing unrestricted Rhino globals into Node.

The design gate for broader Rhino interop is documented in [RHINO_SHIM.md](design/RHINO_SHIM.md). Its recommendation is that any future shim should be explicit, isolated, JSON-serialized, timeout-bounded, and used only for migration.

## Modules Instead Of Globals

Common bridge modules:

```js
const { showToast } = require("toast");
const app = require("app");
const dialogs = require("dialogs");
const engines = require("engines");
const accessibility = require("accessibility");
const image = require("image");
const clipboard = require("clipboard");
const device = require("device");
const shell = require("shell");
```

Use Node builtins only within the supported profile. Raw `child_process`, native addons, unrestricted network modules, and unrestricted filesystem access remain disabled by default.

## TypeScript Declarations

AutoJs6 Node bridge declarations are checked in under [types/autojs6-node](types/autojs6-node). They cover the Safe Node
Profile bridge modules and controlled wrappers, not full desktop Node.js or Rhino globals.

Use the declarations from a TypeScript project by including them in `tsconfig.json`:

```json
{
  "include": [
    "src/**/*.ts",
    "path/to/AutoJs6/docs/nodejs/types/autojs6-node/**/*.d.ts"
  ]
}
```

The sample project [../sample/nodejs/typescript-smoke](../../sample/nodejs/typescript-smoke) exercises the declarations.
From the repository root:

```powershell
tsc -p sample/nodejs/typescript-smoke/tsconfig.json --pretty false
```

## Shell Commands

Node `child_process` is not available in the Safe Node Profile. Use the controlled `shell` bridge for simple command
execution:

```js
"nodejs";

const shell = require("shell");
const result = await shell.exec("echo ok", { timeoutMs: 1000 });
console.log(result.code, result.stdout.trim());
```

The S11-17 compatibility facade also supports common Rhino `shell()` migration:

```js
"nodejs";

const compat = require("autojs6:compat");
const result = await compat.shell("echo ok", { timeoutMs: 1000 });
console.log(result.code, result.stdout.trim());
```

Projects that use a bridge permission manifest must declare `shell`; root execution also requires `shell.root`.
`compat.shell("id", true)` is accepted as shorthand for `{ root: true }`, but root is never enabled implicitly.
Shizuku execution remains unsupported; `compat.shell.checkAccess({ shizuku: true })` reports the missing capability
instead of escalating. `spawn()`, `fork()`, streaming stdio, and interactive shells are not supported.

## Threads And Async Work

Rhino scripts may use older thread APIs. Node scripts should prefer:

- `Promise`
- `async` / `await`
- timers
- bridge APIs with timeout options

The `threads` module is not available in Node scripts, and Node `worker_threads` remains disabled by default.

Before:

```js
threads.start(function () {
  toast("background");
});
```

After:

```js
"nodejs";

(async function main() {
  const { showToast } = require("toast");
  await showToast("background");
})();
```

## Filesystem Migration

Rhino scripts often use AutoJs `files` helpers and absolute Android paths. Node scripts use scoped `fs` or `fs/promises`. Paths are relative to the working directory or project root. Absolute paths, `..` escapes, NUL bytes, `/proc`, `/sys`, `/dev`, symlink escapes, and realpath escapes are denied.

Before:

```js
files.write("/sdcard/AutoJs6/out.txt", "hello");
let text = files.read("/sdcard/AutoJs6/out.txt");
toast(text);
```

After:

```js
"nodejs";

const fs = require("fs");
const { showToast } = require("toast");

fs.mkdirSync("out", { recursive: true });
fs.writeFileSync("out/message.txt", "hello", "utf8");
const text = fs.readFileSync("out/message.txt", "utf8");

(async () => {
  await showToast(text);
})();
```

Promise form:

```js
"nodejs";

(async function main() {
  const fsp = require("fs/promises");
  await fsp.mkdir("out", { recursive: true });
  await fsp.writeFile("out/message.txt", "hello", "utf8");
  console.log(await fsp.readFile("out/message.txt", "utf8"));
})();
```

## Before And After Examples

### Toast

Rhino:

```js
toast("Hello AutoJs6");
```

Node:

```js
"nodejs";

(async function main() {
  const { showToast } = require("toast");
  await showToast("Hello AutoJs6");
})();
```

### App Launch

Rhino:

```js
app.launchPackage("org.example.app");
```

Node:

```js
"nodejs";

(async function main() {
  const app = require("app");
  const launched = await app.launchPackage("org.example.app", { timeoutMs: 5000 });
  console.log("launched=" + launched);
})();
```

Node with the compatibility facade:

```js
"nodejs";

(async function main() {
  const compat = require("autojs6:compat");
  const launched = await compat.app.launchPackage("org.example.app", { timeoutMs: 5000 });
  console.log("launched=" + launched);
})();
```

### Click Text

Rhino:

```js
text("OK").findOne(3000).click();
```

Node with the S11-16 compatibility facade:

```js
"nodejs";

(async function main() {
  const compat = require("autojs6:compat");
  const node = await compat.text("OK").findOne(3000);
  const clicked = node !== null && await compat.text("OK").click({ timeoutMs: 3000 });
  console.log("clicked=" + clicked);
})();
```

Node with the direct bridge module:

```js
"nodejs";

(async function main() {
  const accessibility = require("accessibility");
  const clicked = await accessibility.clickText("OK", { timeoutMs: 3000 });
  console.log("clicked=" + clicked);
})();
```

Node accessibility selectors are JSON descriptors and returned nodes are snapshots. No raw `AccessibilityNodeInfo` objects cross the process boundary. The compatibility selector DSL is synchronous only while building a handle; `findOne(3000)`, `find()`, `click()`, `back()`, and `home()` return Promises and must be awaited.

### Screenshot And Find Image

Rhino:

```js
requestScreenCapture();
let capture = captureScreen();
let template = images.read("./template.png");
let point = images.findImage(capture, template);
toast(String(point));
```

Node:

```js
"nodejs";

(async function main() {
  const image = require("image");
  const { showToast } = require("toast");

  const capture = await image.captureScreen({ timeoutMs: 5000 });
  const template = await image.readImage("template.png", { timeoutMs: 1000 });
  const point = await image.findImage(capture, template, { threshold: 0.9 });

  await image.recycle(template);
  await image.recycle(capture);
  await showToast(JSON.stringify(point));
})();
```

Image values are handles owned by the current execution. They are recycled explicitly or when the service is destroyed.

### File Read And Write

Rhino:

```js
files.createWithDirs("/sdcard/AutoJs6/data/value.txt");
files.write("/sdcard/AutoJs6/data/value.txt", "42");
log(files.read("/sdcard/AutoJs6/data/value.txt"));
```

Node:

```js
"nodejs";

const fs = require("fs");

fs.mkdirSync("data", { recursive: true });
fs.writeFileSync("data/value.txt", "42", "utf8");
console.log(fs.readFileSync("data/value.txt", "utf8"));
```

## Packaged APK Differences

Packaged Node APKs must carry the Embedded Node runtime and service metadata:

- `libnode.so`
- `libautojs6-node.so`
- `libc++_shared.so`
- `NativeNodeEmbeddedScriptService` manifest entry
- foreground service permissions and `specialUse` metadata
- Node entry metadata and module sources

The packaged app package name and version come from the packaged APK, not the AutoJs6 host app.

Rhino-only packages can omit the Node runtime. Node-enabled packages should be checked with:

```powershell
.\gradlew.bat --console=plain :app:embeddedNodePackagedDiagnosticReport
```

For release flavors and ABI policy, see `docs/nodejs/design/RELEASE_FLAVORS.md`.

## Migration Checklist

- Add `"nodejs";` or use `.cjs` / `.node.js`.
- Replace Rhino globals with `require()` modules.
- Convert blocking Android/AutoJs calls to `await` bridge calls.
- Replace `threads` usage with Promise-based control flow.
- Replace `files` helpers with scoped `fs` or `fs/promises`.
- Keep file paths relative to the project working directory.
- Remove direct Java object access.
- Do not rely on `require("rhino")`, `rhino.install()`, `importClass`, `importPackage`, `Packages`, or `JavaAdapter` inside Node.
- Confirm unsupported builtins still fail with `ERR_AUTOJS6_*`.
- Run examples under `sample/nodejs/` for reference.
- Re-run packaged APK verification when the script is packaged.
