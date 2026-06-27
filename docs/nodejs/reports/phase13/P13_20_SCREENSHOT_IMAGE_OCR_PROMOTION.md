# P13-20 Screenshot Image OCR Promotion

Status: partial

P13-20 records the current screenshot, image, and OCR state for Phase 13. The
bridge has enough evidence for a useful Pro/Rhino migration slice: direct
`image`/`images` JavaScript APIs and TypeScript declarations exist, scoped
`readImage` plus explicit `recycle` are backed by provider-owned image handles,
and OCR can run through the live ML Kit Chinese provider over those handles.
Rhino compatibility wrappers route screenshots, image helpers, and OCR through
the same controlled bridge APIs.

The surface is not promoted to `pro_compat_opt_in` yet. Full Auto.js Pro parity
still needs an Activity-owned MediaProjection bridge provider, real image
processing providers, and packaged capture/image/OCR evidence. Paddle/Rapid or
plugin OCR provider selection and raw Bitmap/OpenCV/MediaProjection object
parity stay stable-denied for P13-20 v1.1.

## Implemented Evidence

- Direct modules: `require("image")` and `require("images")` expose
  `captureScreen`, `readImage`, `saveImage`, `clip`, `resize`, `grayscale`,
  `threshold`, `findImage`, `matchTemplate`, `findColor`,
  `findMultiColors`, `getSize`, and `recycle` in JavaScript and TypeScript.
- Live image provider: Android currently promotes only scoped `readImage` and
  explicit `recycle`; handles are owned by the bridge resource registry and the
  provider destroy path must return active image resources to zero.
- Screen capture boundary: `media_projection` reserves
  `requestScreenCapture`, `nextImage`, and `stop`, and the optional real-device
  screen-capture smoke validates the existing Android requester/capturer
  lifecycle outside Node bridge promotion.
- OCR provider: `require("ocr")` exposes `recognize`, `recognizeText`, and
  `detectTextBounds`; the live ML Kit provider consumes provider-owned image
  handles and rejects unknown or recycled handles without leaking resources.
- Rhino compat facade: `compat.images`, `compat.requestScreenCapture`,
  `compat.captureScreen`, and callable `compat.ocr` declarations reuse the
  controlled `image`/`images`, `media_projection`, and `ocr` providers.
- Raw boundary: raw `MediaProjection`, `ImageReader`, `ScreenCapturer`,
  `Image`, `Bitmap`, `Mat`, `ImageWrapper`, OpenCV objects, OCR engines, ML Kit
  objects, native pointers, and Java objects do not cross into Node.

## Boundaries

- `media_projection` remains `unavailable` for the Node bridge. The bridge must
  not prompt directly until an Activity-owned provider owns permission, denial,
  foreground disclosure, stop action, and cleanup behavior.
- `captureScreen`, `saveImage`, image transforms, image matching, and color
  search remain fail-closed in the live Android provider until owned image
  processing providers and packaged scoped file evidence exist.
- OCR is ML Kit-only for the current live provider. Paddle/Rapid/plugin model
  selection, packaged model lookup, and fallback diagnostics stay stable-denied
  for P13-20 v1.1 until a separate provider-selector gate owns model assets,
  fallback behavior, diagnostics, and rollback.
- Packaged APK screenshot, image, and OCR behavior is not promoted by P13-20.
  Missing capability, missing Android permission, disclosure, output path,
  quota, and cleanup evidence still need dedicated gates.
- The current Rhino compat surface is async/Promise based. It is not a
  synchronous Rhino image/OCR runtime and unsupported Rhino OCR modes stay
  blocked.
- Raw Bitmap/OpenCV/MediaProjection object parity stays stable-denied for
  P13-20 v1.1. Node receives only JSON-safe descriptors, frozen result
  snapshots, and provider-owned opaque handles.

## Remaining Blockers

- Implement an Activity-owned MediaProjection provider for
  `requestScreenCapture`, `nextImage`, and `stop`, including permission prompt,
  foreground service disclosure, user denial, task-removal cleanup, and packaged
  APK behavior.
- Promote real image processing providers for save, clip, resize, grayscale,
  threshold, image matching, and color search with scoped input/output paths and
  leak/stress evidence.
- Add packaged positive and negative smokes for screenshot/image/OCR capability
  metadata, Android permissions, provider absence, temporary-handle cleanup, and
  relaunch/crash recovery.

The docs, compatibility notes, device matrix, surface manifest, and Gradle gate
are evidence-only for this partial decision. They do not add a separate P13-20
blocker beyond MediaProjection, advanced image processing, packaged OCR/image
smoke, and packaged disclosure evidence above. The OCR fallback policy gate
tracks the current `compat.ocr.tap("mlkit")` declaration and keeps
Paddle/Rapid/plugin provider selection stable-denied until a separate provider
selector gate exists.

## Gate

Host verifier:

```powershell
node tools/nodejs/phase13/verify-screenshot-image-ocr-promotion.js --repo-root D:\idea-projects\AutoJs6 --report-dir D:\idea-projects\AutoJs6\build\reports\nodejs
```

Gradle gate:

```powershell
.\gradlew.bat --console=plain :app:verifyNodePhase13ScreenshotImageOcrPromotion
```

Expected reports:

```text
build/reports/nodejs/phase13-screenshot-image-ocr-promotion.json
build/reports/nodejs/phase13-screenshot-image-ocr-promotion.md
```
