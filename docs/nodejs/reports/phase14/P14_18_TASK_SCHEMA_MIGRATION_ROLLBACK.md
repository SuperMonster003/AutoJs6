# P14-18 Task Schema Migration Rollback

Status: partial

P14-18 deploys the task schema migration and rollback evidence contract gate.
It adds a fixture matrix for current, older, legacy snapshot, corrupt, unknown,
and packaged owner-mismatch task descriptor inputs while keeping live task
database mutation and live migration tests unpromoted.

This task remains unchecked because there is still no runtime `tasks` provider,
no task database schema migrator, no live current/older/corrupt/unknown
migration instrumentation, and no packaged rollback archive.

## Implemented Evidence

- Plan source:
  `docs/nodejs/phase14/task-schema-migration-rollback-plan.json`.
- P14-17 import:
  `phase14-task-database-provider` remains partial and records schema
  migration/rollback as not ready for live promotion.
- Fixture matrix:
  `current_descriptor_v1`, `older_descriptor_v0`,
  `legacy_timed_task_snapshot_v1`, `corrupt_descriptor`,
  `unknown_future_schema`, and `packaged_owner_mismatch`.
- Rollback contract:
  Safe-profile downgrade ignores Node descriptors, Pro disable disables future
  Node rows, packaged downgrade affects only rows owned by the packaged app
  identity, corruption recovery quarantines descriptors, and unknown future
  schema descriptors are preserved without mutation.
- Raw boundary:
  raw `TimedTask`, database cursors, mutable row handles, and scheduler objects
  still do not cross into Node.

## Remaining Blockers

| Blocker | Classification | Required evidence |
| --- | --- | --- |
| `task_schema_live_migration_tests_archive` | `future_promotion` | Runtime migration tests for current, older, corrupt, and unknown descriptor versions with stable pass/fail diagnostics. |
| `task_schema_corruption_recovery_archive` | `future_promotion` | Corrupt descriptor recovery evidence proving quarantine, no scheduler reschedule, and no legacy row deletion. |
| `task_schema_downgrade_legacy_visibility_archive` | `future_promotion` | Safe downgrade and Pro disable evidence proving legacy Rhino `TimedTask` rows remain visible and Node descriptors fail closed. |
| `task_schema_packaged_rollback_archive` | `future_promotion` | Packaged rollback evidence proving packaged task rows are removed or disabled only for the owning package identity. |

## Gate

Host verifier:

```powershell
node tools/nodejs/phase14/verify-node-phase14-task-schema-migration-rollback.js --repo-root D:\idea-projects\AutoJs6-nodejs --report-dir D:\idea-projects\AutoJs6-nodejs\build\reports\nodejs
```

Gradle gate:

```powershell
.\gradlew.bat --console=plain :app:verifyNodePhase14TaskSchemaMigrationRollback
```
