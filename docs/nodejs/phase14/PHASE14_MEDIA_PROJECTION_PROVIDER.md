# Phase 14 MediaProjection Provider

P14-13 records the Activity-owned MediaProjection provider contract for the
Node v1.2 promotion lane. The current Android app already has an optional
real-device `screen_capture` smoke that launches `MainActivity`, requests the
system MediaProjection prompt through `ScreenCaptureRequester`, captures one
frame through `ScreenCapturer`, validates frame and decoded-bitmap dimensions,
then releases the frame, bitmap, capturer, and foreground service.

The Node bridge provider is not promoted by this plan. `media_projection`
continues to reserve `requestScreenCapture`, `nextImage`, and `stop`, while
the Android provider rejects until an Activity-owned provider owns prompt,
denial, foreground disclosure, stop action, cleanup, relaunch, crash recovery,
and packaged behavior.

## Contract

- Module: `media_projection`.
- Capability: `screen_capture` (`media_projection` remains an accepted alias).
- Methods: `requestScreenCapture`, `nextImage`, and `stop`.
- Prompt ownership: Android `Activity`, never the embedded Node service.
- Foreground behavior: user-visible disclosure and stop action are required
  before promotion.
- Handle boundary: Node receives only opaque capturer and image handles.
- Raw object denial: `MediaProjection`, `ImageReader`, `Bitmap`, `Image`,
  `VirtualDisplay`, `Display`, `Surface`, `ScreenCapturer`, `ImageWrapper`,
  `Context`, `Activity`, and `Intent` must not cross into JavaScript.

## Manual Archive Requirements

P14-13 keeps the required device archives open:

- Android 12 class `screen_capture` run.
- Android 15+ class `screen_capture` run.
- Prompt grant and denial behavior.
- One-frame capture through `ScreenCapturer`.
- `nextImage` opaque image-handle boundary.
- `stop` release behavior for projection and foreground service.
- Foreground disclosure and stop action.
- Task removal cleanup, relaunch behavior, and crash recovery.
- Raw MediaProjection/ImageReader/Bitmap/display object absence.

## Imported Evidence

- P13-20 screenshot/image/OCR promotion report and host gate.
- `NodeScreenCaptureRealDeviceSmokeInstrumentationTest#capturesOneFrameAfterUserGrantsScreenCapturePermission`.
- `docs/nodejs/DEVICE_MATRIX.md` `screen_capture` profile.
- `docs/nodejs/SECURITY_MODEL.md` MediaProjection and raw object boundary.

## Verification

Host verifier:

```powershell
node tools/nodejs/phase14/verify-node-phase14-media-projection-provider.js --repo-root D:\idea-projects\AutoJs6-nodejs --report-dir D:\idea-projects\AutoJs6-nodejs\build\reports\nodejs
```

Gradle gate:

```powershell
.\gradlew.bat --console=plain :app:verifyNodePhase14MediaProjectionProvider
```
