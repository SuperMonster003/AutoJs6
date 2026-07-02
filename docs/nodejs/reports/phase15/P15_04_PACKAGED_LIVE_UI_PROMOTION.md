# P15-04 Packaged Live UI Promotion

Status: partial

Promotion state: `provider_poc_not_promoted`

P15-04 imports P14-07 live UI packaged POC evidence into the v1.3 promotion
lane. The gate is executable and keeps the provider unpromoted until the
remaining packaged live UI relaunch, task removal cleanup, provider crash
recovery, user-visible disclosure, Android 15+ device archive, and downstream
security/type/docs sync evidence are attached.

## Imported Evidence

- P13-17 packaged missing-capability denial and packaged entry/update/close
  smoke.
- P13-17 Android 12 live Activity Back and Recents lifecycle evidence.
- P14-07 provider POC report and generated `phase14-live-ui-provider-packaged`
  report.
- P15-02 promotion contracts and P15-03 disclosure/rollback audit.
- Generic packaged long-running crash evidence anchors for Stop action, task
  removal, relaunch, and next-launch crash collection. These are not
  live-ui-specific promotion evidence.

## Open Blockers

| Blocker | Classification |
| --- | --- |
| `packaged_live_ui_task_relaunch_archive` | `future_promotion` |
| `packaged_live_ui_task_removal_cleanup_archive` | `future_promotion` |
| `packaged_live_ui_crash_recovery_archive` | `future_promotion` |
| `packaged_live_ui_user_visible_disclosure_archive` | `future_promotion` |
| `android_15_plus_packaged_live_ui_archive` | `future_promotion` |
| `phase15_security_type_doc_sync` | `future_promotion` |

## Decision

The gate reports `partial` with zero policy violations. `live_ui` remains
unpromoted and no runtime authority changes in P15-04.

## Gate

```powershell
.\gradlew.bat --console=plain :app:verifyNodePhase15PackagedLiveUiPromotion
```
