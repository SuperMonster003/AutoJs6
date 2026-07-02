# Phase 15 Overlay OEM Matrix

Machine-readable source:

- `docs/nodejs/phase15/overlay-oem-matrix-plan.json`

P15-06 imports the P14-09 overlay permission and OEM matrix into the v1.3
promotion lane. It keeps the `overlay` provider at
`provider_poc_not_promoted` while the required Android 12 class and Android
15+ class archives are still absent.

The gate is a promotion evidence contract, not a runtime authority change. It
requires the P14-09 matrix, the P15-05 overlay provider gate, and the Phase 15
promotion/disclosure contracts to agree before later device evidence can close
the matrix.

## Ready Evidence

- `phase14_oem_matrix_contract`
- `phase14_denied_boundary_import`
- `phase15_overlay_provider_promotion_gate`
- `phase15_promotion_contracts`
- `phase15_disclosure_rollback_contract`
- `phase15_surface_manifest_mapping`

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
| `phase15_security_type_doc_sync` | `future_promotion` | P15-11, P15-23, P15-25, and P15-26 sync before promotion. |

## Rollback

- Disable flag: `node.provider.overlay.enabled`
- Diagnostic code: `NODE_PROVIDER_OVERLAY_DISABLED`
- Safe fallback: deny overlay creation and clean owner-scoped overlay handles.
- Crash circuit breaker: `3 provider crashes within 24h per package/profile/capability`

## Verification

- Host verifier: `tools/nodejs/phase15/verify-node-phase15-overlay-oem-matrix.js`
- Gradle gate: `:app:verifyNodePhase15OverlayOemMatrix`
- Reports:
  - `build/reports/nodejs/phase15-overlay-oem-matrix.json`
  - `build/reports/nodejs/phase15-overlay-oem-matrix.md`
