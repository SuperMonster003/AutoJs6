# Phase 14 Intent Broadcast Provider

Status: partial.

P14-20 records the v1.2 contract for a future JSON-safe
IntentTask/broadcast provider. It imports the P13-22 Android/Rhino receiver
substrate, JSON snapshot, packaged denial, and compat rejection evidence, then
ties that evidence to the P14 task descriptor, schema rollback, and packaged
task ownership policies from P14-17 through P14-19.

This gate does not expose `require("tasks")`, `require("tasks.broadcast")`, or
future task/broadcast capabilities. Runtime `compat.tasks.addIntentTask()`,
`queryIntentTasks()`, `getIntentTask(id)`, and `removeIntentTask(id)` remain
Promise-returning stable rejection stubs until a dedicated provider lands with
device and packaged evidence.

## Contract

- Future provider name: `tasks.broadcast`.
- Descriptor schema: `autojs6-node-intent-broadcast-descriptor-v1`.
- Future operations: `register`, `list`, `get`, `enable`, `disable`,
  `remove`, and `validateManifestPatch`.
- First candidate triggers: `custom_local`, `custom_package_private`, and
  `package_event_self_update`.
- Deferred triggers: boot, user-unlocked, startup, exact-alarm, broad package
  events, and exported custom broadcasts until explicit evidence exists.
- Current authority: not exposed.

Descriptors must be provider-owned JSON records with opaque Node ids, package
identity, project identity, scoped entry path, trigger descriptor, manifest
patch policy, payload policy, enabled state, and descriptor version. Legacy
Rhino `IntentTask` rows remain visible to Rhino and may be copied only through a
reviewed migration path; Node cannot mutate or hold raw legacy row handles.

## Manifest And Export Policy

Packaged broadcast support is opt-in. No packaged APK may gain boot,
user-unlocked, package, or custom receivers merely because it bundles Node
runtime files.

Before promotion, the manifest patcher must archive every receiver, action,
category, data scheme, permission, exported flag, foreground-service
implication, and unsupported trigger. `android.permission.RECEIVE_BOOT_COMPLETED`
is allowed only for opted-in boot tasks. Package-event receivers must declare
`<data android:scheme="package" />`. Custom receivers default to
non-exported. Exported receivers stay denied unless a future gate adds explicit
packaged metadata, reviewed action allowlists, signature or app-private
permission policy, abuse review, and Android 12 / Android 15+ evidence.

## Trigger Payload And Remove Behavior

Trigger payloads must be bounded JSON snapshots using
`autojs6-node-broadcast-trigger-payload-v1`. Raw `Intent`, `Bundle`, `Uri`,
`BroadcastReceiver`, `PendingIntent`, `PackageManager`, signatures, icons,
package lists, receiver handles, database cursors, and Java objects never cross
the bridge.

Receivers must validate the trigger, serialize a privacy-filtered payload,
enqueue controlled AutoJs6 execution, and return without running Node on the
receiver thread. Remove/disable must delete only owned descriptors, unregister
dynamic receivers, disable or remove owned packaged receiver patches, cancel
pending owned executions, preserve legacy Rhino rows, and deny cross-package
remove attempts.

## Open Archives

- `intent_broadcast_live_provider_archive`
- `intent_broadcast_manifest_synthesis_archive`
- `intent_broadcast_exported_permission_policy_archive`
- `intent_broadcast_trigger_payload_schema_archive`
- `intent_broadcast_remove_cleanup_archive`

## Gate

Host verifier:

```powershell
node tools/nodejs/phase14/verify-node-phase14-intent-broadcast-provider.js --repo-root D:\idea-projects\AutoJs6-nodejs --report-dir D:\idea-projects\AutoJs6-nodejs\build\reports\nodejs
```

Gradle gate:

```powershell
.\gradlew.bat --console=plain :app:verifyNodePhase14IntentBroadcastProvider
```

Expected reports:

```text
build/reports/nodejs/phase14-intent-broadcast-provider.json
build/reports/nodejs/phase14-intent-broadcast-provider.md
```
