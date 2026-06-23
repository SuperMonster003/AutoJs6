# Rhino Floaty Compatibility Design

Status: S11-24 design gate. This document maps Rhino `floaty` APIs to the
current Embedded Node state and records the provider gaps that must close before
any Node overlay runtime is exposed.

## Decision

Do not add `require("floaty")`, `compat.floaty`, or a global `floaty` in
S11-24. P12-13 adds `require("ui.overlay")` only as the controlled provider
POC, not as Rhino raw-window compatibility.

P12-11 selects the Provider path for a controlled JSON-only overlay provider.
This does not change the S11-24 raw-window boundary: exact Rhino
`floaty.window()` and `floaty.rawWindow()` compatibility remains a permanent
non-goal for the Safe Node Profile. P12-12A locks the overlay provider v1
contract in [OVERLAY_PROVIDER_V1.md](OVERLAY_PROVIDER_V1.md), and that contract
must not expose Rhino `JsWindow`, `WindowBridge`, `NativeView`, or raw Android
window objects. P12-13 owns the fake provider POC.

P12-13 lands the `ui.overlay` fake provider POC over fake transport for
JSON-only `show`, `update`, `drainEvents`, `close`, `closeAll`,
`hasPermission`, and `openPermissionSettings`. The current Node profile still
has no `require("floaty")`, `compat.floaty`, or global `floaty` runtime
surface. P12-14 adds the overlay live gate for Android permission/settings/cleanup
evidence, but the live Android overlay creation path remains fail-closed.
Dual-engine examples for the raw Rhino APIs may keep
using `compat.blockedByDesign("floaty.window", ...)` or
`compat.blockedByDesign("floaty.rawWindow", ...)`, which returns
`ERR_AUTOJS6_RHINO_COMPAT_BLOCKED_BY_DESIGN` via
`compat.errorCodes.blockedByDesign`.

The current Node profile has the `ui.overlay` fake provider POC and the P12-14
live gate for permission/settings/cleanup metadata, but no promoted live
overlay window provider, no packaged manifest injection beyond recorded
`SYSTEM_ALERT_WINDOW` metadata, and no foreground notification/disclosure model
for script-owned overlay windows. The existing
[FLOATY_NODE.md](FLOATY_NODE.md) gate remains the authoritative overlay
provider design boundary.

`floaty.getClip()` is the only Rhino `floaty` entry with a current non-overlay
migration path: use the controlled `clipboard` bridge or
`require("autojs6:compat").getClip()`. Node must not recreate Rhino's hidden
temporary floaty-window clipboard workaround.

## Rhino Surface

Rhino `floaty` combines overlay permission flow, XML view inflation, Android
window ownership, and live Java/Rhino window wrappers:

- `floaty.window(xml)` creates a resizable overlay window through
  `FloatyService` and `BaseResizableFloatyWindow`
- `floaty.rawWindow(xml)` creates a raw overlay window with lower-level
  `WindowManager.LayoutParams` behavior
- returned window objects expose position, size, focus, touchability, close,
  `exitOnClose()`, and view lookup through `findView`
- `floaty.hasPermission()`, `requestPermission()`, and `ensurePermission()`
  interact with Android draw-over-other-apps permission
- `floaty.closeAll()` closes script-owned Rhino floaty windows
- `floaty.getClip()` creates a temporary raw window to help read clipboard
  contents on newer Android versions

Those APIs depend on Android `WindowManager`, `FloatyService`, `FloatyWindow`,
`WindowBridge`, `LayoutParams`, `View`, `ViewGroup`, Rhino `NativeView`, XML
inflation, and UI-thread window operations. They cannot cross into Node as raw
objects.

## API Mapping

| Rhino API | S11-24 status | Node migration |
| --- | --- | --- |
| `floaty.__module` | unsupported | No `require("floaty")`, `compat.floaty`, or global `floaty` is exposed; use the P12-13 `require("ui.overlay")` provider POC for JSON-only overlay migration experiments. |
| `floaty.hasPermission()` | unsupported | P12-13 exposes `ui.overlay.hasPermission()` over fake transport under `ui.overlay.permission`; Rhino-name facade remains unavailable. |
| `floaty.requestPermission()` | unsupported | P12-14 exposes `ui.overlay.openPermissionSettings()` as an explicit live gate with dry-run evidence; no silent prompt path is allowed. |
| `floaty.ensurePermission()` | unsupported | P12-14 exposes Android permission snapshot evidence only; it does not grant permission or create overlays. |
| `floaty.closeAll()` | unsupported | P12-14 returns `ui.overlay.closeAll()` cleanup summaries for the provider gate; Rhino-name `floaty.closeAll()` remains unavailable. |
| `floaty.window(xml)` | blocked_by_design | Exact Rhino XML/`JsResizableWindow` object shape is rejected; future overlay must use JSON descriptors and opaque handles. |
| `floaty.rawWindow(xml)` | blocked_by_design | Raw window flags, focus/touch controls, and raw view access are rejected. |
| `floaty.getClip()` | renamed | Use `require("clipboard").getText()` or `require("autojs6:compat").getClip()`; do not create a hidden overlay window. |

## Provider Gap List

A future Node overlay provider must define and test all of these before any
runtime API is promoted:

- capability name and manifest policy: `ui.overlay` or `floaty`, not implied by
  the base `ui` capability
- Android overlay permission check and explicit settings-launch flow
- foreground service and notification disclosure rules
- packaged APK manifest injection and template verification for
  `SYSTEM_ALERT_WINDOW`
- JSON-only layout descriptor schema, size/depth limits, and widget whitelist
- opaque `FloatyHandle` lifecycle with `update`, `moveTo`, `resize`, `close`,
  and bounded `drainEvents`
- provider-owned resource registry entries for windows and listener queues
- close paths for handle close, script stop, service destroy, notification stop,
  permission revocation, provider exception, task removal, and process death
- event queue limits, motion/touch throttling, and listener cleanup
- denial of raw `WindowManager`, `LayoutParams`, `FloatyService`,
  `FloatyWindow`, `WindowBridge`, `View`, `NativeView`, Binder, and Java object
  exposure
- packaged behavior for Recents, relaunch, encrypted assets, crash diagnostics,
  and missing live bridge metadata

## Close And Cleanup Model

Every future overlay window must be owned by the provider and current Node
execution id. Close must be idempotent and auditable.

Required close paths:

- explicit `handle.close()`
- execution success, failure, timeout, or user Stop
- embedded service destroy and native bridge destroy
- foreground notification stop action
- permission revocation while windows are active
- provider exception or stale handle detection
- packaged app task removal or process death recovery

A stale handle must reject with a stable bridge error. It must not recreate or
re-attach an overlay window.

## Migration Guidance

Rhino:

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

Current Node profile:

```js
"nodejs";

throw new Error("floaty/overlay is not available in the current Node profile");
```

For in-app UI, migrate to the Activity-owned `require("ui").showLayout(...)`
provider. For clipboard reads, use:

```js
"nodejs";

(async function main() {
  const clipboard = require("clipboard");
  console.log(await clipboard.getText());
})();
```

## Rejected Compatibility

S11-24 rejects:

- default global `floaty`
- `compat.floaty`
- raw `JsWindow`, `JsResizableWindow`, or `JsRawWindow` wrappers
- XML inflation into overlay windows from Node
- raw `WindowManager.LayoutParams` flags or direct focus/touch controls
- hidden background overlays without foreground/user-visible disclosure
- overlay permission prompts launched from an isolated service process
- one-way packaged host fallbacks that create overlay windows without live
  bridge metadata
- global input capture, raw touch injection, key interception, or accessibility
  overlay authority through the floaty module

## Future Work

Implementation must wait for a separate task that adds a provider design and
tests from the gap list above. It should start with a constrained, non-raw,
JSON descriptor overlay surface and keep `floaty.rawWindow()` blocked unless a
new high-risk gate explicitly accepts raw overlay semantics.
