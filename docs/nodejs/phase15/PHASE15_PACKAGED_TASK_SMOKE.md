# Phase 15 Packaged Task Smoke

Machine-readable source:

- `docs/nodejs/phase15/packaged-task-smoke-plan.json`

P15-14 imports the P14-19 packaged task scheduling smoke contract into the
v1.3 promotion lane. It keeps `task_database` at
`provider_poc_not_promoted` while packaged task database rows, relaunch,
task-removal cleanup, denial matrix, disclosure, rollback, Safe downgrade,
security, type, docs, and release evidence are still absent.

The gate does not expose `require("tasks")`, global `tasks`, future `tasks`
capabilities, packaged task database mutation, raw `TimedTask`, raw
`IntentTask`, scheduler objects, Android `WorkManager`, cursors, or mutable row
handles.

## Ready Evidence

- `phase14_packaged_task_scheduling_contract`
- `phase15_task_database_dependency_gate`
- `phase15_task_schema_dependency_gate`
- `phase15_promotion_contracts`
- `phase15_disclosure_rollback_contract`
- `phase15_surface_manifest_mapping`

## Smoke Matrix

| Row | Status | Policy |
| --- | --- | --- |
| `packaged_work_manager_fake_schedule_list_cancel` | `imported` | Existing packaged `work_manager` fake-provider smoke covers schedule/list/cancel without future task rows. |
| `live_work_manager_packaged_project_entry` | `imported` | Existing live provider schedules and cancels a packaged project entry through `scheduled_runner`. |
| `future_tasks_database_add_list_enable_disable_remove` | `future_promotion` | Future packaged `tasks` database add/list/enable-disable/remove still requires archived device evidence. |
| `future_process_death_relaunch_task_removal` | `future_promotion` | Process death, relaunch, task removal, and crash recovery remain future archives. |
| `future_packaged_task_disclosure_stop_action_safe_downgrade` | `future_promotion` | Disclosure, foreground notification ownership, stop action, rollback, and Safe downgrade remain future archives. |
| `future_packaged_task_denial_matrix` | `future_promotion` | Cross-package denial, missing capability denial, metadata mismatch, and raw object denial remain future archives. |

## Open Promotion Blockers

| Blocker | Classification | Required evidence |
| --- | --- | --- |
| `packaged_task_database_positive_smoke_archive` | `future_promotion` | Packaged APK add/list/enable-disable/remove smoke over future tasks database rows. |
| `packaged_task_process_death_relaunch_archive` | `future_promotion` | Packaged task process-death, relaunch, crash recovery, and last-run diagnostics. |
| `packaged_task_removal_cleanup_archive` | `future_promotion` | Task removal cleanup proving owned scheduled work is cancelled or disabled. |
| `packaged_task_disclosure_safe_downgrade_archive` | `future_promotion` | Packaged disclosure, stop action, rollback, and Safe downgrade archive. |
| `packaged_task_cross_package_denial_archive` | `future_promotion` | Cross-package package-owned row mutation denial archive. |
| `packaged_task_missing_capability_denial_archive` | `future_promotion` | Missing `tasks` capability and `require("tasks")` denial archive. |
| `packaged_task_safe_downgrade_archive` | `future_promotion` | Provider-disable Safe downgrade proof for packaged task rows. |
| `phase15_task_device_media_doc_sync` | `future_promotion` | P15-22, P15-23, P15-25, and P15-26 sync before promotion. |

## Verification

- Host verifier: `tools/nodejs/phase15/verify-node-phase15-packaged-task-smoke.js`
- Gradle gate: `:app:installAndRunNodeV13PackagedTaskSmoke`
- Reports:
  - `build/reports/nodejs/phase15-packaged-task-smoke.json`
  - `build/reports/nodejs/phase15-packaged-task-smoke.md`
