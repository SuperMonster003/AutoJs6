# Compat Tasks V1 Scope

Status: P12-15 design-locked the scope. P12-16 implements
`require("autojs6:compat").tasks` as the disposable one-shot facade over
`work_manager`. P12-17 adds stable rejection stubs and Rhino-name aliases for
the Node-owned subset, but does not promote a daily/weekly user task database
provider. This document still does not expose `require("tasks")`, a `tasks`
global, or a new canonical `tasks` capability.

## Decision

`compat.tasks` v1 is a narrow Rhino migration facade over the existing
`work_manager` bridge. It is for disposable, one-shot Node-owned scheduled work
only. P12-16 implements the facade without promoting raw task objects or a
`require("tasks")` module.

The v1 surface is:

| Future API | WorkManager target | Result |
| --- | --- | --- |
| `compat.tasks.addDisposableTask({ path, date|time|delayMs, ... })` | `work_manager.scheduleOnce({ scriptPath|entry, delayMs, backend: "embedded" })` | JSON-safe task id |
| `compat.tasks.removeTask(id)` | `work_manager.cancel(id)` | `true` after cancellation completes |
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

- `tasks.addDailyTask()` and `tasks.addWeeklyTask()`: P12-17 rejects provider
  promotion for wall-clock task database semantics. The methods exist only as
  `ERR_AUTOJS6_RHINO_COMPAT_UNSUPPORTED` stubs in `compat.tasks`.
- `tasks.addIntentTask()`, `queryIntentTasks()`, `getIntentTask()`, and
  `removeIntentTask()`: P12-18 rejects provider promotion for
  broadcast/startup/boot/package/custom trigger ownership. The methods exist
  only as `ERR_AUTOJS6_RHINO_COMPAT_UNSUPPORTED` stubs in `compat.tasks`.
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

P12-16 implements only the disposable facade above:

- `compat.tasks` is available under `autojs6:compat`;
- `addDisposableTask` maps to `work_manager.scheduleOnce`;
- `removeTask(id)` maps to `work_manager.cancel`;
- `queryTasks(options?)` maps to JSON snapshots from `work_manager.list`;
- types and fake bridge tests cover the facade;
- keep daily/weekly providers, intent/broadcast providers, raw task object
  transfer, and time-flag APIs out of the v1 promoted provider surface.

## P12-17 Daily/Weekly Decision

P12-17 keeps daily/weekly user task database compatibility rejected until a
dedicated provider exists. The runtime adds stable compatibility behavior
without database promotion:

- `compat.tasks.addDailyTask()` rejects with
  `ERR_AUTOJS6_RHINO_COMPAT_UNSUPPORTED`;
- `compat.tasks.addWeeklyTask()` rejects with
  `ERR_AUTOJS6_RHINO_COMPAT_UNSUPPORTED`;
- `compat.tasks.queryTimedTasks()` aliases `queryTasks()` for Node-owned
  one-shot JSON snapshots only;
- `compat.tasks.getTimedTask(id)` filters Node-owned one-shot JSON snapshots
  by explicit id and returns `null` when absent;
- `compat.tasks.removeTimedTask(id)` aliases `removeTask(id)` for explicit
  WorkManager-owned ids;
- `compat.tasks.updateTask()` rejects with
  `ERR_AUTOJS6_RHINO_COMPAT_BLOCKED_BY_DESIGN`.

The rejection stubs do not dispatch bridge requests. The aliases do not read or
mutate the Rhino `TimedTask` database and still never return raw `TimedTask` or
`IntentTask` objects. The decision details are tracked in
`TASKS_DAILY_WEEKLY_USER_DATABASE_DECISION.md`.

## P12-18 Intent/Broadcast Decision

P12-18 keeps `IntentTask` and broadcast-triggered task compatibility rejected
until a dedicated provider exists. The runtime adds stable compatibility
behavior without receiver or task database promotion:

- `compat.tasks.addIntentTask()` rejects with
  `ERR_AUTOJS6_RHINO_COMPAT_UNSUPPORTED`;
- `compat.tasks.queryIntentTasks()` rejects with
  `ERR_AUTOJS6_RHINO_COMPAT_UNSUPPORTED`;
- `compat.tasks.getIntentTask(id)` rejects with
  `ERR_AUTOJS6_RHINO_COMPAT_UNSUPPORTED`;
- `compat.tasks.removeIntentTask(id)` rejects with
  `ERR_AUTOJS6_RHINO_COMPAT_UNSUPPORTED`.

The stubs do not dispatch bridge requests, mutate manifests, register or
unregister receivers, query Rhino `IntentTask` records, or expose raw
`BroadcastReceiver`, `IntentFilter`, `PendingIntent`, `Intent`, or custom
broadcast authority. The decision details are tracked in
`TASKS_INTENT_BROADCAST_COMPATIBILITY_DECISION.md`.
