# P13-22 Intent Broadcast Provider

Status: partial

P13-22 records the current IntentTask and broadcast-triggered task provider
state for Phase 13. The Android/Rhino runtime already has `IntentTask`,
`IntentTaskDatabase`, `TimedTaskManager`, `BaseBroadcastReceiver`,
`StaticBroadcastReceiver`, and `DynamicBroadcastReceivers` infrastructure.
Node also has stable `compat.tasks` rejection stubs for the Rhino IntentTask
API names.

The surface is not promoted to `pro_compat_opt_in` yet. Broadcast-triggered
tasks are persistent Android entry points. They can change app behavior after a
script exits, at boot, on package events, or through external broadcasts. P13-22
therefore keeps direct provider registration unavailable until capability
split, receiver authority, manifest synthesis, trigger payload serialization,
packaged behavior, user disclosure, and install/trigger/remove evidence land.

## Implemented Evidence

- Rhino substrate: `tasks.addIntentTask()`, `queryIntentTasks()`,
  `getIntentTask(id)`, and `removeIntentTask(id)` exist for the Rhino runtime
  over `IntentTask` objects and the legacy task database.
- Android persistence: `IntentTaskDatabase` persists `script_path`, `action`,
  `category`, `data_type`, and `local` columns.
- Receiver stack: `StaticBroadcastReceiver` records fixed boot, time, package,
  power, media, user, and connectivity actions; `DynamicBroadcastReceivers`
  registers custom runtime actions and in-app event-bus listeners.
- Launch path: `BaseBroadcastReceiver.runTask()` resolves an `IntentTask`
  through `ScriptIntents.prepareLaunch()` and `Scripts.executeLaunch()`.
- Node boundary: `compat.tasks.addIntentTask()`, `queryIntentTasks()`,
  `getIntentTask(id)`, and `removeIntentTask(id)` reject with
  `ERR_AUTOJS6_RHINO_COMPAT_UNSUPPORTED` before bridge dispatch.
- Raw boundary: raw `BroadcastReceiver`, `Intent`, `IntentFilter`,
  `PendingIntent`, `Bundle`, `Uri`, `PackageManager`, signatures, icons,
  package lists, receiver handles, and raw `IntentTask` objects are not exposed
  to Node.

## Boundaries

- `require("tasks")`, global `tasks`, `tasks.broadcast`, `tasks.intent`,
  `tasks.boot`, and `tasks.package_events` are not accepted capability or
  module names.
- Node cannot register Android receivers, mutate manifests, or accept external
  broadcast wakeups through the current Safe Node Profile.
- `BroadcastChannel` remains an in-process JavaScript messaging primitive; it
  is not an Android broadcast receiver.
- Existing packaged Node support must not inject boot, user-unlocked, package,
  or custom broadcast receivers merely because an APK contains Node runtime
  files.
- A future provider must return bounded JSON trigger payload snapshots instead
  of raw Android `Intent` or `Bundle` objects.
- For P13-22 v1.1, `compat.tasks.addIntentTask()`,
  `queryIntentTasks()`, `getIntentTask(id)`, and `removeIntentTask(id)` remain
  Promise-returning stable rejection stubs. A future promotion must be owned by
  a dedicated JSON-safe broadcast provider; it must not make these compat names
  direct wrappers over raw Rhino `IntentTask` rows or objects.

## Remaining Blockers

- Split capabilities for app-local custom broadcasts, exported broadcasts,
  boot/user-unlocked, package events, startup, and exact-alarm triggers.
- Define trigger descriptors with backend, scoped entry, actions, categories,
  data schemes, local/exported state, timeout, arguments, task id, package
  identity, and descriptor version.
- Implement receiver ownership with duplicate suppression, unregister/delete
  cleanup, process-death recovery, and receiver-thread non-execution
  guarantees.
- Add packaged APK metadata, manifest patch reports, receiver export state,
  `RECEIVE_BOOT_COMPLETED`, package data-scheme declarations, install-run
  positive/negative smokes, and trigger/remove tests.
- Add trigger payload serialization with byte budgets and privacy filtering for
  package names, replacing state, uid, timestamps, and custom payloads.
- Add user-visible consent, task UI visibility, disable/delete authority,
  foreground disclosure, rate limits, cooldowns, and failure diagnostics.

## Gate

Host verifier:

```powershell
node tools/nodejs/phase13/verify-intent-broadcast-provider.js --repo-root D:\idea-projects\AutoJs6 --report-dir D:\idea-projects\AutoJs6\build\reports\nodejs
```

Gradle gate:

```powershell
.\gradlew.bat --console=plain :app:verifyNodePhase13IntentBroadcastProvider
```

Expected reports:

```text
build/reports/nodejs/phase13-intent-broadcast-provider.json
build/reports/nodejs/phase13-intent-broadcast-provider.md
```
