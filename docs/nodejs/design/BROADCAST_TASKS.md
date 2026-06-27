# Broadcast Task Design Gate

Status: design gate only. P12-18 rejects provider promotion for Node
IntentTask/broadcast compatibility and exposes only stable unsupported stubs
through `require("autojs6:compat").tasks`. Embedded Node scripts cannot
register boot, package-event, startup, exact-alarm, or custom
broadcast-triggered tasks yet.

P13-22 records this design gate as partial Phase 13 evidence only. Android/Rhino
`IntentTask` persistence and receiver substrate exist, but Node still has no
broadcast task provider, no receiver registration authority, no manifest
mutation authority, no packaged install/trigger/remove smoke, and no raw
`Intent` or `BroadcastReceiver` access.

## Goal

Boot and broadcast-triggered Node tasks must reuse AutoJs6 task ownership instead of giving Node direct access to Android broadcast APIs.

The future surface must preserve the Safe Node Profile:

- Node scripts request registration only through an AutoJs6 bridge provider.
- The main app or packaged app owns prompts, persistence, receivers, manifest patches, and launch routing.
- The embedded Node service remains an execution target, not a broadcast receiver owner.
- Trigger payloads are serialized JSON snapshots, never raw `Intent` objects.
- Rhino task compatibility must not be broken.

## Current AutoJs6 Context

AutoJs6 already has a broad Rhino-era broadcast task model:

- `IntentTask` stores `scriptPath`, `action`, `category`, `dataType`, and `local`.
- `StaticBroadcastReceiver` handles a fixed action set including boot, user, time, power, media, package, and connectivity actions.
- `DynamicBroadcastReceivers` registers runtime receivers and in-app event-bus listeners for intent tasks.
- Package actions require `<data android:scheme="package" />`.
- Existing receivers dispatch through `ScriptIntents.prepareLaunch()` and `Scripts.executeLaunch()`.
- Packaged inrt apps already have boot and package-replaced receivers for packaged launch configuration.

Node must extend this ownership model. It must not introduce a second receiver stack inside the Node runtime.

## Capability Decisions

| Capability | Decision | Reason |
| --- | --- | --- |
| Boot completed | Deferred | Requires manifest permission, user consent, direct-boot behavior, foreground/background launch policy, and task UI visibility. |
| User unlocked | Deferred | Useful after boot, but direct-boot storage and locked-user constraints must be explicit. |
| Package added/changed/removed | Deferred | Requires package data scheme, privacy review, package visibility policy, and event rate limits. |
| Custom app-local broadcasts | Future first candidate | App-local or package-private actions are easier to constrain than exported system-wide receivers. |
| Exported custom broadcasts | Unsupported by default | Exported receivers create an external IPC surface and need a separate allowlist and abuse review. |
| Raw BroadcastReceiver/Intent APIs | Unsupported | They bypass prompts, manifest verification, serialization, and lifecycle ownership. |

## Proposed Future API Shape

No JavaScript API is implemented yet. A future API should extend the controlled scheduling surface rather than expose Android objects.

Possible shape:

```js
const workManager = require("work_manager");

const taskId = await workManager.registerBroadcastTask({
  entry: "main.cjs",
  backend: "embedded",
  trigger: {
    type: "package",
    actions: ["android.intent.action.PACKAGE_ADDED"],
  },
  timeoutMs: 30000,
});
```

The name and method shape are not committed by this design. Any runtime API must be introduced by a later implementation task with tests and docs.

## Trigger Descriptor

Future persistent storage should use an explicit descriptor, not overload `IntentTask.scriptPath`.

Suggested descriptor:

```kotlin
data class NodeBroadcastTaskDescriptor(
    val version: Int = 1,
    val profile: String = "AutoJs6 Safe Node Profile",
    val backend: String = "embedded",
    val entryKind: String,
    val entryPath: String,
    val workingDirectory: String?,
    val packageName: String?,
    val triggerType: String,
    val actions: List<String>,
    val categories: List<String> = emptyList(),
    val dataSchemes: List<String> = emptyList(),
    val localOnly: Boolean = true,
    val timeoutMs: Long,
    val argumentsJson: String = "[]",
    val createdByPackage: String,
    val createdAtEpochMs: Long,
)
```

Descriptor rules:

- `backend` accepts only `embedded` for the first Node implementation.
- `entryPath` must be scoped relative to the project root or working directory.
- Absolute paths, NUL bytes, `..`, symlink escapes, and realpath escapes are denied before persistence.
- Trigger actions must come from a documented allowlist.
- Trigger payloads passed to Node are JSON snapshots.
- Raw Android `Intent`, `Bundle`, `Uri`, `BroadcastReceiver`, and `Context` objects never cross the bridge.

## Boot Completed Behavior

Boot support must be opt-in at both project and user levels.

Required behavior:

- No boot receiver is injected or enabled merely because a project uses Node.
- The main app can reuse existing boot receivers only after user-visible Node task registration.
- Packaged APKs must opt in before `android.permission.RECEIVE_BOOT_COMPLETED` or boot receivers are included.
- `LOCKED_BOOT_COMPLETED` must not run encrypted project entries unless direct-boot storage is explicitly designed.
- If direct-boot storage is not available, boot tasks wait for `USER_UNLOCKED`.
- Boot tasks must include a boot trigger payload such as `{ type: "boot", action, lockedBoot, userUnlocked }`.
- Long-running Node work must use the existing foreground-service policy and notification visibility when required.
- Startup must be rate-limited so repeated boot/user-unlocked events do not create duplicate executions.

Recommended first behavior:

- use `BOOT_COMPLETED` only to re-check and enqueue eligible visible task descriptors
- run encrypted or normal app-private Node entries only after `USER_UNLOCKED`
- reject locked-boot execution with `ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED` until direct-boot storage is designed

## Package Event Behavior

Package event support is high risk because it can reveal app-install behavior and run often.

Allowed first-candidate actions:

- `android.intent.action.PACKAGE_ADDED`
- `android.intent.action.PACKAGE_REMOVED`
- `android.intent.action.PACKAGE_CHANGED`
- `android.intent.action.MY_PACKAGE_REPLACED` for packaged self-update recovery

Deferred actions:

- `PACKAGE_DATA_CLEARED`
- `PACKAGE_RESTARTED`
- `PACKAGE_FIRST_LAUNCH`
- broad package query or package-list APIs

Rules:

- receivers handling package events must include `<data android:scheme="package" />`
- trigger payloads include only serialized fields such as `action`, `packageName`, `replacing`, `uid`, and `timestamp`
- no raw `PackageManager`, application label, icon, install source, signature, or package list is exposed by default
- package event tasks require explicit user consent and rate limits
- packaged APKs must declare the receiver only when project metadata opts in

## Custom Broadcast Behavior

Custom broadcast support should start app-local.

Recommended first candidate:

- only app-local or package-private actions
- action names must start with the app package prefix or a configured project prefix
- registration remains non-exported
- payloads contain JSON primitives only
- custom system-wide exported actions are denied by default

Deferred:

- exported receiver registration
- arbitrary action names
- wildcard categories
- MIME/data matching beyond package events
- ordered broadcasts and result extras
- sticky broadcasts

## User Consent And Visibility

Persistent broadcast-triggered execution requires explicit user visibility.

The registration prompt must show:

- script or project name
- Node backend
- entry path and working directory
- trigger type and exact actions
- whether the task can run after boot, after unlock, or on package changes
- timeout and run limits
- packaged app id
- manifest permissions or receivers that will be added
- whether execution may require a foreground notification

After registration:

- the task must appear in task UI
- users must be able to disable or delete it
- executions must be visible in logs and notifications where required
- failures must record task id, action, package, backend, entry, and error code

## Packaged APK Manifest Patch

Packaged Node broadcast support is opt-in.

The packaged manifest patcher must:

- add `android.permission.RECEIVE_BOOT_COMPLETED` only for opted-in boot tasks
- add a non-exported boot receiver only for opted-in boot tasks
- add `USER_UNLOCKED` only when unlock recovery is requested
- add package-event receiver entries only for opted-in package triggers
- include `<data android:scheme="package" />` for package events
- keep custom broadcast receivers non-exported by default
- report all added receivers, actions, data schemes, permissions, and foreground-service implications in packaged diagnostics
- fail closed when metadata requests a trigger unsupported by the packaged template

No packaged APK should gain boot or package receivers merely because it bundles `libnode.so`.

## Android Background Restrictions

The design must account for:

- Android 8+ implicit broadcast limits
- background activity start restrictions
- foreground-service type and notification requirements for long-running work
- boot-time startup throttling
- Doze, standby buckets, and manufacturer power policies
- direct boot and credential-encrypted storage boundaries
- package visibility and privacy restrictions
- ordered/sticky broadcast deprecations and restrictions

Broadcast receivers should do minimal work:

1. validate the trigger
2. look up a persisted visible descriptor
3. enqueue or request a controlled AutoJs6 execution
4. return quickly

The receiver must not spin a Node event loop directly.

## Safety Restrictions

Default restrictions:

- no raw Android broadcast APIs in Node
- no exported custom receivers by default
- no arbitrary action strings
- no wildcard package monitoring
- no direct boot execution for encrypted entries
- no Node task execution while the app lacks required foreground-service/notification policy
- no unbounded run frequency
- no duplicate concurrent execution for the same task id unless explicitly allowed
- no dynamic manifest mutation from Node

Policy limits should include:

- maximum broadcast tasks per project/package
- per-task cooldown
- per-action rate limit
- maximum payload bytes
- maximum execution timeout
- maximum pending executions after boot

## Error Policy

Future runtime support must use stable bridge errors:

- `ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED` for unsupported trigger type, missing consent, missing manifest opt-in, or unsafe action
- `ERR_AUTOJS6_BRIDGE_TIMEOUT` for provider timeout
- `ERR_AUTOJS6_BRIDGE_PROCESS_DEAD` if the Node service dies before registration completes
- existing scoped fs codes for invalid entries
- `ERR_AUTOJS6_BUILTIN_DISABLED` if a proposed module is unavailable in the profile

## Test Gate

Before implementation, add tests for:

- boot trigger registration disabled by default
- package trigger registration disabled by default
- custom exported broadcast denied
- allowlisted local custom broadcast descriptor serialization
- user-denied prompt path
- scoped entry path denial
- manifest patch opt-in and opt-out
- package receiver requires `package` data scheme
- trigger payload serialization without raw Intent access
- receiver enqueues execution without running Node on the receiver thread
- duplicate/rate-limit behavior
- packaged diagnostic report for every added receiver and permission

## Current Decision

Task 66 does not implement boot or broadcast-triggered Node tasks. It records
the design gate for future work. The current scheduling runtime surface is the
S7-15 controlled `require("work_manager")` WorkManager timed MVP.

P12-18 keeps broadcast provider promotion rejected. The only Rhino-name runtime
surface is stable rejection under `require("autojs6:compat").tasks`:

- `addIntentTask()`
- `queryIntentTasks()`
- `getIntentTask()`
- `removeIntentTask()`

These methods reject with `ERR_AUTOJS6_RHINO_COMPAT_UNSUPPORTED` before bridge
dispatch. Boot, package-event, startup, exact-alarm, and custom broadcast
execution stay disabled until a later implementation provides user-visible
persistence, manifest verification, scoped descriptor storage, rate limits,
trigger payload serialization, packaged diagnostics, receiver cleanup, and
focused tests. See `TASKS_INTENT_BROADCAST_COMPATIBILITY_DECISION.md` for the
P12-18 compatibility decision.
