# Phase 14 Advanced Image Operations

P14-14 records the advanced image operation promotion contract for the Node
v1.2 Pro media lane. The JavaScript and TypeScript API shape already covers
`saveImage`, `clip`, `resize`, `grayscale`, `threshold`, `findImage`,
`matchTemplate`, `findColor`, `findMultiColors`, `getSize`, and `recycle`.
P13-20 also provides fake-provider coverage, invalid input denial, scoped
`readImage`/`recycle`, and live OCR over provider-owned handles.

The live Android provider is not promoted by this plan. It continues to expose
only scoped `readImage` and `recycle`; advanced transforms, matching, color
search, screenshot capture, and packaged image behavior remain fail-closed
until a dedicated provider owns Bitmap/OpenCV processing, output paths, quota,
leak/stress evidence, and packaged scoped-file behavior.

## Contract

- Modules: `image` and `images`.
- Capability: `image`; screenshot capture also requires `screen_capture`.
- Imported live methods: `readImage` and `recycle`.
- Transform methods: `saveImage`, `clip`, `resize`, `grayscale`, `threshold`.
- Matching/color methods: `findImage`, `matchTemplate`, `findColor`,
  `findMultiColors`.
- JavaScript-only helper: `getSize`.
- Handle boundary: every provider result is an opaque image handle or frozen
  JSON point/match snapshot.
- Derived handles count against image-handle quota until recycled.
- `saveImage` output paths must stay scoped to the project working directory.
- Raw object denial: `Bitmap`, `Mat`, `ImageWrapper`, OpenCV objects, native
  pointers, filesystem path handles, `MediaProjection`, and `ImageReader` must
  not cross into JavaScript.

## Imported Evidence

- P13-20 screenshot/image/OCR promotion report and host gate.
- `NodeImageBridgeExpansionInstrumentationTest#jsImageExpansionUsesFakeTransportAndBlocksShadowing`.
- `NodeImageBridgeExpansionInstrumentationTest#jsImageExpansionRejectsInvalidPathsAndHandlesBeforeDispatch`.
- `NodeImageBridgeExpansionInstrumentationTest#androidImageExpansionRejectsUntilProviderIsConnected`.
- `NodeBridgeConcurrencyLimitsInstrumentationTest#jsImageHandleLimitAllowsRecycleThenReuse`.
- P14-13 MediaProjection contract gate.

## Remaining Archives

- Live transform provider archive for save/clip/resize/grayscale/threshold.
- Live matching/color provider archive for findImage/matchTemplate/findColor/
  findMultiColors.
- Temporary-handle quota, recycle, timeout, provider destroy, and leak/stress
  archive.
- Packaged scoped input/output path and missing-capability archive.

## Verification

Host verifier:

```powershell
node tools/nodejs/phase14/verify-node-phase14-advanced-image-operations.js --repo-root D:\idea-projects\AutoJs6-nodejs --report-dir D:\idea-projects\AutoJs6-nodejs\build\reports\nodejs
```

Gradle gate:

```powershell
.\gradlew.bat --console=plain :app:verifyNodePhase14AdvancedImageOperations
```
