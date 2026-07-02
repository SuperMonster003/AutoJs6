# P14-15 Packaged Image OCR Evidence

Status: partial

P14-15 deploys the packaged image/OCR evidence gate. It imports the P13-20
ML Kit OCR slice, the P14-14 image quota/invalid-input boundary, and packaged
fake-provider OCR smoke while keeping packaged model absence, provider-mode,
quota/leak, and capture/disclosure evidence open.

This task remains unchecked because no packaged APK archive currently proves
model-absence denial, explicit provider-mode lookup, repeated image/OCR quota
stress, or capture-to-OCR foreground disclosure behavior on Android 12 and
Android 15+ profiles.

## Implemented Evidence

- Plan source:
  `docs/nodejs/phase14/packaged-image-ocr-evidence-plan.json`.
- Provider policy:
  current OCR defaults to ML Kit Chinese, accepts only the explicit `mlkit`
  compatibility mode, and keeps Paddle/Rapid/plugin provider selection
  stable-denied.
- Live import:
  P13-20 covers scoped `image.readImage()`, explicit `image.recycle()`, ML Kit
  OCR over provider-owned image handles, unknown/recycled-handle denial, and
  active image/OCR resource counts returning to zero.
- Packaged fake-provider import:
  `EmbeddedNodePackagedApkSmokeTest` covers packaged `ocr` module resolution,
  required `ocr` plus `image` permissions, shadowing denial, and pending-call
  cleanup.
- Quota anchor:
  P14-14 and the bridge concurrency tests cover current image-handle quota and
  recycle-before-reuse behavior for provider-owned handles.
- Raw object boundary:
  raw OCR engines, ML Kit `Text`, Paddle/Rapid models, plugin service binders,
  `Bitmap`, `Mat`, `ImageWrapper`, `MediaProjection`, model paths, native
  pointers, and Java objects stay outside JavaScript.

## Remaining Blockers

| Blocker | Classification | Required evidence |
| --- | --- | --- |
| `packaged_ocr_model_absence_archive` | `future_promotion` | Packaged APK run where ML Kit/Paddle/Rapid/plugin OCR model assets or services are absent, producing stable unavailable/degraded bridge errors without leaking OCR/image resources. |
| `packaged_ocr_explicit_provider_mode_archive` | `future_promotion` | Explicit packaged OCR provider-mode evidence for mlkit success and Paddle/Rapid/plugin stable-denied lookup diagnostics. |
| `packaged_image_ocr_quota_leak_stress_archive` | `future_promotion` | Packaged image/OCR temp-handle quota, repeated read/OCR/recycle loops, provider destroy, timeout, and leak/stress archive with active image and OCR resources returning to zero. |
| `packaged_capture_ocr_disclosure_archive` | `future_promotion` | Packaged capture-to-OCR evidence with MediaProjection foreground disclosure, stop action, task removal/relaunch cleanup, and missing-capability denial. |

## Gate

Host verifier:

```powershell
node tools/nodejs/phase14/verify-node-phase14-packaged-image-ocr-evidence.js --repo-root D:\idea-projects\AutoJs6-nodejs --report-dir D:\idea-projects\AutoJs6-nodejs\build\reports\nodejs
```

Gradle gate:

```powershell
.\gradlew.bat --console=plain :app:verifyNodePhase14PackagedImageOcrEvidence
```
