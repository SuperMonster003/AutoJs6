# P14-13 MediaProjection Provider

Status: partial

P14-13 deploys the Activity-owned MediaProjection provider contract and host
gate. It imports the P13-20 screenshot/image/OCR partial state and the optional
real-device `screen_capture` smoke, then records the remaining device and
lifecycle evidence required before Node can promote `media_projection` beyond
the current fail-closed provider state.

The task remains unchecked because Android 12 class and Android 15+ class
screen-capture archives, foreground disclosure/stop-action evidence, and task
removal/relaunch/crash-recovery evidence are not present yet.

## Implemented Evidence

- Plan source:
  `docs/nodejs/phase14/media-projection-provider-plan.json`.
- Provider contract:
  `requestScreenCapture`, `nextImage`, and `stop` remain reserved for
  `media_projection` with `screen_capture` capability requirements.
- Activity-owned prompt anchor:
  `NodeScreenCaptureRealDeviceSmokeInstrumentationTest#capturesOneFrameAfterUserGrantsScreenCapturePermission`
  uses `MainActivity`, `ScreenCaptureRequester`, `ScreenCapturer`, one captured
  frame, bitmap/frame cleanup, capturer release, and foreground-service stop.
- Imported P13-20 evidence:
  scoped image `readImage`/`recycle` and ML Kit OCR are live, while
  `screenCaptureBridgeProviderReady=false` keeps MediaProjection prompt/capture
  promotion open.
- Raw object boundary:
  raw `MediaProjection`, `ImageReader`, `Bitmap`, `Image`, `VirtualDisplay`,
  `Display`, `Surface`, `ScreenCapturer`, `ImageWrapper`, `Context`,
  `Activity`, and `Intent` must not cross into Node.
- Gate wiring:
  `:app:verifyNodePhase14MediaProjectionProvider` depends on
  `:app:verifyNodePhase14AccessibilitySelectorActionMatrix` and
  `:app:verifyNodePhase13ScreenshotImageOcrPromotion`.

## Remaining Blockers

| Blocker | Classification | Required evidence |
| --- | --- | --- |
| `media_projection_android12_prompt_capture_archive` | `future_promotion` | Archived Android 12 class screen_capture profile run with prompt grant/denial, one-frame capture, stop, and raw object absence. |
| `media_projection_android15_plus_prompt_capture_archive` | `future_promotion` | Archived Android 15+ class screen_capture profile run with prompt grant/denial, one-frame capture, stop, and raw object absence. |
| `media_projection_foreground_disclosure_stop_action_archive` | `future_promotion` | Foreground disclosure ownership and user-visible stop action behavior while capture is active. |
| `media_projection_task_removal_relaunch_crash_archive` | `future_promotion` | Task removal cleanup, package relaunch behavior, and crash recovery evidence for active or recently stopped capture sessions. |

## Gate

Host verifier:

```powershell
node tools/nodejs/phase14/verify-node-phase14-media-projection-provider.js --repo-root D:\idea-projects\AutoJs6-nodejs --report-dir D:\idea-projects\AutoJs6-nodejs\build\reports\nodejs
```

Gradle gate:

```powershell
.\gradlew.bat --console=plain :app:verifyNodePhase14MediaProjectionProvider
```

Expected reports:

```text
build/reports/nodejs/phase14-media-projection-provider.json
build/reports/nodejs/phase14-media-projection-provider.md
```
