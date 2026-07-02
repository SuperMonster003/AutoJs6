# P15-06 Overlay OEM Matrix

Status: partial

Promotion state: `provider_poc_not_promoted`

P15-06 imports the P14-09 overlay permission and OEM matrix into the v1.3
promotion lane. The gate is executable and keeps the provider unpromoted while
Android 12/15+ permission-granted archives, OEM settings round-trip notes, task
removal/relaunch behavior, foreground disclosure/stop action, and downstream
security/type/docs sync are still absent.

## Imported Evidence

- P14-09 overlay permission/OEM matrix contract.
- P14-08 permission-denied boundary imported through the P14-09 matrix.
- P15-05 overlay provider promotion gate and `overlay_oem_matrix_archive`
  blocker.
- Phase 15 provider promotion contracts.
- Phase 15 disclosure/rollback requirements for overlay.
- P15 surface manifest mapping for `device_oem_matrix` evidence.

## Open Blockers

| Blocker | Classification |
| --- | --- |
| `overlay_android12_granted_device_archive` | `future_promotion` |
| `overlay_android15_plus_granted_device_archive` | `future_promotion` |
| `overlay_settings_round_trip_oem_notes` | `future_promotion` |
| `overlay_task_removal_relaunch_evidence` | `future_promotion` |
| `overlay_foreground_disclosure_stop_action_archive` | `future_promotion` |
| `phase15_security_type_doc_sync` | `future_promotion` |

## Decision

The gate reports `partial` with zero policy violations. `overlay` remains
unpromoted and no runtime authority changes in P15-06.

## Gate

```powershell
.\gradlew.bat --console=plain :app:verifyNodePhase15OverlayOemMatrix
```
