# Phase 16 Surface Manifest

`phase16-surface-manifest.json` records task ownership for Node Profile v1.4
provider promotion work. It starts from the Phase 15 v1.3 summary handoff and
imports the v1.4 future-promotion backlog, not v1.3 release blockers.

Machine-readable source:

- `docs/nodejs/phase16/phase16-surface-manifest.json`

## Owner Surfaces

| Owner surface | Profiles | Risk | Tasks |
| --- | --- | --- | --- |
| `phase16.baseline` | `safe_default` | `low` | `P16-00` |
| `phase16.surface_manifest` | `safe_default` | `low` | `P16-01` |
| `phase16.promotion_contracts` | `safe_default`, `pro_compat_opt_in`, `desktop_compat_opt_in`, `debug_unsafe_lab` | `high` | `P16-02` |
| `phase16.disclosure_rollback_audit` | `safe_default`, `pro_compat_opt_in`, `desktop_compat_opt_in` | `high` | `P16-03` |
| `pro.ui_overlay_automation_media` | `pro_compat_opt_in` | `high` | `P16-04` to `P16-11` |
| `pro.tasks_device_media` | `pro_compat_opt_in` | `high` | `P16-12` to `P16-22` |
| `security.types.packaged_migration` | `safe_default`, `pro_compat_opt_in`, `desktop_compat_opt_in`, `debug_unsafe_lab` | `critical` | `P16-23` to `P16-26` |
| `phase16.summary_handoff` | `safe_default`, `pro_compat_opt_in`, `desktop_compat_opt_in` | `medium` | `P16-27` |

## Imported Backlog Ownership

| Phase 15 backlog | Phase 16 task | Owner surface |
| --- | --- | --- |
| `P15-04` | `P16-04` | `pro.ui_overlay_automation_media` |
| `P15-05` | `P16-05` | `pro.ui_overlay_automation_media` |
| `P15-06` | `P16-06` | `pro.ui_overlay_automation_media` |
| `P15-07` | `P16-07` | `pro.ui_overlay_automation_media` |
| `P15-08` | `P16-08` | `pro.ui_overlay_automation_media` |
| `P15-09` | `P16-09` | `pro.ui_overlay_automation_media` |
| `P15-10` | `P16-10` | `pro.ui_overlay_automation_media` |
| `P15-12` | `P16-12` | `pro.tasks_device_media` |
| `P15-13` | `P16-13` | `pro.tasks_device_media` |
| `P15-14` | `P16-14` | `pro.tasks_device_media` |
| `P15-15` | `P16-15` | `pro.tasks_device_media` |
| `P15-16` | `P16-16` | `pro.tasks_device_media` |
| `P15-17` | `P16-17` | `pro.tasks_device_media` |
| `P15-18` | `P16-18` | `pro.tasks_device_media` |
| `P15-19` | `P16-19` | `pro.tasks_device_media` |
| `P15-20` | `P16-20` | `pro.tasks_device_media` |
| `P15-21` | `P16-21` | `pro.tasks_device_media` |

## Rules

- Every `P16-00` through `P16-27` task maps exactly once.
- Every imported `P15` backlog task maps to at least one `P16` owner task.
- Every task declares owner surface, source backlog task list, profile, risk,
  evidence class, gate, and rollback hook.
- High and critical risk tasks must keep rollback hooks explicit before runtime
  authority changes.
- Critical tasks must remain planned until security, packaged aggregate, docs,
  type declarations, and release evidence all agree.

## Verification

- Host verifier: `tools/nodejs/phase16/verify-node-phase16-surface-manifest.js`
- Gradle gate: `:app:verifyNodePhase16SurfaceManifest`
- Schema: `docs/nodejs/schemas/phase16_surface_manifest.schema.json`
- Reports:
  - `build/reports/nodejs/phase16-surface-manifest-verification.json`
  - `build/reports/nodejs/phase16-surface-manifest-verification.md`
