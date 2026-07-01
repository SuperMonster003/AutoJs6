# P14-20 Intent Broadcast Provider

Status: partial.

P14-20 deploys the JSON-safe IntentTask/broadcast provider contract gate for
Node Profile v1.2. It imports P13-22 IntentTask/broadcast substrate evidence,
the P13-23 bounded `app.intent()` / `app.startActivity()` boundary, and the
P14-17 through P14-19 task descriptor, rollback, and packaged ownership
contracts.

The runtime remains unpromoted. `require("tasks")`, `require("tasks.broadcast")`,
global `tasks`, future task/broadcast capabilities, raw Android broadcast
objects, provider-owned manifest mutation, and external broadcast wakeups are
still unavailable.

## Deployed

- Added `docs/nodejs/phase14/intent-broadcast-provider-plan.json`.
- Added `docs/nodejs/phase14/PHASE14_INTENT_BROADCAST_PROVIDER.md`.
- Added `tools/nodejs/phase14/verify-node-phase14-intent-broadcast-provider.js`.
- Added Gradle gate `:app:verifyNodePhase14IntentBroadcastProvider`.
- Recorded future descriptor operations: `register`, `list`, `get`, `enable`,
  `disable`, `remove`, and `validateManifestPatch`.
- Recorded manifest synthesis rules for packaged opt-in receivers,
  `RECEIVE_BOOT_COMPLETED`, package data scheme, non-exported defaults,
  exported receiver denial, and deterministic patch reports.
- Recorded trigger payload rules for bounded JSON snapshots, privacy filtering,
  receiver-thread non-execution, and no raw `Intent` / `Bundle` / `Uri`
  handles.
- Recorded remove cleanup rules for owned descriptors, dynamic receiver
  unregister, packaged patch cleanup, pending execution cancellation, legacy
  row preservation, and cross-package denial.

## Open Future-Promotion Archives

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
