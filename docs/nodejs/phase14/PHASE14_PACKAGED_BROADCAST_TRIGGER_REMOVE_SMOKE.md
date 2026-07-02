# Phase 14 Packaged Broadcast Trigger Remove Smoke

Status: partial.

P14-21 records the packaged broadcast trigger/remove smoke contract for the
future `tasks.broadcast` provider. It imports the P13-22 packaged
IntentTask/broadcast boundary smoke and the P14-20 JSON-safe provider contract.
This gate does not promote positive broadcast trigger delivery yet.

Current packaged evidence proves the boundary state:

- `packaged.intent_broadcast.ready=true`
- `packaged.intent_broadcast.compat_rejected=true`
- `packaged.intent_broadcast.future_modules_denied=true`
- `packaged.intent_broadcast.raw_globals_absent=true`
- `packaged.intent_broadcast.broadcast_channel_js_only=true`
- `packaged.intent_broadcast.surface=true`
- `packaged.intent_broadcast.metadata_denied=true`

## Future Positive Smoke

A promotable packaged positive smoke must install an opted-in packaged APK,
register a package-owned JSON descriptor, trigger exactly one app-local or
package-private broadcast, validate the bounded JSON payload, list the owned
descriptor, remove it, and prove no later trigger delivery occurs.

Expected future markers:

- `packaged.broadcast_trigger.install=true`
- `packaged.broadcast_trigger.register=true`
- `packaged.broadcast_trigger.payload_valid=true`
- `packaged.broadcast_trigger.delivered=true`
- `packaged.broadcast_trigger.remove_cleanup=true`
- `packaged.broadcast_trigger.relaunch=true`
- `packaged.broadcast_trigger.crash_recovery=true`
- `packaged.broadcast_trigger.undeclared_capability_denied=true`

## Cleanup And Denial Policy

Remove/disable must affect only the owning package identity. It must remove or
disable the owned descriptor, unregister dynamic receivers, remove owned
manifest patches, cancel pending executions, prevent post-remove delivery,
preserve legacy Rhino rows, and deny cross-package remove attempts.

Future packaged metadata must opt in to every receiver and capability. Current
metadata still denies `tasks`, `tasks.intent`, `tasks.broadcast`, `tasks.boot`,
`tasks.package_events`, and `android.permission.RECEIVE_BOOT_COMPLETED`.
Exported receivers remain denied unless a later gate supplies explicit packaged
metadata, reviewed action allowlists, signature or app-private permission
policy, abuse review, and Android 12 / Android 15+ evidence.

## Open Archives

- `packaged_broadcast_trigger_positive_archive`
- `packaged_broadcast_remove_cleanup_archive`
- `packaged_broadcast_relaunch_crash_recovery_archive`
- `packaged_broadcast_undeclared_capability_denial_archive`
- `packaged_broadcast_exported_receiver_policy_archive`

## Gate

Host verifier:

```powershell
node tools/nodejs/phase14/verify-node-phase14-packaged-broadcast-trigger-remove-smoke.js --repo-root D:\idea-projects\AutoJs6-nodejs --report-dir D:\idea-projects\AutoJs6-nodejs\build\reports\nodejs
```

Gradle gate:

```powershell
.\gradlew.bat --console=plain :app:verifyNodePhase14PackagedBroadcastTriggerRemoveSmoke
```

Expected reports:

```text
build/reports/nodejs/phase14-packaged-broadcast-trigger-remove-smoke.json
build/reports/nodejs/phase14-packaged-broadcast-trigger-remove-smoke.md
```
