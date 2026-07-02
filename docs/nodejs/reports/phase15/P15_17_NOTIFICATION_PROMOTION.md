# P15-17 Notification Promotion

Status: partial

P15-17 deploys the Phase 15 notification ownership and foreground disclosure
promotion gate. It imports the P14-22 live notification ownership contract,
permission/settings handoff, manifest denial, and raw-object boundary, then
binds them to the P15-16 packaged broadcast dependency gate and the Phase 15
promotion/disclosure/rollback bars for `notifications`.

This task remains unchecked because there is still no packaged post/cancel
archive, no foreground disclosure/stop-action archive, no Android permission
matrix archive, no advanced notification decision archive, no rollback cleanup
archive, no Safe downgrade archive, and no task/device/media docs sync archive.

## Implemented Evidence

- Plan source:
  `docs/nodejs/phase15/notification-promotion-plan.json`.
- Host verifier:
  `tools/nodejs/phase15/verify-node-phase15-notification-promotion.js`.
- Live ownership import:
  `notifications.notify`, `cancel`, `cancelAll`, `getPermissionStatus`, and
  `openSettings` remain live evidence for owned notification resources and
  JSON-safe permission/status snapshots.
- P15 dependency:
  `build/reports/nodejs/phase15-packaged-broadcast-smoke.json` keeps packaged
  broadcast and task/device foreground disclosure dependencies partial and
  unpromoted.
- Runtime boundary:
  Packaged notification foreground disclosure takeover, notification actions,
  groups, custom styles, inline replies, listeners, `RemoteViews`, DND
  mutation, raw notification objects, Android `Context`, `Intent`, and Java
  objects remain unpromoted.

## Remaining Blockers

| Blocker | Classification | Required evidence |
| --- | --- | --- |
| `notification_packaged_post_cancel_archive` | `future_promotion` | Packaged APK post/cancel/cancelAll archive proving package identity ownership, notification permission handling, relaunch, task removal cleanup, and Safe downgrade behavior. |
| `notification_foreground_disclosure_stop_action_archive` | `future_promotion` | Foreground disclosure and stop-action archive for ongoing packaged notifications and long-running or scheduled Node work. |
| `notification_permission_denial_device_matrix_archive` | `future_promotion` | Android 12 and Android 15+ permission-denied/granted matrix covering POST_NOTIFICATIONS, disabled app notifications, settings round-trip, and denial copy. |
| `notification_advanced_action_group_style_decision_archive` | `future_promotion` | Stable-denial or promotion archive for notification actions, groups, styles, inline reply, remote input, listeners, RemoteViews, and DND mutation. |
| `notification_rollback_cleanup_archive` | `future_promotion` | Rollback cleanup archive proving owned notification ids, channels, foreground disclosure metadata, and packaged downgrade behavior are cleaned without touching external notifications. |
| `notification_safe_downgrade_archive` | `future_promotion` | Provider-disable Safe downgrade proof that package-owned notifications are cancelled and future packaged disclosure metadata is disabled. |
| `notification_raw_object_denial_security_archive` | `future_promotion` | Security regression coverage proving raw Notification, NotificationChannel, PendingIntent, RemoteViews, StatusBarNotification, listener handle, Context, Intent, and Java objects remain denied. |
| `phase15_task_device_media_doc_sync` | `future_promotion` | P15-22, P15-23, P15-25, and P15-26 sync before notification ownership can be marked promoted. |

## Gate

Host verifier:

```powershell
node tools/nodejs/phase15/verify-node-phase15-notification-promotion.js --repo-root D:\idea-projects\AutoJs6-nodejs --report-dir D:\idea-projects\AutoJs6-nodejs\build\reports\nodejs
```

Gradle gate:

```powershell
.\gradlew.bat --console=plain :app:verifyNodePhase15NotificationPromotion
```
