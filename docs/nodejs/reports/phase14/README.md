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
