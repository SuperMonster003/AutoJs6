# P14-21 Packaged Broadcast Trigger Remove Smoke

Status: partial.

P14-21 deploys the packaged broadcast trigger/remove smoke evidence contract.
It imports the P13-22 packaged IntentTask/broadcast boundary smoke and the
P14-20 JSON-safe `tasks.broadcast` provider contract, then reserves the future
positive install/trigger/remove/relaunch/crash/denial archive rows.

This does not promote positive broadcast-triggered execution. Packaged Node
still cannot declare `tasks.broadcast`, inject boot/package/custom receivers,
receive external broadcast wakeups, or expose raw Android broadcast objects.

## Deployed

- Added `docs/nodejs/phase14/packaged-broadcast-trigger-remove-smoke-plan.json`.
- Added `docs/nodejs/phase14/PHASE14_PACKAGED_BROADCAST_TRIGGER_REMOVE_SMOKE.md`.
- Added `tools/nodejs/phase14/verify-node-phase14-packaged-broadcast-trigger-remove-smoke.js`.
- Added Gradle gate `:app:verifyNodePhase14PackagedBroadcastTriggerRemoveSmoke`.
- Imported current packaged boundary markers:
  `packaged.intent_broadcast.ready=true`,
  `packaged.intent_broadcast.compat_rejected=true`,
  `packaged.intent_broadcast.future_modules_denied=true`,
  `packaged.intent_broadcast.raw_globals_absent=true`,
  `packaged.intent_broadcast.broadcast_channel_js_only=true`,
  `packaged.intent_broadcast.surface=true`, and
  `packaged.intent_broadcast.metadata_denied=true`.
- Reserved future positive markers for install, register, payload validation,
  delivery, remove cleanup, relaunch, crash recovery, and undeclared-capability
  denial.
- Recorded cleanup policy for owned descriptors, dynamic receiver unregister,
  owned manifest patch cleanup, pending execution cancellation, post-remove
  delivery denial, legacy Rhino row preservation, and cross-package denial.

## Open Future-Promotion Archives

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
