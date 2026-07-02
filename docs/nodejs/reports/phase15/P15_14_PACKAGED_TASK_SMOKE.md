# P15-14 Packaged Task Smoke

Status: partial

P15-14 deploys the Phase 15 packaged task scheduling smoke evidence gate. It
imports the P14-19 packaged `work_manager` schedule/list/cancel smoke and the
live packaged project-entry scheduled-runner anchor, then binds them to the
P15-12 task database and P15-13 task schema dependency gates.

This task remains unchecked because there is still no packaged `tasks`
database provider, no packaged add/list/enable-disable/remove archive over
package identity rows, no process-death/relaunch/task-removal cleanup archive,
no cross-package or missing-capability denial archive, and no packaged task
disclosure or Safe downgrade archive.

## Implemented Evidence

- Plan source:
  `docs/nodejs/phase15/packaged-task-smoke-plan.json`.
- Host verifier:
  `tools/nodejs/phase15/verify-node-phase15-packaged-task-smoke.js`.
- Packaged fake-provider import:
  packaged `work_manager` module resolution, `work_manager` permission
  metadata, `scheduleOnce`, `list`, `cancel`, bridge-module shadowing denial,
  and pending-call cleanup are imported from the v1.1 packaged bridge smoke.
- Live packaged project-entry anchor:
  `NodeWorkManagerBridgeInstrumentationTest` schedules a packaged project entry
  through the live Android provider, executes it through `scheduled_runner`,
  persists `lastRun` / `runHistory`, and cancels the owned work.
- Dependency gates:
  P15-12 and P15-13 remain partial and keep `task_database` at
  `provider_poc_not_promoted`.
- Runtime boundary:
  `require("tasks")`, the future `tasks` capability, packaged task database
  rows, raw `TimedTask`, raw `IntentTask`, scheduler objects, cursors, and
  mutable row handles remain unpromoted.

## Remaining Blockers

| Blocker | Classification | Required evidence |
| --- | --- | --- |
| `packaged_task_database_positive_smoke_archive` | `future_promotion` | Packaged APK add/list/enable-disable/remove smoke over future tasks database rows with package identity ownership. |
| `packaged_task_process_death_relaunch_archive` | `future_promotion` | Packaged task process-death, relaunch, crash recovery, and last-run diagnostic archive. |
| `packaged_task_removal_cleanup_archive` | `future_promotion` | Task removal cleanup evidence proving owned scheduled work is cancelled and package-scoped rows are disabled or removed. |
| `packaged_task_disclosure_safe_downgrade_archive` | `future_promotion` | Packaged disclosure, stop action, Safe downgrade, and rollback evidence for task database promotion. |
| `packaged_task_cross_package_denial_archive` | `future_promotion` | Packaged cross-package add/update/remove denial evidence proving package-owned rows cannot alias main-app or legacy Rhino rows. |
| `packaged_task_missing_capability_denial_archive` | `future_promotion` | Missing future tasks capability and `require("tasks")` denial archive with stable diagnostics. |
| `packaged_task_safe_downgrade_archive` | `future_promotion` | Provider-disable Safe downgrade proof that packaged task rows are preserved, disabled, or removed according to owner policy. |
| `phase15_task_device_media_doc_sync` | `future_promotion` | P15-22, P15-23, P15-25, and P15-26 sync before packaged task scheduling can be marked promoted. |

## Gate

Host verifier:

```powershell
node tools/nodejs/phase15/verify-node-phase15-packaged-task-smoke.js --repo-root D:\idea-projects\AutoJs6-nodejs --report-dir D:\idea-projects\AutoJs6-nodejs\build\reports\nodejs
```

Gradle gate:

```powershell
.\gradlew.bat --console=plain :app:installAndRunNodeV13PackagedTaskSmoke
```
