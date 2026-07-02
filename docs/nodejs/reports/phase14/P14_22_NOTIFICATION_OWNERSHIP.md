# P14-22 Notification Ownership

Status: partial.

P14-22 deploys the notification ownership and foreground disclosure evidence
gate. It imports P13-25 notification/settings evidence and the live Android
provider smoke for `notify`, `cancel`, `cancelAll`, `getPermissionStatus`, and
`openSettings`.

The current runtime evidence covers owned post/cancel behavior, permission
status/settings handoff, manifest capability denial, and raw object denial. The
remaining packaged post/cancel, foreground disclosure/stop-action, permission
matrix, advanced notification decision, and rollback cleanup archives stay
future-promotion work.

## Deployed

- Added `docs/nodejs/phase14/notification-ownership-plan.json`.
- Added `docs/nodejs/phase14/PHASE14_NOTIFICATION_OWNERSHIP.md`.
- Added `tools/nodejs/phase14/verify-node-phase14-notification-ownership.js`.
- Added Gradle gate `:app:verifyNodePhase14NotificationOwnership`.
- Recorded owner-scoped notification id handling, update release, cancel,
  cancelAll, provider destroy cleanup, and `MAX_ACTIVE_NOTIFICATIONS`.
- Recorded foreground disclosure and stop-action requirements for future
  packaged or long-running notification-backed Node work.
- Recorded advanced notification stable-denial/future-decision scope for
  actions, groups, styles, inline reply, remote input, listeners, `RemoteViews`,
  and DND mutation.

## Open Future-Promotion Archives

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
