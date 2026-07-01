# P14-10 Packaged Overlay Positive Smoke

Status: partial

P14-10 deploys the packaged overlay positive smoke/stress fixture and host
gate. The fixture builds a packaged APK with explicit `ui.overlay` capability
metadata and `android.permission.SYSTEM_ALERT_WINDOW`, and it defines the
runtime markers required for permission-granted install-run evidence.

The task remains unchecked because the packaged positive run, foreground
disclosure/stop action, task-removal/relaunch/crash behavior, and event
drain/leak-stress archives are not present yet.

## Implemented Evidence

- Packaged fixture:
  `EmbeddedNodePackagedApkSmokeTest#buildOverlayPositiveEmbeddedNodePackagedApk`
  builds `PackagedNodeOverlayPositiveSmoke_v1.0.0.apk`.
- Capability metadata: the fixture declares `ui.overlay`,
  `ui.overlay.permission`, `ui.overlay.foreground`, and
  `ui.overlay.packaged`.
- Android metadata: the fixture declares
  `android.permission.SYSTEM_ALERT_WINDOW` in packaged project permissions.
- Runtime marker contract: the fixture records marker expectations for
  permission snapshots, foreground disclosure text, show/update/event-drain,
  close/closeAll, three-session stress, leak summary, raw global absence, and
  zero pending bridge dispatch.
- Gate wiring: `:app:verifyNodePhase14PackagedOverlayPositiveSmoke` depends on
  `:app:verifyNodePhase14OverlayPermissionOemMatrix` and writes deterministic
  JSON/MD reports.

## Remaining Blockers

| Blocker | Classification | Required evidence |
| --- | --- | --- |
| `packaged_overlay_positive_device_archive` | `future_promotion` | Archived install-run logs for the packaged overlay positive APK on the Android 12 and Android 15+ matrix. |
| `packaged_overlay_foreground_disclosure_stop_action` | `future_promotion` | Foreground disclosure ownership and stop-action behavior reviewed while packaged overlay is visible. |
| `packaged_overlay_task_removal_relaunch_crash` | `future_promotion` | Task removal cleanup, package relaunch behavior, and crash recovery evidence for packaged overlay sessions. |
| `packaged_overlay_event_drain_leak_stress` | `future_promotion` | Event drain and leak/stress archive proving three overlay sessions close with zero pending dispatch and zero remaining overlays. |

## Gate

Host verifier:

```powershell
node tools/nodejs/phase14/verify-node-phase14-packaged-overlay-positive-smoke.js --repo-root D:\idea-projects\AutoJs6-nodejs --report-dir D:\idea-projects\AutoJs6-nodejs\build\reports\nodejs
```

Gradle gate:

```powershell
.\gradlew.bat --console=plain :app:verifyNodePhase14PackagedOverlayPositiveSmoke
```

Expected reports:

```text
build/reports/nodejs/phase14-packaged-overlay-positive-smoke.json
build/reports/nodejs/phase14-packaged-overlay-positive-smoke.md
```
