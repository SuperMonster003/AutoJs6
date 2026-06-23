# Rhino Tasks To WorkManager Migration

## Decision

S11-22 is a design gate only. It does not add `require("tasks")`,
`require("autojs6:compat").tasks`, or a `tasks` global to Embedded Node.

The current Node surface is `require("work_manager")`. It is a controlled
WorkManager-backed MVP for scoped embedded Node one-shot and bounded periodic work.
It is not Rhino's full user task database, not `TimedTaskManager`, not
`IntentTask`, and not a raw Android scheduler API.

The migration rule is conservative:

- use `work_manager` for Node-owned scheduled entries that can be represented as
  JSON descriptors;
- defer wall-clock daily/weekly tasks to a future user-visible task database
  provider;
- defer `IntentTask`/broadcast/startup/boot/package triggers to the broadcast task
  design gate;
- block raw `TimedTask`/`IntentTask` Java object transfer to V8.

This avoids the main failure mode for S11-22: treating a persistent user task or
long-running background behavior as a one-shot bridge call.

## Current Rhino Surface

Rhino `tasks` exposes synchronous helpers around AutoJs6 task objects:

- `tasks.addDailyTask(options)`
- `tasks.addWeeklyTask(options)`
- `tasks.addDisposableTask(options)`
- `tasks.addIntentTask(options)`
- `tasks.addTask(task)`
- `tasks.getTimedTask(id)`
- `tasks.getIntentTask(id)`
- `tasks.removeTask(task)`
- `tasks.removeTimedTask(id)`
- `tasks.removeIntentTask(id)`
- `tasks.updateTask(task)`
- `tasks.queryTimedTasks(options?)`
- `tasks.queryIntentTasks(options?)`
- `tasks.timeFlagToDays(flag)`
- `tasks.daysToTimeFlag(days)`

Timed APIs create or query `TimedTask` records in the AutoJs6 task database.
Intent APIs create or query `IntentTask` records and may depend on
`DynamicBroadcastReceivers`, startup actions, and broadcast receiver policy.
Several methods accept or return raw `TimedTask` / `IntentTask` Java objects and
support callback/async behavior through Rhino threads.

## Current Node Surface

`require("work_manager")` exposes:

- `scheduleOnce(descriptor): Promise<string>`
- `schedulePeriodic(descriptor): Promise<string>`
- `cancel(id): Promise<void>`
- `list(): Promise<readonly WorkTask[]>`

The descriptor is JSON-only and restricted:

- `scriptPath` or `entry`
- `backend: "embedded"`
- `delayMs` for one-shot tasks
- `intervalMs` for periodic tasks
- `taskTimeoutMs` for the scheduled embedded Node run
- `constraints.charging`, `constraints.network`, and `constraints.idle`
- `packaged` metadata for packaged project entries

The Android provider persists app-private WorkManager task records, enqueues
AndroidX WorkManager requests, revalidates scoped entries when workers fire, and
launches embedded Node through `executionMode=scheduled` and
`launchSurface=scheduled_runner`.

## Migration Table

| Rhino API | S11-22 status | Node target | Notes |
| --- | --- | --- | --- |
| `tasks.__module` | `partial` | `require("work_manager")` | Current Node scripts use the explicit WorkManager module, not `tasks`. |
| `tasks.addDisposableTask({ path, date/time })` | `partial` | `workManager.scheduleOnce({ scriptPath, delayMs })` | Only future absolute times can be translated to `delayMs`; return a JSON task id/record, not `TimedTask`. |
| `tasks.addDailyTask({ path, time })` | `unsupported` | Future task database provider | Do not map to `schedulePeriodic()`: daily wall-clock behavior, next-run calculation, exact-alarm policy, and user task UI differ. |
| `tasks.addWeeklyTask({ path, time, daysOfWeek })` | `unsupported` | Future task database provider | Weekday/time semantics require TimedTask-style storage and UI ownership. |
| `tasks.queryTimedTasks()` | `partial` | `workManager.list()` | Lists Node-owned WorkManager records only; full Rhino TimedTask database parity is deferred. |
| `tasks.getTimedTask(id)` | `partial` | `workManager.list()` plus filter | A future facade may return frozen JSON snapshots for Node-owned tasks only. |
| `tasks.removeTimedTask(id)` | `partial` | `workManager.cancel(id)` | Works only for Node-owned task ids; numeric Rhino database ids are not automatically equivalent. |
| `tasks.addIntentTask(...)` | `unsupported` | Future broadcast task provider | Startup, boot, package, and custom broadcast triggers follow `BROADCAST_TASKS.md`. |
| `tasks.queryIntentTasks(...)` | `unsupported` | Future broadcast/user task database provider | No raw `IntentTask` snapshots or receiver registration in S11-22. |
| `tasks.getIntentTask(id)` | `unsupported` | Future broadcast/user task database provider | Must return JSON snapshots only when designed. |
| `tasks.removeIntentTask(id)` | `unsupported` | Future broadcast/user task database provider | Must own receiver cleanup and UI visibility. |
| `tasks.addTask(task)` | `blocked_by_design` | Explicit JSON descriptors | Raw `TimedTask` / `IntentTask` Java objects must not cross into Node. |
| `tasks.removeTask(task)` | `blocked_by_design` | Explicit ids | Raw task object identity is a Rhino model, not a V8 bridge contract. |
| `tasks.updateTask(task)` | `blocked_by_design` | Future JSON patch/update API | Mutating raw Java task objects from Node is not allowed. |
| `tasks.timeFlagToDays(flag)` | `unsupported` | Future pure utility | Can be added later without scheduler provider access. |
| `tasks.daysToTimeFlag(days)` | `unsupported` | Future pure utility | Can be added later without scheduler provider access. |

## Why Daily/Weekly Cannot Use Periodic Work Directly

`work_manager.schedulePeriodic()` represents OS-managed periodic cadence with a
minimum interval and quota behavior. Rhino daily/weekly tasks represent user-visible
wall-clock schedules with local time, weekday flags, next-run calculation, task UI
state, and potentially exact-alarm or fallback scheduler behavior.

Mapping `addDailyTask()` or `addWeeklyTask()` to `schedulePeriodic()` would silently
change behavior after reboot, clock changes, Doze, WorkManager quota throttling, and
task enable/disable operations. A future compatibility facade must route these APIs
through an AutoJs6-owned task database provider, not through a single bridge call.

## Future Provider Split

S11-22 keeps one current capability:

- `work_manager`

No new canonical capability is added. A later implementation should decide whether
full Rhino task compatibility needs additional capabilities such as:

- `tasks`
- `tasks.intent`
- `tasks.boot`
- `tasks.package_events`

Those names are proposals only. They must not be accepted by
`NodeBridgePermissionManifest.definedCapabilities` until a provider and security
gate exist.

Future provider responsibilities:

- persist user-visible task records in the AutoJs6 task database or packaged app
  private task database;
- store Node backend, entry path/project entry, working directory, timeout, package
  metadata, descriptor version, and JSON arguments explicitly;
- keep task records visible and cancellable in UI;
- reconcile WorkManager ids, AutoJs6 task ids, and packaged app ids;
- record last-run diagnostics, stdout/stderr snippets, exit/error metadata, timeout
  state, Android quota policy, and trigger source;
- never return raw `TimedTask`, `IntentTask`, `WorkRequest`, `PendingIntent`,
  `IntentFilter`, `BroadcastReceiver`, `AlarmManager`, `JobScheduler`, or
  `WorkManager` objects to JavaScript.

## Packaged APK Boundaries

Packaged Node tasks must remain app-local:

- task records belong to the packaged app id and packaged private storage;
- packaged metadata may serialize project entries without plaintext source files at
  registration time;
- live execution must still revalidate a scoped readable entry when the worker fires;
- queued host fallback must reject persistent task registration rather than record a
  one-way effect;
- boot/package receivers and exact-alarm permissions may be injected only after an
  explicit packaged metadata opt-in and verifier report.

## Example Migration

Rhino disposable task:

```js
tasks.addDisposableTask({
  path: "./jobs/cleanup.js",
  date: new Date(Date.now() + 60_000),
});
```

Node today:

```js
"nodejs";

const workManager = require("work_manager");

const id = await workManager.scheduleOnce({
  scriptPath: "jobs/cleanup.cjs",
  backend: "embedded",
  delayMs: 60_000,
  taskTimeoutMs: 30_000,
});

console.log(id);
```

Rhino daily task:

```js
tasks.addDailyTask({
  path: "./jobs/report.js",
  time: "08:30",
});
```

Node S11-22:

```js
throw new Error("daily wall-clock task migration is design-gated");
```

Use `work_manager.schedulePeriodic()` only when the intended behavior is OS-managed
periodic cadence, not a wall-clock daily or weekly task.

## Verification Gate

`verifyNodeRhinoCompatTasksWorkManagerDesign` checks that:

- this design document contains the migration table and deferred-provider split;
- `tasks` status manifest entries distinguish partial WorkManager mapping,
  unsupported task database/broadcast semantics, and blocked raw task objects;
- `work_manager` remains the only promoted current capability;
- `require("tasks")`, `compat.tasks`, and `tasks` globals are not exposed in S11-22;
- migration and security docs warn against mapping long-running or user-visible
  tasks to a one-shot bridge call.
