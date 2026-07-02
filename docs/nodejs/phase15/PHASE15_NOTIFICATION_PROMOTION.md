# Phase 15 Notification Promotion

Machine-readable source:

- `docs/nodejs/phase15/notification-promotion-plan.json`

P15-17 imports the P14-22 notification ownership and foreground disclosure
contract into the v1.3 promotion lane. It keeps `notifications` at
`provider_poc_not_promoted` while packaged post/cancel, foreground disclosure,
stop action, Android permission matrix, rollback cleanup, Safe downgrade,
advanced notification boundary, security, type, docs, and release evidence are
still absent.

The gate does not promote packaged notification foreground disclosure takeover,
notification action callbacks, groups, custom styles, inline replies,
listeners, `RemoteViews`, DND mutation, raw `Notification`, raw
`NotificationChannel`, raw `PendingIntent`, raw `StatusBarNotification`,
Android `Context`, `Intent`, or Java objects.

## Ready Evidence

- `phase14_notification_ownership_contract`
- `phase15_packaged_broadcast_dependency_gate`
- `phase15_promotion_contracts`
- `phase15_disclosure_rollback_contract`
- `phase15_surface_manifest_mapping`

## Evidence Matrix

| Row | Status | Policy |
| --- | --- | --- |
| `live_post_cancel_owned_resource` | `imported` | Existing live provider owns notification ids through ResourceRegistry and cleans active records. |
| `permission_status_settings_handoff` | `imported` | Existing permission/status settings handoff remains JSON-safe. |
| `manifest_capability_denials` | `imported` | Missing `notifications` and `notifications.settings` capabilities reject before bridge dispatch. |
| `future_packaged_post_cancel` | `future_promotion` | Packaged post/cancel/cancelAll with package ownership remains future work. |
| `future_foreground_disclosure_stop_action` | `future_promotion` | User-visible disclosure and stop action remain future work. |
| `future_permission_device_matrix` | `future_promotion` | Android permission-granted/denied matrix remains future work. |
| `future_advanced_notification_decision` | `future_promotion` | Actions, groups, styles, inline reply, listeners, RemoteViews, and DND require future denial/promotion decisions. |
| `future_rollback_cleanup_safe_downgrade` | `future_promotion` | Rollback cleanup and Safe downgrade remain future work. |

## Open Promotion Blockers

| Blocker | Classification | Required evidence |
| --- | --- | --- |
| `notification_packaged_post_cancel_archive` | `future_promotion` | Packaged post/cancel/cancelAll archive. |
| `notification_foreground_disclosure_stop_action_archive` | `future_promotion` | Foreground disclosure and stop-action archive. |
| `notification_permission_denial_device_matrix_archive` | `future_promotion` | Android 12 and Android 15+ notification permission matrix archive. |
| `notification_advanced_action_group_style_decision_archive` | `future_promotion` | Advanced notification stable-denial or promotion archive. |
| `notification_rollback_cleanup_archive` | `future_promotion` | Owned notification rollback cleanup archive. |
| `notification_safe_downgrade_archive` | `future_promotion` | Provider-disable Safe downgrade proof. |
| `notification_raw_object_denial_security_archive` | `future_promotion` | Raw notification object denial security archive. |
| `phase15_task_device_media_doc_sync` | `future_promotion` | P15-22, P15-23, P15-25, and P15-26 sync before promotion. |

## Verification

- Host verifier: `tools/nodejs/phase15/verify-node-phase15-notification-promotion.js`
- Gradle gate: `:app:verifyNodePhase15NotificationPromotion`
- Reports:
  - `build/reports/nodejs/phase15-notification-promotion.json`
  - `build/reports/nodejs/phase15-notification-promotion.md`
