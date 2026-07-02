# Phase 15 Node v1.3 Provider Promotion Tasks

Status: initial executable plan for `feature/nodejs-runtime-v1.3`.

Phase 15 starts from the Phase 14 v1.2 closeout handoff. Phase 14 closed the
v1.2 release-gating handoff with zero open final blockers, zero policy
violations, and zero release-blocking reports. The remaining provider POC work
is imported here as v1.3 future promotion backlog, not as a v1.2 regression.

Primary input:

- [`PHASE14_CLOSEOUT_BREAKDOWN.md`](../phase14/PHASE14_CLOSEOUT_BREAKDOWN.md)
- [`PHASE14_SUMMARY.md`](../phase14/PHASE14_SUMMARY.md)
- [`PHASE14_FUTURE_BACKLOG.json`](../phase14/PHASE14_FUTURE_BACKLOG.json)
- [`PHASE14_NEXT_BRANCH_HANDOFF.md`](../phase14/PHASE14_NEXT_BRANCH_HANDOFF.md)

## v1.3 Goal

Node Profile v1.3 should convert selected v1.2 provider POC reports into
real opt-in Pro/Desktop provider promotion only when implementation, connected
device evidence, packaged APK behavior, user-visible disclosure, rollback,
security regression coverage, TypeScript declarations, migration docs, and
release notes all agree.

The Safe profile remains conservative. Provider promotion must stay opt-in,
diagnosable, reversible, and scoped to JSON-safe or opaque-handle boundaries.

## Imported v1.3 Backlog

| Phase 14 task | Phase 15 lane | v1.3 direction |
| --- | --- | --- |
| `P14-07` | Pro UI | Close packaged live UI relaunch, crash recovery, and disclosure evidence. |
| `P14-08` | Pro UI | Promote Android overlay provider permission-granted path. |
| `P14-09` | Pro UI | Close overlay permission and OEM matrix archives. |
| `P14-10` | Pro UI | Add packaged overlay positive smoke and stress evidence. |
| `P14-11` | Pro automation | Close packaged accessibility lifecycle evidence. |
| `P14-12` | Pro automation | Close selector/action real-device matrix. |
| `P14-13` | Pro media | Promote Activity-owned MediaProjection provider. |
| `P14-14` | Pro media | Promote advanced image operation provider. |
| `P14-15` | Pro media | Close packaged image/OCR quota and model evidence. |
| `P14-17` | Pro tasks | Promote JSON-safe task database mutations. |
| `P14-18` | Pro tasks | Close task schema migration and rollback evidence. |
| `P14-19` | Pro tasks | Add packaged task scheduling smoke. |
| `P14-20` | Pro tasks | Promote JSON-safe IntentTask/broadcast provider. |
| `P14-21` | Pro tasks | Add packaged broadcast trigger/remove smoke. |
| `P14-22` | Pro device | Promote notification ownership and foreground disclosure behavior. |
| `P14-23` | Pro device | Close settings/power OEM and background-launch matrix. |
| `P14-24` | Pro media | Promote real recorder session provider. |
| `P14-25` | Pro media | Add packaged recorder/media stress evidence. |
| `P14-26` | Pro media | Decide scoped MediaStore and playback promotion boundary. |

## Non-Goals Unless Reopened Explicitly

- Raw Android object parity.
- Raw `Context`, `Activity`, `View`, `Bitmap`, `Intent`, `MediaProjection`,
  `MediaRecorder`, `TimedTask`, `BroadcastReceiver`, `Notification`,
  `WakeLock`, `Uri`, `PendingIntent`, `ContentResolver`, file descriptor, or
  Java object handles.
- Native addon `.node` loading, unrestricted child process, raw network
  modules, production inspector, and unrestricted package installation.
- Release tag approval. Phase 15 gates are evidence and planning gates until a
  dedicated RC/tag gate is run for the exact commit.

## Closeout Rule

A Phase 15 task can be marked complete only when all applicable items are true:

- A focused host verifier exists and reports `ready` or `passed`.
- Connected-device evidence is archived for promoted behavior.
- Packaged APK evidence exists for packaged behavior, or the task explicitly
  records why the capability stays main-app-only or denied.
- Disclosure, foreground service ownership, stop action, privacy, rollback,
  crash recovery, and Safe downgrade behavior are covered for high-privilege
  surfaces.
- Stable denial branches stay tested for every authority that remains
  unsupported.
- Docs, examples, TypeScript declarations, security corpus, CI docs, migration
  docs, and release notes are synchronized.

## Task Checklist

### P0 Baseline And Promotion Contract Refresh

- [x] P15-00: Record Phase 15 baseline from the Phase 14 closeout handoff.
  - Inputs: Phase 14 summary JSON, closeout handoff report, future backlog
    export, final blocker registry, current branch name.
  - Output: `build/reports/nodejs/phase15-baseline-verification.json`.
  - Gate: `:app:verifyNodePhase15Baseline`.
  - Acceptance: confirms Phase 14 closeout handoff remains `ready`, imports
    the 19 v1.3 future backlog tasks, and fails if final blockers reopen.
  - Landed:
    - `docs/nodejs/phase15/node-v1-3-baseline.json`
    - `docs/nodejs/phase15/PHASE15_NODE_V1_3_BASELINE.md`
    - `tools/nodejs/phase15/verify-node-phase15-baseline.js`
    - `app/node-phase15-gate.gradle.kts`
    - `build/reports/nodejs/phase15-baseline-verification.json`
    - `build/reports/nodejs/phase15-baseline-verification.md`

- [x] P15-01: Create Phase 15 surface manifest.
  - Scope: map every P15 task to owner surface, source backlog task, profile,
    risk, evidence class, and rollback hook.
  - Gate: `:app:verifyNodePhase15SurfaceManifest`.
  - Acceptance: P15-00 through P15-27 map exactly once and every imported
    P14 backlog task is owned.
  - Landed:
    - `docs/nodejs/phase15/phase15-surface-manifest.json`
    - `docs/nodejs/phase15/PHASE15_SURFACE_MANIFEST.md`
    - `docs/nodejs/schemas/phase15_surface_manifest.schema.json`
    - `tools/nodejs/phase15/verify-node-phase15-surface-manifest.js`
    - `build/reports/nodejs/phase15-surface-manifest-verification.json`
    - `build/reports/nodejs/phase15-surface-manifest-verification.md`

- [x] P15-02: Refresh v1.3 opt-in promotion bars.
  - Scope: promoted, provider POC, future-only, stable-denied, and rejected
    vocabulary for v1.3.
  - Gate: `:app:verifyNodePhase15PromotionContracts`.
  - Acceptance: no provider can become opt-in promoted without device,
    packaged, disclosure, rollback, security, docs, and type evidence.
  - Landed:
    - `docs/nodejs/phase15/node-v1-3-promotion-contracts.json`
    - `docs/nodejs/phase15/PHASE15_NODE_V1_3_PROMOTION_CONTRACTS.md`
    - `tools/nodejs/phase15/verify-node-phase15-promotion-contracts.js`
    - `build/reports/nodejs/phase15-promotion-contracts.json`
    - `build/reports/nodejs/phase15-promotion-contracts.md`

- [x] P15-03: Add v1.3 disclosure and rollback audit gate.
  - Scope: high-privilege prompts, foreground notifications, stop actions,
    Safe downgrade, provider disable flags, and crash circuit breaker evidence.
  - Gate: `:app:verifyNodePhase15DisclosureRollbackAudit`.
  - Acceptance: every promotable provider declares a rollback and disclosure
    proof path before runtime authority changes.
  - Landed:
    - `docs/nodejs/phase15/node-v1-3-disclosure-rollback-audit.json`
    - `docs/nodejs/phase15/PHASE15_NODE_V1_3_DISCLOSURE_ROLLBACK_AUDIT.md`
    - `tools/nodejs/phase15/verify-node-phase15-disclosure-rollback-audit.js`
    - `build/reports/nodejs/phase15-disclosure-rollback-audit.json`
    - `build/reports/nodejs/phase15-disclosure-rollback-audit.md`

### P1 Pro UI, Overlay, Automation, Screenshot, OCR

- [ ] P15-04: Close packaged live UI relaunch and crash recovery.
  - Source backlog: `P14-07`.
  - Gate: `:app:verifyNodePhase15PackagedLiveUiPromotion`.
  - Acceptance: packaged live UI relaunch, task removal, crash recovery,
    pending-dispatch drain, disclosure, and cleanup pass on required devices.
  - Partial gate deployed:
    - `docs/nodejs/phase15/packaged-live-ui-promotion-plan.json`
    - `docs/nodejs/phase15/PHASE15_PACKAGED_LIVE_UI_PROMOTION.md`
    - `docs/nodejs/reports/phase15/P15_04_PACKAGED_LIVE_UI_PROMOTION.md`
    - `tools/nodejs/phase15/verify-node-phase15-packaged-live-ui-promotion.js`
    - `build/reports/nodejs/phase15-packaged-live-ui-promotion.json`
    - `build/reports/nodejs/phase15-packaged-live-ui-promotion.md`
  - Still open: packaged live UI task relaunch archive, task-removal cleanup
    archive, crash recovery archive, user-visible disclosure archive, Android
    15+ packaged archive, and downstream security/type/docs sync.

- [ ] P15-05: Promote Android overlay provider permission-granted path.
  - Source backlog: `P14-08`.
  - Gate: `:app:verifyNodePhase15OverlayProviderPromotion`.
  - Acceptance: overlay sessions use scoped handles, permission checks,
    foreground disclosure, owner cleanup, and raw object denial.
  - Partial gate deployed:
    - `docs/nodejs/phase15/overlay-provider-promotion-plan.json`
    - `docs/nodejs/phase15/PHASE15_OVERLAY_PROVIDER_PROMOTION.md`
    - `docs/nodejs/reports/phase15/P15_05_OVERLAY_PROVIDER_PROMOTION.md`
    - `tools/nodejs/phase15/verify-node-phase15-overlay-provider-promotion.js`
    - `build/reports/nodejs/phase15-overlay-provider-promotion.json`
    - `build/reports/nodejs/phase15-overlay-provider-promotion.md`
  - Still open: `overlay_permission_granted_android12_archive`,
    `overlay_permission_granted_android15_archive`,
    `overlay_foreground_disclosure_stop_action_archive`,
    `overlay_owner_cleanup_leak_stress_archive`,
    `packaged_overlay_positive_smoke_archive`, `overlay_oem_matrix_archive`,
    and `phase15_security_type_doc_sync`.

- [ ] P15-06: Close overlay permission and OEM matrix.
  - Source backlog: `P14-09`.
  - Gate: `:app:verifyNodePhase15OverlayOemMatrix`.
  - Acceptance: Android 12 and Android 15+ class devices record grant, denial,
    settings round-trip, task removal, relaunch, and OEM notes.
  - Partial gate deployed:
    - `docs/nodejs/phase15/overlay-oem-matrix-plan.json`
    - `docs/nodejs/phase15/PHASE15_OVERLAY_OEM_MATRIX.md`
    - `docs/nodejs/reports/phase15/P15_06_OVERLAY_OEM_MATRIX.md`
    - `tools/nodejs/phase15/verify-node-phase15-overlay-oem-matrix.js`
    - `build/reports/nodejs/phase15-overlay-oem-matrix.json`
    - `build/reports/nodejs/phase15-overlay-oem-matrix.md`
  - Still open: `overlay_android12_granted_device_archive`,
    `overlay_android15_plus_granted_device_archive`,
    `overlay_settings_round_trip_oem_notes`,
    `overlay_task_removal_relaunch_evidence`,
    `overlay_foreground_disclosure_stop_action_archive`, and
    `phase15_security_type_doc_sync`.

- [ ] P15-07: Add packaged overlay positive smoke and stress.
  - Source backlog: `P14-10`.
  - Gate: `:app:installAndRunNodeV13PackagedOverlaySmoke`.
  - Acceptance: packaged overlay create/update/close, denial, Safe downgrade,
    crash cleanup, and resource stress are archived.
  - Partial gate deployed:
    - `docs/nodejs/phase15/packaged-overlay-smoke-plan.json`
    - `docs/nodejs/phase15/PHASE15_PACKAGED_OVERLAY_SMOKE.md`
    - `docs/nodejs/reports/phase15/P15_07_PACKAGED_OVERLAY_SMOKE.md`
    - `tools/nodejs/phase15/verify-node-phase15-packaged-overlay-smoke.js`
    - `build/reports/nodejs/phase15-packaged-overlay-smoke.json`
    - `build/reports/nodejs/phase15-packaged-overlay-smoke.md`
  - Still open: `packaged_overlay_positive_smoke_archive`,
    `packaged_overlay_positive_device_archive`,
    `packaged_overlay_foreground_disclosure_stop_action`,
    `packaged_overlay_task_removal_relaunch_crash`,
    `packaged_overlay_event_drain_leak_stress`, and
    `phase15_security_type_doc_sync`.

- [ ] P15-08: Close accessibility lifecycle and selector/action matrix.
  - Source backlog: `P14-11`, `P14-12`.
  - Gate: `:app:verifyNodePhase15AccessibilityPromotion`.
  - Acceptance: service lifecycle, selector/action matrix, packaged behavior,
    permission denial, and raw accessibility object denial are covered.
  - Partial gate deployed:
    - `docs/nodejs/phase15/accessibility-promotion-plan.json`
    - `docs/nodejs/phase15/PHASE15_ACCESSIBILITY_PROMOTION.md`
    - `docs/nodejs/reports/phase15/P15_08_ACCESSIBILITY_PROMOTION.md`
    - `tools/nodejs/phase15/verify-node-phase15-accessibility-promotion.js`
    - `build/reports/nodejs/phase15-accessibility-promotion.json`
    - `build/reports/nodejs/phase15-accessibility-promotion.md`
  - Still open: `accessibility_packaged_service_enabled_archive`,
    `accessibility_settings_round_trip_archive`,
    `accessibility_task_removal_relaunch_archive`,
    `accessibility_disconnect_missing_capability_denial_archive`,
    `accessibility_selector_android12_action_archive`,
    `accessibility_selector_android15_plus_action_archive`,
    `accessibility_selector_global_actions_recents_archive`,
    `accessibility_selector_unsupported_tier_diagnostics_archive`, and
    `phase15_security_type_doc_sync`.

- [ ] P15-09: Promote Activity-owned MediaProjection provider.
  - Source backlog: `P14-13`.
  - Gate: `:app:verifyNodePhase15MediaProjectionPromotion`.
  - Acceptance: prompt, foreground disclosure, capture lifecycle, task removal,
    crash cleanup, Safe downgrade, and raw object denial are archived.
  - Partial gate deployed:
    - `docs/nodejs/phase15/media-projection-promotion-plan.json`
    - `docs/nodejs/phase15/PHASE15_MEDIA_PROJECTION_PROMOTION.md`
    - `docs/nodejs/reports/phase15/P15_09_MEDIA_PROJECTION_PROMOTION.md`
    - `tools/nodejs/phase15/verify-node-phase15-media-projection-promotion.js`
    - `build/reports/nodejs/phase15-media-projection-promotion.json`
    - `build/reports/nodejs/phase15-media-projection-promotion.md`
  - Still open: `media_projection_android12_prompt_capture_archive`,
    `media_projection_android15_plus_prompt_capture_archive`,
    `media_projection_foreground_disclosure_stop_action_archive`,
    `media_projection_task_removal_relaunch_crash_archive`,
    `media_projection_packaged_apk_evidence_archive`,
    `media_projection_safe_downgrade_archive`, and
    `phase15_security_type_doc_sync`.

- [ ] P15-10: Promote advanced image and OCR packaged evidence.
  - Source backlog: `P14-14`, `P14-15`.
  - Gate: `:app:verifyNodePhase15ImageOcrPromotion`.
  - Acceptance: image handles, quotas, OCR model selection, packaged archives,
    cleanup, and raw bitmap denial are verified.
  - Partial gate deployed:
    - `docs/nodejs/phase15/image-ocr-promotion-plan.json`
    - `docs/nodejs/phase15/PHASE15_IMAGE_OCR_PROMOTION.md`
    - `docs/nodejs/reports/phase15/P15_10_IMAGE_OCR_PROMOTION.md`
    - `tools/nodejs/phase15/verify-node-phase15-image-ocr-promotion.js`
    - `build/reports/nodejs/phase15-image-ocr-promotion.json`
    - `build/reports/nodejs/phase15-image-ocr-promotion.md`
  - Still open: `image_advanced_transform_provider_archive`,
    `image_matching_color_provider_archive`,
    `image_temp_handle_quota_leak_stress_archive`,
    `image_packaged_scoped_output_archive`,
    `packaged_ocr_model_absence_archive`,
    `packaged_ocr_explicit_provider_mode_archive`,
    `packaged_image_ocr_quota_leak_stress_archive`,
    `packaged_capture_ocr_disclosure_archive`,
    `image_ocr_safe_downgrade_archive`, and
    `phase15_security_type_doc_sync`.

- [x] P15-11: Sync UI/automation/media docs, examples, declarations, and release notes.
  - Gate: `:app:verifyNodePhase15UiAutomationMediaDocSync`.
  - Acceptance: promoted and future-only state matches executable evidence.
  - UI/automation/media docs sync gate deployed:
    - `docs/nodejs/phase15/ui-automation-media-doc-sync.json`
    - `docs/nodejs/phase15/PHASE15_UI_AUTOMATION_MEDIA_DOC_SYNC.md`
    - `docs/nodejs/releases/NODE_PROFILE_V1_3_UI_AUTOMATION_MEDIA_SYNC.md`
    - `docs/nodejs/reports/phase15/P15_11_UI_AUTOMATION_MEDIA_DOC_SYNC.md`
    - `tools/nodejs/phase15/verify-node-phase15-ui-automation-media-doc-sync.js`
    - `build/reports/nodejs/phase15-ui-automation-media-doc-sync.json`
    - `build/reports/nodejs/phase15-ui-automation-media-doc-sync.md`
  - Decision: P15-04 through P15-10 remain partial and unpromoted; P15-11
    records `provider_poc_not_promoted` docs, examples, declaration, security,
    error, CI, and release-facing state without changing runtime authority.

### P2 Pro Task, Device, And Media Providers

- [ ] P15-12: Promote JSON-safe task database mutations.
  - Source backlog: `P14-17`.
  - Gate: `:app:verifyNodePhase15TaskDatabasePromotion`.
  - Acceptance: add/update/enable-disable/remove/list ownership, migration,
    rollback, and cross-package denial are covered.
  - Partial gate deployed:
    - `docs/nodejs/phase15/task-database-promotion-plan.json`
    - `docs/nodejs/phase15/PHASE15_TASK_DATABASE_PROMOTION.md`
    - `docs/nodejs/reports/phase15/P15_12_TASK_DATABASE_PROMOTION.md`
    - `tools/nodejs/phase15/verify-node-phase15-task-database-promotion.js`
    - `build/reports/nodejs/phase15-task-database-promotion.json`
    - `build/reports/nodejs/phase15-task-database-promotion.md`
  - Still open: `task_database_live_mutation_provider_archive`,
    `task_database_ownership_id_mapping_archive`,
    `task_database_schema_migration_rollback_archive`,
    `task_database_wall_clock_scheduler_archive`,
    `packaged_task_database_mutation_smoke_archive`,
    `task_database_cross_package_denial_archive`,
    `task_database_disclosure_stop_action_archive`,
    `task_database_safe_downgrade_archive`, and
    `phase15_task_device_media_doc_sync`.

- [ ] P15-13: Close task schema migration and rollback evidence.
  - Source backlog: `P14-18`.
  - Gate: `:app:verifyNodePhase15TaskSchemaRollback`.
  - Acceptance: legacy rows, packaged rows, downgrade cleanup, backup, and
    rollback fixtures pass.
  - Partial gate deployed:
    - `docs/nodejs/phase15/task-schema-rollback-plan.json`
    - `docs/nodejs/phase15/PHASE15_TASK_SCHEMA_ROLLBACK.md`
    - `docs/nodejs/reports/phase15/P15_13_TASK_SCHEMA_ROLLBACK.md`
    - `tools/nodejs/phase15/verify-node-phase15-task-schema-rollback.js`
    - `build/reports/nodejs/phase15-task-schema-rollback.json`
    - `build/reports/nodejs/phase15-task-schema-rollback.md`
  - Still open: `task_schema_live_migration_tests_archive`,
    `task_schema_corruption_recovery_archive`,
    `task_schema_downgrade_legacy_visibility_archive`,
    `task_schema_packaged_rollback_archive`,
    `task_schema_backup_restore_archive`,
    `task_schema_safe_downgrade_archive`,
    `task_schema_cross_package_rollback_denial_archive`, and
    `phase15_task_device_media_doc_sync`.

- [ ] P15-14: Add packaged task scheduling smoke.
  - Source backlog: `P14-19`.
  - Gate: `:app:installAndRunNodeV13PackagedTaskSmoke`.
  - Acceptance: schedule/list/cancel, relaunch, task removal, denial, and Safe
    downgrade are archived.
  - Partial gate deployed:
    - `docs/nodejs/phase15/packaged-task-smoke-plan.json`
    - `docs/nodejs/phase15/PHASE15_PACKAGED_TASK_SMOKE.md`
    - `docs/nodejs/reports/phase15/P15_14_PACKAGED_TASK_SMOKE.md`
    - `tools/nodejs/phase15/verify-node-phase15-packaged-task-smoke.js`
    - `build/reports/nodejs/phase15-packaged-task-smoke.json`
    - `build/reports/nodejs/phase15-packaged-task-smoke.md`
  - Still open: `packaged_task_database_positive_smoke_archive`,
    `packaged_task_process_death_relaunch_archive`,
    `packaged_task_removal_cleanup_archive`,
    `packaged_task_disclosure_safe_downgrade_archive`,
    `packaged_task_cross_package_denial_archive`,
    `packaged_task_missing_capability_denial_archive`,
    `packaged_task_safe_downgrade_archive`, and
    `phase15_task_device_media_doc_sync`.

- [ ] P15-15: Promote JSON-safe IntentTask and broadcast provider.
  - Source backlog: `P14-20`.
  - Gate: `:app:verifyNodePhase15IntentBroadcastPromotion`.
  - Acceptance: manifest synthesis, exported policy, payload validation,
    owner cleanup, and raw receiver/intent denial are verified.
  - Partial gate deployed:
    - `docs/nodejs/phase15/intent-broadcast-promotion-plan.json`
    - `docs/nodejs/phase15/PHASE15_INTENT_BROADCAST_PROMOTION.md`
    - `docs/nodejs/reports/phase15/P15_15_INTENT_BROADCAST_PROMOTION.md`
    - `tools/nodejs/phase15/verify-node-phase15-intent-broadcast-promotion.js`
    - `build/reports/nodejs/phase15-intent-broadcast-promotion.json`
    - `build/reports/nodejs/phase15-intent-broadcast-promotion.md`
  - Still open: `intent_broadcast_live_provider_archive`,
    `intent_broadcast_manifest_synthesis_archive`,
    `intent_broadcast_exported_permission_policy_archive`,
    `intent_broadcast_trigger_payload_schema_archive`,
    `intent_broadcast_remove_cleanup_archive`,
    `intent_broadcast_packaged_trigger_remove_smoke_archive`,
    `intent_broadcast_cross_package_denial_archive`,
    `intent_broadcast_safe_downgrade_archive`, and
    `phase15_task_device_media_doc_sync`.

- [ ] P15-16: Add packaged broadcast trigger/remove smoke.
  - Source backlog: `P14-21`.
  - Gate: `:app:installAndRunNodeV13PackagedBroadcastSmoke`.
  - Acceptance: trigger, remove, relaunch, denial, metadata mismatch, and
    rollback are archived.
  - Partial gate deployed:
    - `docs/nodejs/phase15/packaged-broadcast-smoke-plan.json`
    - `docs/nodejs/phase15/PHASE15_PACKAGED_BROADCAST_SMOKE.md`
    - `docs/nodejs/reports/phase15/P15_16_PACKAGED_BROADCAST_SMOKE.md`
    - `tools/nodejs/phase15/verify-node-phase15-packaged-broadcast-smoke.js`
    - `build/reports/nodejs/phase15-packaged-broadcast-smoke.json`
    - `build/reports/nodejs/phase15-packaged-broadcast-smoke.md`
  - Still open: `packaged_broadcast_trigger_positive_archive`,
    `packaged_broadcast_remove_cleanup_archive`,
    `packaged_broadcast_relaunch_crash_recovery_archive`,
    `packaged_broadcast_undeclared_capability_denial_archive`,
    `packaged_broadcast_exported_receiver_policy_archive`,
    `packaged_broadcast_metadata_mismatch_archive`,
    `packaged_broadcast_safe_downgrade_archive`, and
    `phase15_task_device_media_doc_sync`.

- [ ] P15-17: Promote notification ownership and foreground disclosure behavior.
  - Source backlog: `P14-22`.
  - Gate: `:app:verifyNodePhase15NotificationPromotion`.
  - Acceptance: packaged post/cancel, foreground disclosure, stop action,
    Android permission matrix, and raw notification object denial are covered.
  - Partial gate deployed:
    - `docs/nodejs/phase15/notification-promotion-plan.json`
    - `docs/nodejs/phase15/PHASE15_NOTIFICATION_PROMOTION.md`
    - `docs/nodejs/reports/phase15/P15_17_NOTIFICATION_PROMOTION.md`
    - `tools/nodejs/phase15/verify-node-phase15-notification-promotion.js`
    - `build/reports/nodejs/phase15-notification-promotion.json`
    - `build/reports/nodejs/phase15-notification-promotion.md`
  - Still open: `notification_packaged_post_cancel_archive`,
    `notification_foreground_disclosure_stop_action_archive`,
    `notification_permission_denial_device_matrix_archive`,
    `notification_advanced_action_group_style_decision_archive`,
    `notification_rollback_cleanup_archive`,
    `notification_safe_downgrade_archive`,
    `notification_raw_object_denial_security_archive`, and
    `phase15_task_device_media_doc_sync`.

- [ ] P15-18: Close settings/power OEM and background-launch matrix.
  - Source backlog: `P14-23`.
  - Gate: `:app:verifyNodePhase15SettingsPowerPromotion`.
  - Acceptance: prompt copy, dry-run/mutation boundary, OEM notes, exact-alarm
    state, denial, and rollback evidence are archived.
  - Partial gate deployed:
    - `docs/nodejs/phase15/settings-power-promotion-plan.json`
    - `docs/nodejs/phase15/PHASE15_SETTINGS_POWER_PROMOTION.md`
    - `docs/nodejs/reports/phase15/P15_18_SETTINGS_POWER_PROMOTION.md`
    - `tools/nodejs/phase15/verify-node-phase15-settings-power-promotion.js`
    - `build/reports/nodejs/phase15-settings-power-promotion.json`
    - `build/reports/nodejs/phase15-settings-power-promotion.md`
  - Still open: `settings_power_android12_oem_matrix_archive`,
    `settings_power_android15_plus_oem_matrix_archive`,
    `settings_power_exact_alarm_state_archive`,
    `settings_power_background_launch_restriction_archive`,
    `settings_power_mutation_prompt_rollback_archive`,
    `settings_power_packaged_disclosure_stop_action_archive`,
    `settings_power_safe_downgrade_archive`,
    `settings_power_raw_object_denial_security_archive`, and
    `phase15_task_device_media_doc_sync`.

- [ ] P15-19: Promote real recorder session provider.
  - Source backlog: `P14-24`.
  - Gate: `:app:verifyNodePhase15RecorderPromotion`.
  - Acceptance: microphone prompt, start/stop, foreground disclosure, temp
    output cleanup, crash cleanup, and raw media object denial pass.
  - Partial gate deployed:
    - `docs/nodejs/phase15/recorder-promotion-plan.json`
    - `docs/nodejs/phase15/PHASE15_RECORDER_PROMOTION.md`
    - `docs/nodejs/reports/phase15/P15_19_RECORDER_PROMOTION.md`
    - `tools/nodejs/phase15/verify-node-phase15-recorder-promotion.js`
    - `build/reports/nodejs/phase15-recorder-promotion.json`
    - `build/reports/nodejs/phase15-recorder-promotion.md`
  - Still open: `recorder_runtime_permission_prompt_archive`,
    `recorder_foreground_disclosure_stop_action_archive`,
    `recorder_temp_output_ownership_cleanup_archive`,
    `recorder_crash_cleanup_session_resource_archive`,
    `recorder_packaged_start_stop_stress_archive`,
    `recorder_safe_downgrade_archive`,
    `recorder_raw_object_denial_security_archive`, and
    `phase15_task_device_media_doc_sync`.

- [ ] P15-20: Add packaged recorder and media stress evidence.
  - Source backlog: `P14-25`.
  - Gate: `:app:installAndRunNodeV13PackagedRecorderSmoke`.
  - Acceptance: packaged start/stop, relaunch, task removal, quota, denial,
    Safe downgrade, and cleanup are archived.
  - Partial gate deployed:
    - `docs/nodejs/phase15/packaged-recorder-smoke-plan.json`
    - `docs/nodejs/phase15/PHASE15_PACKAGED_RECORDER_SMOKE.md`
    - `docs/nodejs/reports/phase15/P15_20_PACKAGED_RECORDER_SMOKE.md`
    - `tools/nodejs/phase15/verify-node-phase15-packaged-recorder-smoke.js`
    - `build/reports/nodejs/phase15-packaged-recorder-smoke.json`
    - `build/reports/nodejs/phase15-packaged-recorder-smoke.md`
  - Still open: `packaged_recorder_start_stop_positive_archive`,
    `packaged_recorder_task_removal_relaunch_archive`,
    `packaged_recorder_quota_temp_cleanup_archive`,
    `packaged_recorder_denial_without_capability_archive`,
    `packaged_recorder_foreground_disclosure_stop_archive`,
    `packaged_recorder_safe_downgrade_archive`,
    `packaged_recorder_raw_object_denial_security_archive`, and
    `phase15_task_device_media_doc_sync`.

- [x] P15-21: Decide scoped MediaStore and playback promotion boundary.
  - Source backlog: `P14-26`.
  - Gate: `:app:verifyNodePhase15MediaStorePlaybackDecision`.
  - Acceptance: either stable denial remains documented or scoped provider
    requirements are fully met with permission and privacy evidence.
  - Stable-denied decision gate deployed:
    - `docs/nodejs/phase15/mediastore-playback-decision-plan.json`
    - `docs/nodejs/phase15/PHASE15_MEDIASTORE_PLAYBACK_DECISION.md`
    - `docs/nodejs/reports/phase15/P15_21_MEDIASTORE_PLAYBACK_DECISION.md`
    - `tools/nodejs/phase15/verify-node-phase15-mediastore-playback-decision.js`
    - `build/reports/nodejs/phase15-mediastore-playback-decision.json`
    - `build/reports/nodejs/phase15-mediastore-playback-decision.md`
  - Decision: `mediastore_playback` remains `stable_denied` for v1.3; no
    runtime authority changes. Future scoped-provider work remains tracked by
    `media_playback_scoped_provider_decision_archive`,
    `media_playlist_queue_denial_archive`,
    `mediastore_scoped_query_mutation_decision_archive`,
    `broad_media_library_permission_denial_archive`,
    `packaged_playback_mediastore_denial_archive`,
    `mediastore_playback_safe_downgrade_archive`,
    `mediastore_playback_raw_object_denial_security_archive`, and
    `phase15_task_device_media_doc_sync`.

- [x] P15-22: Sync task/device/media docs, examples, declarations, and release notes.
  - Gate: `:app:verifyNodePhase15TaskDeviceMediaDocSync`.
  - Acceptance: promoted and future-only state matches executable evidence.
  - Task/device/media docs sync gate deployed:
    - `docs/nodejs/phase15/task-device-media-doc-sync.json`
    - `docs/nodejs/phase15/PHASE15_TASK_DEVICE_MEDIA_DOC_SYNC.md`
    - `docs/nodejs/releases/NODE_PROFILE_V1_3_TASK_DEVICE_MEDIA_SYNC.md`
    - `docs/nodejs/reports/phase15/P15_22_TASK_DEVICE_MEDIA_DOC_SYNC.md`
    - `tools/nodejs/phase15/verify-node-phase15-task-device-media-doc-sync.js`
    - `build/reports/nodejs/phase15-task-device-media-doc-sync.json`
    - `build/reports/nodejs/phase15-task-device-media-doc-sync.md`
  - Decision: P15-12 through P15-20 remain partial and unpromoted; P15-21
    remains `stable_denied`; P15-22 records `provider_poc_not_promoted` docs,
    examples, declarations, security, error, CI, and release-facing state
    without changing runtime authority.

### P3 Security, Types, Packaged Aggregate, And Migration

- [x] P15-23: Update v1.3 security regression corpus.
  - Gate: `:app:verifyNodePhase15SecurityRegressionCorpus`.
  - Acceptance: every newly promoted provider has positive, negative, abuse,
    rollback, metadata mismatch, and raw-object denial cases.
  - Security regression corpus gate deployed:
    - `docs/nodejs/phase15/security-regression-corpus-plan.json`
    - `docs/nodejs/phase15/PHASE15_SECURITY_REGRESSION_CORPUS.md`
    - `docs/nodejs/security/NODE_PROFILE_V1_3_SECURITY_REGRESSION_CORPUS.json`
    - `docs/nodejs/reports/phase15/P15_23_SECURITY_REGRESSION_CORPUS.md`
    - `tools/nodejs/phase15/verify-node-phase15-security-regression-corpus.js`
    - `build/reports/nodejs/phase15-security-regression-corpus.json`
    - `build/reports/nodejs/phase15-security-regression-corpus.md`
  - Decision: P15-23 updates the v1.3 security regression corpus from P14-33
    and P15-04 through P15-22 with no newly promoted providers and no runtime
    authority change. P15-04 through P15-20 remain partial, P15-21 remains
    `stable_denied`, and the P15-23 surface manifest mapping remains
    `planned` by critical-risk policy.

- [x] P15-24: Add v1.3 packaged aggregate smoke.
  - Gate: `:app:installAndRunNodeV13PackagedAggregateSmoke`.
  - Acceptance: every packaged-capable promoted provider has deterministic
    positive, denial, Safe downgrade, cleanup, and report artifacts.
  - Packaged aggregate smoke gate deployed:
    - `docs/nodejs/phase15/node-v1-3-packaged-aggregate-smoke-plan.json`
    - `docs/nodejs/phase15/PHASE15_NODE_V1_3_PACKAGED_AGGREGATE_SMOKE.md`
    - `docs/nodejs/reports/phase15/P15_24_NODE_V1_3_PACKAGED_AGGREGATE_SMOKE.md`
    - `tools/nodejs/phase15/install-and-run-node-v13-packaged-aggregate-smoke.js`
    - `build/reports/nodejs/phase15-packaged-aggregate-smoke.json`
    - `build/reports/nodejs/phase15-packaged-aggregate-smoke.md`
  - Decision: P15-24 adds a deterministic `host_contract_ready` aggregate
    packaged smoke contract over P15-04, P15-07, P15-08, P15-09, P15-10,
    P15-14, P15-16, P15-17, P15-18, P15-20, P15-21, and P15-23 without
    claiming real device install-run success or changing runtime authority.
    The P15-24 surface manifest mapping remains `planned` by critical-risk
    policy.

- [x] P15-25: Update TypeScript declaration authority profile for v1.3.
  - Gate: `:app:verifyNodePhase15TypeDeclarationProfiles`.
  - Acceptance: callable, opt-in promoted, future-only, and stable-denied
    declarations match runtime evidence.
  - Type declaration profile gate deployed:
    - `docs/nodejs/phase15/node-v1-3-type-declaration-profiles.json`
    - `docs/nodejs/phase15/PHASE15_NODE_V1_3_TYPE_DECLARATION_PROFILES.md`
    - `docs/nodejs/reports/phase15/P15_25_TYPE_DECLARATION_PROFILES.md`
    - `docs/nodejs/types/autojs6-node/profile_capabilities.d.ts`
    - `sample/nodejs/typescript-smoke/src/main.ts`
    - `tools/nodejs/phase15/verify-node-phase15-type-declaration-profiles.js`
    - `build/reports/nodejs/phase15-type-declaration-profiles.json`
    - `build/reports/nodejs/phase15-type-declaration-profiles.md`
  - Decision: P15-25 adds metadata-only v1.3 TypeScript declaration profile
    truth with provider POC, docs sync, security corpus, packaged aggregate,
    and stable-denied rows. It keeps future-only and stable-denied methods
    visibly gated and does not change runtime authority.

- [x] P15-26: Update migration, Pro comparison, compatibility, and release notes.
  - Gate: `:app:verifyNodePhase15MigrationProComparison`.
  - Acceptance: docs do not overclaim raw Android object parity or provider
    promotion beyond executable evidence.
  - Migration and Pro comparison gate deployed:
    - `docs/nodejs/phase15/node-v1-3-migration-pro-comparison.json`
    - `docs/nodejs/phase15/PHASE15_NODE_V1_3_MIGRATION_PRO_COMPARISON.md`
    - `docs/nodejs/releases/NODE_PROFILE_V1_3_RELEASE_NOTES_DRAFT.md`
    - `docs/nodejs/reports/phase15/P15_26_MIGRATION_PRO_COMPARISON.md`
    - `tools/nodejs/phase15/verify-node-phase15-migration-pro-comparison.js`
    - `build/reports/nodejs/phase15-migration-pro-comparison.json`
    - `build/reports/nodejs/phase15-migration-pro-comparison.md`
  - Decision: P15-26 records v1.3 migration, Pro comparison,
    compatibility, examples, and release-note claim sync as
    `migration_pro_comparison_v1_3_ready`. It imports P14-36, P15-11,
    P15-22, P15-23, P15-24, and P15-25, keeps Pro UI/floaty,
    accessibility, screenshot/OCR, tasks, notifications, recorder, and
    MediaStore/playback tied to executable evidence, and does not change
    runtime authority or raw Android object parity.

### P4 Summary And Handoff

- [x] P15-27: Generate Phase 15 summary and next branch handoff.
  - Gate: `:app:generateNodePhase15Summary`.
  - Acceptance: zero open v1.3 release blockers, future backlog visible, and
    next branch handoff documented.
  - Summary handoff gate deployed:
    - `docs/nodejs/phase15/node-v1-3-summary-handoff.json`
    - `docs/nodejs/phase15/PHASE15_NODE_V1_3_SUMMARY_HANDOFF.md`
    - `docs/nodejs/reports/phase15/P15_27_PHASE15_SUMMARY_HANDOFF.md`
    - `docs/nodejs/reports/phase15/PHASE15_SUMMARY.md`
    - `docs/nodejs/reports/phase15/PHASE15_FUTURE_BACKLOG.json`
    - `docs/nodejs/reports/phase15/PHASE15_NEXT_BRANCH_HANDOFF.md`
    - `tools/nodejs/phase15/generate-phase15-summary.js`
    - `build/reports/nodejs/phase15-summary.json`
    - `build/reports/nodejs/phase15-summary.md`
  - Decision: P15-27 records `phase15_summary_handoff_ready`, keeps v1.3
    release-blocking reports at zero, exports the v1.4 future promotion
    backlog, and documents the next branch handoff without changing runtime
    authority.

## Suggested Initial Execution Order

1. Run P15-00 through P15-03 first to lock ownership, promotion bars, and
   rollback/disclosure rules.
2. Start with P15-04 through P15-07 because packaged live UI and overlay have
   the clearest v1.3 user-facing payoff.
3. Run P15-08 through P15-10 only after disclosure and resource cleanup are
   stable for UI/overlay.
4. Promote task database before IntentTask/broadcast because broadcast rows
   depend on task ownership and rollback conventions.
5. Do not update declarations or release notes as promoted until the matching
   runtime, device, packaged, and security gates are ready.
