# Node Floaty And Overlay Design Gate

Status: design gate only. No `require("floaty")`, `require("ui.overlay")`,
overlay provider, packaged manifest injection, or runtime permission flow is
implemented by this document.

Task S11-24 records the Rhino `floaty` compatibility boundary in
[RHINO_FLOATY_COMPATIBILITY.md](RHINO_FLOATY_COMPATIBILITY.md). It does not
promote a Node runtime module; it only fixes the migration and provider-gap
contract for future work.

## Goal

Node floaty support must let scripts create user-visible overlay surfaces
without weakening the Safe Node Profile or bypassing Android overlay policy.

Floaty is higher risk than the base `ui` protocol gate because overlay windows
can appear above other apps, capture touches, obscure security prompts, remain
visible after a script appears idle, and require platform-specific permission
and foreground-service handling. It must be designed as an explicit overlay
capability, not as an automatic extension of `require("ui")`.

## Current Decision

Do not expose Node floaty or overlay windows in v1.1.

P12-11 selects the rejection path for exact Rhino `floaty.window()` and
`floaty.rawWindow()` compatibility. The Provider path is not selected for
Phase 12; P12-12B owns the follow-up negative runtime gate. A future
JSON-only `ui.overlay` provider may still be designed later, but it must not
claim parity with Rhino raw window wrappers.

P12-12B keeps `require("floaty")`, `require("ui.overlay")`, `compat.floaty`,
and global `floaty` unavailable. Script examples that need a stable policy
marker use `compat.blockedByDesign(...)` and
`ERR_AUTOJS6_RHINO_COMPAT_BLOCKED_BY_DESIGN`; they do not create an overlay
provider request.

The Task 182 `require("ui")` protocol reservation remains Activity/provider
owned and fail-closed on Android. It does not imply overlay authority. Any
future floaty implementation must use a separate capability such as
`ui.overlay` or `floaty`, a dedicated provider, user-visible permission flow,
foreground-service policy where required, packaged APK manifest policy, and
focused tests.

Existing Rhino floaty APIs and AutoJs6 internal floating-window classes remain
implementation references only. Node must not receive raw `WindowManager`,
`FloatyService`, `FloatyWindow`, `View`, `WindowBridge`, `LayoutParams`, Rhino
`NativeView`, or Java objects.

## S11-24 Rhino Compatibility Boundary

Rhino `floaty.window(xml)` and `floaty.rawWindow(xml)` are blocked as exact Node
compatibility targets because they return live window wrappers over XML-inflated
Android views. Future Node overlay work may translate only constrained JSON
descriptors into provider-owned windows and opaque handles.

`floaty.hasPermission()`, `floaty.requestPermission()`,
`floaty.ensurePermission()`, and `floaty.closeAll()` remain unsupported until an
overlay provider owns permission checks, disclosure, handles, close paths, and
packaged behavior. `floaty.getClip()` should migrate to the controlled
`clipboard` bridge or `require("autojs6:compat").getClip()`, not to a hidden
temporary overlay window.

The provider gap list for S11-24 is tracked in
[RHINO_FLOATY_COMPATIBILITY.md](RHINO_FLOATY_COMPATIBILITY.md).

## Non-Goals

Node floaty is not:

- part of the base `require("ui")` Activity surface
- a compatibility alias for Rhino global `floaty`
- unrestricted overlay or `WindowManager` interop
- a way to request `SYSTEM_ALERT_WINDOW` silently
- a replacement for foreground service disclosure
- a hidden long-running service mode
- an accessibility overlay or screen-reader helper
- a channel for raw touch injection or global input capture

## Capability Model

Future capability names should remain explicit:

```text
ui.overlay
floaty
floaty.raw
floaty.touch
```

Recommended first capability:

```text
ui.overlay
```

Rules:

- `ui` must not imply overlay authority.
- `ui.activity` must not imply overlay authority.
- `floaty.raw` and unrestricted touch capture remain separate high-risk
  gates, if they are ever considered.
- A project with an enforced bridge capability manifest must declare overlay
  authority before any floaty request dispatches.
- Packaged APK builds may inject `SYSTEM_ALERT_WINDOW` only from explicit
  project/package metadata and must report the mapping in diagnostics.

## Permission And Disclosure

Overlay support requires a user-visible permission and disclosure model.

Required behavior:

- detect current overlay permission before creating a window
- reject with a stable permission error when permission is absent
- expose a controlled way to open the platform overlay settings page only if
  the user requested it
- never show overlay windows while a permission prompt is active unless the
  platform flow explicitly allows it
- use a foreground service and notification when Android policy or script
  lifetime requires it
- show enough notification/diagnostic metadata for users to identify the owning
  script/package
- document OEM-specific denial cases as provider errors, not silent fallbacks

The provider must not grant or emulate overlay permission.

## Lifecycle Ownership

The main app or packaged app must own a floaty provider session. The isolated
Node service must not create `WindowManager` windows directly.

Required lifecycle:

```text
Node script
  -> JSON overlay descriptor
  -> main/package floaty provider
  -> permission and foreground-service checks
  -> main-thread WindowManager operation
  -> opaque FloatyHandle and JSON events
```

Required close paths:

- `handle.close()`
- script completion or failure
- user Stop from AutoJs6 controls
- service destroy or native bridge destroy
- foreground notification stop action
- provider exception
- permission revocation
- package task removal
- Android process death recovery

Close must be idempotent. A stale handle must reject with a stable bridge error
instead of recreating a window.

## Proposed API Shape

This is illustrative only:

```js
const overlay = require("ui.overlay");

const window = await overlay.show({
  type: "Vertical",
  width: 240,
  height: 120,
  position: { x: 32, y: 64 },
  draggable: true,
  children: [
    { id: "status", type: "Text", text: "Running" },
    { id: "stop", type: "Button", text: "Stop" }
  ]
}, {
  timeoutMs: 10000
});

const off = window.on("click", async (event) => {
  if (event.id === "stop") {
    off();
    await window.close();
  }
});
```

Minimum handle shape if implemented:

- `id: string`
- `on(event, callback, options?): () => void`
- `update(patch, options?): Promise<void>`
- `moveTo(x, y, options?): Promise<void>`
- `resize(width, height, options?): Promise<void>`
- `close(options?): Promise<void>`

All descriptors, patches, and events must be JSON-only snapshots. No raw view or
window object may cross the bridge.

## Event Policy

Overlay events are high-volume and user-sensitive.

Rules:

- events are queued by the provider and drained by Node, not pushed directly
  from Android callbacks into JavaScript
- event queues are bounded per window and per execution
- motion/touch events are throttled and coalesced by default
- raw coordinates are window-local unless a separate review accepts screen
  coordinates
- text input events expose values only for widgets owned by the floaty session
- event listeners must be removed on handle close and service destroy
- provider destroy must reject pending drain calls

Global input capture, raw key events, accessibility events, and touch injection
are out of scope.

## Layout And Window Policy

Allowed first widgets should mirror the safe `ui` protocol subset:

- `Text`
- `Button`
- `Input`
- `LinearLayout`
- `Vertical`
- `Horizontal`

Window policy must define:

- maximum window count per execution
- maximum descriptor size and depth
- maximum view count per window
- maximum width/height and offscreen positioning behavior
- focusable versus non-focusable mode
- touchable versus non-touchable mode
- drag/resize handles and bounds
- z-order and interaction with app-owned Activity UI
- screen rotation behavior
- dark/light theme behavior

Raw XML inflation, arbitrary custom views, reflection over widget classes, and
unrestricted `WindowManager.LayoutParams` fields stay denied.

## Packaged APK Policy

Packaged support must be designed before shipping any floaty runtime.

Open requirements:

- whether the packaged manifest declares `SYSTEM_ALERT_WINDOW`
- whether a foreground service declaration is required
- how the packaged app asks the user to grant overlay permission
- how notification text identifies the package/script
- how package relaunch, Recents, and task removal affect floaty windows
- how encrypted packaged sources reference layout assets
- how crash reports identify the floaty session
- how template verification fails when overlay metadata is missing

Older packaged templates without live bridge metadata must not queue overlay
creation as a one-way host effect.

## Security Requirements

Any future implementation must enforce:

- no raw `Context`, `Activity`, `View`, `Window`, `WindowManager`,
  `LayoutParams`, `FloatyService`, `FloatyWindow`, `WindowBridge`, Binder,
  Rhino `NativeView`, or Java object exposure
- explicit overlay capability declaration
- explicit Android overlay permission state check
- no silent permission grant or hidden settings launch
- bounded descriptor size, widget count, event queue, and window count
- provider-owned resource registry entries for windows and widget handles
- foreground service and notification policy where required
- stable cancellation, permission-denied, timeout, and process-dead errors
- cleanup on script stop, service destroy, notification stop, and permission
  revocation
- no global touch injection, global key capture, or accessibility overlay
  authority through the floaty module

## Test Gate

Minimum tests before implementation:

- capability manifest denial for missing `ui.overlay` or `floaty`
- JavaScript module shadowing protection
- JSON-only layout descriptor validation
- fake-provider show/update/move/resize/close
- fake-provider click/text/move event draining
- max window count and event queue limit denial
- permission-missing Android provider rejection
- permission-settings intent descriptor test if exposed
- foreground notification ownership/stop action test if required
- service stop closes all overlay handles
- stale handle rejects after provider destroy
- packaged manifest verifier for overlay metadata
- packaged APK smoke for permission-missing denial
- no raw object serialization test for every handle/event shape

## Open Questions

- Should the module name be `require("ui.overlay")`, `require("floaty")`, or
  both with one alias?
- Should the first API support only non-focusable status overlays?
- Is text input acceptable in an overlay MVP, or should it wait for Activity UI?
- How should users grant overlay permission from packaged APKs?
- Which Android versions require foreground service disclosure for the first
  MVP?
- How should overlay windows interact with long-running Node mode?
- Should drag/resize be provider-owned only, or script-configurable?
- What should happen when overlay permission is revoked while windows are open?

## Recommendation

Keep exact Rhino floaty window compatibility rejected for Node. Implement only
a future separate JSON overlay provider after permission flow, foreground
disclosure, packaged APK behavior, resource cleanup, and security tests prove
that no raw Android window objects cross the bridge.
