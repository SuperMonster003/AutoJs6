# Phase 15 Surface Manifest

`phase15-surface-manifest.json` records task ownership for Node Profile v1.3
provider promotion work. It starts from the Phase 14 v1.2 closeout and imports
only the v1.3 future-promotion backlog, not v1.2 release blockers.

Machine-readable source:

- `docs/nodejs/phase15/phase15-surface-manifest.json`

## Owner Surfaces

| Owner surface | Profiles | Risk | Tasks |
| --- | --- | --- | --- |
| `phase15.baseline` | `safe_default` | `low` | `P15-00` |
| `phase15.surface_manifest` | `safe_default` | `low` | `P15-01` |
| `phase15.promotion_contracts` | `safe_default`, `pro_compat_opt_in`, `desktop_compat_opt_in`, `debug_unsafe_lab` | `high` | `P15-02` |
| `phase15.disclosure_rollback_audit` | `safe_default`, `pro_compat_opt_in`, `desktop_compat_opt_in` | `high` | `P15-03` |
| `pro.ui_overlay_automation_media` | `pro_compat_opt_in` | `high` | `P15-04` to `P15-11` |
| `pro.tasks_device_media` | `pro_compat_opt_in` | `high` | `P15-12` to `P15-22` |
| `security.types.packaged_migration` | `safe_default`, `pro_compat_opt_in`, `desktop_compat_opt_in`, `debug_unsafe_lab` | `critical` | `P15-23` to `P15-26` |
| `phase15.summary_handoff` | `safe_default`, `pro_compat_opt_in`, `desktop_compat_opt_in` | `medium` | `P15-27` |

## Imported Backlog Ownership

| Phase 14 backlog | Phase 15 task | Owner surface |
| --- | --- | --- |
| `P14-07` | `P15-04` | `pro.ui_overlay_automation_media` |
| `P14-08` | `P15-05` | `pro.ui_overlay_automation_media` |
| `P14-09` | `P15-06` | `pro.ui_overlay_automation_media` |
| `P14-10` | `P15-07` | `pro.ui_overlay_automation_media` |
| `P14-11`, `P14-12` | `P15-08` | `pro.ui_overlay_automation_media` |
| `P14-13` | `P15-09` | `pro.ui_overlay_automation_media` |
| `P14-14`, `P14-15` | `P15-10` | `pro.ui_overlay_automation_media` |
| `P14-17` | `P15-12` | `pro.tasks_device_media` |
| `P14-18` | `P15-13` | `pro.tasks_device_media` |
| `P14-19` | `P15-14` | `pro.tasks_device_media` |
| `P14-20` | `P15-15` | `pro.tasks_device_media` |
| `P14-21` | `P15-16` | `pro.tasks_device_media` |
| `P14-22` | `P15-17` | `pro.tasks_device_media` |
| `P14-23` | `P15-18` | `pro.tasks_device_media` |
| `P14-24` | `P15-19` | `pro.tasks_device_media` |
| `P14-25` | `P15-20` | `pro.tasks_device_media` |
| `P14-26` | `P15-21` | `pro.tasks_device_media` |

## Rules

- Every `P15-00` through `P15-27` task maps exactly once.
- Every imported `P14` backlog task maps to at least one `P15` owner task.
- Every task declares owner surface, source backlog task list, profile, risk,
  evidence class, gate, and rollback hook.
- High and critical risk tasks must keep rollback hooks explicit before runtime
  authority changes.
- Critical tasks must remain planned until security, packaged aggregate, docs,
  type declarations, and release evidence all agree.

## Verification

- Host verifier: `tools/nodejs/phase15/verify-node-phase15-surface-manifest.js`
- Gradle gate: `:app:verifyNodePhase15SurfaceManifest`
- Schema: `docs/nodejs/schemas/phase15_surface_manifest.schema.json`
- Reports:
  - `build/reports/nodejs/phase15-surface-manifest-verification.json`
  - `build/reports/nodejs/phase15-surface-manifest-verification.md`
