# Phase 14 Task Schema Migration Rollback

P14-18 records the task schema migration and rollback evidence contract for the
`pro.tasks_device_media` surface. It builds on P14-17 and keeps runtime
authority unchanged: no live `tasks` provider, no `tasks` capability, and no
database mutation are promoted by this gate.

## Schema Contract

| Area | P14-18 rule |
| --- | --- |
| Current descriptor | `autojs6-node-task-database-descriptor-v1` is the current future descriptor schema. |
| Current fixture | `current_descriptor_v1` must round-trip only as a contract fixture until live mutation lands. |
| Older fixture | `older_descriptor_v0` must require an explicit migration rule before mutation. |
| Legacy fixture | `legacy_timed_task_snapshot_v1` remains read-only and visible to Rhino through legacy rows. |
| Corrupt fixture | `corrupt_descriptor` must be quarantined, fail closed, and never reschedule work. |
| Unknown fixture | `unknown_future_schema` must be preserved without mutation until a compatible migrator exists. |
| Packaged fixture | `packaged_owner_mismatch` must deny cross-package aliasing and remove or disable only owned packaged rows. |

## Rollback Contract

- Safe-profile downgrade ignores Node task descriptors and keeps legacy Rhino
  rows visible.
- Pro-profile disable disables future Node task rows without deleting legacy
  `TimedTask` rows.
- Packaged downgrade removes or disables only rows owned by the packaged app
  identity.
- Corruption recovery quarantines corrupt descriptors, emits stable
  diagnostics, and avoids scheduler reschedule.
- Unknown future schema recovery preserves the descriptor without mutation.

## Open Archives

| Archive | Classification | Required evidence |
| --- | --- | --- |
| `task_schema_live_migration_tests_archive` | `future_promotion` | Runtime migration tests for current, older, corrupt, and unknown descriptor versions with stable pass/fail diagnostics. |
| `task_schema_corruption_recovery_archive` | `future_promotion` | Corrupt descriptor recovery evidence proving quarantine, no scheduler reschedule, and no legacy row deletion. |
| `task_schema_downgrade_legacy_visibility_archive` | `future_promotion` | Safe downgrade and Pro disable evidence proving legacy Rhino `TimedTask` rows remain visible and Node descriptors fail closed. |
| `task_schema_packaged_rollback_archive` | `future_promotion` | Packaged rollback evidence proving packaged task rows are removed or disabled only for the owning package identity. |

## Verification

```powershell
.\gradlew.bat --console=plain :app:verifyNodePhase14TaskSchemaMigrationRollback
```

Expected reports:

```text
build/reports/nodejs/phase14-task-schema-migration-rollback.json
build/reports/nodejs/phase14-task-schema-migration-rollback.md
```
