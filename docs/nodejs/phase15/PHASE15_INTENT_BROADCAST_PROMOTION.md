# Phase 15 Intent Broadcast Promotion

Machine-readable source:

- `docs/nodejs/phase15/intent-broadcast-promotion-plan.json`

P15-15 imports the P14-20 JSON-safe IntentTask/broadcast provider contract into
the v1.3 promotion lane. It keeps `intent_broadcast` at
`provider_poc_not_promoted` while live provider implementation, connected
device evidence, packaged trigger/remove smoke, manifest synthesis archives,
exported receiver policy, payload validation, owner cleanup, Safe downgrade,
security, type, docs, and release evidence are still absent.

The gate does not expose `require("tasks")`, `require("tasks.broadcast")`,
global `tasks`, future task/broadcast capabilities, provider-owned manifest
mutation, external broadcast wakeups, raw `Intent`, raw `PendingIntent`, raw
`BroadcastReceiver`, raw `Bundle`, raw `Uri`, receiver handles, package lists,
database cursors, mutable row handles, Android `Context`, or Java objects.

## Ready Evidence

- `phase14_intent_broadcast_contract`
- `phase15_packaged_task_smoke_dependency_gate`
- `phase15_promotion_contracts`
- `phase15_disclosure_rollback_contract`
- `phase15_surface_manifest_mapping`

## Provider Contract

| Area | Policy |
| --- | --- |
| `futureProvider` | `tasks.broadcast` remains future-only. |
| `descriptorSchema` | `autojs6-node-intent-broadcast-descriptor-v1` is reserved for provider-owned JSON descriptors. |
| `operations` | `register`, `list`, `get`, `enable`, `disable`, `remove`, and `validateManifestPatch` remain contract-only. |
| `manifestSynthesisContract` | Packaged receiver patches are opt-in, boot permission is explicit, and exported receivers are denied by default. |
| `triggerPayloadContract` | `autojs6-node-broadcast-trigger-payload-v1` snapshots are bounded JSON and privacy-filtered. |
| `removeBehavior` | Remove/disable cleanup is owner-scoped, cancels pending executions, and preserves legacy Rhino rows. |

## Open Promotion Blockers

| Blocker | Classification | Required evidence |
| --- | --- | --- |
| `intent_broadcast_live_provider_archive` | `future_promotion` | Live register/list/get/enable/disable/remove/validateManifestPatch evidence. |
| `intent_broadcast_manifest_synthesis_archive` | `future_promotion` | Packaged manifest synthesis and deterministic patch report archive. |
| `intent_broadcast_exported_permission_policy_archive` | `future_promotion` | Exported receiver policy, permission, abuse review, and device matrix archive. |
| `intent_broadcast_trigger_payload_schema_archive` | `future_promotion` | Bounded JSON trigger payload and privacy filtering archive. |
| `intent_broadcast_remove_cleanup_archive` | `future_promotion` | Owner cleanup, dynamic unregister, pending cancellation, and legacy preservation archive. |
| `intent_broadcast_packaged_trigger_remove_smoke_archive` | `future_promotion` | P15-16 packaged trigger/remove/relaunch/rollback archive. |
| `intent_broadcast_cross_package_denial_archive` | `future_promotion` | Cross-package descriptor mutation and receiver cleanup denial archive. |
| `intent_broadcast_safe_downgrade_archive` | `future_promotion` | Provider-disable Safe downgrade proof. |
| `phase15_task_device_media_doc_sync` | `future_promotion` | P15-22, P15-23, P15-25, and P15-26 sync before promotion. |

## Verification

- Host verifier: `tools/nodejs/phase15/verify-node-phase15-intent-broadcast-promotion.js`
- Gradle gate: `:app:verifyNodePhase15IntentBroadcastPromotion`
- Reports:
  - `build/reports/nodejs/phase15-intent-broadcast-promotion.json`
  - `build/reports/nodejs/phase15-intent-broadcast-promotion.md`
