# Floaty Overlay Phase 12 Decision

Status: P12-11 decision record.

Selected path: Rejection path.

Provider path: not selected for Phase 12.

## Decision

Phase 12 will not implement exact Rhino `floaty.window()` or
`floaty.rawWindow()` compatibility in Node. Those APIs are a permanent non-goal
for the Safe Node Profile because they depend on XML inflation, live Rhino
window wrappers, raw Android `View` access, and unrestricted overlay window
flags.

This decision does not forbid a future independent overlay provider. Any future
provider must be a new JSON-only surface, such as `require("ui.overlay")`, with
opaque handles and its own security review. It must not claim parity with Rhino
`JsWindow`, `JsResizableWindow`, or `JsRawWindow`.

P12-12B is the follow-up path. It must add the negative runtime gate and
migration wording that make the rejection stable for script developers.

## Rationale

Rhino floaty is not only a layout API. It combines:

- draw-over-other-apps permission flow
- `SYSTEM_ALERT_WINDOW` packaged manifest policy
- foreground service and foreground disclosure requirements
- XML inflation into overlay windows
- raw `WindowManager.LayoutParams` behavior
- live `FloatyWindow` / `WindowBridge` / `NativeView` object access
- process, task, notification, and permission-revocation cleanup semantics

The current Node UI bridge intentionally uses Activity-owned JSON descriptors
and opaque `UiHandle` objects. Reusing that bridge for overlays would imply
overlay authority from the base `ui` capability, which is explicitly rejected.

## Acceptance Coverage

Permission:

- No `floaty`, `ui.overlay`, or `floaty.raw` capability is declared.
- No Node API requests or grants Android overlay permission.
- No service-process fallback opens overlay settings or emulates permission.

Foreground disclosure:

- No hidden overlay may be created from Node.
- A future overlay provider would need explicit foreground notification and
  user-visible ownership text before promotion.

Packaged manifest:

- Packaged APK templates must not inject `SYSTEM_ALERT_WINDOW` for Rhino
  compatibility.
- Older packaged templates without live bridge metadata must fail closed and
  must not queue overlay creation as a host side effect.

closeAll/destroy cleanup:

- Because no overlay provider exists, Node cannot create overlay resources.
- P12-12B must verify `floaty.closeAll()` stays unavailable and provider
  destroy has no overlay handles to drain or recreate.
- Any future provider must prove `handle.close()`, `closeAll()`, script stop,
  timeout, service destroy, notification stop, permission revocation, task
  removal, and process death cleanup before exposure.

Raw window object denial:

- `WindowManager`, `WindowManager.LayoutParams`, `FloatyService`,
  `FloatyWindow`, `WindowBridge`, `View`, `ViewGroup`, `NativeView`, Binder,
  and Java objects must never cross the Node bridge.
- `floaty.window()` and `floaty.rawWindow()` remain blocked_by_design.

## Migration

For in-app UI, migrate to `require("ui").showLayout(...)` or
`require("autojs6:compat").ui`.

For `floaty.getClip()`, migrate to `require("clipboard").getText()` or
`require("autojs6:compat").getClip()`.

For scripts that require overlay windows above other apps, keep the script on
Rhino until a separate JSON-only overlay provider is designed, implemented, and
gated. Do not expect `require("floaty")`, `require("ui.overlay")`,
`compat.floaty`, or a global `floaty` in Node.

## Evidence

- `docs/nodejs/design/RHINO_FLOATY_COMPATIBILITY.md`
- `docs/nodejs/design/FLOATY_NODE.md`
- `docs/nodejs/MIGRATION_FROM_RHINO.md`
- `docs/nodejs/SECURITY_MODEL.md`
- `docs/nodejs/rhino-compat/status-manifest.json`
- `docs/nodejs/RHINO_AUGMENT_COMPATIBILITY_MATRIX.md`
- `tools/nodejs/phase12/verify-rhino-compat-floaty-overlay-decision.js`
- `:app:verifyNodeRhinoCompatFloatyOverlayDecision`
