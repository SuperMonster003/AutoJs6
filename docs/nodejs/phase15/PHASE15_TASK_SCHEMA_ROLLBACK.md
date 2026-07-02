# Phase 15 Task Schema Rollback

Machine-readable source:

- `docs/nodejs/phase15/task-schema-rollback-plan.json`

P15-13 imports the P14-18 task schema migration and rollback contract into the
v1.3 promotion lane. It keeps `task_database` at
`provider_poc_not_promoted` while runtime migration tests, corruption recovery,
legacy visibility downgrade, packaged rollback, backup/restore, Safe downgrade,
cross-package rollback denial, security, type, docs, and release evidence are
still absent.

The gate does not expose `require("tasks")`, global `tasks`, future `tasks`
capabilities, live task database mutation, raw task objects, database cursors,
or scheduler handles.

## Ready Evidence

- `phase14_schema_rollback_contract`
- `phase15_task_database_dependency_gate`
- `phase15_promotion_contracts`
- `phase15_disclosure_rollback_contract`
- `phase15_surface_manifest_mapping`

## Fixture Matrix

| Fixture | Policy |
| --- | --- |
| `current_descriptor_v1` | Current descriptor round trip remains contract-only. |
| `older_descriptor_v0` | Upgrade requires an explicit migration rule. |
| `legacy_timed_task_snapshot_v1` | Legacy rows remain immutable snapshots. |
| `corrupt_descriptor` | Corrupt descriptors quarantine and fail closed. |
| `unknown_future_schema` | Future descriptors are preserved without mutation. |
| `packaged_owner_mismatch` | Packaged owner mismatch denies cross-package aliasing. |

## Open Promotion Blockers

| Blocker | Classification | Required evidence |
| --- | --- | --- |
| `task_schema_live_migration_tests_archive` | `future_promotion` | Runtime migration tests for current, older, corrupt, and unknown descriptors. |
| `task_schema_corruption_recovery_archive` | `future_promotion` | Corruption recovery archive. |
| `task_schema_downgrade_legacy_visibility_archive` | `future_promotion` | Safe downgrade and legacy visibility archive. |
| `task_schema_packaged_rollback_archive` | `future_promotion` | Packaged rollback archive scoped to owning package identity. |
| `task_schema_backup_restore_archive` | `future_promotion` | Backup and restore fixture evidence. |
| `task_schema_safe_downgrade_archive` | `future_promotion` | Provider-disable Safe downgrade proof. |
| `task_schema_cross_package_rollback_denial_archive` | `future_promotion` | Cross-package rollback denial archive. |
| `phase15_task_device_media_doc_sync` | `future_promotion` | P15-22, P15-23, P15-25, and P15-26 sync before promotion. |

## Verification

- Host verifier: `tools/nodejs/phase15/verify-node-phase15-task-schema-rollback.js`
- Gradle gate: `:app:verifyNodePhase15TaskSchemaRollback`
- Reports:
  - `build/reports/nodejs/phase15-task-schema-rollback.json`
  - `build/reports/nodejs/phase15-task-schema-rollback.md`
