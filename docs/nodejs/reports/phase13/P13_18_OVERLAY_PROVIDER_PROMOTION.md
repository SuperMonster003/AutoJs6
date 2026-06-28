# P13-18 Overlay Provider Promotion

Status: partial

P13-18 records the current `require("ui.overlay")` state for Phase 13. The
P12 overlay work already provides a JavaScript facade, type declarations,
capability checks, Android permission/settings live-gate evidence, packaged
`SYSTEM_ALERT_WINDOW` metadata records, and cleanup summaries. The surface is
not promoted to `pro_compat_opt_in` yet because live overlay creation remains
fail-closed for `show`, `update`, `drainEvents`, and per-handle `close`.

## Implemented Evidence

- JavaScript surface: `require("ui.overlay")` exposes JSON-only `show`,
  `update`, `drainEvents`, `close`, `closeAll`, `hasPermission`, and
  `openPermissionSettings` APIs over opaque overlay handles.
- Android live gate: the provider returns live permission snapshots through
  `hasPermission`, opens or dry-runs the system overlay settings intent through
  `openPermissionSettings`, and reports `closeAll` cleanup summaries.
- Capability policy: `ui.overlay`, `ui.overlay.permission`,
  `ui.overlay.foreground`, and `ui.overlay.packaged` are explicit capability
  names; base `ui` does not imply overlay authority.
- Packaged metadata: the app and project metadata record
  `android.permission.SYSTEM_ALERT_WINDOW`, and the provider registry labels
  packaged overlay support as provider-dependent.
- Packaged boundary:
  `EmbeddedNodePackagedApkSmokeTest#installAndRunOverlayCapabilityBoundaryEmbeddedNodePackagedApk`
  verifies packaged metadata without `ui.overlay` rejects
  `ui.overlay.show()` with `ERR_AUTOJS6_BRIDGE_CAPABILITY_NOT_DECLARED`, omits
  `android.permission.SYSTEM_ALERT_WINDOW`, keeps raw Android window globals
  absent, and leaves no bridge dispatch pending.
- Raw boundary: `floaty.window()`, `floaty.rawWindow()`, raw
  `WindowManager.LayoutParams`, hidden overlays, fully transparent overlays,
  raw `View`/`NativeView`, and raw `FloatyWindow`/`WindowBridge` handles remain
  unavailable to Node scripts.
- Type declarations: `docs/nodejs/types/autojs6-node/ui_overlay.d.ts` and the
  TypeScript smoke sample cover the direct `ui.overlay` module.

## Boundaries

- Live overlay creation remains fail-closed. `show`, `update`, `drainEvents`,
  and per-handle `close` require a promoted Android overlay provider.
- `require("floaty")`, `compat.floaty`, and global `floaty` remain absent.
- P13-18 does not claim exact Rhino `floaty.window()` or
  `floaty.rawWindow()` View parity.
- Scheduled/background launch surfaces still cannot create silent overlays.
- No raw Android window, view, context, binder, Java object, or Rhino
  `NativeView` crosses into Node.

## Remaining Blockers

- Implement an actual Android live overlay provider for
  `show`/`update`/`drainEvents`/`close`.
- Record overlay permission granted and denied device evidence, including OEM
  settings behavior.
- Connect foreground notification/disclosure ownership, stop action, and
  script/provider destroy cleanup for visible overlays.
- Add positive packaged APK install-run smoke with explicit overlay metadata,
  foreground disclosure, task removal cleanup, and relaunch or crash evidence.
- Add leak/stress evidence over real overlay windows and event queues.

The docs, compatibility notes, design records, surface manifest, and Gradle
gate are evidence-only for this partial decision. They do not add a separate
P13-18 blocker beyond the live overlay provider, permission matrix, foreground
disclosure/stop action, positive packaged overlay behavior, cleanup, and
leak/stress evidence above.

## Gate

Host verifier:

```powershell
node tools/nodejs/phase13/verify-overlay-provider-promotion.js --repo-root D:\idea-projects\AutoJs6 --report-dir D:\idea-projects\AutoJs6\build\reports\nodejs
```

Gradle gate:

```powershell
.\gradlew.bat --console=plain :app:verifyNodePhase13OverlayProviderPromotion
```

Expected reports:

```text
build/reports/nodejs/phase13-overlay-provider-promotion.json
build/reports/nodejs/phase13-overlay-provider-promotion.md
```
