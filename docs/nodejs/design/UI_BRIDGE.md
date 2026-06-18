# Node UI Bridge Design Gate

Status: Task S7-14 activates the first Activity-owned `require("ui")` live
provider MVP. The JavaScript wrapper, capability manifest entry, fake-provider
event protocol, TypeScript declarations, Android Activity provider, and focused
live gate now exist.

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
`handle.on(event, callback)`, `handle.update(patch)`, and `handle.close()`, and
routes event delivery through bounded `drainEvents` bridge calls. The Android
provider now starts an internal, non-exported Activity and supports `Column`,
`Row`, `Text`, `Button`, `Input`, `Image`, and `ScrollView` descriptors.
Button/input/lifecycle/update events are serialized JSON snapshots; raw Android
objects never cross into JavaScript.

The next implementation step is component/data-binding expansion behind the
same `ui` capability and focused tests. It must not expose raw Android
`Context`, `Activity`, `View`, `Fragment`, `Window`, `Handler`, Rhino
`NativeView`, or Java objects to Node scripts.

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
owning Node execution. The first design may lock orientation or finish on
configuration change, but that choice must be explicit and tested.

## Packaged APK Behavior

Packaged APK behavior must be designed before shipping any UI module.

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

const handle = await ui.showLayout({
  type: "Vertical",
  children: [
    { id: "name", type: "Input", hint: "Name" },
    { id: "ok", type: "Button", text: "OK" }
  ]
}, {
  timeoutMs: 10000
});

const off = handle.on("click", async (event) => {
  if (event.type === "click" && event.id === "ok") {
    off();
    await handle.update({ id: "ok", text: "Done" });
    await handle.close();
  }
});
```

Required properties if this shape is selected:

- `ui.showLayout(layoutJson, options?)` returns an opaque `UiHandle`
- descriptors are JSON only and schema-validated before dispatch
- event draining has bounded request size and listener cleanup
- `handle.close()` is idempotent
- all widget ids are scoped to the handle/session
- state reads, if later added, return JSON snapshots only
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

Task S7-14 tests now cover:

- Kotlin registry and permission manifest coverage for `ui`
- fake-provider `showLayout`, `update`, `drainEvents`, and `close`
- JavaScript shadowing protection for `require("ui")`
- fake-provider and Android-live event callback delivery through drained JSON
  snapshots
- JSON-only invalid descriptor rejection before dispatch
- no raw View/Activity property on the JavaScript handle
- Android live Activity show, Button/Input event delivery, update, close, and
  destroy cleanup
- unsupported descriptor and foreign handle denial
- active UI session limit denial
- TypeScript declaration smoke

Minimum tests before expanding the Android UI provider:

- schema parser unit tests for layout descriptors
- fake-provider success, failure, timeout, cancellation, and destroy tests
- no raw-object serialization tests
- Activity instrumentation for Back and configuration-change policy
- rotation or explicit configuration-change policy test
- packaged APK smoke for UI entry, Back, crash, and relaunch behavior
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

Keep real UI rendering as a v1.1 design gate. The reserved `require("ui")`
protocol should remain fail-closed on Android until the Activity-owned provider,
lifecycle, packaged APK behavior, Rhino compatibility boundary, and provider
test gate above are accepted.
