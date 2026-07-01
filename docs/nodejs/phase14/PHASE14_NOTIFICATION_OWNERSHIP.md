# Phase 14 Notification Ownership

Status: partial.

P14-22 records notification ownership and foreground disclosure evidence for
Node Profile v1.2. It imports the P13-25 notifications/settings provider state
and the live Android notification provider smoke. The current provider already
supports `notify`, `cancel`, `cancelAll`, `getPermissionStatus`, and
`openSettings` over JSON-safe descriptors and snapshots.

This gate does not promote packaged notification post/cancel behavior or
foreground disclosure/stop-action behavior yet. It also does not promote raw
notification actions, listener callbacks, groups, custom styles, inline reply,
remote input, `RemoteViews`, DND mutation, or raw Android notification objects.

## Imported Evidence

- `NodeNotificationsBridgeInstrumentationTest#androidProviderPostsOrReportsNotificationPermissionAndTracksOwnedResource`
  posts or reports `ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED`, registers a
  `NOTIFICATION` resource, verifies cancel cleanup, and verifies provider
  destroy cleanup for owned active records.
- `notifications.getPermissionStatus()` and `notifications.openSettings()`
  return `autojs6-node-notification-permission-status-v1` JSON snapshots.
- Missing `notifications` and `notifications.settings` capabilities reject
  before bridge dispatch with `ERR_AUTOJS6_BRIDGE_CAPABILITY_NOT_DECLARED`.
- Provider registry policy keeps raw `Notification`, `NotificationCompat`
  builder, `NotificationManager`, `PendingIntent`, `RemoteViews`, `Intent`,
  `Context`, listener handles, and Java objects out of Node.

## Ownership Policy

Notification ids are provider-owned bridge resources. `notify` with an
existing owned id releases the old record before registering a replacement.
`cancel` closes only active owned records; unknown ids are a no-op. `cancelAll`
closes every active notification record owned by the provider. Provider destroy
closes owned active records and cancels the corresponding notifications.

## Open Archives

- `notification_packaged_post_cancel_archive`
- `notification_foreground_disclosure_stop_action_archive`
- `notification_permission_denial_device_matrix_archive`
- `notification_advanced_action_group_style_decision_archive`
- `notification_rollback_cleanup_archive`

## Gate

Host verifier:

```powershell
node tools/nodejs/phase14/verify-node-phase14-notification-ownership.js --repo-root D:\idea-projects\AutoJs6-nodejs --report-dir D:\idea-projects\AutoJs6-nodejs\build\reports\nodejs
```

Gradle gate:

```powershell
.\gradlew.bat --console=plain :app:verifyNodePhase14NotificationOwnership
```

Expected reports:

```text
build/reports/nodejs/phase14-notification-ownership.json
build/reports/nodejs/phase14-notification-ownership.md
```
