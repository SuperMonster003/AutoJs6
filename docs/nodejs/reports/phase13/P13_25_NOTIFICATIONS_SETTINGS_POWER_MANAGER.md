# P13-25 Notifications/Settings/Power Manager

- Date: 2026-06-24
- Status: partial
- Gate: `verifyNodePhase13NotificationsSettingsPowerManager`

P13-25 records a partial promotion for the Node notifications/settings and
device power-manager surface. The implemented scope is intentionally narrow:
Node can inspect notification permission state, dry-run or open the app
notification settings page, inspect Android battery-optimization exemption
state, and dry-run or open the battery-optimization settings page.

## Landed Scope

- `notifications.getPermissionStatus()` returns a JSON-safe permission snapshot
  with schema `autojs6-node-notification-permission-status-v1`.
- `notifications.openSettings()` and `notifications.openNotificationSettings()`
  return the same permission snapshot plus dry-run/open result fields.
- `device.isIgnoringBatteryOptimizations()` returns the Android
  `PowerManager.isIgnoringBatteryOptimizations()` state when available.
- `device.openBatteryOptimizationSettings()` returns a JSON-safe
  `autojs6-node-battery-optimization-settings-v1` result and supports
  `{ dryRun: true }` for tests.
- `notifications.settings` and `device.power` are explicit capability tokens.
  They are not implied by base `notifications` or `device` declarations.
- Provider registry details now expose the P13-25 policy anchors and keep raw
  Android manager/builder/intent/wakelock objects out of the Node surface.

## Evidence

- Kotlin bridge surface:
  `app/src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt`
- Capability manifest:
  `app/src/main/java/org/autojs/autojs/engine/NodeBridgePermissionManifest.kt`
- JS facade:
  `app/src/main/cpp/node_bridge_sources.cpp`
- Provider registry:
  `app/src/main/java/org/autojs/autojs/engine/NodeBridgeProviderRegistry.kt`
- Type declarations:
  `docs/nodejs/types/autojs6-node/notifications.d.ts`,
  `docs/nodejs/types/autojs6-node/device.d.ts`
- Tests:
  `NodeNotificationsBridgeInstrumentationTest`,
  `NodeDeviceBridgeInstrumentationTest`,
  `NodeBridgeConformanceInstrumentationTest`,
  `NodeBridgePermissionManifestInstrumentationTest`, and
  `NodeBridgeProviderRegistryInstrumentationTest`
- Host verifier:
  `tools/nodejs/phase13/verify-notifications-settings-power-manager.js`
- Gradle gate:
  `:app:verifyNodePhase13NotificationsSettingsPowerManager`

## Boundaries

The P13-25 partial gate does not expose raw Android notification or power
objects. JavaScript receives only JSON-safe snapshots and booleans. No raw
`Notification`, `NotificationCompat.Builder`, `NotificationManager`,
`PendingIntent`, `RemoteViews`, `Intent`, `PowerManager`, `WakeLock`,
`Context`, account, network, or hardware identifier object crosses the bridge.

Opening settings remains a user-visible Android settings handoff. Tests use
`dryRun` so host/device gates do not launch Settings during automation.

## Remaining Blockers

- Notification actions, grouped notifications, custom styles, listeners,
  remote input, inline reply, foreground disclosure, Do-Not-Disturb, and exact
  alarm policy.
- Direct power-manager APIs, wake locks, power-save callbacks, request-ignore
  battery-optimization flow, OEM power-manager behavior, and device matrix
  evidence.
- Packaged notification/settings/power disclosure, install/run smoke, rollback
  diagnostics, and migration examples.

## Verification

```powershell
node tools\nodejs\phase13\verify-notifications-settings-power-manager.js --repo-root D:\idea-projects\AutoJs6 --report-dir D:\idea-projects\AutoJs6\build\reports\nodejs
.\gradlew.bat --console=plain :app:verifyNodePhase13NotificationsSettingsPowerManager
.\gradlew.bat --console=plain :app:verifyAutoJs6NodeTypeDeclarations :app:typeCheckNodeTypescriptSmoke
```
