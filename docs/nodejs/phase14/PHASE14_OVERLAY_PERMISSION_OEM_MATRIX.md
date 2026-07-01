# Phase 14 Overlay Permission OEM Matrix

Machine-readable source:

- `docs/nodejs/phase14/overlay-permission-oem-matrix.json`

## Status

Status: partial

P14-09 defines the evidence matrix required before the `ui.overlay` provider
POC can move beyond `provider_poc`. The current repository evidence imports
the P14-08 permission-denied provider boundary and reserves the required
Android 12 class and Android 15+ class device archive rows.

This matrix does not claim `pro_compat_opt_in` promotion. The Android
permission-granted device archives, OEM settings notes, and task
removal/relaunch behavior are still future-promotion evidence.

## Required Device Classes

| Class | Requirement |
| --- | --- |
| `android_12_class` | Archived Android 12 class run covering granted and denied `SYSTEM_ALERT_WINDOW`, settings round-trip, task removal, relaunch, and OEM notes. |
| `android_15_plus_class` | Archived Android 15+ class run covering granted and denied `SYSTEM_ALERT_WINDOW`, settings round-trip, task removal, relaunch, and OEM notes. |

## Required Overlay Scenarios

- `SYSTEM_ALERT_WINDOW` denied fail-closed copy
- `SYSTEM_ALERT_WINDOW` granted `show`/`update`/`drainEvents`/`close`
- `openPermissionSettings` settings round-trip
- task removal cleanup
- package relaunch behavior
- OEM permission/settings behavior notes

## Current Rows

| Row | Device class | Status | Notes |
| --- | --- | --- | --- |
| `p14_08_denied_boundary_import` | `host_or_instrumentation_import` | `imported_denied_boundary` | Imports the P14-08 provider fail-closed behavior without `SYSTEM_ALERT_WINDOW`. |
| `android_12_overlay_permission_oem_archive` | `android_12_class` | `missing_device_archive` | Reserved for Android 12 granted/denied/settings/task-removal/relaunch evidence. |
| `android_15_plus_overlay_permission_oem_archive` | `android_15_plus_class` | `missing_device_archive` | Reserved for Android 15+ granted/denied/settings/task-removal/relaunch evidence. |

## Open Blockers

| Blocker | Classification | Required evidence |
| --- | --- | --- |
| `overlay_android12_granted_device_archive` | `future_promotion` | Archived Android 12 class device run with `SYSTEM_ALERT_WINDOW` granted and `show`/`update`/`drainEvents`/`close` plus denial copy recorded. |
| `overlay_android15_plus_granted_device_archive` | `future_promotion` | Archived Android 15+ class device run with `SYSTEM_ALERT_WINDOW` granted and `show`/`update`/`drainEvents`/`close` plus denial copy recorded. |
| `overlay_settings_round_trip_oem_notes` | `future_promotion` | OEM-specific overlay settings round-trip notes for permission grant, revoke, and returned permission snapshot. |
| `overlay_task_removal_relaunch_evidence` | `future_promotion` | Task removal cleanup and package relaunch behavior over visible overlay sessions on the required device matrix. |

## Verification

- Host verifier: `tools/nodejs/phase14/verify-node-phase14-overlay-permission-oem-matrix.js`
- Gradle gate: `:app:verifyNodePhase14OverlayPermissionOemMatrix`
- Reports:
  - `build/reports/nodejs/phase14-overlay-permission-oem-matrix.json`
  - `build/reports/nodejs/phase14-overlay-permission-oem-matrix.md`
