# Rhino Tasks Compatibility

Status: P12-19 migration examples and smoke gate.

P12-19 does not promote a new provider. It records the script-author view of the
current `require("autojs6:compat").tasks` surface after P12-16 through P12-18:
Node-owned disposable one-shot work is usable through async APIs, while
daily/weekly wall-clock task database parity, IntentTask/broadcast triggers, and
raw task-object mutation remain rejected.

## Migration Buckets

### supported_async

These APIs are async and supported for Node-owned one-shot WorkManager records.
The status manifest still marks the `tasks` module `partial` because the support
is intentionally scoped.

| Rhino pattern | Node compat pattern | Notes |
| --- | --- | --- |
| `tasks.addDisposableTask({ path, date })` | `await compat.tasks.addDisposableTask({ path, delayMs })` | Returns a JSON-safe WorkManager id, not a raw `TimedTask`. |
| `tasks.queryTimedTasks()` | `await compat.tasks.queryTasks()` or `await compat.tasks.queryTimedTasks()` | Lists Node-owned one-shot snapshots only. |
| `tasks.getTimedTask(id)` | `await compat.tasks.getTimedTask(id)` | Filters Node-owned one-shot snapshots and returns `null` when absent. |
| `tasks.removeTimedTask(id)` | `await compat.tasks.removeTask(id)` or `await compat.tasks.removeTimedTask(id)` | Cancels explicit Node-owned ids only. |

### unsupported

These APIs are not mapped in Node. P12-17 and P12-18 provide stable rejection
stubs where a Rhino-name method exists on `compat.tasks`.

| Rhino API | Node result | Reason |
| --- | --- | --- |
| `tasks.addDailyTask(options)` | `ERR_AUTOJS6_RHINO_COMPAT_UNSUPPORTED` | Daily wall-clock user task database semantics are not WorkManager periodic cadence. |
| `tasks.addWeeklyTask(options)` | `ERR_AUTOJS6_RHINO_COMPAT_UNSUPPORTED` | Weekday flags, next-run calculation, and task UI ownership need a dedicated provider. |
| `tasks.addIntentTask(options)` | `ERR_AUTOJS6_RHINO_COMPAT_UNSUPPORTED` | Broadcast/startup/boot/package/custom triggers require separate capability design. |
| `tasks.queryIntentTasks(options)` | `ERR_AUTOJS6_RHINO_COMPAT_UNSUPPORTED` | No IntentTask database snapshot provider is promoted. |
| `tasks.getIntentTask(id)` | `ERR_AUTOJS6_RHINO_COMPAT_UNSUPPORTED` | Raw IntentTask identity and snapshots remain unavailable. |
| `tasks.removeIntentTask(id)` | `ERR_AUTOJS6_RHINO_COMPAT_UNSUPPORTED` | Receiver cleanup and user-visible deletion need a provider. |
| `tasks.daysToTimeFlag(days)` | no Node compat API | Pure utility remains outside disposable task v1. |
| `tasks.timeFlagToDays(flag)` | no Node compat API | Pure utility remains outside disposable task v1. |

### blocked_by_design

These Rhino patterns depend on raw Java task objects or mutable task identity and
must not cross the V8 bridge.

| Rhino API | Node result | Reason |
| --- | --- | --- |
| `tasks.addTask(task)` | blocked_by_design | Raw `TimedTask` / `IntentTask` Java objects are not JSON-safe descriptors. |
| `tasks.removeTask(task)` | blocked_by_design | Raw task object identity is not the same as explicit Node-owned WorkManager ids. |
| `tasks.updateTask(task)` | `ERR_AUTOJS6_RHINO_COMPAT_BLOCKED_BY_DESIGN` | No JSON patch/update provider exists for the AutoJs6 user task database. |

## Checked Examples

- `docs/nodejs/examples/rhino-compat/09-tasks.rhino.js` shows the legacy Rhino
  patterns that scripts commonly use.
- `docs/nodejs/examples/rhino-compat/09-tasks.node.cjs` shows the corresponding
  Node `autojs6:compat` migration surface: supported async disposable-task APIs,
  stable unsupported daily/weekly and intent-task rejections, and the blocked
  `updateTask()` result.

## Verification

Run:

```powershell
.\gradlew.bat --console=plain :app:verifyNodeRhinoCompatTasksMigrationExamples
```

The gate checks the tasks examples with `node --check`, verifies the example
manifest and README registration, confirms the `supported_async`,
`unsupported`, and `blocked_by_design` buckets above, and ensures the tasks
matrix advances to P12-20 without changing provider/runtime behavior.
