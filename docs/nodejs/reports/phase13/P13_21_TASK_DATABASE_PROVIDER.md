# P13-21 Task Database Provider

Status: partial

P13-21 records the current daily/weekly/user task database provider state for
Phase 13. The project already has a live `work_manager` bridge for Node-owned
scheduled work and an explicit Rhino `compat.tasks` facade for one-shot
migration. It also has the Android/Rhino `TimedTask` database implementation
that powers legacy daily and weekly tasks.

The surface is not promoted to `pro_compat_opt_in` yet. Daily and weekly task
database parity is still a separate provider problem, not a mechanical
translation to `work_manager.schedulePeriodic()`. P13-21 keeps `require("tasks")`,
global `tasks`, raw `TimedTask` objects, and raw `IntentTask` objects
unavailable until a JSON-safe task database provider owns schema, migration,
packaged behavior, scheduler semantics, and deletion/update policy.

## Implemented Evidence

- Direct Node module: `require("work_manager")` exposes `scheduleOnce`,
  `schedulePeriodic`, `cancel`, and `list` over JSON descriptors and provider
  owned task ids.
- Live Android provider: `NodeWorkManagerBridgeScheduler` persists Node-owned
  task metadata, enqueues AndroidX WorkManager work, revalidates scoped entries
  when the scheduled runner fires, and records last-run diagnostics.
- Rhino compat facade: `compat.tasks.addDisposableTask()` maps to
  `work_manager.scheduleOnce()`, while `queryTasks()`,
  `queryTimedTasks()`, `getTimedTask(id)`, `removeTask(id)`, and
  `removeTimedTask(id)` operate only on Node-owned WorkManager snapshots and
  ids.
- Rejection stubs: `compat.tasks.addDailyTask()` and
  `addWeeklyTask()` reject with `ERR_AUTOJS6_RHINO_COMPAT_UNSUPPORTED`;
  `updateTask()` rejects with `ERR_AUTOJS6_RHINO_COMPAT_BLOCKED_BY_DESIGN`;
  IntentTask methods reject with `ERR_AUTOJS6_RHINO_COMPAT_UNSUPPORTED`.
- Android database substrate: the Rhino runtime still has
  `TimedTask.dailyTask()`, `TimedTask.weeklyTask()`,
  `TimedTaskManager`, and `TimedTaskDatabase`, but those raw objects and rows do
  not cross into Node.
- Raw boundary: raw `TimedTask`, `IntentTask`, `WorkRequest`,
  `PendingIntent`, `AlarmManager`, `JobScheduler`, `WorkManager`,
  `BroadcastReceiver`, `Intent`, and database cursors are not exposed to Node.

## Boundaries

- `work_manager.schedulePeriodic()` remains OS-managed periodic cadence. It is
  not Rhino daily/weekly wall-clock parity and must not be used as a silent
  implementation of `tasks.addDailyTask()` or `tasks.addWeeklyTask()`.
- `require("tasks")` and global `tasks` remain unavailable in the Safe Node
  Profile. The only current migration entry is explicit
  `require("autojs6:compat").tasks`.
- Numeric Rhino task database ids are not automatically equivalent to
  WorkManager string ids. P13-21 requires future id mapping and ownership rules
  before query/update/remove parity can be promoted.
- Packaged daily/weekly task behavior is not promoted. Packaged task rows,
  encrypted entry metadata, receiver/permission synthesis, and UI visibility
  need dedicated evidence.
- Raw task object mutation remains blocked. A future provider must use
  JSON-safe descriptors and patch/update payloads rather than V8 access to
  Android/Rhino task objects.

## Remaining Blockers

- Define a daily/weekly task descriptor schema with backend, scoped entry,
  package identity, user-visible label, arguments, enabled state, timeout,
  constraints, timezone, weekday flags, and descriptor version.
- Implement task database migration and ownership rules for main-app and
  packaged-app task records, including downgrade behavior and legacy Rhino row
  visibility.
- Add `add`, `query`, `get`, `update`, `enable/disable`, and `remove` provider
  operations over JSON snapshots and ids, with no raw task object mutation.
- Define wall-clock scheduler semantics across reboot, timezone/clock changes,
  Doze, exact-alarm permission, WorkManager/JobScheduler fallback, and Android
  quota diagnostics.
- Add packaged APK metadata, manifest/permission synthesis, install-run
  positive/negative smokes, user disclosure, and cleanup evidence.

The docs, compatibility notes, surface manifest, and Gradle gate are
evidence-only for this partial decision. They do not add a separate P13-21
blocker beyond the task schema, ownership/id mapping, JSON snapshot operations,
wall-clock scheduler semantics, packaged metadata, disclosure, and cleanup
evidence above.

## Gate

Host verifier:

```powershell
node tools/nodejs/phase13/verify-task-database-provider.js --repo-root D:\idea-projects\AutoJs6 --report-dir D:\idea-projects\AutoJs6\build\reports\nodejs
```

Gradle gate:

```powershell
.\gradlew.bat --console=plain :app:verifyNodePhase13TaskDatabaseProvider
```

Expected reports:

```text
build/reports/nodejs/phase13-task-database-provider.json
build/reports/nodejs/phase13-task-database-provider.md
```
