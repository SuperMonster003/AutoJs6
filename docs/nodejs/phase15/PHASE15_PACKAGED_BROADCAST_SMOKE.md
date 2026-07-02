# Phase 15 Packaged Broadcast Smoke

Machine-readable source:

- `docs/nodejs/phase15/packaged-broadcast-smoke-plan.json`

P15-16 imports the P14-21 packaged broadcast trigger/remove smoke contract into
the v1.3 promotion lane. It keeps `intent_broadcast` at
`provider_poc_not_promoted` while positive packaged trigger/remove evidence,
relaunch/crash recovery, metadata mismatch denial, undeclared-capability
denial, exported receiver policy, rollback, Safe downgrade, security, type,
docs, and release evidence are still absent.

The gate does not expose `tasks.broadcast`, future task/broadcast capabilities,
boot/package/custom receiver injection, external broadcast wakeups, raw Android
broadcast objects, receiver handles, package lists, Android `Context`, or Java
objects.

## Ready Evidence

- `phase14_packaged_broadcast_trigger_remove_contract`
- `phase15_intent_broadcast_dependency_gate`
- `phase15_promotion_contracts`
- `phase15_disclosure_rollback_contract`
- `phase15_surface_manifest_mapping`

## Smoke Matrix

| Row | Status | Policy |
| --- | --- | --- |
| `current_packaged_boundary_denial_smoke` | `imported` | Current packaged APK denial smoke proves compat rejection, future module denial, raw global absence, JS-only BroadcastChannel, surface presence, and metadata denial. |
| `future_packaged_local_custom_trigger` | `future_promotion` | Positive app-local or package-private trigger delivery remains future work. |
| `future_packaged_remove_cleanup` | `future_promotion` | Owner-scoped remove cleanup and post-remove delivery denial remain future work. |
| `future_packaged_relaunch_crash_recovery` | `future_promotion` | Relaunch/crash recovery without duplicate receivers remains future work. |
| `future_undeclared_capability_and_exported_denial` | `future_promotion` | Undeclared capability and exported receiver denial archives remain future work. |
| `future_metadata_mismatch_safe_downgrade` | `future_promotion` | Metadata mismatch, rollback, and Safe downgrade archives remain future work. |

## Open Promotion Blockers

| Blocker | Classification | Required evidence |
| --- | --- | --- |
| `packaged_broadcast_trigger_positive_archive` | `future_promotion` | Packaged app-local or package-private positive trigger archive. |
| `packaged_broadcast_remove_cleanup_archive` | `future_promotion` | Packaged remove cleanup and no post-remove delivery archive. |
| `packaged_broadcast_relaunch_crash_recovery_archive` | `future_promotion` | Relaunch/crash recovery archive. |
| `packaged_broadcast_undeclared_capability_denial_archive` | `future_promotion` | Missing capability, unsupported trigger, metadata mismatch, and exported receiver denial archive. |
| `packaged_broadcast_exported_receiver_policy_archive` | `future_promotion` | Exported receiver metadata, allowlist, permission, abuse review, and device evidence archive. |
| `packaged_broadcast_metadata_mismatch_archive` | `future_promotion` | Metadata mismatch and owner identity denial archive. |
| `packaged_broadcast_safe_downgrade_archive` | `future_promotion` | Provider-disable Safe downgrade proof. |
| `phase15_task_device_media_doc_sync` | `future_promotion` | P15-22, P15-23, P15-25, and P15-26 sync before promotion. |

## Verification

- Host verifier: `tools/nodejs/phase15/verify-node-phase15-packaged-broadcast-smoke.js`
- Gradle gate: `:app:installAndRunNodeV13PackagedBroadcastSmoke`
- Reports:
  - `build/reports/nodejs/phase15-packaged-broadcast-smoke.json`
  - `build/reports/nodejs/phase15-packaged-broadcast-smoke.md`
