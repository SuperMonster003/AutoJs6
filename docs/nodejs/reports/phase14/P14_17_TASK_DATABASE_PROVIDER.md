# P14-17 Task Database Provider

Status: partial

P14-17 deploys the JSON-safe task database mutation provider contract gate. It
imports the P13-21 `work_manager`, `compat.tasks`, and `NodeTimedTaskSnapshots`
evidence, reserves the future Pro-only `tasks` provider shape, and keeps
runtime authority unchanged until live mutation, ownership/id mapping, schema
migration, scheduler, packaged, and rollback evidence exists.

This task remains unchecked because the project still does not expose
`require("tasks")`, does not accept a `tasks` capability, and does not implement
live add/update/enable/disable/remove/list/get database mutation operations.

## Implemented Evidence

- Plan source: `docs/nodejs/phase14/task-database-provider-plan.json`.
- Provider registry metadata records P14-17 as a JSON-safe mutation contract
  gate and keeps mutation promotion blocked.
- The future operation contract is limited to `add`, `update`, `enable`,
  `disable`, `remove`, `list`, and `get` over JSON descriptors and patches.
- Owner/id mapping separates opaque Node task ids, legacy `TimedTask` ids, and
  packaged task ids.
- Stable denial guidance covers missing capability, permission denial, invalid
  descriptors, unsupported Rhino compat methods, and raw-object attempts.
- Raw `TimedTask`, scheduler, database cursor, mutable row handle, `IntentTask`,
  Android scheduler objects, `PendingIntent`, `Intent`, and Java objects remain
  outside JavaScript.

## Remaining Blockers

| Blocker | Classification | Required evidence |
| --- | --- | --- |
| `task_database_live_mutation_provider_archive` | `future_promotion` | Live provider evidence for add, update, enable, disable, remove, list, and get over JSON descriptors and patches without raw task objects. |
| `task_database_ownership_id_mapping_archive` | `future_promotion` | Owner and id mapping evidence proving Node ids, legacy `TimedTask` ids, packaged task ids, project identity, and package identity cannot alias across owners. |
| `task_database_schema_migration_rollback_archive` | `future_promotion` | Schema version, downgrade, corruption recovery, unknown-version fail-closed, and rollback behavior for task descriptors and legacy row visibility. |
| `task_database_wall_clock_scheduler_archive` | `future_promotion` | Daily/weekly wall-clock scheduler evidence covering timezone and clock changes, reboot, Doze, exact-alarm permission, WorkManager/JobScheduler fallback, and quota diagnostics. |
| `packaged_task_database_mutation_smoke_archive` | `future_promotion` | Packaged APK add/list/enable-disable/remove smoke proving packaged row ownership, disclosure, process death/relaunch behavior, task removal, and Safe downgrade policy. |

## Gate

Host verifier:

```powershell
node tools/nodejs/phase14/verify-node-phase14-task-database-provider.js --repo-root D:\idea-projects\AutoJs6-nodejs --report-dir D:\idea-projects\AutoJs6-nodejs\build\reports\nodejs
```

Gradle gate:

```powershell
.\gradlew.bat --console=plain :app:verifyNodePhase14TaskDatabaseProvider
```
