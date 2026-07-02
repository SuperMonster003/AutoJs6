# Phase 15 Task Database Promotion

Machine-readable source:

- `docs/nodejs/phase15/task-database-promotion-plan.json`

P15-12 imports the P14-17 JSON-safe task database mutation contract into the
v1.3 promotion lane. It keeps `task_database` at
`provider_poc_not_promoted` while live mutation, ownership/id mapping, schema
migration, wall-clock scheduler, packaged task database, cross-package denial,
disclosure, Safe downgrade, security, type, docs, and release evidence are
still absent.

The gate is a promotion evidence contract, not a runtime authority change.
`require("tasks")`, global `tasks`, future `tasks` capabilities, raw
`TimedTask` objects, schedulers, database cursors, mutable row handles,
`IntentTask`, `PendingIntent`, `Intent`, and Java objects remain unavailable.

## Ready Evidence

- `phase14_json_safe_task_database_contract`
- `phase13_work_manager_compat_tasks_import`
- `phase15_promotion_contracts`
- `phase15_disclosure_rollback_contract`
- `phase15_surface_manifest_mapping`

## Open Promotion Blockers

| Blocker | Classification | Required evidence |
| --- | --- | --- |
| `task_database_live_mutation_provider_archive` | `future_promotion` | Live add/update/enable/disable/remove/list/get provider archive. |
| `task_database_ownership_id_mapping_archive` | `future_promotion` | Owner and id mapping archive across Node, legacy, packaged, project, and package identities. |
| `task_database_schema_migration_rollback_archive` | `future_promotion` | P15-13 schema migration, downgrade, backup, corruption, unknown-version, and rollback fixtures. |
| `task_database_wall_clock_scheduler_archive` | `future_promotion` | Wall-clock scheduler archive for timezone, clock changes, reboot, Doze, exact alarm, fallback, and quotas. |
| `packaged_task_database_mutation_smoke_archive` | `future_promotion` | P15-14 packaged task database mutation smoke. |
| `task_database_cross_package_denial_archive` | `future_promotion` | Cross-package and main-app alias denial archive. |
| `task_database_disclosure_stop_action_archive` | `future_promotion` | Disclosure, foreground notification, stop action, task removal, relaunch, and crash recovery archive. |
| `task_database_safe_downgrade_archive` | `future_promotion` | Provider-disable Safe downgrade proof. |
| `phase15_task_device_media_doc_sync` | `future_promotion` | P15-22, P15-23, P15-25, and P15-26 sync before promotion. |

## Rollback

- Disable flag: `node.provider.task_database.enabled`
- Diagnostic code: `NODE_PROVIDER_TASK_DATABASE_DISABLED`
- Safe fallback: preserve rows and deny package-owned task mutations.
- Crash circuit breaker: `3 provider crashes within 24h per package/profile/capability`

## Verification

- Host verifier: `tools/nodejs/phase15/verify-node-phase15-task-database-promotion.js`
- Gradle gate: `:app:verifyNodePhase15TaskDatabasePromotion`
- Reports:
  - `build/reports/nodejs/phase15-task-database-promotion.json`
  - `build/reports/nodejs/phase15-task-database-promotion.md`
