# Phase 16 Overlay OEM Matrix

Machine-readable source:

- `docs/nodejs/phase16/overlay-oem-matrix-plan.json`

P16-06 imports the P15-06 overlay permission and OEM matrix into the v1.4
promotion lane. It keeps the `overlay` provider at
`provider_poc_not_promoted` while the required Android 12 class and Android
15+ class archives are still absent.

The gate is a promotion evidence contract, not a runtime authority change. It
requires the P14-09 matrix, the P15-06 source backlog gate, the P16-05 overlay
provider gate, and the Phase 16 promotion/disclosure contracts to agree before
later device evidence can close the matrix.

## Ready Evidence

- `phase14_oem_matrix_contract`
- `phase14_denied_boundary_import`
- `phase15_overlay_oem_matrix_partial_gate`
- `phase16_overlay_provider_promotion_gate`
- `phase16_promotion_contracts`
- `phase16_disclosure_rollback_contract`
- `phase16_surface_manifest_mapping`

## Required Device Classes

| Class | Requirement |
| --- | --- |
| `android_12_class` | Archived Android 12 class run covering granted and denied `SYSTEM_ALERT_WINDOW`, settings round-trip, task removal, relaunch, disclosure/stop action, and OEM notes. |
| `android_15_plus_class` | Archived Android 15+ class run covering granted and denied `SYSTEM_ALERT_WINDOW`, settings round-trip, task removal, relaunch, disclosure/stop action, and OEM notes. |

## Open Promotion Blockers

| Blocker | Classification | Required evidence |
| --- | --- | --- |
| `overlay_android12_granted_device_archive` | `future_promotion` | Android 12 granted-permission archive with denied-boundary copy. |
| `overlay_android15_plus_granted_device_archive` | `future_promotion` | Android 15+ granted-permission archive with denied-boundary copy. |
| `overlay_settings_round_trip_oem_notes` | `future_promotion` | OEM settings grant/revoke/return notes. |
| `overlay_task_removal_relaunch_evidence` | `future_promotion` | Task removal cleanup and package relaunch behavior. |
| `overlay_foreground_disclosure_stop_action_archive` | `future_promotion` | Foreground disclosure ownership and stop action behavior. |
| `phase16_security_type_doc_sync` | `future_promotion` | P16-11, P16-23, P16-25, and P16-26 sync before promotion. |

## Rollback

- Disable flag: `node.provider.overlay.enabled`
- Diagnostic code: `NODE_PROVIDER_OVERLAY_DISABLED`
- Safe fallback: deny overlay creation and clean owner-scoped overlay handles.
- Crash circuit breaker: `3 provider crashes within 24h per package/profile/capability`

## Verification

- Host verifier: `tools/nodejs/phase16/verify-node-phase16-overlay-oem-matrix.js`
- Gradle gate: `:app:verifyNodePhase16OverlayOemMatrix`
- Reports:
  - `build/reports/nodejs/phase16-overlay-oem-matrix.json`
  - `build/reports/nodejs/phase16-overlay-oem-matrix.md`
