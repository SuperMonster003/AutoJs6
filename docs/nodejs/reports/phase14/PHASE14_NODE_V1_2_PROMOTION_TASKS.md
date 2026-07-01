# Phase 14 Node v1.2 Provider Promotion Tasks

Status: initial executable plan for `feature/nodejs-runtime-v1.2`.

Phase 14 starts from the Phase 13 v1.1 closeout state. Phase 13 closed the
v1.1 release gate with zero open final blockers, zero required-internal
blockers, zero policy violations, and zero release-blocking reports. The
remaining Phase 13 partial reports are carried forward here as v1.2 promotion
work, not as v1.1 regressions.

Primary input:

- [`PHASE13_NODE_PRO_DESKTOP_PARITY_TASKS.md`](../phase13/PHASE13_NODE_PRO_DESKTOP_PARITY_TASKS.md)
- [`PHASE13_CLOSEOUT_BREAKDOWN.md`](../phase13/PHASE13_CLOSEOUT_BREAKDOWN.md)
- [`PHASE13_SUMMARY.md`](../phase13/PHASE13_SUMMARY.md)

## v1.2 Goal

Node Profile v1.2 should move the integration from a safe v1.1 closeout toward
usable Pro/Desktop provider promotion. The work should promote only capabilities
that have runtime implementation, connected-device evidence, packaged APK
evidence where relevant, disclosure and rollback behavior, TypeScript coverage,
and stable denial for every non-promoted authority.

The default Safe profile remains conservative. Pro/Desktop/Debug capabilities
must be opt-in, diagnosable, and reversible.

## Phase 13 Backlog Imported Into Phase 14

| Phase 13 task | Phase 14 lane | v1.2 direction |
| --- | --- | --- |
| `P13-17` live UI provider | Pro UI | Close packaged task relaunch, crash recovery, and user-visible disclosure behavior. |
| `P13-18` floaty/overlay provider | Pro UI | Implement Android overlay provider, permission matrix, foreground disclosure, packaged smoke, and leak evidence. |
| `P13-19` accessibility/selector parity | Pro automation | Add packaged accessibility-service lifecycle and selector/action real-device matrix. |
| `P13-20` screenshot/image/OCR parity | Pro media | Implement Activity-owned MediaProjection, advanced image operations, OCR/package quota evidence. |
| `P13-21` task database provider | Pro tasks | Implement JSON-safe task add/update/enable-disable/remove with migration and packaged evidence. |
| `P13-22` IntentTask/broadcast provider | Pro tasks | Implement manifest synthesis, exported policy, trigger payloads, and positive packaged trigger/remove smoke. |
| `P13-25` notifications/settings/power manager | Pro device | Add foreground disclosure ownership and OEM/background-launch matrix. |
| `P13-26` media/recorder/mediainfo provider | Pro media | Promote real recorder sessions, foreground microphone disclosure, and packaged stress evidence. |
| `P13-34` worker_threads | Desktop runtime | Decide v1.2 native-worker promotion from ABI evidence, or keep intentionally unsupported with refreshed proof. |

## Non-Goals Unless Reopened Explicitly

- Raw network authority, unrestricted sockets, raw `net`/`tls`/`dgram`
  promotion, and release raw network access.
- Native addon `.node` loading, runtime native downloads, npm lifecycle
  scripts, postinstall execution, and unrestricted package installation.
- Unrestricted `child_process`, raw `spawn`/`fork`, inherited stdio, detached
  processes, and raw process handles.
- Raw Android `Context`, `Activity`, `View`, `Bitmap`, `Intent`,
  `BroadcastReceiver`, `Notification`, `WakeLock`, `MediaProjection`,
  `MediaRecorder`, `TimedTask`, database cursor, or Java object exposure.
- Effective Pro/Desktop profile switching without disclosure, rollback,
  packaged metadata, and Safe-profile fallback evidence.
- Node runtime upgrade to a newer community fork unless production-ready
  Android shared-library artifacts exist for the required ABIs.

## Closeout Rule

A Phase 14 task can be marked complete only when all applicable items are true:

- A focused host verifier exists and reports `ready` or `passed`.
- Connected-device instrumentation evidence is archived under
  `build/reports/nodejs/phase14-*`.
- Packaged APK evidence exists for packaged behavior, or the task explicitly
  records why the capability stays main-app-only or denied.
- Prompt, foreground disclosure, stop action, privacy, rollback, and crash
  recovery behavior are covered for promoted high-privilege surfaces.
- TypeScript declarations, examples, migration docs, release matrix, CI docs,
  and security docs are synchronized.
- Stable denial branches stay tested for every authority that remains
  unsupported.
- The Phase 14 summary gate lists open future work without counting it as a
  v1.2 release blocker.

## Priority Lanes

| Lane | Tasks | Why first |
| --- | --- | --- |
| P0 branch and gate foundation | `P14-00` to `P14-06` | Establish v1.2 scope, report names, verifier taxonomy, and packaged disclosure foundations before provider promotion. |
| P1 Pro UI and automation | `P14-07` to `P14-16` | UI, overlay, accessibility, and screenshot/OCR are the largest Pro compatibility gaps. |
| P2 Pro task/device/media providers | `P14-17` to `P14-27` | Task database, IntentTask, notifications, power, and recorder support unlock practical Pro migration flows. |
| P3 Desktop runtime follow-up | `P14-28` to `P14-32` | `worker_threads` needs ABI proof before it can move beyond v1.1 intentional non-support. |
| P4 docs, examples, final gate | `P14-33` to `P14-39` | Release docs and summary should close after real provider evidence exists. |

## Task Checklist

### P0 Branch And Gate Foundation

- [x] P14-00: Record Phase 14 baseline from the Phase 13 closeout.
  - Inputs: Phase 13 summary JSON, closeout breakdown, final blocker registry,
    v1.1 capability matrix, current branch name.
  - Output: `build/reports/nodejs/phase14-baseline-verification.json`.
  - Gate: `:app:verifyNodePhase14Baseline`.
  - Acceptance: confirms Phase 13 release gate remains `ready`, imports the 9
    future-promotion tasks, and fails if v1.1 final blockers reopen.
  - Landed:
    - `tools/nodejs/phase14/verify-node-phase14-baseline.js`
    - `app/node-phase14-gate.gradle.kts`
    - `build/reports/nodejs/phase14-baseline-verification.json`
    - `build/reports/nodejs/phase14-baseline-verification.md`

- [x] P14-01: Create Phase 14 surface manifest.
  - Output: `docs/nodejs/phase14/phase14-surface-manifest.json` and companion
    markdown.
  - Gate: `:app:verifyNodePhase14SurfaceManifest`.
  - Acceptance: every P14 task maps to exactly one surface, profile, risk tier,
    report path, rollback hook, and owner evidence class.
  - Landed:
    - `docs/nodejs/phase14/phase14-surface-manifest.json`
    - `docs/nodejs/phase14/PHASE14_SURFACE_MANIFEST.md`
    - `docs/nodejs/schemas/phase14_surface_manifest.schema.json`
    - `tools/nodejs/phase14/verify-node-phase14-surface-manifest.js`
    - `build/reports/nodejs/phase14-surface-manifest-verification.json`
    - `build/reports/nodejs/phase14-surface-manifest-verification.md`

- [x] P14-02: Define v1.2 capability promotion vocabulary.
  - Scope: `safe_default`, `pro_compat_opt_in`, `desktop_compat_opt_in`,
    `debug_unsafe_lab`, and `permanently_unsupported`.
  - Acceptance: docs distinguish `implemented`, `packaged_ready`,
    `device_matrix_ready`, `policy_ready`, `stable_denied`, and
    `future_only`.
  - Gate: `:app:verifyNodePhase14PromotionContracts`.
  - Landed:
    - `docs/nodejs/phase14/phase14-promotion-contracts.json`
    - `docs/nodejs/phase14/PHASE14_PROMOTION_CONTRACTS.md`

- [x] P14-03: Add Phase 14 verifier/report conventions.
  - Scope: report schemas, deterministic report names, markdown mirrors, and
    release-blocker classification.
  - Acceptance: verifiers can classify blockers as `release_blocking`,
    `future_promotion`, `external`, or `intentional_non_goal`.
  - Gate: `:app:verifyNodePhase14PromotionContracts`.
  - Landed:
    - report name pattern `phase14-<surface-or-task>.json`
    - required report fields, markdown mirror policy, status vocabulary, and
      blocker classification vocabulary

- [x] P14-04: Define packaged high-privilege disclosure contract.
  - Scope: manifest metadata, install/run review copy, foreground notification
    ownership, stop action, and user-visible denial diagnostics.
  - Acceptance: live UI, overlay, MediaProjection, recorder, tasks,
    notifications, shell/root, and future profile switching can share the same
    disclosure vocabulary.
  - Gate: `:app:verifyNodePhase14PromotionContracts`.
  - Landed:
    - shared high-privilege packaged disclosure fields
    - raw Android object denial list for provider promotion

- [x] P14-05: Define rollback and crash circuit breaker hooks for promoted
  providers.
  - Scope: provider-level disable flags, Safe fallback, crash threshold,
    packaged downgrade behavior, and Node Doctor diagnostics.
  - Acceptance: docs and runtime diagnostics can identify which promoted
    provider was disabled and why.
  - Gate: `:app:verifyNodePhase14PromotionContracts`.
  - Landed:
    - provider/package scoped disable policy
    - default crash circuit breaker threshold and required diagnostics

- [x] P14-06: Define v1.2 device and packaged evidence matrix.
  - Required matrix: at least one Android 12 class device and one Android 15+
    class device for high-privilege provider behavior.
  - Acceptance: matrix records permission state, notification permission,
    background-start behavior, task removal, relaunch, crash recovery, and OEM
    quirks.
  - Gate: `:app:verifyNodePhase14PromotionContracts`.
  - Landed:
    - Android 12 class and Android 15+ class minimum matrix policy
    - required permission, lifecycle, foreground disclosure, crash recovery,
      resource, OEM, and report path fields

### P1 Pro UI, Overlay, Automation, Screenshot, OCR

- [ ] P14-07: Close packaged live UI provider gaps from `P13-17`.
  - Scope: packaged UI task relaunch, task removal, crash recovery,
    user-visible disclosure, raw Android global absence, and pending-dispatch
    drain.
  - Gate: `:app:verifyNodePhase14LiveUiProviderPackaged`.
  - Partial gate deployed:
    - Imports `P13-17` packaged capability-denial and entry/update/close smoke.
    - Records `pro.ui_automation_media` as `provider_poc`, not promoted.
    - Writes `build/reports/nodejs/phase14-live-ui-provider-packaged.json`
      and `.md`.
    - Keeps open blockers `packaged_task_relaunch`,
      `packaged_crash_recovery`, and `packaged_user_visible_disclosure` as
      `future_promotion`.
  - Acceptance: packaged UI entry/update/close/relaunch/crash smoke reports
    zero leaked handles and zero pending bridge dispatch.

- [ ] P14-08: Implement Android overlay provider v1.
  - Scope: `show`, `update`, `drainEvents`, `close`, lifecycle cleanup,
    permission denial, and handle ownership.
  - Gate: `:app:verifyNodePhase14OverlayProvider`.
  - Provider POC deployed:
    - Adds controlled JSON-only `ui.overlay` provider POC for
      `show`/`update`/`drainEvents`/`close`/`closeAll`.
    - Requires user-granted `SYSTEM_ALERT_WINDOW` before live overlay creation
      and fails closed with `ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED` otherwise.
    - Registers provider-owned `OVERLAY_SESSION` resources and cleans them up
      on `close`, `closeAll`, and provider destroy.
    - Writes `build/reports/nodejs/phase14-overlay-provider.json` and `.md`.
    - Keeps open blockers `overlay_permission_granted_device_evidence`,
      `overlay_foreground_disclosure_stop_action`,
      `packaged_overlay_positive_smoke`, and `overlay_leak_stress_evidence` as
      `future_promotion`.
  - Acceptance: provider works with `SYSTEM_ALERT_WINDOW` granted and fails
    closed without raw `window`/`View`/`Context` exposure.

- [ ] P14-09: Add overlay permission and OEM matrix.
  - Scope: granted/denied `SYSTEM_ALERT_WINDOW`, settings round-trip, task
    removal, relaunch, and OEM behavior notes.
  - Gate: `:app:verifyNodePhase14OverlayPermissionOemMatrix`.
  - Matrix gate deployed:
    - Adds `docs/nodejs/phase14/overlay-permission-oem-matrix.json` and
      `PHASE14_OVERLAY_PERMISSION_OEM_MATRIX.md` for required Android 12 /
      Android 15+ overlay evidence rows.
    - Imports the P14-08 permission-denied fail-closed boundary without
      claiming permission-granted device coverage.
    - Writes
      `build/reports/nodejs/phase14-overlay-permission-oem-matrix.json` and
      `.md`.
    - Keeps open blockers `overlay_android12_granted_device_archive`,
      `overlay_android15_plus_granted_device_archive`,
      `overlay_settings_round_trip_oem_notes`, and
      `overlay_task_removal_relaunch_evidence` as `future_promotion`.
  - Acceptance: at least Android 12 and Android 15+ evidence are archived, with
    denial copy and settings behavior recorded.

- [ ] P14-10: Add packaged overlay positive smoke and stress.
  - Scope: install/run, foreground disclosure, stop action, task-removal
    cleanup, relaunch, crash recovery, event drain, and leak/stress evidence.
  - Gate: `:app:verifyNodePhase14PackagedOverlayPositiveSmoke`.
  - Packaged positive fixture deployed:
    - Adds `docs/nodejs/phase14/packaged-overlay-positive-smoke-plan.json`
      and `PHASE14_PACKAGED_OVERLAY_POSITIVE_SMOKE.md` for the packaged
      overlay positive smoke/stress contract.
    - Adds
      `EmbeddedNodePackagedApkSmokeTest#buildOverlayPositiveEmbeddedNodePackagedApk`
      to build a packaged APK declaring `ui.overlay*` capabilities and
      `android.permission.SYSTEM_ALERT_WINDOW`.
    - Defines runtime markers for permission snapshot, foreground disclosure
      text, show/update/event drain, close/closeAll, three-session stress,
      leak summary, raw global absence, and pending dispatch cleanup.
    - Writes
      `build/reports/nodejs/phase14-packaged-overlay-positive-smoke.json`
      and `.md`.
    - Keeps open blockers `packaged_overlay_positive_device_archive`,
      `packaged_overlay_foreground_disclosure_stop_action`,
      `packaged_overlay_task_removal_relaunch_crash`, and
      `packaged_overlay_event_drain_leak_stress` as `future_promotion`.
  - Acceptance: packaged overlay can be promoted only if foreground disclosure
    and cleanup pass on the device matrix.

- [ ] P14-11: Add accessibility-service packaged lifecycle evidence.
  - Scope: service enabled/disabled state, packaged ownership, settings launch,
    task removal, relaunch, and denial paths.
  - Gate: `:app:verifyNodePhase14AccessibilityLifecycle`.
  - Packaged lifecycle fixture deployed:
    - Adds `docs/nodejs/phase14/accessibility-lifecycle-plan.json`
      and `PHASE14_ACCESSIBILITY_LIFECYCLE.md` for the packaged
      accessibility lifecycle contract.
    - Adds
      `EmbeddedNodePackagedApkSmokeTest#buildAccessibilityLifecycleEmbeddedNodePackagedApk`
      to build a packaged APK declaring `accessibility` and
      `android.permission.BIND_ACCESSIBILITY_SERVICE`.
    - Imports the P13-19 selector/live denial evidence and verifies the
      `ACTION_ACCESSIBILITY_SETTINGS`, `accessibility_service_description`,
      and reviewed-disclosure packaged policy contract.
    - Defines runtime markers for metadata, privacy/settings contract,
      disabled-state denial paths, raw global absence, unsupported helper
      absence, and pending dispatch cleanup.
    - Writes `build/reports/nodejs/phase14-accessibility-lifecycle.json`
      and `.md`.
    - Keeps open blockers `accessibility_packaged_service_enabled_archive`,
      `accessibility_settings_round_trip_archive`,
      `accessibility_task_removal_relaunch_archive`, and
      `accessibility_disconnect_missing_capability_denial_archive` as
      `future_promotion`.
  - Acceptance: packaged service lifecycle does not leak raw service handles
    and leaves selector globals fail-closed when capability is missing.

- [ ] P14-12: Add selector/action real-device matrix.
  - Scope: selector aliases, chain helpers, Back/Home/Recents, click/longClick
    where allowed, denial for gesture/root/Shizuku tiers.
  - Gate: `:app:verifyNodePhase14AccessibilitySelectorActionMatrix`.
  - Selector/action matrix gate deployed:
    - Adds `docs/nodejs/phase14/accessibility-selector-action-matrix.json`
      and `PHASE14_ACCESSIBILITY_SELECTOR_ACTION_MATRIX.md` for Android 12
      and Android 15+ accessibility selector/action archive requirements.
    - Extends the manual `accessibility` device profile smoke with controlled
      selector `longClick` and optional `recentApps` coverage when
      `autojs.nodejs.accessibilitySmoke.globalActions=true`.
    - Imports P13-19 direct selector, Rhino compat alias/chain,
      timeout/cancellation, missing-service denial, and raw-object boundary
      evidence.
    - Imports P13-24 root/Shizuku stable denial evidence so gesture/root/
      Shizuku tiers remain separate from `accessibility`.
    - Writes
      `build/reports/nodejs/phase14-accessibility-selector-action-matrix.json`
      and `.md`.
    - Keeps open blockers `accessibility_selector_android12_action_archive`,
      `accessibility_selector_android15_plus_action_archive`,
      `accessibility_selector_global_actions_recents_archive`, and
      `accessibility_selector_unsupported_tier_diagnostics_archive` as
      `future_promotion`.
  - Acceptance: matrix covers success, timeout, missing service, missing
    capability, and unsupported action diagnostics.

- [ ] P14-13: Implement Activity-owned MediaProjection provider.
  - Scope: request prompt, capture, stop, foreground disclosure, stop action,
    task removal cleanup, relaunch, crash recovery, and denial behavior.
  - Gate: `:app:verifyNodePhase14MediaProjectionProvider`.
  - MediaProjection provider gate deployed:
    - Adds `docs/nodejs/phase14/media-projection-provider-plan.json` and
      `PHASE14_MEDIA_PROJECTION_PROVIDER.md` for the Activity-owned
      MediaProjection provider contract.
    - Imports P13-20 screenshot/image/OCR evidence while keeping
      `screenCaptureBridgeProviderReady=false` and `media_projection`
      fail-closed.
    - Imports the optional real-device `screen_capture` smoke anchor:
      `NodeScreenCaptureRealDeviceSmokeInstrumentationTest#capturesOneFrameAfterUserGrantsScreenCapturePermission`.
    - Records the raw object boundary for `MediaProjection`, `ImageReader`,
      `Bitmap`, `Image`, display/surface objects, `ScreenCapturer`,
      `ImageWrapper`, `Context`, `Activity`, and `Intent`.
    - Writes `build/reports/nodejs/phase14-media-projection-provider.json`
      and `.md`.
    - Keeps open blockers
      `media_projection_android12_prompt_capture_archive`,
      `media_projection_android15_plus_prompt_capture_archive`,
      `media_projection_foreground_disclosure_stop_action_archive`, and
      `media_projection_task_removal_relaunch_crash_archive` as
      `future_promotion`.
  - Acceptance: no raw `MediaProjection`, `ImageReader`, `Bitmap`, or display
    object escapes into Node.

- [ ] P14-14: Promote advanced image operations.
  - Scope: save, clip, resize, grayscale, threshold, findImage, matchTemplate,
    color matching, temp-handle quota, and recycle semantics.
  - Gate: `:app:verifyNodePhase14AdvancedImageOperations`.
  - Advanced image operations gate deployed:
    - Adds `docs/nodejs/phase14/advanced-image-operations-plan.json` and
      `PHASE14_ADVANCED_IMAGE_OPERATIONS.md` for the advanced image operation
      promotion contract.
    - Imports P13-20 fake-provider, TypeScript, invalid-input,
      scoped `readImage`/`recycle`, OCR image-handle, and image-handle quota
      evidence.
    - Imports P14-13 MediaProjection boundary so `captureScreen` and
      screenshot-backed compat helpers remain blocked until prompt/capture
      ownership is promoted.
    - Keeps live Android save/transform/matching/color operations fail-closed
      instead of exposing raw Bitmap/OpenCV/ImageWrapper handles.
    - Writes `build/reports/nodejs/phase14-advanced-image-operations.json`
      and `.md`.
    - Keeps open blockers `image_advanced_transform_provider_archive`,
      `image_matching_color_provider_archive`,
      `image_temp_handle_quota_leak_stress_archive`, and
      `image_packaged_scoped_output_archive` as `future_promotion`.
  - Acceptance: operations work on opaque image handles with bounded memory and
    stable errors for invalid/recycled handles.

- [ ] P14-15: Add packaged image/OCR quota and model evidence.
  - Scope: ML Kit optional mode, Paddle/Rapid/plugin provider lookup policy,
    packaged model absence, temp-handle quota, and leak/stress evidence.
  - Gate: `:app:verifyNodePhase14PackagedImageOcrEvidence`.
  - Packaged image/OCR evidence gate deployed:
    - Adds `docs/nodejs/phase14/packaged-image-ocr-evidence-plan.json` and
      `PHASE14_PACKAGED_IMAGE_OCR_EVIDENCE.md` for the packaged image/OCR
      evidence contract.
    - Imports P13-20 scoped `readImage`/`recycle`, live ML Kit OCR,
      unknown/recycled-handle denial, and active resource cleanup evidence.
    - Imports P14-14 image invalid-input and quota boundary evidence.
    - Imports packaged v1.1 fake-provider OCR smoke for module resolution,
      `ocr` plus `image` capability checks, shadowing denial, and pending-call
      cleanup.
    - Keeps Paddle/Rapid/plugin provider selection stable-denied and packaged
      model absence, explicit provider-mode, quota/leak stress, and
      capture/disclosure archives open as `future_promotion`.
    - Writes `build/reports/nodejs/phase14-packaged-image-ocr-evidence.json`
      and `.md`.
  - Acceptance: OCR remains stable-denied when provider/model is absent and
    promoted only for explicit provider modes with archived evidence.

- [x] P14-16: Sync UI/automation/media docs, examples, and declarations.
  - Scope: TypeScript declarations, Pro migration examples, release matrix,
    compatibility profile, security docs, and error catalog.
  - Gate: `:app:verifyNodePhase14UiAutomationMediaDocSync`.
  - UI/automation/media docs sync gate deployed:
    - Adds `docs/nodejs/phase14/ui-automation-media-doc-sync.json` and
      `PHASE14_UI_AUTOMATION_MEDIA_DOC_SYNC.md`.
    - Adds `docs/nodejs/releases/NODE_PROFILE_V1_2_UI_AUTOMATION_MEDIA_MATRIX.md`
      as the v1.2 UI/automation/media release matrix.
    - Syncs declaration notes, Pro parity example policy, compatibility
      profile, security model, and error catalog under the same
      `provider_poc_not_promoted` decision.
    - Keeps only `ui-layout` and `tasks-work-manager` in the Pro parity runtime
      smoke subset; overlay, media/recorder, screenshot/OCR, and accessibility
      snippets remain static, guarded, or denial-oriented.
    - Writes `build/reports/nodejs/phase14-ui-automation-media-doc-sync.json`
      and `.md`.
  - Acceptance: examples execute only for promoted paths and stay static or
    denied for future-only high-risk snippets.

### P2 Pro Task, Broadcast, Notification, Power, Media Providers

- [ ] P14-17: Implement JSON-safe task database mutations.
  - Scope: add, update, enable, disable, remove, list, get, ownership mapping,
    id mapping, and stable errors.
  - Gate: `:app:verifyNodePhase14TaskDatabaseProvider`.
  - Task database provider contract gate deployed:
    - Adds `docs/nodejs/phase14/task-database-provider-plan.json` and
      `PHASE14_TASK_DATABASE_PROVIDER.md` for the JSON-safe task database
      mutation contract.
    - Imports P13-21 `work_manager`, `compat.tasks`, and
      `NodeTimedTaskSnapshots` evidence without exposing `require("tasks")`.
    - Reserves future `add`, `update`, `enable`, `disable`, `remove`, `list`,
      and `get` operations over versioned JSON descriptors and patches.
    - Records owner/id mapping boundaries for opaque Node ids, legacy
      `TimedTask` ids, packaged task ids, project identity, and package
      identity.
    - Keeps live mutation provider, ownership/id mapping archive, schema
      migration/rollback, wall-clock scheduler, and packaged mutation smoke
      archives open as `future_promotion`.
    - Writes `build/reports/nodejs/phase14-task-database-provider.json` and
      `.md`.
  - Acceptance: no raw `TimedTask`, scheduler, database cursor, or mutable row
    handle is exposed.

- [ ] P14-18: Add task schema migrations and rollback evidence.
  - Scope: versioned JSON schema, downgrade behavior, corruption recovery,
    packaged row ownership, and legacy visibility.
  - Gate: `:app:verifyNodePhase14TaskSchemaMigrationRollback`.
  - Task schema migration and rollback gate deployed:
    - Adds `docs/nodejs/phase14/task-schema-migration-rollback-plan.json` and
      `PHASE14_TASK_SCHEMA_MIGRATION_ROLLBACK.md`.
    - Imports P14-17 task database provider contract evidence and keeps
      schema migration/rollback marked not ready for live promotion.
    - Defines fixture coverage for `current_descriptor_v1`,
      `older_descriptor_v0`, `legacy_timed_task_snapshot_v1`,
      `corrupt_descriptor`, `unknown_future_schema`, and
      `packaged_owner_mismatch`.
    - Records Safe downgrade, Pro disable, packaged rollback, corruption
      quarantine, unknown schema preservation, and legacy `TimedTask`
      visibility policy.
    - Keeps live migration tests, corruption recovery archive, downgrade
      legacy visibility archive, and packaged rollback archive open as
      `future_promotion`.
    - Writes
      `build/reports/nodejs/phase14-task-schema-migration-rollback.json` and
      `.md`.
  - Acceptance: migration tests cover current, older, corrupt, and unknown
    schema versions.

- [ ] P14-19: Add packaged task scheduling smoke.
  - Scope: packaged add/list/enable-disable/remove, process death, relaunch,
    task removal, disclosure, and Safe downgrade.
  - Acceptance: packaged task rows remain scoped to the package identity and
    are removed or disabled according to rollback policy.

- [ ] P14-20: Implement JSON-safe IntentTask/broadcast provider.
  - Scope: owned descriptors, manifest synthesis, exported/non-exported
    policy, permission gates, trigger payload schema, and remove behavior.
  - Gate: proposed `:app:verifyNodePhase14IntentBroadcastProvider`.
  - Acceptance: no raw `Intent`, `PendingIntent`, `BroadcastReceiver`, or
    Android component object is exposed.

- [ ] P14-21: Add packaged broadcast trigger/remove smoke.
  - Scope: install/run, trigger delivery, payload validation, receiver cleanup,
    relaunch, crash recovery, and denial for undeclared capabilities.
  - Acceptance: exported receivers are denied unless explicitly declared and
    documented.

- [ ] P14-22: Add notification ownership and foreground disclosure evidence.
  - Scope: notification post/cancel ownership, stop action, permission denial,
    group/style/action stable denial or promotion decision, and cleanup.
  - Gate: proposed `:app:verifyNodePhase14NotificationOwnership`.
  - Acceptance: Node can cancel only owned notifications and cannot expose raw
    `Notification`, `PendingIntent`, `RemoteViews`, or listener handles.

- [ ] P14-23: Add settings/power OEM and background-launch matrix.
  - Scope: battery optimization state, exact-alarm state, settings dry-run,
    background launch behavior, Android 15+ restrictions, and OEM notes.
  - Acceptance: mutation flows stay denied unless a reviewed prompt and
    rollback path exists.

- [ ] P14-24: Implement real recorder session promotion.
  - Scope: microphone prompt, start, pause/resume if supported, stop, status,
    foreground disclosure, temp output ownership, and crash cleanup.
  - Gate: proposed `:app:verifyNodePhase14RecorderProvider`.
  - Acceptance: no raw `MediaRecorder`, file descriptor, or broad media-library
    permission escapes into Node.

- [ ] P14-25: Add packaged recorder and media stress evidence.
  - Scope: packaged recorder start/stop, task removal, relaunch, crash
    recovery, quota, temporary file cleanup, and denial without capability.
  - Acceptance: microphone foreground disclosure is visible and stop behavior
    is deterministic.

- [ ] P14-26: Decide MediaStore and playback boundaries for v1.2.
  - Scope: keep stable-denied or define a scoped future provider with explicit
    ownership, permissions, and privacy behavior.
  - Acceptance: raw media library mutation, playlist, and broad storage
    permissions remain denied unless a full provider gate is opened.

- [ ] P14-27: Sync task/device/media docs, examples, and declarations.
  - Scope: TypeScript declarations, migration cookbook, release matrix, error
    catalog, compatibility profile, and CI gates.
  - Acceptance: every promoted task/device/media method has docs, examples,
    stable errors, and denial examples.

### P3 Desktop Runtime Follow-Up

- [ ] P14-28: Audit native `worker_threads` availability on target ABIs.
  - Scope: required ABIs, current Node 24.5 artifacts, native preflight,
    Android loader behavior, and crash signatures.
  - Gate: proposed `:app:verifyNodePhase14WorkerNativeAvailability`.
  - Acceptance: v1.2 either has native runnable worker proof on target ABIs or
    a refreshed intentional non-support decision.

- [ ] P14-29: Add worker runnable and packaged smoke if native support exists.
  - Scope: basic worker, message passing, termination, timeout, exception,
    parent cleanup, nested worker policy, and packaged behavior.
  - Acceptance: worker authority remains disabled by default until packaged and
    resource gates pass.

- [ ] P14-30: Add worker resource and RSS stress if native support exists.
  - Scope: thread count, RSS, Java/native heap, fd count, message queues,
    cleanup, repeated start/stop, and crash recovery.
  - Acceptance: no monotonic leaks, orphan workers, or unrecoverable runtime.

- [ ] P14-31: Decide process-worker replacement for v1.2.
  - Scope: keep no user-facing facade, or define a WorkerPool-compatible API
    that accounts for execution slots, cleanup, packaging, and UX.
  - Acceptance: no raw process handles, stdio, signals, `spawn`, or `fork` are
    exposed.

- [ ] P14-32: Sync desktop runtime docs and release policy.
  - Scope: worker policy, process-worker decision, TypeScript declarations,
    release matrix, migration docs, and security corpus.
  - Acceptance: worker support is either promoted with evidence or explicitly
    remains intentionally unsupported for v1.2.

### P4 Security, Examples, Release Matrix, Final Gate

- [ ] P14-33: Expand v1.2 security regression corpus.
  - Scope: promoted UI/overlay/accessibility/MediaProjection/tasks/broadcast/
    notifications/recorder/worker paths, raw object denial, path escape,
    permission bypass, lifecycle cleanup, and packaged metadata mismatch.
  - Gate: proposed `:app:verifyNodePhase14SecurityRegressionCorpus`.
  - Acceptance: every promoted capability has positive, negative, and abuse
    cases.

- [ ] P14-34: Add v1.2 packaged aggregate smoke.
  - Scope: packaged Pro provider smoke for every promoted packaged-capable
    v1.2 surface, plus undeclared capability denial and Safe downgrade.
  - Gate: proposed `:app:installAndRunNodeV12PackagedSmoke`.
  - Acceptance: packaged smoke can be run from a deterministic Gradle entry
    and writes report artifacts under `build/reports/nodejs`.

- [ ] P14-35: Update TypeScript declaration profiles for v1.2.
  - Scope: promoted APIs, gated surfaces, stable-denied modules, profile
    capability metadata, and examples.
  - Acceptance: type smoke covers promoted APIs and keeps future-only methods
    visibly gated.

- [ ] P14-36: Update migration and Pro comparison docs for v1.2.
  - Scope: Pro UI, floaty, accessibility, screenshot/OCR, tasks,
    notifications, recorder, worker policy, packaged behavior, and rollback.
  - Acceptance: claims match executable evidence and do not imply raw Android
    object parity.

- [ ] P14-37: Create Phase 14 summary generator and gate.
  - Scope: aggregate task checklist, surface manifest, final blocker registry,
    capability truth, generated reports, open future work, and release-blocking
    report classification.
  - Gate: proposed `:app:generateNodePhase14Summary`.
  - Acceptance: v1.2 closeout requires zero open final blockers, zero policy
    violations, and zero release-blocking reports.

- [ ] P14-38: Create v1.2 release candidate checklist.
  - Scope: host gates, connected-device gates, packaged APK gates, docs sync,
    examples sync, TypeScript smoke, and release notes.
  - Acceptance: checklist separates required v1.2 blockers from v1.3 future
    promotion backlog.

- [ ] P14-39: Close Phase 14 and prepare the next branch handoff.
  - Scope: closeout breakdown, summary docs, final blocker registry updates,
    future backlog export, and branch handoff note.
  - Acceptance: `feature/nodejs-runtime-v1.2` can be committed with a clean
    worktree and the next branch can start from a documented backlog.

## Suggested Initial Execution Order

1. Run `P14-00` to `P14-06` first so every later provider has report,
   disclosure, rollback, and matrix vocabulary.
2. Close `P14-07` before broad UI work. It is closest to completion from the
   Phase 13 live UI evidence.
3. Implement overlay in `P14-08` to `P14-10`, because it exercises the same
   foreground disclosure and packaged cleanup contracts needed by recorder and
   MediaProjection.
4. Run accessibility and MediaProjection tracks in parallel only after the
   shared packaged disclosure contract is stable.
5. Promote task database before IntentTask/broadcast, because broadcast rows
   need ownership and rollback conventions.
6. Decide worker_threads early through `P14-28`; do not spend packaged worker
   effort until native availability is proven.
7. Keep docs, declarations, and examples updated task-by-task, then finish with
   `P14-37` to `P14-39`.

## v1.2 Done Definition

Phase 14 is ready to close when:

- `generateNodePhase14Summary` reports `ready`.
- All v1.2 release-blocking reports have zero blockers.
- Future-only reports are visible and excluded from release-blocking counts.
- The Safe profile remains default and fail-closed.
- Every promoted Pro/Desktop capability has stable denial coverage for missing
  profile, missing capability, missing Android permission, packaged metadata
  mismatch, task removal, relaunch, and crash recovery where applicable.
- The v1.2 branch has a clean worktree and a documented next-phase backlog.
