# P15-15 Intent Broadcast Promotion

Status: partial

P15-15 deploys the Phase 15 JSON-safe IntentTask/broadcast provider promotion
gate. It imports the P14-20 provider descriptor, manifest synthesis, exported
receiver, trigger payload, and owner cleanup contracts, then binds them to the
P15-14 packaged task ownership dependency gate and the Phase 15
promotion/disclosure/rollback bars for `intent_broadcast`.

This task remains unchecked because there is still no live `tasks.broadcast`
provider, no connected-device register/list/get/enable/disable/remove archive,
no packaged trigger/remove archive, no exported receiver permission matrix, no
payload validation archive, no owner cleanup archive, no cross-package denial
archive, and no Safe downgrade or task/device/media docs sync archive.

## Implemented Evidence

- Plan source:
  `docs/nodejs/phase15/intent-broadcast-promotion-plan.json`.
- Host verifier:
  `tools/nodejs/phase15/verify-node-phase15-intent-broadcast-promotion.js`.
- P14-20 import:
  JSON-safe descriptor schema, manifest synthesis rules, exported receiver
  denial, bounded trigger payload snapshots, owner-scoped remove cleanup, and
  current runtime denial for `tasks.broadcast`.
- P15 dependency:
  `build/reports/nodejs/phase15-packaged-task-smoke.json` keeps package-owned
  task scheduling partial and unpromoted before broadcast rows can be promoted.
- Runtime boundary:
  `require("tasks")`, `require("tasks.broadcast")`, global `tasks`,
  future task/broadcast capabilities, raw Android broadcast objects, external
  broadcast wakeups, receiver handles, package lists, database cursors, mutable
  row handles, Android `Context`, and Java objects remain unpromoted.

## Remaining Blockers

| Blocker | Classification | Required evidence |
| --- | --- | --- |
| `intent_broadcast_live_provider_archive` | `future_promotion` | Live JSON-safe provider evidence for register, list, get, enable, disable, remove, and validateManifestPatch without raw Android broadcast objects. |
| `intent_broadcast_manifest_synthesis_archive` | `future_promotion` | Manifest synthesis evidence for packaged opt-in receivers, boot permission, package data scheme, non-exported defaults, and deterministic patch reports. |
| `intent_broadcast_exported_permission_policy_archive` | `future_promotion` | Exported receiver denial/default evidence plus allowlist, permission, abuse-review, and Android 12/15+ matrix proof for any exported receiver promotion. |
| `intent_broadcast_trigger_payload_schema_archive` | `future_promotion` | Trigger payload serialization evidence proving bounded JSON snapshots, privacy filtering, no raw Intent/Bundle/Uri handles, and receiver-thread non-execution. |
| `intent_broadcast_remove_cleanup_archive` | `future_promotion` | Remove/disable evidence proving owned descriptor cleanup, dynamic receiver unregister, pending execution cancellation, packaged patch cleanup, and legacy Rhino row preservation. |
| `intent_broadcast_packaged_trigger_remove_smoke_archive` | `future_promotion` | P15-16 packaged install, trigger, remove, relaunch, metadata mismatch, undeclared capability, and rollback archive. |
| `intent_broadcast_cross_package_denial_archive` | `future_promotion` | Cross-package descriptor mutation and receiver cleanup denial evidence for main-app, packaged, and legacy Rhino rows. |
| `intent_broadcast_safe_downgrade_archive` | `future_promotion` | Provider-disable Safe downgrade proof that package-owned receivers are removed or disabled and broadcast trigger authority is denied. |
| `phase15_task_device_media_doc_sync` | `future_promotion` | P15-22, P15-23, P15-25, and P15-26 sync before IntentTask/broadcast promotion can be marked promoted. |

## Gate

Host verifier:

```powershell
node tools/nodejs/phase15/verify-node-phase15-intent-broadcast-promotion.js --repo-root D:\idea-projects\AutoJs6-nodejs --report-dir D:\idea-projects\AutoJs6-nodejs\build\reports\nodejs
```

Gradle gate:

```powershell
.\gradlew.bat --console=plain :app:verifyNodePhase15IntentBroadcastPromotion
```
