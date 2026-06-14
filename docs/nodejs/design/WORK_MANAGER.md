# Work Manager Design Gate

Status: design gate only. No runtime behavior is enabled by this document, and `require("tasks")` is not exposed to Embedded Node scripts yet.

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
| Timed tasks | Future MVP | One-shot, daily, and weekly schedules map cleanly to existing `TimedTask` semantics and can be made user-visible. |
| Broadcast tasks | Deferred | Broadcast registration has exported receiver, action allowlist, privacy, and Android background-start implications. |
| App startup tasks | Deferred | Existing `ACTION_STARTUP` behavior is an intent task; Node should not silently persist app-start execution until prompts and storage are designed. |
| Boot tasks | Deferred | Boot execution requires manifest permission, user visibility, background-start policy, and packaged opt-in. |
| Package events | Deferred | Package add/remove/replace actions require manifest filters, `package` data scheme, privacy review, and rate limiting. |
| Raw WorkManager/AlarmManager/JobScheduler APIs | Unsupported | They would let Node bypass AutoJs6 prompts, task UI, packaged manifest checks, and lifecycle ownership. |

## MVP Scope

The first implementation should expose only timed task registration through a future `tasks` bridge module.

Proposed JavaScript shape:

```js
const tasks = require("tasks");

const task = await tasks.createTimedTask({
  name: "daily-node-job",
  entry: "./main.cjs",
  backend: "embedded",
  schedule: {
    type: "daily",
    time: "09:30",
  },
  timeoutMs: 30000,
  arguments: ["from-scheduler"],
});

console.log(task.id, task.nextRunAt);
```

MVP methods:

- `createTimedTask(options): Promise<TaskInfo>`
- `list(options?): Promise<TaskInfo[]>`
- `get(id): Promise<TaskInfo | null>`
- `cancel(id): Promise<boolean>`
- `setEnabled(id, enabled): Promise<boolean>`

MVP schedule kinds:

- `once`: absolute local date/time
- `daily`: local time
- `weekly`: local time plus day-of-week list

MVP exclusions:

- no `createBroadcastTask`
- no boot task registration
- no package event task registration
- no custom Android `IntentFilter` registration
- no direct WorkManager constraints API
- no exact-alarm opt-in from Node
- no background service control from Node

## Bridge Ownership

The future `tasks` module must be a bridge module, not a Node builtin backed by Android objects.

Required flow:

```text
Node JS tasks module
  -> autojs6:bridge request
  -> NativeNodeEmbeddedScriptService
  -> main-process task capability provider
  -> TimedTaskManager / task database / scheduler
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

If the packaged template does not carry live bridge metadata, `require("tasks")` must remain unavailable or reject with a stable unsupported error. It must not queue persistent task registration as a one-way host effect.

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

Policy limits should include:

- maximum number of Node tasks per project/package
- minimum schedule interval or OS-backed throttling behavior
- maximum default timeout
- exact-alarm opt-in separated from normal timed tasks
- no silent boot/package event registration from script code

## Error Policy

Future implementation should use bridge errors rather than ad hoc exceptions.

Suggested canonical errors:

- `ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED` for missing user approval or disallowed task type
- `ERR_AUTOJS6_BRIDGE_TIMEOUT` for provider timeout
- `ERR_AUTOJS6_BRIDGE_PROCESS_DEAD` when the service is destroyed before the request completes
- `ERR_AUTOJS6_FS_PATH_ESCAPE` or the existing scoped fs codes for invalid entries
- `ERR_AUTOJS6_BUILTIN_DISABLED` if `require("tasks")` remains unavailable in a profile

Avoid adding a persistent task if any descriptor validation or prompt step fails.

## Future Broadcast Design Gate

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

## Tests Required For Implementation

Future runtime work needs both positive and negative coverage:

- `require("tasks")` unavailable until the feature is implemented
- timed task descriptor serialization and migration
- positive once/daily/weekly registration through a fake provider
- scoped entry path denial for absolute, NUL, `..`, symlink, and realpath escapes
- user-denied prompt path
- provider timeout path
- disabled broadcast/boot/package registration paths
- scheduled run launches embedded Node with expected backend and timeout
- timeout or crash does not poison the next scheduled run
- packaged manifest verifier reports task-related permissions and receivers
- packaged smoke for a timed Node task when packaged task support is enabled

## Current Decision

Task 38 does not implement `require("tasks")`. The current Safe Node Profile should continue to deny direct scheduling authority. Timed task registration is the only approved MVP direction, and even that must land later through a bridge provider, user-visible registration, descriptor storage, scoped path validation, packaged manifest verification, and focused tests.
