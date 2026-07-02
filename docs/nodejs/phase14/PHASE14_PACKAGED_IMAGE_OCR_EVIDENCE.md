# Phase 14 Packaged Image OCR Evidence

P14-15 records the packaged image/OCR evidence contract for Node v1.2. It does
not promote new OCR providers or packaged capture authority. The current live
slice remains ML Kit Chinese OCR over provider-owned image handles imported
from P13-20, with P14-14 supplying image invalid-input and quota boundaries.

## Current Contract

- `image` and `images` remain the only source of provider-owned image handles.
- `ocr` requires both `ocr` and `image` capabilities.
- `autojs6:compat`.ocr may load scoped path inputs through temporary image
  handles and must recycle them after OCR settles.
- The only explicit compatibility OCR mode currently accepted is `mlkit`.
- Paddle, Rapid, and plugin OCR provider selection stay stable-denied until a
  separate provider-mode archive proves lookup, denial, fallback diagnostics,
  and packaged model behavior.
- Packaged capture-to-OCR remains blocked until the P14-13 MediaProjection
  prompt/disclosure/cleanup archive is promoted.
- JavaScript must not receive raw OCR engines, ML Kit `Text`, Paddle/Rapid
  models, plugin service binders, `Bitmap`, `Mat`, `ImageWrapper`, native
  pointers, model paths, or Java objects.

## Evidence Imported

- P13-20 live ML Kit OCR over scoped `readImage` handles, explicit
  `recycle()`, unknown/recycled-handle denial, and zero active resource counts.
- P14-14 advanced image operation gate for image-handle quota and stable
  invalid-input behavior.
- Packaged v1.1 fake-provider bridge smoke for `ocr` with required `ocr` and
  `image` permissions, builtin shadowing denial, and pending-call cleanup.
- Provider registry details for ML Kit availability, Paddle embedded asset
  lookup, Paddle plugin-service lookup, packaged image degraded support, and
  OCR provider-dependent packaged support.

## Open Archives

| Archive | Classification | Required evidence |
| --- | --- | --- |
| `packaged_ocr_model_absence_archive` | `future_promotion` | Packaged APK run where ML Kit/Paddle/Rapid/plugin OCR model assets or services are absent, producing stable unavailable/degraded bridge errors without leaking OCR/image resources. |
| `packaged_ocr_explicit_provider_mode_archive` | `future_promotion` | Explicit packaged OCR provider-mode evidence for mlkit success and Paddle/Rapid/plugin stable-denied lookup diagnostics. |
| `packaged_image_ocr_quota_leak_stress_archive` | `future_promotion` | Packaged image/OCR temp-handle quota, repeated read/OCR/recycle loops, provider destroy, timeout, and leak/stress archive with active image and OCR resources returning to zero. |
| `packaged_capture_ocr_disclosure_archive` | `future_promotion` | Packaged capture-to-OCR evidence with MediaProjection foreground disclosure, stop action, task removal/relaunch cleanup, and missing-capability denial. |

## Gate

```powershell
.\gradlew.bat --console=plain :app:verifyNodePhase14PackagedImageOcrEvidence
```

Expected reports:

```text
build/reports/nodejs/phase14-packaged-image-ocr-evidence.json
build/reports/nodejs/phase14-packaged-image-ocr-evidence.md
```
