# P14-14 Advanced Image Operations

Status: partial

P14-14 deploys the advanced image operation promotion contract and host gate.
It imports P13-20 screenshot/image/OCR evidence, the P14-13 MediaProjection
contract, JavaScript/TypeScript API coverage, fake-provider coverage,
invalid-input denial, scoped `readImage`/`recycle`, and current handle-quota
evidence.

The task remains unchecked because the live Android provider still does not
promote save/transform/matching/color operations, temporary derived-handle
leak/stress evidence is not archived, and packaged scoped input/output evidence
is not present yet.

## Implemented Evidence

- Plan source:
  `docs/nodejs/phase14/advanced-image-operations-plan.json`.
- API contract:
  `image` and `images` expose `saveImage`, `clip`, `resize`, `grayscale`,
  `threshold`, `findImage`, `matchTemplate`, `findColor`, `findMultiColors`,
  `getSize`, and `recycle` in JavaScript and TypeScript.
- Fake-provider coverage:
  `NodeImageBridgeExpansionInstrumentationTest#jsImageExpansionUsesFakeTransportAndBlocksShadowing`
  covers transform, matching/color, path normalization, timeout forwarding,
  frozen results, and single-dispatch recycle.
- Invalid-input coverage:
  `NodeImageBridgeExpansionInstrumentationTest#jsImageExpansionRejectsInvalidPathsAndHandlesBeforeDispatch`
  rejects path escapes, absolute paths, invalid handles, invalid dimensions,
  invalid threshold options, invalid colors, and empty multi-color points before
  bridge dispatch.
- Live boundary:
  `NodeImageBridgeExpansionInstrumentationTest#androidImageExpansionRejectsUntilProviderIsConnected`
  keeps advanced live Android operations fail-closed with stable bridge errors.
- Quota anchor:
  `NodeBridgeConcurrencyLimitsInstrumentationTest#jsImageHandleLimitAllowsRecycleThenReuse`
  verifies image-handle quota rejection and reuse after recycle.
- Raw object boundary:
  raw `Bitmap`, `Mat`, `ImageWrapper`, OpenCV objects, native pointers,
  filesystem path handles, `MediaProjection`, and `ImageReader` stay outside
  JavaScript.
- Gate wiring:
  `:app:verifyNodePhase14AdvancedImageOperations` depends on
  `:app:verifyNodePhase14MediaProjectionProvider` and
  `:app:verifyNodePhase13ScreenshotImageOcrPromotion`.

## Remaining Blockers

| Blocker | Classification | Required evidence |
| --- | --- | --- |
| `image_advanced_transform_provider_archive` | `future_promotion` | Live Android provider evidence for saveImage, clip, resize, grayscale, and threshold over opaque provider-owned image handles. |
| `image_matching_color_provider_archive` | `future_promotion` | Live Android provider evidence for findImage, matchTemplate, findColor, and findMultiColors with deterministic JSON point/match snapshots. |
| `image_temp_handle_quota_leak_stress_archive` | `future_promotion` | Temporary derived-handle quota, repeated recycle, provider destroy, timeout, and leak/stress archive with active image resources returning to zero. |
| `image_packaged_scoped_output_archive` | `future_promotion` | Packaged scoped input/output path evidence for read, save, transform, matching/color denial or success, relaunch cleanup, and missing-capability denial. |

## Gate

Host verifier:

```powershell
node tools/nodejs/phase14/verify-node-phase14-advanced-image-operations.js --repo-root D:\idea-projects\AutoJs6-nodejs --report-dir D:\idea-projects\AutoJs6-nodejs\build\reports\nodejs
```

Gradle gate:

```powershell
.\gradlew.bat --console=plain :app:verifyNodePhase14AdvancedImageOperations
```

Expected reports:

```text
build/reports/nodejs/phase14-advanced-image-operations.json
build/reports/nodejs/phase14-advanced-image-operations.md
```
