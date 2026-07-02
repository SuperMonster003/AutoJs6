# Phase 15 Packaged Live UI Promotion

Machine-readable source:

- `docs/nodejs/phase15/packaged-live-ui-promotion-plan.json`

P15-04 starts from the P14-07 packaged live UI provider POC. Current evidence
covers packaged missing-capability denial, packaged entry/update/close, raw
Android UI global absence, pending bridge drain, and Android 12 live Activity
Back/Recents lifecycle events.

The provider is not promoted. The plan keeps `live_ui` at
`provider_poc_not_promoted` until packaged relaunch, task removal cleanup,
provider crash recovery, reviewed user-visible disclosure, Android 15+ packaged
evidence, and downstream security/type/docs sync are archived.

## Ready Evidence

- `phase13_packaged_capability_boundary`
- `phase13_packaged_entry_update_close`
- `phase13_android12_live_activity_lifecycle`
- `phase14_provider_poc_contract`
- `phase15_disclosure_rollback_contract`
- `general_packaged_crash_lifecycle_anchor`

## Open Promotion Blockers

| Blocker | Classification | Required evidence |
| --- | --- | --- |
| `packaged_live_ui_task_relaunch_archive` | `future_promotion` | Packaged task relaunch with live UI handle cleanup or intentional restore. |
| `packaged_live_ui_task_removal_cleanup_archive` | `future_promotion` | Task removal releases owned UI resources and does not inherit stale handles. |
| `packaged_live_ui_crash_recovery_archive` | `future_promotion` | Provider crash recovery and crash circuit breaker with no leaked handles. |
| `packaged_live_ui_user_visible_disclosure_archive` | `future_promotion` | Reviewed launch, stop, relaunch, crash recovery, privacy, and cleanup copy. |
| `android_15_plus_packaged_live_ui_archive` | `future_promotion` | Android 15+ packaged archive for entry/update/close, relaunch, task removal, crash recovery, disclosure, and Safe downgrade. |
| `phase15_security_type_doc_sync` | `future_promotion` | P15-11, P15-23, P15-25, and P15-26 must synchronize docs, types, security, migration, compatibility, and release notes. |

## Rollback

- Disable flag: `node.provider.live_ui.enabled`
- Diagnostic code: `NODE_PROVIDER_LIVE_UI_DISABLED`
- Safe fallback: stable denial and no live UI session dispatch.
- Crash circuit breaker: `3 provider crashes within 24h per package/profile/capability`

## Verification

- Host verifier: `tools/nodejs/phase15/verify-node-phase15-packaged-live-ui-promotion.js`
- Gradle gate: `:app:verifyNodePhase15PackagedLiveUiPromotion`
- Reports:
  - `build/reports/nodejs/phase15-packaged-live-ui-promotion.json`
  - `build/reports/nodejs/phase15-packaged-live-ui-promotion.md`
