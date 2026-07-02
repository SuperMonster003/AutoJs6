# Phase 14 Packaged Task Scheduling Smoke

P14-19 records the packaged task scheduling smoke baseline for the
`pro.tasks_device_media` surface. It imports the existing packaged
`work_manager` fake-provider smoke and live packaged project-entry
scheduled-runner evidence, but it does not promote packaged task database rows
or the future `tasks` provider.

## Imported Smoke

| Smoke | Current state | Boundary |
| --- | --- | --- |
| `packaged_work_manager_fake_schedule_list_cancel` | Imported from the packaged v1.1 bridge smoke. | Proves packaged module resolution, `work_manager` permission metadata, fake-provider `scheduleOnce` / `list` / `cancel`, shadowing denial, and pending-call cleanup. |
| `live_work_manager_packaged_project_entry` | Imported from `NodeWorkManagerBridgeInstrumentationTest`. | Proves a live Android provider can execute a packaged project entry through `scheduled_runner`, persist `lastRun` / `runHistory`, and cancel owned WorkManager work. |
| `future_tasks_database_add_list_enable_disable_remove` | Future promotion. | Requires packaged task database rows with `add`, `list`, `enable`, `disable`, and `remove` over JSON-safe descriptors. |
| `future_process_death_relaunch_task_removal` | Future promotion. | Requires process death, relaunch, task removal, and crash recovery evidence. |
| `future_disclosure_safe_downgrade` | Future promotion. | Requires install/run disclosure, stop action, Safe downgrade, and package-scoped disable/remove evidence. |

## Ownership Contract

- Packaged task rows require a package identity.
- Packaged rows cannot alias main-app rows or legacy Rhino `TimedTask` rows.
- Safe downgrade disables or removes only rows owned by the packaged app.
- Task removal cancels owned scheduled work and disables or removes only owned
  packaged rows.
- Cross-package remove/update attempts are denied.
- Legacy Rhino rows remain preserved.

## Open Archives

| Archive | Classification | Required evidence |
| --- | --- | --- |
| `packaged_task_database_positive_smoke_archive` | `future_promotion` | Packaged APK add/list/enable-disable/remove smoke over future tasks database rows with package identity ownership. |
| `packaged_task_process_death_relaunch_archive` | `future_promotion` | Packaged task process-death, relaunch, crash recovery, and last-run diagnostic archive. |
| `packaged_task_removal_cleanup_archive` | `future_promotion` | Task removal cleanup evidence proving owned scheduled work is cancelled and package-scoped rows are disabled or removed. |
| `packaged_task_disclosure_safe_downgrade_archive` | `future_promotion` | Packaged disclosure, stop action, Safe downgrade, and rollback evidence for task database promotion. |

## Verification

```powershell
.\gradlew.bat --console=plain :app:verifyNodePhase14PackagedTaskSchedulingSmoke
```

Expected reports:

```text
build/reports/nodejs/phase14-packaged-task-scheduling-smoke.json
build/reports/nodejs/phase14-packaged-task-scheduling-smoke.md
```
