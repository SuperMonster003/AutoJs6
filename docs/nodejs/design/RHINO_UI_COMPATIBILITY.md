# Rhino UI Compatibility Design

Status: S11-23 design gate, with the P12-08 JSON layout subset locked in
[RHINO_UI_JSON_LAYOUT_SUBSET.md](RHINO_UI_JSON_LAYOUT_SUBSET.md). This document
records how Rhino `ui` scripts may migrate to the existing Node `require("ui")`
Activity-owned provider without copying Rhino's Android object model into V8.

## Decision

Do not add `compat.ui`, `require("tasks")`-style aliases, or a global `ui` in
S11-23. The current supported Node surface remains the explicit
`require("ui")` module.

The Node module is intentionally declarative and JSON-only:

- `ui.showLayout(layoutJson, options?)` starts an Activity-owned UI session
- `UiHandle.update(patch)` and `UiHandle.batchUpdate(patches)` mutate widgets
  through JSON patches
- `UiHandle.on(event, callback)` receives drained JSON event snapshots
- `UiHandle.close()` closes the provider-owned session
- `ui.state(initialJson)` and `ui.bind(state, key)` provide one-way JSON
  binding values

The provider owns the Android Activity and all `View` instances. JavaScript
receives only an opaque `UiHandle` and event snapshots. `UI_SESSION` entries in
`NodeBridgeResourceRegistry` own cleanup on script stop, provider destroy, and
Activity destroy.

## Rhino Surface

Rhino `ui` is not a single declarative API. The augment module combines:

- XML layout syntax and `ui.layout(...)` / `ui.layoutFile(...)`
- `ui.inflate(...)`, `ui.__inflate__(...)`, `ui.setContentView(view)`, and
  `NativeView` wrappers
- `ui.run(fn)` and `ui.post(fn, delay)` helpers that execute JavaScript through
  the Android main-thread looper
- `ui.findById(...)`, `ui.findView(...)`, `ui.root`, and dynamic property access
  to live view wrappers
- widget registration, `ui.widgets`, `ui.R`, and Activity emitter access
- status/navigation/background/window helpers tied to the current Activity

Those shapes assume Rhino `Scriptable` objects, Java Android objects, and direct
main-thread execution. They cannot be installed as-is in the Safe Node Profile.
In S11-23, any path that returns or accepts an Android `View`, `NativeView`,
widget registry object, Activity emitter, or Android resource object is treated
as a raw-object leak and blocked by design.

## Thread Model

The Node event loop never becomes the Android UI thread.

Required relationship:

```text
Node event loop
  -> JSON bridge request
  -> UI provider dispatch
  -> Android main thread Activity/View operation
  -> JSON response or drained event snapshot
  -> Node event loop callback
```

Rules:

- `ui.run(fn)` and `ui.post(fn)` are not implemented for Node because they move
  JavaScript execution onto Android's main looper and can capture raw view
  objects.
- Event callbacks registered with `UiHandle.on(...)` run on the Node event loop
  after the provider drains bounded JSON events.
- Layout updates use `handle.update(...)` or `handle.batchUpdate(...)`; they do
  not mutate Android views directly.
- Long-running UI scripts remain alive only while they hold an owned
  `UiHandle` or other explicit Node resource.

## API Mapping

| Rhino API | S11-23 status | Node migration |
| --- | --- | --- |
| `ui.__module` | partial | Use `const ui = require("ui")`; no default global. |
| `ui.layout(...)` | partial | Translate supported declarative layout into `ui.showLayout(json)`. XML and executable attributes are not accepted. |
| `ui.layoutFile(path)` | partial | Future migration may parse a constrained project-local layout file into JSON before `showLayout`. |
| `ui.finish()` | partial | Close the current Node UI session with `handle.close()`. There is no implicit current Activity. |
| `ui.keepScreenOn(...)` | partial | Candidate session/window option for a future provider update; no raw Activity access. |
| `ui.run(fn)` / `ui.post(fn)` | blocked_by_design | Replace with normal async JavaScript plus `handle.update(...)`, `handle.batchUpdate(...)`, and event callbacks. |
| `ui.inflate(...)`, `ui.__inflate__(...)`, `ui.setContentView(view)` | blocked_by_design | Do not move `View` or `NativeView` objects into Node. Use JSON descriptors. |
| `ui.findById(...)`, `ui.findByStringId(...)`, `ui.findView(...)`, `ui.root` | blocked_by_design | Keep widget ids scoped inside `UiHandle`; events and state snapshots are JSON only. |
| `ui.registerWidget(...)`, `ui.widgets`, `ui.R`, `ui.emitter` | blocked_by_design | These depend on Rhino widgets, Android resources, or Activity event emitters. |
| status/navigation/background color and height helpers | unsupported | Provider-level window option and inset snapshot support is deferred. |

## Migration Example

Rhino:

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

Node:

```js
"nodejs";

(async function main() {
  const ui = require("ui");

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

The Node version keeps JavaScript on the Node event loop. Button events arrive
as JSON snapshots through `drainEvents`, and widget updates are JSON patches
sent back to the provider.

## Rejected Compatibility

S11-23 rejects these shortcuts:

- injecting Rhino global `ui` by default
- returning `NativeView`, `View`, `ViewGroup`, `Activity`, `Context`, `Window`,
  `Handler`, Android resource objects, Binder objects, or Java objects
- evaluating XML attributes as JavaScript inside the UI provider
- exposing `ui.run()` / `ui.post()` as main-thread JavaScript execution
- reusing Rhino widget registration or Activity emitter objects
- enabling overlay/floaty behavior through the base `ui` capability

## Future Work

Future UI compatibility work must be split into small tasks:

- `compat.ui` constrained facade over the P12-08 JSON descriptor subset
- constrained XML-to-JSON migration helper for common `ui.layout(...)` scripts
- provider schema coverage for status/navigation/background options
- inset snapshots for status/navigation bar height queries
- packaged APK UI behavior and Back/Recents policy
- optional `compat.ui` facade only after the JSON mapping is testable and does
  not expose raw Android/Rhino objects

Floaty and overlay compatibility remains separate and is tracked by
[FLOATY_NODE.md](FLOATY_NODE.md).
