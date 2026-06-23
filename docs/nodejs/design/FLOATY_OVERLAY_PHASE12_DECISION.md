# Floaty Overlay Phase 12 Decision

Status: P12-11 decision record.

Selected path: Provider path.

Rejection path: not selected for Phase 12.

## Decision

Phase 12 will pursue a controlled Node overlay provider. The provider path is
not Rhino `floaty.window()` / `floaty.rawWindow()` parity: exact Rhino XML
inflation, live Rhino window wrappers, raw Android `View` access, and
unrestricted overlay window flags remain a permanent non-goal for the Safe Node
Profile.

The selected path is a JSON-only overlay surface, preferably
`require("ui.overlay")`, with opaque handles, explicit capability declaration,
permission/disclosure policy, packaged manifest policy, and audited cleanup. It
must not claim parity with Rhino `JsWindow`, `JsResizableWindow`, or
`JsRawWindow`.

P12-12A is the follow-up path. It must define the overlay provider v1 contract
before any runtime module, capability, type declaration, or packaged overlay
behavior is promoted. P12-13 may then add a fake-provider facade POC, and
P12-14 may add live overlay packaged/cleanup evidence.

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
and opaque `UiHandle` objects. The provider path keeps that separation: overlay
authority must come from an explicit overlay capability and provider, never from
the base `ui` capability.

## Acceptance Coverage

Permission:

- P12-12A must define an explicit overlay capability such as `ui.overlay`.
- The base `ui` capability must not imply overlay authority.
- The provider must check Android overlay permission and may open settings only
  through explicit user action.
- No service-process fallback may grant, emulate, or silently request overlay
  permission.

Foreground disclosure:

- No hidden overlay may be created from Node.
- The provider contract must require foreground notification and user-visible
  ownership text where Android policy or script lifetime requires it.

Packaged manifest:

- Packaged APK templates may inject `SYSTEM_ALERT_WINDOW` only from explicit
  project/package metadata for the overlay provider.
- Older packaged templates without live bridge metadata must fail closed and
  must not queue overlay creation as a host side effect.

closeAll/destroy cleanup:

- P12-12A must define provider-owned overlay handles and `closeAll()` ownership
  rules.
- The provider must prove `handle.close()`, `closeAll()`, script stop, timeout,
  service destroy, notification stop, permission revocation, task removal, and
  process death cleanup before live exposure.

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
Rhino until the JSON-only overlay provider contract, fake-provider POC, and live
packaged/cleanup gates land. Do not expect exact Rhino `floaty.window()` or
`floaty.rawWindow()` semantics in Node.

## Evidence

- `docs/nodejs/design/RHINO_FLOATY_COMPATIBILITY.md`
- `docs/nodejs/design/FLOATY_NODE.md`
- `docs/nodejs/MIGRATION_FROM_RHINO.md`
- `docs/nodejs/SECURITY_MODEL.md`
- `docs/nodejs/rhino-compat/status-manifest.json`
- `docs/nodejs/RHINO_AUGMENT_COMPATIBILITY_MATRIX.md`
- `tools/nodejs/phase12/verify-rhino-compat-floaty-overlay-decision.js`
- `:app:verifyNodeRhinoCompatFloatyOverlayDecision`
