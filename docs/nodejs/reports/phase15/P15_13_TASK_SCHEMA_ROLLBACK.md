# P15-13 Task Schema Rollback

Status: partial

Promotion state: `provider_poc_not_promoted`

P15-13 imports the P14-18 schema migration and rollback contract into the v1.3
promotion lane. The gate is executable and keeps `task_database` unpromoted
while live migration, corruption recovery, downgrade legacy visibility,
packaged rollback, backup/restore, Safe downgrade, cross-package rollback
denial, security/type/docs, and release sync evidence are still absent.

## Imported Evidence

- P14-18 schema fixture matrix and rollback contract.
- P15-12 JSON-safe task database dependency gate.
- Phase 15 provider promotion contracts.
- Phase 15 disclosure/rollback requirements for `task_database`.
- P15 surface manifest mapping for `migration_rollback_fixture` evidence.

## Open Blockers

| Blocker | Classification |
| --- | --- |
| `task_schema_live_migration_tests_archive` | `future_promotion` |
| `task_schema_corruption_recovery_archive` | `future_promotion` |
| `task_schema_downgrade_legacy_visibility_archive` | `future_promotion` |
| `task_schema_packaged_rollback_archive` | `future_promotion` |
| `task_schema_backup_restore_archive` | `future_promotion` |
| `task_schema_safe_downgrade_archive` | `future_promotion` |
| `task_schema_cross_package_rollback_denial_archive` | `future_promotion` |
| `phase15_task_device_media_doc_sync` | `future_promotion` |

## Decision

The gate reports `partial` with zero policy violations. `task_database`
schema migration and rollback remain unpromoted and no runtime authority
changes in P15-13.

## Gate

```powershell
.\gradlew.bat --console=plain :app:verifyNodePhase15TaskSchemaRollback
```
