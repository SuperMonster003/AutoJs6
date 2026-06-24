# Tasks Daily/Weekly User Database Decision

Status: P12-17 rejected provider promotion for daily/weekly Rhino user-task
database compatibility. The implemented Node surface stays under
`require("autojs6:compat").tasks`; it does not add `require("tasks")`, a global
`tasks`, or a direct AutoJs6 task database provider.

## Decision

P12-17 keeps Rhino daily/weekly `TimedTask` database parity out of the Node
provider surface.

P13-21 reopens this area only as a partial Pro-profile provider gate. It records
the existing Android/Rhino task database substrate, live `work_manager`
scheduled-work provider, and `compat.tasks` one-shot migration, but it does not
promote daily/weekly add/query/update/remove behavior yet.

The promoted `compat.tasks` methods are still the WorkManager-backed,
Node-owned subset:

- `addDisposableTask(options)` maps to `work_manager.scheduleOnce()`.
- `queryTasks(options?)` maps to `work_manager.list()` and filters one-shot
  Node-owned snapshots.
- `removeTask(id, options?)` maps to `work_manager.cancel()` for explicit
  WorkManager-owned string ids.
- `queryTimedTasks(options?)`, `getTimedTask(id, options?)`, and
  `removeTimedTask(id, options?)` are Rhino-name aliases over the same
  JSON-safe Node-owned snapshots and ids.

The rejected `compat.tasks` methods are explicit stable-error stubs:

- `addDailyTask(options?)` rejects with
  `ERR_AUTOJS6_RHINO_COMPAT_UNSUPPORTED`.
- `addWeeklyTask(options?)` rejects with
  `ERR_AUTOJS6_RHINO_COMPAT_UNSUPPORTED`.
- `updateTask(task)` rejects with
  `ERR_AUTOJS6_RHINO_COMPAT_BLOCKED_BY_DESIGN`.

These stubs do not dispatch bridge requests and do not create persistent task
database rows.

## Why Provider Promotion Is Rejected

Rhino daily/weekly tasks are user-visible AutoJs6 task database records. They
are not equivalent to WorkManager periodic work.

`work_manager.schedulePeriodic()` represents OS-managed periodic cadence. It
has minimum interval, quota, Doze, reboot, and scheduler behavior that can
differ from wall-clock daily or weekly task semantics.

`tasks.addDailyTask()` and `tasks.addWeeklyTask()` must not map mechanically to
`work_manager.schedulePeriodic()`.

## Future Provider Preconditions

A future provider can revisit daily/weekly compatibility only after all of the
following are designed, documented, implemented, and verified:

- Task ownership: main app vs packaged app ownership, project identity, user
  visibility, and cancellation authority.
- Database schema: descriptor version, Node backend, scoped entry, package
  metadata, timeout, constraints, arguments, enabled state, and task ids.
- Migration snapshot: Rhino `TimedTask` to JSON snapshot shape, compatibility
  versioning, downgrade behavior, and legacy row handling.
- Update/delete policy: JSON patch descriptors, enabled/disabled state,
  reschedule behavior, delete ownership, and raw task object rejection.
- Scheduler backend policy: AlarmManager, exact alarm permission, WorkManager
  fallback, JobScheduler fallback, timezone and clock changes, Doze, reboot,
  user-unlocked state, diagnostics, and quota reporting.
- Packaged APK policy: manifest permission injection, encrypted entry metadata,
  receiver declarations, and foreground/user-visible disclosure.
- Error policy: stable `ERR_AUTOJS6_RHINO_COMPAT_*` errors before bridge
  dispatch for unsupported providers, invalid descriptors, and raw object
  attempts.

Without those pieces, promoting daily/weekly provider access would silently
change user task behavior and make migration unsafe.

## Migration Guidance

Use `compat.tasks.addDisposableTask()` for one-shot future work that can be
represented by a bounded delay:

```js
"nodejs";

const { tasks } = require("autojs6:compat");

const taskId = await tasks.addDisposableTask({
  path: "jobs/cleanup.cjs",
  delayMs: 60_000,
  taskTimeoutMs: 30_000,
});
```

Use `compat.tasks.queryTimedTasks()` or `compat.tasks.getTimedTask(id)` only
for Node-owned one-shot snapshots created through `compat.tasks` or
`work_manager`.

Keep Rhino daily/weekly user-visible schedules on the Rhino engine until a
dedicated task database provider is promoted. Use
`work_manager.schedulePeriodic()` only when the intended behavior is
OS-managed periodic cadence and not wall-clock daily/weekly task parity.

Do not pass raw `TimedTask` or `IntentTask` objects into Node. Use explicit
JSON descriptors and ids only.
