# P14-19 Packaged Task Scheduling Smoke

Status: partial

P14-19 deploys the packaged task scheduling smoke evidence gate. It imports the
packaged v1.1 bridge fake-provider `work_manager` smoke and the live
`work_manager` packaged project-entry scheduled-runner instrumentation as a
baseline for future packaged task database promotion.

This task remains unchecked because there is still no packaged `tasks`
database provider, no packaged add/list/enable-disable/remove over package
identity rows, no archived process-death/relaunch/task-removal evidence, and
no packaged task disclosure or Safe downgrade archive.

## Implemented Evidence

- Plan source:
  `docs/nodejs/phase14/packaged-task-scheduling-smoke-plan.json`.
- Packaged fake-provider smoke:
  packaged `work_manager` module resolution, `work_manager` permission
  metadata, `scheduleOnce`, `list`, `cancel`, bridge-module shadowing denial,
  and pending-call cleanup are imported from the v1.1 packaged bridge smoke.
- Live packaged project-entry anchor:
  `NodeWorkManagerBridgeInstrumentationTest` schedules a packaged project entry
  through the live Android provider, executes it through `scheduled_runner`,
  persists `lastRun` / `runHistory`, and cancels the owned work.
- Ownership contract:
  future packaged task rows require package identity, cannot alias main-app or
  legacy rows, and must be disabled or removed only according to owned packaged
  row policy.
- Runtime boundary:
  `require("tasks")`, the future `tasks` capability, packaged task database
  rows, raw `TimedTask`, scheduler objects, cursors, and mutable row handles
  remain unpromoted.

## Remaining Blockers

| Blocker | Classification | Required evidence |
| --- | --- | --- |
| `packaged_task_database_positive_smoke_archive` | `future_promotion` | Packaged APK add/list/enable-disable/remove smoke over future tasks database rows with package identity ownership. |
| `packaged_task_process_death_relaunch_archive` | `future_promotion` | Packaged task process-death, relaunch, crash recovery, and last-run diagnostic archive. |
| `packaged_task_removal_cleanup_archive` | `future_promotion` | Task removal cleanup evidence proving owned scheduled work is cancelled and package-scoped rows are disabled or removed. |
| `packaged_task_disclosure_safe_downgrade_archive` | `future_promotion` | Packaged disclosure, stop action, Safe downgrade, and rollback evidence for task database promotion. |

## Gate

Host verifier:

```powershell
node tools/nodejs/phase14/verify-node-phase14-packaged-task-scheduling-smoke.js --repo-root D:\idea-projects\AutoJs6-nodejs --report-dir D:\idea-projects\AutoJs6-nodejs\build\reports\nodejs
```

Gradle gate:

```powershell
.\gradlew.bat --console=plain :app:verifyNodePhase14PackagedTaskSchedulingSmoke
```
