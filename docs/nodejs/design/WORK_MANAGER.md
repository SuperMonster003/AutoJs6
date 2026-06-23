# Work Manager Design And MVP Gate

Status: Tasks 65, 180, S7-15, and S7-21 expose `require("work_manager")` as a controlled bridge MVP for scheduled-task registration and execution. S7-15 promotes the Android provider from metadata-only storage to WorkManager-backed embedded Node execution for scoped one-shot and periodic entries. S7-21 routes fired work through a dedicated `scheduled_runner` launch surface with `executionMode=scheduled`, CJS/ESM entry support, persisted `lastRun`/`runHistory`, and stable runner diagnostics. S11-22 documents Rhino `tasks` migration in `RHINO_TASKS_WORK_MANAGER_MIGRATION.md`; `require("tasks")` remains unavailable to Embedded Node scripts.

## Goal

Scheduled and background Node scripts should reuse AutoJs6 task ownership instead of giving Node direct access to Android scheduling APIs.

The future surface must preserve the Safe Node Profile:

- Node scripts may request task registration only through an AutoJs6 bridge module.
- The main app or packaged app owns persistence, prompts, receiver registration, and execution launch.
- The embedded Node service remains an execution target, not a background scheduler authority.
- Rhino task compatibility must not be broken.

## Current AutoJs6 Context

The existing Rhino task model is already broader than the first Node MVP should expose:

- `tasks.addDailyTask`, `addWeeklyTask`, and `addDisposableTask` create `TimedTask` records.
- `tasks.addIntentTask` creates `IntentTask` records for broadcast-style triggers.
- `TimedTaskManager` persists tasks and schedules timed work through `TimedTaskScheduler`.
- `AlarmTimedTaskScheduler` uses exact alarms and falls back to `WorkTimedTaskScheduler` when alarm scheduling fails.
- `WorkTimedTaskScheduler` runs timed work through Android WorkManager workers.
- `StaticBroadcastReceiver` and `DynamicBroadcastReceivers` dispatch intent tasks for boot, user, time, package, and custom actions.
- Packaged inrt apps already have boot and package-replaced receivers for packaged launch configuration.
- Embedded Node probes already treat inline broadcast, AlarmManager, JobScheduler, and WorkManager access as denied paths.

This means the Node design should extend AutoJs6 task descriptors and launch routing. It should not introduce a second scheduler stack inside the Node runtime.

## Capability Decisions

| Capability | Decision | Reason |
| --- | --- | --- |
| Timed metadata | MVP | One-shot and bounded periodic descriptors can be registered through the controlled `work_manager` bridge without exposing Android scheduler objects. |
| Timed execution | MVP | The Android provider can now enqueue scoped embedded Node one-shot and periodic work through AndroidX WorkManager, then launch fired work through the controlled scheduled-runner surface. Long-running restart, boot, broadcast, and user-visible task UI flows remain separate gates. S7-19 records Android 16 JobScheduler quota diagnostics for WorkManager-backed Node tasks, and S7-21 persists execution history and scheduled-runner native values. |
| Broadcast tasks | Deferred | Broadcast registration has exported receiver, action allowlist, privacy, and Android background-start implications. |
| App startup tasks | Deferred | Existing `ACTION_STARTUP` behavior is an intent task; Node should not silently persist app-start execution until prompts and storage are designed. |
| Boot tasks | Deferred | Boot execution requires manifest permission, user visibility, background-start policy, and packaged opt-in. |
| Package events | Deferred | Package add/remove/replace actions require manifest filters, `package` data scheme, privacy review, and rate limiting. |
| Raw WorkManager/AlarmManager/JobScheduler APIs | Unsupported | They would let Node bypass AutoJs6 prompts, task UI, packaged manifest checks, and lifecycle ownership. |

## Implemented MVP Scope

The Task 65/180/S7-15/S7-21 implementation exposes scheduled-task registration and controlled execution through `require("work_manager")`.

Current JavaScript shape:

```js
const workManager = require("work_manager");

const taskId = await workManager.scheduleOnce({
  scriptPath: "./main.cjs",
  backend: "embedded",
  delayMs: 1000,
  constraints: { network: true },
});

console.log(taskId);
console.log(await workManager.list());
```

MVP methods:

- `scheduleOnce(options): Promise<TaskId>`
- `schedulePeriodic(options): Promise<TaskId>`
- `cancel(taskId): Promise<void>`
- `list(): Promise<TaskInfo[]>`

Implemented descriptor fields:

- `scriptPath` or `entry`
- `backend`, currently `embedded` only
- `delayMs`
- `intervalMs` for periodic tasks
- `taskTimeoutMs` for the scheduled embedded Node run timeout (`timeoutMs` remains the bridge call timeout)
- `packaged` for packaged project-entry metadata
- `constraints.charging`
- `constraints.network`
- `constraints.idle`

Current behavior:

- descriptors are persisted as WorkManager task records under the app-private files directory
- `scheduleOnce` enqueues a `OneTimeWorkRequest`; `schedulePeriodic` enqueues a `PeriodicWorkRequest`
- the Worker reads the scoped entry file, launches `NativeNodeEmbeddedScriptService` with `executionMode=scheduled` and `launchSurface=scheduled_runner`, and persists the last run result plus bounded history
- workers return success or failure only; they do not return `Result.retry`
- `cancel` cancels the unique WorkManager work and removes the persisted descriptor
- `list` returns task records for the same package/project working-directory scope
- the Android provider validates descriptor policy again after JavaScript validation
- task metadata stores `backend` plus either ordinary `scriptPath` or project `entry` with `entryKind`
- `scriptPath` and `entry` must be relative, non-empty, NUL-free, and scoped to the working directory
- non-packaged paths must resolve to an existing file inside the working directory
- packaged project entries can serialize metadata without requiring a plaintext file path at registration time; live execution requires a readable scoped entry when the WorkManager worker fires
- fired work supports both CommonJS and ESM source names when the scoped entry and project metadata select those formats
- scheduled runs receive stable task environment variables: `AUTOJS6_NODE_SCHEDULED_TASK_ID`, `AUTOJS6_NODE_SCHEDULED_TASK_TYPE`, and `AUTOJS6_NODE_SCHEDULED_TASK_ATTEMPT`
- `lastRun` and `runHistory` expose trigger, runner version, attempt, execution id, execution mode, launch surface, stdout/stderr snippets, exit/error metadata, timeout state, Android quota policy, and selected embedded native values
- periodic intervals are bounded to at least `900000` ms
- queued host fallback rejects `work_manager` requests with `ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED`

MVP exclusions:

- no unrestricted background execution process
- no persisted task database entry yet
- no user-visible task UI entry yet
- no Rhino `tasks` compatibility facade yet
- no raw `TimedTask` or `IntentTask` object transfer
- no interactive UI from a scheduled background run
- no automatic restart or retry policy beyond the explicit WorkManager schedule cadence
- no `createBroadcastTask`
- no boot task registration
- no package event task registration
- no custom Android `IntentFilter` registration
- no raw WorkManager constraints API
- no exact-alarm opt-in from Node
- no background service control from Node

## Bridge Ownership

The `work_manager` module is a bridge module, not a Node builtin backed by Android objects.

Required flow:

```text
Node JS work_manager module
  -> autojs6:bridge request
  -> NativeNodeEmbeddedScriptService
  -> NodeBridgeCapabilityProvider
  -> metadata store now; future TimedTaskManager / task database / scheduler handoff
```

The Node service must not directly instantiate or register:

- `androidx.work.WorkManager`
- `AlarmManager`
- `JobScheduler`
- `BroadcastReceiver`
- `PendingIntent`
- raw Android `IntentFilter`

Every method must return a Promise, use a bounded timeout, and reject with stable bridge errors on policy denial, timeout, or provider failure.

## Node Task Descriptor

Existing `TimedTask` and `IntentTask` records store a script path. Node needs more metadata than a single path can safely represent. Do not encode full Node project state into `scriptPath` alone.

Future storage should use either a schema migration or sidecar descriptor table.

Suggested descriptor:

```kotlin
data class NodeScheduledTaskDescriptor(
    val version: Int = 1,
    val profile: String = "AutoJs6 Safe Node Profile",
    val backend: String = "embedded",
    val entryKind: String,
    val entryPath: String,
    val projectRoot: String?,
    val workingDirectory: String?,
    val sourceName: String?,
    val packageName: String?,
    val packageJsonMain: String?,
    val encryptedEntryId: String?,
    val moduleSourcesId: String?,
    val timeoutMs: Long,
    val argumentsJson: String,
    val createdByPackage: String,
    val createdAtEpochMs: Long,
)
```

Descriptor rules:

- `backend` must use the existing backend switch vocabulary: `embedded`, `native`, `rhino`, or `auto`; MVP should accept only `embedded`.
- `entryPath` must be relative to `projectRoot` or the scoped working directory.
- Absolute paths, NUL bytes, `..`, symlink escapes, and realpath escapes must be rejected before persistence.
- Encrypted packaged entries must store an encrypted entry id or module source id, not a decrypted plaintext path.
- `argumentsJson` must contain JSON primitives, arrays, and objects only.
- The descriptor version must be explicit so future ESM/network/worker task metadata can be migrated.

## Launch Routing

When a scheduled Node task fires, the receiver or worker should resolve the descriptor into the same execution request shape used by manual Node project launches.

Required launch properties:

- script execution goes through `ScriptEngineManager` or the existing Node project runner path
- backend selection remains explicit and logged
- embedded Node runs in `:nodejs_embedded`
- timeout and stop use the embedded lifecycle controller
- stdout, stderr, result, bridge diagnostics, and error shape are captured like manual runs
- the task id and schedule trigger are included in diagnostics
- fired WorkManager tasks must use `executionMode=scheduled` only through the `scheduled_runner` launch surface; direct script launches with `scheduled` still reject before user code
- each fired run gets a generated execution id tied to the task id and attempt count
- task records must append a bounded execution history instead of replacing all prior diagnostics

Timed task completion must update scheduler state only after the launch request has been accepted by AutoJs6. A later runtime failure should be visible in logs and notifications, not silently swallowed by the scheduler.

## Packaged APK Behavior

Packaged apps are app-local. They must not depend on the main AutoJs6 task database.

Rules:

- packaged Node tasks use the packaged app id and packaged private storage
- packaged tasks must use packaged project metadata, encrypted entries, and packaged module sources
- build-time metadata decides whether scheduled/background support is included
- receivers and permissions are injected only for opted-in task types
- packaged task settings must remain visible to the user inside the packaged app
- uninstalling the packaged app removes its scheduled work
- updating the packaged app must reconcile descriptor versions and stale task ids

Suggested packaged project metadata:

```json
{
  "node": {
    "backend": "embedded",
    "backgroundTasks": {
      "enabled": true,
      "timed": true,
      "boot": false,
      "packageEvents": false
    }
  }
}
```

If the packaged template does not carry live bridge metadata, `require("work_manager")` must reject with a stable bridge policy error. It must not queue persistent task registration as a one-way host effect. `require("tasks")` remains unavailable until a compatibility alias is deliberately designed.

## Manifest Requirements

Main AutoJs6 app behavior can reuse existing declared permissions and receivers, but the Node API still needs user confirmation before persistent registration.

Packaged APK requirements are feature-dependent:

- timed tasks using WorkManager: no special receiver permission beyond the packaged runtime components
- exact timed tasks: `android.permission.SCHEDULE_EXACT_ALARM` or `android.permission.USE_EXACT_ALARM` only if the packaged project explicitly opts into exact alarms
- boot tasks: `android.permission.RECEIVE_BOOT_COMPLETED` and a non-exported boot receiver
- user-unlocked boot recovery: a receiver handling `android.intent.action.USER_UNLOCKED`
- package events: a non-exported receiver with package actions and `<data android:scheme="package" />`
- long-running Node execution: `NativeNodeEmbeddedScriptService` with `android:process=":nodejs_embedded"` and the documented foreground service permissions/type
- foreground notification on Android 13+: notification permission handling where the packaged app posts foreground-service notifications

No packaged build should inject boot/package receivers merely because the app contains Node. The project must opt in and the verifier must report the resulting manifest surface.

## Security Prompt And Visibility

Persistent background execution requires explicit user visibility.

Before registration, the UI prompt should show:

- script or project name
- Node backend
- entry path
- working directory or project root
- schedule type and next run
- timeout
- whether the task can run after app startup or device boot
- requested Android permissions or manifest features
- packaged app id when running in an inrt APK

After registration, the task must be visible in task management UI with enable/disable and delete actions.

During execution:

- background Node runs must have log visibility
- long-running work must use a foreground notification when required by Android policy
- user stop must route through the unified lifecycle stop path
- task failures must be recorded with task id, entry, backend, error code, and trigger source
- Android 16+ WorkManager/JobScheduler quota risk must be visible in task
  records and last-run diagnostics

Policy limits should include:

- maximum number of Node tasks per project/package
- minimum schedule interval or OS-backed throttling behavior
- maximum default timeout
- exact-alarm opt-in separated from normal timed tasks
- no silent boot/package event registration from script code

## Error Policy

The MVP uses bridge errors rather than ad hoc exceptions.

Canonical errors:

- `ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED` for missing user approval or disallowed task type
- `ERR_AUTOJS6_BRIDGE_TIMEOUT` for provider timeout
- `ERR_AUTOJS6_BRIDGE_PROCESS_DEAD` when the service is destroyed before the request completes
- `ERR_AUTOJS6_FS_PATH_ESCAPE` or the existing scoped fs codes for invalid entries
- `ERR_AUTOJS6_BUILTIN_DISABLED` if `require("tasks")` remains unavailable in a profile

Avoid adding metadata or a future persistent task if any descriptor validation or prompt step fails.

## Future Broadcast Design Gate

Detailed boot, package-event, and custom broadcast policy is tracked in `docs/nodejs/design/BROADCAST_TASKS.md`.

Before enabling broadcast, startup, boot, or package-event tasks from Node, write a follow-up design that answers:

- exact action allowlist
- exported vs non-exported receiver behavior
- package event data-scheme requirements
- Android 8+ implicit broadcast limits
- boot/user-unlocked behavior after direct boot
- user prompt wording
- packaged manifest patching
- rate limits
- whether custom actions are local-only
- how task invocations receive the triggering intent payload without exposing raw `Intent`

The JavaScript result should receive a serialized trigger object, not a raw Android intent.

## Rhino Tasks Migration Gate

S11-22 keeps `work_manager` as the only current Node scheduled-task surface. It
does not add `require("tasks")`, `compat.tasks`, or task globals.

The safe migration subset is intentionally narrow:

- `tasks.addDisposableTask()` can become a future wrapper over
  `work_manager.scheduleOnce()` only when its absolute date/time is converted to a
  bounded future `delayMs` and the script entry is scoped.
- `tasks.queryTimedTasks()`, `getTimedTask()`, and `removeTimedTask()` can target
  JSON snapshots and ids for Node-owned WorkManager one-shot records only. P12-17
  exposes these as `compat.tasks` Rhino-name aliases without reading the Rhino
  task database.
- `tasks.addDailyTask()` and `tasks.addWeeklyTask()` remain rejected in P12-17
  with `ERR_AUTOJS6_RHINO_COMPAT_UNSUPPORTED` because wall-clock time, weekdays,
  next-run calculation, exact-alarm policy, and task UI state are not WorkManager
  periodic cadence.
- `tasks.addIntentTask()`, intent queries, startup, boot, package, and custom
  broadcast triggers stay with `BROADCAST_TASKS.md`.
- `tasks.addTask()`, `removeTask(task)`, and `updateTask(task)` are blocked by
  design when they depend on raw `TimedTask` or `IntentTask` Java objects.

See `docs/nodejs/design/RHINO_TASKS_WORK_MANAGER_MIGRATION.md` for the S11-22
mapping table and status manifest decisions. P12-15 narrows the
`compat.tasks` v1 surface in `docs/nodejs/design/COMPAT_TASKS_V1.md` to
disposable one-shot migration only, and P12-16 implements that facade:
`addDisposableTask` over `work_manager.scheduleOnce`, `removeTask(id)` over
`work_manager.cancel`, and `queryTasks(options?)` over `work_manager.list`, all
returning JSON-safe task ids or snapshots. P12-17 adds daily/weekly stable
rejection stubs plus `queryTimedTasks()`, `getTimedTask(id)`, and
`removeTimedTask(id)` aliases for Node-owned one-shot snapshots only; the
user-visible task database provider remains unpromoted.

## Tests Required

Task 65/180 coverage:

- `require("work_manager")` module routing and shadowing protection
- `require("tasks")` remains unavailable
- positive once and periodic metadata registration through a fake provider
- WorkManager-backed one-shot embedded Node execution that writes an observable result
- WorkManager-backed scheduled runner uses `executionMode=scheduled` and `launchSurface=scheduled_runner`
- CommonJS and ESM scheduled entries execute through the controlled runner and persist queryable results
- `lastRun` and bounded `runHistory` preserve attempt, trigger, execution id, launch surface, native values, stdout/stderr, exit/error, timeout, and Android quota diagnostics
- cancellation of delayed WorkManager work before execution
- packaged project-entry metadata execution when a scoped packaged entry file is readable
- `cancel` and `list`
- ordinary `scriptPath` versus project `entry` metadata serialization
- packaged project-entry metadata serialization, including packaged entries without a plaintext file
- queued fallback rejection
- Android provider persisted task storage
- scoped entry path denial for absolute, NUL, `..`, and missing non-packaged files
- bounded periodic interval denial
- non-embedded backend denial

Future scheduled-task work needs additional coverage:

- descriptor migration into the user-visible AutoJs6 task database
- scoped entry path denial for symlink-specific edge cases beyond canonical realpath checks
- user-denied prompt path
- provider timeout path beyond one-shot worker result persistence
- disabled broadcast/boot/package registration paths
- timeout or crash does not poison the next scheduled run
- scheduled task recovery after full process death and OS-delayed periodic cadence
- packaged manifest verifier reports task-related permissions and receivers
- full packaged APK smoke for a timed Node task when packaged task support is enabled

## Current Decision

Tasks 65, 180, S7-15, and S7-21 implement `require("work_manager")` as a controlled WorkManager-backed scheduling MVP. The Safe Node Profile still denies direct Android scheduler authority: Node receives only serializable task ids and task records, never raw WorkManager, AlarmManager, JobScheduler, BroadcastReceiver, PendingIntent, or IntentFilter objects. S7-15 permits scoped one-shot and bounded periodic embedded Node execution with explicit `work_manager` capability, minimum periodic interval enforcement, stable result/failure records, and no implicit retry. S7-21 permits `executionMode=scheduled` only through the WorkManager-owned `scheduled_runner` launch surface, adds CJS/ESM scheduled entry coverage, records runner attempt/execution id/env metadata, and exposes bounded execution history through `lastRun`/`runHistory`. S7-19 adds Android API-level policy diagnostics so WorkManager task records expose whether Android 16+ long-running workers share JobScheduler quota and how quota exhaustion should be handled. Broadcast, startup, boot, package-event registration, task UI migration, unrestricted periodic Node processes, and interactive UI from background scheduled tasks remain deferred.
