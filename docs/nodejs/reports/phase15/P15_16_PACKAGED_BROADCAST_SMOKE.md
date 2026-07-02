# P15-16 Packaged Broadcast Smoke

Status: partial

P15-16 deploys the Phase 15 packaged broadcast trigger/remove smoke evidence
gate. It imports the P14-21 packaged IntentTask/broadcast boundary smoke and
the P15-15 `intent_broadcast` provider promotion dependency gate, then records
the positive trigger/remove/relaunch/rollback evidence still required before
`tasks.broadcast` can be promoted.

This task remains unchecked because there is still no packaged positive
trigger archive, no remove cleanup archive, no relaunch/crash recovery archive,
no undeclared-capability or metadata mismatch denial archive, no exported
receiver policy archive, and no Safe downgrade or task/device/media docs sync
archive.

## Implemented Evidence

- Plan source:
  `docs/nodejs/phase15/packaged-broadcast-smoke-plan.json`.
- Host verifier:
  `tools/nodejs/phase15/verify-node-phase15-packaged-broadcast-smoke.js`.
- Current packaged boundary import:
  `packaged.intent_broadcast.ready=true`,
  `packaged.intent_broadcast.compat_rejected=true`,
  `packaged.intent_broadcast.future_modules_denied=true`,
  `packaged.intent_broadcast.raw_globals_absent=true`,
  `packaged.intent_broadcast.broadcast_channel_js_only=true`,
  `packaged.intent_broadcast.surface=true`, and
  `packaged.intent_broadcast.metadata_denied=true`.
- P15 dependency:
  `build/reports/nodejs/phase15-intent-broadcast-promotion.json` keeps
  `intent_broadcast` partial and unpromoted.
- Runtime boundary:
  `tasks.broadcast`, future task/broadcast capabilities, boot/package/custom
  receiver injection, external broadcast wakeups, raw Android broadcast
  objects, receiver handles, package lists, Android `Context`, and Java objects
  remain unpromoted.

## Remaining Blockers

| Blocker | Classification | Required evidence |
| --- | --- | --- |
| `packaged_broadcast_trigger_positive_archive` | `future_promotion` | Packaged APK positive trigger archive for app-local or package-private broadcast descriptor registration, payload delivery, and JSON-only payload validation. |
| `packaged_broadcast_remove_cleanup_archive` | `future_promotion` | Packaged remove cleanup archive proving owned descriptor cleanup, receiver unregister or manifest patch cleanup, pending execution cancellation, and no post-remove delivery. |
| `packaged_broadcast_relaunch_crash_recovery_archive` | `future_promotion` | Packaged relaunch and crash recovery archive proving no duplicate receivers, orphan pending executions, or lost visible descriptors. |
| `packaged_broadcast_undeclared_capability_denial_archive` | `future_promotion` | Packaged denial archive for missing tasks.broadcast capabilities, unsupported triggers, metadata mismatch, and exported receiver requests. |
| `packaged_broadcast_exported_receiver_policy_archive` | `future_promotion` | Packaged exported receiver policy archive with explicit metadata, allowlist, permission model, abuse review, and Android 12/15+ evidence before any exported receiver is promoted. |
| `packaged_broadcast_metadata_mismatch_archive` | `future_promotion` | Packaged metadata mismatch and package identity mismatch denial evidence proving receiver patches cannot be replayed or aliased across owners. |
| `packaged_broadcast_safe_downgrade_archive` | `future_promotion` | Provider-disable Safe downgrade proof that package-owned receiver patches, descriptors, and pending executions are removed or disabled while broadcast trigger authority is denied. |
| `phase15_task_device_media_doc_sync` | `future_promotion` | P15-22, P15-23, P15-25, and P15-26 sync before packaged broadcast trigger/remove smoke can be marked promoted. |

## Gate

Host verifier:

```powershell
node tools/nodejs/phase15/verify-node-phase15-packaged-broadcast-smoke.js --repo-root D:\idea-projects\AutoJs6-nodejs --report-dir D:\idea-projects\AutoJs6-nodejs\build\reports\nodejs
```

Gradle gate:

```powershell
.\gradlew.bat --console=plain :app:installAndRunNodeV13PackagedBroadcastSmoke
```
