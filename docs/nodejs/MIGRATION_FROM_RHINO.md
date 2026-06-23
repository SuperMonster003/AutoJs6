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

For checked migration pairs, see `docs/nodejs/examples/rhino-compat`. S11-27
covers toast/files/storage/selector/image/ocr/app/device/shell with old Rhino
snippets and Node `autojs6:compat` rewrites. P12-19 adds tasks migration
examples covering supported async disposable tasks, unsupported daily/weekly and
IntentTask rejections, and blocked raw task-object mutation. The examples are
statically verified by `verifyNodeRhinoCompatExamples`, and the tasks-specific
bucket report is verified by `verifyNodeRhinoCompatTasksMigrationExamples`.

S11-28 adds dual-engine compatibility fixtures in
`docs/nodejs/dual-engine-compat`. These pair Rhino snippets with Node
compat-mode snippets and separate `supported_async` cases from
`blocked_by_design` cases. Run `verifyNodeRhinoDualEngineCompatTests` to
produce the host report and device instrumentation plan evidence.

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

The design gate for broader Rhino interop is documented in [RHINO_SHIM.md](design/RHINO_SHIM.md). S11-26 narrows the only acceptable future shape to a JSON-only `rhino.run({ source, timeoutMs, args })` / scoped `path` API, documented in [RHINO_JSON_ONLY_SHIM.md](design/RHINO_JSON_ONLY_SHIM.md). This is a future POC plan, not a current runtime feature:

```js
"nodejs";

const rhino = require("rhino"); // not available in the current Safe Node Profile

const result = await rhino.run({
  source: "var input = arguments[0]; ({ value: input.value + 1 })",
  args: [{ value: 41 }],
  timeoutMs: 5000
});
```

If implemented later, `rhino.run()` must accept and return only JSON-safe values, must be timeout-bounded, and must not install Rhino globals into V8. It must reject Java objects, Rhino `Scriptable`, Android objects, functions, image/UI/accessibility handles, native pointers, threads, locks, process handles, cycles, `undefined`, `bigint`, symbols, and prototype-bearing objects at the engine boundary.

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
- timers and `timers/promises`
- `AbortController`
- bridge APIs with timeout options
- explicit `engines.execScriptFile()` / `engines.execScript()` slots for
  separate bounded executions
- `work_manager` for scheduled bounded work

The `threads` module is not available in Node scripts, and Node
`worker_threads` remains disabled by default. Do not migrate Rhino code by
expecting `require("threads")`, `compat.threads`, a global `threads`, a global
`sync`, or default `worker_threads` to exist.

`threads.start()`, `threads.pool()`, `threads.interrupt()`,
`threads.disposable()`, and `sync(fn, lock)` are all `blocked_by_design` as
exact Node compatibility targets. They depend on shared Rhino `ScriptRuntime`
state, raw Java `Thread` / `ThreadPoolExecutor` handles, Rhino `Synchronizer`,
Java locks, and blocking `VolatileDispose` waits. Node code must not block the
event loop or share mutable Rhino/Java objects across execution slots. See
[RHINO_THREADS_MIGRATION_POLICY.md](design/RHINO_THREADS_MIGRATION_POLICY.md).

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

Rhino `threads.disposable()` code that waits with `blockedGet()` should become
a Promise flow:

```js
"nodejs";

(async function main() {
  const fs = require("fs/promises");
  const value = await Promise.race([
    fs.readFile("value.txt", "utf8"),
    new Promise((_, reject) => setTimeout(() => reject(new Error("timeout")), 5000))
  ]);
  console.log(value);
})();
```

Rhino `sync(fn, lock)` should become an async serialized owner rather than a
shared lock:

```js
"nodejs";

function createQueue() {
  let tail = Promise.resolve();
  return function enqueue(task) {
    tail = tail.then(task, task);
    return tail;
  };
}

(async function main() {
  let count = 0;
  const enqueue = createQueue();
  await Promise.all([
    enqueue(() => { count += 1; }),
    enqueue(() => { count += 1; })
  ]);
  console.log(count);
})();
```

For work that really needs a separate execution slot, use a bounded script
entry or scheduled work. This is not a same-runtime thread:

```js
"nodejs";

(async function main() {
  const engines = require("engines");
  await engines.execScriptFile("jobs/cleanup.cjs", {
    args: { source: "main" },
    timeoutMs: 30000
  });
})();
```

Scheduled work should use `work_manager`:

```js
"nodejs";

(async function main() {
  const workManager = require("work_manager");
  await workManager.scheduleOnce({
    scriptPath: "jobs/cleanup.cjs",
    backend: "embedded",
    delayMs: 1000,
    taskTimeoutMs: 30000
  });
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

Node with the S11-19 compatibility facade:

```js
"nodejs";

(async function main() {
  const compat = require("autojs6:compat");

  await compat.requestScreenCapture(true);
  const capture = await compat.images.captureScreen({ timeoutMs: 5000 });
  const template = await compat.images.read("template.png", { timeoutMs: 1000 });
  const point = await compat.images.findImage(capture, template, 0, 0, 1080, 2400, 0.9);

  await compat.images.recycle(template);
  await compat.images.recycle(capture);
  console.log(JSON.stringify(point));
})();
```

Image values are handles owned by the current execution. They are recycled explicitly or when the service is destroyed. The S11-18 facade keeps Rhino names such as `images.read()` and `images.save()`, but bridge work is still asynchronous and must be awaited. S11-19 adds `requestScreenCapture()`, `requestScreenCaptureAsync()`, and `captureScreen()` compatibility wrappers; permission requests still go through the controlled Activity-owned `media_projection` provider, so denial or packaged fallback rejection is reported as a stable bridge error.

### OCR And Barcode

Rhino:

```js
requestScreenCapture();
let textRows = ocr.recognizeText(captureScreen(), [0, 0, 400, 200]);
let codes = qrcode.recognizeTexts(images.read("./qr.png"));
log(textRows.join("\n"));
```

Node with the S11-20 compatibility facade:

```js
"nodejs";

(async function main() {
  const compat = require("autojs6:compat");

  await compat.requestScreenCapture();
  const capture = await compat.captureScreen({ timeoutMs: 5000 });
  const textRows = await compat.ocr.recognizeText(capture, { region: [0, 0, 400, 200] });
  const pathRows = await compat.ocr("fixtures/ocr.png", { timeoutMs: 5000 });

  await compat.images.recycle(capture);
  console.log(textRows.concat(pathRows).join("\n"));
})();
```

`compat.ocr` is ML Kit-only in S11-20. It accepts an opaque image handle, a project-relative image path that is read and recycled after OCR completes, or no image argument to capture through the existing-permission screenshot path. `compat.ocr.recognizeText()` returns a string array like Rhino, while `compat.ocr.detect()` and `detectTextBounds()` return JSON-safe result snapshots. `ocr.tap("mlkit")` is accepted; Paddle/Rapid mode modules are not exposed in Node. `barcode` and `qrcode` remain a documented bridge gap until Node has a controlled barcode provider and JSON-safe barcode result contract.

### Key And Touch Events

Rhino:

```js
events.observeKey();
events.onKeyDown("volume_up", function(event) {
  toast("volume up");
});
events.setKeyInterceptionEnabled("volume_up", true);
events.observeTouch();
events.on("touch", function(point) {
  log(point.x + "," + point.y);
});
```

Node S11-21:

```js
"nodejs";

const { EventEmitter } = require("node:events");
const bus = new EventEmitter();
bus.on("ready", () => console.log("ready"));
bus.emit("ready");
```

`node:events` is only the pure JavaScript event-emitter replacement. It is not a Rhino `events` facade and does not observe Android keys or touch input. `events.__asEmitter__()` / `events.asEmitter()` is blocked by design because it depends on Rhino `ScriptableObject`, Java `EventEmitter`, `Thread`, and `MainThreadProxy` semantics.

`events.observeKey()`, `events.onKeyDown()`, `events.onKeyUp()`, `events.setKeyInterceptionEnabled()`, and `events.observeTouch()` remain provider gated. P12-05 exposes `require("input_observer")` with `source: "fake"` so scripts and tests can validate opaque subscription handles, drained JSON event snapshots, close behavior, and capability denial. P12-06 adds `source: "accessibility"` for live key observation when the AutoJs6 accessibility service is operational in the current process; scripts must still handle the stable unavailable error when that service state is missing. P12-07 keeps scheduled/background live observation, key interception, touch observation, shell/root fallback, and overlay capture rejected, so migrated scripts should close subscriptions explicitly and treat unavailable provider state as normal. The `events.keys` constants are available through `require("autojs6:compat").keys` and `require("autojs6:compat").events.keys`.

### Scheduled Tasks

Rhino disposable task:

```js
tasks.addDisposableTask({
  path: "./jobs/cleanup.js",
  date: new Date(Date.now() + 60 * 1000),
});
```

Node with the current WorkManager MVP:

```js
"nodejs";

(async function main() {
  const workManager = require("work_manager");
  const taskId = await workManager.scheduleOnce({
    scriptPath: "jobs/cleanup.cjs",
    backend: "embedded",
    delayMs: 60 * 1000,
    taskTimeoutMs: 30 * 1000,
  });
  console.log(taskId);
})();
```

`work_manager` is Promise-based and schedules scoped embedded Node entries only. It returns string task ids and JSON task snapshots; it does not return Rhino `TimedTask` or `IntentTask` objects.

P12-15 locks the `compat.tasks` v1 scope to disposable one-shot migration only,
and P12-16 exposes that facade:
`compat.tasks.addDisposableTask({ path, date|time|delayMs, ... })` wraps
`work_manager.scheduleOnce()`, `compat.tasks.removeTask(id)` wraps
`work_manager.cancel()`, and `compat.tasks.queryTasks(options?)` wraps
`work_manager.list()` for one-shot JSON snapshots. `require("tasks")` and global
`tasks` remain absent.

P12-17 adds Rhino-name compatibility aliases for the same Node-owned subset:
`compat.tasks.queryTimedTasks()`, `compat.tasks.getTimedTask(id)`, and
`compat.tasks.removeTimedTask(id)`. They operate only on JSON-safe
WorkManager-owned ids and snapshots created by Node. They do not read Rhino
`TimedTask` database rows.

Do not migrate `tasks.addDailyTask()` or `tasks.addWeeklyTask()` by mechanically calling `workManager.schedulePeriodic()`. Rhino daily/weekly tasks are wall-clock user tasks with next-run calculation, task UI/database state, and scheduler fallback semantics. WorkManager periodic work is OS-managed cadence with minimum interval and quota behavior. P12-17 keeps daily/weekly task compatibility rejected until a user-visible task database provider exists; the compatibility stubs reject with `ERR_AUTOJS6_RHINO_COMPAT_UNSUPPORTED`.

`tasks.addIntentTask()`, `queryIntentTasks()`, `getIntentTask()`, boot/startup/package/exact-alarm/custom broadcast triggers, and `removeIntentTask()` remain rejected in P12-18 with stable `ERR_AUTOJS6_RHINO_COMPAT_UNSUPPORTED` stubs. Node does not register Android receivers, mutate packaged manifests, expose raw trigger payloads, or grant custom broadcast authority. Raw `tasks.addTask(task)`, `removeTask(task)`, and `updateTask(task)` are blocked by design in Node because they depend on raw `TimedTask` / `IntentTask` Java objects. P12-17 exposes `compat.tasks.updateTask()` only as a stable `ERR_AUTOJS6_RHINO_COMPAT_BLOCKED_BY_DESIGN` rejection stub. See [RHINO_TASKS_WORK_MANAGER_MIGRATION.md](design/RHINO_TASKS_WORK_MANAGER_MIGRATION.md), [COMPAT_TASKS_V1.md](design/COMPAT_TASKS_V1.md), [TASKS_DAILY_WEEKLY_USER_DATABASE_DECISION.md](design/TASKS_DAILY_WEEKLY_USER_DATABASE_DECISION.md), and [TASKS_INTENT_BROADCAST_COMPATIBILITY_DECISION.md](design/TASKS_INTENT_BROADCAST_COMPATIBILITY_DECISION.md).

### UI Scripts

Rhino UI scripts mix XML layout declarations, direct view wrappers, and
main-thread helpers:

```js
"ui";

ui.layout(
  <vertical>
    <input id="name" hint="Name" />
    <button id="ok" text="OK" />
  </vertical>
);

ui.ok.click(() => {
  ui.run(() => {
    ui.ok.setText("Done");
  });
});
```

Node UI uses either the explicit `require("ui")` module or the constrained
`require("autojs6:compat").ui` facade. Both route to the same JSON-only
Activity-owned provider:

```js
"nodejs";

(async function main() {
  const ui = require("autojs6:compat").ui;

  const handle = await ui.showLayout({
    type: "Column",
    children: [
      { id: "name", type: "Input", hint: "Name" },
      { id: "ok", type: "Button", text: "OK" }
    ]
  }, { timeoutMs: 10000 });

  handle.on("click", async (event) => {
    if (event.id === "ok") {
      await handle.update({ id: "ok", text: "Done" });
      await handle.close();
    }
  });
})();
```

Do not migrate Rhino `ui.run()` or `ui.post()` by trying to execute Node
JavaScript on Android's UI thread. Node callbacks run on the Node event loop
after UI events are drained as JSON snapshots. Layout mutation goes through
`handle.update()` or `handle.batchUpdate()`, not through `NativeView`,
`View`, `ui.findView()`, `ui.root`, or dynamic view properties. P12-08 locks
the accepted JSON descriptor subset and fixture set in
[RHINO_UI_JSON_LAYOUT_SUBSET.md](design/RHINO_UI_JSON_LAYOUT_SUBSET.md); see
also [RHINO_UI_COMPATIBILITY.md](design/RHINO_UI_COMPATIBILITY.md).
`require("autojs6:compat")` still does not install a default global `ui`; a
global `ui` appears only after an explicit `compat.install({ globals: true })`
call and is the same constrained `compat.ui` JSON facade.

### Floaty And Overlay

Rhino floaty scripts create overlay windows and return live window/view
wrappers:

```js
const window = floaty.window(
  <vertical>
    <text id="status" text="Running" />
    <button id="stop" text="Stop" />
  </vertical>
);

window.stop.click(() => {
  window.close();
});
```

There is no current Node equivalent for `floaty.window()` or
`floaty.rawWindow()`. Do not migrate these APIs by expecting
`require("floaty")`, `compat.floaty`, or a global `floaty` to exist. P12-13
adds `require("ui.overlay")` as a fake-provider POC for JSON-only overlay
shape, capability, and handle-lifecycle checks. P12-14 adds the live gate for
permission snapshots, settings dry-run/launch evidence, packaged metadata, and
cleanup summaries. Production/live overlay support remains gated until
foreground disclosure, opaque window handles, event drain, and live
show/update/close behavior are implemented.

P12-11 selects the Provider path for a controlled JSON-only overlay provider,
and P12-12A locks the contract in
[OVERLAY_PROVIDER_V1.md](design/OVERLAY_PROVIDER_V1.md). Treat exact Rhino
`floaty.window()` and `floaty.rawWindow()` raw-wrapper semantics as a permanent
non-goal in Node; keep production scripts that need real overlay windows on
Rhino after the P12-14 live gate, because live overlay creation remains
fail-closed.

The current Node profile has only the P12-13 `require("ui.overlay")`
fake-provider POC. `require("floaty")`, `compat.floaty`, and global `floaty`
remain unavailable until an explicit compatibility facade task promotes them.
When a migration example needs a stable marker for the raw Rhino APIs, use
`compat.blockedByDesign("floaty.window", reason)` or
`compat.blockedByDesign("floaty.rawWindow", reason)` and check
`compat.errorCodes.blockedByDesign`
(`ERR_AUTOJS6_RHINO_COMPAT_BLOCKED_BY_DESIGN`).
For clipboard migration, `compat.getClip()` remains the Rhino-name facade over
the controlled clipboard bridge.

For in-app UI, use `require("ui").showLayout(...)`. For clipboard reads that
previously used `floaty.getClip()`, use the controlled clipboard bridge:

```js
"nodejs";

(async function main() {
  const clipboard = require("clipboard");
  console.log(await clipboard.getText());
})();
```

`floaty.window()` and `floaty.rawWindow()` are blocked as exact Node
compatibility targets because they depend on XML inflation, `FloatyService`,
`WindowManager.LayoutParams`, live `JsWindow` wrappers, and raw `View` /
`NativeView` lookup. See
[RHINO_FLOATY_COMPATIBILITY.md](design/RHINO_FLOATY_COMPATIBILITY.md).

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
- Review `docs/nodejs/examples/rhino-compat` for checked
  toast/files/storage/selector/image/ocr/app/device/shell/tasks migration pairs.
- Review `docs/nodejs/dual-engine-compat` for S11-28 dual-engine fixtures that
  distinguish `supported_async` from `blocked_by_design`.
- Run examples under `sample/nodejs/` for reference.
- Re-run packaged APK verification when the script is packaged.
