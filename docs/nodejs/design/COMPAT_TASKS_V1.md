# Compat Tasks V1 Scope

Status: P12-15 design-locked scope only. This document does not expose
`require("tasks")`, `require("autojs6:compat").tasks`, a `tasks` global, or a new
canonical `tasks` capability.

## Decision

`compat.tasks` v1 is a narrow Rhino migration facade over the existing
`work_manager` bridge. It is for disposable, one-shot Node-owned scheduled work
only. P12-16 may implement this facade, but P12-15 only locks the contract.

The v1 surface is:

| Future API | WorkManager target | Result |
| --- | --- | --- |
| `compat.tasks.addDisposableTask({ path, date|delayMs, ... })` | `work_manager.scheduleOnce({ scriptPath|entry, delayMs, backend: "embedded" })` | JSON-safe task id/snapshot |
| `compat.tasks.removeTask(id)` | `work_manager.cancel(id)` | JSON-safe cancellation result |
| `compat.tasks.queryTasks(options?)` | `work_manager.list()` | Frozen JSON-safe task snapshots |

The facade must not return or accept raw Rhino `TimedTask` or `IntentTask`
objects. It must not expose Android `WorkRequest`, `WorkManager`,
`AlarmManager`, `JobScheduler`, `BroadcastReceiver`, `Intent`, `IntentFilter`,
or `PendingIntent` objects.

## V1 Input Rules

`addDisposableTask` accepts only JSON descriptors:

- `path` or `scriptPath`: scoped relative embedded Node script path.
- `entry`: packaged project entry, following the existing `work_manager` entry
  rules.
- `date`: future absolute time converted to bounded `delayMs`.
- `delayMs`: bounded future delay accepted directly.
- `taskTimeoutMs`: bounded scheduled-run timeout.
- `constraints`: the existing `work_manager` subset, currently `charging`,
  `network`, and `idle`.
- `packaged`: packaged metadata flag accepted only through the existing
  WorkManager packaged-entry policy.

If both `date` and `delayMs` are present, P12-16 must reject the descriptor. Past
dates, invalid dates, negative delays, absolute paths, NUL bytes, `..` escapes,
non-embedded backends, and unknown scheduler fields must reject before bridge
dispatch.

## V1 Output Rules

`compat.tasks` v1 returns JSON-safe values only:

- task ids are strings owned by the WorkManager provider;
- snapshots are frozen plain objects derived from `work_manager.WorkTask`;
- `runHistory` remains bounded by the WorkManager provider;
- cancellation accepts explicit string ids only.

No raw `TimedTask`, `IntentTask`, Java object, Android scheduler object, database
row, cursor, or receiver handle may cross into Node.

## Explicit Non-Goals

These Rhino APIs are outside P12-15/P12-16 v1:

- `tasks.addDailyTask()` and `tasks.addWeeklyTask()`: wall-clock task database
  semantics, next-run calculation, exact-alarm policy, and task UI state need a
  separate P12-17 decision.
- `tasks.addIntentTask()`, `queryIntentTasks()`, `getIntentTask()`, and
  `removeIntentTask()`: broadcast/startup/boot/package/custom trigger ownership
  needs the P12-18 broadcast task decision.
- `tasks.addTask(task)`, `removeTask(task)`, and `updateTask(task)`: raw
  `TimedTask`/`IntentTask` Java object transfer remains blocked by design.
- `tasks.daysToTimeFlag()` and `tasks.timeFlagToDays()`: pure utilities can be
  added later, but they are not part of disposable task v1.
- `require("tasks")` and global `tasks`: not exposed by v1. P12-16 targets only
  `require("autojs6:compat").tasks` after implementation.

## Security Boundary

`compat.tasks` v1 inherits the `work_manager` security model:

- `scheduled` execution is allowed only through `launchSurface=scheduled_runner`.
- direct script launches still cannot request scheduled mode.
- queued host fallback must reject persistent task registration and must not
  create one-way stored work.
- packaged tasks must remain app-local and revalidate scoped entries when fired.
- interactive UI, overlay, accessibility observation, and arbitrary long-running
  background behavior stay unavailable from scheduled tasks unless a separate
  provider gate promotes them.

P12-15 keeps `work_manager` as the only live scheduled-task provider. Future
`tasks` capabilities remain proposals until a provider, permission manifest
entry, packaged metadata policy, and verifier are added.

## P12-16 Handoff

P12-16 may implement only the disposable facade above:

- add `compat.tasks` under `autojs6:compat`;
- map `addDisposableTask` to `work_manager.scheduleOnce`;
- map `removeTask(id)` to `work_manager.cancel`;
- map `queryTasks(options?)` to JSON snapshots from `work_manager.list`;
- add types and fake/live bridge tests;
- keep daily/weekly, intent/broadcast, raw task object, and time-flag APIs out
  of the v1 runtime surface.
