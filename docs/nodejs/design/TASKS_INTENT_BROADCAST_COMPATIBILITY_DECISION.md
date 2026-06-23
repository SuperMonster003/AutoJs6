# Tasks Intent/Broadcast Compatibility Decision

Status: P12-18 rejected provider promotion for Rhino `IntentTask` and
broadcast-triggered task compatibility. The runtime exposes stable rejection
stubs under `require("autojs6:compat").tasks`, but it does not add a broadcast
task provider, Android receiver registration, manifest mutation, or
`require("tasks")`.

## Decision

P12-18 keeps Rhino `IntentTask` database and broadcast trigger parity out of
the Node provider surface.

The following `compat.tasks` methods are explicit stable-error stubs:

- `addIntentTask(options?)` rejects with
  `ERR_AUTOJS6_RHINO_COMPAT_UNSUPPORTED`.
- `queryIntentTasks(options?)` rejects with
  `ERR_AUTOJS6_RHINO_COMPAT_UNSUPPORTED`.
- `getIntentTask(id, options?)` rejects with
  `ERR_AUTOJS6_RHINO_COMPAT_UNSUPPORTED`.
- `removeIntentTask(id, options?)` rejects with
  `ERR_AUTOJS6_RHINO_COMPAT_UNSUPPORTED`.

The stubs fail before bridge dispatch. They do not create `IntentTask` database
rows, query existing Rhino `IntentTask` records, register or unregister
receivers, add manifest entries, or expose trigger payloads.

## Rejected Runtime Authority

P12-18 does not expose any of the following to Node scripts:

- raw `BroadcastReceiver`, `Intent`, `IntentFilter`, `PendingIntent`, `Bundle`,
  `Uri`, Android `Context`, or receiver handles;
- custom broadcast authority or arbitrary action strings;
- boot, user-unlocked, package-event, startup, exact-alarm, or custom
  broadcast-triggered task registration;
- dynamic manifest mutation from JavaScript;
- receiver lifecycle ownership inside the embedded Node runtime;
- raw Rhino `IntentTask` objects or database row cursors.

Boot, package-event, exact-alarm, startup, and custom broadcast triggers must
remain separate capabilities. A future provider may split them further, for
example into `tasks.broadcast`, `tasks.boot`, and `tasks.package_events`, but
those names are not promoted by P12-18.

## Why Provider Promotion Is Rejected

Broadcast-triggered tasks are persistent Android entry points. Registering them
changes app behavior outside the current script run and can create background
execution, external IPC, package visibility, and boot-time behavior. A Node
facade cannot safely implement that by forwarding one bridge call.

Rhino `IntentTask` compatibility also depends on the AutoJs6 task database and
receiver stack. Returning raw `IntentTask` instances would violate the Safe
Node Profile, while returning JSON snapshots requires a dedicated provider and
explicit lifecycle ownership.

## Future Provider Preconditions

A future provider can revisit IntentTask/broadcast compatibility only after all
of the following are designed, implemented, documented, and verified:

- Capability split: boot, user-unlocked, package-event, startup, exact-alarm,
  and custom broadcast authority must be individually gated.
- Task ownership: main app vs packaged app ownership, project identity, user
  visibility, user consent, disable/delete authority, and launch routing.
- Receiver lifecycle: static receiver declarations, dynamic receiver
  registration, cleanup, process death recovery, duplicate suppression, and
  receiver-thread non-execution guarantees.
- Trigger descriptor schema: backend, scoped entry, trigger type, actions,
  categories, data schemes, local-only/exported state, timeout, arguments,
  versioning, and created-by package metadata.
- Trigger payload serialization: JSON snapshots only, bounded payload bytes,
  no raw `Intent`, `Bundle`, `Uri`, `PackageManager`, signature, icon, or
  package-list objects.
- Custom broadcast authority: default-deny exported receivers, app-local or
  package-private first candidate, action allowlist, prefix policy, and abuse
  review.
- Packaged APK behavior: explicit metadata opt-in, manifest patch report,
  receiver export state, `RECEIVE_BOOT_COMPLETED`, package data-scheme
  declarations, foreground disclosure, and fail-closed diagnostics.
- Background policy: Android 8+ implicit broadcast limits, direct boot and
  credential-encrypted storage, Doze, foreground service notification policy,
  rate limits, cooldowns, and maximum pending executions.
- Error policy: stable `ERR_AUTOJS6_RHINO_COMPAT_*` errors before bridge
  dispatch for unpromoted compatibility calls and stable bridge errors for a
  future provider.

## Migration Guidance

Keep Rhino `IntentTask` scripts on the Rhino engine until a dedicated broadcast
task provider is promoted.

Use `compat.tasks.addDisposableTask()` or `require("work_manager")` only for
scheduled Node work that does not need Android broadcast receiver registration:

```js
"nodejs";

const { tasks } = require("autojs6:compat");

try {
  await tasks.addIntentTask({
    path: "jobs/on-package.cjs",
    action: "android.intent.action.PACKAGE_ADDED",
  });
} catch (error) {
  console.log(error.code); // ERR_AUTOJS6_RHINO_COMPAT_UNSUPPORTED
}
```

Do not replace Android broadcast triggers with `BroadcastChannel`. The Node
`BroadcastChannel` global is only an in-process JavaScript messaging primitive
and is not an Android receiver.

Do not pass raw `IntentTask`, `Intent`, `BroadcastReceiver`, `IntentFilter`, or
`PendingIntent` objects into Node. Use explicit JSON descriptors only after a
future provider defines and verifies them.
