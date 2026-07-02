# P14-08 Overlay Provider V1

Status: partial

promotionState: provider_poc

P14-08 adds a controlled JSON-only `require("ui.overlay")` provider POC for
`show`, `update`, `drainEvents`, `close`, and `closeAll`. The provider creates
provider-owned Android overlay sessions only when `SYSTEM_ALERT_WINDOW` is
already granted by the user, returns JSON snapshots/events, registers
`NodeBridgeResourceType.OVERLAY_SESSION`, and fails closed without raw Android
window or view objects.

The surface is not promoted to `pro_compat_opt_in` yet. Permission-granted
device evidence, foreground disclosure/stop action, packaged positive smoke,
and leak/stress evidence remain open Phase 14 work.

## Implemented Evidence

- Runtime provider: `AndroidNodeBridgeCapabilityProvider.dispatchUiOverlay`
  now routes `show`, `update`, `drainEvents`, `close`, and `closeAll` to a
  controlled `ControlledOverlaySession`.
- Permission boundary: `show` checks `Settings.canDrawOverlays` and rejects
  without `SYSTEM_ALERT_WINDOW` using `ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED`.
- Session ownership: each live overlay registers
  `NodeBridgeResourceType.OVERLAY_SESSION` and is scoped to the current Node
  execution.
- Cleanup: `close`, `closeAll`, and provider destroy remove provider-owned
  overlay sessions and release resource registry handles.
- JSON-only surface: snapshots expose `id`, `type`, `closed`, `visible`,
  `createdAt`, `bounds`, `disclosure.title`, and `droppedCount`; drained events
  expose JSON-only `type`, `overlayId`, `targetId`, `eventTime`, and optional
  `reason`.
- Raw boundary: Node scripts still receive no raw `WindowManager`,
  `LayoutParams`, `FloatyWindow`, `WindowBridge`, `View`, `Context`, or Java
  object.
- Conditional device test:
  `NodeOverlayBridgeInstrumentationTest#kotlinOverlayRegistryManifestAndProviderPocAreStable`
  verifies denied behavior without overlay permission and exercises
  show/update/drain/close when `SYSTEM_ALERT_WINDOW` is already granted.

## Open Blockers

| Blocker | Classification | Required evidence |
| --- | --- | --- |
| `overlay_permission_granted_device_evidence` | `future_promotion` | Archived Android device run with `SYSTEM_ALERT_WINDOW` granted and show/update/drain/close passing. |
| `overlay_foreground_disclosure_stop_action` | `future_promotion` | Foreground notification/disclosure ownership and stop action behavior for visible overlays. |
| `packaged_overlay_positive_smoke` | `future_promotion` | Packaged APK install-run smoke with explicit overlay metadata, foreground disclosure, cleanup, task removal, and relaunch/crash evidence. |
| `overlay_leak_stress_evidence` | `future_promotion` | Real overlay leak/stress run proving zero remaining sessions and bounded event queues. |

## Decision

The `pro.ui_automation_media` surface remains `provider_poc`. P14-08 proves the
runtime provider path exists, but v1.2 opt-in promotion must wait for P14-09 and
P14-10 device/packaged evidence plus foreground disclosure and stress coverage.

## Gate

Host verifier:

```powershell
node tools/nodejs/phase14/verify-node-phase14-overlay-provider.js --repo-root D:\idea-projects\AutoJs6-nodejs --report-dir D:\idea-projects\AutoJs6-nodejs\build\reports\nodejs
```

Gradle gate:

```powershell
.\gradlew.bat --console=plain :app:verifyNodePhase14OverlayProvider
```

Expected reports:

```text
build/reports/nodejs/phase14-overlay-provider.json
build/reports/nodejs/phase14-overlay-provider.md
```
