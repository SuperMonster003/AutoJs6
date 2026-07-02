# Phase 15 Reports

Phase 15 tracks Node Profile v1.3 provider promotion work after the Phase 14
v1.2 closeout handoff.

Primary checklist:

- `PHASE15_NODE_V1_3_PROVIDER_PROMOTION_TASKS.md`

Foundation gates:

- `:app:verifyNodePhase15Baseline`
- `:app:verifyNodePhase15SurfaceManifest`
- `:app:verifyNodePhase15PromotionContracts`
- `:app:verifyNodePhase15DisclosureRollbackAudit`

Phase 15 imports the Phase 14 future backlog for live UI, overlay,
accessibility, screenshot/OCR, task providers, notifications/power,
media/recorder, and MediaStore/playback. These items are v1.3 promotion work,
not v1.2 release blockers.

`P15-00` records the v1.3 baseline as `passed` when the Phase 14 closeout
handoff remains ready, the current branch is `feature/nodejs-runtime-v1.3`, and
the 19 provider backlog tasks are imported from
`PHASE14_FUTURE_BACKLOG.json`.

`P15-01` records the v1.3 surface manifest as `passed` when
`phase15-surface-manifest.json` maps `P15-00` through `P15-27` exactly once,
owns every imported Phase 14 backlog task, and declares profile, risk,
evidence class, gate, and rollback hook metadata for each task.

`P15-02` records the v1.3 promotion contracts as `ready` when the `promoted`,
`provider_poc`, `future_only`, `stable_denied`, and `rejected` vocabulary is
current and every promotable provider requires implementation, connected
device, packaged APK, disclosure, rollback, security, TypeScript, docs, Safe
downgrade, and raw-object denial evidence before opt-in promotion.

`P15-03` records the v1.3 disclosure and rollback audit as `ready` when every
promotable provider declares prompt, manifest metadata, install/run copy,
foreground notification ownership, stop action, privacy, Safe downgrade,
provider disable flag, diagnostic code, rollback proof path, and crash circuit
breaker marker requirements before runtime authority changes.

Provider promotion gates:

- `:app:verifyNodePhase15PackagedLiveUiPromotion`
- `:app:verifyNodePhase15OverlayProviderPromotion`
- `:app:verifyNodePhase15OverlayOemMatrix`
- `:app:installAndRunNodeV13PackagedOverlaySmoke`
- `:app:verifyNodePhase15AccessibilityPromotion`
- `:app:verifyNodePhase15MediaProjectionPromotion`
- `:app:verifyNodePhase15ImageOcrPromotion`
- `:app:verifyNodePhase15UiAutomationMediaDocSync`
- `:app:verifyNodePhase15TaskDatabasePromotion`

`P15-04` currently reports `partial`: it imports P13/P14 packaged live UI
evidence and P15 disclosure/rollback requirements, keeps `live_ui` at
`provider_poc_not_promoted`, and lists the remaining packaged relaunch, task
removal cleanup, crash recovery, disclosure, Android 15+, and downstream
security/type/docs sync archives required before promotion.

`P15-05` currently reports `partial`: it imports P14 overlay provider POC
evidence, permission-denied and raw-object-denial boundaries, the conditional
permission-granted fixture, P13 packaged overlay denial, and P15
disclosure/rollback requirements. It keeps `overlay` unpromoted until Android
12/15+ granted-permission archives, foreground disclosure, cleanup stress,
packaged overlay positive smoke, OEM matrix, and security/type/docs sync land.

`P15-06` currently reports `partial`: it imports the P14 overlay
permission/OEM matrix, validates the required Android 12 and Android 15+
device slots, and depends on the P15-05 overlay provider gate. It keeps
`overlay` unpromoted until granted-permission archives, OEM settings notes,
task removal/relaunch behavior, foreground disclosure/stop action, and
security/type/docs sync land.

`P15-07` currently reports `partial`: it imports the P14 packaged overlay
positive smoke/stress fixture, marker contract, and APK metadata, then depends
on the P15-05 and P15-06 overlay gates. It keeps `overlay` unpromoted until
packaged install-run archives, Android 12/15+ coverage, disclosure/stop action,
task removal/relaunch/crash behavior, stress/leak proof, and
security/type/docs sync land.

`P15-08` currently reports `partial`: it imports the P14 accessibility
lifecycle fixture and selector/action matrix, plus P13 selector and shell tier
boundaries. It keeps `accessibility` unpromoted until Android 12/15+
service-enabled archives, settings round-trip, task removal/relaunch,
disconnect/missing-capability denial, safe global-action diagnostics,
unsupported-tier diagnostics, and security/type/docs sync land.

`P15-09` currently reports `partial`: it imports the P14 Activity-owned
MediaProjection contract, optional screen_capture prompt/capture smoke, P13
screenshot/image/OCR evidence, and P15 disclosure/rollback requirements. It
keeps `media_projection` unpromoted until Android 12/15+ prompt/capture
archives, foreground disclosure/stop action, task removal/relaunch/crash
evidence, packaged APK evidence, Safe downgrade proof, and security/type/docs
sync land.

`P15-10` currently reports `partial`: it imports the P14 advanced image
operation contract and packaged image/OCR evidence contract, plus P13 scoped
image/OCR evidence and the P15 MediaProjection dependency gate. It keeps
`image_ocr` unpromoted until live transform/matching archives, packaged scoped
output, packaged OCR model/provider-mode archives, quota/leak stress,
capture-to-OCR disclosure, Safe downgrade proof, and security/type/docs sync
land.

`P15-11` reports `ready`: it imports the P15-04 through P15-10 partial provider
reports and the P14-16 docs sync report, then verifies that examples,
TypeScript declaration notes, release-facing text, compatibility, security,
error guidance, TESTING, and CI_GATES all keep the UI/automation/media lane at
`provider_poc_not_promoted` without adding runtime authority.

`P15-12` currently reports `partial`: it imports the P14 JSON-safe task
database mutation contract, P13 `work_manager`/`compat.tasks`/TimedTask
snapshot evidence, and P15 disclosure/rollback requirements. It keeps
`task_database` unpromoted until live mutation archives, owner/id mapping,
schema migration/rollback, wall-clock scheduler evidence, packaged mutation
smoke, cross-package denial, disclosure/stop action, Safe downgrade, and
security/type/docs sync land.
