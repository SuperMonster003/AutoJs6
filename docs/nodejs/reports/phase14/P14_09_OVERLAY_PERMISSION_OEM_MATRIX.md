# P14-09 Overlay Permission OEM Matrix

Status: partial

P14-09 deploys the overlay permission and OEM evidence matrix for the Phase 14
`ui.overlay` provider POC. The gate imports the P14-08 permission-denied
boundary and reserves explicit Android 12 class and Android 15+ class archive
rows for permission-granted overlay behavior.

The task remains unchecked because the required real-device archives are not
present yet. The matrix is a promotion contract and report gate, not a
promotion claim.

## Implemented Evidence

- Matrix source: `docs/nodejs/phase14/overlay-permission-oem-matrix.json`
  records required Android 12 and Android 15+ device classes, required Phase 14
  device fields, required overlay scenarios, current evidence rows, and open
  blockers.
- Matrix docs: `docs/nodejs/phase14/PHASE14_OVERLAY_PERMISSION_OEM_MATRIX.md`
  mirrors the required rows and remaining evidence.
- Imported denial boundary: P14-08 verifies `ui.overlay.show` fails closed
  without `SYSTEM_ALERT_WINDOW`, keeps active overlay session count at zero,
  and preserves raw Android object denial.
- Surface state: `pro.ui_automation_media` remains `provider_poc`, not
  `pro_compat_opt_in` promoted.
- Gate wiring: `:app:verifyNodePhase14OverlayPermissionOemMatrix` depends on
  `:app:verifyNodePhase14OverlayProvider` and writes deterministic JSON/MD
  reports.

## Remaining Blockers

| Blocker | Classification | Required evidence |
| --- | --- | --- |
| `overlay_android12_granted_device_archive` | `future_promotion` | Archived Android 12 class device run with `SYSTEM_ALERT_WINDOW` granted and `show`/`update`/`drainEvents`/`close` plus denial copy recorded. |
| `overlay_android15_plus_granted_device_archive` | `future_promotion` | Archived Android 15+ class device run with `SYSTEM_ALERT_WINDOW` granted and `show`/`update`/`drainEvents`/`close` plus denial copy recorded. |
| `overlay_settings_round_trip_oem_notes` | `future_promotion` | OEM-specific overlay settings round-trip notes for permission grant, revoke, and returned permission snapshot. |
| `overlay_task_removal_relaunch_evidence` | `future_promotion` | Task removal cleanup and package relaunch behavior over visible overlay sessions on the required device matrix. |

## Gate

Host verifier:

```powershell
node tools/nodejs/phase14/verify-node-phase14-overlay-permission-oem-matrix.js --repo-root D:\idea-projects\AutoJs6-nodejs --report-dir D:\idea-projects\AutoJs6-nodejs\build\reports\nodejs
```

Gradle gate:

```powershell
.\gradlew.bat --console=plain :app:verifyNodePhase14OverlayPermissionOemMatrix
```

Expected reports:

```text
build/reports/nodejs/phase14-overlay-permission-oem-matrix.json
build/reports/nodejs/phase14-overlay-permission-oem-matrix.md
```
