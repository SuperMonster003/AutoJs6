# P16-05 Overlay Provider Promotion

Status: partial

Promotion state: `provider_poc_not_promoted`

P16-05 imports P15-05 overlay provider POC evidence into the v1.4 promotion
lane. The gate is executable and keeps the provider unpromoted until
permission-granted Android 12/15+ archives, foreground disclosure/stop action,
owner cleanup stress, packaged positive smoke, OEM matrix, and downstream
security/type/docs sync are attached.

## Imported Evidence

- P14-08 controlled JSON-only runtime provider POC.
- Permission-denied and raw-object-denial boundaries.
- Conditional instrumentation fixture for permission-granted show/update/drain/close.
- P13 packaged overlay missing-capability boundary.
- P15-05 overlay provider partial gate.
- P16-02 promotion contracts and P16-03 disclosure/rollback audit for overlay.
- P16-04 live UI foundation gate.

## Open Blockers

| Blocker | Classification |
| --- | --- |
| `overlay_permission_granted_android12_archive` | `future_promotion` |
| `overlay_permission_granted_android15_archive` | `future_promotion` |
| `overlay_foreground_disclosure_stop_action_archive` | `future_promotion` |
| `overlay_owner_cleanup_leak_stress_archive` | `future_promotion` |
| `packaged_overlay_positive_smoke_archive` | `future_promotion` |
| `overlay_oem_matrix_archive` | `future_promotion` |
| `phase16_security_type_doc_sync` | `future_promotion` |

## Decision

The gate reports `partial` with zero policy violations. `overlay` remains
unpromoted and no runtime authority changes in P16-05.

## Gate

```powershell
.\gradlew.bat --console=plain :app:verifyNodePhase16OverlayProviderPromotion
```
