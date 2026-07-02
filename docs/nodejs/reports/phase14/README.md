# Phase 14 Reports

Phase 14 tracks Node Profile v1.2 provider promotion work after the Phase 13
v1.1 closeout gate.

Primary checklist:

- `PHASE14_NODE_V1_2_PROMOTION_TASKS.md`

Foundation gates:

- `:app:verifyNodePhase14Baseline`
- `:app:verifyNodePhase14SurfaceManifest`
- `:app:verifyNodePhase14PromotionContracts`

Provider POC gates:

- `:app:verifyNodePhase14LiveUiProviderPackaged`
- `:app:verifyNodePhase14OverlayProvider`
- `:app:verifyNodePhase14OverlayPermissionOemMatrix`
- `:app:verifyNodePhase14PackagedOverlayPositiveSmoke`
- `:app:verifyNodePhase14AccessibilityLifecycle`
- `:app:verifyNodePhase14AccessibilitySelectorActionMatrix`
- `:app:verifyNodePhase14MediaProjectionProvider`
- `:app:verifyNodePhase14AdvancedImageOperations`
- `:app:verifyNodePhase14PackagedImageOcrEvidence`
- `:app:verifyNodePhase14UiAutomationMediaDocSync`
- `:app:verifyNodePhase14TaskDatabaseProvider`
- `:app:verifyNodePhase14TaskSchemaMigrationRollback`
- `:app:verifyNodePhase14PackagedTaskSchedulingSmoke`
- `:app:verifyNodePhase14IntentBroadcastProvider`
- `:app:verifyNodePhase14PackagedBroadcastTriggerRemoveSmoke`
- `:app:verifyNodePhase14NotificationOwnership`

Phase 14 carries forward the Phase 13 future-promotion backlog for live UI,
overlay, accessibility, screenshot/OCR, task providers, notifications/power,
media/recorder, and `worker_threads`. These items are v1.2 promotion work, not
v1.1 release blockers.

`P14_07_LIVE_UI_PROVIDER_PACKAGED.md` records the current packaged live UI
state as `provider_poc`. It imports the `P13-17` packaged capability-denial and
entry/update/close smoke evidence while keeping packaged task relaunch, crash
recovery, and user-visible disclosure as future-promotion blockers.

`P14_08_OVERLAY_PROVIDER_V1.md` records the current Android overlay provider
state as `provider_poc`. It adds controlled JSON-only overlay session creation
for permission-granted devices while keeping permission-granted evidence,
foreground disclosure/stop action, packaged positive smoke, and leak/stress
coverage as future-promotion blockers.

`P14_09_OVERLAY_PERMISSION_OEM_MATRIX.md` records the overlay permission/OEM
matrix as `partial`. It imports the P14-08 permission-denied boundary, reserves
Android 12 and Android 15+ archive rows, and keeps granted device evidence,
OEM settings round-trip notes, and task removal/relaunch evidence as
future-promotion blockers.

`P14_10_PACKAGED_OVERLAY_POSITIVE_SMOKE.md` records the packaged overlay
positive smoke/stress fixture as `partial`. It adds a packaged APK fixture with
explicit overlay capability metadata and `SYSTEM_ALERT_WINDOW` while keeping
install-run archives, foreground disclosure/stop action, task
removal/relaunch/crash behavior, and event drain/leak-stress evidence as
future-promotion blockers.

`P14_11_ACCESSIBILITY_LIFECYCLE.md` records the packaged accessibility
lifecycle fixture as `partial`. It adds a packaged APK fixture with explicit
`accessibility` capability metadata and `BIND_ACCESSIBILITY_SERVICE`, imports
the P13-19 selector denial boundary, and keeps user-enabled service, settings
round-trip, task removal/relaunch, disconnect, and missing-capability archives
as future-promotion blockers.

`P14_12_ACCESSIBILITY_SELECTOR_ACTION_MATRIX.md` records the selector/action
real-device matrix as `partial`. It imports P13-19 selector and compat alias
coverage, extends the manual accessibility profile with `longClick` and
optional Recents coverage, imports P13-24 root/Shizuku denial evidence, and
keeps Android 12/15+, global-action, and unsupported-tier archives as
future-promotion blockers.

`P14_13_MEDIA_PROJECTION_PROVIDER.md` records the Activity-owned
MediaProjection provider contract as `partial`. It imports the P13-20
screenshot/image/OCR state and the optional real-device `screen_capture` smoke
anchor, keeps `media_projection` fail-closed, and keeps Android 12/15+ prompt
capture, foreground disclosure/stop action, and task-removal/relaunch/crash
archives as future-promotion blockers.

`P14_14_ADVANCED_IMAGE_OPERATIONS.md` records the advanced image operation
promotion contract as `partial`. It imports P13-20 fake-provider,
TypeScript, invalid-input, scoped `readImage`/`recycle`, OCR handle, and quota
evidence, keeps live Android save/transform/matching/color operations
fail-closed, and keeps live provider, leak/stress, and packaged scoped-output
archives as future-promotion blockers.

`P14_15_PACKAGED_IMAGE_OCR_EVIDENCE.md` records the packaged image/OCR evidence
contract as `partial`. It imports P13-20 live ML Kit OCR, P14-14 image quota
and invalid-input boundaries, and packaged v1.1 fake-provider OCR smoke while
keeping model-absence, explicit provider-mode, quota/leak stress, and
capture/disclosure archives as future-promotion blockers.

`P14_16_UI_AUTOMATION_MEDIA_DOC_SYNC.md` records the UI/automation/media docs,
examples, declarations, release matrix, compatibility, security, and error
catalog sync as `ready`. It keeps `pro.ui_automation_media` at
`provider_poc_not_promoted` and ensures high-risk Pro examples stay static,
guarded, or denial-oriented unless their source provider gates are promoted.

`P14_17_TASK_DATABASE_PROVIDER.md` records the JSON-safe task database mutation
provider contract as `partial`. It imports P13-21 `work_manager`,
`compat.tasks`, and `NodeTimedTaskSnapshots` evidence while keeping
`require("tasks")`, future `tasks` capabilities, live database mutation,
schema migration, wall-clock scheduler parity, and packaged task rows
unpromoted.

`P14_18_TASK_SCHEMA_MIGRATION_ROLLBACK.md` records the task schema migration
and rollback contract as `partial`. It adds current, older, legacy snapshot,
corrupt, unknown, and packaged owner-mismatch fixture coverage while keeping
live migration tests, corruption recovery, downgrade legacy visibility, and
packaged rollback archives open.

`P14_19_PACKAGED_TASK_SCHEDULING_SMOKE.md` records the packaged task scheduling
smoke baseline as `partial`. It imports packaged `work_manager` fake-provider
schedule/list/cancel smoke and live packaged project-entry scheduled-runner
evidence while keeping packaged task database add/list/enable-disable/remove,
process death/relaunch, task-removal cleanup, disclosure, and Safe downgrade
archives open.

`P14_20_INTENT_BROADCAST_PROVIDER.md` records the JSON-safe
IntentTask/broadcast provider contract as `partial`. It imports P13-22
receiver substrate, compat rejection, JSON snapshot, and packaged boundary
evidence, defines future `tasks.broadcast` descriptor, manifest, exported
policy, trigger payload, and remove semantics, and keeps live provider,
manifest synthesis, exported/permission policy, payload, and cleanup archives
open.

`P14_21_PACKAGED_BROADCAST_TRIGGER_REMOVE_SMOKE.md` records the packaged
broadcast trigger/remove smoke contract as `partial`. It imports the current
packaged IntentTask/broadcast boundary smoke, reserves future positive
install/trigger/remove/relaunch/crash/denial markers, and keeps positive
trigger, cleanup, relaunch/crash, undeclared-capability, and exported receiver
archives open.

`P14_22_NOTIFICATION_OWNERSHIP.md` records notification ownership and
foreground disclosure evidence as `partial`. It imports P13-25
notification/settings evidence and live Android `notify`/`cancel` ownership
coverage while keeping packaged post/cancel, foreground disclosure/stop action,
permission matrix, advanced notification, and rollback cleanup archives open.

`P14_23_SETTINGS_POWER_OEM_BACKGROUND_MATRIX.md` records settings/power OEM
and background-launch matrix evidence as `partial`. It imports P13-25
`device.power` status/settings dry-run evidence, records Android 12/15+,
exact-alarm, background-launch, OEM, prompt, and rollback archive requirements,
and keeps direct power-manager mutation authority unpromoted behind
`:app:verifyNodePhase14SettingsPowerOemBackgroundMatrix`.

`P14_24_RECORDER_PROVIDER.md` records real recorder session promotion evidence
as `partial`. It imports P13-26 recorder status and packaged boundary smoke,
defines runtime permission, foreground disclosure, ResourceRegistry session,
temporary output, crash cleanup, and packaged stress requirements, and keeps
`recorder.start()` / `recorder.stop()` unpromoted behind
`:app:verifyNodePhase14RecorderProvider`.

`P14_25_PACKAGED_RECORDER_MEDIA_STRESS.md` records packaged recorder/media
stress evidence as `partial`. It imports the current packaged media/recorder
boundary smoke, reserves positive start/stop, foreground disclosure, task
removal, relaunch/crash, quota, temp cleanup, and denial archives, and keeps
packaged recorder start/stop unpromoted behind
`:app:verifyNodePhase14PackagedRecorderMediaStress`.

`P14_26_MEDIA_PLAYBACK_MEDIASTORE_BOUNDARY.md` records media playback and
MediaStore boundary evidence as `partial`. It imports P13-26 stable-denial
evidence, keeps playback, playlists, scanFile, MediaStore, broad media-library
permissions, and raw media handles denied, and requires a future scoped
provider gate before any of those surfaces can be promoted behind
`:app:verifyNodePhase14MediaPlaybackMediaStoreBoundary`.

`P14_27_TASK_DEVICE_MEDIA_DOC_SYNC.md` records task/device/media docs, examples,
declarations, release matrix, migration cookbook, compatibility, security,
error, testing, and CI sync as `ready`. It imports P14-17 through P14-26 reports
and keeps the surface state as `provider_poc_not_promoted` behind
`:app:verifyNodePhase14TaskDeviceMediaDocSync`.

`P14_28_WORKER_NATIVE_AVAILABILITY.md` records the v1.2 worker native
availability audit as `ready`. It verifies Node 24.5 target ABI artifacts,
imports the P13-34 unavailable native-preflight evidence, audits Android loader
and crash-signature anchors, and refreshes `worker_threads` as intentional
non-support until a target ABI reports `worker.preflight.status=available`
behind `:app:verifyNodePhase14WorkerNativeAvailability`.

`P14_29_WORKER_RUNNABLE_PACKAGED_SMOKE.md` records the worker runnable and
packaged smoke gate as `ready` with a `conditional_skip_native_unavailable`
decision. It verifies the existing Worker lifecycle, message, WorkerPool,
exception/timeout, cleanup, nested-denial, and packaged `.mjs` smoke source
anchors while keeping runnable execution, packaged execution, and worker
authority disabled behind `:app:verifyNodePhase14WorkerRunnablePackagedSmoke`.

`P14_30_WORKER_RESOURCE_RSS_STRESS.md` records the worker resource/RSS stress
gate as `ready` with a `conditional_skip_native_unavailable` decision. It
verifies source anchors for sequential Worker cycles, WorkerPool stress,
cleanup, exception/timeout recovery, RSS snapshots, and thread-count bounded
assertions while recording Java heap, native heap, fd, queue, active worker, and
active WorkerPool deltas as future native-available archives behind
`:app:verifyNodePhase14WorkerResourceRssStress`.

`P14_31_PROCESS_WORKER_REPLACEMENT_DECISION.md` records the process-worker
replacement decision as `ready` with a `no_user_facing_facade_v1_2` decision.
It imports P13-35, P14-28, P14-29, and P14-30 reports, verifies the diagnostic
`processWorkerReplacementProfile`, absence of callable `process_worker` /
`compute_worker` facades, default `child_process` denial, separate `shell`
bridge policy, reserved `worker_computation`, and keeps raw process handles,
stdio, signals, `spawn`, `fork`, packaged process-worker behavior, and
WorkerPool replacement API authority unpromoted behind
`:app:verifyNodePhase14ProcessWorkerReplacementDecision`.

`P14_32_DESKTOP_RUNTIME_DOC_SYNC.md` records the desktop runtime docs and
release-policy sync as `ready` with a
`worker_intentionally_unsupported_v1_2` decision. It imports P14-28 through
P14-31 generated reports and verifies TypeScript declaration references,
worker design docs, desktop examples, release matrix, migration cookbook,
compatibility profile, security model, security corpus, error catalog,
Pro/AutoX migration notes, testing, and CI docs behind
`:app:verifyNodePhase14DesktopRuntimeDocSync`.

`P14_33_SECURITY_REGRESSION_CORPUS.md` records the v1.2 security regression
corpus expansion as `ready` with a
`security_corpus_expanded_no_authority_promoted` decision. It imports P14-07
through P14-32 generated reports and verifies positive, negative, abuse,
runtime guard, promotion precondition, and rollback entries for live UI,
overlay, accessibility, MediaProjection, image/OCR, tasks, broadcast,
notifications, power, recorder/media, MediaStore/playback, worker/process, and
packaged metadata/Safe downgrade boundaries behind
`:app:verifyNodePhase14SecurityRegressionCorpus`.

`P14_34_NODE_V1_2_PACKAGED_AGGREGATE_SMOKE.md` records the v1.2 packaged
aggregate smoke as `ready` with a
`packaged_aggregate_smoke_contract_ready` decision. It imports P14-07, P14-10,
P14-11, P14-15, P14-19, P14-21, P14-22, P14-23, P14-25, P14-29, and P14-33
generated reports, verifies aggregate suite contracts for positive or
conditional paths, undeclared capability denial, packaged metadata mismatch,
Safe downgrade, cleanup, and report artifacts, and writes
`phase14-packaged-aggregate-smoke.json` plus `.md` behind
`:app:installAndRunNodeV12PackagedSmoke`.
