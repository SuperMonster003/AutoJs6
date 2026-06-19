# Node Profile v1.1 User Draft

This draft is the user-facing map for Node Profile v1.1 features. It describes
what scripts can try today and what still requires a design gate, test provider,
or future runtime work.

For the exact compatibility boundary, read
[COMPATIBILITY_PROFILE.md](COMPATIBILITY_PROFILE.md). For the release target
matrix, read
[releases/NODE_PROFILE_V1_1_CAPABILITY_MATRIX.md](releases/NODE_PROFILE_V1_1_CAPABILITY_MATRIX.md).

## Status Legend

- `stable`: intended as a supported user surface within the Safe Node Profile.
- `partial`: usable subset with explicit limits; desktop Node or Rhino parity is
  not implied.
- `experimental`: available only behind explicit capability/feature gates or
  provider support; behavior may still change.
- `blocked`: documented shape only; normal user scripts should not rely on it.

## Stable Base

These v1 foundations remain the recommended path for production-like scripts:

- CommonJS entry scripts with `"nodejs";`, `.cjs`, or `.node.js`.
- Project metadata through `project.json`.
- Vendored pure JavaScript dependencies under `node_modules`.
- Scoped filesystem access rooted at the script working directory.
- Existing bridge modules such as `toast`, `app`, `clipboard`, and `device`
  when the matching capability is declared.

Example project permissions:

```json
{
  "type": "node",
  "main": "main.cjs",
  "node": {
    "backend": "embedded",
    "timeoutMs": 30000,
    "permissions": ["toast", "app"]
  }
}
```

When `node.permissions` is present, undeclared bridge calls reject before they
reach an Android provider.

## v1.1 Feature Status

| Feature | Status | User-facing note |
| --- | --- | --- |
| ESM scripts | partial | `.mjs`, `type=module`, local static imports, selected builtin imports, top-level await, ESM-to-CJS interop, package conditions, and local dynamic import are covered. Loader hooks and full desktop Node parity are not supported. |
| `fetch` / `axios` | experimental | Controlled bridge-backed HTTP shape. Requires `network` capability and a provider-enabled build; raw `http`, `https`, `net`, `tls`, `dns`, and `dgram` remain denied. |
| WebSocket | experimental | Controlled bridge-backed connections with bounded messages and connection counts. Requires `network` capability and provider support; raw sockets remain denied. |
| OCR | partial | `require("ocr")` accepts opaque image handles. Real OCR model/provider availability is separate from the JavaScript API shape. |
| `storage` / `storages` | partial | JSON-only key/value storage with project/package/working-directory isolation. No raw SharedPreferences or file handles are exposed. |
| notifications | partial | Controlled post/cancel API with Android notification permission handling. Actions and richer notification layouts are not part of this draft. |
| sensors | partial | Limited sensor set: accelerometer, gyroscope, and light. Subscriptions are bounded and close on provider destroy. |
| UI MVP | partial | JSON layout descriptors and opaque handles only. The current live Activity-owned provider is still gated, so normal scripts must handle provider denial. |
| `work_manager` | partial | Controlled one-shot and bounded periodic scheduled Node work through AndroidX WorkManager. Entries stay scoped to the project root, periodic intervals are bounded, CJS/ESM fired entries run through the dedicated scheduled runner, and raw Android scheduler APIs remain unavailable. |
| JS-only plugins | experimental | Project-local `./plugins/<id>` manager with local install registration, enable/disable, config, dependency metadata, named exports, status/capability reporting, `start`/`stop` lifecycle hooks, owned resource cleanup, unload, and explicit debug-only reload. Native plugins, signed archives, download installers, updates, Android provider adapters, and packaged plugin runtime are not enabled. |
| execution modes beyond `one_shot` | partial | `interactive_long_running` is available only through the explicit interactive foreground runtime with a persistent Stop notification, `autojs6:lifecycle` checkpoint/explicit-restore hooks, and restart policy `never`. `scheduled` is available only through the controlled WorkManager scheduled runner. Direct script requests still reject before user code, and `worker_computation` remains reserved. Normal scripts remain one-shot and timeout-bounded. |

## ESM Scripts

Status: `partial`.

Use `.mjs` or `package.json` with `"type": "module"`:

```js
// main.mjs
import path from "node:path";
import helper from "./helper.mjs";

const value = await helper();
console.log(path.basename(import.meta.url), value);
```

Supported in the v1.1 partial path:

- local static imports of `.mjs`, `.js`, and `.cjs`
- top-level await
- default import from CommonJS
- selected builtin imports such as `node:path`, `node:buffer`, and supported
  stream/zlib modules
- package `exports` and `imports` conditions through the AutoJs6 resolver
- local dynamic `import()` for relative modules, allowed builtins, and scoped
  packages

Still out of scope:

- loader hooks
- network URL imports
- absolute or path-escape imports
- native addons
- raw network, `child_process`, `process.binding`, or `process.dlopen`

CommonJS remains the widest-compatibility format. Calling `require()` on an ESM
module rejects with `ERR_AUTOJS6_REQUIRE_ESM_UNSUPPORTED`.

## Controlled Fetch And Axios

Status: `experimental`.

Declare `network` before using controlled network modules:

```json
{
  "node": {
    "permissions": ["network"]
  }
}
```

Fetch:

```js
"nodejs";

const fetch = require("fetch");

const response = await fetch("https://example.com/data.json", {
  timeoutMs: 5000,
  maxResponseBytes: 64 * 1024
});

console.log(response.status, await response.text());
console.log(fetch.diagnostics());
```

Axios compatibility layer:

```js
"nodejs";

const axios = require("axios");

const response = await axios.get("https://example.com/data.json", {
  timeout: 5000,
  responseType: "json"
});

console.log(response.status, response.data);
```

Limits:

- raw Node network modules remain disabled
- desktop axios adapters, agents, proxy tunneling, and custom adapters are
  denied
- response size, redirect count, timeout, and concurrency are bounded
- normal builds without the controlled Android provider reject with a stable
  network-disabled error

Packaged APKs add `android.permission.INTERNET` only from explicit `network`
capability metadata, not from module presence.

## WebSocket

Status: `experimental`.

```js
"nodejs";

const websocket = require("websocket");

const socket = await websocket.connect("wss://example.com/events", {
  timeoutMs: 5000,
  maxMessageBytes: 16 * 1024
});

socket.onmessage = (message) => {
  console.log(message.type, message.text || message.dataBase64);
};

await socket.send("hello");
await socket.close(1000, "done");
```

The wrapper exposes only controlled connection handles. It does not expose raw
TCP/TLS sockets, HTTP upgrade authority, or unlimited queues.

## OCR

Status: `partial`.

OCR consumes opaque image handles returned by the image/screen-capture bridge.
It does not accept raw Android `Bitmap`, OpenCV, or model objects.

```js
"nodejs";

const ocr = require("ocr");

const imageHandle = await captureOrReadImageSomehow();
const results = await ocr.recognize(imageHandle, {
  lang: "auto",
  region: [0, 0, 500, 300],
  timeoutMs: 5000
});

for (const result of results) {
  console.log(result.text, result.confidence, result.bounds);
}
```

Declare both `ocr` and `image` capabilities when OCR is used with image handles.
If the Android OCR provider or model assets are unavailable, the Promise rejects
instead of exposing a raw engine.

## Storage

Status: `partial`.

```js
"nodejs";

const storage = require("storage");
const settings = storage.create("settings");

await settings.put("mode", { value: "fast", retries: 2 });
console.log(await settings.get("mode", { value: "safe", retries: 0 }));
console.log(await settings.keys());
await settings.remove("mode");
```

`require("storages")` is an alias of `require("storage")`.

Values must be JSON-compatible. Storage is scoped by package/project/working
directory and does not expose raw database, SharedPreferences, or file handles.

## Notifications

Status: `partial`.

```js
"nodejs";

const notifications = require("notifications");

const id = await notifications.notify({
  title: "Node task",
  text: "Running",
  channel: "node",
  ongoing: true,
  priority: "default"
});

await notifications.cancel(id);
```

Declare `notifications`. On Android versions that require runtime notification
permission, permission denial is reported as a bridge error. Only notifications
owned by the provider are cancelled.

## Sensors

Status: `partial`.

```js
"nodejs";

const sensors = require("sensors");

console.log(await sensors.getAvailableSensors());

const event = await sensors.once("accelerometer", {
  samplingIntervalMs: 200,
  timeoutMs: 5000
});
console.log(event.values);

const subscription = sensors.subscribe("light", (reading) => {
  console.log(reading.values[0]);
}, { samplingIntervalMs: 1000 });

await subscription.ready;
await subscription.close();
```

Supported sensor names are `accelerometer`, `gyroscope`, and `light`.
Sampling intervals, subscription count, and event queue size are bounded.

## UI MVP

Status: `partial`.

The v1.1 UI module uses JSON descriptors and opaque handles. No raw `Activity`,
`View`, `Context`, or layout inflater object crosses into JavaScript.

```js
"nodejs";

const ui = require("ui");
const model = ui.state({ title: "Node UI", enabled: true, progress: 25 });

const handle = await ui.showLayout({
  type: "Vertical",
  children: [
    { type: "Text", id: "title", text: ui.bind(model, "title") },
    { type: "Switch", id: "enabled", text: "Enabled", checked: ui.bind(model, "enabled") },
    { type: "Progress", id: "progress", progress: ui.bind(model, "progress"), max: 100 },
    { type: "List", id: "choices", items: ["One", "Two"] },
    {
      type: "Tabs",
      id: "tabs",
      tabs: [
        { id: "first", title: "First", content: { type: "Text", text: "First tab" } },
        { id: "second", title: "Second", content: { type: "Text", text: "Second tab" } }
      ]
    },
    { type: "Button", id: "ok", text: "OK" }
  ]
});

const off = handle.on("click", async (event) => {
  if (event.id === "ok") {
    off();
    model.update({ title: "Clicked", progress: 100 });
    await handle.batchUpdate([{ id: "enabled", checked: false }]);
    await handle.close();
  }
});
```

Current provider status is Activity-owned live MVP in the main app. Scripts
should still treat UI as a constrained declarative bridge, not as a replacement
for Rhino UI APIs or a raw Android View API.
`WebView` remains unavailable until a separate security gate is completed.

## Work Manager

Status: `partial`.

```js
"nodejs";

const workManager = require("work_manager");

const id = await workManager.scheduleOnce({
  scriptPath: "./main.cjs",
  delayMs: 60_000,
  backend: "embedded",
  constraints: { network: true }
});

console.log(await workManager.list());
await workManager.cancel(id);
```

The current module stores bounded task metadata, launches fired CJS/ESM entries
through `executionMode=scheduled` on the `scheduled_runner` surface, and records
Android WorkManager/JobScheduler quota diagnostics for scheduled Node tasks.
Queryable task records include `lastRun` and bounded `runHistory` entries with
attempt, execution id, stdout/stderr snippets, error metadata, and selected
native diagnostics. It is not an unlimited background runtime, and boot/reboot
recovery remains a separate gate. `tasks` is not exposed in the Safe Node
Profile.

## JS-only Plugins

Status: `experimental`.

Project-local plugins live under `./plugins/<id>` and require exact
`plugin.<id>` capability opt-in:

```text
project/
  project.json
  plugins/
    org.example.demo/
      package.json
      index.js
```

```json
{
  "node": {
    "permissions": ["plugin.org.example.demo"]
  }
}
```

```js
"nodejs";

const plugins = require("plugins");

plugins.install("org.example.demo", {
  source: "./plugins/org.example.demo",
  config: { mode: "demo" }
});
console.log(plugins.manifest("org.example.demo"));
console.log(plugins.version("org.example.demo"));
const demo = plugins.load("org.example.demo");
plugins.start("org.example.demo");
console.log(plugins.status("org.example.demo"));
plugins.stop("org.example.demo");
console.log(demo);
plugins.unload("org.example.demo");
```

Only JavaScript plugin entries are in scope. `install()` registers an
already-present sanitized local package for the current execution; it does not
download, copy, or persist packages. `start()` passes a lifecycle context with
the plugin namespace, effective capability subset, frozen config, and
`ctx.resources` registry. `stop()`, `unload()`, and `disable()` release
registered timers plus tracked provider/UI/socket/image handles. `reload()`
requires `{ debug: true }` and is intended for local development only. Native
addons, signed plugin archives, download installers, updates, Java provider
expansion, Android provider adapters, and packaged plugin runtime are not
enabled.

## Long-running Node Scripts

Status: `partial`.

Normal scripts cannot opt into long-running behavior directly. This request
shape is rejected before user code runs:

```json
{
  "node": {
    "executionMode": "interactiveLongRunning",
    "timeoutMs": 0
  }
}
```

The v1.1 runtime has a controlled interactive foreground entry for host-owned
launches. It starts a user-visible Node session, posts a persistent notification
with Stop, reports status/duration/resource summary in app-private storage, and
uses restart policy `never`. Android foreground-service start/type failures and
system timeout cleanup are reported through stable diagnostics. Status snapshots
also include runtime heartbeat and budget diagnostics such as event-loop
responsiveness, last bridge progress, active resource counts, and budget breach
counts. It is not a packaged service descriptor, scheduler, or hidden background
mode.

Interactive sessions can use `require("autojs6:lifecycle")`:

```js
const lifecycle = require("autojs6:lifecycle");

lifecycle.onStop(async (reason) => {
  await lifecycle.checkpoint({ reason, cursor: 42 }, { reason: "user.stop" });
});

await lifecycle.onRestore((checkpoint) => {
  console.log(checkpoint.cursor);
});
```

Checkpoint data must be JSON-serializable and is limited to 64 KiB. It is stored
under app-private project-scoped storage and restored only when the script calls
`onRestore()` or `readCheckpoint()`. AutoJs6 does not restart a script
automatically from a checkpoint.

Keep normal scripts one-shot and timeout-bounded. Use notifications and the
controlled `work_manager` scheduling MVP within their current partial limits:
scheduled entries remain scoped, bounded, WorkManager-backed, and routed through
the dedicated scheduled runner. Direct scripts cannot request `scheduled`
execution, and `workerComputation` remains a policy descriptor until its
dedicated gate is implemented.

## Packaging Notes

Packaged APK support is a first-class v1.1 target, but support depends on each
feature's provider status:

- packaged CommonJS, dependency, fs, stream, zlib, and bridge routing smokes are
  covered
- packaged v1.1 bridge smoke covers controlled `fetch`, OCR, storage, UI, and
  `work_manager` through fake transport
- packaged `.mjs`, package `type=module`, and local dynamic import smokes run
  against the default production inrt template through `:app:verifyNodePackagedEsmGate`
- packaged plugins and packaged long-running service mode are not enabled

## See Also

- [QUICK_START.md](QUICK_START.md)
- [PROJECT_WIZARD.md](PROJECT_WIZARD.md)
- [MIGRATION_FROM_RHINO.md](MIGRATION_FROM_RHINO.md)
- [types/autojs6-node](types/autojs6-node)
- [releases/NODE_PROFILE_V1_1_CAPABILITY_MATRIX.md](releases/NODE_PROFILE_V1_1_CAPABILITY_MATRIX.md)
- [TESTING.md](TESTING.md)
