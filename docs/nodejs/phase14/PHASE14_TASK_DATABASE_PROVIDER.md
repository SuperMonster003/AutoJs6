# Phase 14 Task Database Provider

P14-17 records the v1.2 JSON-safe task database mutation contract for the
`pro.tasks_device_media` surface. It imports the P13-21 partial task database
evidence and keeps runtime authority unchanged: `require("tasks")`, global
`tasks`, direct task database mutation, and future `tasks` capabilities remain
unavailable until the live provider, schema migration, scheduler, packaged,
and rollback archives land.

## Contract

| Area | P14-17 rule |
| --- | --- |
| Provider name | `tasks` is reserved for a future Pro-only provider. |
| Current module | `work_manager` remains the only live scheduled-task module. |
| Migration facade | `require("autojs6:compat").tasks` remains the one-shot migration facade. |
| Operations | Future provider operations are `add`, `update`, `enable`, `disable`, `remove`, `list`, and `get`. |
| Descriptor boundary | Requests and responses use versioned JSON descriptors, JSON patches, opaque ids, and immutable snapshots. |
| Ownership | Node task ids, legacy `TimedTask` row ids, and packaged task ids are separate namespaces and cannot alias across owners. |
| Stable denials | Missing capability, invalid descriptor, unsupported compat method, and raw-object attempts must fail with stable `ERR_AUTOJS6_*` errors. |
| Raw object boundary | Raw `TimedTask`, scheduler, database cursor, mutable row handle, `IntentTask`, `PendingIntent`, `Intent`, and Java objects stay outside Node. |

## Imported Evidence

- P13-21 records live `work_manager` scheduling, `compat.tasks` one-shot
  migration, and JSON-safe legacy `TimedTask` query/get snapshots.
- `NodeTimedTaskSnapshots` records read-only legacy task rows with the
  `autojs6-node-timed-task-snapshot-v1` schema and raw handle denial policy.
- `compat.tasks.addDailyTask()`, `addWeeklyTask()`, `updateTask()`, and
  IntentTask helpers reject before bridge dispatch.
- `NodeBridgePermissionManifest` still does not accept a `tasks` capability.

## Open Archives

| Archive | Classification | Required evidence |
| --- | --- | --- |
| `task_database_live_mutation_provider_archive` | `future_promotion` | Live provider evidence for add, update, enable, disable, remove, list, and get over JSON descriptors and patches without raw task objects. |
| `task_database_ownership_id_mapping_archive` | `future_promotion` | Owner and id mapping evidence proving Node ids, legacy `TimedTask` ids, packaged task ids, project identity, and package identity cannot alias across owners. |
| `task_database_schema_migration_rollback_archive` | `future_promotion` | Schema version, downgrade, corruption recovery, unknown-version fail-closed, and rollback behavior for task descriptors and legacy row visibility. |
| `task_database_wall_clock_scheduler_archive` | `future_promotion` | Daily/weekly wall-clock scheduler evidence covering timezone and clock changes, reboot, Doze, exact-alarm permission, WorkManager/JobScheduler fallback, and quota diagnostics. |
| `packaged_task_database_mutation_smoke_archive` | `future_promotion` | Packaged APK add/list/enable-disable/remove smoke proving packaged row ownership, disclosure, process death/relaunch behavior, task removal, and Safe downgrade policy. |

## Verification

```powershell
.\gradlew.bat --console=plain :app:verifyNodePhase14TaskDatabaseProvider
```

Expected reports:

```text
build/reports/nodejs/phase14-task-database-provider.json
build/reports/nodejs/phase14-task-database-provider.md
```
