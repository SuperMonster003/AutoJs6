# Phase 14 Packaged Overlay Positive Smoke

Machine-readable source:

- `docs/nodejs/phase14/packaged-overlay-positive-smoke-plan.json`

## Status

Status: partial

P14-10 defines the packaged `ui.overlay` positive smoke/stress fixture and the
archive requirements needed before packaged overlay promotion. The fixture
builds a packaged APK that declares the overlay Node capabilities and
`android.permission.SYSTEM_ALERT_WINDOW`, then emits deterministic markers for
metadata, permission snapshot, visible disclosure, show/update/event-drain,
close/closeAll, a three-session stress loop, raw global denial, and pending
dispatch cleanup.

This is not a promotion claim. The fixture still needs real install-run
archives on the Android 12 and Android 15+ matrix with overlay permission
granted by the user before launch.

Manual archive flags:

- `requiresUserGrantedOverlayPermission`: `true`
- `requiresForegroundDisclosureReview`: `true`

## Fixture

| Field | Value |
| --- | --- |
| Android test class | `EmbeddedNodePackagedApkSmokeTest` |
| Build method | `buildOverlayPositiveEmbeddedNodePackagedApk` |
| Builder method | `buildPackagedOverlayPositiveSmokeApk` |
| Project | `PackagedNodeOverlayPositiveSmoke` |
| Package | `org.autojs6.packaged.node.overlay.positive.smoke` |
| APK | `PackagedNodeOverlayPositiveSmoke_v1.0.0.apk` |

Required Node permissions:

- `ui.overlay`
- `ui.overlay.permission`
- `ui.overlay.foreground`
- `ui.overlay.packaged`

Required Android permission:

- `android.permission.SYSTEM_ALERT_WINDOW`

## Required Runtime Markers

- `packaged.overlay_positive.ready=true`
- `packaged.overlay_positive.metadata=true`
- `packaged.overlay_positive.permission_snapshot=true`
- `packaged.overlay_positive.permission_granted=true`
- `packaged.overlay_positive.disclosure=true`
- `packaged.overlay_positive.show=true`
- `packaged.overlay_positive.update=true`
- `packaged.overlay_positive.event_drain=true`
- `packaged.overlay_positive.close=true`
- `packaged.overlay_positive.close_all=true`
- `packaged.overlay_positive.stress_sessions=3`
- `packaged.overlay_positive.leak_summary=true`
- `packaged.overlay_positive.raw_globals_absent=true`
- `packaged.overlay_positive.pending=0`

## Open Blockers

| Blocker | Classification | Required evidence |
| --- | --- | --- |
| `packaged_overlay_positive_device_archive` | `future_promotion` | Archived install-run logs for the packaged overlay positive APK on the Android 12 and Android 15+ matrix. |
| `packaged_overlay_foreground_disclosure_stop_action` | `future_promotion` | Foreground disclosure ownership and stop-action behavior reviewed while packaged overlay is visible. |
| `packaged_overlay_task_removal_relaunch_crash` | `future_promotion` | Task removal cleanup, package relaunch behavior, and crash recovery evidence for packaged overlay sessions. |
| `packaged_overlay_event_drain_leak_stress` | `future_promotion` | Event drain and leak/stress archive proving three overlay sessions close with zero pending dispatch and zero remaining overlays. |

## Verification

- Host verifier: `tools/nodejs/phase14/verify-node-phase14-packaged-overlay-positive-smoke.js`
- Gradle gate: `:app:verifyNodePhase14PackagedOverlayPositiveSmoke`
- Reports:
  - `build/reports/nodejs/phase14-packaged-overlay-positive-smoke.json`
  - `build/reports/nodejs/phase14-packaged-overlay-positive-smoke.md`
