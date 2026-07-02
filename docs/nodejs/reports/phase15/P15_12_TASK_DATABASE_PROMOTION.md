# P15-12 Task Database Promotion

Status: partial

Promotion state: `provider_poc_not_promoted`

P15-12 imports the P14-17 JSON-safe task database mutation contract into the
v1.3 promotion lane. The gate is executable and keeps `task_database`
unpromoted while live mutation, ownership/id mapping, schema migration,
wall-clock scheduler, packaged smoke, cross-package denial, disclosure,
Safe downgrade, security/type/docs, and release sync evidence are still absent.

## Imported Evidence

- P14-17 JSON-safe task database mutation contract.
- P13-21 `work_manager`, `compat.tasks`, and read-only
  `NodeTimedTaskSnapshots` evidence.
- Phase 15 provider promotion contracts.
- Phase 15 disclosure/rollback requirements for `task_database`.
- P15 surface manifest mapping for `provider_mutation_archive` evidence.

## Open Blockers

| Blocker | Classification |
| --- | --- |
| `task_database_live_mutation_provider_archive` | `future_promotion` |
| `task_database_ownership_id_mapping_archive` | `future_promotion` |
| `task_database_schema_migration_rollback_archive` | `future_promotion` |
| `task_database_wall_clock_scheduler_archive` | `future_promotion` |
| `packaged_task_database_mutation_smoke_archive` | `future_promotion` |
| `task_database_cross_package_denial_archive` | `future_promotion` |
| `task_database_disclosure_stop_action_archive` | `future_promotion` |
| `task_database_safe_downgrade_archive` | `future_promotion` |
| `phase15_task_device_media_doc_sync` | `future_promotion` |

## Decision

The gate reports `partial` with zero policy violations. `task_database`
remains unpromoted and no runtime authority changes in P15-12.

## Gate

```powershell
.\gradlew.bat --console=plain :app:verifyNodePhase15TaskDatabasePromotion
```
