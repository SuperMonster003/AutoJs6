# P13-17 Live UI Provider Promotion

Status: partial

P13-17 records the current live `require("ui")` provider state for Phase 13.
The Activity-owned provider is available for explicit Node UI sessions, but the
surface is not promoted to `pro_compat_opt_in` yet because live packaged UI
entry, Recents/task relaunch, crash, and disclosure evidence are still missing.

## Implemented Evidence

- Runtime provider: `AndroidNodeBridgeCapabilityProvider` dispatches
  `ui.showLayout`, `ui.update`, `ui.batchUpdate`, `ui.drainEvents`, and
  `ui.close` through the main-process `NodeBridgeUiDispatchService`.
- Live Activity path: `NodeBridgeUiActivity` renders JSON descriptors for
  `Column`, `Row`, `Text`, `Button`, `Input`, `Image`, `ScrollView`, `List`,
  `Switch`, `Checkbox`, `Progress`, and `Tabs`.
- JavaScript surface: `require("ui")` exposes `showLayout`, `state`, `bind`,
  and opaque frozen `UiHandle` objects with `on`, `update`, `batchUpdate`, and
  `close`.
- Event delivery: events are bounded JSON snapshots drained through
  `drainEvents`; JavaScript receives no raw Android callback objects.
- Resource ownership: UI sessions register as `NodeBridgeResourceType.UI_SESSION`
  and are released on `ui.close` or provider destroy through the bridge resource
  registry.
- Back lifecycle: focused instrumentation calls `NodeBridgeUiActivity.onBackPressed()`
  and verifies the live provider emits `ui.lifecycle.back.reason=activity_destroyed`
  through `ui.drainEvents`; this is live Activity lifecycle evidence, not
  packaged task relaunch or cleanup promotion.
- Configuration-change policy: focused instrumentation verifies
  `NodeBridgeUiActivity` does not declare orientation/screen-size
  `configChanges`, keeps `screenOrientation` unspecified, is not exported, and
  is excluded from Recents. Configuration changes are explicitly not a P13-17 survivability contract.
  The current policy is Android destroy/recreate with no live UI session
  survival claim.
- Device evidence: on 2026-06-28, `QV710AF65F` / Sony XQ-AT72 / Android 12
  passed the focused instrumentation method
  `NodeUiBridgeInstrumentationTest#androidUiProviderRecordsBackActivityDestroyLifecycleEvent`
  with `-Pautojs.nodejs.test.deviceProfile=sony`.
- Packaged capability boundary:
  `EmbeddedNodePackagedApkSmokeTest#installAndRunUiCapabilityBoundaryEmbeddedNodePackagedApk`
  verifies packaged metadata without `node.permissions: ["ui"]` rejects
  `ui.showLayout` with `ERR_AUTOJS6_BRIDGE_CAPABILITY_NOT_DECLARED`, reports
  missing `ui`, keeps raw Android UI globals absent, and leaves no bridge
  dispatch pending.
- Rhino migration: `require("autojs6:compat").ui` wraps the same JSON provider;
  packaged P12 evidence covers fake-transport routing and
  `packaged.rhino_compat.compat_ui=true`.
- Type declarations: `docs/nodejs/types/autojs6-node/ui.d.ts` and the
  TypeScript smoke sample cover direct `ui` and `compat.ui` usage.

## Boundaries

- Base `ui` does not imply `ui.overlay`, floaty, WebView, or raw Android view
  authority.
- `RawView`, `WebView`, foreign handles, unsupported descriptors, and active
  UI session overflow reject with stable bridge errors.
- The provider registry now reports packaged UI support as
  `provider-dependent`; P13-17 must not claim live packaged UI promotion.
- Packaged UI metadata denial is covered, but packaged live UI entry, task
  relaunch, crash recovery, and user-visible disclosure remain unpromoted.

## Remaining Blockers

- Recents/task-removal Activity lifecycle instrumentation and packaged task
  relaunch evidence.
- Live packaged APK UI smoke covering entry, close, relaunch, crash, and
  user-visible disclosure evidence.

## Gate

Host verifier:

```powershell
node tools/nodejs/phase13/verify-live-ui-provider-promotion.js --repo-root D:\idea-projects\AutoJs6 --report-dir D:\idea-projects\AutoJs6\build\reports\nodejs
```

Gradle gate:

```powershell
.\gradlew.bat --console=plain :app:verifyNodePhase13LiveUiProviderPromotion
```

Expected reports:

```text
build/reports/nodejs/phase13-live-ui-provider-promotion.json
build/reports/nodejs/phase13-live-ui-provider-promotion.md
```
