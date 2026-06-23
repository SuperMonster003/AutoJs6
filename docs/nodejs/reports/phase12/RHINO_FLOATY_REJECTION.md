# Rhino Floaty Rejection Gate

Status: P12-12B negative runtime gate for the P12-11 Rejection path.

## Decision

Exact Rhino `floaty.window()` and `floaty.rawWindow()` compatibility is a
permanent non-goal for Node. The Node profile does not expose:

- `require("floaty")`
- `require("ui.overlay")`
- `compat.floaty`
- global `floaty`

The stable script-level marker for examples and dual-engine policy fixtures is
`compat.blockedByDesign(apiName, reason)`, with code
`ERR_AUTOJS6_RHINO_COMPAT_BLOCKED_BY_DESIGN` exposed as
`compat.errorCodes.blockedByDesign`.

Module-level runtime surfaces stay absent rather than becoming bridge calls.
That means `require("floaty")` and `require("ui.overlay")` must fail in the
resolver before any Android provider, permission request, overlay settings
intent, `SYSTEM_ALERT_WINDOW` manifest effect, or resource registry entry can
exist.

## Negative Runtime Evidence

- `NodeBridgeConformanceInstrumentationTest.kt` asserts
  `require("floaty")` and `require("ui.overlay")` are unavailable,
  `compat.floaty` is `undefined`, global `floaty` is absent before and after
  `compat.install({ globals: true })`, and `compat.status().availableGlobalNames`
  does not include `floaty`.
- `docs/nodejs/dual-engine-compat/cases/floaty-window.node.cjs` records
  `compat.blockedByDesign("floaty.window", ...)` and
  `compat.blockedByDesign("floaty.rawWindow", ...)`.
- `docs/nodejs/dual-engine-compat/manifest.json` maps the case to
  `floaty.window` and `floaty.rawWindow` status-manifest entries.
- `docs/nodejs/rhino-compat/status-manifest.json` keeps `floaty.window` and
  `floaty.rawWindow` as `blocked_by_design`, keeps permission and `closeAll`
  APIs unsupported, and keeps `floaty.getClip` renamed to clipboard.

## Migration

- In-app UI: use `require("ui").showLayout(...)` or
  `require("autojs6:compat").ui`.
- Clipboard: use `require("clipboard").getText()` or
  `require("autojs6:compat").getClip()` (`compat.getClip()` after assigning
  the module to `compat`).
- Overlay windows above other apps: keep the script on Rhino until a separate
  JSON-only overlay provider exists; do not expect Rhino raw window semantics
  in Node.

## Gate

Host gate: `verifyNodeRhinoCompatFloatyRejection`.

Suggested local command:

```powershell
.\gradlew.bat --console=plain :app:verifyNodeRhinoCompatFloatyRejection
```
