# Node UI Bridge Design Gate

Status: Task S7-14 activated the first Activity-owned `require("ui")` live
provider MVP. Task S7-44 expands the same Activity-owned provider with basic
form/list/navigation components, one-way JSON state binding, batched updates,
TypeScript declarations, and focused live gates while keeping WebView blocked
behind a separate security gate.

Task S11-23 records the Rhino `ui` compatibility boundary in
[RHINO_UI_COMPATIBILITY.md](RHINO_UI_COMPATIBILITY.md). P12-08 locks the
JSON-only migration subset in
[RHINO_UI_JSON_LAYOUT_SUBSET.md](RHINO_UI_JSON_LAYOUT_SUBSET.md). P12-09 adds
`autojs6:compat.ui` as a constrained wrapper over this provider and keeps
default global `ui` injection disabled. P12-10 adds a compat.ui packaged marker
and UI_SESSION cleanup evidence without promoting live packaged background UI.
P13-17 records the Phase 13 live UI provider promotion gate: direct
`require("ui")` live Activity evidence is present, including Back/Recents
lifecycle, but packaged entry, task relaunch, crash, and disclosure evidence
still block `pro_compat_opt_in` promotion.

## Goal

Node-driven UI must not destabilize the Safe Node Profile or bypass Android
lifecycle ownership.

The current Node bridge is suitable for short, bounded automation calls. UI is
different: it owns Android `Activity` or overlay windows, runs on the main
thread, receives high-volume events, can outlive a single JavaScript tick, and
interacts with packaged APK back stack and permissions. A Node UI bridge needs
a separate provider contract before any runtime API is exposed.

## Current Decision

Expose only the narrow declarative Activity UI provider for `require("ui")` in
Node Profile v1.1.

The current module accepts JSON-only layout descriptors through
`showLayout(layoutJson, options?)`, returns an opaque `UiHandle`, supports
`handle.on(event, callback)`, `handle.update(patch)`,
`handle.batchUpdate(patches)`, and `handle.close()`, and routes event delivery
through bounded `drainEvents` bridge calls. `ui.state(initialJson)` and
`ui.bind(state, key)` provide one-way binding for JSON descriptor properties;
binding values are resolved before provider dispatch, so the UI process never
receives executable JavaScript.

The Android provider starts an internal, non-exported Activity and supports
`Column`, `Row`, `Text`, `Button`, `Input`, `Image`, `ScrollView`, `List`,
`Switch`, `Checkbox`, `Progress`, and `Tabs` descriptors. Component,
lifecycle, update, and batch-update events are serialized JSON snapshots; raw
Android objects never cross into JavaScript. `WebView` descriptors reject until
a separate WebView security gate is designed.

Existing bridge modules remain the UI-adjacent surface for now:

- `dialogs` for small modal decisions, currently fail-closed until a UI provider
  is connected
- `app.startActivity()` for controlled intent descriptors
- `toast` for transient messages

## Non-Goals

The Node UI bridge is not:

- a desktop DOM or browser replacement
- unrestricted Android view interop
- a clone of Rhino global `ui`
- a shortcut to raw Java interop
- a way to create overlay/floaty windows without overlay policy
- a hidden background service mode
- a reason to make one-shot Node scripts run forever by default

## Mode Comparison

| Mode | Description | Decision |
| --- | --- | --- |
| Protocol-gated `require("ui")` | JavaScript module and fake-provider protocol exist. | Baseline retained for regression tests. |
| Activity-owned declarative UI | A provider owns an internal Activity and accepts JSON layout/state/actions. | Current v1.1 live MVP. |
| WebView-hosted UI | Node drives a constrained local WebView surface. | Possible separate design, not default. |
| Rhino UI compatibility shim | Node mimics Rhino `ui.*` and XML semantics. | Migration-only and high risk. |
| Floaty/overlay UI | Node owns overlay windows. | Must wait behind overlay permission and floaty policy. |
| Raw Android View interop | Node receives live View/Activity objects. | Rejected. |

## Capability Model

Future capability names should be explicit and narrow:

```text
ui
ui.activity
ui.dialog
ui.webview
ui.overlay
```

Task S7-14 uses the broad `ui` capability for the Activity-owned JSON protocol.
Future narrower names may still be introduced if the first live provider needs
separate Activity, dialog, WebView, or overlay permissions. `ui.overlay` must
not be implied by `ui`; overlay windows need separate user-visible permission,
foreground-service, packaged manifest, and disclosure policy.

Bridge capability manifests should remain deny-by-default when a project opts
into permissions. Missing UI declarations must reject before provider dispatch.

## Thread Model

The Node event loop must never touch Android views directly.

Required shape:

```text
Node script
  -> JSON bridge request
  -> UI capability provider
  -> main thread Activity/View operation
  -> JSON response or event envelope
```

Rules:

- all Android view creation, mutation, and disposal happens on the main thread
- Node receives Promise results and event snapshots, not live View references
- provider callbacks marshal back to the Node bridge completion path
- long-running event streams have bounded queues and cancellation
- every request has a timeout except explicit event subscription reads
- service destroy, script stop, Activity destroy, and process death close all UI
  sessions

## Lifecycle Ownership

The UI provider must own a `UiSession` resource registered to the current Node
execution id.

Required lifecycle states:

```text
created
starting_activity
visible
paused
resumed
closing
closed
failed
```

Required close paths:

- script success or failure
- user Stop from AutoJs6 runtime controls
- Android Back when the UI session is configured to finish
- Activity `onDestroy`
- service destroy
- bridge timeout or provider exception
- native crash recovery cleanup

The provider must make close idempotent. A stale JavaScript handle must reject
with a stable bridge error instead of resurrecting an Activity.

Rotation and process recreation are not allowed to silently detach UI from its
owning Node execution. P13-17 chooses an explicit destroy/recreate
configuration-change policy with no live UI survival claim; future promotion
must re-open this if it wants state preservation across rotation.

## Packaged APK Behavior

Packaged APK behavior must be designed before shipping any UI module.

P13-17 keeps live packaged UI in provider-dependent state. The provider registry
may report the main-app Activity provider as available, but packaged support
must not be treated as promoted until packaged entry, task relaunch, crash, and
disclosure evidence below is covered by device smoke.

Open packaged requirements:

- which Activity class owns the Node UI
- how packaged app labels, themes, icons, and task affinity apply
- whether UI launch is the packaged main entry or a secondary entry
- how Back, Recents, and task relaunch behave
- whether encrypted packaged sources can define layouts without leaking assets
- which manifest entries, permissions, services, and receivers are injected
- how crash reports identify the package, script, UI session, and Activity

The bridge must not depend on host-app-only Activities that packaged templates
do not provide.

## Rhino Compatibility Boundary

AutoJs6 already has Rhino UI infrastructure: `ui.run()`, `ui.post()`, XML
inflation, `NativeView`, view wrappers, and many `Js*` widgets. Node must not
import that object model wholesale.

Reasons:

- Rhino UI code can hold live Java objects and View wrappers
- XML inflater semantics are tied to Rhino runtime assumptions
- synchronous `ui.run()` style APIs conflict with the Node event loop
- current Activity tracking differs between main app, service, and packaged APK
- leaking a Rhino `NativeView` into Node would bypass the bridge policy

If a compatibility layer is ever added, it should translate a narrow subset of
Rhino UI declarations into the Node UI provider's JSON protocol. It should not
expose Rhino globals by default.

## S11-23 Rhino UI Mapping

The S11-23 compatibility decision is:

- use `require("ui")` as the only current Node UI entrypoint
- map Rhino `ui.layout(...)` / `ui.layoutFile(...)` only through constrained
  JSON descriptors passed to `showLayout`
- migrate updates to `UiHandle.update(...)` and `UiHandle.batchUpdate(...)`
- deliver events through bounded provider `drainEvents` calls and
  `UiHandle.on(...)` callbacks on the Node event loop
- treat `ui.run()` and `ui.post()` as blocked by design for Node because they
  imply main-thread JavaScript execution
- reject `NativeView`, Android `View`, `Activity`, resource objects, widget
  registries, and Activity emitter leakage into Node

This keeps Android view work on the main thread while Node receives only JSON
results and event snapshots.

## Relation To AutoX v7 And Auto.js Pro Styles

AutoX v7 and Auto.js Pro style APIs are useful migration references, but they
should not define the Node contract automatically.

Node Profile should prefer:

- explicit `require("ui")` import, not global `ui`
- Promise and async iterator/event APIs
- JSON layout descriptors or a constrained declarative schema
- opaque ids for UI sessions and widgets
- provider-owned lifecycle cleanup
- clear packaged APK behavior

It should avoid:

- unrestricted property mutation on live Java views
- synchronous UI-thread calls from JavaScript
- broad widget reflection
- implicit current Activity access
- overlay support as part of the first UI module

## Reserved API Shape

Task 182 reserves this JavaScript shape:

```js
const ui = require("ui");
const model = ui.state({ name: "", ready: false });

const handle = await ui.showLayout({
  type: "Vertical",
  children: [
    { id: "name", type: "Input", hint: "Name" },
    { id: "ready", type: "Switch", text: "Ready", checked: ui.bind(model, "ready") },
    { id: "progress", type: "Progress", progress: 0, max: 100 },
    { id: "ok", type: "Button", text: "OK" }
  ]
}, {
  timeoutMs: 10000
});

const off = handle.on("click", async (event) => {
  if (event.type === "click" && event.id === "ok") {
    off();
    model.set("ready", true);
    await handle.batchUpdate([
      { id: "progress", progress: 100 },
      { id: "ok", text: "Done" }
    ]);
    await handle.close();
  }
});
```

Required properties if this shape is selected:

- `ui.showLayout(layoutJson, options?)` returns an opaque `UiHandle`
- `ui.state()` and `ui.bind()` are one-way JSON helpers, not UI-process code
- descriptors are JSON only and schema-validated before dispatch
- event draining has bounded request size and listener cleanup
- `handle.close()` is idempotent
- `handle.batchUpdate()` applies bounded JSON patch batches
- all widget ids are scoped to the handle/session
- state reads and event snapshots return JSON only
- no raw View or Activity object crosses into Node

## WebView Option

A WebView-hosted option may be attractive for Node authors, but it has its own
risk model.

It must define:

- local asset loading policy
- JavaScript bridge policy inside WebView
- network policy and URL allowlist
- file upload/download policy
- cookie, storage, and cache isolation
- CSP or equivalent restrictions
- packaged asset inclusion and size limits

Do not use WebView as a shortcut to unrestricted DOM, network, filesystem, or
Android JavaScript interface access.

## Floaty And Overlay Boundary

Floaty is not part of the base UI bridge.

Overlay UI requires:

- `SYSTEM_ALERT_WINDOW` permission flow or equivalent platform policy
- foreground service and notification behavior where required
- drag/resize lifecycle ownership
- explicit user-visible disclosure
- packaged manifest injection
- denial behavior when overlay permission is absent
- cleanup on script stop, Activity destroy, service destroy, and crash

Overlay support uses the separate [FLOATY_NODE.md](FLOATY_NODE.md) design gate
and should be exposed, if ever implemented, through a separate `ui.overlay` or
`floaty` capability.

S11-24 adds the Rhino migration boundary in
[RHINO_FLOATY_COMPATIBILITY.md](RHINO_FLOATY_COMPATIBILITY.md): Rhino
`floaty.window()` and `floaty.rawWindow()` remain blocked as exact Node
compatibility targets, `floaty.getClip()` migrates to the controlled clipboard
surface, and no base `ui` capability may imply overlay authority.

## Security Requirements

Any future implementation must enforce:

- no raw `Context`, `Activity`, `View`, `Window`, `Fragment`, `Handler`, Binder,
  or Java object exposure
- no direct Java interop requirement
- no reflection over widgets or Android classes
- bounded layout descriptor size and depth
- bounded event queue and listener count
- bounded UI session count per execution
- provider-owned resource registry entries for sessions and widget handles
- stable cancellation and timeout errors
- no hidden background UI work after script completion
- no overlay permission escalation from `ui.activity`

## Test Gate

Task S7-14 and S7-44 tests now cover:

- Kotlin registry and permission manifest coverage for `ui`
- fake-provider `showLayout`, `update`, `batchUpdate`, `drainEvents`, and
  `close`
- JavaScript shadowing protection for `require("ui")`
- `ui.state()` / `ui.bind()` one-way binding and batched binding updates
- fake-provider and Android-live event callback delivery through drained JSON
  snapshots
- JSON-only invalid descriptor rejection before dispatch
- no raw View/Activity property on the JavaScript handle
- Android live Activity show, Button/Input/List/Switch/Checkbox/Tabs event
  delivery, Progress batch update, update, close, and destroy cleanup
- unsupported descriptor, WebView security-gate, and foreign handle denial
- active UI session limit denial
- TypeScript declaration smoke
- P12-10 `compat.ui` packaged marker, fake-transport event/update/batch/close
  smoke, and `UI_SESSION` cleanup evidence
- P13-17 `verifyNodePhase13LiveUiProviderPromotion` host gate, which records
  live Activity provider evidence and keeps live packaged UI blockers explicit

Minimum tests before promoting packaged live UI or WebView:

- schema parser unit tests for layout descriptors
- fake-provider success, failure, timeout, cancellation, and destroy tests
- no raw-object serialization tests
- Activity instrumentation for Back and configuration-change policy
- explicit configuration-change destroy/recreate policy test
- packaged APK smoke for UI entry, Back, crash, and relaunch behavior
- WebView local asset, URL, JavaScript bridge, and storage isolation tests if
  WebView support is introduced
- leak test for Activity, View, session, and event listener cleanup
- bridge limit tests for max sessions, widgets, descriptor size, and event queue
- accessibility smoke for basic labels and focus order if native widgets are used

## Open Questions

- Should the first UI module be `ui.activity` only, or should it reserve
  `require("ui")` with a narrow activity surface?
- Should Node UI require an explicit project mode such as `node.ui=true`?
- Does a UI script become long-running automatically, or must it use the future
  service mode from `LONG_RUNNING_SERVICE.md`?
- Should packaged APKs launch Node UI from `SplashActivity`, a new
  `NodeUiActivity`, or the existing script execution Activity?
- Should XML be accepted, or should the first schema be JSON only?
- How are localized strings and packaged resources referenced?
- How does UI coexist with encrypted packaged sources?
- What is the migration path for existing Rhino UI scripts?

## Recommendation

Keep the base Activity UI provider narrow and JSON-only. Further expansion,
including packaged APK UI behavior, WebView, overlay/floaty, and Rhino `ui`
compatibility helpers, should remain behind focused design and verification
gates.
